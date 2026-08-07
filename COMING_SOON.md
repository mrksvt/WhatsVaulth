# Coming Soon

Fitur-fitur berikut sedang dalam perencanaan atau pengembangan.

## Status Fitur

| Status | Keterangan |
|---|---|
| ✅ | Sudah diimplementasikan |
| ⚠️ | Partial / perlu peningkatan |
| ❌ | Belum diimplementasikan |

| # | Fitur | Status | Deskripsi Singkat |
|---|---|---|---|
| 1 | 🗓️ Message Scheduler | ❌ | Jadwalkan pesan sekali atau berulang dengan antrian terpadu |
| 2 | ✅ Hide Second Tick (iPhone) | ❌ | Sembunyikan centang kedua saat kirim ke pengguna iPhone |
| 3 | 💬 Auto Reply | ❌ | Balas otomatis berdasarkan kata kunci, delay, dan jam aktif |
| 4 | 📞 Call & Video Recording HD | ⚠️ | Rekam panggilan suara/video dasar ada, HD (WAV/OPUS/AAC) belum |
| 5 | 🎨 Screen UI Theme Builder | ⚠️ | Kustomisasi dasar ada, theme builder + live preview + drag & drop + AI belum |
| 6 | 🗑️ Trash & Deleted Recovery | ❌ | Pulihkan pesan dan media yang dihapus via hook-based interception |
| 7 | 🤖 Groq AI Translator (Composer) | ⚠️ | Translate pesan masuk ada (beta), translate di chat composer belum |
| 8 | ☁️ WhatsVault Backup | ❌ | Backup WA (chat+media) ke multi-GDrive via Ktor server + Cloudflare Tunnel mekanisme menyerupai 9drive |
| 9 | 🔐 Enhanced KeyBox & Bootloader Spoofer | ⚠️ | KeyBox manual ada, auto-fetch + fingerprint Pixel Canary + hourly refresh belum |

---

## 🗓️ Message Scheduler
Jadwalkan pengiriman pesan pada waktu tertentu, termasuk dukungan pesan berulang (recurring).

**Rencana:**
- Penjadwalan pesan satu kali dengan tanggal & waktu
- Pengiriman berulang (harian, mingguan, dll.)
- Manajemen antrian pesan terjadwal

---

## ✅ Hide Second Tick (iPhone Recipients)
Sembunyikan tanda centang kedua khusus saat mengirim ke pengguna iPhone, termasuk bypass deteksi iOS.

---

## 💬 Auto Reply
Balas pesan otomatis berdasarkan kata kunci dengan dukungan delay dan pembatasan jam aktif.

**Rencana:**
- Kata kunci kustom (custom keywords)
- Delay balasan yang dapat dikonfigurasi
- Pembatasan jam aktif (jam operasional)

---

## 📞 Call & Video Call Recording — HD
Rekam panggilan suara dan video dengan kualitas tinggi.

**Status:** Implementasi dasar sudah ada, peningkatan kualitas HD (WAV/OPUS/AAC) belum.

**Rencana:**
- Dukungan format WAV, OPUS, AAC
- Kualitas rekaman high-quality

---

## 🎨 Screen UI Customization — Theme Builder
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

## 🤖 Groq AI Translator — In Chat Composer
Terjemahan berbasis AI langsung di chat composer sebelum pesan dikirim.

**Status:** Integrasi Groq/Google Translate untuk terjemahan pesan masuk sudah ada (beta). Translate di composer belum.

**Rencana:**
- Translate teks di input field sebelum kirim
- Dukungan multi-bahasa via Groq AI

**Known Issues (inline translator — perlu diperbaiki):**
- Groq butuh API key manual — tidak ada fallback ke Google jika key kosong
- Bahasa target tidak bisa dipilih manual — selalu ikut locale sistem
- Terjemahan tidak persisten — hilang saat conversation di-close atau WA restart, tidak ada cache ke DB
- `TranslatorWrapperAdapter.instance` static singleton — buka dua conversation sekaligus, instance lama tertimpa, terjemahan conversation sebelumnya hilang
- Label popup hardcoded Bahasa Indonesia ("Terjemahkan" / "Sembunyikan terjemahan") — tidak ikut locale
- Google Translate pakai endpoint tidak resmi (`client=gtx`) — rawan rate-limit atau breaking change
- Tidak ada loading indicator saat menunggu hasil terjemahan
- Error hanya tampil Toast tanpa opsi retry
- Groq kadang salah interpretasi slang/bahasa daerah — alih-alih terjemahkan, malah balik bertanya (contoh: "wis maem yank?" → harusnya "sudah makan sayang?" tapi Groq jawab "apa yang kamu maksud dengan yank?"); perlu system prompt yang lebih kuat + contoh konteks bahasa Indonesia/Jawa/slang

