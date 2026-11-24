package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class MessengerRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val text = input.text
        val stripped = input.stripped
        val fullLower = input.fullLower

        val isVideoCall = Regex("""\bvideo\s*call\b""").containsMatchIn(text)
        val isMessengerCall = fullLower.contains("messenger") || isVideoCall

        if (isMessengerCall && Regex("""\b(?:call|dial|ring)\b""").containsMatchIn(text)) {
            val callTargetMatch = Regex(
                """(?:video\s*call|messenger\s*call|call|dial|ring)\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9 ._-]+?)(?:\s+(?:on|via|through|in|sa)\s+messenger)?$""",
                RegexOption.IGNORE_CASE
            ).find(stripped)
            val target = ContactAliasResolver.resolve(callTargetMatch?.groupValues?.get(1)?.trim().orEmpty())
            return DeterministicMatch(
                "send_messenger",
                mapOf(
                    "action" to if (isVideoCall) "video_call" else "audio_call",
                    "recipient" to target
                )
            )
        }

        val isMessengerIntent = fullLower.contains("messenger") ||
                Regex("""\b(?:chat|i-?chat|pm)\b""").containsMatchIn(text)
        if (!isMessengerIntent) return null

        // Case A: Explicit connector ("chat anne fornillos in messenger and say goodnight")
        val withConnector = Regex(
            """^(?:send\s+(?:a\s+)?(?:message|chat)\s+to|i-?chat(?:\s+mo)?|chat|message|pm|tell)\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9 ._-]+?)\s*(?:(?:on|in|via|sa)\s+messenger\s*)?(?:\band\s+)?(?:say\s+that|saying|say|tell\s+(?:him|her|them)\s+(?:that\s+)?|that|with|:|,)\s*["']?([^"']+)["']?$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)

        if (withConnector != null) {
            val recipient = ContactAliasResolver.resolve(withConnector.groupValues[1].trim())
            val msg = MessageTextNormalizer.normalizeDirectMessage(withConnector.groupValues[2])
            if (recipient.isNotEmpty() && msg.isNotEmpty()) {
                return DeterministicMatch("send_messenger", mapOf("recipient" to recipient, "message" to msg))
            }
        }

        // Case B: Direct command with 3+ words after trigger ("chat anne ask if she is on her way")
        val directMatch = Regex(
            """^(?:send\s+(?:a\s+)?(?:message|chat)\s+to|i-?chat(?:\s+mo)?|chat|message|pm|tell)\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9._-]+(?:\s+[a-zA-Z0-9._-]+)?)\s+(\S+\s+.+)$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)

        if (directMatch != null) {
            val recipient = ContactAliasResolver.resolve(directMatch.groupValues[1].trim())
            val msg = MessageTextNormalizer.normalizeDirectMessage(directMatch.groupValues[2])
            if (recipient.isNotEmpty() && msg.isNotEmpty()) {
                return DeterministicMatch("send_messenger", mapOf("recipient" to recipient, "message" to msg))
            }
        }

        // Case C: Bare trigger ("can you chat" / "chat anne" without message yet)
        val bareChatMatch = Regex(
            """^(?:i-?chat(?:\s+mo)?|chat|pm)(?:\s+(?:si\s+|kay\s+|to\s+)?([a-zA-Z0-9 ._-]+?))?(?:\s+(?:on|in|via|sa)\s+messenger)?$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)

        if (bareChatMatch != null) {
            val recipient = ContactAliasResolver.resolve(bareChatMatch.groupValues[1].trim())
            return DeterministicMatch("send_messenger", mapOf("recipient" to recipient, "message" to ""))
        }

        return null
    }
}