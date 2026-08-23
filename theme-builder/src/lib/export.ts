import JSZip from 'jszip'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import * as Icons from 'lucide-react'
import type { Theme, ThemeElement, ScreenId } from '../types'
import { styleToCssClass, ID_OPTIONS } from '../data'

function dataUrlToBlob(dataUrl: string): Blob {
  const [meta, b64] = dataUrl.split(',')
  const mime = meta.match(/data:(.*?);/)?.[1] ?? 'image/png'
  const bin = atob(b64)
  const arr = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i)
  return new Blob([arr], { type: mime })
}

function iconToSvg(name: string): string | null {
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  if (!Icon) return null
  return renderToStaticMarkup(createElement(Icon, { width: 24, height: 24 }))
}

export interface IdValidation {
  element: ThemeElement
  known: boolean
}

export function validateThemeIds(theme: Theme): IdValidation[] {
  return theme.elements.map((el) => {
    const known = ID_OPTIONS[el.screen as ScreenId]?.includes(el.id) ?? false
    return { element: el, known }
  })
}

export function buildStyleCss(theme: Theme): string {
  let css = `/*\nprimary_color = #075E54\ntext_color = #111B21\nbackground_color = #ECE5DD\nbubble_right = #DCF8C6\nbubble_left = #FFFFFF\nchange_colors = true\n*/\n\n`
  for (const el of theme.elements) {
    const cls = styleToCssClass(el.style)
    if (!cls && !el.style.icon) continue
    css += `${el.id} {\n`
    if (cls) css += `  class: "${cls}";\n`
    if (el.style.icon) css += `  icon: lucide-${el.style.icon};\n`
    css += `}\n\n`
  }
  return css
}

export function buildThemeJson(theme: Theme): string {
  const css = buildStyleCss(theme)
  const json = {
    changecolor: { type: 'Boolean', value: true },
    custom_filters: { type: 'Boolean', value: true },
    primary_color: { type: 'Integer', value: 0xff075e54 },
    text_color: { type: 'Integer', value: 0xff111b21 },
    background_color: { type: 'Integer', value: 0xffeceddd },
    bubble_right: { type: 'Integer', value: 0xffdcf8c6 },
    bubble_left: { type: 'Integer', value: 0xffffffff },
    custom_css: { type: 'String', value: css },
    folder_theme: { type: 'String', value: theme.name },
  }
  return JSON.stringify(json, null, 2)
}

export async function exportThemeZip(theme: Theme): Promise<Blob> {
  const zip = new JSZip()
  const folder = zip.folder(theme.name)!

  folder.file('style.css', buildStyleCss(theme))
  folder.file(`${theme.name}.json`, buildThemeJson(theme))

  if (theme.wallpaper) {
    folder.file('wall.png', dataUrlToBlob(theme.wallpaper))
  }

  const usedIcons = new Set(theme.elements.map((e) => e.style.icon).filter(Boolean) as string[])
  for (const icon of usedIcons) {
    const svg = iconToSvg(icon)
    if (svg) folder.file(`lucide/${icon}.svg`, svg)
  }

  return zip.generateAsync({ type: 'blob' })
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
