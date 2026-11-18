package com.reveila.aegin.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ModelDownloadWorker"

        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_FALLBACK_URLS = "fallback_urls"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val KEY_APPROX_SIZE_BYTES = "approx_size_bytes"
        const val KEY_TARGET_PATH = "target_path"
        const val KEY_PROGRESS_PCT = "progress_pct"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_STATUS_PHASE = "status_phase"
        const val KEY_STATUS_MSG = "status_msg"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_ERROR_MSG = "error_msg"

        private const val CHANNEL_ID = "aegin_model_downloads"

        fun uniqueWorkName(modelId: String): String =
            "gguf_download_${modelId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"

        /**
         * Enqueues a unique resumable download for a specific GGUF model and returns its WorkRequest UUID.
         */
        fun enqueue(
            context: Context,
            modelId: String,
            downloadUrl: String,
            expectedSha256: String,
            targetFile: File,
            fallbackUrls: List<String> = emptyList(),
            approxSizeBytes: Long = 0L
        ): UUID {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_MODEL_ID to modelId,
                        KEY_DOWNLOAD_URL to downloadUrl,
                        KEY_FALLBACK_URLS to fallbackUrls.toTypedArray(),
                        KEY_EXPECTED_SHA256 to expectedSha256,
                        KEY_APPROX_SIZE_BYTES to approxSizeBytes,
                        KEY_TARGET_PATH to targetFile.absolutePath
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(modelId),
                ExistingWorkPolicy.REPLACE,
                request
            )
            return request.id
        }
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext failure("Missing modelId")
        val primaryUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext failure("Missing downloadUrl")
        val fallbackUrls = inputData.getStringArray(KEY_FALLBACK_URLS)?.toList().orEmpty()
        val expectedSha256 = inputData.getString(KEY_EXPECTED_SHA256).orEmpty().trim().lowercase()
        val approxSizeBytes = inputData.getLong(KEY_APPROX_SIZE_BYTES, 0L)
        val targetPath = inputData.getString(KEY_TARGET_PATH) ?: return@withContext failure("Missing targetPath")

        val finalFile = File(targetPath)
        ensureNotificationChannel()
        val notificationId = modelId.hashCode()
        try {
            setForeground(createForegroundInfo(notificationId, modelId, 0, "Connecting..."))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to elevate worker to foreground service: ${e.message}")
        }

        val candidateUrls = buildList {
            if (primaryUrl.isNotBlank()) add(primaryUrl)
            addAll(fallbackUrls.filter { it.isNotBlank() })
        }.distinct()

        val outcome = ResumableModelDownloader.downloadWithResume(
            urls = candidateUrls,
            targetFile = finalFile,
            expectedSha256 = expectedSha256,
            approxSizeBytes = approxSizeBytes,
            userAgent = "Aegin-Reveila-WorkManager/1.0",
            shouldStop = { isStopped },
            onProgress = { phase, dlBytes, totBytes, pct, activeUrl, msg ->
                setProgress(
                    workDataOf(
                        KEY_STATUS_PHASE to phase,
                        KEY_PROGRESS_PCT to pct.coerceAtLeast(0),
                        KEY_DOWNLOADED_BYTES to dlBytes,
                        KEY_TOTAL_BYTES to totBytes,
                        KEY_SOURCE_URL to activeUrl,
                        KEY_STATUS_MSG to msg
                    )
                )
                val notifText = if (phase == "VERIFYING") {
                    "Verifying GGUF integrity..."
                } else {
                    val mbDone = dlBytes / (1024 * 1024)
                    if (totBytes > 0L) "$mbDone MB / ${totBytes / (1024 * 1024)} MB" else "$mbDone MB downloaded"
                }
                try {
                    setForeground(createForegroundInfo(notificationId, modelId, pct, notifText))
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                }
            }
        )

        if (outcome.stopped || isStopped) {
            return@withContext Result.retry()
        }

        if (outcome.succeeded) {
            return@withContext Result.success(
                workDataOf(
                    KEY_TARGET_PATH to finalFile.absolutePath,
                    KEY_PROGRESS_PCT to 100,
                    KEY_DOWNLOADED_BYTES to outcome.bytesDownloaded,
                    KEY_TOTAL_BYTES to outcome.bytesDownloaded,
                    KEY_SOURCE_URL to outcome.usedUrl
                )
            )
        }

        if (outcome.fatal) {
            return@withContext failure(outcome.errorMessage ?: "Download failed.")
        }

        if (runAttemptCount < 4) {
            val pct = if (approxSizeBytes > 0L && outcome.bytesDownloaded > 0L) {
                ((outcome.bytesDownloaded * 100L) / approxSizeBytes).toInt().coerceIn(0, 99)
            } else {
                0
            }
            setProgress(
                workDataOf(
                    KEY_STATUS_PHASE to "DOWNLOADING",
                    KEY_PROGRESS_PCT to pct,
                    KEY_DOWNLOADED_BYTES to outcome.bytesDownloaded,
                    KEY_TOTAL_BYTES to approxSizeBytes,
                    KEY_SOURCE_URL to outcome.usedUrl,
                    KEY_STATUS_MSG to "Stalled (${outcome.errorMessage ?: "network error"}); retrying..."
                )
            )
            Result.retry()
        } else {
            failure("Download failed after ${runAttemptCount + 1} attempts: ${outcome.errorMessage ?: "Unknown error"}")
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        modelId: String,
        progressPct: Int,
        statusText: String
    ): ForegroundInfo {
        val indeterminate = progressPct < 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Aegin Model: $modelId")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (indeterminate) 0 else progressPct, indeterminate)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aegin Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background downloads and verification for local GGUF models"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR_MSG to message))
}
