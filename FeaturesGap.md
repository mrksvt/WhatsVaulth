# FeaturesGap.md - WhatsVault (fork) vs OriginalWaEnhancer (upstream Dev4Mod/WaEnhancer)

**Tanggal:** 2026-10-28  
**Fork:** git@github.com:mrksvt/WhatsVaulth.git (HEAD 60d940cd)  
**Upstream:** https://github.com/Dev4Mod/WaEnhancer.git (HEAD 82c7a50a, 1.6.2)  
**Version:** 1.6.2 (fork mengikuti versi upstream yang diport)  
**Fork package:** `com.mrksvt.waen` | **Upstream package:** `com.wmods.wppenhacer`  
**Git histories:** unrelated (no merge-base) - sync manual, bukan git merge

---

## Ringkasan

| Metric | Fork (WhatsVault) | Upstream |
|--------|-------------------|----------|
| Total .kt/.java source | 201 | 165 |
| Registered features | 71 | 51 |
| WA versions supported | 2.26.43–2.26.45 | 2.26.25–2.26.41 |
| Business build variant | ya (restored) | ya |
| Kotlin features | majority | majority |
| Java rewrites (Kt→Java) | 7 files | original |

---

## Fitur Per Kategori

### CUSTOMIZATION (Fork: 14+2=16 | Orig: 14)

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| BubbleColors | Y | Y | shared |
| ContactVerify | Y | Y | shared |
| CustomThemeV2 | Y | Y | shared |
| CustomTime | Y | Y | shared |
| CustomToolbar | Y | Y | shared |
| CustomView | Y | Y | shared |
| DefaultEmoji | Y | Y | shared |
| FilterGroups | Y | Y | shared |
| FloatingBottomBar | Y | Y | shared |
| HideSeenView | Y | Y | shared |
| HideTabs | Y | Y | shared |
| IGStatus | Y | Y | shared |
| NewSettings | Y | Y | shared |
| SeparateGroup | Y | Y | shared |
| ShowOnline | Y | Y | shared |
| CustomFont | Y | N | NEW-FORK |
| CustomTick | Y | N | NEW-FORK |

---

### GENERAL (Fork: 21 | Orig: 19)

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| AntiRevoke | Y | Y | shared |
| AntiWa | Y | N | NEW-FORK |
| CallType | Y | Y | shared |
| ChatLimit | Y | Y | shared |
| CustomPrivacy | Y | N | NEW-FORK |
| DeleteStatus | Y | Y | shared |
| DndMode | Y | Y | shared |
| FreezeLastSeen | Y | Y | shared |
| HideChat | Y | Y | shared |
| LockedChatsEnhancer | Y | N | NEW-FORK |
| NewChat | Y | Y | shared |
| Others | Y | Y | shared |
| PinnedLimit | Y | Y | shared |
| SeenTick | Y | Y | shared |
| ShareLimit | Y | Y | shared |
| ShowEditMessage | Y | Y | shared |
| TagMessage | Y | Y | shared |
| Tasker | Y | Y | shared |
| TypingPrivacy | Y | Y | shared |
| TrashRecovery | Y | N | NEW-FORK |

---

### LISTENERS (Fork: 3 | Orig: 3) - identical

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| ContactItemListener | Y | Y | shared |
| ConversationItemListener | Y | Y | shared |
| MenuStatusListener | Y | Y | shared |

---

### MEDIA (Fork: 6 | Orig: 6) - identical

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| CallRecording | Y | Y | shared |
| DownloadProfile | Y | Y | shared |
| DownloadViewOnce | Y | Y | shared |
| MediaPreview | Y | Y | shared |
| MediaQuality | Y | Y | shared (fork: +CBR ported) |
| StatusDownload | Y | Y | shared |

---

