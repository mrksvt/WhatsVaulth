# Web Theme Builder — Plan & Todo

> **Tujuan:** Theme builder berbasis web (React + TailwindCSS) di luar aplikasi Android. Export memakai Tailwind class. Engine CSS WhatsVault (`CustomView`) diperluas agar support Tailwind utility + icon (lucide dll).

## Status Terkini

| Komponen | Status |
|----------|--------|
| Release APK v1.5.5-stable | ✅ `691e8726` (release) / `a7e0195e` (flag beta) |
| Engine Tailwind parser | ✅ `class:` property (bg/text/rounded/shadow/p/m/w/h/opacity/font) |
| Engine icon renderer | ✅ `icon:` property → `lucide/{name}.svg` |
| Web builder scaffold | ✅ Vite + React + Tailwind v4, deploy pm2 `10.42.0.1:4173` |
| Mockup multi-screen | ✅ Home, Panggilan, Pembaruan, Chat, Grup, Komunitas |
| Device frame | ✅ `react-device-mockup` (iPhone/Android/iPad) |
| Canvas polos build-from-0 | ✅ Tambah elemen: Text, Icon, Image, Box, Container |
| ID assignment | ✅ Dropdown ID per screen + manual |
| Resize handle | ✅ Drag akurat, tidak menggeser elemen |
| Move (drag) | ✅ Drag langsung, tanpa toggle klik |
| Marquee select | ✅ Drag canvas → lasso pilih multi elemen (Figma style) |
| Lock elemen | ✅ Lock via tombol / setelah drag |
| Klik kanan modal | ✅ ID, konten, icon, warna, radius |
| Template elemen | ✅ Toolbar, Bottom Nav, Chat Input, Conversation Row |
| Safe area | ✅ Boundary clamp dinamis per device (ResizeObserver) |
| Export ZIP | ✅ style.css + theme.json + assets |

---

## Gap yang Sudah Diimplementasikan

1. **Tailwind utility parser** (CustomView.kt) — `class: "bg-[#..] p-4 ..."` diparse ke style view
2. **Icon renderer** — `icon: lucide-send` → cari `lucide/send.svg` → BitmapDrawable
3. **Multi-screen mockup** — 6 tab screen, tiap screen punya elemen sendiri
4. **Device frame** — iPhone/Android/iPad via `react-device-mockup`
5. **Canvas polos** — mockup kosong, user tambah elemen dari 0
6. **ID assignment** — elemen bisa di-assign CSS selector (dropdown + manual)
7. **Klik kanan modal** — edit ID, konten (text/icon/box/image), icon lucide, warna, radius
8. **Export ZIP** — `style.css` (class: + icon:) + `theme-name.json` + wallpaper + lucide SVG
9. **Interaksi recycle** — klik 1 → resize, klik 2 → move, klik 3 → resize (count indicator)
10. **Lock via drag-drop** — elemen terkunci setelah drag selesai
11. **Marquee select** — drag canvas kosong → lasso select multi elemen (bbox collision), klik kosong → deselect
12. **DnD stabil** — klik = select, drag = move (tanpa mode toggle), pointer capture sebelum dragRef, guard resize vs move
13. **Resize akurat** — `resizingRef` mencegah elemen ikut berpindah saat resize handle didrag
14. **Multi-select** — `selectedIds[]` (bukan `selectedId`), delete/duplicate batch, updateStyle patch semua terpilih
15. **Template elemen** — 4 template (Toolbar, Bottom Nav, Chat Input, Conversation Row) dari hello_remake, insert via modal, ID unik + offset
16. **Safe area** — boundary clamp dinamis: ResizeObserver ukur tinggi canvas aktual per device, move/resize tidak bisa keluar frame

## Gap yang Belum / Bermasalah (TODO)

### 🟡 Penyempurnaan
- [ ] Snap to grid / align guide (opsional)
- [ ] Undo/redo (opsional)

### 🟢 Engine / Export
- [ ] Engine: `top-[Xpx] left-[Xpx]` arbitrary value di-tailwind parser (sudah di web, perlu engine)
- [ ] Export: validasi selector ID vs daftar yang dikenal engine

---

## Arsitektur

```
┌─────────────────────────────┐      ┌──────────────────────────────┐
│  Web Theme Builder          │      │  WhatsVault (Android)        │
│  React + Tailwind + Vite    │      │                              │
│                             │      │  CustomView CSS Engine       │
│  - Canvas polos + elemen    │ ZIP  │  ├─ Tailwind utility parser  │
│  - Multi-screen mockup      │─────▶│  ├─ Icon renderer (lucide)   │
│  - Device frame             │      │  ├─ existing CSS properties  │
│  - Export: style.css        │      │  └─ background-image (png/svg)│
│    + theme.json + assets    │      │                              │
└─────────────────────────────┘      └──────────────────────────────┘
```

## Format Export (ZIP)

```
theme-name/
├── style.css        # CSS rules: class (Tailwind) + icon + posisi
├── theme-name.json  # prefs map: changecolor, custom_filters, custom_css, folder_theme
├── wall.png         # wallpaper (opsional)
└── lucide/{icon}.svg # icon yang dipakai
```

### style.css (contoh)
```css
/* comment block: warna */
primary_color = #075E54
text_color = #111B21
background_color = #ECE5DD
bubble_right = #DCF8C6
bubble_left = #FFFFFF
change_colors = true
*/

#toolbar { class: "bg-[#075E54] p-4 shadow-md"; }
#send { icon: lucide-send; class: "w-10 h-10"; }
```

---

## Todo (prioritas)

- [x] **T-S** Select dengan DnD di canvas — marquee/lasso pilih satu/beberapa elemen (Figma/Canva style), drag elemen untuk move
- [x] **T-A** Fix interaksi DnD canvas (debug event flow, pointer capture, klik vs drag)
- [x] **T-B** Stabilkan resize handle & move (drag langsung, bukan hanya toggle)
- [x] **T-C** Lock via drag-drop yang andal
- [x] **T-D** Duplicate offset (elemen copy tidak menumpuk)
- [x] **T-H** Template elemen (Toolbar, Bottom Nav, Chat Input, Conversation Row)
- [x] **T-I** Safe area — boundary clamp dinamis per device
- [ ] **T-E** Engine: top/left arbitrary di Tailwind parser
- [ ] **T-F** Validasi ID selector export
- [ ] **T-G** Snap grid / undo-redo (opsional)

## Deploy

```bash
cd theme-builder
npm run build
scp -r dist/* anlap05:~/theme-builder/dist/
ssh anlap05 'pm2 restart theme-builder'
# http://10.42.0.1:4173/
```
