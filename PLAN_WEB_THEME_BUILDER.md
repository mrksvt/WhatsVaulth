# Web Theme Builder — Plan & Todo

> **Tujuan:** Theme builder berbasis web (React + TailwindCSS) di luar aplikasi Android. Export memakai Tailwind class. Engine CSS WhatsVault (`CustomView`) diperluas agar support Tailwind utility + icon font (bootstrap, lucide, fontawesome, material icons).

## Status: ✅ Release v1.5.5-stable (commit 691e8726)

---

## Arsitektur

```
┌─────────────────────────────┐      ┌──────────────────────────────┐
│  Web Theme Builder          │      │  WhatsVault (Android)        │
│  React + Tailwind + Vite    │      │                              │
│                             │      │  CustomView CSS Engine       │
│  - Preview WhatsApp mockup  │ ZIP  │  ├─ Tailwind utility parser  │
│  - Drag & drop elemen       │─────▶│  ├─ Icon font renderer       │
│  - Export: style.css        │      │  │  (lucide/bootstrap/fa/mdi)│
│    + theme.json + assets    │      │  ├─ existing CSS properties  │
│                             │      │  └─ background-image (png/svg)│
└─────────────────────────────┘      └──────────────────────────────┘
```

## Format Export (ZIP)

```
theme-name/
├── style.css        # CSS rules: Tailwind class + icon + properties
├── theme-name.json  # prefs map: changecolor, warna, custom_css, folder_theme
└── assets/          # gambar (wallpaper.png, icon.svg, dll)
```

### Contoh style.css (format baru)
```css
/* Tailwind utility diproses engine */
#toolbar { @apply bg-[#6C63FF] text-white rounded-b-2xl shadow-lg; }
#send-btn { @apply icon:lucide-send w-10 h-10; }
#conversation_background { background-image: url("assets/wall.png"); }
```

---

## FASE 1 — Engine WhatsVault: dukungan Tailwind + Icon font

### 1.1 Tailwind utility parser (CustomView.kt)
- [ ] Tambah property `@apply` / `class` di `when(property)` → parse Tailwind utility subset
- [ ] Support utility: warna (`bg-*`, `text-*`, `border-*`, `shadow-*`), spacing (`p-*`,`m-*`,`w-*`,`h-*`,`gap-*`), radius (`rounded-*`), display (`hidden`,`flex`,`block`), tipografi (`text-xs..xl`,`font-bold`), opacity
- [ ] Warna Tailwind: map nama (red-500, emerald-300, dll) → hex (subset palette standar)
- [ ] Fallback: utility tak dikenal → ignore (log)

### 1.2 Icon font renderer (CustomView.kt / DesignUtils.kt)
- [ ] Bundle icon font: `lucide.ttf` (prioritas — open source, kecil)
- [ ] Tambah property `icon:` → cari glyph di font → render ke BitmapDrawable (seperti SVG path)
- [ ] Support selector `#id { icon: lucide-send; }` → set drawable
- [ ] Map nama icon → codepoint (subset umum: send, mic, camera, attach, emoji, check, dll)

### 1.3 Format & kompatibilitas
- [ ] Parser CSS terima `@apply` dan `icon:` di samping property existing
- [ ] JSON theme builder: tambah field `"engine": "tailwind"` (opsional, backward compatible)
- [ ] Test: import ZIP hasil export web → preview + apply jalan

---

## FASE 2 — Web Theme Builder (React + Tailwind + Vite)

### 2.1 Setup project
- [ ] `theme-builder/` — Vite + React + TailwindCSS (v4)
- [ ] Tailwind config: custom theme (WhatsApp palette)

### 2.2 Editor UI
- [ ] Canvas preview: mockup WhatsApp (toolbar, chat list, chat bubble, input bar)
- [ ] Panel elemen: pilih elemen (toolbar, bubble, background, fab, dll)
- [ ] Property editor: warna, ukuran, radius, shadow, padding, margin
- [ ] Icon picker: lucide/bootstrap/fontawesome/material (cari + pilih)
- [ ] Live preview: Tailwind class diterapkan real-time di mockup
- [ ] Undo/redo + reset

### 2.3 Export
- [ ] Generate `style.css` (Tailwind class + icon + properties)
- [ ] Generate `theme-name.json` (prefs map)
- [ ] Bundle assets (wallpaper upload + icon SVG)
- [ ] Download ZIP (`theme-name.zip`)

### 2.4 Import ke WhatsVault
- [ ] ZIP hasil export → Theme Shop → Import → apply (sudah ada pipeline)
- [ ] Validasi: engine parse Tailwind + icon dengan benar

---

## FASE 3 — Aset icon font

- [ ] Download/ekspor lucide font (atau gunakan SVG icon inline — engine sudah support SVG background-image)
- [ ] Bootstrap Icons (opsional, ukuran besar)
- [ ] Font Awesome (opsional)
- [ ] Material Icons (opsional)

> **Keputusan:** mulai dengan **Lucide** (open source, konsisten, kecil). Bootstrap/FA/MDI menyusul jika format glyph sama.

---

## Todo List (eksekusi berurutan)

- [ ] **T1** Commit + release APK v1.5.5-stable ✅ (691e8726)
- [ ] **T2** Tailwind utility parser di CustomView (subset warna/spacing/radius/display/tipografi)
- [ ] **T3** Icon renderer: bundle lucide.ttf + property `icon:` + map glyph
- [ ] **T4** Test engine: ZIP tema Tailwind + icon → import → apply
- [ ] **T5** Setup Vite + React + Tailwind theme-builder
- [ ] **T6** Editor UI: canvas mockup + panel elemen + property editor
- [ ] **T7** Icon picker + live preview Tailwind
- [ ] **T8** Export ZIP (style.css + json + assets)
- [ ] **T9** End-to-end: web export → WhatsVault import → apply → verifikasi
- [ ] **T10** Commit + release + dokumentasi format

---

## Risiko & Keputusan

| Risiko | Mitigasi |
|--------|----------|
| Engine tak kenal Tailwind | T2: parser `@apply`/`class` + fallback ignore |
| Ukuran APK (font icon) | Mulai Lucide saja (~150KB); sisanya via SVG |
| Tailwind palette besar | Subset ~100 warna umum dulu, perluas jika perlu |
| Web builder besar | Iterasi: preview dasar → property → export |

**Lokasi:** `theme-builder/` di repo (web terpisah dari Android app).
