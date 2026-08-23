import type { ThemeElement, ElementStyle, ScreenId, ScreenInfo, DeviceId } from './types'

export const SCREENS: ScreenInfo[] = [
  { id: 'home', label: 'Home' },
  { id: 'calls', label: 'Panggilan' },
  { id: 'updates', label: 'Pembaruan' },
  { id: 'conversation', label: 'Chat' },
  { id: 'groups', label: 'Grup' },
  { id: 'communities', label: 'Komunitas' },
]

export const DEVICES: { id: DeviceId; label: string }[] = [
  { id: 'iphone', label: 'iPhone' },
  { id: 'android', label: 'Android' },
  { id: 'ipad', label: 'iPad' },
]

// ID selector yang tersedia per screen (untuk assignment elemen)
export const ID_OPTIONS: Record<ScreenId, string[]> = {
  home: ['#toolbar', '#toolbar TextView', '#search_bar_inner_layout', '#conversations_row_content',
    '#conversations_row_contact_name', '#single_msg_tv', '#conversations_row_date', '#fab',
    '#bottom_nav', '#main_container', '#conversation_background', '#input_layout', '#entry', '#send'],
  calls: ['#toolbar', '#toolbar TextView', '#call_row_container', '#call_log_title', '#call_log_subtitle',
    '#bottom_nav', '#main_container', '#video_call', '#voice_call'],
  updates: ['#toolbar', '#toolbar TextView', '#status_row_container', '#updates_list', '#bottom_nav',
    '#main_container'],
  conversation: ['#toolbar', '#conversation_contact_name', '#conversation_background', '#conversation_layout',
    '#input_layout', '#entry', '#send', '#camera_btn', '#voice_note_btn', '#input_attach_button', '#emoji_picker_btn'],
  groups: ['#toolbar', '#toolbar TextView', '#conversations_row_content', '#conversations_row_contact_name',
    '#bottom_nav', '#main_container'],
  communities: ['#toolbar', '#toolbar TextView', '#community_header', '#conversations_row_content',
    '#bottom_nav', '#main_container'],
}

export const ADDABLE: { type: ThemeElement['type']; label: string; icon?: string }[] = [
  { type: 'text', label: 'Text', icon: 'type' },
  { type: 'icon-btn', label: 'Icon Button', icon: 'mic' },
  { type: 'image', label: 'Image', icon: 'image' },
  { type: 'box', label: 'Box', icon: 'square' },
  { type: 'container', label: 'Container', icon: 'layout' },
  { type: 'rectangle', label: 'Rectangle', icon: 'square' },
  { type: 'circle', label: 'Circle', icon: 'circle' },
  { type: 'line', label: 'Line', icon: 'minus' },
]

export const DEFAULT_ELEMENTS: ThemeElement[] = []

export function styleToTailwind(style: ElementStyle): string {
  // customClass = override penuh (bukan append) — kalau user edit textarea,
  // seluruh string jadi source of truth; kalau kosong → auto-generate dari style
  if (style.customClass?.trim()) return style.customClass.trim()
  const parts: string[] = []
  if (style.bg) parts.push(style.bg)
  if (style.textColor) parts.push(style.textColor)
  if (style.rounded) parts.push(style.rounded)
  if (style.shadow) parts.push(style.shadow)
  if (style.padding) parts.push(style.padding)
  if (style.margin) parts.push(style.margin)
  if (style.width) parts.push(`w-[${style.width}px]`)
  if (style.height) parts.push(`h-[${style.height}px]`)
  if (style.opacity) parts.push(style.opacity)
  if (style.fontWeight) parts.push(style.fontWeight)
  if (style.fontSize) parts.push(style.fontSize)
  if (style.borderWidth) parts.push(style.borderWidth)
  if (style.borderColor) parts.push(style.borderColor)
  if (style.borderStyle) parts.push(style.borderStyle)
  if (style.rotate) parts.push(style.rotate)
  if (style.cornerRadius) {
    const cr = style.cornerRadius
    if (cr.tl) parts.push(`rounded-tl-${cr.tl}`)
    if (cr.tr) parts.push(`rounded-tr-${cr.tr}`)
    if (cr.bl) parts.push(`rounded-bl-${cr.bl}`)
    if (cr.br) parts.push(`rounded-br-${cr.br}`)
  }
  return parts.join(' ')
}

export function styleToCssClass(style: ElementStyle): string {
  return styleToTailwind(style)
}

export function cssForElements(elements: ThemeElement[]): string {
  return elements.map((el) => {
    const cls = styleToTailwind(el.style)
    const icon = el.style.icon
    let css = `${el.id} {\n`
    if (cls) css += `  class: "${cls}";\n`
    if (icon) css += `  icon: ${icon};\n`
    css += `}\n`
    return css
  }).join('\n')
}

