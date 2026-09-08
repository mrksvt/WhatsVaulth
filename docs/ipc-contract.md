# IPC Contract - Sisi Hook <-> Sisi App (Contact Voice TTS)

Dokumen ini mendefinisikan kontrak komunikasi lintas proses antara proses
WhatsApp (tempat kode hook di-inject via LSPosed) dan proses aplikasi
WhatsVault sendiri (tempat compute berat TTS berjalan).

## Mekanisme

Reuse channel IPC yang SUDAH ada di project ini: AIDL `WaeIIFace`
(`app/src/main/aidl/com/mrksvt/waen/xposed/bridge/WaeIIFace.aidl`),
diimplementasikan oleh `HookBinder` (sisi app) dan diakses dari sisi hook via
`WppCore.getClientBridge(): WaeIIFace?` (`BridgeClientKt` / `ProviderClientKt`).

TIDAK ada channel IPC baru. Semua method Voice TTS ditambahkan sebagai method
baru pada interface yang sama.

## Konvensi umum

- `contactId`  = JID mentah kontak/percakapan (mis. `628123456789@s.whatsapp.net`, `...@g.us`).
- `messageId`  = ID pesan WA (string hex/UUID dari `FMessageWpp.key.messageID`).
- `textHash`   = SHA-256 hex dari teks pesan (UTF-8).
- Semua method adalah **synchronous binder call** yang murah; pekerjaan berat
  (inference) selalu async di sisi app (WorkManager). Sisi hook TIDAK menunggu
  hasil di binder call - hasilnya diambil via polling `exists()` (lihat bawah).
- Nilai error: method yang mengembalikan `String` memakai konvensi project:
  string kosong `""` = sukses, non-kosong = pesan error.
- Method yang mengembalikan `Int` memakai `1/0` sebagai boolean, `-1` = error.

## Kontrak data file

Sisi hook dan sisi app berbagi filesystem device yang sama tapi UID berbeda.
Akses file antar-UID tidak diizinkan langsung, jadi:

- **Tulis file dari sisi hook** -> lewat `openFile(path, create=true)`
  (mengembalikan `ParcelFileDescriptor` milik proses app, pola yang sudah
  dipakai `Utils.copyFile` / AntiRevoke).
- **Baca keberadaan file dari sisi hook** -> lewat `exists(path)`.
- **Baca daftar file** -> `listFiles(path)` (dipakai untuk maintenance, bukan hot path).

### Lokasi storage (semua LOCAL ONLY, private app storage)

| Data | Path |
|---|---|
| Voice note hasil hook | `/data/data/<appId>/files/voice_notes/` |
| Speaker embedding | `/data/data/<appId>/files/voice_embeddings/` |
| Cache audio TTS | `/data/data/<appId>/files/tts_cache/` |

`<appId>` = `com.mrksvt.waen` (flavor whatsapp) atau suffix build-nya.
Path dihitung dari `BuildConfig.APPLICATION_ID` di kedua sisi lewat
`TtsCachePaths` (module-internal, tidak di-hardcode per sisi).

### Penamaan file cache TTS (kontrak penting)

```
tts_cache/<sanitizedContactId>_<sha256(text)>.wav
```

- `sanitizedContactId`: karakter non `[A-Za-z0-9_-]` diganti `_`.
- Deterministik: teks + kontak yang sama selalu menghasilkan path yang sama.
- Worker sisi app menulis ke `<name>.tmp` lalu `rename()` ke `<name>.wav`,
  sehingga `exists(path)` yang true di sisi hook menjamin file sudah utuh
  (rename atomik dalam satu filesystem).

## Interface methods (Voice TTS)

### requestTTS

```kotlin
fun requestTTS(contactId: String, messageId: String, text: String): String
```

- **Arah**: hook -> app.
- **Perilaku**: sisi app enqueue `VoiceTtsWorker(ACTION_GENERATE_TTS)` via
  WorkManager (reliable, boleh tertunda Doze tapi eventual). Panggilan binder
  sendiri langsung return.
- **Return**: `""` = berhasil di-enqueue; non-kosong = pesan error.
- **Hasil**: TIDAK dikembalikan lewat callback binder. Sisi hook polling
  `exists(TtsCachePaths.cacheFile(contactId, sha256(text)))` tiap 500 ms
  (timeout 30 s), lalu menandai balon sintetis READY dengan path itu.
  Alasan: AIDL callback dua-arah butuh `oneway` + lifecycle objek binder di
  proses host yang berisiko bocor saat WhatsApp restart; polling `exists()`
  murah, stateless, dan tetap benar setelah proses hook restart.
