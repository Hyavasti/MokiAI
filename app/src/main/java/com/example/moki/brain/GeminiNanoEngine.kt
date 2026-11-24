package com.example.moki.brain

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel

/**
 * Tier 1A: On-device Gemini Nano via ML Kit GenAI Prompt API (zero-cost when available in foreground).
 */
class GeminiNanoEngine {

    private var model: GenerativeModel? = null

    suspend fun generate(systemPrompt: String, userQuery: String): String? {
        return try {
            val client = model ?: Generation.getClient().also { model = it }
            val status = client.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                return null
            }
            val fullPrompt = "$systemPrompt\n\nUser: $userQuery\nMoki:"
            val response = client.generateContent(fullPrompt)
            response.candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}