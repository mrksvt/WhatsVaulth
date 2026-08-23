export type ElementType =
  | 'toolbar' | 'toolbar-title' | 'search' | 'chat-row'
  | 'chat-name' | 'chat-message' | 'chat-time' | 'fab' | 'send'
  | 'bottom-nav' | 'input' | 'input-field'
  | 'bubble-incoming' | 'bubble-outgoing'
  | 'text' | 'icon-btn' | 'avatar'

export interface ElementStyle {
  bg?: string; textColor?: string; rounded?: string; shadow?: string
  padding?: string; margin?: string; width?: string; height?: string
  opacity?: string; fontWeight?: string; fontSize?: string; icon?: string
}

export interface ThemeElement {
  id: string; label: string; type: ElementType
  style: ElementStyle; removable?: boolean
}

export interface Theme {
  name: string; elements: ThemeElement[]; wallpaper: string | null
}
