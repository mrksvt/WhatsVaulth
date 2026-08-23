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
]

export const DEFAULT_ELEMENTS: ThemeElement[] = []

export function styleToTailwind(style: ElementStyle): string {
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
  return parts.join(' ')
}

export function styleToCssClass(style: ElementStyle): string {
  return styleToTailwind(style)
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
