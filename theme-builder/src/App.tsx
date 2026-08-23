import { useState } from 'react'
import type { Theme, ThemeElement } from './types'
import { DEFAULT_ELEMENTS } from './data'
import WhatsAppMockup from './components/WhatsAppMockup'
import PropertyPanel from './components/PropertyPanel'
import { exportThemeZip, downloadBlob } from './lib/export'

export default function App() {
  const [name, setName] = useState('my-theme')
  const [elements, setElements] = useState<ThemeElement[]>(DEFAULT_ELEMENTS)
  const [selectedId, setSelectedId] = useState<string>('#toolbar')
  const [wallpaper, setWallpaper] = useState<string | null>(null)

  const selected = elements.find((e) => e.id === selectedId)

  const updateStyle = (patch: Partial<ThemeElement['style']>) => {
    setElements((prev) =>
      prev.map((e) => (e.id === selectedId ? { ...e, style: { ...e.style, ...patch } } : e))
    )
  }

  const onWallpaper = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setWallpaper(reader.result as string)
    reader.readAsDataURL(file)
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
          <div className="px-3 py-2 text-xs font-semibold text-gray-400 uppercase">Elements</div>
          {elements.map((el) => (
            <button
              key={el.id}
              onClick={() => setSelectedId(el.id)}
              className={`w-full text-left px-3 py-1.5 text-sm ${
                el.id === selectedId ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'
              }`}
            >
              {el.label}
            </button>
          ))}
        </aside>

        <main className="flex-1 flex items-center justify-center bg-gray-200 overflow-auto p-6">
          <div className="scale-[0.9]">
            <WhatsAppMockup elements={elements} wallpaper={wallpaper} />
          </div>
        </main>

        <aside className="w-72 bg-white border-l border-gray-200 overflow-y-auto">
          {selected && (
            <PropertyPanel label={selected.label} style={selected.style} onChange={updateStyle} />
          )}
        </aside>
      </div>
    </div>
  )
}
