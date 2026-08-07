# PLAN & TODO — Groq/Google Translator Patch

**Tanggal:** 07 Agustus 2026
**Status:** Planning
**File utama:**
- `GoogleTranslate.kt` — logika translate, popup, error handling
- `TranslatorWrapperAdapter.kt` — display bubble, in-memory cache
- `GroqTranslator.kt` — adapter wrapper hook
- `preference_general_translator.xml` — settings UI
- `res/values/strings.xml` — string resources

---

## Ringkasan Pekerjaan

| # | Issue | Prioritas | Effort |
|---|-------|-----------|--------|
| 1 | Fallback ke Google jika Groq key kosong | Tinggi | Kecil |
| 2 | Bahasa target bisa dipilih manual | Sedang | Sedang |
| 3 | Terjemahan persisten (survive close/restart) | Tinggi | Besar |
| 4 | Singleton issue — multi-conversation support | Tinggi | Sedang |
| 5 | Label popup localization | Rendah | Kecil |
| 6 | Google Translate endpoint bisa diatur | Sedang | Kecil |
| 7 | Loading indicator | Sedang | Kecil |
| 8 | Error dengan opsi retry | Sedang | Kecil |
| 9 | System prompt lebih kuat untuk slang/bahasa daerah | Tinggi | Kecil |

---

## Detail Per Issue

---

### Issue 1 — Fallback ke Google jika Groq key kosong

**Problem:**
`translateGroq()` dipanggil tanpa cek apakah `groq_translator_api_key` kosong. Kalau kosong → request gagal → Toast error, tanpa fallback.

**Lokasi:**
- `GoogleTranslate.kt` baris ~70 dan ~84 — tempat `translateGroq()` dipanggil

**Fix:**
```kotlin
// Sebelum panggil translateGroq(), cek dulu key
val groqKey = prefs.getString("groq_translator_api_key", "") ?: ""
val future = if (provider == "groq" && groqKey.isNotBlank()) {
    translateGroq(text, lang)
} else {
    // fallback ke Google
    translateGoogle(text, lang)
}
```

**Pref keys:** `translator_provider`, `groq_translator_api_key`

**TODO:**
- [ ] Tambah cek key sebelum dispatch ke Groq
- [ ] Log warning kalau fallback terjadi

---

### Issue 2 — Bahasa target bisa dipilih manual

**Problem:**
Bahasa target hardcoded dari `Locale.getDefault().language` — tidak bisa diatur user.

**Lokasi:**
- `GoogleTranslate.kt` baris ~159 (`val lang = Locale.getDefault().language`)
- `preference_general_translator.xml` — perlu tambah preference baru

**Fix:**

1. Tambah pref baru di `preference_general_translator.xml`:
```xml
<ListPreference
    android:key="translator_target_lang"
    android:title="Bahasa Tujuan"
    android:summary="Pilih bahasa hasil terjemahan"
    android:entries="@array/translator_lang_names"
    android:entryValues="@array/translator_lang_codes"
    android:defaultValue="auto" />
```

2. Tambah arrays di `res/values/arrays.xml`:
```xml
<string-array name="translator_lang_names">
    <item>Otomatis (Locale sistem)</item>
    <item>Indonesia</item>
    <item>English</item>
    <item>Jawa</item>
    <!-- ... tambah sesuai kebutuhan -->
</string-array>
<string-array name="translator_lang_codes">
    <item>auto</item>
    <item>id</item>
    <item>en</item>
    <item>jv</item>
</string-array>
```

3. Di `GoogleTranslate.kt`, ganti:
```kotlin
// Sebelum
val lang = Locale.getDefault().language

// Sesudah
val prefLang = prefs.getString("translator_target_lang", "auto") ?: "auto"
val lang = if (prefLang == "auto") Locale.getDefault().language else prefLang
```

**Pref keys baru:** `translator_target_lang`

**TODO:**
- [ ] Tambah `ListPreference` di `preference_general_translator.xml`
- [ ] Tambah arrays bahasa di `res/values/arrays.xml`
- [ ] Update `GoogleTranslate.kt` baca pref, fallback ke locale kalau "auto"

---

### Issue 3 — Terjemahan persisten (survive close/restart)

**Problem:**
`translationMap` di `TranslatorWrapperAdapter` adalah `HashMap` in-memory — hilang saat conversation di-close atau WA restart.

