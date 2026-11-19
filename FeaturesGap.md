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

---

## Dukungan 2.26.46.xx

**Tanggal:** 2026-11-15
**Keputusan:** `supported_versions_wpp` = `2.26.44.xx`, `2.26.45.xx`, `2.26.46.xx` (3 entri, kebijakan 3 versi). `supported_versions_business` juga `2.26.44.xx` - `2.26.46.xx` setelah APK W4B `2.26.46.2` diverifikasi (sebelumnya tertahan di `2.26.43.xx` karena belum ada APK business).

### Anchor yang berubah

Diff 458 literal `Unobfuscator.kt` vs pool `2.26.45.3`: 117 OK, 103 OK-CONTAINS, 228 DEAD (stale pre-existing), 2 REGRESSION.

| Literal | Lokasi | Status di 2.26.46.3 | Aksi |
|---------|--------|---------------------|------|
| `com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment` | `loadUnknownStatusPlaybackMethod` | Kelas masih ada (`classes7.dex`, `Lcom/whatsapp/status/playback/fragment/StatusPlaybackContactFragment;`); literal dotted hilang dari pool karena inner class `$maybeSetupHeaderCTA$1` dihapus. `XposedHelpers.findClass` dengan nama dotted tetap resolve. | Diganti `findFirstClassUsingName(loader, EndsWith, "StatusPlaybackContactFragment")` - pola yang sudah dipakai `Others.autoNextStatus()` dan `MenuStatusListener`, sekaligus menghapus hardcoded class name |
| `frame_visibility_serial_worker` | `loadVideoViewContainerClass` | Hilang dari dex 2.26.46.3. Kelas pemilik pindah obfuscation `X/Im5` (2.26.45) ke `X/L95` (`classes11.dex`), literal `thumb view type is not correct` masih ada dan unik ke kelas itu (1 kemunculan). | Fallback chain: anchor lama dipertahankan, ditambah `thumb view type is not correct` |

Tidak ada anchor lama yang dihapus. `check_anchors.py` tetap melaporkan `frame_visibility_serial_worker` sebagai REGRESSION karena literal lama sengaja dipertahankan sebagai jalur pertama.

### Verifikasi pool W4B 2.26.46.2

Diff 458 literal `Unobfuscator.kt` vs pool W4B `2.26.46.2`: 119 OK, 103 OK-CONTAINS, 226 DEAD, 1 REGRESSION.

| Literal | Status di W4B 2.26.46.2 | Aksi |
|---------|-------------------------|------|
| `contact.ui.picker.ContactPickerFragment` | False positive. Literal dotted tidak ada di pool, tetapi kelasnya ada (`classes4.dex`, `Lcom/whatsapp/contact/ui/picker/ContactPickerFragment;`). Jalur utama `ContactPickerFragment/onContactsLoaded/loaded all:` masih ada, jadi `loadLoadedContactsMethod` resolve di path kedua | Tidak diubah |
| `frame_visibility_serial_worker` | Absen juga di W4B | Fallback `thumb view type is not correct` ada (1 kemunculan) |
| `StatusPlaybackContactFragment` | Kelas ada (`classes11.dex`) | Anchor `findFirstClassUsingName` berlaku untuk WPP dan W4B |
| `playbackFragment/refreshCurrentPageSubTitle message is empty` | Ada | - |

Tidak ada perubahan kode untuk W4B. Cukup entri array.

### Status smoke test

| Versi | Perangkat | Hasil |
|-------|-----------|-------|
| `2.26.46.3` (WA) | `ZP2222SQLL` | 76 plugin, `Loaded Hooks in 17073ms`, tidak ada `Unsupported version` |
| `2.26.46.2` (W4B) | `ZP2222SQLL` | 48 plugin tercatat, `Loaded Hooks in 24075ms`, tidak ada `Unsupported version`, tidak ada `MainFeatures[Critical]` |

Jumlah baris `Loaded Plugin` pada W4B lebih sedikit karena `plugins()` menunggu maksimum 15 detik (`executorService.awaitTermination(15, TimeUnit.SECONDS)`) sebelum mencetak daftar durasi, sementara total load W4B 24075ms. Tidak ada `ErrorItem` yang tercatat, jadi bukan plugin yang gagal.

