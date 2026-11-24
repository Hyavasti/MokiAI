package com.example.moki.actions

import android.content.Context
import android.content.Intent
import com.example.moki.data.local.NoteDao
import com.example.moki.data.local.NoteEntity
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class NotesAction(
    private val context: Context,
    private val noteDao: NoteDao
) : MokiSkill {
    override val name = "notes"
    override val description = "Save a new note, append to an existing note, or read saved notes."
    override val parameterSchema = """{"action": "save" | "append" | "read", "content": "string", "target": "optional string"}"""
    override val triggerKeywords = listOf("note", "notes", "remember", "memo", "write down", "listing")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val action = params["action"]?.lowercase() ?: "save"
        return when (action) {
            "read", "list", "get" -> {
                val notes = noteDao.getRecentNotes(5)
                if (notes.isEmpty()) {
                    SkillExecutionResult(true, "You don't have any saved notes yet.")
                } else {
                    val summary = notes.joinToString("; ") { it.content }
                    SkillExecutionResult(true, "Here are your recent notes: $summary")
                }
            }
            "append" -> {
                val addition = params["content"]?.trim().orEmpty()
                val targetHint = params["target"]?.trim().orEmpty()
                if (addition.isEmpty()) {
                    return SkillExecutionResult(false, "What would you like me to add to that note?")
                }

                val recent = noteDao.getRecentNotes(10)
                val existing = recent.firstOrNull { note ->
                    targetHint.isNotEmpty() && (
                            note.content.contains(targetHint, ignoreCase = true) ||
                                    note.title.contains(targetHint, ignoreCase = true)
                            )
                } ?: recent.firstOrNull()

                if (existing != null) {
                    val updatedContent = "${existing.content}\n- $addition"
                    noteDao.insertNote(NoteEntity(title = existing.title, content = updatedContent))
                    openInSystemNotesIfAvailable(updatedContent)
                    SkillExecutionResult(true, "Added \"$addition\" to your note.")
                } else {
                    noteDao.insertNote(NoteEntity(title = "Moki Note", content = addition))
                    openInSystemNotesIfAvailable(addition)
                    SkillExecutionResult(true, "Saved a new note: $addition")
                }
            }
            else -> {
                val content = params["content"]?.trim().orEmpty()
                if (content.isEmpty()) {
                    SkillExecutionResult(false, "What would you like me to write down?")
                } else {
                    noteDao.insertNote(NoteEntity(title = "Moki Note", content = content))
                    openInSystemNotesIfAvailable(content)
                    SkillExecutionResult(true, "Got it. I saved that note: $content")
                }
            }
        }
    }

    private fun openInSystemNotesIfAvailable(content: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Moki Note")
                putExtra(Intent.EXTRA_TEXT, content)
                setPackage("com.miui.notes")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(sendIntent)
        } catch (_: Exception) {
        }
    }
}