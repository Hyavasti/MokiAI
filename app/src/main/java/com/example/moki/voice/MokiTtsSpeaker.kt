package com.example.moki.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class MokiTtsSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val completionCallbacks = mutableMapOf<String, () -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(1.02f)
                tts?.setSpeechRate(1.04f)
                isReady = true
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { id ->
                    completionCallbacks.remove(id)?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { id ->
                    completionCallbacks.remove(id)?.invoke()
                }
            }
        })
    }

    fun applyTuning(pitch: Float, rate: Float) {
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady || tts == null) {
            onDone?.invoke()
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            completionCallbacks[utteranceId] = onDone
        }
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        completionCallbacks.clear()
        tts?.stop()
    }

    fun shutdown() {
        completionCallbacks.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}