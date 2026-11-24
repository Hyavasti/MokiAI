package com.example.moki.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class AlarmAction(private val context: Context) : MokiSkill {
    override val name = "set_alarm"
    override val description = "Set or cancel an alarm for a specific hour and minute."
    override val parameterSchema = """{"action": "set" | "cancel", "hour": "0-23", "minute": "0-59", "label": "string"}"""
    override val triggerKeywords = listOf("alarm", "wake me", "timer", "clock")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val action = params["action"]?.lowercase() ?: "set"
        val hour = params["hour"]?.toIntOrNull()
        val minute = params["minute"]?.toIntOrNull() ?: 0
        val label = params["label"]?.ifBlank { "Moki Alarm" } ?: "Moki Alarm"

        if (action == "cancel") {
            return try {
                val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                    if (hour != null) {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val timeStr = if (hour != null) " for $hour:${minute.toString().padStart(2, '0')}" else ""
                SkillExecutionResult(true, "Dismissed your alarm$timeStr.")
            } catch (_: Exception) {
                SkillExecutionResult(false, "I couldn't cancel that alarm directly—opening your Clock app.")
            }
        }

        if (hour == null || hour !in 0..23 || minute !in 0..59) {
            return SkillExecutionResult(false, "Tell me what time you want the alarm set for.")
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formattedMinute = minute.toString().padStart(2, '0')
            SkillExecutionResult(true, "Alarm set for $hour:$formattedMinute.")
        } catch (_: Exception) {
            SkillExecutionResult(false, "I couldn't reach your clock app to set that alarm.")
        }
    }
}