export function parseCssUpdates(css: string, knownIds: string[]): Map<string, string> {
  const updates = new Map<string, string>()
  // regex: #id { class: "..." }
  const blockRe = /#([^{]+?)\s*\{\s*(?:class:\s*"([^"]*)"\s*;?\s*)?[^}]*\s*\}/g
  let m
  while ((m = blockRe.exec(css)) !== null) {
    const id = `#${m[1].trim()}`
    if (!knownIds.includes(id)) continue
    const cls = (m[2] || '').trim()
    updates.set(id, cls)
  }
  return updates
}

export const TAILWIND_COLORS = [
  { name: 'slate', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'gray', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'red', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'orange', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'amber', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'yellow', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'green', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'emerald', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'teal', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'cyan', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'blue', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'indigo', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'violet', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'purple', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'pink', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'rose', shades: [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] },
  { name: 'white', shades: [] },
  { name: 'black', shades: [] },
  { name: 'transparent', shades: [] },
]

export const SPACING = [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 72, 80, 96]
export const RADIUS = ['none', 'sm', 'md', 'lg', 'xl', '2xl', '3xl', 'full']
export const SHADOWS = ['none', 'sm', 'md', 'lg', 'xl', '2xl']
export const FONT_SIZES = ['xs', 'sm', 'base', 'lg', 'xl', '2xl', '3xl']
export const FONT_WEIGHTS = ['font-light', 'font-normal', 'font-medium', 'font-semibold', 'font-bold']

// Template element groups (hello_remake style)
export interface ElementTemplate {
  name: string
  screen: ScreenId
  elements: Omit<ThemeElement, 'screen'>[]
}

export const TEMPLATES: ElementTemplate[] = [
  {
    name: 'Toolbar (Home)',
    screen: 'home',
    elements: [
      { id: '#toolbar', label: 'Toolbar', type: 'box', style: { width: 360, height: 56, top: 0, left: 0, bg: 'bg-teal-600' }, removable: true, customId: false },
      { id: '#search_bar_inner_layout', label: 'Search', type: 'box', style: { width: 200, height: 40, top: 8, left: 80, bg: 'bg-white', rounded: 'rounded-full' }, removable: true, customId: false },
      { id: '#menu_icon', label: 'Menu', type: 'icon-btn', style: { width: 40, height: 40, top: 8, left: 320, icon: 'menu' }, removable: true, customId: true },
    ],
  },
  {
    name: 'Bottom Navigation',
    screen: 'home',
    elements: [
      { id: '#bottom_nav', label: 'Bottom Nav Container', type: 'container', style: { width: 360, height: 56, top: 744, left: 0, bg: 'bg-gray-100' }, removable: true, customId: false },
      { id: '#nav_chat', label: 'Nav Chat', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 40, icon: 'message-square' }, removable: true, customId: true },
      { id: '#nav_status', label: 'Nav Status', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 130, icon: 'circle-dashed' }, removable: true, customId: true },
      { id: '#nav_call', label: 'Nav Call', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 220, icon: 'phone' }, removable: true, customId: true },
      { id: '#nav_settings', label: 'Nav Settings', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 310, icon: 'settings' }, removable: true, customId: true },
    ],
  },
  {
    name: 'Chat Input Bar',
    screen: 'conversation',
    elements: [
      { id: '#input_layout', label: 'Input Container', type: 'container', style: { width: 360, height: 56, top: 744, left: 0, bg: 'bg-white' }, removable: true, customId: false },
      { id: '#emoji_picker_btn', label: 'Emoji', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 8, icon: 'smile' }, removable: true, customId: false },
      { id: '#input_attach_button', label: 'Attach', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 48, icon: 'paperclip' }, removable: true, customId: false },
      { id: '#entry', label: 'Input Field', type: 'box', style: { width: 200, height: 40, top: 752, left: 88, bg: 'bg-gray-100', rounded: 'rounded-full' }, removable: true, customId: false },
      { id: '#camera_btn', label: 'Camera', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 296, icon: 'camera' }, removable: true, customId: false },
      { id: '#voice_note_btn', label: 'Voice', type: 'icon-btn', style: { width: 32, height: 32, top: 756, left: 336, icon: 'mic' }, removable: true, customId: false },
    ],
  },
  {
    name: 'Conversation Row',
    screen: 'home',
    elements: [
      { id: '#conversations_row_content', label: 'Chat Row', type: 'box', style: { width: 360, height: 72, top: 80, left: 0, bg: 'bg-white' }, removable: true, customId: false },
      { id: '#conversations_row_contact_name', label: 'Contact Name', type: 'text', style: { width: 200, height: 20, top: 88, left: 72, textColor: 'text-gray-900', fontWeight: 'font-semibold' }, removable: true, customId: false },
      { id: '#single_msg_tv', label: 'Last Message', type: 'text', style: { width: 200, height: 16, top: 112, left: 72, textColor: 'text-gray-600', fontSize: 'text-sm' }, removable: true, customId: false },
      { id: '#conversations_row_date', label: 'Date', type: 'text', style: { width: 60, height: 14, top: 88, left: 288, textColor: 'text-gray-400', fontSize: 'text-xs' }, removable: true, customId: false },
    ],
  },
]
