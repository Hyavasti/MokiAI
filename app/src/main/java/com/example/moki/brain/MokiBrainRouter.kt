package com.example.moki.brain

import android.content.Context
import com.example.moki.data.local.ChatMessageDao
import com.example.moki.data.local.ChatMessageEntity
import com.example.moki.data.local.TraceLogDao
import com.example.moki.data.local.TraceLogEntity
import com.example.moki.data.preferences.EscalationMode
import com.example.moki.data.preferences.MokiSettingsRepository
import com.example.moki.skills.JsonSanitizer
import com.example.moki.skills.MokiSkillRegistry
import com.example.moki.skills.SkillExecutionResult
import kotlinx.coroutines.flow.first

data class PendingSkillConfirmation(
    val skillName: String,
    val label: String,
    val params: Map<String, String>
)

data class BrainTurnResult(
    val spokenReply: String,
    val tierUsed: String,
    val pendingConfirmation: PendingSkillConfirmation? = null
)

class MokiBrainRouter(
    private val context: Context,
    private val skillRegistry: MokiSkillRegistry,
    private val deterministicMatcher: DeterministicMatcher,
    private val geminiNanoEngine: GeminiNanoEngine,
    private val liteRtLmEngine: LiteRtLmEngine,
    private val cloudEscalationEngine: CloudEscalationEngine,
    private val chatMessageDao: ChatMessageDao,
    private val traceLogDao: TraceLogDao,
    private val settingsRepository: MokiSettingsRepository
) {

    var pendingConfirmation: PendingSkillConfirmation? = null
        private set

    suspend fun processUserTurn(userUtterance: String, isForeground: Boolean = true): BrainTurnResult {
        val startMs = System.currentTimeMillis()
        val cleanInput = userUtterance.trim()

        chatMessageDao.insertMessage(
            ChatMessageEntity(role = "user", content = cleanInput, engineUsed = "user")
        )

        // Check if user is confirming or canceling a pending sensitive action (SMS/Call/Email/Messenger)
        val pending = pendingConfirmation
        if (pending != null) {
            val lower = cleanInput.lowercase().replace(Regex("""[.,!?]"""), "").trim()
            val isConfirm = lower in listOf(
                "yes", "yes confirm", "confirm", "yeah", "yep", "yes please",
                "send", "send it", "do it", "go ahead", "sure", "okay", "ok"
            ) || lower.startsWith("yes ")

            val isCancel = lower in listOf(
                "no", "no no", "nope", "nah", "cancel", "stop", "never mind",
                "nevermind", "don't", "do not", "dont send", "don't send"
            ) || lower.startsWith("no ")

            if (isConfirm) {
                pendingConfirmation = null
                val skill = skillRegistry.getSkill(pending.skillName)
                if (skill != null) {
                    val result = skill.execute(pending.params, confirmed = true)
                    return finalizeTurn(cleanInput, result.spokenResponse, "ConfirmedAction", pending.skillName, startMs, result.success)
                }
            } else if (isCancel) {
                pendingConfirmation = null
                return finalizeTurn(cleanInput, "Okay, canceled.", "ConfirmedAction", pending.skillName, startMs, true)
            } else {
                pendingConfirmation = null
            }
        }

        // TIER 0: Deterministic <5ms fast path
        val tier0 = deterministicMatcher.match(cleanInput)
        if (tier0 != null) {
            val skill = skillRegistry.getSkill(tier0.toolName)
            if (skill != null) {
                val execResult = skill.execute(tier0.params, confirmed = false)
                return handleSkillResult(cleanInput, execResult, "Tier0-Deterministic", skill.name, startMs)
            }
        }

        // Build compact context & relevant tools for local LLMs
        val relevantSkills = skillRegistry.filterRelevantSkills(cleanInput)
        val toolSpec = skillRegistry.buildPromptToolSpec(relevantSkills)
        val history = chatMessageDao.getRecentMessages(4)
            .reversed()
            .filterNot { it.content.contains("can't provide an action", ignoreCase = true) }
            .joinToString("\n") { "${if (it.role == "user") "User" else "Moki"}: ${it.content}" }

        val systemPrompt = MokiPersonaPrompt.buildSystemPrompt(
            toolSpecs = toolSpec,
            recentHistory = history,
            hasTools = relevantSkills.isNotEmpty()
        )

        // TIER 1A: On-device Gemini Nano (ML Kit GenAI Prompt API - foreground)
        if (isForeground) {
            val nanoRaw = geminiNanoEngine.generate(systemPrompt, cleanInput)
            if (!nanoRaw.isNullOrBlank()) {
                return handleLlmRawOutput(cleanInput, nanoRaw, "Tier1A-GeminiNano", startMs)
            }
        }

        // TIER 1B: Universal On-device LiteRT-LM Engine (zero-cost conversational AI)
        val liteRtRaw = liteRtLmEngine.generate(systemPrompt, cleanInput)
        if (!liteRtRaw.isNullOrBlank()) {
            return handleLlmRawOutput(cleanInput, liteRtRaw, "Tier1B-LiteRT-LM", startMs)
        }

        // TIER 2: Optional Cloud Escalation (only if user opted in)
        val settings = settingsRepository.settingsFlow.first()
        if (settings.escalationMode != EscalationMode.LOCAL_ONLY) {
            val cloudReply = cloudEscalationEngine.escalate(systemPrompt, cleanInput)
            if (!cloudReply.isNullOrBlank()) {
                return handleLlmRawOutput(cleanInput, cloudReply, "Tier2-CloudEscalation", startMs)
            }
        }

        // Graceful offline fallback if local LiteRT-LM model hasn't been downloaded yet
        val fallbackReply = if (!liteRtLmEngine.isModelDownloaded()) {
            "I can handle quick commands like your flashlight, alarms, notes, and apps right now. For open-ended conversations offline, download my local brain model in the Setup screen."
        } else {
            "I didn't quite catch how to help with that—could you rephrase it for me?"
        }
        return finalizeTurn(cleanInput, fallbackReply, "GracefulFallback", null, startMs, false)
    }

    suspend fun confirmPendingAction(): BrainTurnResult? {
        val pending = pendingConfirmation ?: return null
        pendingConfirmation = null
        val startMs = System.currentTimeMillis()
        val skill = skillRegistry.getSkill(pending.skillName) ?: return null
        val result = skill.execute(pending.params, confirmed = true)
        return finalizeTurn("Confirm ${pending.label}", result.spokenResponse, "CardConfirmed", pending.skillName, startMs, result.success)
    }

    fun dismissPendingAction() {
        pendingConfirmation = null
    }

    private suspend fun handleLlmRawOutput(
        userQuery: String,
        rawOutput: String,
        tierName: String,
        startMs: Long
    ): BrainTurnResult {
        val decision = JsonSanitizer.parseModelOutput(rawOutput)
        if (decision.isToolCall && decision.toolName != null) {
            val skill = skillRegistry.getSkill(decision.toolName)
            if (skill != null) {
                val execResult = skill.execute(decision.params, confirmed = false)
                return handleSkillResult(userQuery, execResult, tierName, skill.name, startMs)
            }
        }
        return finalizeTurn(userQuery, decision.replyText, tierName, null, startMs, true)
    }

    private suspend fun handleSkillResult(
        userQuery: String,
        result: SkillExecutionResult,
        tierName: String,
        skillName: String,
        startMs: Long
    ): BrainTurnResult {
        var pendingCard: PendingSkillConfirmation? = null
        if (result.requiresConfirmation && result.pendingConfirmationLabel != null) {
            pendingCard = PendingSkillConfirmation(
                skillName = skillName,
                label = result.pendingConfirmationLabel,
                params = result.pendingParams
            )
            pendingConfirmation = pendingCard
        }
        return finalizeTurn(
            userQuery = userQuery,
            reply = result.spokenResponse,
            tierName = tierName,
            skillName = skillName,
            startMs = startMs,
            success = result.success,
            pendingCard = pendingCard
        )
    }

    private suspend fun finalizeTurn(
        userQuery: String,
        reply: String,
        tierName: String,
        skillName: String?,
        startMs: Long,
        success: Boolean,
        pendingCard: PendingSkillConfirmation? = null
    ): BrainTurnResult {
        val latency = System.currentTimeMillis() - startMs
        chatMessageDao.insertMessage(
            ChatMessageEntity(role = "moki", content = reply, engineUsed = tierName)
        )
        traceLogDao.insertTrace(
            TraceLogEntity(
                userQuery = userQuery,
                tierSelected = tierName,
                skillInvoked = skillName,
                latencyMs = latency,
                success = success
            )
        )
        return BrainTurnResult(
            spokenReply = reply,
            tierUsed = tierName,
            pendingConfirmation = pendingCard
        )
    }
}