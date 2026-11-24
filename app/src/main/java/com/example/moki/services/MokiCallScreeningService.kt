package com.example.moki.services

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.content.ContextCompat
import com.example.moki.voice.MokiTtsSpeaker

class MokiCallScreeningService : CallScreeningService() {

    private var speaker: MokiTtsSpeaker? = null

    override fun onCreate() {
        super.onCreate()
        speaker = MokiTtsSpeaker(this)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val callerName = resolveCallerName(number) ?: number.ifEmpty { "an unknown caller" }

        speaker?.speak("Incoming call from $callerName.")
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    private fun resolveCallerName(phoneNumber: String): String? {
        if (phoneNumber.isEmpty()) return null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}