### Catatan rilis

HideSeen dan CallRecording tetap bekerja di 2.26.43/44/45. Yang perlu disebut di changelog hanya sebagai catatan teknis: hook tambahan `enforceHiding` (HideSeen) dan hook `VoipCall.getPeerJid` (Call Recording) tidak terpasang karena nama kelas WhatsApp berubah, tanpa memengaruhi fitur. 2.26.44.xx belum diuji runtime.

---

## Fix crash HomeActivity 2.26.46 (`The item position should be less or equal to`)

**Tanggal:** 2026-11-19
**Laporan:** WA 2.26.46.11, WAE 1.6.4 (6B18B423), Android 16 (API 36), motorola moto g57 power
**Gejala:** HomeActivity gagal dibuat setiap kali WhatsApp dibuka.

```
java.lang.IllegalArgumentException: The item position should be less or equal to:5
  at X.17w.A0N(:101)
  at X.0xO.A08(:46)
  at X.0sW.A5n(:274)
  at com.whatsapp.home.ui.HomeActivity.onCreate(:1236)
```

### Akar masalah (dibuktikan dari dex 2.26.46.70)

| Temuan | Bukti |
|--------|-------|
| Method daftar chat pindah dari `ConversationsFragment` ke `ConversationsFragmentKt` | `com.whatsapp.conversationslist.ConversationsFragment` di 2.26.46 hanya punya `<init>()`. Tidak ada lagi method tanpa argumen yang mengembalikan `List`, jadi `Unobfuscator.loadTabFragmentMethod()` melempar `TabFragment method not found` |
| `SeparateGroup.doHook()` dulu menjalankan `hookTabList()` (menyuntikkan id tab 500) sebelum `hookTabInstance()` dan `hookTabName()` | Urutan lama: `hookTabList` -> `hookTabIcon` -> `hookTabInstance` -> `hookTabName` -> `hookTabCount` |
| Kegagalan di `hookTabInstance()` membatalkan sisa `doHook()` | Exception keluar dari `doHook()`, ditangkap `FeatureLoader.plugins()`, sehingga `hookTabName()` tidak pernah terpasang |
| Validator tab WhatsApp tidak mengenal id 500 | `LX/17f;->A0K(I)` dan `A0M(I)` hanya punya cabang untuk 200, 300, 400, 600, 700, 800, 900, 1300, 1400. Cabang default melempar `IllegalArgumentException("The item position should be less or equal to: " + A05)`, dengan `A05` = jumlah tab bawaan (5) |
| `A5u(posisi)` bukan penyebab | `HomeActivityKt.A5u(I)` memanggil `LX/0vs;->A07(I)`: posisi di luar rentang di-clamp ke id 200, tidak melempar |

Jadi urutannya: tab 500 sudah masuk daftar tab, tetapi hook judul/fragment untuk 500 belum terpasang, lalu validator tab WhatsApp melempar exception saat `HomeActivity.onCreate`.

### Perbaikan

| File | Perubahan |
|------|-----------|
| `SeparateGroup.kt` | Anchor wajib (judul tab, get tab, kelas ConversationsFragment) di-resolve lebih dulu. Kalau salah satu hilang, tab GROUPS tidak disuntikkan. `hookTabName()` dan `hookTabInstance()` dipasang sebelum `hookTabList()`. Hook opsional (ikon, badge, daftar chat, fab, publishResults) dibungkus `runCatching` supaya satu anchor hilang tidak membatalkan seluruh fitur |
| `SeparateGroup.kt` | `guardTabValidators()`: hook pada kelas validator tab (anchor `The item position should be less or equal to:`). Guard hanya aktif untuk id tab GROUPS (500). Kalau id itu ditolak WhatsApp, judul diisi string kosong dan fragment memakai instance `ConversationsFragment`, lalu kejadiannya dicatat ke log. Id tab bawaan WhatsApp tidak disentuh supaya tidak ada instance fragment ganda |
| `SeparateGroup.kt` | `resolveTabId()`: accessor tab tidak lagi diindeks dengan id tab (`tabs[id]` bisa IndexOutOfBounds). Id dipakai apa adanya kalau ada di daftar, kalau tidak diperlakukan sebagai posisi |
| `Unobfuscator.kt` | `loadTabNameMethod`: anchor teks `No tab title override for tab id: ` + `usingNumbers(200, 300, 400)` dicoba lebih dulu, jalur lama berbasis nama resource `updates` dipertahankan sebagai fallback |
| `Unobfuscator.kt` | `loadGetTabMethod`: tambah fallback teks `No HomeFragment mapping for tab id: ` |
| `Unobfuscator.kt` | Loader baru `loadConversationsFragmentClass()` (utamakan `.ConversationsFragment`, cadangan `.ConversationsFragmentKt`) dan `loadTabValidatorClass()` |

