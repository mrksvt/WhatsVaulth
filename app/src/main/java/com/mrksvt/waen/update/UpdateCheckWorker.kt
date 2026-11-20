package com.mrksvt.waen.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrksvt.waen.UpdateChecker
import com.mrksvt.waen.UpdateInfoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pemeriksaan pembaruan periodik. Hanya berjalan ketika preferensi
 * `update_check` aktif, lalu memperbarui cache dan notifikasi.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val enabled = UpdateCheckScheduler.isEnabled(applicationContext)
        val networkConnected = UpdateCheckScheduler.isNetworkConnected(applicationContext)
        if (!UpdateCheckPolicy.shouldRunPeriodic(enabled, networkConnected)) {
            return@withContext Result.success()
        }

        try {
            UpdateChecker(applicationContext).run()
        } catch (recoverable: UnknownHostException) {
            return@withContext Result.retry()
        } catch (recoverable: SocketTimeoutException) {
            return@withContext Result.retry()
        } catch (throwable: Throwable) {
            android.util.Log.w("UpdateCheckWorker", "pemeriksaan gagal: ${throwable.message}")
            return@withContext Result.success()
        }

        runCatching {
            UpdateNotifier.notifyFollowedChannels(
                applicationContext,
                UpdateInfoStore.getFollowedChannels(applicationContext)
            )
        }
        UpdateInfoStore.setLastCheckAt(applicationContext, System.currentTimeMillis())
        Result.success()
    }
}
