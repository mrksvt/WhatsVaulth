# Contact Voice TTS - Arsitektur & Schema

Ringkasan arsitektur fitur Contact Voice TTS di project WhatsVault.

## Ringkasan

Fitur ini memungkinkan setiap kontak WhatsApp punya suara TTS khas (di-clone
dari voice note yang pernah mereka kirim). UX meniru pola fitur Custom
Translator yang sudah ada: balon sintetis di bawah pesan asli, opsi popup di
tap balon, toggle di menu header chat.

## Dua proses, satu APK

```
┌─────────────────────┐        AIDL (WaeIIFace)         ┌──────────────────────┐
│  Proses WhatsApp    │  ──────────────────────────────► │  Proses WhatsVault   │
│  (com.whatsapp)     │ ◄────────────────────────────── │  (app host)          │
│                     │                                  │                      │
│  Hook via Xposed    │  openFile / exists / listFiles   │  Room DB             │
│  VoiceTTSFeature    │  requestTTS                      │  WorkManager         │
│  GoogleTranslate    │  isAutoTtsEnabled                │  TTS engine          │
│  TranslatorWrapper  │  setAutoTtsEnabled               │  VoiceTtsWorker      │
│                     │  getContactVoiceProfileStatus     │  HookBinder          │
│  SyntheticBubble    │  registerIncomingVoiceNote        │  App.kt              │
│  VoiceNoteViewClone │                                  │                      │
└─────────────────────┘                                  └──────────────────────┘
```

Binder sisi hook: `WppCore.getClientBridge(): WaeIIFace?`
(ke `BridgeClientKt`/`ProviderClientKt` -> `HookBinder`).

## Alur data

### Voice note masuk (Tugas A)

```
FMessageWpp hook (bg thread)
  ├─ mediaType != 2 && != 82 → skip
  ├─ isFromMe → skip
  ├─ bridge.exists(destPath) → duplikat → skip
  ├─ computeFileHash(file) [SHA-256]
  ├─ Utils.copyFile → dest: /files/voice_notes/<jid>_<msgId>.opus
  └─ bridge.registerIncomingVoiceNote(jid, hash, path, 0, ts)
       └─ app side:
            ├─ message_hash exists? → return ""
            ├─ message_hash INSERT
            └─ WorkManager: EXTRACT_EMBEDDING
                 ├─ copy → /files/voice_embeddings/<jid>.emb
                 ├─ voice_profiles UPSERT (merge strategy: see below)
                 └─ done
```

### Manual TTS (Tugas E#1)

```
message tap → PopupMenu "Baca dengan Suara"
  ├─ SyntheticBubbleStore.addBubble(msgId, TTS, LOADING)
  ├─ TranslatorWrapperAdapter.refreshBubbles(jid)
  ├─ bridge.requestTTS(jid, msgId, text)  [enqueue, async]
  ├─ poll bridge.exists(expectedPath) every 500ms
  │    expectedPath = TtsCachePaths.cacheFile(jid, sha256(text))
  ├─ found → bubble READY(path) + refresh
  └─ >30s → bubble ERROR + refresh
```

### Auto TTS on incoming (Tugas E#3)

```
loadNewMessageWithMediaMethod hook
  ├─ isFromMe → skip
  ├─ bridge.isAutoTtsEnabled(jid) != 1 → skip
  ├─ addBubble(msgId, TTS, LOADING)
  ├─ bridge.requestTTS(jid, msgId, text)
  └─ poll + refresh (same as manual TTS)
```

### TTS generation (Tugas B, app side)

```
VoiceTtsWorker.doGenerateTts()
  ├─ tts_cache lookup: (contactId, textHash)
  │    hit + file exists → return success (cache hit)
  ├─ voice_profiles lookup: contactId
  │    found → voiceId = embeddingPath
  │    not found → voiceId = null (default voice)
  ├─ FallbackTtsEngine.speakToFile(text, voiceId, audioFile)
  │    Android TextToSpeech -> .wav
  ├─ tts_cache INSERT (deterministic filename)
  └─ return success
```

### Notification playback (Tugas F)

```
SISI HOOK (proses WhatsApp):
  NotificationManager.notify() hooked by NotificationPlayHelper.install()
    ├─ extractText(notification) → textHash = sha256(text)
    ├─ audio READY di readyByHash[hash]? 
    │    ya → addActionToNotification(notification, hash)
    │          buildPlayAction() → mActions.add(action)
    │          [PendingIntent broadcast → TtsPlayReceiver]
    └─ audio belum siap + requestedHashes[hash] ada?
         ya → parkir notifikasi di pendingRepost[hash]
              begitu cacheForNotificationPlayback() dipanggil:
              → repost notification ID sama + play action

SISI APP (proses WhatsVault):
  TtsPlayReceiver (BroadcastReceiver)
    ACTION_PLAY_TTS
      ├─ validasi path: canonicalFile.startsWith(tts_cache/) && endsWith .wav
      └─ TtsPlaybackService.startPlay(audioPath, messageId)
           ├─ startForeground(channel: wae_tts_playback)
           ├─ AudioFocusRequest GAIN_TRANSIENT_MAY_DUCK
           ├─ MediaPlayer(path) → start()
           ├─ onCompletion / ACTION_STOP
           │    ├─ abandonAudioFocusRequest
           │    ├─ stopForeground(REMOVE)
           │    └─ stopSelf()
           └─ AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK → volume 0.2
```