Tidak ada anchor lama yang dihapus.

### Batas verifikasi

- Analisis memakai dex 2.26.46.70 (APK resmi WhatsApp). Dex 2.26.46.11 tidak tersedia di mesin ini, jadi nama kelas obfuscated di log laporan (`X.17w`, `X.0xO`, `X.0sW`) tidak dicocokkan satu per satu.
- Belum diuji runtime. Yang bisa dipastikan dari kode: tab 500 tidak lagi disuntikkan tanpa hook judul/fragment, dan validator tab punya jaring pengaman.
- Filter daftar chat untuk tab grup masih bergantung pada `loadTabFragmentMethod()`, yang di 2.26.46 gagal karena method daftar chat pindah ke `ConversationsFragmentKt`. Di versi itu tab grup tetap tampil dan tidak crash, tetapi isinya belum tentu terpisah dari tab chat. Ini batas yang disengaja: menebak method pengganti tanpa uji runtime lebih berisiko daripada membiarkan filter nonaktif.

---

## Fix IG Status 2.26.46 (`GetViewConversation method not found`)

**Tanggal:** 2026-11-19
**Laporan:** WA 2.26.46.8 dan 2.26.46.11, WAE 1.6.4 (EF32DBBF)
**Pesan:** `Error getting method loadGetViewConversationMethod: GetViewConversation method not found` dari `IGStatus.doHook` baris 75.

### Akar masalah

`Unobfuscator.loadGetViewConversationMethod()` mencari method dengan 3 parameter yang mengembalikan `View` dan menerima `LayoutInflater` di dalam `declaredMethods` kelas `com.whatsapp.conversationslist.ConversationsFragment`.

Di 2.26.46 kelas itu hanya menyisakan konstruktor. Implementasinya pindah ke base class `ConversationsFragmentKt`:

```
ConversationsFragment      -> hanya <init>()
  extends ConversationsFragmentKt  -> A2P(Bundle, LayoutInflater, ViewGroup) : View
    extends WaFragment
      extends androidx.fragment.app.Fragment
```

Karena `declaredMethods` tidak mewarisi method, pencarian gagal walau method-nya tetap ada di kelas turunan saat runtime.

### Perbaikan

`Unobfuscator.loadGetViewConversationMethod()` sekarang menelusuri hierarki kelas mulai dari `ConversationsFragment` sampai `Any`. Urutan pencarian tetap: kelas asli dulu, baru superclass. Kalau method ditemukan di superclass, nama kelasnya dicatat ke log Xposed dengan tag `[Unobfuscator] GetViewConversation ditemukan di superclass`.

Tidak ada anchor lama yang dihapus.

### Verifikasi dari dex 2.26.46.70

Method yang cocok di hierarki `ConversationsFragment` hanya satu: `A2P(Bundle, LayoutInflater, ViewGroup) -> View` di `ConversationsFragmentKt`. Kelas lain yang punya signature sama (`ConversationFragment`, `UpdatesFragment`) tidak berada di hierarki ini, jadi tidak ikut terpilih.

### Catatan

- Belum diuji runtime. Yang bisa dipastikan: pencarian tidak lagi bergantung pada `declaredMethods` kelas turunan.
- Kalau setelah perbaikan ini masih ada error IG Status lain, penyebabnya anchor berikutnya di `IGStatus.doHook` (`loadStatusInfoClass`, `loadOnUpdateStatusChanged`, `loadStatusListUpdatesClass`), bukan method ini lagi.

