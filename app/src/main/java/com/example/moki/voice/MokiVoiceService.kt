package com.example.moki.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.moki.MainActivity
import com.example.moki.MokiApplication
import com.example.moki.brain.MokiPersonaPrompt
import com.example.moki.brain.PendingSkillConfirmation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class MokiVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wakeDetector: SpeechWakeDetector
    private lateinit var speechCapture: SpeechCaptureManager
    private lateinit var ttsSpeaker: MokiTtsSpeaker
    private var followUpCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Standby • Say \"Hey Moki\""))

        ttsSpeaker = MokiTtsSpeaker(this)
        speechCapture = SpeechCaptureManager(this)
        wakeDetector = SpeechWakeDetector(this) { inlineCommand ->
            followUpCount = 0
            if (!inlineCommand.isNullOrBlank()) {
                handleUserUtterance(inlineCommand)
            } else {
                onWakeWordTriggered(skipAck = false)
            }
        }

        serviceScope.launch {
            val settings = MokiApplication.instance.settingsRepository.settingsFlow.first()
            ttsSpeaker.applyTuning(settings.ttsPitch, settings.ttsSpeechRate)
            armWakeWord()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_TURN -> {
                followUpCount = 0
                onWakeWordTriggered(skipAck = false)
            }
            ACTION_LISTEN_DIRECT -> {
                followUpCount = 0
                wakeDetector.pauseForActiveTurn()
                beginCommandListening()
            }
            ACTION_STOP_LOOP -> {
                followUpCount = 0
                ttsSpeaker.stop()
                speechCapture.stopListening()
                serviceScope.launch {
                    delay(600L)
                    armWakeWord()
                }
            }
            ACTION_DISABLE_SERVICE -> {
                followUpCount = 0
                wakeDetector.stop()
                speechCapture.stopListening()
                ttsSpeaker.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeDetector.stop()
        speechCapture.stopListening()
        ttsSpeaker.shutdown()
        serviceScope.cancel()
        _voiceState.value = VoiceState.Idle
        super.onDestroy()
    }

    private fun onWakeWordTriggered(skipAck: Boolean = false) {
        wakeDetector.pauseForActiveTurn()
        if (skipAck) {
            beginCommandListening()
            return
        }

        val ackPhrase = MokiPersonaPrompt.randomWakeAck()
        _voiceState.value = VoiceState.SpeakingAck(ackPhrase)

        ttsSpeaker.speak(ackPhrase) {
            beginCommandListening()
        }
    }

    private fun beginCommandListening() {
        _voiceState.value = VoiceState.ListeningCommand("")
        speechCapture.startListening(
            onPartialResult = { partial ->
                _voiceState.value = VoiceState.ListeningCommand(partial)
            },
            onFinalResult = { finalUtterance ->
                handleUserUtterance(finalUtterance)
            },
            onNoSpeechOrError = {
                followUpCount = 0
                serviceScope.launch {
                    delay(600L)
                    armWakeWord()
                }
            }
        )
    }

    private fun handleUserUtterance(utterance: String) {
        wakeDetector.pauseForActiveTurn()
        val normalized = utterance.lowercase().replace(Regex("""[.,!?]"""), "").trim()

        // 1. Instant voice STOP command — exits active listening immediately without waking Gemma-3
        if (normalized in setOf(
                "stop", "stop listening", "go to sleep", "sleep", "shut up",
                "be quiet", "quiet", "standby", "pause listening"
            )
        ) {
            followUpCount = 0
            ttsSpeaker.stop()
            speechCapture.stopListening()
            MokiApplication.instance.brainRouter.dismissPendingAction()
            clearConfirmationState()
            serviceScope.launch {
                delay(600L)
                armWakeWord()
            }
            return
        }

        // 2. Instant voice CONVERSATION RESET — clears Gemma-3 memory & returns to standby
        if (normalized in setOf(
                "reset", "reset chat", "reset conversation", "clear chat",
                "clear history", "new topic", "start over", "forget everything"
            )
        ) {
            followUpCount = 0
            serviceScope.launch {
                val app = MokiApplication.instance
                app.brainRouter.dismissPendingAction()
                clearConfirmationState()
                app.database.chatMessageDao().clearHistory()
                val reply = "Conversation reset. Say Hey Moki whenever you're ready."
                _voiceState.value = VoiceState.SpeakingResponse(reply)
                ttsSpeaker.speak(reply) {
                    serviceScope.launch {
                        delay(600L)
                        armWakeWord()
                    }
                }
            }
            return
        }

        _voiceState.value = VoiceState.Thinking
        serviceScope.launch {
            val router = MokiApplication.instance.brainRouter
            val result = router.processUserTurn(utterance, isForeground = true)
            _pendingConfirmation.value = result.pendingConfirmation

            _voiceState.value = VoiceState.SpeakingResponse(result.spokenReply)
            ttsSpeaker.speak(result.spokenReply) {
                serviceScope.launch {
                    val settings = MokiApplication.instance.settingsRepository.settingsFlow.first()
                    if (result.pendingConfirmation != null) {
                        // Always listen for "yes" / "no" when a confirmation card is active
                        beginFollowUpWindow()
                    } else if (settings.continuousFollowUp && followUpCount < 1) {
                        // Cap at 1 automatic follow-up per wake so background noise never loops infinitely
                        followUpCount++
                        beginFollowUpWindow()
                    } else {
                        followUpCount = 0
                        delay(600L)
                        armWakeWord()
                    }
                }
            }
        }
    }

    private fun beginFollowUpWindow() {
        _voiceState.value = VoiceState.ListeningCommand("")
        speechCapture.startListening(
            onPartialResult = { partial ->
                _voiceState.value = VoiceState.ListeningCommand(partial)
            },
            onFinalResult = { followUp ->
                handleUserUtterance(followUp)
            },
            onNoSpeechOrError = {
                followUpCount = 0
                serviceScope.launch {
                    delay(600L)
                    armWakeWord()
                }
            }
        )
    }

    private fun armWakeWord() {
        wakeDetector.start()
        wakeDetector.resumeAfterTurn()
        _voiceState.value = VoiceState.WakeWordArmed
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Moki Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Moki is active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "moki_voice_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TRIGGER_TURN = "com.example.moki.TRIGGER_TURN"
        const val ACTION_LISTEN_DIRECT = "com.example.moki.LISTEN_DIRECT"
        const val ACTION_STOP_LOOP = "com.example.moki.STOP_LOOP"
        const val ACTION_DISABLE_SERVICE = "com.example.moki.DISABLE_SERVICE"

        private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
        val voiceState: StateFlow<VoiceState> = _voiceState

        private val _pendingConfirmation = MutableStateFlow<PendingSkillConfirmation?>(null)
        val pendingConfirmation: StateFlow<PendingSkillConfirmation?> = _pendingConfirmation

        fun clearConfirmationState() {
            _pendingConfirmation.value = null
        }

        fun startService(context: Context, action: String? = null) {
            val intent = Intent(context, MokiVoiceService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}