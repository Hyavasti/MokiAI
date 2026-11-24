package com.example.moki.actions

import android.content.Context
import android.content.Intent
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class AppLauncherAction(private val context: Context) : MokiSkill {
    override val name = "open_app"
    override val description = "Open or launch an installed application by name."
    override val parameterSchema = """{"app_name": "string"}"""
    override val triggerKeywords = listOf("open", "launch", "start", "app")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val target = params["app_name"]?.trim().orEmpty()
        if (target.isEmpty()) {
            return SkillExecutionResult(false, "Which app would you like me to open?")
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)

        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().equals(target, ignoreCase = true)
        } ?: apps.firstOrNull {
            it.loadLabel(pm).toString().contains(target, ignoreCase = true)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                SkillExecutionResult(true, "Opening ${match.loadLabel(pm)}.")
            } else {
                SkillExecutionResult(false, "I found ${match.loadLabel(pm)}, but Android won't let me launch it directly.")
            }
        } else {
            SkillExecutionResult(false, "I couldn't find an app called $target installed on your phone.")
        }
    }
}