---

## Fix IG Status 2.26.46 (baris status tidak muncul)

**Tanggal:** 2026-11-19
**Gejala:** hook tidak lagi error setelah perbaikan `loadGetViewConversationMethod`, tetapi baris IG Status tidak muncul di tab chat.

### Akar masalah

`ConversationsFragmentKt.A2P` (onCreateView) di 2.26.46 menginflate layout lalu mengambil `android.R.id.list` dan memberi assertion:

```
instance-of v8, v5, Landroidx/recyclerview/widget/RecyclerView;
const-string v6, "Only RecyclerView is supported"
```

Jadi daftar chat sekarang `RecyclerView`, bukan `ListView` lagi.

Kode IG Status punya dua jalur:

| Kondisi | Perilaku lama |
|---------|---------------|
| `list is ListView` | `addHeaderView()` - benar |
| lainnya | `parent.addView(container, 0)` - container jadi child index 0 |

Pada parent bertipe `FrameLayout`, child index 0 digambar paling bawah. RecyclerView (MATCH_PARENT) menutupinya, sehingga baris status ada di hierarki view tetapi tidak terlihat. Tidak ada exception, jadi tidak ada error di log. Itu sebabnya fitur tampak mati tanpa pesan.

### Perbaikan

| Perubahan | Keterangan |
|-----------|------------|
| `attachStatusRow()` | Logika pemasangan dipindah ke method sendiri dan dibungkus `runCatching` di hook, supaya kegagalan layout tercatat di log |
| Parent `LinearLayout` vertikal | Container disisipkan tepat sebelum daftar, tanpa padding |
| Parent lain (FrameLayout) | Padding atas daftar ditambah setinggi baris status, `clipToPadding` diaktifkan, container ditambahkan sebagai child terakhir (digambar di atas) dan digeser lewat margin atas layout params sebesar padding asli. `translationY` tidak dipakai karena override di `IGStatusView` mengabaikan nilai selama tinggi view masih 0 |
| Dedupe | Kalau parent sudah punya child `IGStatusView`, hook tidak menambah container baru (view recreate) |

Tidak ada anchor lama yang dihapus.

### Batas verifikasi

- Belum diuji runtime. Yang bisa dipastikan dari kode: container tidak lagi ditempatkan di belakang RecyclerView, dan kegagalan layout sekarang muncul di log.
- Kalau baris masih tidak muncul, log `IGStatus: ...` akan menunjukkan apakah hook jalan, daftar chat ditemukan, dan parent-nya bertipe apa.

---

## Log runtime di perangkat (tanpa adb)

**Tanggal:** 2026-11-19
**Gejala:** masalah IG Status tidak bisa didiagnosis karena perangkat hanya HP tanpa adb, dan `XposedBridge.log` tidak terbaca tanpa membuka log LSPosed di perangkat.

### Perancangan

Hook berjalan di proses WhatsApp, dan proses itu tidak punya izin menulis ke `/storage/emulated/0` secara umum. `RuntimeLog` (`xposed/core/RuntimeLog.kt`) karena itu memilih tujuan secara berurutan:

| Urutan | Tujuan | Syarat |
|--------|--------|--------|
| 1 | `/storage/emulated/0/WhatsVault/logs` langsung dari proses WhatsApp | WhatsApp punya akses penyimpanan luas |
| 2 | `/storage/emulated/0/WhatsVault/logs` lewat `HookProvider` | Aplikasi modul sudah diberi MANAGE_EXTERNAL_STORAGE |
| 3 | `Android/media/com.whatsapp/WhatsVault/logs` | Selalu bisa, tanpa izin |
| 4 | `filesDir/logs` | Cadangan terakhir, hanya terbaca lewat root |

Path yang benar-benar dipakai ditulis di baris pertama file log (`sink=`), jadi tidak perlu menebak.

### Detail teknis

