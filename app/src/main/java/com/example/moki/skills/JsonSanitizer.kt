package com.example.moki.skills

import org.json.JSONObject

data class ParsedModelDecision(
    val isToolCall: Boolean,
    val toolName: String? = null,
    val params: Map<String, String> = emptyMap(),
    val replyText: String = ""
)

object JsonSanitizer {

    fun parseModelOutput(rawOutput: String): ParsedModelDecision {
        val cleaned = rawOutput
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        val jsonStart = cleaned.indexOf('{')
        val jsonEnd = cleaned.lastIndexOf('}')

        if (jsonStart != -1 && jsonEnd > jsonStart) {
            val candidate = cleaned.substring(jsonStart, jsonEnd + 1)
            try {
                val obj = JSONObject(candidate)
                val tool = obj.optString("tool", "").trim()
                if (tool.isNotEmpty() && !tool.equals("none", ignoreCase = true)) {
                    val paramsMap = mutableMapOf<String, String>()
                    val paramsObj = obj.optJSONObject("params")
                    if (paramsObj != null) {
                        val keys = paramsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            paramsMap[k] = paramsObj.optString(k, "")
                        }
                    }
                    return ParsedModelDecision(
                        isToolCall = true,
                        toolName = tool,
                        params = paramsMap,
                        replyText = obj.optString("reply", "")
                    )
                }
                val reply = obj.optString("reply", "")
                if (reply.isNotEmpty()) {
                    return ParsedModelDecision(isToolCall = false, replyText = reply)
                }
            } catch (_: Exception) {
                // Fall back to plain conversational text
            }
        }

        // Strip any stray JSON-looking artifacts so Moki never speaks raw syntax
        val humanSafeText = cleaned
            .replace(Regex("\\{.*\\}"), "")
            .replace(Regex("^(moki|assistant)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { "I'm right here with you. Could you say that one more time?" }

        return ParsedModelDecision(isToolCall = false, replyText = humanSafeText)
    }
}