### OTHERS (Fork: 24 | Orig: 16)

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| ActivityController | Y | Y | shared |
| AudioTranscript | Y | Y | shared |
| BackupRestore | Y | Y | shared |
| Channels | Y | Y | shared |
| ChatFilters | Y | Y | shared |
| CopySelectionMessage | Y | Y | shared |
| CopyStatus | Y | Y | shared |
| DebugFeature | Y | Y | shared |
| GoogleTranslate | Y | Y | shared |
| GroupAdmin | Y | Y | shared |
| JumpFirstMessage | Y | Y | shared |
| MenuHome | Y | Y | shared |
| Stickers | Y | Y | shared |
| TextStatusComposer | Y | Y | shared |
| ToastViewer | Y | Y | shared (fork: refactored) |
| AboutContactPicker | Y | N | NEW-FORK |
| ComposerTranslator | Y | N | NEW-FORK |
| DevEngineering | Y | N | NEW-FORK |
| GroqTranslator | Y | N | NEW-FORK |
| MarketingMessagesDebug | Y | N | NEW-FORK |
| MarketingMessagesFix | Y | N | NEW-FORK |
| PremiumMessageFix | Y | N | NEW-FORK |
| TranslatorWrapperAdapter | Y | N | NEW-FORK |
| ViewInspector | Y | N | NEW-FORK |

---

### PRIVACY (Fork: 11 | Orig: 11) - identical

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| CallPrivacy | Y | Y | shared |
| HideSeen | Y | Y | shared |
| ViewOnce | Y | Y | shared |

---

### TRANSLATOR (Fork: 1 | Orig: 0)

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| TranslatorSettingsFragment | Y | N | NEW-FORK (settings UI) |

---

### xposed/AntiUpdater (Orig: 1 | Fork: 0)

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| AntiUpdater | N | Y | MISSING-FORK (sengaja dihapus; HomeFragment.java:137: "Always hide update card") |

---

## Upstream Commits Tidak Di-Port

| Commit | Deskripsi | Status |
|--------|-----------|--------|
| 883041c6 | ToastViewer bugfix (changelog) | SUDAH DI-PORT |
| 1f3792a4 | ToastViewer refactor: cleanup scheduling, ConcurrentHashMap, AtomicBoolean guard, single daemon thread | SUDAH DI-PORT |
| d16fa87d | arrays: support WA 2.26.33.xx | TIDAK RELEVAN (fork sudah 2.26.35.xx) |
| 5d1735a0 | changelog update | TIDAK RELEVAN |
| 6756579a | MediaQuality: constant bitrate mode (CBR) | SUDAH DI-PORT |

---

## Perubahan Yang Sudah Dilakukan (2026-09-06)

1. **ToastViewer.kt** - port full upstream refactor:
   - `HashMap` → `ConcurrentHashMap` (thread safety)
   - `CompletableFuture.runAsync` → `Utils.databaseExecutor.execute` (dedicated executor)
   - Tambah `AtomicBoolean cleanupStarted` guard (prevent double cleanup)
   - Cleanup interval: 1s → 30s (fix excessive cleanup)
   - `ScheduledThreadPool(1)` → single daemon thread executor
   - Hapus `@Synchronized` yang redundant
   - `continue` bukan `return` dalam loop (fix early exit bug)
   - Safe null handling untuk `getWaContactFromJid` (nullable fallback)
   - Select specific columns di query (performance)

2. **MediaQuality.kt** - tambah CBR support:
   - Import `android.media.MediaCodecInfo`
   - Set `videoBitrateMode = BITRATE_MODE_CBR` (constant bitrate = kualitas konsisten)

3. **build.gradle.kts** - restore business flavor:
   - Tambah `create("business")` block (applicationIdSuffix `.w4b`)
   - `applicationVariants.all`: dynamic APK name + app_name per flavor
   - Business APK: `WhatsVault-Business-<version>.apk` + `application-label: WhatsVault Business`

4. **.github/workflows/build-business.yml** - CI workflow business:
   - Consistent dengan fork conventions (NDK+CMake, dynamic artifact name, verify native libs)

---

## UI Files: Kotlin→Java Rewrite (Tidak Perlu Di-Port)

Fork me-rewrite 7 file upstream dari Kotlin ke Java. Isi functionally equivalent:

| File | Upstream | Fork |
|------|----------|------|
| HomeFragment | .kt | .java |
| AudioPlayerDialog | .kt | .java |
| RecordingsFragment | .kt | .java |
| Recording | .kt | .java |
| TextEditorActivity | .kt | .java |
| FileReaderPreference | .kt | .java |
| WallpaperView | .kt | .java |

---

## Fitur Core Framework