| Hal | Keterangan |
|-----|------------|
| Aktif hanya di debug | Semua entry point diawali `if (!BuildConfig.DEBUG) return` |
| Titik start | `FeatureLoader` memanggil `RuntimeLog.start(application)` di `callApplicationOnCreate` sebelum plugin dimuat |
| Sumber log | `Feature.log()` (semua plugin), error plugin di `FeatureLoader` beserta stack trace, dan log khusus IG Status |
| Thread | Penulisan di thread daemon terpisah, buffer 5000 baris, flush tiap 1.5 detik, supaya hook di main thread tidak menunggu I/O |
| Rotasi | File dihapus kalau melewati 8 MB, lalu mulai lagi |
| Kegagalan tulis | Tidak pernah dilempar ke WhatsApp. Kalau jalur provider gagal 3 kali berturut-turut, log pindah ke folder media |
| Keamanan provider | `HookProvider` exported, jadi nama file dari bundle disanitasi dan hanya boleh berisi huruf, angka, titik, garis bawah, dan strip |

### Batas verifikasi

- Belum diuji runtime. Yang bisa dipastikan dari kode: tidak ada jalur yang melempar exception ke hook, dan setiap kegagalan tulis hanya menurunkan tujuan log.

### Hasil build

| Item | Nilai |
|------|-------|
| Run | `44564305523` (sukses) |
| Commit | `56099e4` |
| versionCode | 9 |
| versionName | `1.6.4 (56099E4E)_mrksvt` |
| Penanda tangan | SHA-256 `3347ed18ee7b28350e21c4cdc7f07077a65f076dcbb55e5a6625cb9be798d99f` (sama dengan build 8, jadi bisa dipasang sebagai pembaruan) |
| Isi dex | `RuntimeLog`, `HookProvider`, `IGStatus` ada, dan `Feature.log` serta `FeatureLoader` memanggil `RuntimeLog` |

File APK ada di `/storage/emulated/0/Download/WhatsVault-v9-56099E4E.apk`.

---

## Signature APK tidak stabil

**Tanggal:** 2026-11-19
**Gejala:** setiap APK hasil build harus di-uninstall dulu sebelum bisa dipasang, dan data aplikasi hilang.

### Akar masalah

| Temuan | Bukti |
|--------|-------|
| Build debug selalu memakai `signingConfigs["debug"]` | `app/build.gradle.kts` buildType `debug` sebelumnya hardcoded `signingConfig = signingConfigs["debug"]` |
| Di GitHub Actions, keystore debug dibuat baru tiap run | Runner adalah mesin bersih, `~/.android/debug.keystore` dibuat AGP dengan kunci acak setiap build |
| Secret signing kosong | Log run `44548337952`, step `Write key` mengekspansi `androidStorePassword=''`, `androidKeyAlias=''`, `androidKeyPassword=''`. Blok `if [ ! -z "$KEY_STORE" ]` tidak pernah jalan |
| Workflow menulis ke file yang salah | Step menulis `gradle.properties`, sedangkan `app/build.gradle.kts` membaca `signing.properties` |

Kesimpulan: setiap build punya sertifikat berbeda, dan tidak ada kunci yang disimpan, sehingga signature build lama tidak bisa dipakai ulang.

### Perbaikan

| Perubahan | Keterangan |
|-----------|------------|
| Keystore tetap | `key.jks` (PKCS12, RSA 2048, 10950 hari, alias `whatsvaulth`) dibuat dan disimpan sebagai secret repo: `KEY_STORE`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD` |
| Workflow | Menulis `signing.properties` (file yang benar-benar dibaca Gradle) dan berhenti dengan error kalau `KEY_STORE` kosong, supaya tidak diam-diam menghasilkan APK dengan signature acak |
| `app/build.gradle.kts` | BuildType `debug` memakai `signingConfigs["config"]` kalau keystore tersedia, fallback ke debug keystore hanya untuk build lokal tanpa `signing.properties` |
| `versionCode` | 8 |

### Catatan

Satu kali install ulang masih diperlukan untuk build ini. APK yang terpasang sekarang ditandatangani debug keystore acak milik runner yang sudah dihapus, dan kunci privatnya tidak pernah disimpan, jadi secara kriptografis tidak mungkin dipakai lagi. Setelah build dengan keystore tetap ini terpasang, build berikutnya bisa dipasang sebagai pembaruan biasa.
