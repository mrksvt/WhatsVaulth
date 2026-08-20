# PLAN.md — Brainstorming Fitur Baru

> Dokumen ini berisi brainstorming teknis untuk 3 fitur baru:
> **TTS (Text-To-Speech)**, **Custom Font**, dan **Custom Tick**.

---

## Status

| # | Fitur | Status |
|---|---|---|
| 1 | 🔊 TTS — Auto Read Incoming Messages | ❌ Belum |
| 2 | 🔤 Custom Font | ✅ Selesai — `CustomFont.kt` + `CustomFontSettingsFragment.java` + 20 bundled fonts, donatur-gated |
| 3 | ✅ Custom Tick | ✅ Selesai — `CustomTick.kt` + preset manager (add/edit/delete), 5 states, SVG + color per state, donatur-gated |

---

## 1. 🔊 TTS — Auto Read Incoming Messages

### Use Case
Bacakan pesan masuk otomatis **hanya** pada kontak/group terpilih, **hanya** jika bahasa pesan bukan Bahasa Indonesia maupun Jawa.

### Flow
```
FMessageWpp incoming
  └─ Cek: pengirim ada di whitelist? → tidak → skip
  └─ Cek: teks kosong / media only? → skip
  └─ Detect bahasa teks
        └─ Bahasa ID/JW → skip
        └─ Bahasa lain → TextToSpeech.speak()
```

### Language Detection

| Opsi | Cara | Pro | Con |
|---|---|---|---|
| **Android TextClassifier** | `TextClassifier.detectLanguage(text)` | Offline, Android 11+ | Akurasi rendah untuk teks pendek |
| **Groq API** | Kirim teks, detect + translate sekaligus | Sudah ada client, akurasi tinggi | Butuh internet + latency ~500ms |
| **Heuristics** | Cek karakter non-Latin, common ID/JW words | Zero dependency | Mudah false-positive |

**Rekomendasi:** Groq API — proyek sudah punya `GroqClient`, bisa sekalian translate ke ID dulu baru TTS bacakan versi ID-nya. Lebih useful dari bacakan teks asing raw.

**Fallback:** Jika tidak ada internet / API key kosong → pakai `TextClassifier` built-in.

### TTS Engine
Android `TextToSpeech` API (built-in, offline, tidak perlu dependency baru):
```kotlin
val tts = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) {
        tts.language = Locale("id") // baca dalam bahasa Indonesia
        tts.speak(translatedText, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }
}
```

### Hook Point
`ConversationItemListener.onItemBind` atau langsung di `FMessageWpp` incoming hook (pattern sama dengan Deleted Log / Auto Reply).

### Settings UI yang Diperlukan
- Toggle enable/disable
- Pilih kontak whitelist (multi-select dari kontak)
- Toggle: bacakan teks asli vs terjemahan ID
- Speed TTS (slow/normal/fast)
- Volume TTS (atau ikut system volume)

### Risiko
- `TextToSpeech` init async — harus handle `onInit` callback, tidak bisa langsung `.speak()` saat pertama kali
- Pesan masuk saat layar mati / WA di background → perlu `FOREGROUND_SERVICE` atau biarkan drop
- Antrian pesan: kalau 5 pesan masuk sekaligus → `QUEUE_ADD` vs `QUEUE_FLUSH` — perlu diputuskan
- Groq latency: TTS baru jalan setelah ~500ms-1s delay

### Effort Estimasi
- Hook + language detect + TTS playback: **~4-6 jam**
- Settings UI + whitelist picker: **~3-4 jam**
- Total: **~1 hari**

---

## 2. 🔤 Custom Font

### Referensi: GBWhatsApp

GBWha simpan **79 font TTF/OTF** di `assets/fonts/`. Ini proof of concept bahwa bundling font di WhatsApp mod **proven works**. Font list GBWha mencakup:
- Latin: Comfortaa, Pacifico, Raleway, BebasNeue, MavenPro, ProductSans, Helvetica, ComicSans, RobotoMono, SourceSansPro, dll
- Arabic: Alarabiya, ALMajd, Dubai, DroidKufi, Mobily, dll
- Decorative: HaryPotter, Transformers, Bauhaus, Norican, Satisfy, dll

