export interface ElementStyle {
  bg?: string
  textColor?: string
  rounded?: string
  shadow?: string
  padding?: string
  margin?: string
  width?: string
  height?: string
  opacity?: string
  fontWeight?: string
  fontSize?: string
  icon?: string
}

export interface ThemeElement {
  id: string
  label: string
  style: ElementStyle
}

export interface Theme {
  name: string
  elements: ThemeElement[]
  wallpaper: string | null
}
