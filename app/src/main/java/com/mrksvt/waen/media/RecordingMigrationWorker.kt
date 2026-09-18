package com.mrksvt.waen.media

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mrksvt.waen.BuildConfig
import java.io.File

/**
 * Memindahkan rekaman dari lokasi lama ke struktur folder per jenis panggilan.
 *
 * Kebijakan (`FINDINGS.md` B-5 keputusan opsi 1):
 * - HANYA memindahkan file. Tidak pernah menghapus.
 * - Kalau pemindahan gagal, file sumber dibiarkan apa adanya.
 * - File yang sudah di tempat yang benar, atau bukan rekaman, tidak disentuh.
 *
 * Berjalan sekali; penanda disimpan di SharedPreferences supaya tidak diulang
 * setiap app dibuka.
 */
class RecordingMigrationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "RecordingMigration"
        const val PREF_DONE = "recording_migration_done"

        private fun logD(msg: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, msg)
        }

        private fun logW(msg: String) {
            if (BuildConfig.DEBUG) Log.w(TAG, msg)
        }
    }

    override fun doWork(): Result {
        val context = applicationContext
        val prefs = try {
            PreferenceManager.getDefaultSharedPreferences(context)
        } catch (t: Throwable) {
            logW("prefs tidak tersedia: ${t.message}")
            return Result.failure()
        }

        if (prefs.getBoolean(PREF_DONE, false)) {
            logD("migrasi sudah pernah dijalankan")
            return Result.success()
        }

        val currentRoot = prefs.getString(
            "call_recording_path",
            RecordingStorage.DEFAULT_RECORDINGS_ROOT
        ) ?: RecordingStorage.DEFAULT_RECORDINGS_ROOT

        val legacyDirs = RecordingStorage.legacyBaseDirs(
            configuredPath = prefs.getString("call_recording_path", null),
            downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            externalRoot = Environment.getExternalStorageDirectory(),
            whatsappDataDirs = listOf(
                File("/sdcard/Android/data/com.whatsapp/files/Recordings"),
                File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings")
            )
        )

        var moved = 0
        var skipped = 0

        for (dir in legacyDirs) {
            if (!dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile) continue

                val videoKind = file.name.lowercase().endsWith(".mp4")
                if (!videoKind && !RecordingStorage.isAudioRecording(file.name)) continue

                if (!RecordingStorage.needsMigration(file.absolutePath, currentRoot, videoKind)) {
                    skipped++
                    continue
                }

                val targetDir = RecordingStorage.callKindFolder(File(currentRoot), videoKind)
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    logW("tidak bisa membuat folder tujuan: ${targetDir.absolutePath}")
                    skipped++
                    continue
                }

                val target = File(targetDir, file.name)
                if (target.exists()) {
                    logD("file tujuan sudah ada, dilewati: ${target.name}")
                    skipped++
                    continue
                }

                if (moveFile(file, target)) {
                    moved++
                } else {
                    logW("pemindahan gagal, file sumber dibiarkan: ${file.absolutePath}")
                    skipped++
                }
            }
        }

        logD("migrasi selesai: dipindah=$moved dilewati=$skipped")
        prefs.edit().putBoolean(PREF_DONE, true).apply()
        return Result.success()
    }

    /**
     * Pindahkan hanya kalau berhasil sepenuhnya. `renameTo` gagal lintas
     * filesystem, jadi ada jalur salin; kalau salin gagal, file tujuan parsial
     * dihapus dan file sumber tetap utuh.
     */
    private fun moveFile(source: File, target: File): Boolean {
        if (source.renameTo(target)) return true

        return try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!source.delete()) {
                logW("file tersalin tapi sumber tidak bisa dihapus: ${source.name}")
                return true
            }
            true
        } catch (t: Throwable) {
            logW("salin gagal: ${t.message}")
            if (target.exists() && !target.delete()) {
                logW("file tujuan parsial tidak bisa dihapus")
            }
            false
        }
    }
}
