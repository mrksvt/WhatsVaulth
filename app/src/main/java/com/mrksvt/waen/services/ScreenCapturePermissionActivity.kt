package com.mrksvt.waen.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import java.io.File
import android.util.Log
import com.mrksvt.waen.BuildConfig

/**
 * Activity tanpa tampilan yang meminta consent `MediaProjection` lalu
 * menyerahkan hasilnya ke `ScreenCaptureService`.
 *
 * Dipisah dari service karena consent hanya bisa diminta lewat Activity, dan
 * dipisah dari UI pengaturan karena consent harus muncul saat panggilan video
 * benar-benar mulai, bukan saat user mengubah preferensi.
 *
 * Android 14+ (API 34): token projection sekali pakai, jadi activity ini
 * dijalankan sekali per panggilan video.
 */
class ScreenCapturePermissionActivity : Activity() {

    companion object {
        private const val TAG = "ScreenCapturePermission"
        private const val REQUEST_CODE = 0x5C0D

        const val EXTRA_OUTPUT_PATH = "output_path"

        const val SCREEN_MODE_KEY = "call_recording_screen_mode"

        private const val ROOT_IDENTIFIER_KEY = "call_recording_root_identifier"

        fun intent(context: Context, outputPath: String): Intent =
            Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
            }

        private fun logD(msg: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, msg)
        }
    }

    private var outputPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH).orEmpty()

        if (startRootRecordingIfSelected()) {
            finish()
            return
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager == null) {
            logD("MediaProjectionManager tidak tersedia")
            finish()
            return
        }

        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
        } catch (t: Throwable) {
            logD("createScreenCaptureIntent gagal: ${t.message}")
            finish()
        }
    }

    @Deprecated("startActivityForResult dipakai karena consent MediaProjection berbasis Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE) {
            finish()
            return
        }

        if (resultCode != RESULT_OK || data == null) {
            logD("consent ditolak; rekaman audio tidak terpengaruh")
            finish()
            return
        }

        val started = ScreenCaptureService.start(this, resultCode, data, outputPath)
        logD("ScreenCaptureService.start -> $started")
        finish()
    }

    /**
     * Mode root tidak butuh Activity sama sekali; kalau dipilih dan berhasil,
     * tidak ada consent yang perlu diminta. Kalau gagal, kita jatuh kembali ke
     * jalur projection alih-alih membiarkan panggilan tanpa rekaman video.
     */
    private fun startRootRecordingIfSelected(): Boolean {
        val prefs = try {
            PreferenceManager.getDefaultSharedPreferences(this)
        } catch (t: Throwable) {
            logD("prefs tidak tersedia: ${t.message}")
            return false
        }

        if (prefs.getString(SCREEN_MODE_KEY, RootScreenRecord.MODE_PROJECTION)
            != RootScreenRecord.MODE_ROOT
        ) {
            return false
        }

        val dir = File(outputPath)
        val identifier = prefs.getString(ROOT_IDENTIFIER_KEY, "Unknown").orEmpty()
        val started = RootScreenRecord.start(dir, identifier.ifBlank { "Unknown" })
        logD("root screen record started=$started")
        return started
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}
