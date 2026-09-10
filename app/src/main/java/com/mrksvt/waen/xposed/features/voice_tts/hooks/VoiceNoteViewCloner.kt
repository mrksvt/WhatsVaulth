package com.mrksvt.waen.xposed.features.voice_tts.hooks

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.WeakHashMap

/**
 * Reuses WhatsApp's own voice-note bubble layout for TTS bubbles instead of
 * building a custom audio player from scratch.
 *
 * How the layout is discovered without hardcoding obfuscated names:
 *  1. Public resource names survive obfuscation, so the anchor ids
 *     (audio_play_btn / audio_duration / wave_view ...) are resolved at
 *     runtime via Utils.getID against the host package - same pattern the
 *     project already uses for "message_text".
 *  2. LayoutInflater#inflate is hooked once; whenever an inflated layout
 *     contains one of those anchors, its resource id is captured as the
 *     reference voice-note layout.
 *  3. TTS bubbles then inflate that exact resource id, so the bubble looks
 *     like a real WhatsApp voice note, and a MediaPlayer is bound to the
 *     play button / seek bar / duration label inside it.
 *
 * If no voice note has been inflated yet (capture happens lazily), the
 * caller falls back to the built-in simple bubble renderer.
 */
object VoiceNoteViewCloner {

    // anchors verified against WhatsApp 2.26.x voice-note row layouts
    // (conversation_row_audio_* / audio_root_layout); resolved at runtime via
    // Utils.getID so a renamed id never hard-crashes the feature
    private const val ANCHOR_PLAY = "audio_file_play_btn"
    private const val ANCHOR_DURATION = "audio_file_duration"
    private const val ANCHOR_ROOT = "audio_root_layout"
    private const val ANCHOR_PLAYER = "conversation_row_audio_player_view"
    private const val ANCHOR_SEEK = "audio_seek_bar"

    @Volatile
    private var voiceNoteLayoutId: Int = 0

    private val players = WeakHashMap<View, MediaPlayer>()
    // fd sumber audio (dibuka via bridge, file hidup di private dir modul)
    private val audioFds = WeakHashMap<View, ParcelFileDescriptor>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hookInstalled = false

