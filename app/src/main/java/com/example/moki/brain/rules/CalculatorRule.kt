package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch
import java.util.Locale

class CalculatorRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        val mathMatch = Regex(
            """^(?:calculate|compute|solve|what(?:'s|\s+is)|add)?\s*(-?\d+(?:\.\d+)?)\s*(plus|\+|minus|-|times|x|\*|multiplied\s+by|divided\s+by|/)\s*(-?\d+(?:\.\d+)?)\??$""",
            RegexOption.IGNORE_CASE
        ).find(input.stripped) ?: return null

        val a = mathMatch.groupValues[1].toDoubleOrNull() ?: return null
        val op = mathMatch.groupValues[2].lowercase().trim()
        val b = mathMatch.groupValues[3].toDoubleOrNull() ?: return null

        val res = when (op) {
            "plus", "+" -> a + b
            "minus", "-" -> a - b
            "times", "x", "*", "multiplied by" -> a * b
            "divided by", "/" -> if (b != 0.0) a / b else Double.NaN
            else -> Double.NaN
        }
        if (res.isNaN()) return null

        val formatted = if (res % 1.0 == 0.0) {
            res.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", res)
        }
        return DeterministicMatch("calculator", mapOf("answer" to formatted))
    }
}