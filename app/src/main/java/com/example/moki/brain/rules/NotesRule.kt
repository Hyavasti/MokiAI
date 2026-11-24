package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class NotesRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val stripped = input.stripped
        val text = input.text

        // 1. Explicit NEW note commands ("saying" MUST come before "say" so "saying" isn't sliced to "ing")
        val noteSaveMatch = Regex(
            """^(?:(?:set|add|create|write|make|save)\s+(?:a\s+)?(?:new\s+)?notes?(?:\s*,|\s+(?:then\s+)?(?:saying|say\s+that|say|put|that|with|:))?|take\s+a\s+note|note\s+that|remember\s+to)\s*(?:to\s+)?["']?(.+?)["']?$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)
        if (noteSaveMatch != null) {
            val content = noteSaveMatch.groupValues[1].trim()
            if (content.isNotEmpty()) {
                return DeterministicMatch("notes", mapOf("action" to "save", "content" to content))
            }
        }

        // 2. Append to an existing note:
        //    - "add food to my buy water note"
        //    - "in/under the note for school work tomorrow add neck tech"
        val noteAppendMatch = Regex(
            """^(?:(?:add\s+(?:a\s+note\s+)?)?(?:in|under|to)\s+(?:the\s+|my\s+)?(?:note\s+(?:for\s+|about\s+|titled\s+)?)?([a-zA-Z0-9 _-]+?)(?:\s+note|\s+list|\s+listing)?(?:\s+we\s+made)?\s*(?:,|\s+(?:can\s+you\s+|please\s+)?(?:add|put|append|saying|:))\s*(.+)|add\s+(.+?)\s+to\s+(?:my\s+|the\s+)([a-zA-Z0-9 _-]+?)\s*(?:note|list|listing))$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)
        if (noteAppendMatch != null) {
            val target = noteAppendMatch.groupValues[1].ifEmpty { noteAppendMatch.groupValues[4] }.trim()
            val item = noteAppendMatch.groupValues[2].ifEmpty { noteAppendMatch.groupValues[3] }.trim()
            if (item.isNotEmpty() && target.isNotEmpty()) {
                return DeterministicMatch("notes", mapOf("action" to "append", "target" to target, "content" to item))
            }
        }

        // 3. Read / list saved notes
        if (Regex("""\b(read|show|list|check)\b.*\bnotes?\b""").containsMatchIn(text)) {
            return DeterministicMatch("notes", mapOf("action" to "read"))
        }

        return null
    }
}