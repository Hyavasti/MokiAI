package com.example.moki.actions

import android.content.Context
import android.content.Intent
import com.example.moki.services.MokiAccessibilityService
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class MessengerAction(private val context: Context) : MokiSkill {
    override val name = "send_messenger"
    override val description = "Send a message or place an audio/video call on Facebook Messenger."
    override val parameterSchema = """{"action": "message" | "audio_call" | "video_call", "recipient": "string", "message": "optional string"}"""
    override val triggerKeywords = listOf("messenger", "chat", "pm", "video call", "messenger call", "call on messenger")
    override val requiresUserConfirmation = true

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val action = params["action"]?.lowercase()?.trim().orEmpty().ifEmpty { "message" }
        val recipient = params["recipient"]?.trim().orEmpty()
        val message = params["message"]?.trim().orEmpty()

        val isCall = action == "audio_call" || action == "video_call"
        val isVideo = action == "video_call"

        if (recipient.isEmpty()) {
            val prompt = if (isCall) {
                "Gotcha—who do you want me to call on Messenger?"
            } else {
                "Gotcha—who are we messaging on Messenger, and what should I say?"
            }
            return SkillExecutionResult(false, prompt)
        }

        if (!isCall && message.isEmpty()) {
            return SkillExecutionResult(false, "Bet. What do you want me to send to $recipient on Messenger?")
        }

        if (!confirmed) {
            val label = when {
                isVideo -> "Messenger video call to $recipient"
                isCall -> "Messenger audio call to $recipient"
                else -> "Messenger to $recipient: \"$message\""
            }
            val spoken = when {
                isVideo -> "Start a Messenger video call with $recipient?"
                isCall -> "Call $recipient on Messenger?"
                else -> "Should I send \"$message\" to $recipient on Messenger?"
            }
            return SkillExecutionResult(
                success = true,
                spokenResponse = spoken,
                requiresConfirmation = true,
                pendingConfirmationLabel = label,
                pendingParams = params
            )
        }

        val accessibility = MokiAccessibilityService.instance
        if (accessibility == null) {
            return SkillExecutionResult(
                false,
                "To control Messenger hands-free, please enable the Moki Accessibility Helper in your phone settings first."
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.facebook.orca")
            ?: return SkillExecutionResult(false, "Messenger doesn't appear to be installed on your phone.")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        return if (isCall) {
            accessibility.queueMessengerCall(recipient, isVideo = isVideo)
            val modeLabel = if (isVideo) "video calling" else "calling"
            SkillExecutionResult(true, "Opening Messenger and $modeLabel $recipient.")
        } else {
            accessibility.queueMessengerAutomation(recipient, message)
            SkillExecutionResult(true, "Opening Messenger to send your message to $recipient.")
        }
    }
}