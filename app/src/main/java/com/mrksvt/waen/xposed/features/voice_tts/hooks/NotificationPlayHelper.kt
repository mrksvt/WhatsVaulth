package com.mrksvt.waen.xposed.features.voice_tts.hooks

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.R
import com.mrksvt.waen.receivers.TtsPlayReceiver
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Tugas F: inject "Putar suara TTS" action ke notifikasi pesan WhatsApp.
 *
 * Aturan:
 *  - action HANYA ditambahkan untuk pesan dari kontak auto_tts_enabled DAN
 *    audio-nya sudah selesai digenerate (ada di [readyByHash])
 *  - action trigger PendingIntent BROADCAST ke TtsPlayReceiver (bukan Activity),
 *    yang start foreground TtsPlaybackService di proses sisi app
 *  - race condition: kalau notifikasi muncul sebelum audio siap, notifikasi
 *    disimpan; begitu audio siap, notifikasi di-notify ulang dengan ID sama
 *    plus action play
 *
 * Pencocokan notifikasi <-> pesan: pakai potongan teks pesan (notifikasi WA
 * memuat teks pesan di extras android.text / android.title). Semua data hanya
 * di memori proses, tidak pernah ditulis/dikirim keluar.
 *
 * Semua jalur dibungkus try-catch log-warning (pola CustomThemeV2): perubahan
 * struktur Notification/field mActions di versi WA lain tidak boleh crash.
 */
object NotificationPlayHelper {

    private const val MAX_TEXT_MATCH_LEN = 120

    private data class ReadyAudio(val messageId: String, val text: String, val audioPath: String)

    /** textHash -> audio siap pakai. */
    private val readyByHash = ConcurrentHashMap<String, ReadyAudio>()

    /** textHash -> notifikasi yang sudah terlanjur tampil sebelum audio siap. */
    private val pendingRepost =
        ConcurrentHashMap<String, PendingNotification>()

