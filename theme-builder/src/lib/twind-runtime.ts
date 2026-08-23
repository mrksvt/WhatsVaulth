import { setup, cssom, observe } from '@twind/core'
import presetTailwind from '@twind/preset-tailwind'
import presetAutoprefix from '@twind/preset-autoprefix'

// Style tag di APPEND ke akhir head — twind harus menang dari Tailwind build CSS
// (default cssom() prepend ke head, Tailwind build CSS lebih akhir → override twind)
const styleEl = document.createElement('style')
document.head.appendChild(styleEl)

const sheet = cssom(styleEl)
const tw = setup(
  {
    presets: [presetAutoprefix(), presetTailwind()],
    hash: false,
  },
  sheet,
)

observe(tw)
