package com.example.moki.actions

import com.example.moki.brain.CloudEscalationEngine
import com.example.moki.brain.GeminiNanoEngine
import com.example.moki.brain.LiteRtLmEngine
import com.example.moki.data.preferences.EscalationMode
import com.example.moki.data.preferences.MokiSettingsRepository
import com.example.moki.services.MokiAccessibilityService
import com.example.moki.services.ScreenContextReader
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult
import kotlinx.coroutines.flow.first

class ScreenSummaryAction(
    private val geminiNanoEngine: GeminiNanoEngine,
    private val liteRtLmEngine: LiteRtLmEngine,
    private val cloudEscalationEngine: CloudEscalationEngine,
    private val settingsRepository: MokiSettingsRepository
) : MokiSkill {

    override val name: String = "screen_context"
    override val description: String =
        "Reads visible text from the active screen to summarize content or draft and paste a reply into an open app."
    override val parameterSchema: String =
        """{"mode": "summarize|describe|reply", "instruction": "optional user guidance"}"""
    override val triggerKeywords: List<String> = listOf(
        "screen", "summarize", "summarise", "recap", "reply", "respond", "page", "article", "email"
    )

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        if (MokiAccessibilityService.instance == null) {
            return SkillExecutionResult(
                success = false,
                spokenResponse = "Enable Moki Accessibility Service in Settings first so I can read your screen."
            )
        }

        val snapshot = ScreenContextReader.captureActiveScreen()
            ?: return SkillExecutionResult(
                success = false,
                spokenResponse = "Switch to the app or page you want me to read first, then ask me again."
            )

        val mode = params["mode"]?.lowercase() ?: "summarize"
        val instruction = params["instruction"].orEmpty().trim()
        val appLabel = friendlyAppName(snapshot.packageName)

        val systemPrompt = when (mode) {
            "reply" -> """
                You are Moki, a concise on-device AI assistant.
                Read the visible conversation or email from $appLabel and write ONLY the ready-to-send reply text.
                Do not include subject lines, quotes, greetings like "Here is a reply:", or markdown formatting.
                Keep it natural, polite, and under 3 sentences.
            """.trimIndent()

            else -> """
                You are Moki, a concise on-device AI voice assistant.
                Summarize what is visible on the user's $appLabel screen in 2 to 3 crisp, natural spoken sentences.
                Do not use bullet points, markdown, or introductory filler.
            """.trimIndent()
        }

        val userPrompt = buildString {
            if (instruction.isNotEmpty()) {
                append("User goal: $instruction\n\n")
            }
            append("Active app: $appLabel (${snapshot.packageName})\n")
            append("Visible screen text:\n${snapshot.visibleText}")
        }

        val generated = runInference(systemPrompt, userPrompt)
            ?: return SkillExecutionResult(
                success = false,
                spokenResponse = "I captured the screen from $appLabel, but my local AI model isn't ready yet. Download Gemma in Setup or enable cloud escalation."
            )

        val cleanOutput = generated
            .replace(Regex("""^["']|["']$"""), "")
            .trim()

        if (mode == "reply") {
            val pasted = ScreenContextReader.pasteIntoActiveInput(cleanOutput)
            return if (pasted) {
                SkillExecutionResult(
                    success = true,
                    spokenResponse = "I drafted a reply and pasted it into $appLabel for you: $cleanOutput"
                )
            } else {
                SkillExecutionResult(
                    success = true,
                    spokenResponse = "Tap the reply box first next time so I can paste it, but here is your draft: $cleanOutput"
                )
            }
        }

        return SkillExecutionResult(
            success = true,
            spokenResponse = cleanOutput
        )
    }

    private suspend fun runInference(systemPrompt: String, userPrompt: String): String? {
        geminiNanoEngine.generate(systemPrompt, userPrompt)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        liteRtLmEngine.generate(systemPrompt, userPrompt)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val settings = settingsRepository.settingsFlow.first()
        if (settings.escalationMode != EscalationMode.LOCAL_ONLY) {
            cloudEscalationEngine.escalate(systemPrompt, userPrompt)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return null
    }

    private fun friendlyAppName(pkg: String): String = when {
        pkg.contains("chrome") -> "Chrome"
        pkg.contains("gm") || pkg.contains("gmail") -> "Gmail"
        pkg.contains("orca") -> "Messenger"
        pkg.contains("youtube") -> "YouTube"
        pkg.contains("whatsapp") -> "WhatsApp"
        pkg.contains("telegram") -> "Telegram"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}