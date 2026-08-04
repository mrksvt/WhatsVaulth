# Coming Soon

Fitur-fitur berikut sedang dalam perencanaan atau pengembangan.

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

---

*Dokumen ini diperbarui sesuai perkembangan fitur.*

