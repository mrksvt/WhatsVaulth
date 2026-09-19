package com.mrksvt.waen.services

import android.util.Log
import com.mrksvt.waen.BuildConfig
import com.topjohnwu.superuser.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rekaman layar video call lewat `screenrecord` sebagai root.
 *
 * Jalur alternatif ketika consent `MediaProjection` tidak diinginkan. Bedanya
 * dengan jalur projection:
 * - TIDAK butuh dialog consent
 * - TIDAK bisa crop (selalu seluruh layar)
 * - Hasilnya TERPECAH per chunk karena `screenrecord` membatasi durasi
 *
 * Karena satu panggilan menghasilkan beberapa file, jalur ini tidak
 * menghasilkan satu `.mp4` gabungan ber-audio; chunk-nya video-only.
 */
object RootScreenRecord {

    private const val TAG = "RootScreenRecord"
    private const val CHUNK_SECONDS = 180
    private const val STOP_GRACE_MS = 1_500L

    /**
     * Batas jumlah chunk sebagai jaring pengaman terakhir. Kalau tidak ada yang
     * memanggil [stop] (mis. proses yang menghentikan mati lebih dulu),
     * perekaman berhenti sendiri alih-alih berjalan tanpa batas. 40 chunk x 180
     * detik = 2 jam, jauh melebihi panggilan video terpanjang yang wajar.
     */
    private const val MAX_CHUNKS = 40

    const val MODE_PROJECTION = "projection"
    const val MODE_ROOT = "root"

    private val running = AtomicBoolean(false)

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var stopRequested = false

    private fun logD(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private fun logW(msg: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, msg)
    }

    /** `true` kalau binary `screenrecord` ada di device ini. */
    @JvmStatic
    fun isAvailable(): Boolean = try {
        Shell.cmd("which screenrecord || command -v screenrecord").exec().isSuccess
    } catch (t: Throwable) {
        logW("cek screenrecord gagal: ${t.message}")
        false
    }

    @JvmStatic
    fun isRunning(): Boolean = running.get()

    /**
     * Mulai merekam ke [outputDir]. Mengembalikan `false` kalau root tidak
     * tersedia atau `screenrecord` tidak ada, sehingga pemanggil bisa jatuh
     * kembali ke jalur projection.
     */
    @JvmStatic
    fun start(outputDir: File, identifier: String): Boolean {
        if (running.get()) {
            logW("sudah merekam")
            return true
        }

        val shell = Shell.getShell()
        if (!shell.isRoot) {
            logW("root tidak tersedia")
            return false
        }
        if (!isAvailable()) {
            logW("screenrecord tidak ada di device ini")
            return false
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            logW("tidak bisa membuat folder tujuan: ${outputDir.absolutePath}")
            return false
        }

        stopRequested = false
        running.set(true)

        val thread = Thread({ recordLoop(outputDir, identifier) }, "WaEnhancer-RootRecord").apply {
            isDaemon = true
        }
        worker = thread
        thread.start()
        return true
    }

    private fun recordLoop(outputDir: File, identifier: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var part = 1

        while (!stopRequested) {
            val name = "Call_${identifier}_$timestamp-video-part%02d.mp4".format(part)
            val target = File(outputDir, name)
            val cmd = "screenrecord --time-limit $CHUNK_SECONDS --bit-rate 8000000 " +
                "'${target.absolutePath}'"

            try {
                val result = Shell.cmd(cmd).exec()
                logD("chunk $part selesai (exit=${result.code}) -> ${target.name}")
            } catch (t: Throwable) {
                logW("chunk $part gagal: ${t.message}")
                break
            }

            if (stopRequested) break
            if (part >= MAX_CHUNKS) {
                logW("batas $MAX_CHUNKS chunk tercapai, perekaman berhenti sendiri")
                break
            }
            part++
        }

        running.set(false)
        logD("perekaman root berhenti setelah $part chunk")
    }

    /**
     * Hentikan loop. Root shell tidak forward sinyal dengan baik, jadi proses
     * `screenrecord` yang sedang jalan dimatikan lewat `pkill` dulu supaya
     * chunk terakhir tidak menggantung 180 detik.
     */
    @JvmStatic
    fun stop() {
        if (!running.getAndSet(false)) return
        stopRequested = true

        try {
            Shell.cmd("pkill -f 'screenrecord --time-limit'").exec()
        } catch (t: Throwable) {
            logW("pkill gagal: ${t.message}")
        }

        try {
            worker?.join(STOP_GRACE_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        worker = null
        logD("stop() selesai")
    }
}