    /**
     * Install the LayoutInflater capture hook. Safe to call multiple times;
     * wrapped in try-catch so a failure never breaks the feature (CustomThemeV2
     * pattern).
     */
    @JvmStatic
    fun installCaptureHook() {
        if (hookInstalled) return
        hookInstalled = true
        try {
            XposedBridge.hookMethod(
                LayoutInflater::class.java.getDeclaredMethod(
                    "inflate",
                    Int::class.javaPrimitiveType,
                    ViewGroup::class.java,
                    Boolean::class.javaPrimitiveType
                ),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (voiceNoteLayoutId != 0) return
                        try {
                            val resId = param.args[0] as Int
                            val root = param.result as? View ?: return
                            if (containsVoiceAnchors(root)) {
                                voiceNoteLayoutId = resId
                                if (BuildConfig.DEBUG) {
                                    XposedBridge.log(
                                        "[WAE_TTS] captured voice-note layout 0x" +
                                            Integer.toHexString(resId)
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            if (BuildConfig.DEBUG) {
                                XposedBridge.log("[WAE_TTS] capture EX: ${t.message}")
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[WAE_TTS] installCaptureHook failed: ${t.message}")
        }
    }

    /** True when the WhatsApp voice-note layout has been captured already. */
    @JvmStatic
    fun isLayoutAvailable(): Boolean = voiceNoteLayoutId != 0

    /**
     * Inflate WhatsApp's voice-note layout and bind [audioPath] to it.
     *
     * @return the ready-to-return bubble view, or null when the layout has not
     *         been captured yet (caller should use the fallback renderer).
     */
    @JvmStatic
    fun bind(parent: ViewGroup, audioPath: String): View? {
        val layoutId = voiceNoteLayoutId
        if (layoutId == 0) return null
        return try {
            val view = LayoutInflater.from(parent.context)
                .inflate(layoutId, parent, false)
            attachPlayer(view, audioPath)
            view
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) XposedBridge.log("[WAE_TTS] bind EX: ${t.message}")
            null
        }
    }

    /** Release every MediaPlayer owned by recycled bubble views. */
    @JvmStatic
    fun releaseAll() {
        players.values.forEach { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        players.clear()
        audioFds.values.forEach { runCatching { it.close() } }
        audioFds.clear()
    }

    private fun containsVoiceAnchors(root: View): Boolean {
        val playId = Utils.getID(ANCHOR_PLAY, "id")
        if (playId > 0 && root.findViewById<View>(playId) != null) return true
        val rootId = Utils.getID(ANCHOR_ROOT, "id")
        if (rootId > 0 && root.findViewById<View>(rootId) != null) return true
        val playerId = Utils.getID(ANCHOR_PLAYER, "id")
        return playerId > 0 && root.findViewById<View>(playerId) != null
    }

    private fun attachPlayer(view: View, audioPath: String) {
        val playId = Utils.getID(ANCHOR_PLAY, "id")
        val durationId = Utils.getID(ANCHOR_DURATION, "id")
        val seekId = Utils.getID(ANCHOR_SEEK, "id")

        val playButton = (if (playId != 0 && playId != -1)
            view.findViewById<View>(playId) else null)
            ?: findFirstImageButton(view)

        val durationText = if (durationId != 0 && durationId != -1)
            view.findViewById<TextView>(durationId) else null
        val seekBar = if (seekId != 0 && seekId != -1)
            view.findViewById<SeekBar>(seekId) else null

        // neutralize WhatsApp's own state on the cloned widgets
        (playButton as? ImageButton)?.setImageResource(
            android.R.drawable.ic_media_play
        )
        durationText?.text = "0:00"
        seekBar?.progress = 0
        seekBar?.setOnTouchListener { v, _ -> v.performClick(); false }

        playButton?.setOnClickListener {
            try {
                togglePlayback(view, audioPath, playButton, durationText, seekBar)
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) XposedBridge.log("[WAE_TTS] toggle EX: ${t.message}")
            }
        }
    }

    /**
     * Audio TTS disimpan di private dir modul. Proses WhatsApp (UID berbeda) tidak
     * bisa membuka path itu langsung -> open failed: ENOENT. Ambil fd dari sisi
     * app via bridge openFile, lalu lewatkan fileDescriptor ke MediaPlayer.
     */
    private fun MediaPlayer.setBridgeDataSource(view: View, audioPath: String) {
        val pfd = try {
            WppCore.getClientBridge()?.openFile(audioPath, false)
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG)
                XposedBridge.log("[WAE_TTS] cloner src=direct err=${t.message}")
            null
        }
        if (pfd != null) {
            if (BuildConfig.DEBUG)
                XposedBridge.log("[WAE_TTS] cloner src=bridge-fd ok")
            runCatching { audioFds.remove(view)?.close() }
            audioFds[view] = pfd
            setDataSource(pfd.fileDescriptor)
        } else {
            setDataSource(audioPath)
        }
    }

    private fun togglePlayback(
        view: View,
        audioPath: String,
        playButton: View,
        durationText: TextView?,
        seekBar: SeekBar?
    ) {
        val existing = players[view]
        if (existing != null && existing.isPlaying) {
            runCatching { existing.pause() }
            (playButton as? ImageButton)?.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        val player = existing ?: MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setBridgeDataSource(view, audioPath)
            setOnPreparedListener {
                durationText?.text = formatDuration(it.duration)
                seekBar?.max = it.duration
            }
            setOnCompletionListener {
                (playButton as? ImageButton)?.setImageResource(android.R.drawable.ic_media_play)
                seekBar?.progress = 0
                runCatching { it.seekTo(0) }
            }
            prepare()
        }

        players[view] = player
        seekBar?.let { sb ->
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) runCatching { player.seekTo(progress) }
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        startProgressTicker(view, player, seekBar)
        runCatching { player.start() }
        (playButton as? ImageButton)?.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun startProgressTicker(view: View, player: MediaPlayer, seekBar: SeekBar?) {
        mainHandler.post(object : Runnable {
            override fun run() {
                val current = players[view] ?: return
                runCatching {
                    if (current.isPlaying) {
                        seekBar?.progress = current.currentPosition
                        mainHandler.postDelayed(this, 250)
                    }
                }
            }
        })
    }

    private fun findFirstImageButton(root: View): ImageButton? {
        if (root is ImageButton) return root
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) {
            val found = findFirstImageButton(root.getChildAt(i))
            if (found != null) return found
        }
        return null
    }

    private fun formatDuration(ms: Int): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
}
