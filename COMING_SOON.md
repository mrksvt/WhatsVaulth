# Coming Soon

Fitur-fitur berikut sedang dalam perencanaan atau pengembangan.

## Status Fitur

| Status | Keterangan                  |
| ------ | --------------------------- |
| ✅     | Sudah diimplementasikan     |
| ⚠️   | Partial / perlu peningkatan |
| ❌     | Belum diimplementasikan     |

| # | Fitur                                   | Status | Deskripsi Singkat                                                                                      |
| - | --------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------ |
| 1 | 🗓️ Message Scheduler                  | ❌     | Jadwalkan pesan sekali atau berulang dengan antrian terpadu                                            |
| 2 | 💬 Auto Reply                           | ❌     | Balas otomatis berdasarkan kata kunci, delay, dan jam aktif                                            |
| 3 | 📞 Call & Video Recording HD            | ⚠️   | Audio AAC 96/192/256 kbps + rekaman layar video call jadi satu MP4 di recordings/video_call        |
| 4 | 🎨 Screen UI Theme Builder              | ⚠️   | Kustomisasi dasar ada, theme builder + live preview + drag & drop + AI belum                           |
| 5 | 🗑️ Deleted Log                        | ✅     | Log pesan yang dihapus dengan export JSON ke Downloads                                                 |
| 6 | 🤖 Groq AI Translator (Bubble + Composer) | ✅   | Bubble translator jalan (WA reguler + Business), Composer translator jalan, model updated              |
| 7 | 🔐 Enhanced KeyBox & Bootloader Spoofer | ⚠️   | KeyBox manual ada, auto-fetch + fingerprint Pixel Canary + hourly refresh belum                        |
| 8 | ⬇️ In-App Update Installer              | ✅     | Unduh APK dari GitHub release di dalam app lalu install lewat installer sistem                          |

---

## 🗓️ Message Scheduler

Jadwalkan pengiriman pesan pada waktu tertentu, termasuk dukungan pesan berulang (recurring).

**Rencana:**

- Penjadwalan pesan satu kali dengan tanggal & waktu
- Pengiriman berulang (harian, mingguan, dll.)
- Manajemen antrian pesan terjadwal

---

## 💬 Auto Reply

Balas pesan otomatis berdasarkan kata kunci dengan dukungan delay dan pembatasan jam aktif.

**Rencana:**

- Kata kunci kustom (custom keywords)
- Delay balasan yang dapat dikonfigurasi
- Pembatasan jam aktif (jam operasional)

---

## 📞 Call & Video Call Recording HD

Rekam panggilan suara dan video call dengan kualitas tinggi.

**Status:** Selesai.

**Yang sudah ada:**

- Audio AAC 96, 192, dan 256 kbps (pilih di Media > Audio Quality)
- Rekaman layar video call penuh (seluruh layar, termasuk pratinjau diri sendiri)
- Hasil video call = satu MP4 berisi audio dan video yang sudah digabung
- Harga: Android 14+ meminta izin rekam layar sekali per video call

**Lokasi simpan:**

```
/sdcard/WhatsVault/recordings/
├── video_call/   Call_<nama>_<tanggal>_<jam>.mp4
└── voice_call/   Call_<nama>_<tanggal>_<jam>.m4a
```

Rekaman lama ikut dipindahkan otomatis saat app pertama dibuka, dan file lama
tidak pernah dihapus.

**Mode rekam layar:**

- Standar: butuh izin per panggilan, hasilnya satu MP4 ber-audio
- Root: tanpa izin, tapi hanya seluruh layar dan videonya terpecah per 180
  detik tanpa audio. Hanya muncul kalau device punya root dan binary
  `screenrecord`

**Yang dibatalkan:**

- Mode crop "hanya lawan bicara" dan "hanya diri sendiri": terlalu bergantung
  pada layout internal WhatsApp. Rekaman selalu seluruh layar
- WAV/OPUS lossless: `MediaRecorder` Android tidak bisa output PCM mentah, dan
  AAC 256 kbps sudah cukup untuk rekaman panggilan

---

## 🎨 Screen UI Customization Theme Builder