**Opsi implementasi:**
1. **Room DB** (berat tapi proper) — butuh entity, DAO, migration
2. **SharedPreferences JSON** (ringan, cocok untuk data kecil) — serialize map ke JSON string

**Rekomendasi: SharedPreferences JSON** — sesuai pattern yang sudah ada di project, tidak perlu Room entity baru.

**Struktur data:**
```
pref key: "translator_cache"
pref value: JSON string
{
  "A5C4DEC545C9F049E79ED9944DBE5E6B": "Saya ingin makan, tetapi...",
  "AC61ED56A204E49FF33C53778A4A2560": "Pan makan apa?"
}
```

**Lokasi:**
- `TranslatorWrapperAdapter.kt` — `translationMap`, `showTranslation()`, `hideTranslation()`

**Fix:**
```kotlin
// Load cache saat init
init {
    instance = this
    loadCacheFromPrefs()
    try { realAdapter.registerDataSetObserver(realAdapterObserver) } catch (_: Exception) {}
}

private fun loadCacheFromPrefs() {
    val json = prefs.getString("translator_cache", null) ?: return
    try {
        val obj = org.json.JSONObject(json)
        obj.keys().forEach { key -> translationMap[key] = obj.getString(key) }
    } catch (_: Exception) {}
}

private fun saveCacheToPrefs() {
    val obj = org.json.JSONObject()
    translationMap.forEach { (k, v) -> obj.put(k, v) }
    prefs.edit().putString("translator_cache", obj.toString()).apply()
}
```

Panggil `saveCacheToPrefs()` di `showTranslation()` dan `hideTranslation()` setelah update map.

**TODO:**
- [ ] Tambah `prefs: SharedPreferences` ke constructor `TranslatorWrapperAdapter` (sudah ada)
- [ ] Tambah `loadCacheFromPrefs()` di `init`
- [ ] Tambah `saveCacheToPrefs()`, panggil setelah setiap update `translationMap`
- [ ] Clear cache ketika user force-close conversation (opsional — bisa skip untuk MVP)

---

### Issue 4 — Singleton issue: multi-conversation support

**Problem:**
```kotlin
companion object {
    private var instance: TranslatorWrapperAdapter? = null
```
Static singleton — buka conversation kedua → `instance` tertimpa → terjemahan conversation pertama hilang.

**Root cause:** Singleton di-set di `init` block, tidak ada lifecycle management.

**Fix:**
Ganti dari singleton statis menjadi map per `conversationId`:

```kotlin
companion object {
    private val instances = HashMap<String, WeakReference<TranslatorWrapperAdapter>>()

    fun getInstance(conversationId: String): TranslatorWrapperAdapter? =
        instances[conversationId]?.get()

    fun showTranslation(conversationId: String, messageId: String, text: String) {
        val adapter = instances[conversationId]?.get() ?: return
        // ... existing logic
    }
}
```

Di `init`:
```kotlin
init {
    instances[conversationId] = WeakReference(this)
    // ...
}
```

`conversationId` bisa diambil dari `WppCore.getCurrentConversationId()` atau dipass saat wrapping di `GroqTranslator`.

**Perlu investigasi:** Cara ambil conversation ID yang stable di WA (bisa JID atau internal ID).

**TODO:**
- [ ] Investigasi cara ambil `conversationId` yang reliable dari WppCore atau context
- [ ] Refactor `instance` → `instances: HashMap<String, WeakReference<>>`
- [ ] Update `showTranslation()`, `hideTranslation()`, `hasTranslation()` terima `conversationId`
- [ ] Update pemanggil di `GoogleTranslate.kt` pass conversation ID

---

### Issue 5 — Label popup localization

**Problem:**
```kotlin
popup.menu.add(0, 1, 0, "Terjemahkan")         // baris 136
popup.menu.add(0, 2, 1, "Sembunyikan terjemahan") // baris 139
```
Hardcoded Bahasa Indonesia.

**Fix:**
1. Tambah ke `res/values/strings.xml`:
```xml
<string name="translator_action_translate">Terjemahkan</string>
<string name="translator_action_hide">Sembunyikan terjemahan</string>
```

