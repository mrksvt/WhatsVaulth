import { useEffect, useRef, useState } from 'react'
import type { Theme, ThemeElement, ScreenId, DeviceId } from './types'
import { DEFAULT_ELEMENTS, DEVICES, ID_OPTIONS, ADDABLE, TEMPLATES } from './data'
import WhatsAppMockup from './components/WhatsAppMockup'
import PropertyPanel from './components/PropertyPanel'
import ElementModal from './components/ElementModal'
import { exportThemeZip, downloadBlob, validateThemeIds } from './lib/export'
import './safelist'

export default function App() {
  const [name, setName] = useState('my-theme')
  const [elements, setElements] = useState<ThemeElement[]>(DEFAULT_ELEMENTS)
  const [screen, setScreen] = useState<ScreenId>('home')
  const [device, setDevice] = useState<DeviceId>('iphone')
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [wallpaper, setWallpaper] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [showTemplates, setShowTemplates] = useState(false)
  const [canvasHeight, setCanvasHeight] = useState(812)
  const [history, setHistory] = useState<ThemeElement[][]>([])
  const [future, setFuture] = useState<ThemeElement[][]>([])
  const lastCommitRef = useRef(0)
  const SNAP = 4

  const commitHistory = () => {
    const now = Date.now()
    if (now - lastCommitRef.current < 300) return
    lastCommitRef.current = now
    setHistory((h) => [...h.slice(-49), elements])
    setFuture([])
  }

  const undo = () => {
    setHistory((h) => {
      if (h.length === 0) return h
      const prev = h[h.length - 1]
      setFuture((f) => [...f, elements])
      setElements(prev)
      return h.slice(0, -1)
    })
  }

  const redo = () => {
    setFuture((f) => {
      if (f.length === 0) return f
      const next = f[f.length - 1]
      setHistory((h) => [...h, elements])
      setElements(next)
      return f.slice(0, -1)
    })
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey
      const tag = (document.activeElement?.tagName ?? '').toLowerCase()
      const inInput = tag === 'input' || tag === 'textarea' || tag === 'select'
      if (inInput && !mod) return

      if (mod && e.key.toLowerCase() === 'z' && e.shiftKey) { e.preventDefault(); redo() }
      else if (mod && e.key.toLowerCase() === 'z') { e.preventDefault(); undo() }
      else if (mod && e.key.toLowerCase() === 'y') { e.preventDefault(); redo() }
      else if (mod && e.key.toLowerCase() === 'd') { e.preventDefault(); handleDuplicate(selectedIds) }
      else if (e.key === 'Escape') { setSelectedIds([]) }
      else if (selectedIds.length > 0 && !inInput) {
        const step = e.shiftKey ? 10 : 1
        const dx = e.key === 'ArrowLeft' ? -step : e.key === 'ArrowRight' ? step : 0
        const dy = e.key === 'ArrowUp' ? -step : e.key === 'ArrowDown' ? step : 0
        if (dx !== 0 || dy !== 0) {
          e.preventDefault()
          const updates = elements
            .filter((el) => selectedIds.includes(el.id))
            .map((el) => ({ id: el.id, top: (el.style.top ?? 10) + dy, left: (el.style.left ?? 10) + dx }))
          handleBatchUpdate(updates)
        } else if (e.key === 'Delete' || e.key === 'Backspace') {
          e.preventDefault()
          handleDelete(selectedIds)
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const screenElements = elements.filter((e) => e.screen === screen)
  const selected = selectedIds.length > 0 ? elements.find((e) => e.id === selectedIds[0]) : undefined

  const updateStyle = (patch: Partial<ThemeElement['style']>) => {
    if (selectedIds.length === 0) return
    commitHistory()
    setElements((prev) =>
      prev.map((e) => selectedIds.includes(e.id) ? { ...e, style: { ...e.style, ...patch } } : e)
    )
  }

  const handleDelete = (ids: string[]) => {
    commitHistory()
    setElements((prev) => {
      const deleting = new Set(ids)
      return prev.map((e) => {
        if (!deleting.has(e.id) || e.parentId === undefined) return e
        const parent = prev.find((p) => p.id === e.parentId)
        if (!parent) return { ...e, parentId: undefined }
        return {
          ...e,
          parentId: undefined,
          style: { ...e.style, left: (e.style.left ?? 10) + (parent.style.left ?? 10), top: (e.style.top ?? 10) + (parent.style.top ?? 10) },
        }
      }).filter((e) => !deleting.has(e.id))
    })
    setSelectedIds([])
  }

  const handleDuplicate = (ids: string[]) => {
    commitHistory()
    setElements((prev) => {
      const next = [...prev]
      for (const id of ids) {
        const idx = next.findIndex((e) => e.id === id)
        if (idx === -1) continue
        const src = next[idx]
        const copy: ThemeElement = {
          ...src,
          id: `${src.id}-copy-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          label: `${src.label} (copy)`,
          customId: true,
        }
        next.splice(idx + 1, 0, copy)
      }
      return next
    })
  }

  const handleMove = (id: string, dx: number, dy: number) => {
    const CANVAS_WIDTH = 375
    const CANVAS_HEIGHT = canvasHeight
    const snap = (v: number) => Math.round(v / SNAP) * SNAP
    setElements((prev) =>
      prev.map((e) => {
        if (e.id !== id) return e
        const newTop = (e.style.top ?? 10) + dy
        const newLeft = (e.style.left ?? 10) + dx
        const w = e.style.width ?? 120
        const h = e.style.height ?? 40
        return {
          ...e,
          style: {
            ...e.style,
            top: snap(Math.max(0, Math.min(newTop, CANVAS_HEIGHT - h))),
            left: snap(Math.max(0, Math.min(newLeft, CANVAS_WIDTH - w))),
          },
        }
      })
    )
  }

  const handleResize = (id: string, w: number, h: number) => {
    const CANVAS_WIDTH = 375
    const CANVAS_HEIGHT = canvasHeight
    setElements((prev) =>
      prev.map((e) => {
        if (e.id !== id) return e
        const top = e.style.top ?? 10
        const left = e.style.left ?? 10
        const maxW = CANVAS_WIDTH - left
        const maxH = CANVAS_HEIGHT - top
        return {
          ...e,
          style: {
            ...e.style,
            width: Math.max(20, Math.min(w, maxW)),
            height: Math.max(20, Math.min(h, maxH)),
          },
        }
      })
    )
  }

  const handleAdd = (type: ThemeElement['type'], label: string, icon?: string) => {
    commitHistory()
    const usedIds = elements.filter((e) => e.screen === screen).map((e) => e.id)
    const options = ID_OPTIONS[screen]
    let id = options.find((o) => !usedIds.includes(o))
    if (!id) id = `#custom_${Date.now()}`
    const base: Partial<ThemeElement['style']> = { top: 20 + (screenElements.length * 15), left: 20 }
    if (type === 'circle') { base.width = 80; base.height = 80; base.rounded = 'rounded-full'; base.bg = 'bg-gray-100' }
    else if (type === 'line') { base.width = 160; base.height = 4; base.bg = 'bg-gray-500' }
    else { base.width = 120; base.height = 40; base.bg = 'bg-gray-100'; base.rounded = 'rounded-md' }
    const newEl: ThemeElement = {
      id,
      label,
      type,
      screen,
      style: base as ThemeElement['style'],
      removable: true,
      customId: !options.includes(id),
    }
    if (icon) newEl.style.icon = icon
    setElements((prev) => [...prev, newEl])
    setSelectedIds([newEl.id])
  }

  const handleInsertTemplate = (templateName: string) => {
    commitHistory()
    const template = TEMPLATES.find((t) => t.name === templateName && t.screen === screen)
    if (!template) return
    
    const usedIds = elements.map((e) => e.id)
    const newElements: ThemeElement[] = template.elements.map((el, idx) => {
      let uniqueId = el.id
      if (usedIds.includes(el.id)) {
        uniqueId = `${el.id}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
      }
      return {
        ...el,
        id: uniqueId,
        screen,
        style: {
          ...el.style,
          top: (el.style.top ?? 10) + (idx * 5),
          left: (el.style.left ?? 10) + (idx * 5),
        },
      }
    })
    
    setElements((prev) => [...prev, ...newElements])
    setSelectedIds(newElements.map((e) => e.id))
    setShowTemplates(false)
  }

  const onWallpaper = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setWallpaper(reader.result as string)
    reader.readAsDataURL(file)
  }

  const handleBatchUpdate = (updates: { id: string; top?: number; left?: number }[]) => {
    commitHistory()
    setElements((prev) =>
      prev.map((e) => {
        const u = updates.find((u) => u.id === e.id)
        if (!u) return e
        return { ...e, style: { ...e.style, ...(u.top !== undefined ? { top: u.top } : {}), ...(u.left !== undefined ? { left: u.left } : {}) } }
      })
    )
  }

  const handleSetParent = (updates: { id: string; parentId: string | undefined }[]) => {
    commitHistory()
    setElements((prev) =>
      prev.map((e) => {
        const u = updates.find((u) => u.id === e.id)
        if (!u) return e
        return { ...e, parentId: u.parentId }
      })
    )
  }

  const handleUnnest = (updates: { id: string; parentId: string | undefined }[]) => {
    commitHistory()
    setElements((prev) =>
      prev.map((e) => {
        const u = updates.find((u) => u.id === e.id)
        if (!u || e.parentId === undefined) return e
        const parent = prev.find((p) => p.id === e.parentId)
        if (!parent) return { ...e, parentId: undefined }
        const pL = parent.style.left ?? 10
        const pT = parent.style.top ?? 10
        return {
          ...e,
          parentId: undefined,
          style: { ...e.style, left: (e.style.left ?? 10) + pL, top: (e.style.top ?? 10) + pT },
        }
      })
    )
  }
  const handleSaveElement = (patch: Partial<ThemeElement>) => {
    if (!editingId) return
    setElements((prev) => prev.map((e) => (e.id === editingId ? { ...e, ...patch } : e)))
  }

  const onExport = async () => {
    const theme: Theme = { name, elements, wallpaper, device }
    const invalid = validateThemeIds(theme).filter((v) => !v.known)
    if (invalid.length > 0) {
      const ids = invalid.map((v) => v.element.id).join(', ')
      const ok = window.confirm(
        `ID berikut tidak dikenal engine (CustomView) dan mungkin tidak diterapkan:\n\n${ids}\n\nTetap export?`
      )
      if (!ok) return
    }
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
        <select
          value={device}
          onChange={(e) => setDevice(e.target.value as DeviceId)}
          className="border border-gray-300 rounded px-2 py-1 text-sm"
        >
          {DEVICES.map((d) => <option key={d.id} value={d.id}>{d.label}</option>)}
        </select>
        <div className="flex-1" />
        <button
          onClick={undo}
          disabled={history.length === 0}
          title="Undo (Ctrl+Z)"
          className="px-2 py-1 text-sm border border-gray-300 rounded disabled:opacity-40 hover:bg-gray-50"
        >
          ↩ Undo
        </button>
        <button
          onClick={redo}
          disabled={future.length === 0}
          title="Redo (Ctrl+Shift+Z)"
          className="px-2 py-1 text-sm border border-gray-300 rounded disabled:opacity-40 hover:bg-gray-50"
        >
          ↪ Redo
        </button>
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
          <div className="px-3 py-2 text-xs font-semibold text-gray-400 uppercase">Elements ({screen})</div>
          {screenElements.length === 0 && (
            <div className="px-3 py-2 text-xs text-gray-400">Kosong — tambah elemen di bawah</div>
          )}
          {screenElements.map((el) => (
            <div
              key={el.id}
              onClick={() => setSelectedIds([el.id])}
              className={`w-full text-left px-3 py-1.5 text-sm flex justify-between items-center cursor-pointer ${
                selectedIds.includes(el.id) ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'
              }`}
            >
              <span className="truncate flex-1">{el.label}</span>
              <span className="text-[9px] text-gray-400 font-mono truncate max-w-[80px]">{el.id}</span>
              <span className="text-gray-300 text-xs cursor-pointer ml-1" onClick={(e) => { e.stopPropagation(); handleDelete([el.id]) }}>✕</span>
            </div>
          ))}
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
          <div className="px-3 pt-3 pb-1 text-xs font-semibold text-gray-400 uppercase">Templates</div>
          <button
            onClick={() => setShowTemplates(true)}
            className="w-full text-left px-3 py-1.5 text-sm text-blue-700 hover:bg-blue-50"
          >
            📋 Insert Template
          </button>
        </aside>

        <main className="flex-1 flex flex-col bg-gray-200 overflow-auto">
          <div className="flex-1 flex items-center justify-center p-6">
            <WhatsAppMockup
              elements={elements}
              wallpaper={wallpaper}
              screen={screen}
              device={device}
              onScreen={setScreen}
              selectedIds={selectedIds}
              onSelect={setSelectedIds}
              onDelete={handleDelete}
              onDuplicate={handleDuplicate}
              onMove={handleMove}
              onResize={handleResize}
              onEdit={setEditingId}
              onCanvasHeight={setCanvasHeight}
              onBatchUpdate={handleBatchUpdate}
              onSetParent={handleSetParent}
              onUnnest={handleUnnest}
            />
          </div>
        </main>

        <aside className="w-72 bg-white border-l border-gray-200 overflow-y-auto">
          {selected ? (
            <PropertyPanel
              label={selected.label}
              id={selected.id}
              customId={selected.customId}
              screen={screen}
              style={selected.style}
              onChange={updateStyle}
              onIdChange={(id) => { commitHistory(); setElements((prev) => prev.map((e) => (e.id === selected.id ? { ...e, id, customId: true } : e))) }}
              parentId={selected.parentId}
              onSelectParent={() => { if (selected.parentId) setSelectedIds([selected.parentId]) }}
              elementType={selected.type}
              screenElements={screenElements}
              onUpdateElements={(updates) => {
                commitHistory()
                setElements((prev) => prev.map((e) => {
                  const cls = updates.get(e.id)
                  if (cls === undefined) return e
                  const patch: Partial<ThemeElement['style']> = { customClass: cls || undefined }
                  const wMatch = cls.match(/w-\[(\d+)px\]/)
                  const hMatch = cls.match(/h-\[(\d+)px\]/)
                  if (wMatch) patch.width = parseInt(wMatch[1], 10)
                  if (hMatch) patch.height = parseInt(hMatch[1], 10)
                  return { ...e, style: { ...e.style, ...patch } }
                }))
              }}
            />
          ) : (
            <div className="p-4 text-sm text-gray-400 text-center">Pilih elemen di mockup untuk edit</div>
          )}
        </aside>
      </div>
      {editingId && selected && (
        <ElementModal
          element={selected}
          screen={screen}
          onSave={handleSaveElement}
          onClose={() => setEditingId(null)}
        />
      )}
      {showTemplates && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setShowTemplates(false)}>
          <div className="bg-white rounded-lg shadow-xl w-96 max-h-[80vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="p-4 border-b border-gray-200 flex justify-between items-center">
              <h2 className="text-lg font-semibold text-gray-900">Insert Template</h2>
              <button onClick={() => setShowTemplates(false)} className="text-gray-400 hover:text-gray-600">✕</button>
            </div>
            <div className="p-4">
              {TEMPLATES.filter((t) => t.screen === screen).length === 0 && (
                <p className="text-sm text-gray-500">No templates for this screen</p>
              )}
              {TEMPLATES.filter((t) => t.screen === screen).map((template) => (
                <button
                  key={template.name}
                  onClick={() => handleInsertTemplate(template.name)}
                  className="w-full text-left p-3 mb-2 border border-gray-200 rounded-lg hover:bg-blue-50 hover:border-blue-300 transition-colors"
                >
                  <div className="font-medium text-gray-900">{template.name}</div>
                  <div className="text-xs text-gray-500 mt-1">{template.elements.length} elements</div>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
