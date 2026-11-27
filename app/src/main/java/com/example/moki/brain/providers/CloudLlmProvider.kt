package com.example.moki.brain.providers

enum class CloudProviderType(
    val displayName: String,
    val defaultModel: String,
    val hintPlaceholder: String
) {
    GEMINI("Google Gemini", "gemini-3.8-flash", "AIza..."),
    OPENAI("OpenAI", "gpt-6.1-sol", "sk-..."),
    CLAUDE("Anthropic Claude", "claude-sonnet-5-5", "sk-ant-..."),
    DEEPSEEK("DeepSeek", "deepseek-chat", "sk-..."),
    GROQ("Groq (Fast)", "llama-3.3-70b-versatile", "gsk_...")
}

interface CloudLlmProvider {
    val type: CloudProviderType
    suspend fun generate(
        apiKey: String,
        modelOverride: String,
        systemPrompt: String,
        userQuery: String
    ): String?
}