2. Tambah ke `res/values-en/strings.xml` (buat jika belum ada):
```xml
<string name="translator_action_translate">Translate</string>
<string name="translator_action_hide">Hide translation</string>
```

3. Di `GoogleTranslate.kt`, ganti hardcoded string:
```kotlin
val ctx = rootView.context
popup.menu.add(0, 1, 0, ctx.getString(R.string.translator_action_translate))
popup.menu.add(0, 2, 1, ctx.getString(R.string.translator_action_hide))
```

**TODO:**
- [ ] Tambah string resources ke `strings.xml`
- [ ] Buat `values-en/strings.xml` kalau belum ada
- [ ] Update `GoogleTranslate.kt` pakai `R.string.*`

---

### Issue 6 — Google Translate endpoint bisa diatur

**Problem:**
URL hardcoded:
```kotlin
"https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s"
```

**Fix:**
1. Tambah pref di `preference_general_translator.xml`:
```xml
<EditTextPreference
    android:key="google_translate_endpoint"
    android:title="Google Translate Endpoint"
    android:summary="URL endpoint (kosongkan untuk default)"
    android:defaultValue="" />
```

2. Di `translateGoogle()`:
```kotlin
val customEndpoint = prefs.getString("google_translate_endpoint", "") ?: ""
val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint
              else "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s"
val url = String.format(baseUrl, languageDest, URLEncoder.encode(text, "UTF-8"))
```

**Catatan:** Kalau endpoint custom, format URL mungkin beda — perlu validasi atau dokumentasi format yang diharapkan.

**Pref keys baru:** `google_translate_endpoint`

**TODO:**
- [ ] Tambah `EditTextPreference` di `preference_general_translator.xml`
- [ ] Update `translateGoogle()` baca endpoint dari pref

---

### Issue 7 — Loading indicator

**Problem:**
Setelah tap "Terjemahkan", tidak ada feedback — user tidak tahu sedang loading.

**Opsi:**
1. **Bubble placeholder** di `TranslatorWrapperAdapter.getView()` — tampilkan "Menerjemahkan..." saat result belum ada
2. **Toast "Sedang menerjemahkan..."** — simpel tapi mengganggu

**Rekomendasi: Bubble placeholder** — lebih elegan, konsisten dengan flow yang ada.

**Fix:**
Di `TranslatorWrapperAdapter`:
```kotlin
// Tambah map loading state
private val loadingSet = HashSet<String>() // messageId yang sedang loading

companion object {
    fun startLoading(messageId: String) {
        val adapter = instance ?: return
        Handler(Looper.getMainLooper()).post {
            adapter.loadingSet.add(messageId)
            adapter.rebuildIndex()
            adapter.notifyDataSetChanged()
        }
    }
}
```

Di `getView()` untuk translation slot:
```kotlin
val isLoading = realPosToMessageId[realPos]?.let { loadingSet.contains(it) } ?: false
tv.text = if (isLoading) "⏳ Menerjemahkan..." else translationMap[msgId] ?: ""
```

Di `GoogleTranslate.kt`, panggil `TranslatorWrapperAdapter.startLoading(messageId)` sebelum request, clear loading di `thenAccept` dan `exceptionally`.

**TODO:**
- [ ] Tambah `loadingSet: HashSet<String>` di `TranslatorWrapperAdapter`
- [ ] Tambah `startLoading()` di companion object
- [ ] Update `getView()` cek loading state
- [ ] Update `GoogleTranslate.kt` call `startLoading()` sebelum request, clear setelah selesai

---

### Issue 8 — Error dengan opsi retry

**Problem:**
```kotlin
Toast.makeText(rootView.context, "Gagal: ${err.message}", Toast.LENGTH_SHORT).show()
```
Tidak ada retry.

**Fix:**
Ganti Toast dengan Snackbar + action Retry:
```kotlin
.exceptionally { err ->
    Handler(Looper.getMainLooper()).post {
        val snackbar = Snackbar.make(
            rootView,
            "Terjemahan gagal: ${err.message}",
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction("Coba Lagi") {
            // Dispatch ulang terjemahan
            doTranslate(rootView, fMessage, provider, prefs)
        }
        snackbar.show()
        // Clear loading state
        TranslatorWrapperAdapter.clearLoading(messageId)
    }
    null
}
```

**Catatan:** Perlu ekstrak logic translate ke fungsi `doTranslate()` agar bisa dipanggil ulang untuk retry.

