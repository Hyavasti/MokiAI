package com.example.moki.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class EmailAction(private val context: Context) : MokiSkill {
    override val name = "send_email"
    override val description = "Draft or send an email to an email address."
    override val parameterSchema = """{"to": "string", "subject": "string", "body": "string"}"""
    override val triggerKeywords = listOf("email", "mail", "gmail")
    override val requiresUserConfirmation = true

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val to = params["to"]?.trim().orEmpty()
        val subject = params["subject"]?.trim().ifNullOrEmpty { "Quick note" }
        val body = params["body"]?.trim().orEmpty()

        if (!confirmed) {
            return SkillExecutionResult(
                success = true,
                spokenResponse = "Ready to prepare that email${if (to.isNotEmpty()) " to $to" else ""}. Confirm to open your mail app.",
                requiresConfirmation = true,
                pendingConfirmationLabel = "Email ${to.ifEmpty { "recipient" }}: $subject",
                pendingParams = params
            )
        }

        return try {
            val uri = Uri.parse("mailto:${Uri.encode(to)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SkillExecutionResult(true, "I've opened your email draft so you can review and send it.")
        } catch (_: Exception) {
            SkillExecutionResult(false, "I couldn't find an email app set up on your device.")
        }
    }

    private inline fun String?.ifNullOrEmpty(defaultValue: () -> String): String =
        if (this.isNullOrEmpty()) defaultValue() else this
}