| Komponen | Fork | Orig | Notes |
|----------|------|------|-------|
| Feature.kt | Y | Y | base class |
| FeatureLoader.kt | Y | Y | 71 vs 51 plugins |
| WppCore.kt | Y | Y | WA interaction |
| WaCallback.kt | Y | Y | activity lifecycle |
| ActivityStateRegistry.kt | Y | N | NEW-FORK |
| Unobfuscator.kt | Y | Y | DexKit engine |
| UnobfuscatorCache.kt | Y | Y | DexKit cache |
| HookOverrideStore | Y | N | NEW-FORK (runtime hook override) |
| HookAuditLogger | Y | N | NEW-FORK |
| TelegramReporter | Y | N | NEW-FORK (crash reports) |
| ErrorMessageTranslator | Y | N | NEW-FORK |
| db/ (Room layer) | Y | Y | AntiRevokeDB + MessageStore |

### IPC Bridge (identical)

| Komponen | Fork | Orig |
|----------|------|------|
| BridgeClientKt | Y | Y |
| ProviderClientKt | Y | Y |
| BridgeService | Y | Y |
| BridgeReceiver | Y | Y |

### Spoofer + Downgrade (identical)

| Komponen | Fork | Orig |
|----------|------|------|
| HookBL | Y | Y |
| AntiUpdater | N | Y (dihapus sengaja) |

---

## Build & CI Gaps

| Aspek | Fork | Orig |
|-------|------|------|
| productFlavors | whatsapp + business | whatsapp + business |
| CI: build-whatsapp.yml | Y (NDK+CMake) | Y |
| CI: build-business.yml | Y (restored) | Y |
| CI: dependabot.yml | N | Y |
| CI: dependabot-ci.yml | N | Y |
| compileSdk | 36 | 37 |
| ndkVersion | 28.2.13676358 | tidak ada |
| crowdin.yml (translations) | N | Y |
| LICENSE | N | Y |
| dexkit-android.source.jar | N | Y |

---

## Port WaEnhancer 1.6.2 (f4a8da1e..82c7a50a)

Status: seluruh commit fitur/fix pada rentang tersebut sudah diport ke fork, kecuali item N/A.

| Upstream commit | Topik | Status di fork |
|-----------------|-------|----------------|
| f2551e79 | SharedPreferencesWrapper skip abstract class | ported |
| 2d2301a7 | Finder channel header, outgoing call, locked chats | ported (outgoing call N/A: finder tidak ada di fork) |
| 2fc180b0 | Hide Delivered untuk nomor tidak dikenal | ported |
| 01a8bb72 | AntiRevoke hasil type-safe | ported |
| 3d01fad6 | Others: schedule message + View Once text | ported |
| 1ef4e190 | ScopeHook SettingsProvider + visibility | ported (ScopeHook.java, legacy API) |
| 8db37f41 | CustomPrivacy key per-LID | ported |
| e5c347ec | IGStatus force load status | ported |
| 5db954a2 | StickerColoredOutline Rect | ported |
| cc51ac76 | WA/Business 2.26.41.xx | sudah ada di arrays.xml |
| 1abe476b | ChatLimit via msgstore WhatsApp | ported |
| 790a7e22 | PinnedLimit bypass limit | ported |
| 7c4d1401 | ProGuard optimasi + nama tetap terbaca | ported |
| 2f7ea490 | R8 obfuscation + shrink resources | adaptasi: `-dontobfuscate` dipertahankan, shrink resources + locale filter diaktifkan |
| 509c4ec1 | TextStatusComposer param order | ported |
| bc5029d8 | RootDiagnostics Magisk DenyList | ported |
| ebc7d339 | Terjemahan AssemblyAI | ported |
| df141fe9 | Flyme checkDowngrade log | N/A: Patch.java sudah silent |
| 570d0e94 | YukiHookAPI 1.5.0 | N/A: fork tidak memakai YukiHookAPI |
| 42f16320 | CI release variants + mapping upload | N/A |
| 9b68b21f, 76572693, changelog/chore commits | versi/README/changelog | N/A |

---

## Port WaEnhancer 1.6.3 (82c7a50a..36c22aed)

**Tanggal:** 2026-10-29
**Upstream:** https://github.com/Dev4Mod/WaEnhancer.git (36c22aed)
**Range:** 12 commit, port ke API legacy `de.robv.android.xposed`

