package com.example.moki.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechWakeDetector(
    private val context: Context,
    private val onWakeDetected: (inlineCommand: String?) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isPausedForTurn = false

    // Matches "hey moki", "hey maki", "timoki", "hi moki", "hey monkey", "hey mokey", "hey mookie", "moki"
    private val wakeRegex = Regex(
        """^(?:(?:hey|hi|yo|oy|ok|okay|ti|te|hey\s+there)\s*)?(?:moki|maki|mokey|mookie|moky|mocky|monkey|timoki|himoki)\b[,\s.?!]*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    fun start() {
        if (isRunning) return
        isRunning = true
        isPausedForTurn = false
        scheduleRestart(150L)
    }

    fun pauseForActiveTurn() {
        isPausedForTurn = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { destroyRecognizer() }
    }

    fun resumeAfterTurn() {
        if (!isRunning) return
        isPausedForTurn = false
        scheduleRestart(750L)
    }

    fun stop() {
        isRunning = false
        isPausedForTurn = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { destroyRecognizer() }
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        if (!isRunning || isPausedForTurn) return
        mainHandler.postDelayed({ startListeningOnce() }, delayMs)
    }

    private fun startListeningOnce() {
        if (!isRunning || isPausedForTurn) return
        destroyRecognizer()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("SpeechWakeDetector", "SpeechRecognizer unavailable")
            return
        }

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        val candidates = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty()
                        checkWakeCandidates(candidates, isFinal = false)
                    }

                    override fun onResults(results: Bundle?) {
                        val candidates = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty()
                        if (!checkWakeCandidates(candidates, isFinal = true)) {
                            scheduleRestart(250L)
                        }
                    }

                    override fun onError(error: Int) {
                        val backoff = when (error) {
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                            SpeechRecognizer.ERROR_CLIENT -> 850L
                            else -> 300L
                        }
                        scheduleRestart(backoff)
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10_000L)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                startListening(intent)
            }
        } catch (e: Exception) {
            Log.e("SpeechWakeDetector", "Wake start failed", e)
            scheduleRestart(850L)
        }
    }

    private fun checkWakeCandidates(candidates: List<String>, isFinal: Boolean): Boolean {
        if (candidates.isEmpty() || isPausedForTurn) return false

        for (raw in candidates) {
            val heard = raw.trim()
            if (heard.isEmpty()) continue
            val match = wakeRegex.find(heard) ?: continue
            val trailingCommand = match.groupValues[1].trim()

            if (trailingCommand.isEmpty()) {
                pauseForActiveTurn()
                onWakeDetected(null)
                return true
            } else if (isFinal) {
                pauseForActiveTurn()
                onWakeDetected(trailingCommand)
                return true
            }
        }
        return false
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            recognizer = null
        }
    }
}