**TODO:**
- [ ] Ekstrak translate logic ke `doTranslate(rootView, fMessage, provider, prefs)`
- [ ] Ganti Toast error dengan Snackbar + action "Coba Lagi"
- [ ] Pastikan Snackbar dismiss setelah retry berhasil

---

### Issue 9 — System prompt lebih kuat untuk slang/bahasa daerah

**Problem:**
System prompt saat ini:
```
"You are a translator. Translate the user's text to $langName. Return ONLY the translated text, no explanation."
```
Terlalu lemah — Groq kadang balik bertanya alih-alih terjemahkan. Contoh: "wis maem yank?" → "apa yang kamu maksud dengan yank?" padahal harusnya "sudah makan sayang?"

**Fix:**
Perkuat system prompt dengan:
1. Instruksi eksplisit untuk terjemahkan apapun, termasuk slang
2. Contoh pasangan slang Indonesia/Jawa
3. Larangan eksplisit bertanya balik

```kotlin
val systemPrompt = """
You are a professional translator specializing in Indonesian language, Javanese, and Indonesian internet slang.

Rules:
1. ALWAYS translate the text. NEVER ask questions or request clarification.
2. If the text contains slang, abbreviations, or regional dialect (Javanese, Sundanese, etc.), infer the meaning from context and translate it.
3. Return ONLY the translated text. No explanations, no questions, no alternatives.
4. Target language: $langName

Common Indonesian slang reference:
- "yank/yang" = sayang (term of endearment, "dear/honey")
- "wis/udah" = sudah (already/done) [Javanese]
- "maem/makan" = eat [Javanese/slang]
- "gw/gue" = saya/aku (I/me) [Jakarta slang]
- "lu/lo" = kamu (you) [Jakarta slang]
- "dong/deh/sih/nih/lah" = filler particles, ignore or translate contextually
- "mantap/mantul" = great/awesome
- "gabut" = bored/nothing to do
- "baper" = emotionally affected
- "kepo" = nosy/curious
""".trimIndent()
```

**TODO:**
- [ ] Update `translateGroq()` di `GoogleTranslate.kt` dengan system prompt baru
- [ ] Tambah pref opsional `groq_custom_system_prompt` untuk override manual (advanced user)

---

## Urutan Implementasi (Rekomendasi)

**Fase 1 — Quick wins (effort kecil, dampak langsung):**
1. Issue 9 — System prompt fix (1 string change)
2. Issue 1 — Fallback Google jika Groq key kosong (5 baris)
3. Issue 7 — Loading indicator bubble placeholder
4. Issue 8 — Retry via Snackbar
5. Issue 5 — Label localization

**Fase 2 — Fitur settings:**
6. Issue 2 — Pilih bahasa target
7. Issue 6 — Google endpoint custom

**Fase 3 — Arsitektur (butuh investigasi lebih):**
8. Issue 3 — Cache persisten via SharedPreferences JSON
9. Issue 4 — Multi-conversation singleton fix

---

## File yang Akan Diubah

| File | Issue |
|------|-------|
| `GoogleTranslate.kt` | 1, 2, 5, 6, 7, 8, 9 |
| `TranslatorWrapperAdapter.kt` | 3, 4, 7 |
| `preference_general_translator.xml` | 2, 6 |
| `res/values/strings.xml` | 5 |
| `res/values/arrays.xml` | 2 |
| `res/values-en/strings.xml` | 5 |

---

## Checklist Final

- [ ] Issue 1 — Fallback ke Google jika Groq key kosong
- [ ] Issue 2 — Bahasa target bisa dipilih manual
- [ ] Issue 3 — Cache persisten via SharedPreferences JSON
- [ ] Issue 4 — Multi-conversation singleton fix
- [ ] Issue 5 — Label popup localization
- [ ] Issue 6 — Google endpoint bisa diatur
- [ ] Issue 7 — Loading indicator (bubble placeholder)
- [ ] Issue 8 — Error dengan Snackbar + retry
- [ ] Issue 9 — System prompt kuat untuk slang/bahasa daerah
- [ ] Build pass `./gradlew assembleWhatsappDebug`
- [ ] Flash + test semua skenario
- [ ] Update `COMING_SOON.md` mark issues resolved
