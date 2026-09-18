package com.mrksvt.waen.media

/**
 * Pemilihan parameter encoder H.264 berdasarkan tinggi layar.
 *
 * Semua nilai disimpan sebagai data murni supaya bisa diuji di JVM tanpa
 * Android runtime. Konstanta `MediaCodec` tidak dibaca di sini; pemanggil
 * yang memasang `MediaFormat`.
 *
 * Resolusi selalu kelipatan 2 karena encoder H.264 menolak dimensi ganjil.
 */
object VideoEncoderProfile {

    const val MIN_FRAME_RATE = 24
    const val MAX_FRAME_RATE = 60

    /** Dipakai saat preferensi user memilih kualitas tertentu. */
    const val PREF_AUTO = "auto"

    data class Profile(
        val id: String,
        val width: Int,
        val height: Int,
        val bitRate: Int,
        val frameRate: Int,
        val mimeType: String = MIME_H264
    ) {
        val resolutionLabel: String
            get() = "${height}p"
    }

    const val MIME_H264 = "video/avc"

    private fun even(value: Int): Int {
        val safe = if (value < 2) 2 else value
        return safe - (safe % 2)
    }

    /**
     * Profile untuk ukuran layar nyata. `auto` sengaja memakai resolusi layar
     * penuh (bukan bucket) supaya layar 2400p tidak diturunkan tanpa alasan;
     * hanya bitrate dan frame rate yang dipilih dari bucket.
     */
    fun forResolution(width: Int, height: Int): Profile {
        val safeWidth = if (width < 2) 2 else width
        val safeHeight = if (height < 2) 2 else height

        val bitRate = when {
            safeHeight >= 1440 -> 12_000_000
            safeHeight >= 1080 -> 8_000_000
            safeHeight >= 720 -> 5_000_000
            else -> 2_500_000
        }

        return Profile(
            id = "auto-${safeHeight}p",
            width = even(safeWidth),
            height = even(safeHeight),
            bitRate = bitRate,
            frameRate = recommendedFrameRate(safeHeight)
        )
    }

    fun recommendedFrameRate(height: Int): Int = when {
        height >= 1440 -> 60
        height >= 1080 -> 30
        else -> 30
    }

    /**
     * Profile untuk pilihan eksplisit user. `auto` dan nilai tak dikenal
     * memakai [forResolution]. Tinggi yang tidak dikenal dipetakan ke bucket
     * terdekat supaya preferensi usang tidak pernah menggagalkan capture.
     */
    fun forPreference(preference: String?, screenWidth: Int, screenHeight: Int): Profile {
        val normalized = preference?.trim()?.lowercase()
        if (normalized == null || normalized.isEmpty() || normalized == PREF_AUTO) {
            return forResolution(screenWidth, screenHeight)
        }

        val requestedHeight = normalized.toIntOrNull() ?: return forResolution(screenWidth, screenHeight)
        val bucket = when {
            requestedHeight >= 1440 -> 1440
            requestedHeight >= 1080 -> 1080
            requestedHeight >= 720 -> 720
            else -> 480
        }

        val (bitRate, frameRate) = when (bucket) {
            1440 -> 12_000_000 to 60
            1080 -> 8_000_000 to 30
            720 -> 5_000_000 to 30
            else -> 2_500_000 to 30
        }

        val targetHeight = minOf(bucket, screenHeight)
        val targetWidth = if (screenHeight <= 0) {
            screenWidth
        } else {
            (screenWidth.toLong() * targetHeight / screenHeight).toInt()
        }

        return Profile(
            id = "pref-${bucket}p",
            width = even(targetWidth),
            height = even(targetHeight),
            bitRate = bitRate,
            frameRate = frameRate
        )
    }

    /**
     * Satu tingkat resolusi lebih rendah, atau `null` kalau sudah di
     * [MIN_HEIGHT]. Tinggi SELALU turun supaya rantai tidak pernah mandek di
     * tingkat yang sama.
     */
    fun downgrade(profile: Profile): Profile? {
        if (profile.height <= MIN_HEIGHT) return null

        val nextHeight = when {
            profile.height > 1080 -> 1080
            profile.height > 720 -> 720
            else -> MIN_HEIGHT
        }
        val nextBitRate = when (nextHeight) {
            1080 -> 8_000_000
            720 -> 5_000_000
            else -> 2_500_000
        }
        val scale = profile.height.toLong()
        val nextWidth = if (scale <= 0L) profile.width
        else even((profile.width.toLong() * nextHeight / scale).toInt())

        return profile.copy(
            id = "downgrade-${nextHeight}p",
            width = nextWidth,
            height = nextHeight,
            bitRate = nextBitRate,
            frameRate = recommendedFrameRate(nextHeight)
        )
    }

    /**
     * Rantai profile yang dicoba saat `configure()` gagal. Panjang mengikuti
     * jumlah tingkat resolusi yang tersedia (maksimum
     * [MAX_ATTEMPTS]), supaya dari 1440p pun tingkat terendah tetap tercoba.
     */
    fun attemptChain(preference: String?, screenWidth: Int, screenHeight: Int): List<Profile> {
        val chain = ArrayList<Profile>(MAX_ATTEMPTS)
        var current: Profile? = forPreference(preference, screenWidth, screenHeight)
        while (current != null && chain.size < MAX_ATTEMPTS) {
            chain.add(current)
            current = downgrade(current)
        }
        return chain
    }

    const val MIN_HEIGHT = 480

    const val MAX_ATTEMPTS = 4
}
