package com.example.moki.brain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.moki.data.preferences.EscalationMode
import com.example.moki.data.preferences.MokiSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tier 2: Optional escalation (only used when user explicitly enables Gemini App handoff or Cloud API).
 */
class CloudEscalationEngine(
    private val context: Context,
    private val settingsRepository: MokiSettingsRepository
) {

    suspend fun escalate(systemPrompt: String, userQuery: String): String? {
        val settings = settingsRepository.settingsFlow.first()
        return when (settings.escalationMode) {
            EscalationMode.LOCAL_ONLY -> null
            EscalationMode.GEMINI_APP_HANDOFF -> launchGeminiAppHandoff(userQuery)
            EscalationMode.CLOUD_API -> callGeminiRestApi(settings.geminiApiKey, systemPrompt, userQuery)
        }
    }

    private fun launchGeminiAppHandoff(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                setPackage("com.google.android.apps.bard")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Handing that over to the Gemini app for a deeper look."
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://gemini.google.com/app?q=${Uri.encode(query)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            "Opening Gemini in your browser for that one."
        }
    }

    private suspend fun callGeminiRestApi(
        apiKey: String,
        systemPrompt: String,
        userQuery: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "$systemPrompt\n\nUser: $userQuery"))
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = JSONObject(response.body?.string().orEmpty())
                body.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            }
        } catch (_: Exception) {
            null
        }
    }
}