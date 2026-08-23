import * as Icons from 'lucide-react'
import * as faModule from '@fortawesome/free-solid-svg-icons'

export interface IconEntry {
  family: 'lucide' | 'bootstrap' | 'fa'
  name: string
  label: string
  keywords: string[]
}

const toKebab = (s: string) => s.replace(/([A-Z])/g, '-$1').toLowerCase().replace(/^-/, '')

// -- Lucide —
const LUCIDE_KEYS = Object.keys(Icons).filter((k) => k !== 'default' && k !== 'createLucideIcon' && k !== 'icons')

export const lucideIcons: IconEntry[] = LUCIDE_KEYS.map((k) => ({
  family: 'lucide' as const,
  name: toKebab(k),
  label: `lucide-${toKebab(k)}`,
  keywords: [k.toLowerCase(), toKebab(k)],
}))

// -- Bootstrap (SVG raw via import.meta.glob) —
const biRaw: Record<string, string> = import.meta.glob('/node_modules/bootstrap-icons/icons/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true,
})

const biNameToSvg = new Map<string, string>()
for (const [path, svg] of Object.entries(biRaw)) {
  const name = path.split('/').pop()!.replace(/\.svg$/, '')
  biNameToSvg.set(name, svg)
}

export const bootstrapIcons: IconEntry[] = [...biNameToSvg.keys()].map((name) => ({
  family: 'bootstrap' as const,
  name,
  label: `bi-${name}`,
  keywords: [name.replace(/-/g, ' '), name],
}))

export function getBootstrapSvg(name: string): string | null {
  return biNameToSvg.get(name) ?? null
}

// -- FontAwesome —
interface FaMeta { name: string; width: number; height: number; path: string }

const faList: FaMeta[] = Object.entries(faModule)
  .filter(([, v]) => (v as any)?.icon && Array.isArray((v as any).icon))
  .map(([key, val]) => {
    const icon = (val as any).icon as [number, number, string[], string, string]
    return {
      name: (val as any).iconName ?? toKebab(key.replace(/^fa/, '')),
      width: icon[0],
      height: icon[1],
      path: icon[4],
    }
  })

export const faIcons: IconEntry[] = faList.map((ic) => ({
  family: 'fa' as const,
  name: ic.name,
  label: `fa-${ic.name}`,
  keywords: [ic.name.replace(/-/g, ' '), ic.name],
}))

export function getFAPath(name: string): FaMeta | null {
  return faList.find((ic) => ic.name === name) ?? null
}

export function getIconSvg(label: string): string | null {
  if (label.startsWith('<svg')) return label
  if (label.startsWith('bi-')) return getBootstrapSvg(label.slice(3))
  if (label.startsWith('fa-')) {
    const fa = getFAPath(label.slice(3))
    return fa
      ? `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${fa.width} ${fa.height}" width="${fa.width}" height="${fa.height}"><path d="${fa.path}"/></svg>`
      : null
  }
  if (label.startsWith('lucide-')) {
    const key = label.replace('lucide-', '').replace(/-(\w)/g, (_, c) => c.toUpperCase())
    const first = key.charAt(0).toUpperCase() + key.slice(1)
    const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[first]
    if (!Icon) return null
    return first
  }
  return null
}