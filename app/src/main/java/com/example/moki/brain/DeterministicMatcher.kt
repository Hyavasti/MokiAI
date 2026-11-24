package com.example.moki.brain

import com.example.moki.brain.rules.*

data class DeterministicMatch(
    val toolName: String,
    val params: Map<String, String>
)

class DeterministicMatcher(
    private val rules: List<DeterministicRule> = listOf(
        GreetingRule(),
        DeviceStatusRule(),
        CalculatorRule(),
        MessengerRule(),
        MapsRule(),
        NotesRule(),
        SmsRule(),
        SystemControlRule()
    )
) {
    private var pendingTool: String? = null
    private var pendingRecipient: String? = null

    fun match(utterance: String): DeterministicMatch? {
        val original = utterance.trim()
        val lowerOriginal = original.lowercase()

        if (lowerOriginal in setOf("cancel", "never mind", "nevermind", "stop", "forget it")) {
            clearPendingSlot()
            return null
        }

        val parsed = parseUtterance(original)
        val freshMatch = rules.firstNotNullOfOrNull { it.match(parsed) }

        if (freshMatch != null) {
            val isIncompleteMessage = (freshMatch.toolName == "send_messenger" || freshMatch.toolName == "send_sms") &&
                    freshMatch.params["recipient"].orEmpty().isNotEmpty() &&
                    freshMatch.params["message"].isNullOrEmpty() &&
                    freshMatch.params["action"].isNullOrEmpty()

            if (isIncompleteMessage) {
                pendingTool = freshMatch.toolName
                pendingRecipient = freshMatch.params["recipient"]
            } else {
                clearPendingSlot()
            }
            return freshMatch
        }

        val waitingTool = pendingTool
        val waitingRecipient = pendingRecipient
        if (waitingTool != null && !waitingRecipient.isNullOrBlank()) {
            clearPendingSlot()
            val cleanMessage = MessageTextNormalizer.normalizeDirectMessage(original)
            if (cleanMessage.isNotEmpty()) {
                return DeterministicMatch(
                    waitingTool,
                    mapOf("recipient" to waitingRecipient, "message" to cleanMessage)
                )
            }
        }

        return null
    }

    private fun parseUtterance(original: String): ParsedUtterance {
        val noPolitePrefix = original.replace(
            Regex(
                """^(?:(?:no[,\s]+)?(?:i\s+(?:want|need)\s+you\s+to\s+)?)?(?:(?:hey\s+moki|moki)[,\s]*)?(?:(?:can|could|would|will)\s+you\s+|please\s+|now\s+)+""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).replace(
            Regex("""^(?:no[,\s]+)?i\s+(?:want|need)\s+you\s+to\s+""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        val stripped = noPolitePrefix.replace(
            Regex("""^(?:open|launch)\s+[a-z0-9 ._]+(?:\s+(?:then|the|and)\s+|,\s*)""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        return ParsedUtterance(
            original = original,
            stripped = stripped,
            text = stripped.lowercase(),
            fullLower = original.lowercase()
        )

    }

    private fun clearPendingSlot() {
        pendingTool = null
        pendingRecipient = null
    }
}