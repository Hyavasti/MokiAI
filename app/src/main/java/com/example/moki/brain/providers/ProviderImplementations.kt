package com.example.moki.brain.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SharedCloudHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
}

class GeminiProvider : CloudLlmProvider {
    override val type = CloudProviderType.GEMINI

    override suspend fun generate(
        apiKey: String,
        modelOverride: String,
        systemPrompt: String,
        userQuery: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val model = modelOverride.ifBlank { type.defaultModel }
        try {
            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", userQuery)))
                }))
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody(SharedCloudHttp.jsonMediaType))
                .build()

            SharedCloudHttp.client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val json = JSONObject(res.body?.string().orEmpty())
                json.getJSONArray("candidates")
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

class OpenAiCompatibleProvider(
    override val type: CloudProviderType,
    private val endpointUrl: String
) : CloudLlmProvider {

    override suspend fun generate(
        apiKey: String,
        modelOverride: String,
        systemPrompt: String,
        userQuery: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val model = modelOverride.ifBlank { type.defaultModel }
        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userQuery))
                })
                put("max_tokens", 512)
            }
            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(SharedCloudHttp.jsonMediaType))
                .build()

            SharedCloudHttp.client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val json = JSONObject(res.body?.string().orEmpty())
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        } catch (_: Exception) {
            null
        }
    }
}

class ClaudeProvider : CloudLlmProvider {
    override val type = CloudProviderType.CLAUDE

    override suspend fun generate(
        apiKey: String,
        modelOverride: String,
        systemPrompt: String,
        userQuery: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val model = modelOverride.ifBlank { type.defaultModel }
        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 512)
                put("system", systemPrompt)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", userQuery)
                ))
            }
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody(SharedCloudHttp.jsonMediaType))
                .build()

            SharedCloudHttp.client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val json = JSONObject(res.body?.string().orEmpty())
                json.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            }
        } catch (_: Exception) {
            null
        }
    }
}

object CloudProviderRegistry {
    private val providers: Map<CloudProviderType, CloudLlmProvider> = mapOf(
        CloudProviderType.GEMINI to GeminiProvider(),
        CloudProviderType.OPENAI to OpenAiCompatibleProvider(
            CloudProviderType.OPENAI,
            "https://api.openai.com/v1/chat/completions"
        ),
        CloudProviderType.CLAUDE to ClaudeProvider(),
        CloudProviderType.DEEPSEEK to OpenAiCompatibleProvider(
            CloudProviderType.DEEPSEEK,
            "https://api.deepseek.com/chat/completions"
        ),
        CloudProviderType.GROQ to OpenAiCompatibleProvider(
            CloudProviderType.GROQ,
            "https://api.groq.com/openai/v1/chat/completions"
        )
    )

    fun get(type: CloudProviderType): CloudLlmProvider =
        providers[type] ?: providers.getValue(CloudProviderType.GEMINI)
}