import { useState } from 'react'
import type { Theme, ThemeElement, ScreenId, DeviceId } from './types'
import { DEFAULT_ELEMENTS, DEVICES, ID_OPTIONS, ADDABLE } from './data'
import WhatsAppMockup from './components/WhatsAppMockup'
import PropertyPanel from './components/PropertyPanel'
import { exportThemeZip, downloadBlob } from './lib/export'
import './safelist'

export default function App() {
  const [name, setName] = useState('my-theme')
  const [elements, setElements] = useState<ThemeElement[]>(DEFAULT_ELEMENTS)
  const [screen, setScreen] = useState<ScreenId>('home')
  const [device, setDevice] = useState<DeviceId>('iphone')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [wallpaper, setWallpaper] = useState<string | null>(null)

  const screenElements = elements.filter((e) => e.screen === screen)
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
        id: `${src.id}-copy-${Date.now()}`,
        label: `${src.label} (copy)`,
        customId: true,
      }
      const next = [...prev]
      next.splice(idx + 1, 0, copy)
      return next
    })
  }

  const handleMove = (id: string, dx: number, dy: number) => {
    setElements((prev) =>
      prev.map((e) => {
        if (e.id !== id) return e
        return { ...e, style: { ...e.style, top: (e.style.top ?? 10) + dy, left: (e.style.left ?? 10) + dx } }
      })
    )
  }

  const handleResize = (id: string, w: number, h: number) => {
    setElements((prev) =>
      prev.map((e) => (e.id === id ? { ...e, style: { ...e.style, width: w, height: h } } : e))
    )
  }

  const handleAdd = (type: ThemeElement['type'], label: string, icon?: string) => {
    const usedIds = elements.filter((e) => e.screen === screen).map((e) => e.id)
    const options = ID_OPTIONS[screen]
    let id = options.find((o) => !usedIds.includes(o))
    if (!id) id = `#custom_${Date.now()}`
    const newEl: ThemeElement = {
      id,
      label,
      type,
      screen,
      style: { width: 120, height: 40, top: 20 + (screenElements.length * 15), left: 20, bg: 'bg-gray-100', rounded: 'rounded-md' },
      removable: true,
      customId: !options.includes(id),
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

  const onExport = async () => {
    const theme: Theme = { name, elements, wallpaper, device }
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
              onClick={() => setSelectedId(el.id)}
              className={`w-full text-left px-3 py-1.5 text-sm flex justify-between items-center cursor-pointer ${
                el.id === selectedId ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'
              }`}
            >
              <span className="truncate flex-1">{el.label}</span>
              <span className="text-[9px] text-gray-400 font-mono truncate max-w-[80px]">{el.id}</span>
              <span className="text-gray-300 text-xs cursor-pointer ml-1" onClick={(e) => { e.stopPropagation(); handleDelete(el.id) }}>✕</span>
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
        </aside>

        <main className="flex-1 flex flex-col bg-gray-200 overflow-auto">
          <div className="flex-1 flex items-center justify-center p-6">
            <WhatsAppMockup
              elements={elements}
              wallpaper={wallpaper}
              screen={screen}
              device={device}
              onScreen={setScreen}
              selectedId={selectedId}
              onSelect={setSelectedId}
              onDelete={handleDelete}
              onDuplicate={handleDuplicate}
              onMove={handleMove}
              onResize={handleResize}
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
              onIdChange={(id) => setElements((prev) => prev.map((e) => (e.id === selected.id ? { ...e, id, customId: true } : e)))}
            />
          ) : (
            <div className="p-4 text-sm text-gray-400 text-center">Pilih elemen di mockup untuk edit</div>
          )}
        </aside>
      </div>
    </div>
  )
}
