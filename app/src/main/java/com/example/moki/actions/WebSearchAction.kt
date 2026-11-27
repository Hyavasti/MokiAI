package com.example.moki.actions

import com.example.moki.brain.CloudEscalationEngine
import com.example.moki.brain.LiteRtLmEngine
import com.example.moki.brain.providers.SharedCloudHttp
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class WebSearchAction(
    private val liteRtLmEngine: LiteRtLmEngine,
    private val cloudEngine: CloudEscalationEngine
) : MokiSkill {

    override val name: String = "web_search"
    override val description: String =
        "Searches the live web for up-to-date facts, news, weather, or general knowledge and summarizes the results."
    override val parameterSchema: String =
        """{"query": "search terms to look up online"}"""
    override val triggerKeywords: List<String> = listOf(
        "search", "look up", "google", "news", "latest", "current", "online", "web", "who won"
    )

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val cleanQuery = params["query"].orEmpty().trim()
        if (cleanQuery.isEmpty()) {
            return SkillExecutionResult(
                success = false,
                spokenResponse = "Tell me what you want me to search for."
            )
        }

        val snippets = fetchWebSnippets(cleanQuery)
        if (snippets.isBlank()) {
            return SkillExecutionResult(
                success = false,
                spokenResponse = "I couldn't reach live search results right now. Check your internet connection and try again."
            )
        }

        val systemPrompt = """
            You are Moki, a concise on-device Android voice assistant.
            Answer the user's question in 2 short, clear spoken sentences using only the provided live web search snippets.
            Do not use markdown, bullet points, or introductory filler.
        """.trimIndent()

        val prompt = "Question: $cleanQuery\n\nLive Web Snippets:\n$snippets"

        val synthesized = cloudEngine.escalate(systemPrompt, prompt)
            ?.takeIf { it.isNotBlank() }
            ?: liteRtLmEngine.generate(systemPrompt, prompt)
                ?.takeIf { it.isNotBlank() }

        val finalReply = synthesized
            ?: snippets.lines().take(2).joinToString(" ").replace("• ", "")

        return SkillExecutionResult(
            success = true,
            spokenResponse = finalReply.trim()
        )
    }

    private suspend fun fetchWebSnippets(query: String): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val collected = mutableListOf<String>()

        try {
            val ddgReq = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .addHeader("User-Agent", "MokiAssistant/1.0")
                .build()
            SharedCloudHttp.client.newCall(ddgReq).execute().use { res ->
                if (res.isSuccessful) {
                    val json = JSONObject(res.body?.string().orEmpty())
                    val abstractText = json.optString("AbstractText").trim()
                    if (abstractText.isNotEmpty()) collected.add(abstractText)

                    val related = json.optJSONArray("RelatedTopics")
                    if (related != null) {
                        for (i in 0 until minOf(related.length(), 4)) {
                            val item = related.optJSONObject(i) ?: continue
                            val text = item.optString("Text").trim()
                            if (text.isNotEmpty()) collected.add(text)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (collected.isEmpty()) {
            try {
                val htmlReq = Request.Builder()
                    .url("https://html.duckduckgo.com/html/?q=$encoded")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
                    .build()
                SharedCloudHttp.client.newCall(htmlReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val html = res.body?.string().orEmpty()
                        val snippetRegex = Regex(
                            """class="result__snippet"[^>]*>(.*?)</a>""",
                            RegexOption.DOT_MATCHES_ALL
                        )
                        snippetRegex.findAll(html).take(4).forEach { match ->
                            val clean = android.text.Html.fromHtml(
                                match.groupValues[1],
                                android.text.Html.FROM_HTML_MODE_COMPACT
                            ).toString().trim()
                            if (clean.isNotEmpty()) collected.add(clean)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        collected.distinct().joinToString("\n• ").take(2500)
    }
}