**Strategi: salin subset font GBWha ke `assets/fonts/` WaEnhancer** — sudah proven compatible dengan WhatsApp runtime, tidak perlu reinvent.

### Approach Rekomendasi

Extend `CustomView.kt` — tambah support `font-family` CSS property:

```css
/* user tulis di custom CSS */
TextView {
  font-family: 'comfortaa';
}
```

```kotlin
// di CustomView.kt setRuleInView()
"font-family" -> {
    val fontName = value.trim('\'', '"')
    val typeface = FontCache.getOrLoad(context, fontName)
    if (view is TextView && typeface != null) {
        view.typeface = typeface
    }
}
```

`FontCache` load dari `assets/fonts/` atau dari path file yang user pilih via file picker.

### Font Source Options (Updated)

| Source | Pro | Con |
|---|---|---|
| **Bundled di assets/ (dari GBWha)** | Proven works di WA runtime, offline, ~79 font siap pakai | APK size naik (~10-15MB) |
| **User upload via file picker** | Bebas pilih font apapun | Perlu copy ke app internal storage |
| **Download dari Google Fonts API** | Ribuan font | Butuh internet, perlu cache management |

**Rekomendasi:** Bundled subset GBWha fonts (pilih ~15-20 font populer, skip Arabic kecuali diperlukan) + support user upload `.ttf`/`.otf`. APK size tradeoff acceptable.

**Font rekomendasi dari GBWha untuk di-bundle:**
- Comfortaa, Pacifico, Raleway, BebasNeue, MavenPro, ProductSans
- RobotoMono, SourceSansPro, Courgette, Norican, Satisfy
- Helvetica, CourierPrime, Bauhaus, Exo2

### Scope Hook

WhatsApp punya ratusan `TextView`. Hook semua via CSS engine `CustomView.kt` sudah cover mayoritas — tidak perlu hook per-class.

### Settings UI
- Pilih font dari list (bundled) — tampilkan nama font + preview string
- Upload file .ttf/.otf custom
- Reset ke default

### Risiko
- WhatsApp update bisa refactor internal `TextView` usage → hook bisa tidak cover semua
- Font Arabic/RTL harus ditest terpisah (skip dulu, focus Latin)
- Memory: `Typeface` object cukup besar — wajib cache singleton via `FontCache`
- APK size: 15-20 font ≈ +5-8MB — acceptable

### Effort Estimasi
- Copy font assets dari GBWha + `FontCache` + extend `CustomView.kt`: **~2-3 jam**
- Settings UI + font picker list: **~2-3 jam**
- Total: **~1 hari** (lebih cepat karena font sudah ada, tinggal integrate)

---

## 3. ✅ Custom Tick (Checkmark)

### Referensi: GBWhatsApp

GBWha punya **80+ tick style** bundled sebagai PNG drawable. Naming convention:

```
{style}_message_unsent.png                          → sending (1 tick abu)
{style}_message_got_receipt_from_server.png         → delivered ke server (1 tick penuh)
{style}_message_got_receipt_from_target.png         → delivered ke device (2 tick abu)
{style}_message_got_read_receipt_from_target.png    → read (2 tick biru)
{style}_message_*_onmedia.png                       → versi untuk bubble media (bg gelap)
```

Contoh style tersedia: `allo`, `alien`, `bbm`, `google`, `ios`, `hike`, `messenger`, `heart`, `minions`, `pacman`, dll — 80+ total.

**Strategi: reuse tick PNG dari GBWha langsung ke assets WaEnhancer.**

### Approach

**Level 1 — Custom warna tick** (mudah, extend `SeenTick.kt`):
- Sudah ada `PorterDuff.Mode.SRC_ATOP` tinting di `SeenTick.kt`
- Tambah color picker per-state: sending / delivered / read