| Upstream commit | Topik | Status di fork |
|-----------------|-------|----------------|
| 39907bc3 | DexKit 2.3.0 dependency | ported: aar lokal diganti `org.luckypray:dexkit:2.3.0`, `usingNumbers` memakai `UsingNumberData` |
| c0cfdd3a | DexKitManager lazy open | ported: `devkit/DexKitManager.kt` baru, lease count + idle close 30 detik |
| dcda601d | ViewHookDispatcher | adapted: dispatcher baru di API legacy, konsumen CustomThemeV2/CustomView/FloatingBottomBar/HideTabs/MediaPreview/Stickers dipindah |
| b0a86814 | Others filter item | ported: hook `View.invalidate` diganti `onAttachedToWindow` + `onSetFlags` |
| 549218e7 | ReflectionUtils best-match cache | N/A: fork `ReflectionUtils` tidak punya API name-based `callMethod`/`findMethodBestMatchByArgs` |
| 6813a52d | JID device-suffix regex | ported: regex dikompilasi sekali di `WppCore` dan `FMessageWpp.UserJid` |
| 5d1b37a8 | Wallpaper prefetch | adapted: `WallpaperView` di fork bahasa Java, ditulis ulang dengan load async + `prefetch()` statis |
| 847bba6b | Bridge force-start | ported: `performConnection(forceStart)` hanya force-start saat percobaan pertama gagal |
| a0e1c894 | Hot-path trims | adapted: cache warna `CustomThemeV2`, lazy drawable hook `CustomView`, id resource dihitung sekali, `Utils.getID` pakai `ConcurrentHashMap` |
| e08a833f | IGStatus stutter + perf | adapted: cache foto `IGStatusAdapter.java`, antrean view `HorizontalListView.java`, guard layout `FloatingBottomBar`, reuse adapter + restore page limit `IGStatus` |
| d2c71a5d | SeenTick batch update | adapted: batch DAO + `updateViewedMessagesBatch`, tanpa hop executor karena fork `allowMainThreadQueries()` |
| 1a06ff0d | LockedChatsEnhancer | ported: satu set locked (phone/user/number), field HashSet kedua opsional, guard crash |
| 36c22aed | Changelog upstream | adapted: changelog fork berbahasa Indonesia |

---

## Port range 36c22aed..ceca486e (WaEnhancer 1.6.3)

**Tanggal:** 2026-11-07
**Upstream:** https://github.com/Dev4Mod/WaEnhancer.git (ceca486e)
**Range:** 8 commit, port ke API legacy `de.robv.android.xposed`

| Upstream commit | Topik | Status di fork |
|-----------------|-------|----------------|
| bbad12f9 | Support WhatsApp 2.26.43.xx | adapted: anchor DexKit baru (`loadStatusPlaybackReplyContainerViewMethod`, `loadStanzaWriterMethod`, `loadBottomNavigationViewClass`, `homeActivityClassDataChain`) plus fallback chain di `loadAddOptionSearchBarMethod`; migrasi YukiHookAPI di IGStatus/Others/Channels N/A karena fork sudah pakai `findHomeMenuMethod`/`hookAllMethods` |
| de0d20ad | arrays.xml 2.26.43.xx | N/A: urusan pipeline `new`, bukan port |
| 5dc59522 | Persist status blue tick | ported: `sendBlueTickStatus` pakai `insertHideSeenMessage`, bukan `updateViewedMessage` |
| c694caf4 | HideSeen LID + delivery receipt leak | ported: kolom `userjid` + 2 index, query `IN (:jids)`, `MIGRATION_11_12`, `hookStanzaWriter`/`revealsDelivery`, LID param di semua call site |
| 374d61dd | CallPrivacy notification + Custom Privacy priority | ported: `notifyCallBlocked`, `RejectMethod`, `PrivacyType.labelRes`, `Utils.showNotification(title, content, iconRes)`, switch di `fragment_privacy.xml`, entri `FeatureCatalog` |
| 4ae79dba | HookBL: buang spoofer bawaan | ported: key material bawaan dihapus, keypair nullable, hanya keybox.xml kustom, gate `bootloader_spoofer_custom` di `FeatureLoader`/`AntiWa`, pref UI + strings |
| 598d5ed7 | Version bump 1.6.3 | N/A: versi fork dikelola sendiri |
| ceca486e | Changelog upstream | adapted: changelog fork berbahasa Indonesia |

