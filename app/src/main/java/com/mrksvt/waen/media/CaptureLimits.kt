package com.mrksvt.waen.media

/**
 * Batas ukuran rekaman layar dan ruang penyimpanan.
 *
 * Fungsi murni supaya bisa diuji di JVM. Pemanggil menyediakan angka ukuran;
 * kelas ini tidak menyentuh filesystem.
 */
object CaptureLimits {

    /** Batas ukuran untuk satu sesi rekaman. 4 GiB. */
    const val DEFAULT_MAX_SESSION_BYTES = 4L * 1024 * 1024 * 1024

    /**
     * Ruang yang disisakan agar penulisan metadata penutup (moov atom) tetap
     * punya tempat. 250 MiB.
     */
    const val DEFAULT_SAFETY_MARGIN_BYTES = 250L * 1024 * 1024

    /**
     * `true` kalau ukuran sudah LEWAT batas. Tepat di batas belum dianggap
     * lewat, sehingga capture tidak berhenti karena pembulatan.
     */
    fun exceedsSizeLimit(bytes: Long, maxBytes: Long = DEFAULT_MAX_SESSION_BYTES): Boolean {
        if (maxBytes <= 0L) return true
        return bytes > maxBytes
    }

    /**
     * Ruang yang masih boleh dipakai untuk merekam, atau 0 kalau sudah di
     * bawah margin. Tidak pernah negatif supaya pemanggil bisa langsung
     * membandingkan dengan ambang tanpa menangani tanda.
     */
    fun remainingBytes(
        availableBytes: Long,
        safetyMargin: Long = DEFAULT_SAFETY_MARGIN_BYTES
    ): Long {
        if (availableBytes <= 0L) return 0L
        val margin = if (safetyMargin < 0L) 0L else safetyMargin
        val remaining = availableBytes - margin
        return if (remaining <= 0L) 0L else remaining
    }

    /**
     * `true` kalau capture video boleh dimulai. Ruang harus melebihi margin
     * ditambah jumlah minimum yang masuk akal untuk satu rekaman.
     */
    fun canStartCapture(
        availableBytes: Long,
        minimumNeededBytes: Long = DEFAULT_SAFETY_MARGIN_BYTES,
        safetyMargin: Long = DEFAULT_SAFETY_MARGIN_BYTES
    ): Boolean = remainingBytes(availableBytes, safetyMargin) >= minimumNeededBytes
}
