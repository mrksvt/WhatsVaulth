import { useMemo, useState } from 'react'
import * as Icons from 'lucide-react'
import type { IconEntry } from '../lib/iconRegistry'
import { lucideIcons, bootstrapIcons, faIcons, getBootstrapSvg, getFAPath } from '../lib/iconRegistry'

interface Props {
  value?: string
  onSelect: (label: string) => void
  onClose: () => void
}

const PAGE = 240

function LucideThumb({ name }: { name: string }) {
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  return Icon ? <Icon className="w-5 h-5" /> : null
}

function PathThumb({ label }: { label: string }) {
  const biSvg = label.startsWith('bi-') ? getBootstrapSvg(label.slice(3)) : null
  const fa = label.startsWith('fa-') ? getFAPath(label.slice(3)) : null
  if (biSvg) {
    return <div className="w-5 h-5" dangerouslySetInnerHTML={{ __html: biSvg }} />
  }
  if (fa) {
    return (
      <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${fa.width} ${fa.height}`} className="w-5 h-5">
        <path d={fa.path} />
      </svg>
    )
  }
  return null
}

function Thumb({ entry }: { entry: IconEntry }) {
  if (entry.family === 'lucide') return <LucideThumb name={entry.name} />
  return <PathThumb label={entry.label} />
}

export default function IconPicker({ value, onSelect, onClose }: Props) {
  const [tab, setTab] = useState<'lucide' | 'bootstrap' | 'fa'>('lucide')
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(PAGE)
  const [customSvg, setCustomSvg] = useState('')

  const pool: IconEntry[] = tab === 'lucide' ? lucideIcons : tab === 'bootstrap' ? bootstrapIcons : faIcons

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return pool
    return pool.filter((ic) => ic.keywords.some((k) => k.toLowerCase().includes(q)))
  }, [pool, query])

  const visible = filtered.slice(0, limit)

  const applySelect = (label: string) => { onSelect(label); onClose() }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl w-[520px] max-h-[85vh] flex flex-col" onClick={(e) => e.stopPropagation()}>
        <div className="p-4 border-b border-gray-200 flex justify-between items-center">
          <h3 className="font-bold text-gray-800">Pilih Icon</h3>
          <button className="text-gray-400 hover:text-gray-600" onClick={onClose}><Icons.X className="w-5 h-5" /></button>
        </div>

        <div className="px-4 pt-3 flex gap-2">
          {(['lucide', 'bootstrap', 'fa'] as const).map((t) => (
            <button
              key={t}
              onClick={() => { setTab(t); setLimit(PAGE) }}
              className={`px-3 py-1 rounded-lg text-xs font-semibold capitalize ${tab === t ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
            >
              {t} ({t === 'lucide' ? lucideIcons.length : t === 'bootstrap' ? bootstrapIcons.length : faIcons.length})
            </button>
          ))}
        </div>

        <div className="px-4 py-2">
          <input
            autoFocus
            value={query}
            onChange={(e) => { setQuery(e.target.value); setLimit(PAGE) }}
            placeholder="Cari icon... (mis. send, chat, phone)"
            className="w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm"
          />
        </div>

        {value && (
          <div className="px-4 pb-2 flex items-center gap-2 text-xs text-gray-500">
            <span>Selected:</span>
            {value.startsWith('<svg') ? (
              <div className="w-5 h-5" dangerouslySetInnerHTML={{ __html: value }} />
            ) : (
              <Thumb entry={{ family: tab, name: value.split('-').slice(1).join('-'), label: value, keywords: [] }} />
            )}
            <span className="font-mono truncate">{value.startsWith('<svg') ? 'custom svg' : value}</span>
          </div>
        )}

        <div className="flex-1 overflow-y-auto p-4 pt-2">
          <div className="grid grid-cols-8 gap-1">
            {visible.map((ic) => (
              <button
                key={ic.label}
                title={ic.label}
                onClick={() => applySelect(ic.label)}
                className={`p-2 rounded-lg border flex items-center justify-center h-10 ${value === ic.label ? 'bg-blue-50 border-blue-400 text-blue-600' : 'border-gray-200 text-gray-700 hover:bg-gray-100'}`}
              >
                <Thumb entry={ic} />
              </button>
            ))}
          </div>
          {filtered.length > limit && (
            <button
              onClick={() => setLimit((l) => l + PAGE)}
              className="w-full mt-3 py-2 text-xs font-semibold text-blue-600 hover:bg-blue-50 rounded-lg border border-blue-200"
            >
              Load more ({filtered.length - limit} remaining)
            </button>
          )}
          {filtered.length === 0 && <p className="text-center text-sm text-gray-400 py-6">Tidak ada icon</p>}
        </div>

        <div className="p-4 border-t border-gray-200">
          <label className="block text-xs font-semibold text-gray-500 mb-1">Insert Custom SVG (markup langsung)</label>
          <textarea
            value={customSvg}
            onChange={(e) => setCustomSvg(e.target.value)}
            rows={2}
            placeholder='<svg xmlns="..." viewBox="0 0 24 24"><path d="..."/></svg>'
            className="w-full border border-gray-300 rounded-lg px-2 py-1 text-[10px] font-mono mb-2"
          />
          <button
            onClick={() => { if (customSvg.trim()) applySelect(customSvg.trim()) }}
            disabled={!customSvg.trim()}
            className="w-full py-1.5 rounded-lg bg-green-600 text-white text-sm font-semibold disabled:opacity-40 hover:bg-green-700"
          >
            Insert SVG
          </button>
        </div>
      </div>
    </div>
  )
}