**Catatan:** semua hook di range ini belum diuji runtime (tidak ada perangkat saat build).

---

## Port range ceca486e..44050758 (WaEnhancer 1.6.4)

**Tanggal:** 2026-11-08
**Upstream:** https://github.com/Dev4Mod/WaEnhancer.git (44050758)
**Range:** 8 commit, 6 di antaranya portabel
**Build:** `:app:assembleWhatsappDebug --offline` sukses, R8 minify jalan
**Runtime:** diuji di `2.26.45.3` (WhatsApp, device `ZP2222SQLL`): modul ter-inject, `Loaded Hooks in 8208ms`, `AntiRevoke` 2906ms, `HideSeen` 737ms, `IGStatus` 1ms. Fallback WP3 (`loadAntiRevokeFStatusMethod`, `loadFStatusToFMessage`) resolve tanpa error. IG Status, Anti Revoke, dan Hide Seen aman. `HideSeen enforceHiding` masih gagal memuat `com.whatsapp.protocol.Message` (path `hookEnforceHiding` hardcode kelas, sudah ada sebelum port ini)

| Upstream commit | Topik | Status di fork |
|-----------------|-------|----------------|
| 29e51237 | perf(AntiRevoke): cache hasil lookup original key di DB | N/A: fork sudah punya `timestampCache` + `negativeCache` berukuran tetap, key `jid\0msgid`, dan `persistRevokedMessage` yang membuang entri negatif. Struktur upstream (`originalMessageKeyCache`, `loadedRevokedJids`) tidak ada di fork, jadi menambahkannya berarti cache kedua yang paralel, bukan perbaikan |
| 8e2b5e44 | fix(AntiRevoke): teks tanggal basi saat view dipakai ulang | ported: `FIELD_ORIGINAL_DATE_TEXT`/`FIELD_DECORATED_DATE_TEXT`, `restoreDateText`, `decorateDateText`; parameter `originalMessage` dihapus dari jalur `applyRevokedUI`. Fork memakai `XposedHelpers.get/set/removeAdditionalInstanceField`, bukan `ReflectionUtils` |
| aecc2265 | fix(AntiRevoke): revoke status di 2.26.43 | ported sebagai fallback: `loadAntiRevokeFStatusMethod` memakai `findMethodUsingFilter` dulu, lalu fallback `declaredMethods` yang return enum dengan minimal 2 parameter `FStatusKey`; `loadFStatusToFMessage` fallback ke `bridge.findMethod` + filter `!isSynthetic`; hook membaca `args.lastOrNull { FStatusKey.TYPE.isInstance(it) }` |
| 1a82e672 | refactor(IGStatus): RecyclerView + DiffUtil | ported: `IGStatusAdapter.java` jadi `RecyclerView.Adapter` dengan `submitList` + `StatusDiffCallback`, `IGStatusView.java` memakai `RecyclerView` horizontal, `HorizontalListView.java` dihapus, `updateList(itens)` di 5 call site, `adapter = null` saat container didaur ulang. Helper fork (`resolveStatusInfo`, `resolveJid`, `readStatusCounts`, cache foto) tetap utuh |
| c539ce20 | fix(HideSeen): media dikirim ulang tidak terlihat | ported: hook tunggal ke `loadSenderPlayedBusiness`, `processSenderPlayed` loop semua pesan dalam Set, `hookSenderPlayed` lama dihapus dari `doHook`. Anchor `loadSenderPlayedMethod` tetap ada di `Unobfuscator` sebagai fallback (aturan keep-anchor) |
| 648e439a | feat(IGStatus): pakai bar status native WhatsApp | ported: pref `native_igstatus`, `propsBoolean[25327] = igstatus && nativeIgStatus` di `Others.kt`, early return di `IGStatus.doHook()`, entri `FeatureCatalog.java`, switch di `fragment_customization.xml` (`app:dependency="igstatus"`), strings `values` + `values-pt` |
| 7b32d787 | Changelog upstream | N/A: changelog fork ditulis sendiri dalam bahasa Indonesia |
| 44050758 | Version bump 1.6.4 | N/A: versi fork dikelola sendiri |

**Catatan build:** dependensi `androidx.recyclerview:recyclerview:1.3.2` ditambahkan eksplisit ke `gradle/libs.versions.toml` + `app/build.gradle.kts`. Sebelumnya kelas RecyclerView hanya ikut secara transitif.

