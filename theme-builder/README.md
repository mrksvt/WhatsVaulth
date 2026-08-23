# WhatsVault Theme Builder (Web)

Theme builder berbasis web untuk membuat tema WhatsApp, di luar aplikasi Android WhatsVault. Export memakai **Tailwind class** + **icon** (lucide) yang dipahami engine CSS CustomView.

## Cara Pakai

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # produksi (dist/)
```

## Alur

1. **Pilih elemen** (kiri): toolbar, chat row, bubble, fab, send button, dll
2. **Edit property** (kanan): background/text color, radius, shadow, padding, w/h, font, opacity, icon
3. **Live preview** (tengah): mockup WhatsApp berubah real-time
4. **Export ZIP**: `nama-tema.zip` → Import di Theme Shop (WhatsVault)

## Format Export

```
nama-tema/
├── style.css          # CSS rules: class (Tailwind) + icon
├── nama-tema.json     # prefs map (changecolor, custom_filters, custom_css, folder_theme)
├── wall.png           # wallpaper (opsional)
└── lucide/{icon}.svg  # icon lucide yang dipakai
```

### style.css (contoh)
```css
/* comment block: warna untuk CustomThemeV2 */
primary_color = #075E54
text_color = #111B21
background_color = #ECE5DD
bubble_right = #DCF8C6
bubble_left = #FFFFFF
change_colors = true
*/

#toolbar { class: "bg-[#075E54] p-4 shadow-md"; }
#send { icon: lucide-send; class: "w-10 h-10 color-tint-white"; }
```

## Dukungan Engine (CustomView)

- **Tailwind utility** (property `class`): `bg-*`, `text-*`, `border-*`, `rounded-*`, `shadow-*`, `p/m [x/y/t/r/b/l]-*`, `w-*`, `h-*`, `opacity-*`, `text-{size}`, `font-{weight}`, `hidden/block/flex`, `color-tint-*`
- **Warna Tailwind**: palette 26 warna (slate..rose, white, black) + shade 50-950 (interpolasi)
- **Icon** (property `icon`): `lucide-{name}` → cari `lucide/{name}.svg` di folder tema
- **background-image**: PNG/JPG/SVG (engine sudah support)

## Teknologi

Vite 8 + React 19 + TypeScript + TailwindCSS v4 + lucide-react + jszip