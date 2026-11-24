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

class SpeechCaptureManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var lastPartialHeard: String = ""
    private var hasDeliveredResult = false
    private var isStarting = false
    private var activeOnFinal: ((String) -> Unit)? = null

    private val silenceCommitRunnable = Runnable {
        if (!hasDeliveredResult && lastPartialHeard.isNotBlank()) {
            hasDeliveredResult = true
            val committed = lastPartialHeard
            val callback = activeOnFinal
            stopListeningInternal()
            callback?.invoke(committed)
        }
    }

    private fun adaptiveSilenceDelayMs(partialText: String): Long {
        val wordCount = partialText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return when {
            wordCount <= 3 -> 850L
            wordCount <= 7 -> 1450L
            else -> 2100L
        }
    }

    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onNoSpeechOrError: () -> Unit
    ) {
        if (isStarting) return
        isStarting = true

        mainHandler.removeCallbacksAndMessages(null)
        stopListeningInternal()
        lastPartialHeard = ""
        hasDeliveredResult = false
        activeOnFinal = onFinalResult

        mainHandler.postDelayed({
            isStarting = false
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w("SpeechCaptureManager", "SpeechRecognizer not available")
                onNoSpeechOrError()
                return@postDelayed
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
                            if (hasDeliveredResult) return
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                lastPartialHeard = partial
                                onPartialResult(partial)
                                mainHandler.removeCallbacks(silenceCommitRunnable)
                                mainHandler.postDelayed(
                                    silenceCommitRunnable,
                                    adaptiveSilenceDelayMs(partial)
                                )
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            if (hasDeliveredResult) return
                            hasDeliveredResult = true
                            mainHandler.removeCallbacks(silenceCommitRunnable)
                            val finalStr = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            val bestText = finalStr.ifEmpty { lastPartialHeard }
                            stopListeningInternal()
                            if (bestText.isNotBlank()) {
                                onFinalResult(bestText)
                            } else {
                                onNoSpeechOrError()
                            }
                        }

                        override fun onError(error: Int) {
                            if (hasDeliveredResult) return
                            hasDeliveredResult = true
                            mainHandler.removeCallbacks(silenceCommitRunnable)
                            Log.w("SpeechCaptureManager", "STT error=$error, partial='$lastPartialHeard'")
                            stopListeningInternal()
                            if (lastPartialHeard.isNotBlank()) {
                                onFinalResult(lastPartialHeard)
                            } else {
                                onNoSpeechOrError()
                            }
                        }
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    }
                    startListening(intent)
                }
            } catch (e: Exception) {
                Log.e("SpeechCaptureManager", "Failed to start SpeechRecognizer", e)
                onNoSpeechOrError()
            }
        }, 300L)
    }

    fun stopListening() {
        isStarting = false
        activeOnFinal = null
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { stopListeningInternal() }
    }

    private fun stopListeningInternal() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            recognizer = null
        }
    }
}