---

## Trim dukungan versi ke 2.26.43.xx - 2.26.45.xx

**Tanggal:** 2026-11-07
**Keputusan:** hanya 3 versi didukung. `2.26.43.xx` dan `2.26.44.xx` (lama), `2.26.45.xx` (beta).
**Dihapus dari dukungan:** `2.26.35.xx` sampai `2.26.41.xx`.

### Yang berubah di kode

| Perubahan | File | Bukti |
|-----------|------|-------|
| `supported_versions_wpp` dan `supported_versions_business` tinggal 3 item | `app/src/main/res/values/arrays.xml` | `grep -c "<item>2.26"` = 6 |
| `generate_build_notes.sh` membaca seluruh array, bukan `grep -A2` dengan `- 2.26.30.xx` hardcoded | `generate_build_notes.sh` | output `2.26.43.xx - 2.26.44.xx - 2.26.45.xx` |
| Anchor `VideoTranscoder/transcodeVideoNew/` dihapus dari `loadVideoTranscoderStartMethod`, fallback videolite jadi satu-satunya jalur | `core/devkit/Unobfuscator.kt` | absen di dex 2.26.43.76 dan 2.26.45.3 |

Tidak ada hook fitur yang dihapus. Audit 181 anchor DexKit dan seluruh literal nama metode di `features/` tidak menemukan anchor lain yang terbukti mati sebelum `2.26.43` (lihat `.work/AUDIT-wa-version-support-trim.md`).

### Hook redundan yang gagal (fitur tetap bekerja)

Dua hook gagal di `2.26.43.11` (W4B) dan `2.26.45.3`, keduanya ditambahkan commit `09f533ec` sebagai jalur tambahan, bukan jalur utama. Nama kelas WhatsApp di-hardcode. HideSeen dan CallRecording tetap berfungsi lewat jalur yang sudah ada sebelum commit itu.

| Hook | Error di log | Peran | Jalur yang dipakai fitur |
|------|--------------|-------|--------------------------|
| `HideSeen.hookEnforceHiding` | `Didn't find class "com.whatsapp.protocol.Message"` | Tambahan: blokir `isRead` di level model pesan | `hookSendReadReceiptJob`, `hookReceiptMethod`, `hookStanzaWriter`, `hookSenderPlayed` |
| `CallRecording.hookPeerJidMethod` | `Didn't find class "com.whatsapp.voipcalling.VoipCall"` | Tambahan: hook `getPeerJid` di kelas VoIP | `extractUserJid` dari `soundPortCreated`: `getCallInfo()` lalu `getPeerJid()` lalu field `peerJid` lalu map `participants` |

Bukti jalur utama ada di 2.26.43.76 dan 2.26.45.3: `SendReadReceiptJob` (4 dan 6), `getCallInfo` (15 dan 15), `getPeerJid` (8 dan 8), `soundPortCreated` (2 dan 2), `fieldstatsReady` (7 dan 7), `VoiceServiceEventCallback` (28 dan 29). `CallPrivacy` memakai `getPeerJid` yang sama dan tidak error di log.

Tidak dihapus dan tidak diperbaiki dalam scope trim. Kalau nanti dibersihkan, ganti nama kelas hardcoded dengan anchor DexKit.

### Status smoke test

| Versi | Perangkat | Hasil |
|-------|-----------|-------|
| `2.26.45.3` (WA) | `ZP2222SQLL` | 76 plugin, `Loaded Hooks in 12446ms`, tidak ada `Unsupported version` |
| `2.26.43.11` (W4B) | `ZP2222SQLL` | 76 plugin, `Loaded Hooks in 10702ms`, tidak ada `Unsupported version` |
| `2.26.44.xx` | tidak ada | Belum diuji, tidak ada APK. Anchor default KEEP |

### Catatan rilis

HideSeen dan CallRecording tetap bekerja di 2.26.43/44/45. Yang perlu disebut di changelog hanya sebagai catatan teknis: hook tambahan `enforceHiding` (HideSeen) dan hook `VoipCall.getPeerJid` (Call Recording) tidak terpasang karena nama kelas WhatsApp berubah, tanpa memengaruhi fitur. 2.26.44.xx belum diuji runtime.
