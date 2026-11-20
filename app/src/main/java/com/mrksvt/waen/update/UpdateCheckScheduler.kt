package com.mrksvt.waen.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Mengatur pekerjaan periodik pemeriksaan pembaruan. Satu unique work bernama
 * tetap, sehingga preferensi hanya punya dua keadaan: aktif atau dibatalkan.
 */
object UpdateCheckScheduler {
    const val PREF_UPDATE_CHECK = "update_check"
    const val UNIQUE_WORK_NAME = "whatsvault_update_check"

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_UPDATE_CHECK, true)
    }

    @JvmStatic
    fun isNetworkConnected(context: Context): Boolean {
        return runCatching {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    @JvmStatic
    fun sync(context: Context) {
        val appContext = context.applicationContext ?: context
        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            if (!isEnabled(appContext)) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                UpdateCheckPolicy.INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }.onFailure {
            android.util.Log.w("UpdateCheckScheduler", "sync gagal: ${it.message}")
        }
    }
}
