package com.example.moki.actions

import android.content.Context
import android.content.Intent
import android.os.Environment
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult
import java.io.File

class FileManagerAction(private val context: Context) : MokiSkill {
    override val name = "find_files"
    override val description = "Search Downloads or open the file manager."
    override val parameterSchema = """{"query": "string"}"""
    override val triggerKeywords = listOf("file", "files", "download", "downloads", "document", "pdf")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val query = params["query"]?.trim().orEmpty()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (query.isNotEmpty() && downloadsDir.exists()) {
            val matches = downloadsDir.listFiles()
                ?.filter { it.name.contains(query, ignoreCase = true) }
                .orEmpty()

            if (matches.isNotEmpty()) {
                val names = matches.take(3).joinToString(", ") { it.name }
                openSystemFiles()
                return SkillExecutionResult(
                    true,
                    "I found ${matches.size} matching file${if (matches.size > 1) "s" else ""} in Downloads, including $names. Opening Files now."
                )
            }
        }

        return if (openSystemFiles()) {
            SkillExecutionResult(true, "Opening your file manager.")
        } else {
            SkillExecutionResult(false, "I couldn't open a file manager app on this device.")
        }
    }

    private fun openSystemFiles(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "resource/folder"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}