Editor tema visual langsung dari dalam aplikasi WAE dengan live preview dan bantuan AI.

**Status:** Kustomisasi dasar sudah ada, theme builder UI belum.

**Rencana:**

- Theme builder UI di dalam WhatsVault
- Live preview perubahan tema secara real-time
- Drag & drop elemen
- AI Design assistant untuk saran desain

---

## 🗑️ Trash & Deleted Recovery

Pulihkan pesan dan media yang telah dihapus sepenuhnya.

**Rencana:**

- Restore pesan yang dihapus (hook-based interception)
- Restore media yang dihapus

---

## 🤖 Groq AI Translator In Chat Composer

Terjemahan berbasis AI langsung di chat composer sebelum pesan dikirim.

**Status:** ✅ Selesai diimplementasikan — per-chat config (enabled + language), dialog UI, logging diagnostik, multi-variant (com.whatsapp + com.whatsapp.w4b).

**Implemented:**

- ✅ Translate teks di input field sebelum kirim
- ✅ Dukungan multi-bahasa via Groq AI / Google Translate
- ✅ Per-chat toggle (ON/OFF per conversation)
- ✅ Per-chat target language (setiap chat bisa beda bahasa)
- ✅ Config persisten via `WppCore.getPrivPrefs()` keyed by JID
- ✅ Dialog UI: BottomSheet → plain Dialog (fix WhatsApp context crash)
- ✅ String localization (id, en, ar, de, es, fr, it, pt, ru, tr, zh)
- ✅ Fail-safe: jika JID tidak bisa di-resolve, translator bypass

**Known Issues (inline translator — perlu diperbaiki):**

- ~~Groq butuh API key manual — tidak ada fallback ke Google jika key kosong~~ ✅ Fixed
- ~~Bahasa target tidak bisa dipilih manual — selalu ikut locale sistem~~ ✅ Fixed — ListPreference + pref `translator_target_lang`
- ~~Terjemahan tidak persisten — hilang saat conversation di-close atau WA restart, tidak ada cache ke DB~~ ✅ Fixed — Room DB `TranslationCache` per JID
- ~~`TranslatorWrapperAdapter.instance` static singleton — buka dua conversation sekaligus, instance lama tertimpa~~ ✅ Fixed — `instances: HashMap<String, WeakReference<>>` per JID
- ~~Label popup hardcoded Bahasa Indonesia ("Terjemahkan" / "Sembunyikan terjemahan") — tidak ikut locale~~ ✅ Fixed — `R.string.*`
- ~~Google Translate pakai endpoint tidak resmi (`client=gtx`) — rawan rate-limit atau breaking change~~ ✅ Fixed — endpoint bisa diatur via settings
- ~~Tidak ada loading indicator saat menunggu hasil terjemahan~~ ✅ Fixed — bubble `⏳ Menerjemahkan...`
- ~~Error hanya tampil Toast tanpa opsi retry~~ ✅ Fixed — Snackbar + "Coba Lagi"
- ~~Groq kadang salah interpretasi slang/bahasa daerah — malah balik bertanya~~ ✅ Fixed — system prompt kuat + few-shot disambiguation + user-editable prompt & slang kamus
- ~~Multi-conversation: JID registration via `registerJidForCurrentAdapter()` — kadang adapter terdaftar setelah pertama kali `onItemBind` → terjemahan mungkin tidak tampil pada buka pertama conversation baru (refresh conversation fix ini)~~ ✅ Fixed — per-chat config baca on-demand by JID, tidak bergantung adapter registration timing
- ~~Translate di composer belum diimplementasi~~ ✅ Fixed — per-chat enabled + per-chat language, config persisten via JID

---

## 🔐 Enhanced KeyBox & Bootloader Spoofer AlwaysStrong Integration

Perkuat fitur Custom KeyBox dan Bootloader Spoofer yang sudah ada dengan kemampuan auto-fetch KeyBox + Fingerprint, sehingga TEE valid, bootloader aman, dan Play Integrity diharapkan mencapai STRONG — meskipun untuk Android < 13 STRONG masih sulit.

**Goals:**

