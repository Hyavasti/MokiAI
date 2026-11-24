package com.example.moki.actions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceStatusAction(private val context: Context) : MokiSkill {
    override val name = "device_status"
    override val description = "Check real phone battery, time, date, toggle Do Not Disturb, or read recent incoming SMS texts."
    override val parameterSchema = """{"query": "battery" | "time" | "date" | "dnd" | "recent_messages", "state": "optional on/off"}"""
    override val triggerKeywords = listOf("battery", "charge", "charging", "time", "date", "day", "dnd", "do not disturb", "who texted")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val query = params["query"]?.lowercase() ?: "battery"
        return when (query) {
            "time" -> {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                SkillExecutionResult(true, "It's $timeStr.")
            }
            "date" -> {
                val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
                SkillExecutionResult(true, "Today is $dateStr.")
            }
            "dnd" -> {
                val turnOn = (params["state"]?.lowercase() ?: "on") != "off"
                toggleDoNotDisturb(turnOn)
            }
            "recent_messages" -> {
                readLatestIncomingSms()
            }
            else -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val statusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val chargeText = if (isCharging) "and charging" else "and not charging"
                SkillExecutionResult(true, "Your battery is at $level percent $chargeText.")
            }
        }
    }

    private fun toggleDoNotDisturb(enable: Boolean): SkillExecutionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            return SkillExecutionResult(
                false,
                "Please allow Do Not Disturb access for Moki in Settings first, then try again."
            )
        }

        return try {
            val filter = if (enable) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            nm.setInterruptionFilter(filter)
            SkillExecutionResult(
                true,
                if (enable) "Do Not Disturb is now on." else "Do Not Disturb is now off."
            )
        } catch (_: Exception) {
            SkillExecutionResult(false, "I couldn't change Do Not Disturb mode on this device.")
        }
    }

    private fun readLatestIncomingSms(): SkillExecutionResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SkillExecutionResult(
                false,
                "I need Read SMS permission in Settings before I can check who texted you."
            )
        }

        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(0).orEmpty()
                    val body = it.getString(1).orEmpty().trim().take(120)
                    val senderName = resolveContactName(address) ?: address
                    return SkillExecutionResult(
                        true,
                        "Your latest text is from $senderName: \"$body\""
                    )
                }
            }
            SkillExecutionResult(true, "You don't have any texts in your inbox.")
        } catch (_: Exception) {
            SkillExecutionResult(false, "I couldn't read your SMS inbox right now.")
        }
    }

    private fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}