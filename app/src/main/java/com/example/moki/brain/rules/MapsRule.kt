package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class MapsRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.text
        val mapsDestMatch = Regex(
            """(?:let'?s\s+go\s+to|go\s+to|navigate\s+to|directions\s+to|take\s+me\s+to)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(input.stripped)

        if (mapsDestMatch != null) {
            val dest = mapsDestMatch.groupValues[1]
                .replace(Regex("""\b(?:open|on|in|using)\s+(?:google\s+)?maps?\b.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (dest.isNotEmpty()) {
                return DeterministicMatch("maps_navigation", mapOf("command" to "navigate", "destination" to dest))
            }
        }
        if (text.contains("avoid toll")) {
            return DeterministicMatch("maps_navigation", mapOf("command" to "avoid_tolls"))
        }
        if (text.contains("traffic") && (text.contains("map") || text.contains("route") || text.contains("ahead"))) {
            return DeterministicMatch("maps_navigation", mapOf("command" to "traffic"))
        }
        return null
    }
}