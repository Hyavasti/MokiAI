package com.example.moki.brain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.moki.brain.providers.CloudProviderRegistry
import com.example.moki.data.preferences.EscalationMode
import com.example.moki.data.preferences.MokiSettingsRepository
import kotlinx.coroutines.flow.first

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
            EscalationMode.CLOUD_API -> {
                val activeProvider = settings.activeCloudProvider
                val apiKey = settings.apiKeyFor(activeProvider)
                if (apiKey.isBlank()) return null
                CloudProviderRegistry.get(activeProvider).generate(
                    apiKey = apiKey,
                    modelOverride = settings.cloudModelOverride,
                    systemPrompt = systemPrompt,
                    userQuery = userQuery
                )
            }
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
}