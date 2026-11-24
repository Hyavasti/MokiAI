package com.example.moki.services

import android.content.Intent
import android.service.voice.VoiceInteractionService
import com.example.moki.MainActivity

class MokiVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }

    fun launchAssistantUi() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}