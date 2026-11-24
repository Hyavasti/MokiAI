package com.example.moki.actions

import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class GreetingAction : MokiSkill {
    override val name = "greeting"
    override val description = "Respond instantly to casual greetings."
    override val parameterSchema = """{}"""
    override val triggerKeywords = listOf("hi", "hello", "hey", "yo", "sup")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        return SkillExecutionResult(true, "Yo, what's up? What do you need?")
    }
}