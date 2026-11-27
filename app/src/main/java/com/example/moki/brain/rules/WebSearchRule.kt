package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class WebSearchRule : DeterministicRule {

    private val explicitSearchRegex = Regex(
        """^(?:search(?:\s+(?:the\s+)?(?:web|online|internet|google))?\s+(?:for|about)?|look\s+up|google|find\s+(?:info|information|news)\s+(?:on|about))\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val liveFactRegex = Regex(
        """^(?:what(?:'s|\s+is|\s+are)\s+the\s+(?:latest|current|today(?:'s)?)\s+.+|who\s+won\s+.+|weather\s+(?:in|for|today)\s+.+)$""",
        RegexOption.IGNORE_CASE
    )

    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.stripped.trim().replace(Regex("""[?.!]+$"""), "")
        if (text.isEmpty()) return null

        explicitSearchRegex.matchEntire(text)?.let { match ->
            val query = match.groupValues[1].trim()
            if (query.isNotEmpty()) {
                return DeterministicMatch(
                    toolName = "web_search",
                    params = mapOf("query" to query)
                )
            }
        }

        if (liveFactRegex.matches(text)) {
            return DeterministicMatch(
                toolName = "web_search",
                params = mapOf("query" to text)
            )
        }

        return null
    }
}