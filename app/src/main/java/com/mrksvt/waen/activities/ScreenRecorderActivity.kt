package com.mrksvt.waen.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import com.mrksvt.waen.R
import com.mrksvt.waen.activities.base.BaseActivity
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderResult
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderSession
import com.mrksvt.waen.services.ScreenRecorderService
import com.mrksvt.waen.services.ScreenRecorderSessionHolder
import java.io.File

/**
 * Halaman SR: mulai/hentikan, status, durasi, buka/share hasil terakhir.
 *
 * Consent diminta dari foreground dan token diteruskan SEKALI ke
 * `ScreenRecorderService`. Activity tidak pernah memulai capture dari
 * lifecycle; capture hanya jalan setelah user menekan tombol dan consent
 * diberikan.
 */
class ScreenRecorderActivity : BaseActivity() {

    private lateinit var toggle: MaterialButton
    private lateinit var openButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var status: android.widget.TextView
    private lateinit var duration: android.widget.TextView
    private lateinit var error: android.widget.TextView

    private var bound = false
    private var binder: ScreenRecorderService.LocalBinder? = null
    private var lastResult: File? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? ScreenRecorderService.LocalBinder
            bound = binder != null
            render(binder?.state())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            binder = null
            render(ScreenRecorderSessionHolder.lastSnapshot)
        }
    }

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            toast(getString(R.string.sr_consent_needed))
            render(ScreenRecorderSessionHolder.lastSnapshot)
        } else {
            val started = ScreenRecorderService.start(this, result.resultCode, data)
            if (!started) {
                toast(getString(R.string.sr_failed))
            }
            bindService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_recorder)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toggle = findViewById(R.id.sr_toggle)
        openButton = findViewById(R.id.sr_open)
        shareButton = findViewById(R.id.sr_share)
        status = findViewById(R.id.sr_status)
        duration = findViewById(R.id.sr_duration)
        error = findViewById(R.id.sr_error)

        toggle.setOnClickListener { onToggleClicked() }
        openButton.setOnClickListener { openLastResult() }
        shareButton.setOnClickListener { shareLastResult() }

        refreshLastResult()
        render(ScreenRecorderSessionHolder.lastSnapshot)
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        unbindService()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshLastResult()
        render(binder?.state() ?: ScreenRecorderSessionHolder.lastSnapshot)
    }

    private fun bindService() {
        if (bound) return
        val intent = Intent(this, ScreenRecorderService::class.java)
        runCatching { bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE) }
            .onFailure { render(ScreenRecorderSessionHolder.lastSnapshot) }
    }

    private fun unbindService() {
        if (!bound) {
            binder = null
            return
        }
        runCatching { unbindService(serviceConnection) }
        bound = false
        binder = null
    }

    private fun onToggleClicked() {
        val snapshot = binder?.state() ?: ScreenRecorderSessionHolder.lastSnapshot
        if (snapshot.isActive) {
            binder?.stop()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun render(snapshot: ScreenRecorderSession.Snapshot?) {
        val state = snapshot?.state ?: ScreenRecorderSession.State.IDLE
        val result = snapshot?.result
        val active = snapshot?.isActive == true

        toggle.text = if (active) getString(R.string.sr_stop) else getString(R.string.sr_start)
        toggle.isEnabled = !(state == ScreenRecorderSession.State.STOPPING)

        status.text = when (state) {
            ScreenRecorderSession.State.IDLE -> getString(R.string.sr_idle)
            ScreenRecorderSession.State.REQUESTING,
            ScreenRecorderSession.State.STARTING -> getString(R.string.sr_start)
            ScreenRecorderSession.State.RECORDING -> getString(R.string.sr_notification_title)
            ScreenRecorderSession.State.STOPPING -> getString(R.string.sr_stop)
            ScreenRecorderSession.State.SAVED -> getString(R.string.sr_saved)
            ScreenRecorderSession.State.ERROR -> getString(R.string.sr_failed)
        }

        val saved = result as? ScreenRecorderResult.Saved
        duration.text = if (saved != null && state != ScreenRecorderSession.State.STOPPING) {
            formatDuration(saved.durationUs)
        } else {
            getString(R.string.sr_duration_zero)
        }

        error.visibility = if (state == ScreenRecorderSession.State.ERROR) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        if (state == ScreenRecorderSession.State.ERROR) {
            error.text = (result as? ScreenRecorderResult.Failed)?.partialPath
                ?.let { getString(R.string.sr_partial) }
                ?: getString(R.string.sr_failed)
        }

        val hasResult = lastResult?.exists() == true
        openButton.isEnabled = hasResult
        shareButton.isEnabled = hasResult
    }

    private fun refreshLastResult() {
        val baseDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: filesDir
        val dir = com.mrksvt.waen.media.screenrecorder.ScreenRecorderStorage.outputDir(baseDir)
        lastResult = com.mrksvt.waen.media.screenrecorder.ScreenRecorderStorage.readLastResult(dir)
    }

    private fun openLastResult() {
        val file = lastResult ?: return
        val uri = uriFor(file) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_VIDEO)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(getString(R.string.sr_open_unavailable))
        }
    }

    private fun shareLastResult() {
        val file = lastResult ?: return
        val uri = uriFor(file) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_VIDEO
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.sr_share_result)))
        } catch (e: Exception) {
            toast(getString(R.string.sr_share_unavailable))
        }
    }

    private fun uriFor(file: File): Uri? =
        runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull()

    private fun formatDuration(durationUs: Long): String {
        val totalSeconds = durationUs / 1_000_000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MIME_VIDEO = "video/mp4"
    }
}
