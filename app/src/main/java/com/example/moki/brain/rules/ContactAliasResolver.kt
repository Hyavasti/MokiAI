package com.example.moki.brain.rules

object ContactAliasResolver {

    // Map canonical contact names -> all nicknames & common speech-to-text mishearings
    private val aliases: Map<String, Set<String>> = mapOf(
        "Anne Fornillos" to setOf(
            "anne",
            "ann",
            "an",
            "han",
            "anne fornillos",
            "ann fornillos",
            "an fornillos",
            "anne fornilos",
            "ann fornilos",
            "and fornillos",
            "anne fornelos",
            "ann fornelos"
        ),
        "Jr Saludo" to setOf(
            "jr",
            "j r",
            "jr saludo",
            "junior",
            "junior saludo"
        )
    )

    fun resolve(rawRecipient: String): String {
        val cleaned = rawRecipient
            .lowercase()
            .replace(Regex("""[.,!?]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isEmpty()) return rawRecipient.trim()

        // 1. Exact alias match ("ann", "anne", "an fornillos", etc.)
        for ((canonicalName, variants) in aliases) {
            if (cleaned in variants) {
                return canonicalName
            }
        }

        // 2. Fuzzy phonetic match if speech recognizer hears "anne <anything starting with forn/for>"
        if (Regex("""^(?:anne|ann|an|han|and)\s+forn\w*$""").matches(cleaned)) {
            return "Anne Fornillos"
        }

        return rawRecipient.trim()
    }
}