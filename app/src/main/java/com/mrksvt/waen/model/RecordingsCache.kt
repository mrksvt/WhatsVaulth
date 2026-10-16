package com.mrksvt.waen.model

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Cache in-memory daftar rekaman panggilan, berumur selama proses app hidup.
 *
 * Dipakai untuk stale-while-revalidate: screen rekaman menampilkan isi cache
 * lebih dulu (instan, tanpa spinner), lalu refresh diam-diam di background.
 *
 * Kenapa app-scoped, bukan ViewModel: `RecordingsFragment` di-host oleh
 * `FragmentStateAdapter` (ViewPager2) yang menghancurkan fragment saat geser
 * jauh. ViewModel ber-scope fragment ikut mati, jadi cache tidak selamat.
 *
 * Thread-safety: [store] dan [snapshot] dipanggil dari main thread, [markDirty]
 * juga bisa dipanggil dari FileObserver (thread sendiri). List di-swap atomik
 * lewat volatile; flag dirty pakai AtomicBoolean.
 */
object RecordingsCache {

    @Volatile
    private var recordings: List<Recording> = emptyList()

    @Volatile
    private var loaded: Boolean = false

    private val dirty = AtomicBoolean(true)

    /** Naik tiap [markDirty]; dipakai mendeteksi perubahan saat scan berjalan. */
    private val generation = AtomicLong(0)

    /** Isi cache saat ini. Kosong kalau belum pernah dimuat. */
    @JvmStatic
    fun snapshot(): List<Recording> = recordings

    /** `true` kalau cache kosong atau ada perubahan yang belum dipantulkan. */
    @JvmStatic
    fun isDirty(): Boolean = dirty.get() || !loaded

    /** Tandai isi cache tidak lagi akurat (rekaman baru/hapus di luar screen). */
    @JvmStatic
    fun markDirty() {
        dirty.set(true)
        generation.incrementAndGet()
    }

    /** Generasi saat ini, diambil SEBELUM scan untuk [store]. */
    @JvmStatic
    fun currentGeneration(): Long = generation.get()

    /**
     * Simpan hasil scan. Dirty hanya dibersihkan kalau tidak ada perubahan
     * yang terjadi selama scan (generasi tak berubah), supaya rekaman baru
     * yang lolos di tengah scan tidak ikut terhapus dari antrian refresh.
     */
    @JvmStatic
    fun store(list: List<Recording>, scannedGeneration: Long) {
        recordings = list
        loaded = true
        if (generation.get() == scannedGeneration) {
            dirty.set(false)
        }
    }
}
