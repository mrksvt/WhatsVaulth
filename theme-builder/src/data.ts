import type { ThemeElement, ElementStyle, ScreenInfo } from './types'

export const SCREENS: ScreenInfo[] = [
  { id: 'home', label: 'Home / Chat' },
  { id: 'calls', label: 'Panggilan' },
  { id: 'updates', label: 'Pembaruan' },
  { id: 'conversation', label: 'Chat' },
  { id: 'groups', label: 'Grup' },
  { id: 'communities', label: 'Komunitas' },
]

export const DEFAULT_ELEMENTS: ThemeElement[] = [
  // ── HOME ──
  { id: 'home_toolbar', label: 'Toolbar', type: 'toolbar', screen: 'home', style: { bg: 'bg-[#075E54]', padding: 'p-4', shadow: 'shadow-md' } },
  { id: 'home_toolbar_title', label: 'Toolbar Title', type: 'toolbar-title', screen: 'home', style: { textColor: 'text-white', fontSize: 'text-lg', fontWeight: 'font-bold' } },
  { id: 'home_search', label: 'Search Bar', type: 'search', screen: 'home', style: { bg: 'bg-white', rounded: 'rounded-lg', padding: 'p-2', margin: 'm-2' }, removable: true },
  { id: 'home_row', label: 'Chat Row', type: 'chat-row', screen: 'home', style: { padding: 'p-4', margin: 'm-1', rounded: 'rounded-lg' }, removable: true },
  { id: 'home_row_name', label: 'Chat Name', type: 'chat-name', screen: 'home', style: { textColor: 'text-gray-900', fontWeight: 'font-bold', fontSize: 'text-base' } },
  { id: 'home_row_msg', label: 'Chat Message', type: 'chat-message', screen: 'home', style: { textColor: 'text-gray-500', fontSize: 'text-sm' } },
  { id: 'home_row_time', label: 'Chat Time', type: 'chat-time', screen: 'home', style: { textColor: 'text-gray-400', fontSize: 'text-xs' } },
  { id: 'home_fab', label: 'FAB', type: 'fab', screen: 'home', style: { bg: 'bg-[#25D366]', rounded: 'rounded-full', shadow: 'shadow-lg', icon: 'message-circle' }, removable: true },
  { id: 'home_nav', label: 'Bottom Nav', type: 'bottom-nav', screen: 'home', style: { bg: 'bg-white', padding: 'p-2', shadow: 'shadow-md' } },

  // ── CALLS ──
  { id: 'calls_toolbar', label: 'Toolbar', type: 'toolbar', screen: 'calls', style: { bg: 'bg-[#075E54]', padding: 'p-4' } },
  { id: 'calls_toolbar_title', label: 'Toolbar Title', type: 'toolbar-title', screen: 'calls', style: { textColor: 'text-white', fontSize: 'text-lg', fontWeight: 'font-bold' } },
  { id: 'calls_row', label: 'Call Row', type: 'call-row', screen: 'calls', style: { padding: 'p-4', margin: 'm-1', rounded: 'rounded-lg' }, removable: true },
  { id: 'calls_row_name', label: 'Call Name', type: 'call-name', screen: 'calls', style: { textColor: 'text-gray-900', fontWeight: 'font-semibold', fontSize: 'text-base' } },
  { id: 'calls_row_type', label: 'Call Type', type: 'call-type', screen: 'calls', style: { textColor: 'text-gray-500', fontSize: 'text-xs' } },
  { id: 'calls_nav', label: 'Bottom Nav', type: 'bottom-nav', screen: 'calls', style: { bg: 'bg-white', padding: 'p-2' } },

  // ── UPDATES ──
  { id: 'updates_toolbar', label: 'Toolbar', type: 'toolbar', screen: 'updates', style: { bg: 'bg-[#075E54]', padding: 'p-4' } },
  { id: 'updates_toolbar_title', label: 'Toolbar Title', type: 'toolbar-title', screen: 'updates', style: { textColor: 'text-white', fontSize: 'text-lg', fontWeight: 'font-bold' } },
  { id: 'updates_row', label: 'Status Row', type: 'status-row', screen: 'updates', style: { padding: 'p-4', margin: 'm-1', rounded: 'rounded-lg' }, removable: true },
  { id: 'updates_row_name', label: 'Status Name', type: 'status-name', screen: 'updates', style: { textColor: 'text-gray-900', fontWeight: 'font-semibold', fontSize: 'text-base' } },
  { id: 'updates_row_time', label: 'Status Time', type: 'status-time', screen: 'updates', style: { textColor: 'text-gray-500', fontSize: 'text-xs' } },
  { id: 'updates_ring', label: 'Status Ring', type: 'status-ring', screen: 'updates', style: { width: 'w-12', height: 'h-12', rounded: 'rounded-full', bg: 'bg-green-500' } },
  { id: 'updates_nav', label: 'Bottom Nav', type: 'bottom-nav', screen: 'updates', style: { bg: 'bg-white', padding: 'p-2' } },

  // ── CONVERSATION ──
  { id: 'conv_toolbar', label: 'Chat Toolbar', type: 'conv-toolbar', screen: 'conversation', style: { bg: 'bg-[#075E54]', padding: 'p-3' } },
  { id: 'conv_name', label: 'Chat Name', type: 'conv-name', screen: 'conversation', style: { textColor: 'text-white', fontWeight: 'font-bold', fontSize: 'text-base' } },
  { id: 'conv_bubble_in', label: 'Incoming Bubble', type: 'bubble-incoming', screen: 'conversation', style: { bg: 'bg-white', rounded: 'rounded-xl', padding: 'p-3' }, removable: true },
  { id: 'conv_bubble_out', label: 'Outgoing Bubble', type: 'bubble-outgoing', screen: 'conversation', style: { bg: 'bg-[#DCF8C6]', rounded: 'rounded-xl', padding: 'p-3' }, removable: true },
  { id: 'conv_input', label: 'Input Bar', type: 'input', screen: 'conversation', style: { bg: 'bg-white', padding: 'p-2', rounded: 'rounded-xl' } },
  { id: 'conv_send', label: 'Send Button', type: 'send', screen: 'conversation', style: { icon: 'send', width: 'w-10', height: 'h-10' }, removable: true },

  // ── GROUPS ──
  { id: 'groups_toolbar', label: 'Toolbar', type: 'toolbar', screen: 'groups', style: { bg: 'bg-[#075E54]', padding: 'p-4' } },
  { id: 'groups_toolbar_title', label: 'Toolbar Title', type: 'toolbar-title', screen: 'groups', style: { textColor: 'text-white', fontSize: 'text-lg', fontWeight: 'font-bold' } },
  { id: 'groups_row', label: 'Group Row', type: 'chat-row', screen: 'groups', style: { padding: 'p-4', margin: 'm-1', rounded: 'rounded-lg' }, removable: true },
  { id: 'groups_badge', label: 'Group Badge', type: 'group-badge', screen: 'groups', style: { bg: 'bg-green-100', textColor: 'text-green-700', rounded: 'rounded-md', padding: 'p-1', fontSize: 'text-xs' }, removable: true },
  { id: 'groups_nav', label: 'Bottom Nav', type: 'bottom-nav', screen: 'groups', style: { bg: 'bg-white', padding: 'p-2' } },

  // ── COMMUNITIES ──
  { id: 'com_toolbar', label: 'Toolbar', type: 'toolbar', screen: 'communities', style: { bg: 'bg-[#075E54]', padding: 'p-4' } },
  { id: 'com_toolbar_title', label: 'Toolbar Title', type: 'toolbar-title', screen: 'communities', style: { textColor: 'text-white', fontSize: 'text-lg', fontWeight: 'font-bold' } },
  { id: 'com_header', label: 'Community Header', type: 'community-header', screen: 'communities', style: { bg: 'bg-white', rounded: 'rounded-xl', padding: 'p-4', margin: 'm-2', shadow: 'shadow-md' }, removable: true },
  { id: 'com_row', label: 'Group Row', type: 'chat-row', screen: 'communities', style: { padding: 'p-4', margin: 'm-1', rounded: 'rounded-lg' }, removable: true },
  { id: 'com_nav', label: 'Bottom Nav', type: 'bottom-nav', screen: 'communities', style: { bg: 'bg-white', padding: 'p-2' } },
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
