# FeaturesGap.md — WhatsVault (fork) vs OriginalWaEnhancer (upstream Dev4Mod/WaEnhancer)

**Tanggal:** 2026-09-06  
**Fork:** git@github.com:mrksvt/WhatsVaulth.git (HEAD db1342de)  
**Upstream:** https://github.com/Dev4Mod/WaEnhancer.git (HEAD 883041c6)  
**Version:** 1.5.5 (both)  
**Fork package:** `com.mrksvt.waen` | **Upstream package:** `com.wmods.wppenhacer`  
**Git histories:** unrelated (no merge-base) — sync manual, bukan git merge

---

## Ringkasan

| Metric | Fork (WhatsVault) | Upstream |
|--------|-------------------|----------|
| Total .kt/.java source | 201 | 165 |
| Registered features | 71 | 51 |
| WA versions supported | 2.26.29–2.26.35 | 2.26.25–2.26.33 |
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

### LISTENERS (Fork: 3 | Orig: 3) — identical

| Fitur | Fork | Orig | Status |
|-------|------|------|--------|
| ContactItemListener | Y | Y | shared |
| ConversationItemListener | Y | Y | shared |
| MenuStatusListener | Y | Y | shared |

---

### MEDIA (Fork: 6 | Orig: 6) — identical

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

### PRIVACY (Fork: 11 | Orig: 11) — identical

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

1. **ToastViewer.kt** — port full upstream refactor:
   - `HashMap` → `ConcurrentHashMap` (thread safety)
   - `CompletableFuture.runAsync` → `Utils.databaseExecutor.execute` (dedicated executor)
   - Tambah `AtomicBoolean cleanupStarted` guard (prevent double cleanup)
   - Cleanup interval: 1s → 30s (fix excessive cleanup)
   - `ScheduledThreadPool(1)` → single daemon thread executor
   - Hapus `@Synchronized` yang redundant
   - `continue` bukan `return` dalam loop (fix early exit bug)
   - Safe null handling untuk `getWaContactFromJid` (nullable fallback)
   - Select specific columns di query (performance)

2. **MediaQuality.kt** — tambah CBR support:
   - Import `android.media.MediaCodecInfo`
   - Set `videoBitrateMode = BITRATE_MODE_CBR` (constant bitrate = kualitas konsisten)

3. **build.gradle.kts** — restore business flavor:
   - Tambah `create("business")` block (applicationIdSuffix `.w4b`)
   - `applicationVariants.all`: dynamic APK name + app_name per flavor
   - Business APK: `WhatsVault-Business-<version>.apk` + `application-label: WhatsVault Business`

4. **.github/workflows/build-business.yml** — CI workflow business:
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
