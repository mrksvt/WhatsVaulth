import { useState } from 'react'
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { Theme, ThemeElement, ElementType } from './types'
import { DEFAULT_ELEMENTS } from './data'
import WhatsAppMockup from './components/WhatsAppMockup'
import PropertyPanel from './components/PropertyPanel'
import { exportThemeZip, downloadBlob } from './lib/export'
import './safelist'

function SortableItem({ element, selectedId, onSelect, onDelete }: {
  element: ThemeElement; selectedId: string | null; onSelect: (id: string) => void; onDelete: (id: string) => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: element.id })
  const style = { transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 }
  return (
    <div ref={setNodeRef} style={style} {...attributes}
      className={`w-full text-left px-3 py-1.5 text-sm flex justify-between items-center ${element.id === selectedId ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'}`}
    >
      <span className="flex-1 cursor-pointer" onClick={() => onSelect(element.id)}>{element.label}</span>
      <span {...listeners} className="cursor-grab text-gray-300 text-xs mr-1">⠿</span>
      {element.removable && (
        <span className="text-gray-300 text-xs cursor-pointer" onClick={(e) => { e.stopPropagation(); onDelete(element.id) }}>✕</span>
      )}
    </div>
  )
}

const ADDABLE: { type: ElementType; label: string; icon?: string }[] = [
  { type: 'chat-row', label: 'Chat Row' },
  { type: 'search', label: 'Search Bar' },
  { type: 'fab', label: 'FAB Button', icon: 'message-circle' },
  { type: 'send', label: 'Send Button', icon: 'send' },
  { type: 'bubble-incoming', label: 'Bubble Incoming' },
  { type: 'bubble-outgoing', label: 'Bubble Outgoing' },
  { type: 'text', label: 'Text Block' },
  { type: 'icon-btn', label: 'Icon Button', icon: 'mic' },
]

export default function App() {
  const [name, setName] = useState('my-theme')
  const [elements, setElements] = useState<ThemeElement[]>(DEFAULT_ELEMENTS)
  const [selectedId, setSelectedId] = useState<string | null>('#toolbar')
  const [wallpaper, setWallpaper] = useState<string | null>(null)

  const selected = elements.find((e) => e.id === selectedId)

  const updateStyle = (patch: Partial<ThemeElement['style']>) => {
    if (!selectedId) return
    setElements((prev) =>
      prev.map((e) => (e.id === selectedId ? { ...e, style: { ...e.style, ...patch } } : e))
    )
  }

  const handleDelete = (id: string) => {
    setElements((prev) => prev.filter((e) => e.id !== id))
    setSelectedId(null)
  }

  const handleDuplicate = (id: string) => {
    setElements((prev) => {
      const idx = prev.findIndex((e) => e.id === id)
      if (idx === -1) return prev
      const src = prev[idx]
      const copy: ThemeElement = {
        ...src,
        id: `${src.id}-c${Date.now()}`,
        label: `${src.label} (copy)`,
      }
      const next = [...prev]
      next.splice(idx + 1, 0, copy)
      return next
    })
  }

  const handleAdd = (type: ElementType, label: string, icon?: string) => {
    const base = type.replace(/-/g, '_')
    const newEl: ThemeElement = {
      id: `#${base}_${Date.now()}`,
      label,
      type,
      style: { padding: 'p-2', bg: 'bg-gray-100', rounded: 'rounded-md' },
      removable: true,
    }
    if (icon) newEl.style.icon = icon
    setElements((prev) => [...prev, newEl])
    setSelectedId(newEl.id)
  }

  const onWallpaper = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setWallpaper(reader.result as string)
    reader.readAsDataURL(file)
  }

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const onDragEnd = (e: DragEndEvent) => {
    const { active, over } = e
    if (!over || active.id === over.id) return
    setElements((prev) => {
      const oldIdx = prev.findIndex((el) => el.id === active.id)
      const newIdx = prev.findIndex((el) => el.id === over.id)
      if (oldIdx === -1 || newIdx === -1) return prev
      const next = [...prev]
      const [moved] = next.splice(oldIdx, 1)
      next.splice(newIdx, 0, moved)
      return next
    })
  }

  const onExport = async () => {
    const theme: Theme = { name, elements, wallpaper }
    const blob = await exportThemeZip(theme)
    downloadBlob(blob, `${name}.zip`)
  }

  return (
    <div className="h-screen flex flex-col bg-gray-100">
      <header className="bg-white border-b border-gray-200 px-4 py-2 flex items-center gap-3">
        <h1 className="text-lg font-bold text-gray-800">WhatsVault Theme Builder</h1>
        <input
          value={name}
          onChange={(e) => setName(e.target.value.replace(/[^a-z0-9-_]/gi, '-').toLowerCase())}
          className="border border-gray-300 rounded px-2 py-1 text-sm"
          placeholder="theme-name"
        />
        <div className="flex-1" />
        <label className="text-sm text-blue-600 cursor-pointer hover:underline">
          Wallpaper
          <input type="file" accept="image/*" className="hidden" onChange={onWallpaper} />
        </label>
        <button
          onClick={onExport}
          className="bg-green-600 text-white px-4 py-1.5 rounded-lg text-sm font-semibold hover:bg-green-700"
        >
          Export ZIP
        </button>
      </header>

      <div className="flex-1 flex overflow-hidden">
        <aside className="w-56 bg-white border-r border-gray-200 overflow-y-auto">
          <div className="px-3 py-2 text-xs font-semibold text-gray-400 uppercase">Elements (drag ⠿)</div>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
            <SortableContext items={elements.map((e) => e.id)} strategy={verticalListSortingStrategy}>
              {elements.map((el) => (
                <SortableItem key={el.id} element={el} selectedId={selectedId} onSelect={setSelectedId} onDelete={handleDelete} />
              ))}
            </SortableContext>
          </DndContext>
          <div className="px-3 pt-3 pb-1 text-xs font-semibold text-gray-400 uppercase">Add Element</div>
          {ADDABLE.map((a) => (
            <button
              key={a.type + a.label}
              onClick={() => handleAdd(a.type, a.label, a.icon)}
              className="w-full text-left px-3 py-1.5 text-sm text-green-700 hover:bg-green-50"
            >
              + {a.label}
            </button>
          ))}
        </aside>

        <main className="flex-1 flex items-center justify-center bg-gray-200 overflow-auto p-6">
          <div className="scale-[0.9]">
            <WhatsAppMockup
              elements={elements}
              wallpaper={wallpaper}
              selectedId={selectedId}
              onSelect={setSelectedId}
              onDelete={handleDelete}
              onDuplicate={handleDuplicate}
            />
          </div>
        </main>

        <aside className="w-72 bg-white border-l border-gray-200 overflow-y-auto">
          {selected ? (
            <PropertyPanel label={selected.label} style={selected.style} onChange={updateStyle} />
          ) : (
            <div className="p-4 text-sm text-gray-400 text-center">Pilih elemen di mockup untuk edit</div>
          )}
        </aside>
      </div>
    </div>
  )
}
