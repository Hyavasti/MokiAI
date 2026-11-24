package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class DeviceStatusRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.text

        if (text.contains("battery") || text.contains("charge level") || text.contains("battery percentage")) {
            return DeterministicMatch("device_status", mapOf("query" to "battery"))
        }
        if (Regex("""\bwhat(?:'s|\s+is)?\s+(?:the\s+)?time\b""").containsMatchIn(text)) {
            return DeterministicMatch("device_status", mapOf("query" to "time"))
        }
        if (Regex("""\bwhat(?:'s|\s+is)?\s+(?:the\s+|today'?s\s+)?date\b""").containsMatchIn(text)) {
            return DeterministicMatch("device_status", mapOf("query" to "date"))
        }
        if (Regex("""\b(?:who|check|read|show)\b.*\b(?:text|texted|sms|messaged|messages|notifications?)\b""").containsMatchIn(text) &&
            !text.contains("send")
        ) {
            return DeterministicMatch("device_status", mapOf("query" to "recent_messages"))
        }
        if (Regex("""\b(?:dnd|do\s+not\s+disturb)\b""").containsMatchIn(text)) {
            val turnOff = Regex("""\b(off|disable|stop|turn\s+off|turn\s+of)\b""").containsMatchIn(text)
            return DeterministicMatch("device_status", mapOf("query" to "dnd", "state" to if (turnOff) "off" else "on"))
        }
        return null
    }
}