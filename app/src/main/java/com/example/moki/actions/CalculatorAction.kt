package com.example.moki.actions

import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class CalculatorAction : MokiSkill {
    override val name = "calculator"
    override val description = "Solve quick arithmetic calculations."
    override val parameterSchema = """{"answer": "string"}"""
    override val triggerKeywords = listOf("calculate", "plus", "minus", "times", "divided by", "add")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val answer = params["answer"]?.trim().orEmpty()
        return if (answer.isNotEmpty()) {
            SkillExecutionResult(true, "That's $answer.")
        } else {
            SkillExecutionResult(false, "I couldn't calculate that.")
        }
    }
}