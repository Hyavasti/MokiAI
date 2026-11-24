package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class SmsRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.text
        val stripped = input.stripped
        val fullLower = input.fullLower

        val isSmsIntent = fullLower.contains("sms") ||
                fullLower.contains("messages") ||
                Regex("""\b(?:text|i-?text|pag-?text)\b""").containsMatchIn(text)
        if (!isSmsIntent) return null

        val smsMatch = Regex(
            """^(?:send\s+(?:an?\s+)?(?:sms|text(?:\s+message)?)\s+to|send\s+to|i-?text(?:\s+mo)?|pag-?text|text)\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9 +._-]+?)(?:\s+(?:later|at)\s+(\d{1,2})(?::(\d{2}))?\s*(?:am|pm)?)?\s*(?:\band\s+)?(?:say\s+that|saying|say|tell\s+(?:him|her|them)\s+(?:that\s+)?|that|with|:|,)\s*["']?([^"']+)["']?$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)

        if (smsMatch != null) {
            val recipient = smsMatch.groupValues[1].trim()
            val hourStr = smsMatch.groupValues[2].trim()
            val minStr = smsMatch.groupValues[3].trim()
            val body = MessageTextNormalizer.normalizeDirectMessage(smsMatch.groupValues[4])
            val params = mutableMapOf("recipient" to recipient, "message" to body)
            if (hourStr.isNotEmpty()) {
                params["send_at_hour"] = hourStr
                params["send_at_minute"] = minStr.ifEmpty { "0" }
            }
            return DeterministicMatch("send_sms", params)
        }

        val directSmsMatch = Regex(
            """^(?:i-?text(?:\s+mo)?|pag-?text|text)\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9._-]+(?:\s+[a-zA-Z0-9._-]+)?)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)

        if (directSmsMatch != null) {
            val recipient = directSmsMatch.groupValues[1].trim()
            val body = MessageTextNormalizer.normalizeDirectMessage(directSmsMatch.groupValues[2])
            if (recipient.isNotEmpty() && body.isNotEmpty()) {
                return DeterministicMatch("send_sms", mapOf("recipient" to recipient, "message" to body))
            }
        }

        val bareSmsMatch = Regex(
            """^(?:kahit\s+)?(?:send\s+(?:an?\s+)?(?:sms|text(?:\s+message)?)|i-?text(?:\s+mo)?|pag-?text|text)(?:\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9 +._-]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)

        if (bareSmsMatch != null) {
            val recipient = bareSmsMatch.groupValues[1].trim()
            return DeterministicMatch("send_sms", mapOf("recipient" to recipient, "message" to ""))
        }

        return null
    }
}