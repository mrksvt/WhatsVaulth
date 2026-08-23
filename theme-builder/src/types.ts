export type ScreenId = 'home' | 'calls' | 'updates' | 'conversation' | 'groups' | 'communities'

export type ElementType =
  | 'toolbar' | 'toolbar-title' | 'search' | 'chat-row' | 'chat-name'
  | 'chat-message' | 'chat-time' | 'avatar' | 'fab' | 'send'
  | 'bottom-nav' | 'input' | 'input-field'
  | 'bubble-incoming' | 'bubble-outgoing'
  | 'call-row' | 'call-name' | 'call-type' | 'call-icon'
  | 'status-row' | 'status-name' | 'status-time' | 'status-ring'
  | 'conv-toolbar' | 'conv-name'
  | 'group-badge' | 'community-header'
  | 'text' | 'icon-btn'

export interface ElementStyle {
  bg?: string; textColor?: string; rounded?: string; shadow?: string
  padding?: string; margin?: string; width?: string; height?: string
  opacity?: string; fontWeight?: string; fontSize?: string; icon?: string
}

export interface ThemeElement {
  id: string; label: string; type: ElementType; screen: ScreenId
  style: ElementStyle; removable?: boolean
}

export interface Theme {
  name: string; elements: ThemeElement[]; wallpaper: string | null
}

export interface ScreenInfo { id: ScreenId; label: string }
