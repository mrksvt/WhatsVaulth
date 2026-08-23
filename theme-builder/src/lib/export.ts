import JSZip from 'jszip'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import * as Icons from 'lucide-react'
import type { Theme, ThemeElement, ScreenId } from '../types'
import { styleToCssClass, ID_OPTIONS } from '../data'
import { getIconSvg } from './iconRegistry'

function dataUrlToBlob(dataUrl: string): Blob {
  const [meta, b64] = dataUrl.split(',')
  const mime = meta.match(/data:(.*?);/)?.[1] ?? 'image/png'
  const bin = atob(b64)
  const arr = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i)
  return new Blob([arr], { type: mime })
}

function iconToSvg(label: string): string | null {
  if (label.startsWith('<svg')) return label
  if (label.startsWith('bi-') || label.startsWith('fa-')) return getIconSvg(label)
  const key = label.charAt(0).toUpperCase() + label.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
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

function normalizeIconLabel(label: string): string {
  if (label.startsWith('<svg')) return label
  if (label.startsWith('lucide-') || label.startsWith('bi-') || label.startsWith('fa-')) return label
  return `lucide-${label}`
}

export function buildStyleCss(theme: Theme): string {
  let css = `/*\nprimary_color = #075E54\ntext_color = #111B21\nbackground_color = #ECE5DD\nbubble_right = #DCF8C6\nbubble_left = #FFFFFF\nchange_colors = true\n*/\n\n`
  const absOf = (el: ThemeElement): { top: number; left: number } => {
    let top = el.style.top ?? 10
    let left = el.style.left ?? 10
    let parent = theme.elements.find((p) => p.id === el.parentId)
    while (parent) {
      top += parent.style.top ?? 10
      left += parent.style.left ?? 10
      parent = theme.elements.find((p) => p.id === parent!.parentId)
    }
    return { top, left }
  }
  for (const el of theme.elements) {
    const cls = styleToCssClass(el.style)
    const pos = el.parentId ? absOf(el) : null
    const iconLabel = el.style.icon ? normalizeIconLabel(el.style.icon) : null
    if (!cls && !iconLabel && !pos) continue
    css += `${el.id} {\n`
    if (cls) css += `  class: "${cls}";\n`
    if (iconLabel) {
      if (iconLabel.startsWith('<svg')) {
        const file = `custom-${Math.abs(hashCode(el.id))}.svg`
        css += `  icon: assets/${file};\n`
      } else {
        css += `  icon: ${iconLabel};\n`
      }
    }
    if (pos) {
      css += `  top: ${pos.top}px;\n  left: ${pos.left}px;\n`
    }
    css += `}\n\n`
  }
  return css
}

function hashCode(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return h
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
    const label = normalizeIconLabel(icon)
    const svg = label.startsWith('<svg') ? label : iconToSvg(label)
    if (!svg) continue
    if (label.startsWith('<svg')) {
      const file = `custom-${Math.abs(hashCode(icon))}.svg`
      folder.file(`assets/${file}`, svg)
    } else if (label.startsWith('bi-')) {
      folder.file(`bi/${label.slice(3)}.svg`, svg)
    } else if (label.startsWith('fa-')) {
      folder.file(`fa/${label.slice(3)}.svg`, svg)
    } else {
      folder.file(`lucide/${label.replace('lucide-', '')}.svg`, svg)
    }
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