---

## ☁️ WhatsVault Backup — Native Cloud Storage
Backup WhatsApp (chat + media) ke Google Drive multi-akun via storage gateway native yang berjalan di HP user sendiri. Menjadi fallback jika backup bawaan WhatsApp tidak tersedia, atau sebagai alternatif pilihan user.

**Arsitektur:**
```
HP User (rooted)
├── WhatsVault
│   ├── Backup scheduler (WorkManager - PeriodicWorkRequest)
│   ├── Setup wizard: GDrive OAuth + CF tunnel token
│   └── Backup UI (status, history, restore)
├── BackupService (foreground service)
│   ├── Copy msgstore.db + WAL dari /data/data/com.whatsapp/databases/ (root)
│   ├── Copy media dari /sdcard/WhatsApp/
│   └── Upload ke 9drive via localhost HTTP
├── 9drive Native (Ktor HTTP server, foreground service)
│   ├── Expose HTTP API di localhost:8080
│   ├── Route ke multiple Google Drive accounts
│   └── Bypass 5GB limit via multi-account pooling
└── cloudflared arm64 (foreground service)
    ├── Binary: cloudflared-linux-arm64 (download saat setup)
    ├── Tunnel localhost:8080 → 9drive.{user-domain}
    └── Autentikasi via CF Zero Trust token
```

**Keputusan teknis:**
- Root access via `libsu` (Magisk/Shizuku)
- 9drive backend: Kotlin native (Ktor + NanoHTTPD) — bukan Node.js
- cloudflared binary: download saat setup pertama (~17MB)
- Backup trigger: scheduled via WorkManager
- CF tunnel token: guided setup dari dalam app WhatsVault
- Subdomain format: `9drive.{user-domain}`
- Google Drive OAuth: per-akun WebView flow, multi-account

**Rencana implementasi:**
1. Setup wizard CF Zero Trust + GDrive OAuth
2. 9drive Ktor server (multi-account GDrive routing)
3. cloudflared binary management (download, start, stop)
4. BackupService (root file copy + upload)
5. WorkManager scheduled backup
6. UI restore flow (download → extract → overwrite WA db)

**Pertimbangan open:**
- Enkripsi backup sebelum upload ke GDrive (plain vs AES)
- Notifikasi persistent 2 foreground service
- Battery optimization exemption guide

## 🔐 Enhanced KeyBox & Bootloader Spoofer — AlwaysStrong Integration
Perkuat fitur Custom KeyBox dan Bootloader Spoofer yang sudah ada dengan kemampuan auto-fetch KeyBox + Fingerprint, sehingga TEE valid, bootloader aman, dan Play Integrity diharapkan mencapai STRONG — meskipun untuk Android < 13 STRONG masih sulit.

**Goals:**
- TEE valid via KeyBox yang selalu fresh dan tidak di-revoke Google
- Bootloader spoofing aman via fingerprint Pixel Canary terbaru
- Play Integrity STRONG (best-effort; Android 13+ lebih mudah, Android < 13 terbatas)

**Arsitektur (port dari AlwaysStrong ke Kotlin native):**

| Komponen AlwaysStrong | Port ke WhatsVault |
|---|---|
| `keybox_fetch.sh` + `asfetch` | Kotlin HTTP client (OkHttp), fetch `keybox.xml` base64 dari mirror |
| `pif_native_fetch.sh` | Kotlin crawler: `developer.android.com` → `flash.android.com` → GFlash API → Pixel Canary fingerprint |
| `service.sh` hourly loop | WorkManager PeriodicWorkRequest (interval default 1 jam, configurable) |
| `conflict_scan.sh` | Root check: disable conflicting modules (TrickyStore, PlayIntegrityFix) via `libsu` |
| WebUI toggles | Settings UI: `no_auto_fp`, `no_auto_keybox` toggle |

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

*Dokumen ini diperbarui sesuai perkembangan fitur.*

