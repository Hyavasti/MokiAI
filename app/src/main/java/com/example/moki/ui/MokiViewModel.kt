package com.example.moki.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.moki.MokiApplication
import com.example.moki.brain.PendingSkillConfirmation
import com.example.moki.data.local.ChatMessageEntity
import com.example.moki.data.local.TraceLogEntity
import com.example.moki.data.model.ModelDownloadWorker
import com.example.moki.data.preferences.EscalationMode
import com.example.moki.data.preferences.MokiSettings
import com.example.moki.voice.MokiVoiceService
import com.example.moki.voice.VoiceState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MokiViewModel(application: Application) : AndroidViewModel(application) {

    private val app = MokiApplication.instance
    private val brainRouter = app.brainRouter
    private val settingsRepo = app.settingsRepository

    val voiceState: StateFlow<VoiceState> = MokiVoiceService.voiceState
    val servicePendingConfirmation: StateFlow<PendingSkillConfirmation?> = MokiVoiceService.pendingConfirmation

    private val _uiPendingConfirmation = MutableStateFlow<PendingSkillConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingSkillConfirmation?> =
        combine(servicePendingConfirmation, _uiPendingConfirmation) { svc, ui -> svc ?: ui }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatMessages: StateFlow<List<ChatMessageEntity>> =
        app.database.chatMessageDao().observeAllMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val traceLogs: StateFlow<List<TraceLogEntity>> =
        app.database.traceLogDao().observeRecentTraces()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<MokiSettings> =
        settingsRepo.settingsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MokiSettings())

    private val _modelDownloaded = MutableStateFlow(ModelDownloadWorker.getModelFile(application).exists())
    val modelDownloaded: StateFlow<Boolean> = _modelDownloaded

    val downloadProgress: StateFlow<Int?> =
        WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
            .map { infos ->
                val info = infos.firstOrNull()
                if (info?.state == WorkInfo.State.SUCCEEDED) {
                    _modelDownloaded.value = true
                }
                if (info?.state == WorkInfo.State.RUNNING) {
                    info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                } else {
                    null
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun sendTextPrompt(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = brainRouter.processUserTurn(text.trim(), isForeground = true)
            _uiPendingConfirmation.value = result.pendingConfirmation
            if (result.pendingConfirmation == null) {
                MokiVoiceService.clearConfirmationState()
            }
        }
    }

    fun triggerSimulatedHeyMoki() {
        MokiVoiceService.startService(getApplication(), MokiVoiceService.ACTION_TRIGGER_TURN)
    }

    fun startBackgroundWakeService() {
        viewModelScope.launch {
            settingsRepo.setWakeServiceEnabled(true)
            MokiVoiceService.startService(getApplication())
        }
    }

    fun stopBackgroundWakeService() {
        viewModelScope.launch {
            settingsRepo.setWakeServiceEnabled(false)
            MokiVoiceService.startService(getApplication(), MokiVoiceService.ACTION_DISABLE_SERVICE)
        }
    }

    fun confirmAction() {
        viewModelScope.launch {
            brainRouter.confirmPendingAction()
            _uiPendingConfirmation.value = null
            MokiVoiceService.clearConfirmationState()
        }
    }

    fun cancelAction() {
        brainRouter.dismissPendingAction()
        _uiPendingConfirmation.value = null
        MokiVoiceService.clearConfirmationState()
    }

    fun downloadLocalModel() {
        ModelDownloadWorker.enqueue(getApplication())
    }

    fun savePorcupineKey(key: String) {
        viewModelScope.launch { settingsRepo.updatePorcupineKey(key) }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepo.updateGeminiApiKey(key) }
    }

    fun setEscalationMode(mode: EscalationMode) {
        viewModelScope.launch { settingsRepo.updateEscalationMode(mode) }
    }

    fun updateTtsTuning(pitch: Float, rate: Float) {
        viewModelScope.launch { settingsRepo.updateTtsTuning(pitch, rate) }
    }

    fun clearChatHistory() {
        viewModelScope.launch { app.database.chatMessageDao().clearHistory() }
    }

    fun stopListeningLoop() {
        MokiVoiceService.startService(getApplication(), MokiVoiceService.ACTION_STOP_LOOP)
    }

    fun resetConversation() {
        viewModelScope.launch {
            brainRouter.dismissPendingAction()
            _uiPendingConfirmation.value = null
            MokiVoiceService.clearConfirmationState()
            app.database.chatMessageDao().clearHistory()
            MokiVoiceService.startService(getApplication(), MokiVoiceService.ACTION_STOP_LOOP)
        }
    }
}