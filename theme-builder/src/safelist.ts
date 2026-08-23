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

// Utility umum untuk custom class
const UTILITY_VALS = ['0', '0.5', '1', '1.5', '2', '2.5', '3', '3.5', '4', '5', '6', '7', '8', '9', '10', '12', '14', '16', '20', '24', '28', '32', '36', '40', '44', '48', '52', '56', '60', '64', '72', '80', '96']
const TRANSFORMS = ['scale', 'skew']
const BLURS = ['none', 'sm', 'md', 'lg', 'xl', '2xl', '3xl']
for (const t of TRANSFORMS) for (const v of UTILITY_VALS.slice(0, 15)) list.push(`${t}-${v}`)
for (const b of BLURS) list.push(`blur-${b}`)
for (const v of ['none', 'sm', 'md', 'lg', 'xl', '2xl']) list.push(`drop-shadow-${v}`)
for (const v of ['0', '50', '75', '90', '95', '100']) list.push(`grayscale-${v}`, `invert-${v}`, `sepia-${v}`)
list.push('brightness-50', 'brightness-75', 'brightness-90', 'brightness-95', 'brightness-100', 'brightness-105', 'brightness-110', 'brightness-125', 'brightness-150', 'brightness-200')
list.push('contrast-0', 'contrast-50', 'contrast-75', 'contrast-100', 'contrast-125', 'contrast-150', 'contrast-200')
list.push('saturate-0', 'saturate-50', 'saturate-100', 'saturate-150', 'saturate-200')
list.push('hue-rotate-0', 'hue-rotate-15', 'hue-rotate-30', 'hue-rotate-60', 'hue-rotate-90', 'hue-rotate-180')
list.push('transition-all', 'transition', 'transition-colors', 'transition-opacity', 'transition-shadow', 'transform', 'filter', 'backdrop-filter')
list.push('cursor-pointer', 'cursor-default', 'cursor-move', 'cursor-grab', 'cursor-grabbing', 'select-none', 'select-all', 'pointer-events-none', 'pointer-events-auto')
list.push('overflow-hidden', 'overflow-auto', 'overflow-scroll', 'overflow-visible')
list.push('truncate', 'whitespace-nowrap', 'whitespace-pre', 'break-words', 'break-all')
list.push('z-0', 'z-10', 'z-20', 'z-30', 'z-40', 'z-50')
list.push('gap-0', 'gap-1', 'gap-2', 'gap-3', 'gap-4', 'gap-5', 'gap-6', 'gap-8')
list.push('items-center', 'items-start', 'items-end', 'justify-center', 'justify-between', 'justify-around', 'flex-1', 'flex-col', 'flex-row', 'flex-wrap')
list.push('absolute', 'relative', 'fixed', 'static', 'sticky', 'inset-0', 'top-0', 'left-0', 'right-0', 'bottom-0')
for (const h of ['hover', 'focus', 'active', 'group-hover']) {
  for (const o of OPACITIES) list.push(`${h}:opacity-${o}`)
  for (const s of SHADOWS) list.push(`${h}:shadow-${s}`)
  list.push(`${h}:scale-110`, `${h}:scale-105`, `${h}:scale-95`, `${h}:scale-90`)
  list.push(`${h}:translate-x-1`, `${h}:translate-x-2`, `${h}:-translate-x-1`, `${h}:-translate-x-2`)
  list.push(`${h}:translate-y-1`, `${h}:translate-y-2`, `${h}:-translate-y-1`, `${h}:-translate-y-2`)
}

export const safelist = list
export default safelist