**Level 2 — Custom tick style dari GBWha assets** (sedang):
- Copy subset tick PNG dari GBWha ke `assets/ticks/{style}/`
- Hook `getDrawable` / `getResources().getDrawable()` di WhatsApp
- Replace drawable berdasarkan nama (`message_unsent`, `message_got_read_receipt_from_target`, dll) dengan PNG dari assets

**Level 3 — Animated tick** (complex, skip dulu):
- Lottie — belum ada di project, tambah dependency besar

**Rekomendasi: Level 1 dulu, lanjut Level 2.**

### Drawable Names di WhatsApp (target hook)

| State | Drawable WA | GBWha suffix |
|---|---|---|
| Sending | `ic_msg_status_clock` | `_message_unsent` |
| Delivered to server | `ic_msg_status_delivered` | `_message_got_receipt_from_server` |
| Delivered to device | `ic_msg_status_dbl` | `_message_got_receipt_from_target` |
| Read | `ic_notif_mark_read` | `_message_got_read_receipt_from_target` |
| On media (dark bg) | `*_onmedia` variants | `*_onmedia` |

Nama WA perlu di-verify via DexKit runtime probe atau APK inspect — bisa berubah tiap update.

### Settings UI
- Dropdown pilih tick style (dari list bundled, dengan preview gambar)
- Color picker untuk Level 1 (tinting)
- Preview: tampilkan 4 state tick sekaligus

### Tick Styles Rekomendasi untuk di-bundle dari GBWha
`stockorg` (default WA), `ios`, `google`, `messenger`, `hike`, `bbm`, `allo`, `heart`, `alien` — 9 style cukup untuk v1.

### Risiko
- WhatsApp rename drawable tiap update → nama hook perlu re-verify
- `_onmedia` variant penting untuk bubble foto/video — jangan skip
- PNG size harus match slot drawable asli (biasanya 16-24dp)
- Dark mode: `_onmedia` variants sudah handle ini di GBWha

### Effort Estimasi
- Level 1 (custom color): **~2-3 jam**
- Level 2 (custom style dari GBWha assets): **~4-5 jam**
- Total: **~1 hari**

---

## Prioritas & Urutan Implementasi

| # | Fitur | Effort | Complexity | Assets Tersedia | Nilai UX |
|---|---|---|---|---|---|
| 1 | **Custom Tick — Color** | 2-3 jam | Rendah | `SeenTick.kt` sudah hook | Tinggi |
| 2 | **Custom Tick — Style** | 4-5 jam | Sedang | 80+ style PNG dari GBWha | Tinggi |
| 3 | **Custom Font** | 1 hari | Sedang | 79 TTF/OTF dari GBWha | Tinggi |
| 4 | **TTS Auto Read** | 1 hari | Sedang | Groq client existing | Sedang-Tinggi |

**Rekomendasi urutan:** Custom Tick Color → Custom Tick Style → Custom Font → TTS.

GBWha sudah selesaikan masalah "cari font" dan "cari tick drawable" — tinggal copy assets + wire hook. Effort turun signifikan vs estimasi sebelumnya.

---

## Dependency Baru yang Dibutuhkan

| Fitur | Dependency | Status |
|---|---|---|
| TTS | Android `TextToSpeech` (built-in) | ✅ Tidak perlu tambah |
| TTS language detect | `GroqClient` existing | ✅ Sudah ada |
| Custom Font | `Typeface.createFromAsset()` (built-in) | ✅ Tidak perlu tambah |
| Custom Tick | `BitmapFactory`, `PorterDuff` (built-in) | ✅ Tidak perlu tambah |

Tidak ada dependency baru yang perlu ditambah ke `libs.versions.toml`. Semua pakai Android SDK built-in.

---

*Dokumen ini untuk brainstorming — belum final. Update sesuai hasil eksplorasi kode lebih lanjut.*
