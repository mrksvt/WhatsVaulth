import type { ThemeElement, ElementStyle } from './types'

export const DEFAULT_ELEMENTS: ThemeElement[] = [
  { id: '#toolbar', label: 'Toolbar', style: { bg: 'bg-[#075E54]', padding: 'p-4', shadow: 'shadow-md' } },
  { id: '#toolbar TextView', label: 'Toolbar Title', style: { textColor: 'text-white', fontSize: 'text-lg', fontWeight: 'font-bold' } },
  { id: '#search_bar_inner_layout', label: 'Search Bar', style: { bg: 'bg-white', rounded: 'rounded-lg', padding: 'p-2', margin: 'm-2' } },
  { id: '#conversations_row_content', label: 'Chat Row', style: { padding: 'p-4', margin: 'm-1', rounded: 'rounded-lg' } },
  { id: '#conversations_row_contact_name', label: 'Chat Name', style: { textColor: 'text-gray-900', fontWeight: 'font-bold', fontSize: 'text-base' } },
  { id: '#single_msg_tv', label: 'Chat Message', style: { textColor: 'text-gray-500', fontSize: 'text-sm' } },
  { id: '#conversations_row_date', label: 'Chat Time', style: { textColor: 'text-gray-400', fontSize: 'text-xs' } },
  { id: '#fab', label: 'FAB Button', style: { bg: 'bg-[#25D366]', rounded: 'rounded-full', shadow: 'shadow-lg', icon: 'message-circle' } },
  { id: '#send', label: 'Send Button', style: { icon: 'send', width: 'w-10', height: 'h-10' } },
  { id: '#bottom_nav', label: 'Bottom Nav', style: { bg: 'bg-white', padding: 'p-2', shadow: 'shadow-md' } },
  { id: '#input_layout', label: 'Input Bar', style: { bg: 'bg-white', padding: 'p-2', rounded: 'rounded-xl' } },
  { id: '#entry', label: 'Input Field', style: { textColor: 'text-gray-700', fontSize: 'text-sm', padding: 'p-2' } },
  { id: '#conversation_background', label: 'Chat Background', style: { bg: 'bg-[#ECE5DD]' } },
  { id: '#bubble_incoming', label: 'Incoming Bubble', style: { bg: 'bg-white', rounded: 'rounded-xl', padding: 'p-3' } },
  { id: '#bubble_outgoing', label: 'Outgoing Bubble', style: { bg: 'bg-[#DCF8C6]', rounded: 'rounded-xl', padding: 'p-3' } },
]

export function styleToTailwind(style: ElementStyle): string {
  const parts: string[] = []
  if (style.bg) parts.push(style.bg)
  if (style.textColor) parts.push(style.textColor)
  if (style.rounded) parts.push(style.rounded)
  if (style.shadow) parts.push(style.shadow)
  if (style.padding) parts.push(style.padding)
  if (style.margin) parts.push(style.margin)
  if (style.width) parts.push(style.width)
  if (style.height) parts.push(style.height)
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