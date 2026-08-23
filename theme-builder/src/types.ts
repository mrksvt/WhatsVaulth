export type ScreenId = 'home' | 'calls' | 'updates' | 'conversation' | 'groups' | 'communities'

export type DeviceId = 'iphone' | 'android' | 'ipad'

export type ElementType =
  | 'text' | 'icon-btn' | 'image' | 'box' | 'container'
  | 'rectangle' | 'circle' | 'line'

export interface ElementStyle {
  bg?: string; textColor?: string; rounded?: string; shadow?: string
  padding?: string; margin?: string; width?: number; height?: number
  opacity?: string; fontWeight?: string; fontSize?: string; icon?: string
  top?: number; left?: number
  borderWidth?: string; borderColor?: string; borderStyle?: string
  rotate?: string; cornerRadius?: { tl?: string; tr?: string; bl?: string; br?: string }
  customClass?: string
}

export interface ThemeElement {
  id: string          // selector css, contoh: '#toolbar'
  label: string
  type: ElementType
  screen: ScreenId
  style: ElementStyle
  removable?: boolean
  customId?: boolean  // true jika id diinput manual
  parentId?: string   // id container tempat elemen ini nested; undefined = root-level
}

export interface Theme {
  name: string
  elements: ThemeElement[]
  wallpaper: string | null
  device: DeviceId
}

export interface ScreenInfo { id: ScreenId; label: string }