- TEE valid via KeyBox yang selalu fresh dan tidak di-revoke Google
- Bootloader spoofing aman via fingerprint Pixel Canary terbaru
- Play Integrity STRONG (best-effort; Android 13+ lebih mudah, Android < 13 terbatas)

**Arsitektur (port dari AlwaysStrong ke Kotlin native):**

| Komponen AlwaysStrong             | Port ke WhatsVault                                                                                          |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `keybox_fetch.sh` + `asfetch` | Kotlin HTTP client (OkHttp), fetch`keybox.xml` base64 dari mirror                                         |
| `pif_native_fetch.sh`           | Kotlin crawler:`developer.android.com` → `flash.android.com` → GFlash API → Pixel Canary fingerprint |
| `service.sh` hourly loop        | WorkManager PeriodicWorkRequest (interval default 1 jam, configurable)                                      |
| `conflict_scan.sh`              | Root check: disable conflicting modules (TrickyStore, PlayIntegrityFix) via`libsu`                        |
| WebUI toggles                     | Settings UI:`no_auto_fp`, `no_auto_keybox` toggle                                                       |

**Rencana implementasi:**

1. **KeyBox auto-fetch** — OkHttp GET ke mirror, base64 decode, validate `<Keybox>` XML tag, SHA256 check sebelum write ke `/data/adb/tricky_store/keybox.xml`
2. **Manual input** — user bisa paste KeyBox XML manual atau upload file dari storage
3. **Fingerprint auto-fetch** — crawl Pixel Canary build, write `custom.pif.prop` dengan `spoofProvider=0, spoofVendingFinger=1` (wajib untuk STRONG)
4. **Auto-refresh** — WorkManager periodic, restart `com.google.android.gms.unstable` + Play Store setelah update via `libsu`
5. **Mirror KeyBox** — host mirror sendiri (tidak bergantung `evoker.qzz.io`)
6. **UI status** — tampilkan status TEE + Play Integrity verdict di dalam app

**Critical notes:**

- KeyBox **bisa di-revoke Google** kapan saja — mirror harus rutin diperbarui
- `spoofProvider=0, spoofVendingFinger=1` **wajib** di `custom.pif.prop` untuk STRONG
- Android < 13: STRONG sangat sulit, target realistis DEVICE integrity
- Restart GMS tidak butuh reboot: `kill com.google.android.gms.unstable` via root
- Xposed/LSPosed manager harus di-exclude dari attestation target

---

## ⬇️ In-App Update Installer

Layar What's New (`ChangelogActivity`) bisa mengunduh APK rilis langsung dari GitHub dan
menginstall-nya lewat installer sistem, tanpa keluar app.

**Alur:**

1. `UpdateChecker` membaca asset APK pada release GitHub dan memilih asset yang cocok dengan
   flavor terpasang (`WhatsVault-...` untuk whatsapp, `WhatsVault-Business-...` untuk business).
   URL dan ukuran disimpan di `UpdateInfoStore`.
2. Tombol utama di layar changelog menjadi `Download`. Progres tampil di tombol
   (`Downloading 42%`) dan ada tombol `Cancel download`.
3. Setelah selesai, APK diverifikasi sebelum install: package name, versionCode harus lebih
   baru, signature harus kompatibel.
4. Tombol berubah menjadi `Install`. Bila izin unknown sources belum ada, app membuka
   Settings lalu melanjutkan install saat kembali.

**Batasan:**

- Installer butuh izin **Install unknown apps** (Settings → Special app access) untuk WhatsVault.
- Build business bisa update in-app **hanya bila** release punya asset `WhatsVault-Business-*.apk`.
  `release.yml` saat ini baru membangun varian whatsapp, jadi build business akan jatuh ke
  fallback browser.
- APK harus ditandatangani kunci yang sama dengan yang terpasang. Build debug lokal berbeda
  signature dengan build release, sehingga menampilkan pesan signature mismatch.
- Release tanpa asset APK: tombol langsung menjadi `Open in browser`.

**Berkas terkait:** `update/ApkDownloader.kt`, `update/ApkInstaller.kt`, `ChangelogActivity.java`,
`ChangelogViewModel.java`.

---

*Dokumen ini diperbarui sesuai perkembangan fitur.*
