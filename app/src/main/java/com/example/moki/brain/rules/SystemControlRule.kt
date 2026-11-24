package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class SystemControlRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.text
        val stripped = input.stripped

        val callMatch = Regex("""^(?:call|dial|ring|phone)\s+([a-zA-Z0-9 +._-]+)$""", RegexOption.IGNORE_CASE).find(stripped)
        if (callMatch != null) {
            return DeterministicMatch("phone_call", mapOf("contact" to callMatch.groupValues[1].trim()))
        }

        if (text.contains("flashlight") || text.contains("torch")) {
            val turnOff = Regex("""\b(off|disable|close|stop)\b""").containsMatchIn(text)
            return DeterministicMatch("flashlight", mapOf("state" to if (turnOff) "off" else "on"))
        }

        if (Regex("""\b(lock)\b.*\b(screen|phone|device)\b""").containsMatchIn(text) ||
            text in setOf("lock screen", "power off", "turn off", "screen off", "sleep")
        ) {
            return DeterministicMatch("screen_control", mapOf("mode" to "lock"))
        }
        if (Regex("""\b(unlock)\b.*\b(screen|phone|device)\b""").containsMatchIn(text)) {
            return DeterministicMatch("screen_control", mapOf("mode" to "unlock"))
        }

        if (text.contains("alarm") || text.contains("wake me")) {
            val isCancel = Regex("""\b(cancel|delete|remove|dismiss|turn off|stop)\b""").containsMatchIn(text)
            val timeMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(text)
            if (timeMatch != null) {
                var hour = timeMatch.groupValues[1].toIntOrNull() ?: return null
                val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                val meridiem = timeMatch.groupValues[3]
                if (meridiem == "pm" && hour < 12) hour += 12
                if (meridiem == "am" && hour == 12) hour = 0
                return DeterministicMatch(
                    "set_alarm",
                    mapOf(
                        "action" to if (isCancel) "cancel" else "set",
                        "hour" to hour.toString(),
                        "minute" to minute.toString()
                    )
                )
            } else if (isCancel) {
                return DeterministicMatch("set_alarm", mapOf("action" to "cancel"))
            }
        }

        if (text.contains("weather") || text.contains("temperature") || text.contains("forecast")) {
            val cityMatch = Regex(
                """(?:weather|temperature|forecast)\s+(?:in|at|for|is)\s+([a-zA-Z\s]+?)[?.!]*$""",
                RegexOption.IGNORE_CASE
            ).find(stripped)
            val rawCity = cityMatch?.groupValues?.get(1)?.trim().orEmpty()
            val nonCityWords = setOf("today", "tonight", "tomorrow", "now", "right now", "this week", "my location", "here")
            val city = if (rawCity.lowercase() in nonCityWords) "current" else rawCity.ifEmpty { "current" }
            return DeterministicMatch("weather", mapOf("location" to city))
        }

        val openMatch = Regex("""^(?:open|launch|start)\s+([a-z0-9 ._-]+?)(?:\s+and\s+.+)?$""").find(text)
        if (openMatch != null && !text.contains(" then ") && !text.contains(" send ") && !text.contains(",")) {
            val appName = openMatch.groupValues[1].trim()
            if (appName !in listOf("file", "files", "my notes", "notes", "alarm")) {
                return DeterministicMatch("open_app", mapOf("app_name" to appName))
            }
        }

        return null
    }
}