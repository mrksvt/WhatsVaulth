export type ScreenId = 'home' | 'calls' | 'updates' | 'conversation' | 'groups' | 'communities'

export type DeviceId = 'iphone' | 'android' | 'ipad'

export type ElementType =
  | 'text' | 'icon-btn' | 'image' | 'box' | 'container'

export interface ElementStyle {
  bg?: string; textColor?: string; rounded?: string; shadow?: string
  padding?: string; margin?: string; width?: number; height?: number
  opacity?: string; fontWeight?: string; fontSize?: string; icon?: string
  top?: number; left?: number
}

export interface ThemeElement {
  id: string          // selector css, contoh: '#toolbar'
  label: string
  type: ElementType
  screen: ScreenId
  style: ElementStyle
  removable?: boolean
  customId?: boolean  // true jika id diinput manual
}

export interface Theme {
  name: string
  elements: ThemeElement[]
  wallpaper: string | null
  device: DeviceId
}

export interface ScreenInfo { id: ScreenId; label: string }
