package com.example.moki.brain.rules

import com.example.moki.brain.DeterministicMatch

class GreetingRule : DeterministicRule {
    override fun match(input: ParsedUtterance): DeterministicMatch? {
        if (input.text in setOf("hi", "hello", "hey", "yo", "moki", "hey moki", "sup")) {
            return DeterministicMatch("greeting", emptyMap())
        }
        return null
    }
}