package com.example.moki.data.model

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: DEFAULT_MODEL_URL
        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val targetFile = File(modelsDir, MODEL_FILENAME)
        val tempFile = File(modelsDir, "$MODEL_FILENAME.part")

        try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(modelUrl)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure()
                }
                val body = response.body ?: return@withContext Result.failure()
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastReportedPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16384)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isStopped) return@withContext Result.retry()
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val pct = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    setProgress(workDataOf(KEY_PROGRESS to pct))
                                }
                            }
                        }
                    }
                }
            }
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "moki_litertlm_download"
        const val MODEL_FILENAME = "gemma-3-1b-it-int4.litertlm"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_PROGRESS = "progress"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/MiCkSoftware/Gemma3-1B-IT-LiteRT/resolve/main/gemma3-1b-it-int4.litertlm"

        fun getModelFile(context: Context): File =
            File(File(context.filesDir, "models"), MODEL_FILENAME)

        fun enqueue(context: Context, customUrl: String? = null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            val data = workDataOf(KEY_MODEL_URL to (customUrl ?: DEFAULT_MODEL_URL))
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}