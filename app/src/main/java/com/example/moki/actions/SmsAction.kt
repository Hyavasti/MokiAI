package com.example.moki.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class SmsAction(private val context: Context) : MokiSkill {
    override val name = "send_sms"
    override val description = "Send an SMS text message immediately or at a scheduled time."
    override val parameterSchema = """{"recipient": "string", "message": "string", "send_at_hour": "optional 0-23", "send_at_minute": "optional 0-59"}"""
    override val triggerKeywords = listOf("sms", "text", "text message", "messages")
    override val requiresUserConfirmation = true

    private val smsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val recipient = params["recipient"]?.trim().orEmpty()
        val message = params["message"]?.trim().orEmpty()
        val sendAtHour = params["send_at_hour"]?.toIntOrNull()
        val sendAtMinute = params["send_at_minute"]?.toIntOrNull() ?: 0

        if (recipient.isEmpty()) {
            return SkillExecutionResult(false, "Gotcha—who are we texting in Messages, and what's the message?")
        }
        if (message.isEmpty()) {
            return SkillExecutionResult(false, "Bet. What do you want me to text $recipient?")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SkillExecutionResult(
                false,
                "I don't have SMS permission yet. You can grant it in Settings."
            )
        }

        val formattedTime = if (sendAtHour != null) {
            " at $sendAtHour:${sendAtMinute.toString().padStart(2, '0')}"
        } else {
            ""
        }

        if (!confirmed) {
            return SkillExecutionResult(
                success = true,
                spokenResponse = "Send SMS to $recipient$formattedTime: \"$message\"?",
                requiresConfirmation = true,
                pendingConfirmationLabel = "SMS to $recipient$formattedTime: \"$message\"",
                pendingParams = params
            )
        }

        val phoneNumber = resolvePhoneNumber(recipient)
            ?: return SkillExecutionResult(false, "I couldn't find a phone number for $recipient.")

        if (sendAtHour != null) {
            val delayMs = computeDelayUntil(sendAtHour, sendAtMinute)
            smsScope.launch {
                delay(delayMs)
                sendDirectSms(phoneNumber, message)
            }
            return SkillExecutionResult(true, "Scheduled your text to $recipient$formattedTime.")
        }

        return if (sendDirectSms(phoneNumber, message)) {
            SkillExecutionResult(true, "Sent your text to $recipient.")
        } else {
            SkillExecutionResult(false, "Your device or carrier blocked sending that text right now.")
        }
    }

    @Suppress("DEPRECATION")
    private fun sendDirectSms(phoneNumber: String, message: String): Boolean {
        return try {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = context.getSystemService(SmsManager::class.java)
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    base.createForSubscriptionId(subId)
                } else {
                    base
                }
            } else if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun computeDelayUntil(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour.coerceIn(0, 23))
            set(Calendar.MINUTE, targetMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis && targetHour in 1..11) {
            target.add(Calendar.HOUR_OF_DAY, 12)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
    }

    private fun resolvePhoneNumber(query: String): String? {
        val digitsOnly = query.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 7) return digitsOnly
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(uri, projection, selection, arrayOf("%$query%"), null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}