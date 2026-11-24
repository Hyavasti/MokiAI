package com.example.moki.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "moki_settings")

enum class EscalationMode {
    LOCAL_ONLY,
    GEMINI_APP_HANDOFF,
    CLOUD_API
}

data class MokiSettings(
    val porcupineAccessKey: String = "",
    val geminiApiKey: String = "",
    val escalationMode: EscalationMode = EscalationMode.LOCAL_ONLY,
    val ttsPitch: Float = 1.02f,
    val ttsSpeechRate: Float = 1.04f,
    val continuousFollowUp: Boolean = true,
    val wakeServiceEnabled: Boolean = false
)

class MokiSettingsRepository(private val context: Context) {

    private object Keys {
        val PORCUPINE_KEY = stringPreferencesKey("porcupine_access_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val ESCALATION_MODE = stringPreferencesKey("escalation_mode")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val CONTINUOUS_FOLLOW_UP = booleanPreferencesKey("continuous_follow_up")
        val WAKE_SERVICE_ENABLED = booleanPreferencesKey("wake_service_enabled")
    }

    val settingsFlow: Flow<MokiSettings> = context.dataStore.data.map { prefs ->
        MokiSettings(
            porcupineAccessKey = prefs[Keys.PORCUPINE_KEY] ?: "",
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            escalationMode = runCatching {
                EscalationMode.valueOf(prefs[Keys.ESCALATION_MODE] ?: EscalationMode.LOCAL_ONLY.name)
            }.getOrDefault(EscalationMode.LOCAL_ONLY),
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.02f,
            ttsSpeechRate = prefs[Keys.TTS_RATE] ?: 1.04f,
            continuousFollowUp = prefs[Keys.CONTINUOUS_FOLLOW_UP] ?: true,
            wakeServiceEnabled = prefs[Keys.WAKE_SERVICE_ENABLED] ?: false
        )
    }

    suspend fun updatePorcupineKey(key: String) {
        context.dataStore.edit { it[Keys.PORCUPINE_KEY] = key.trim() }
    }

    suspend fun updateGeminiApiKey(key: String) {
        context.dataStore.edit { it[Keys.GEMINI_API_KEY] = key.trim() }
    }

    suspend fun updateEscalationMode(mode: EscalationMode) {
        context.dataStore.edit { it[Keys.ESCALATION_MODE] = mode.name }
    }

    suspend fun updateTtsTuning(pitch: Float, rate: Float) {
        context.dataStore.edit {
            it[Keys.TTS_PITCH] = pitch.coerceIn(0.7f, 1.4f)
            it[Keys.TTS_RATE] = rate.coerceIn(0.7f, 1.5f)
        }
    }

    suspend fun setWakeServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_SERVICE_ENABLED] = enabled }
    }
}