## Schema Database (Room, sisi app)

```
┌─────────────────────────────────────────────────────────────────────┐
│ voice_profiles                                                      │
├─────────────────────────────────────────────────────────────────────┤
│ contact_id (PK)       │ TEXT │ JID kontak WhatsApp                  │
│ embedding_path        │ TEXT │ path ke file .emb (speaker embedding)│
│ auto_tts_enabled      │ BOOL │ default false                        │
│ source_count          │ INT  │ jumlah voice note yang diproses      │
│ last_source_message_hash │ TEXT │ hash voice note terakhir          │
│ created_at            │ LONG │ epoch ms                             │
│ updated_at            │ LONG │ epoch ms                             │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ tts_cache                                                           │
├─────────────────────────────────────────────────────────────────────┤
│ cache_id (PK, auto)   │ LONG │ auto-increment                       │
│ message_id            │ TEXT │ ID pesan WA asli                     │
│ contact_id            │ TEXT │ FK konseptual ke voice_profiles      │
│ text                  │ TEXT │ teks asli                            │
│ text_hash             │ TEXT │ SHA-256(teks), UNIQUE dengan contact │
│ audio_file_path       │ TEXT │ path ke .wav hasil generate          │
│ generated_at          │ LONG │ epoch ms                             │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ message_hash                                                        │
├─────────────────────────────────────────────────────────────────────┤
│ message_hash (PK)     │ TEXT │ SHA-256 isi file audio               │
│ message_id            │ TEXT │ (opsional)                           │
│ contact_id            │ TEXT │                                      │
│ audio_path            │ TEXT │ path voice note yang sudah di-copy   │
│ duration_ms           │ LONG │ 0 jika tidak diketahui               │
│ created_at            │ LONG │ epoch ms                             │
└─────────────────────────────────────────────────────────────────────┘
```

### Index

```
tts_cache:    UNIQUE(contact_id, text_hash)
              INDEX(message_id)
voice_profiles: UNIQUE(contact_id)
```

### Embedding merge strategy

Kontak yang mengirim N voice note akan punya N embedding sample.
Strategi: **latest-wins** - embedding terbaru menimpa yang lama
(`source_count` ditambah tiap kali, `last_source_message_hash` diperbarui).
Trade-off: sederhana, tidak perlu batching; kontak dengan voice note
consisten menghasilkan embedding yang konvergen. Kontak dengan variasi nada
bicara tinggi mungkin perlu averaging (future improvement).

## SyntheticBubbleStore (Tugas D)

```
Map<originalMessageId, MutableList<SyntheticBubble>>

FlattenedListIndex:
  for each originalMessageId in realAdapter order:
    push Original(msgId)
    for each bubble in store[msgId] (insertion order):
      push Synthetic(msgId, bubble)
```

Invariants:
- bubble HANYA refer ke `originalMessageId`, TIDAK ada parent/child chaining
- hapus bubble di tengah: yang di bawahnya otomatis "naik" (index regenerate)
- insert selalu di akhir list per message, TIDAK pernah di tengah atau reorder

## Penamaan file cache deterministik

```
TtsCachePaths.cacheFile(contactId, sha256(text))
  = /data/data/<appId>/files/tts_cache/<sanitized_jid>_<sha256>.wav

Worker menulis:  <path>.tmp  → rename() →  <path>.wav
Hook polling:    bridge.exists(<path>)
```

Rename atomik dalam satu filesystem menjamin `exists=true` hanya setelah file
utuh (tidak partial read).

## Sumber data: Android TTS engine

`FallbackTtsEngine` = `android.speech.tts.TextToSpeech` dengan `Locale.getDefault()`.
Ini fallback sederhana; voice cloning zero-shot (OpenVoice V2 / YourTTS) bisa
diintegrasikan nanti via ONNX Runtime (onnxruntime-android sudah di Maven
Central, SDK kompatibel). Embedding disimpan sebagai JSON array float di file
`.emb`; voiceId null -> default voice.

## Lokasi file (LOCAL ONLY, TIDAK pernah upload)

| Data | Path |
|---|---|
| Voice note asli | `/data/data/<appId>/files/voice_notes/` |
| Embedding | `/data/data/<appId>/files/voice_embeddings/` |
| Cache audio TTS | `/data/data/<appId>/files/tts_cache/` |