- Cache hit di sisi app (teks+kontak pernah digenerate) membuat worker return
  cepat tanpa inferensi.

### isAutoTtsEnabled

```kotlin
fun isAutoTtsEnabled(contactId: String): Int
```

- `1` = auto TTS aktif untuk kontak, `0` = tidak, `-1` = error.
- Default semua kontak: `0` (fitur hanya aktif untuk kontak yang di-flag manual).
- Dipakai sisi hook pada titik penerimaan pesan baru (Tugas E#3) dan menu
  titik-3 (Tugas E#2).

### setAutoTtsEnabled

```kotlin
fun setAutoTtsEnabled(contactId: String, enabled: Int): Int
```

- `enabled`: `1` on, `0` off. Return `0` sukses, `-1` error.
- Menyimpan flag di tabel `voice_profiles` (baris dibuat otomatis bila belum
  ada, `auto_tts_enabled` default false).

### getContactVoiceProfileStatus

```kotlin
fun getContactVoiceProfileStatus(contactId: String): Int
```

- Enum status sebagai Int (AIDL tidak support Kotlin enum):
  - `0` = `NO_PROFILE` - belum ada embedding; TTS akan fallback ke default voice.
  - `1` = `HAS_PROFILE` - embedding tersedia.
  - `-1` = error.
- Dipakai untuk indikator UI di menu titik-3 dan untuk menentukan voice.

### registerIncomingVoiceNote

```kotlin
fun registerIncomingVoiceNote(
    contactId: String,
    messageHash: String,
    audioPath: String,
    durationMs: Long,
    timestamp: Long
): String
```

- **Arah**: hook -> app, dipanggil SETELAH sisi hook menyalin file voice note
  ke `files/voice_notes/` via `openFile` (jangan kirim path internal WA -
  proses app tidak bisa membacanya).
- `messageHash`: SHA-256 isi file audio. Ini **guard dedup**: bila hash sudah
  ada di tabel `message_hash`, sisi app langsung return `""` tanpa enqueue
  extraction, dan sisi hook sebaiknya cek dulu lewat `exists()` pada path
  tujuan agar tidak copy dua kali.
- `durationMs`: durasi voice note (0 jika tidak diketahui).
- Return: `""` = diterima (baru atau duplikat), non-kosong = error.
- Efek samping: enqueue `VoiceTtsWorker(ACTION_EXTRACT_EMBEDDING)`.

### Method warisan yang dipakai ulang

| Method | Pakai untuk |
|---|---|
| `openFile(path, create)` | Salin voice note dari proses WA ke storage app |
| `createDir(path)` | Menyiapkan folder `voice_notes/` |
| `exists(path)` | Polling hasil TTS + cek duplikat |
| `listFiles(path)` | Maintenance/cleanup cache |

## Sequence diagram (ringkas)

```
Voice note masuk:
  WA process                         WhatsVault app process
  ---------                          ----------------------
  hook media in (bg thread)
    sha256(file) -> dedup check
    openFile() -> copy .opus  ----->  files/voice_notes/<jid>_<msgId>.opus
    registerIncomingVoiceNote() --->  WorkManager: EXTRACT_EMBEDDING
                                       -> embedding .emb + voice_profiles upsert

Manual/auto TTS:
  addSyntheticBubble(LOADING)
  requestTTS()  ------------------>  WorkManager: GENERATE_TTS
                                      cache hit? done
                                      else TTS -> <jid>_<hash>.wav (tmp->rename)
  poll exists(path) every 500ms <--
  found -> bubble READY(path)
  >30s  -> bubble ERROR

Playback notifikasi:
  Notification action (PendingIntent broadcast, app process)
    -> TtsPlayReceiver -> foreground TtsPlaybackService
       AudioFocus -> MediaPlayer(path) -> release focus -> stopSelf
```

## Aturan keamanan & stabilitas

- Semua call dari sisi hook WAJIB dibungkus try-catch; kegagalan IPC hanya
  menandai balon ERROR, tidak boleh meng-crash-kan WhatsApp.
- Tidak ada data suara yang dikirim ke jaringan. Seluruh path di private
  storage app; tidak ada `FileProvider` eksternal untuk data ini.
- Binder call tidak pernah melakukan inferensi; hanya enqueue work.
- `oneway` TIDAK dipakai: method cepat (enqueue/lookup) lebih mudah di-debug
  dan error string return lebih berguna.
