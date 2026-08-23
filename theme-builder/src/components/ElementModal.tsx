import { useState } from 'react'
import * as Icons from 'lucide-react'
import type { ThemeElement, ScreenId } from '../types'
import { ID_OPTIONS } from '../data'
import IconPicker from './IconPicker'

interface Props {
  element: ThemeElement
  screen: ScreenId
  onSave: (patch: Partial<ThemeElement>) => void
  onClose: () => void
}

export default function ElementModal({ element, screen, onSave, onClose }: Props) {
  const [id, setId] = useState(element.id)
  const [content, setContent] = useState(element.type)
  const [icon, setIcon] = useState(element.style.icon ?? '')
  const [text, setText] = useState(element.label)
  const [bg, setBg] = useState(element.style.bg ?? '')
  const [textColor, setTextColor] = useState(element.style.textColor ?? '')
  const [rounded, setRounded] = useState(element.style.rounded ?? 'rounded-md')
  const [showIconPicker, setShowIconPicker] = useState(false)

  const save = () => {
    const style = { ...element.style }
    if (bg) style.bg = bg; else delete style.bg
    if (textColor) style.textColor = textColor; else delete style.textColor
    if (rounded) style.rounded = rounded; else delete style.rounded
    if (icon) style.icon = icon; else delete style.icon
    onSave({ id, label: text, type: content, style, customId: true })
    onClose()
  }

  const colorBtns = ['bg-white','bg-black','bg-gray-100','bg-gray-500','bg-red-500','bg-orange-500','bg-amber-500','bg-yellow-400','bg-green-500','bg-emerald-500','bg-teal-500','bg-cyan-500','bg-blue-500','bg-indigo-500','bg-violet-500','bg-purple-500','bg-pink-500','bg-rose-500']
  const textBtns = ['text-white','text-black','text-gray-500','text-gray-900','text-red-500','text-green-600','text-blue-500']

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl w-[380px] max-h-[90vh] overflow-y-auto p-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex justify-between items-center mb-3">
          <h3 className="font-bold text-gray-800">Edit Element</h3>
          <button className="text-gray-400 hover:text-gray-600" onClick={onClose}><Icons.X className="w-5 h-5" /></button>
        </div>

        <label className="block text-xs font-semibold text-gray-500 mb-1">ID (CSS selector)</label>
        <input value={id} onChange={(e) => setId(e.target.value)} className="w-full border border-gray-300 rounded px-2 py-1 text-sm font-mono mb-2" />
        <div className="flex flex-wrap gap-1 mb-3">
          {ID_OPTIONS[screen]?.map((o) => (
            <button key={o} onClick={() => setId(o)} className={`px-1.5 py-0.5 rounded text-[10px] border ${id === o ? 'bg-blue-500 text-white border-blue-500' : 'border-gray-300 text-gray-600 hover:bg-gray-100'}`}>{o}</button>
          ))}
        </div>

        <label className="block text-xs font-semibold text-gray-500 mb-1">Konten</label>
        <div className="flex gap-1 mb-2">
          {(['text', 'icon-btn', 'box', 'container', 'image'] as const).map((t) => (
            <button key={t} onClick={() => setContent(t)} className={`px-2 py-1 rounded text-xs border ${content === t ? 'bg-blue-500 text-white border-blue-500' : 'border-gray-300 text-gray-600'}`}>{t}</button>
          ))}
        </div>

        {content === 'icon-btn' && (
          <>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Icon</label>
            <button
              onClick={() => setShowIconPicker(true)}
              className="w-full text-left px-2 py-1.5 rounded-lg border border-gray-300 text-sm text-gray-600 hover:bg-gray-50 mb-2"
            >
              {icon ? `Current: ${icon}` : 'Choose icon (lucide / bootstrap / fa / custom svg)...'}
            </button>
            {showIconPicker && (
              <IconPicker
                value={icon}
                onSelect={(label) => { setIcon(label); setShowIconPicker(false) }}
                onClose={() => setShowIconPicker(false)}
              />
            )}
            {icon && (
              <button
                onClick={() => setIcon('')}
                className="text-xs text-red-500 mb-2 hover:underline"
              >
                Clear icon
              </button>
            )}
          </>
        )}

        {(content === 'text' || content === 'box' || content === 'container') && (
          <>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Teks</label>
            <input value={text} onChange={(e) => setText(e.target.value)} className="w-full border border-gray-300 rounded px-2 py-1 text-sm mb-2" />
          </>
        )}

        <label className="block text-xs font-semibold text-gray-500 mb-1">Background</label>
        <div className="flex flex-wrap gap-1 mb-2">
          {colorBtns.map((c) => (
            <button key={c} onClick={() => setBg(c)} className={`px-1.5 py-0.5 rounded text-[10px] border ${bg === c ? 'ring-2 ring-blue-500' : 'border-gray-300'}`}>{c.replace('bg-', '')}</button>
          ))}
        </div>

        <label className="block text-xs font-semibold text-gray-500 mb-1">Teks Warna</label>
        <div className="flex flex-wrap gap-1 mb-2">
          {textBtns.map((c) => (
            <button key={c} onClick={() => setTextColor(c)} className={`px-1.5 py-0.5 rounded text-[10px] border ${textColor === c ? 'ring-2 ring-blue-500' : 'border-gray-300'}`}>{c.replace('text-', '')}</button>
          ))}
        </div>

        <label className="block text-xs font-semibold text-gray-500 mb-1">Radius</label>
        <div className="flex flex-wrap gap-1 mb-3">
          {['rounded-none','rounded-sm','rounded-md','rounded-lg','rounded-xl','rounded-2xl','rounded-full'].map((r) => (
            <button key={r} onClick={() => setRounded(r)} className={`px-1.5 py-0.5 rounded text-[10px] border ${rounded === r ? 'bg-blue-500 text-white' : 'border-gray-300'}`}>{r.replace('rounded-', '')}</button>
          ))}
        </div>

        <div className="flex gap-2 justify-end">
          <button className="px-3 py-1.5 rounded-lg border border-gray-300 text-sm text-gray-600" onClick={onClose}>Batal</button>
          <button className="px-4 py-1.5 rounded-lg bg-green-600 text-white text-sm font-semibold" onClick={save}>Simpan</button>
        </div>
      </div>
    </div>
  )
}
