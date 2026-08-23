// Safelist semua Tailwind class yang mungkin dipakai (dibangun dinamis di PropertyPanel).
// Tailwind v4 scanner hanya detect literal string — class concat (bg-${name}) tidak ter-generate
// tanpa daftar ini. Import di App.tsx agar scanner melihat semua kombinasi.

const COLORS = [
  'slate', 'gray', 'zinc', 'neutral', 'stone', 'red', 'orange', 'amber',
  'yellow', 'lime', 'green', 'emerald', 'teal', 'cyan', 'sky', 'blue',
  'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose',
]
const SHADES = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950]
const SPACING = ['0','0.5','1','1.5','2','2.5','3','3.5','4','5','6','7','8','9','10','11','12','14','16','20','24','28','32','36','40','44','48','52','56','60','64','72','80','96']
const SIZES = ['4','6','8','10','12','14','16','20','24','32']
const RADIUS = ['none','sm','md','lg','xl','2xl','3xl','full']
const SHADOWS = ['none','sm','md','lg','xl','2xl']
const FONTSIZES = ['xs','sm','base','lg','xl','2xl','3xl']
const FONTWEIGHTS = ['font-light','font-normal','font-medium','font-semibold','font-bold']
const OPACITIES = ['0','25','50','75','100']

const list: string[] = []

for (const c of COLORS) {
  for (const s of SHADES) {
    list.push(`bg-${c}-${s}`, `text-${c}-${s}`, `border-${c}-${s}`)
  }
}
list.push('bg-white','bg-black','bg-transparent', 'text-white','text-black', 'border-white','border-black')
for (const n of SPACING) {
  list.push(`p-${n}`, `px-${n}`, `py-${n}`, `pt-${n}`, `pr-${n}`, `pb-${n}`, `pl-${n}`)
  list.push(`m-${n}`, `mx-${n}`, `my-${n}`, `mt-${n}`, `mr-${n}`, `mb-${n}`, `ml-${n}`)
}
for (const n of SIZES) list.push(`w-${n}`, `h-${n}`)
for (const r of RADIUS) list.push(`rounded-${r}`)
for (const s of SHADOWS) list.push(`shadow-${s}`)
for (const f of FONTSIZES) list.push(`text-${f}`)
for (const w of FONTWEIGHTS) list.push(w)
for (const o of OPACITIES) list.push(`opacity-${o}`)
list.push('hidden', 'block', 'flex', 'invisible')

export const safelist = list
export default safelist
