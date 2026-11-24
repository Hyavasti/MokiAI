package com.example.moki.voice

sealed class VoiceState {
    data object Idle : VoiceState()
    data object WakeWordArmed : VoiceState()
    data class SpeakingAck(val phrase: String) : VoiceState()
    data class ListeningCommand(val partialText: String = "") : VoiceState()
    data object Thinking : VoiceState()
    data class SpeakingResponse(val responseText: String) : VoiceState()
}