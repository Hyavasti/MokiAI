package com.example.moki.skills

data class SkillExecutionResult(
    val success: Boolean,
    val spokenResponse: String,
    val requiresConfirmation: Boolean = false,
    val pendingConfirmationLabel: String? = null,
    val pendingParams: Map<String, String> = emptyMap()
)

interface MokiSkill {
    val name: String
    val description: String
    val parameterSchema: String
    val triggerKeywords: List<String>
    val requiresUserConfirmation: Boolean get() = false

    suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean = false
    ): SkillExecutionResult
}