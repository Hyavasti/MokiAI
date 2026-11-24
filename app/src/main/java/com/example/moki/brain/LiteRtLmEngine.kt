package com.example.moki.brain

import android.content.Context
import android.util.Log
import com.example.moki.data.model.ModelDownloadWorker
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LiteRtLmEngine(private val context: Context) {

    private val mutex = Mutex()
    private var engine: Engine? = null

    fun isModelDownloaded(): Boolean {
        val file = ModelDownloadWorker.getModelFile(context)
        return file.exists() && file.length() > 10_000_000L
    }

    suspend fun generate(systemPrompt: String, userQuery: String): String? = withContext(Dispatchers.IO) {
        if (!isModelDownloaded()) return@withContext null

        mutex.withLock {
            try {
                val activeEngine = getOrInitEngine() ?: return@withContext null
                val config = ConversationConfig(systemMessage = Message.of(systemPrompt))
                activeEngine.createConversation(config).use { conversation ->
                    val response = conversation.sendMessage(Message.of(userQuery))
                    response.toString().trim().takeIf { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                Log.e("LiteRtLmEngine", "Inference failed", e)
                // Reset engine once in case session state became stale
                engine = null
                null
            }
        }
    }

    private fun getOrInitEngine(): Engine? {
        engine?.let { return it }
        val modelPath = ModelDownloadWorker.getModelFile(context).absolutePath
        val cacheDir = context.cacheDir.absolutePath

        // Prefer CPU backend first for maximum compatibility across Xiaomi chipsets
        return try {
            val cpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU,
                cacheDir = cacheDir
            )
            Engine(cpuConfig).apply { initialize() }.also { engine = it }
        } catch (cpuErr: Exception) {
            Log.w("LiteRtLmEngine", "CPU init failed, trying GPU", cpuErr)
            try {
                val gpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU,
                    cacheDir = cacheDir
                )
                Engine(gpuConfig).apply { initialize() }.also { engine = it }
            } catch (gpuErr: Exception) {
                Log.e("LiteRtLmEngine", "All LiteRT-LM backends failed", gpuErr)
                null
            }
        }
    }
}