import * as Icons from 'lucide-react'
import { useState } from 'react'
import type { ThemeElement, ScreenId } from '../types'

interface Props {
  elements: ThemeElement[]
  screen: ScreenId
  selectedIds: string[]
  onSelect: (id: string, isMulti: boolean) => void
  onDelete: (id: string) => void
}

function LayerRow({ element, depth, elements, selectedIds, onSelect, onDelete }: {
  element: ThemeElement
  depth: number
  elements: ThemeElement[]
  selectedIds: string[]
  onSelect: (id: string, isMulti: boolean) => void
  onDelete: (id: string) => void
}) {
  const [open, setOpen] = useState(true)
  const children = elements.filter((e) => e.parentId === element.id)
    .sort((a, b) => (a.zOrder ?? 0) - (b.zOrder ?? 0))
  const selected = selectedIds.includes(element.id)

  return (
    <>
      <div
        onClick={(e) => onSelect(element.id, e.ctrlKey || e.metaKey || e.shiftKey)}
        className={`flex items-center gap-1 py-1 px-2 cursor-pointer text-sm ${selected ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'}`}
        style={{ paddingLeft: 8 + depth * 16 }}
      >
        {children.length > 0 ? (
          <button
            className="text-gray-400 hover:text-gray-600"
            onClick={(e) => { e.stopPropagation(); setOpen(!open) }}
          >
            <Icons.ChevronRight className={`w-3.5 h-3.5 transition-transform ${open ? 'rotate-90' : ''}`} />
          </button>
        ) : (
          <span className="w-3.5" />
        )}
        {element.type === 'group' && <Icons.Layers className="w-3.5 h-3.5 text-purple-500" />}
        {element.type === 'container' && <Icons.Box className="w-3.5 h-3.5 text-blue-500" />}
        {element.type === 'circle' && <span className="w-2.5 h-2.5 rounded-full bg-blue-400 inline-block" />}
        {element.type === 'rectangle' && <span className="w-2.5 h-2.5 border border-blue-400 inline-block" />}
        <span className="truncate flex-1">{element.label || element.type}</span>
        <span className="text-[9px] text-gray-400 font-mono truncate max-w-[70px]">{element.id}</span>
        <button className="text-gray-300 hover:text-red-500 text-xs" onClick={(e) => { e.stopPropagation(); onDelete(element.id) }}>✕</button>
      </div>
      {open && children.map((child) => (
        <LayerRow key={child.id} element={child} depth={depth + 1} elements={elements} selectedIds={selectedIds} onSelect={onSelect} onDelete={onDelete} />
      ))}
    </>
  )
}

export default function LayersPanel({ elements, screen, selectedIds, onSelect, onDelete }: Props) {
  const roots = elements.filter((e) => e.screen === screen && !e.parentId)
    .sort((a, b) => (a.zOrder ?? 0) - (b.zOrder ?? 0))
  return (
    <div>
      {roots.length === 0 && <div className="px-3 py-2 text-xs text-gray-400">Kosong — tambah elemen di bawah</div>}
      {roots.map((el) => (
        <LayerRow key={el.id} element={el} depth={0} elements={elements} selectedIds={selectedIds} onSelect={onSelect} onDelete={onDelete} />
      ))}
    </div>
  )
}