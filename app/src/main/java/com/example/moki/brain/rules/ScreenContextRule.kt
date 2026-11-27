package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class ScreenContextRule : DeterministicRule {

    private val replyRegex = Regex(
        """^(?:draft\s+(?:a\s+)?)?(?:reply|respond)(?:\s+to\s+(?:this|the)\s+(?:email|message|chat|thread|comment|post))?(?:\s+(?:saying|with|that)\s+(.+))?$""",
        RegexOption.IGNORE_CASE
    )

    private val summarizeRegex = Regex(
        """^(?:please\s+)?(?:summarize|summarise|recap|tldr|tl;dr)(?:\s+(?:this|the|my|it))?(?:\s+(?:screen|page|article|email|thread|chat|video|window|content|text))?$""",
        RegexOption.IGNORE_CASE
    )

    private val whatsOnScreenRegex = Regex(
        """^(?:(?:can\s+you\s+)?(?:see|read|tell\s+me)\s+)?(?:what(?:'s|\s+is)\s+on\s+(?:my\s+|the\s+)?screen|what\s+i(?:'m|\s+am)\s+(?:looking\s+at|reading|viewing)|read\s+(?:my\s+|this\s+|the\s+)?(?:screen|page|article)|explain\s+(?:this\s+|the\s+)?(?:screen|page|article))$""",
        RegexOption.IGNORE_CASE
    )

    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.stripped.trim().replace(Regex("""[?.!]+$"""), "")
        if (text.isEmpty()) return null

        replyRegex.matchEntire(text)?.let { match ->
            val hint = match.groupValues.getOrNull(1)?.trim().orEmpty()
            return DeterministicMatch(
                toolName = "screen_context",
                params = mapOf(
                    "mode" to "reply",
                    "instruction" to hint
                )
            )
        }

        if (summarizeRegex.matches(text)) {
            return DeterministicMatch(
                toolName = "screen_context",
                params = mapOf(
                    "mode" to "summarize",
                    "instruction" to "Summarize the main points concisely in 2 to 3 short spoken sentences."
                )
            )
        }

        if (whatsOnScreenRegex.matches(text)) {
            return DeterministicMatch(
                toolName = "screen_context",
                params = mapOf(
                    "mode" to "describe",
                    "instruction" to "Briefly explain what is currently shown on screen in 2 short sentences."
                )
            )
        }

        return null
    }
}