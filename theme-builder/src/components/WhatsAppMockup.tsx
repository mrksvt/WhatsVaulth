import { useRef } from 'react'
import * as Icons from 'lucide-react'
import { IPhoneMockup, AndroidMockup, IPadMockup } from 'react-device-mockup'
import type { ThemeElement, DeviceId, ScreenId } from '../types'
import { styleToTailwind, SCREENS } from '../data'

interface Props {
  elements: ThemeElement[]
  wallpaper: string | null
  screen: ScreenId
  device: DeviceId
  onScreen: (s: ScreenId) => void
  selectedId: string | null
  onSelect: (id: string) => void
  onDelete: (id: string) => void
  onDuplicate: (id: string) => void
  onMove: (id: string, dx: number, dy: number) => void
  onResize: (id: string, w: number, h: number) => void
}

function iconFor(name?: string) {
  if (!name) return null
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  return Icon ? <Icon className="w-full h-full p-0.5" /> : null
}

function Box({ element, selected, onClick, onDelete, onDuplicate, onMove, onResize, className = '', children }: {
  element: ThemeElement; selected: boolean; onClick: () => void
  onDelete: () => void; onDuplicate: () => void
  onMove?: (dx: number, dy: number) => void
  onResize?: (w: number, h: number) => void
  className?: string; children: React.ReactNode
}) {
  const drag = useRef<{ x: number; y: number } | null>(null)
  const resize = useRef<{ x: number; y: number; w: number; h: number } | null>(null)
  const w = element.style.width ?? 120
  const h = element.style.height ?? 40
  return (
    <div
      style={{
        position: 'absolute',
        top: element.style.top ?? 10,
        left: element.style.left ?? 10,
        width: w,
        height: h,
        zIndex: selected ? 10 : 1,
      }}
      className={`group cursor-pointer outline outline-2 outline-offset-1 transition-all ${
        selected ? 'outline-blue-500' : 'outline-transparent hover:outline-blue-300'
      } ${className}`}
      onClick={(e) => { e.stopPropagation(); onClick() }}
    >
      {children}

      {selected && (
        <>
          {/* Action toolbar */}
          <div className="absolute -top-8 left-0 flex gap-1 z-20 bg-gray-800 rounded-md px-1 py-0.5">
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Select parent" onClick={(e) => { e.stopPropagation(); onClick() }}><Icons.ArrowUp className="w-3 h-3" /></button>
            <div className="w-px bg-white/20" />
            <span className="text-white text-[10px] leading-5 px-1 truncate max-w-[100px]">{element.label}</span>
            <div className="w-px bg-white/20" />
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Duplicate" onClick={(e) => { e.stopPropagation(); onDuplicate() }}><Icons.Copy className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-red-400 hover:bg-red-500/20 rounded flex items-center justify-center" title="Delete" onClick={(e) => { e.stopPropagation(); onDelete() }}><Icons.Trash2 className="w-3 h-3" /></button>
          </div>

          {/* Move handle */}
          <div
            className="absolute -top-1 -left-1 w-3 h-3 bg-blue-500 rounded-full cursor-grab z-20"
            onPointerDown={(e) => { e.stopPropagation(); drag.current = { x: e.clientX, y: e.clientY }; (e.target as HTMLElement).setPointerCapture(e.pointerId) }}
            onPointerMove={(e) => { if (!drag.current) return; const dx = e.clientX - drag.current.x, dy = e.clientY - drag.current.y; drag.current = { x: e.clientX, y: e.clientY }; onMove?.(dx, dy) }}
            onPointerUp={() => { drag.current = null }}
          />

          {/* Resize handle (bottom-right) */}
          <div
            className="absolute -bottom-1 -right-1 w-3 h-3 bg-blue-500 rounded-full cursor-nwse-resize z-20"
            onPointerDown={(e) => { e.stopPropagation(); resize.current = { x: e.clientX, y: e.clientY, w, h }; (e.target as HTMLElement).setPointerCapture(e.pointerId) }}
            onPointerMove={(e) => { if (!resize.current) return; const dw = e.clientX - resize.current.x, dh = e.clientY - resize.current.y; onResize?.(Math.max(20, resize.current.w + dw), Math.max(20, resize.current.h + dh)) }}
            onPointerUp={() => { resize.current = null }}
          />
        </>
      )}
    </div>
  )
}

export default function WhatsAppMockup({ elements, wallpaper, screen, device, onScreen, selectedId, onSelect, onDelete, onDuplicate, onMove, onResize }: Props) {
  const sel = (id: string) => onSelect(id)
  const isSel = (id: string) => selectedId === id
  const el = (id: string) => elements.find((e) => e.screen === screen && e.id === id)
  const cls = (id: string) => styleToTailwind(el(id)?.style ?? {})

  const Toolbar = () => (
    <div className="flex items-center gap-1 px-2 py-1.5 bg-gray-100 border-b border-gray-200 text-[10px]">
      <div className="flex gap-1">
        {SCREENS.map((s) => (
          <button key={s.id} onClick={() => onScreen(s.id)}
            className={`px-2 py-0.5 rounded ${screen === s.id ? 'bg-green-600 text-white' : 'bg-white text-gray-600 border border-gray-300'}`}>
            {s.label}
          </button>
        ))}
      </div>
    </div>
  )

  const screenElements = elements.filter((e) => e.screen === screen)
  const Device = device === 'iphone' ? IPhoneMockup : device === 'ipad' ? IPadMockup : AndroidMockup

  return (
    <div className="flex flex-col items-center">
      <Toolbar />
      <Device screenWidth={375}>
        <div className="relative w-full h-full bg-white overflow-hidden">
          {wallpaper && <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: `url(${wallpaper})` }} />}
          {screenElements.map((elem) => (
            <Box key={elem.id} element={elem} selected={isSel(elem.id)} onClick={() => sel(elem.id)}
              onDelete={() => onDelete(elem.id)} onDuplicate={() => onDuplicate(elem.id)}
              onMove={(dx, dy) => onMove(elem.id, dx, dy)}
              onResize={(w, h) => onResize(elem.id, w, h)}
            >
              <div className={`w-full h-full flex items-center justify-center ${cls(elem.id)}`}>
                {elem.type === 'icon-btn' && elem.style.icon ? iconFor(elem.style.icon) : null}
                {elem.type === 'text' && <span className={cls(elem.id)}>{elem.label}</span>}
                {elem.type === 'box' && <span className="text-gray-400 text-[10px]">{elem.id}</span>}
                {elem.type === 'container' && <span className="text-gray-400 text-[10px]">{elem.id}</span>}
                {elem.type === 'image' && <Icons.Image className="w-6 h-6 text-gray-400" />}
              </div>
            </Box>
          ))}
        </div>
      </Device>
    </div>
  )
}