    private class PendingNotification(
        val tag: String?,
        val id: Int,
        val notificationRef: WeakReference<Notification>,
        val packageName: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun logD(msg: String) {
        if (BuildConfig.DEBUG) XposedBridge.log("[WAE_TTS_NOTIF] $msg")
    }

    /**
     * Dipanggil sisi hook saat audio auto-TTS selesai (bubble READY).
     * Kalau notifikasi pesan ini sudah tampil, update dengan action play.
     */
    @JvmStatic
    fun cacheForNotificationPlayback(messageId: String, text: String, audioPath: String) {
        val hash = textKey(text)
        readyByHash[hash] = ReadyAudio(messageId, text, audioPath)
        logD("cached audio for msg=$messageId")

        pendingRepost.remove(hash)?.let { pending ->
            mainHandler.post {
                val notif = pending.notificationRef.get() ?: return@post
                if (addActionToNotification(notif, hash)) {
                    tryRepost(pending, notif)
                }
            }
        }
    }

    /** Install hook NotificationManager.notify di proses WhatsApp. */
    @JvmStatic
    fun install(context: Context) {
        try {
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        handleNotify(param)
                    } catch (t: Throwable) {
                        logD("notify hook EX: ${t.message}")
                    }
                }
            }
            XposedBridge.hookAllMethods(NotificationManager::class.java, "notify", callback)
            logD("NotificationManager.notify hooked")
        } catch (t: Throwable) {
            XposedBridge.log("[WAE_TTS_NOTIF] install failed: ${t.message}")
        }
    }

    private fun handleNotify(param: XC_MethodHook.MethodHookParam) {
        val args = param.args
        val notification = args.lastOrNull() as? Notification ?: return
        val tag = args.getOrNull(0) as? String
        val id = args.getOrNull(if (args.size == 3) 1 else 0) as? Int ?: return

        // jangan sentuh notifikasi kita sendiri (channel playback)
        val pkg = try {
            XposedHelpers.getObjectField(notification, "mPackage") as? String
        } catch (_: Throwable) { null }
        if (pkg != null && pkg == BuildConfig.APPLICATION_ID) return

        val notifText = extractText(notification) ?: return
        if (notifText.isBlank()) return

        val hash = findReadyHash(notifText)
        if (hash != null) {
            if (addActionToNotification(notification, hash)) {
                logD("play action added to notification id=$id")
            }
            return
        }

        // audio belum siap? catat untuk re-post saat cacheForNotificationPlayback datang
        val pendingHash = findPendingHash(notifText)
        if (pendingHash != null) {
            pendingRepost[pendingHash] = PendingNotification(
                tag, id, WeakReference(notification),
                Utils.application.packageName
            )
            logD("notification parked for late action id=$id")
        }
    }

    /** Teks notifikasi (title + big/text) untuk pencocokan. */
    private fun extractText(notification: Notification): String? {
        return try {
            val extras = notification.extras ?: return null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: "")
            "$title $text".trim().ifBlank { null }
        } catch (_: Exception) { null }
    }

    /** Cari hash audio READY yang teksnya termuat di notifikasi. */
    private fun findReadyHash(notifText: String): String? {
        for ((hash, ready) in readyByHash) {
            if (textMatches(ready.text, notifText)) return hash
        }
        return null
    }

    /**
     * Hash untuk pesan auto-TTS yang masih diproses. VoiceTTSFeature
     * mendaftarkan pesan yang di-request auto-TTS di sini.
     */
    private val requestedHashes = ConcurrentHashMap<String, String>() // hash -> messageId

    @JvmStatic
    fun markRequested(messageId: String, text: String) {
        requestedHashes[textKey(text)] = messageId
        // GC sederhana: batasi ukuran
        if (requestedHashes.size > 200) {
            val it = requestedHashes.keys.iterator()
            while (it.hasNext() && requestedHashes.size > 150) {
                val k = it.next()
                if (!readyByHash.containsKey(k)) it.remove()
            }
        }
    }

    private fun findPendingHash(notifText: String): String? {
        for (hash in requestedHashes.keys) {
            if (readyByHash.containsKey(hash)) continue
            val msgId = requestedHashes[hash] ?: continue
            // teks asli tidak disimpan untuk requested; cocokkan via bubble store
            val bubbleText = VoiceTTSFeature.bubbleTextFor(msgId) ?: continue
            if (textMatches(bubbleText, notifText)) return hash
        }
        return null
    }

    private fun textMatches(messageText: String, notifText: String): Boolean {
        val needle = messageText.take(MAX_TEXT_MATCH_LEN).trim()
        if (needle.length < 4) return false
        return notifText.contains(needle, ignoreCase = true)
    }

    private fun textKey(text: String): String {
        return try {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(32)
        } catch (_: Exception) { text.hashCode().toString(16) }
    }

    /**
     * Tambah action play ke Notification via field mActions (refleksi, pola
     * yang sama dipakai modul-modul Xposed lain). Return true bila berubah.
     */
    private fun addActionToNotification(notification: Notification, hash: String): Boolean {
        val ready = readyByHash[hash] ?: return false
        return try {
            @Suppress("UNCHECKED_CAST")
            val actions = try {
                XposedHelpers.getObjectField(notification, "mActions") as? MutableList<Notification.Action>
            } catch (_: Exception) { null } ?: return false

            // sudah ada? jangan duplikat
            if (actions.any { it.title?.toString()?.startsWith("\uD83D\uDD0A") == true }) return false

            val action = buildPlayAction(Utils.application, ready) ?: return false
            actions.add(action)
            true
        } catch (t: Throwable) {
            logD("addAction EX: ${t.message}")
            false
        }
    }

    private fun buildPlayAction(context: Context, ready: ReadyAudio): Notification.Action? {
        return try {
            val intent = Intent(TtsPlayReceiver.ACTION_PLAY_TTS)
                .setClassName(BuildConfig.APPLICATION_ID, TtsPlayReceiver::class.java.name)
                .putExtra(TtsPlayReceiver.EXTRA_AUDIO_PATH, ready.audioPath)
                .putExtra(TtsPlayReceiver.EXTRA_MESSAGE_ID, ready.messageId)
            val pi = PendingIntent.getBroadcast(
                context, ready.messageId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // varian Icon (API 23+, minSdk 28 aman) - bukan ctor int deprecated
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(
                    context, android.R.drawable.ic_media_play
                ),
                "\uD83D\uDD0A " + context.getString(R.string.voice_tts_play),
                pi
            ).build()
        } catch (t: Throwable) {
            logD("buildAction EX: ${t.message}")
            null
        }
    }

    /** Re-post notifikasi yang sudah di-update dengan action (ID sama). */
    private fun tryRepost(pending: PendingNotification, notification: Notification) {
        try {
            val mgr = Utils.application
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(pending.tag, pending.id, notification)
            logD("re-posted notification id=${pending.id} with play action")
        } catch (t: Throwable) {
            logD("repost EX: ${t.message}")
        }
    }
}
