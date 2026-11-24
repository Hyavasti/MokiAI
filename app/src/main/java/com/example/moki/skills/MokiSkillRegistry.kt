package com.example.moki.skills

class MokiSkillRegistry {
    private val skills = LinkedHashMap<String, MokiSkill>()

    fun register(skill: MokiSkill) {
        skills[skill.name.lowercase()] = skill
    }

    fun getSkill(name: String): MokiSkill? = skills[name.lowercase()]

    fun getAllSkills(): List<MokiSkill> = skills.values.toList()

    /**
     * Filters relevant tools per turn so the local small LLM prompt stays compact and accurate.
     */
    fun filterRelevantSkills(utterance: String, maxTools: Int = 5): List<MokiSkill> {
        val lower = utterance.lowercase()
        val matched = skills.values.filter { skill ->
            skill.triggerKeywords.any { kw -> lower.contains(kw) }
        }
        return if (matched.isNotEmpty()) {
            matched.take(maxTools)
        } else {
            emptyList()
        }
    }

    fun buildPromptToolSpec(relevantSkills: List<MokiSkill>): String {
        if (relevantSkills.isEmpty()) return "No device tools needed for this query. Answer conversationally."
        return relevantSkills.joinToString("\n") { skill ->
            "- tool=\"${skill.name}\": ${skill.description} | params: ${skill.parameterSchema}"
        }
    }
}