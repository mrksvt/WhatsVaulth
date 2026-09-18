package com.mrksvt.waen.xposed.features.media

/**
 * Profile kualitas audio untuk Call Recording.
 *
 * Semua encoder di sini adalah built-in `MediaRecorder` (AAC), jadi tidak ada
 * dependency native/eksternal yang ditambahkan. Nilai profile disimpan sebagai
 * data murni supaya bisa diuji tanpa perangkat Android.
 *
 * Pemilihan profile dilakukan lewat [resolveAudioProfile]. Nilai prefs yang
 * tidak dikenal selalu jatuh ke [AudioProfile.AAC_96] (perilaku lama) supaya
 * preferensi usang tidak pernah membuat rekaman gagal.
 *
 * Catatan test: konstanta `MediaRecorder.AudioEncoder` TIDAK dibaca di sini.
 * Unit test JVM akan melempar `RuntimeException("Stub!")` kalau menyentuh
 * konstanta Android, dan modul ini tidak memasang `unitTests.returnDefaultValues`.
 * Karena itu [AAC_ENCODER] dideklarasikan sebagai konstanta lokal.
 */
object CallRecordingQuality {

    /** Key SharedPreferences untuk profile audio. */
    const val PREFS_KEY = "call_recording_audio_profile"

    /** Nilai prefs -> [AudioProfile]. Satu-satunya sumber pemetaan. */
    const val VALUE_AAC_96 = "aac_96"
    const val VALUE_AAC_192 = "aac_192"
    const val VALUE_AAC_256 = "aac_256"

    /**
     * `MediaRecorder.AudioEncoder.AAC`. Nilainya 3 dan stabil sejak API 10.
     * Disalin sebagai konstanta supaya `CallRecordingQualityTest` bisa jalan di
     * JVM tanpa menyentuh stub Android.
     */
    const val AAC_ENCODER = 3

    /**
     * Batas jumlah percobaan `prepare()` untuk satu sumber audio.
     *
     * Nilainya harus cukup untuk menurunkan profile tertinggi (256 kbps) sampai
     * [FLOOR_BITRATE]: 256 -> 224 -> 160 -> 128 -> 96, yaitu 5 tingkat. Batas
     * yang lebih kecil membuat tingkat terendah tidak pernah dicoba, sehingga
     * device yang menolak semua bitrate tinggi gagal merekam padahal masih ada
     * tingkat yang belum diuji.
     */
    const val MAX_ATTEMPTS = 5

    /**
     * Bitrate terendah pada rantai penurunan, sengaja disamakan dengan
     * `AAC_96` supaya jalur default tidak pernah turun di bawah perilaku lama.
     * Ini lantai kebijakan, bukan batas teknis encoder AAC.
     */
    const val FLOOR_BITRATE = 96_000

    enum class Container {
        M4A
    }

    data class AudioProfile(
        val id: String,
        val sampleRate: Int,
        val bitRate: Int,
        val encoder: Int,
        val container: Container
    ) {
        fun downgraded(): AudioProfile? {
            val next = when {
                bitRate > 224_000 -> 224_000
                bitRate > 160_000 -> 160_000
                bitRate > 128_000 -> 128_000
                bitRate > FLOOR_BITRATE -> FLOOR_BITRATE
                else -> return null
            }
            if (next >= bitRate) return null
            return copy(bitRate = next)
        }
    }

    val AAC_96 = AudioProfile(
        id = VALUE_AAC_96,
        sampleRate = 44_100,
        bitRate = 96_000,
        encoder = AAC_ENCODER,
        container = Container.M4A
    )

    val AAC_192 = AudioProfile(
        id = VALUE_AAC_192,
        sampleRate = 48_000,
        bitRate = 192_000,
        encoder = AAC_ENCODER,
        container = Container.M4A
    )

    val AAC_256 = AudioProfile(
        id = VALUE_AAC_256,
        sampleRate = 48_000,
        bitRate = 256_000,
        encoder = AAC_ENCODER,
        container = Container.M4A
    )

    val ALL: List<AudioProfile> = listOf(AAC_96, AAC_192, AAC_256)

    val HD_PROFILES: List<AudioProfile> = ALL.filter { it.bitRate > AAC_96.bitRate }

    /**
     * Peta nilai prefs yang dikenal. Nilai tak dikenal diperlakukan sebagai
     * [AAC_96] oleh [resolveAudioProfile].
     */
    private val BY_ID: Map<String, AudioProfile> = ALL.associateBy { it.id }

    /**
     * Resolusi profile dari nilai SharedPreferences.
     *
     * Kontrak: TIDAK PERNAH melempar dan TIDAK PERNAH mengembalikan null.
     * `null`, string kosong, dan nilai tak dikenal semuanya jatuh ke [AAC_96].
     */
    fun resolveAudioProfile(prefsValue: String?): AudioProfile {
        if (prefsValue == null) return AAC_96
        return BY_ID[prefsValue.trim().lowercase()] ?: AAC_96
    }

    /**
     * Rantai profile yang akan dicoba untuk satu sumber audio: profile pilihan
     * user, lalu turunannya saat `prepare()` gagal.
     *
     * Selalu mengembalikan minimal satu elemen dan tidak pernah memuat bitrate
     * di bawah [FLOOR_BITRATE].
     */
    fun attemptChain(prefsValue: String?): List<AudioProfile> {
        val chain = ArrayList<AudioProfile>(MAX_ATTEMPTS)
        var current: AudioProfile? = resolveAudioProfile(prefsValue)
        while (current != null && chain.size < MAX_ATTEMPTS) {
            chain.add(current)
            current = current.downgraded()
        }
        return chain
    }
}
