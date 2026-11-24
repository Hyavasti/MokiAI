package com.example.moki.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class PhoneCallAction(private val context: Context) : MokiSkill {
    override val name = "phone_call"
    override val description = "Place a phone call to a contact or phone number."
    override val parameterSchema = """{"contact": "string"}"""
    override val triggerKeywords = listOf("call", "dial", "ring", "phone")
    override val requiresUserConfirmation = true

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val contact = params["contact"]?.trim().orEmpty()
        if (contact.isEmpty()) {
            return SkillExecutionResult(false, "Who would you like me to call?")
        }

        if (!confirmed) {
            return SkillExecutionResult(
                success = true,
                spokenResponse = "Should I call $contact?",
                requiresConfirmation = true,
                pendingConfirmationLabel = "Call $contact",
                pendingParams = params
            )
        }

        val number = resolvePhoneNumber(contact)
            ?: return SkillExecutionResult(false, "I couldn't find $contact in your contacts.")

        return try {
            val hasCallPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SkillExecutionResult(true, "Calling $contact now.")
        } catch (_: Exception) {
            SkillExecutionResult(false, "I wasn't able to place that call right now.")
        }
    }

    private fun resolvePhoneNumber(query: String): String? {
        if (query.any { it.isDigit() } && query.length >= 3) return query
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