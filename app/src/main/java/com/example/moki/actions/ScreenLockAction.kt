package com.example.moki.actions

import android.content.Context
import android.os.PowerManager
import com.example.moki.services.MokiAccessibilityService
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class ScreenLockAction(private val context: Context) : MokiSkill {
    override val name = "screen_control"
    override val description = "Lock the phone screen or wake the display."
    override val parameterSchema = """{"mode": "lock" | "unlock" | "wake"}"""
    override val triggerKeywords = listOf("lock", "unlock", "screen off", "sleep", "wake screen")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val mode = params["mode"]?.lowercase() ?: "lock"

        if (mode == "unlock") {
            return SkillExecutionResult(
                success = false,
                spokenResponse = "For your security, Android doesn't let me bypass your PIN, pattern, or fingerprint lock—you'll need to unlock it yourself."
            )
        }

        if (mode == "wake") {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "moki:WakeScreen"
            )
            wakeLock.acquire(3000L)
            return SkillExecutionResult(true, "Waking up your screen.")
        }

        val accessibility = MokiAccessibilityService.instance
        return if (accessibility != null && accessibility.lockDeviceScreen()) {
            SkillExecutionResult(true, "Screen locked.")
        } else {
            SkillExecutionResult(
                false,
                "I need the Moki Accessibility Helper enabled in your phone settings before I can lock the screen for you."
            )
        }
    }
}