package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

data class ParsedUtterance(
    val original: String,
    val stripped: String,
    val text: String,
    val fullLower: String
)

interface DeterministicRule {
    fun match(input: ParsedUtterance): DeterministicMatch?
}

object MessageTextNormalizer {
    fun normalizeDirectMessage(raw: String): String {
        var msg = raw.trim()

        msg = msg.replace(
            Regex(
                """^(?:(?:please|can\s+you|could\s+you)\s+)?(?:ask(?:\s+(?:him|her|them))?\s+(?:if|whether)\s+|ask(?:\s+(?:him|her|them))?\s+|tell(?:\s+(?:him|her|them))?\s+(?:that\s+)?|say(?:\s+that)?\s+)""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()

        val replacements = listOf(
            Regex("""\b(?:he|she)\s+is\b""", RegexOption.IGNORE_CASE) to "you are",
            Regex("""\b(?:he's|she's)\b""", RegexOption.IGNORE_CASE) to "you're",
            Regex("""\b(?:is|was)\s+(?:he|she)\b""", RegexOption.IGNORE_CASE) to "are you",
            Regex("""\b(?:does|did)\s+(?:he|she)\b""", RegexOption.IGNORE_CASE) to "do you",
            Regex("""\b(?:has|had)\s+(?:he|she)\b""", RegexOption.IGNORE_CASE) to "have you",
            Regex("""\b(?:can|could|will|would)\s+(?:he|she)\b""", RegexOption.IGNORE_CASE) to "can you",
            Regex("""\b(?:his|her)\b""", RegexOption.IGNORE_CASE) to "your",
            Regex("""\b(?:him|her)\b""", RegexOption.IGNORE_CASE) to "you",
            Regex("""\b(?:he|she)\b""", RegexOption.IGNORE_CASE) to "you"
        )

        for ((pattern, replacement) in replacements) {
            msg = msg.replace(pattern, replacement)
        }

        return msg.replace(Regex("""\bare\s+you\s+(?:is|are)\b""", RegexOption.IGNORE_CASE), "are you").trim()
    }
}