import { useRef, useState } from 'react'
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
  onEdit: (id: string) => void
}

function iconFor(name?: string) {
  if (!name) return null
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  return Icon ? <Icon className="w-full h-full p-0.5" /> : null
}

function Box({ element, selected, onClick, onDelete, onDuplicate, onMove, onResize, onEdit, className = '', children }: {
  element: ThemeElement; selected: boolean; onClick: () => void
  onDelete: () => void; onDuplicate: () => void
  onMove?: (dx: number, dy: number) => void
  onResize?: (w: number, h: number) => void
  onEdit?: () => void
  className?: string; children: React.ReactNode
}) {
  const [mode, setMode] = useState<'resize' | 'move'>('resize')
  const [count, setCount] = useState(0)
  const [locked, setLocked] = useState(false)
  const dragRef = useRef<{ x: number; y: number; w: number; h: number } | null>(null)
  const lastClickRef = useRef<number>(0)
  const w = element.style.width ?? 120
  const h = element.style.height ?? 40

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onClick()
    if (locked) return
    const now = Date.now()
    if (now - lastClickRef.current < 300) {
      setMode((m) => (m === 'resize' ? 'move' : 'resize'))
      setCount((c) => (c >= 2 ? 1 : c + 1))
    } else {
      setMode('resize')
      setCount(1)
    }
    lastClickRef.current = now
  }

  // Drag hanya untuk mode move; resize via handle kanan-bawah
  const startMove = (e: React.PointerEvent) => {
    if (mode !== 'move') return
    e.stopPropagation()
    e.preventDefault()
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  const onMoveDrag = (e: React.PointerEvent) => {
    if (!dragRef.current || mode !== 'move') return
    const dx = e.clientX - dragRef.current.x
    const dy = e.clientY - dragRef.current.y
    onMove?.(dx, dy)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
  }

  const startResize = (e: React.PointerEvent) => {
    e.stopPropagation()
    e.preventDefault()
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  const onResizeDrag = (e: React.PointerEvent) => {
    if (!dragRef.current) return
    const dw = e.clientX - dragRef.current.x
    const dh = e.clientY - dragRef.current.y
    onResize?.(Math.max(20, dragRef.current.w + dw), Math.max(20, dragRef.current.h + dh))
  }

  const stopDrag = () => {
    if (dragRef.current && mode === 'move') setLocked(true)
    dragRef.current = null
  }

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
      className={`group outline outline-2 outline-offset-1 transition-all ${
        selected ? 'outline-blue-500' : 'outline-transparent hover:outline-blue-300'
      } ${mode === 'move' ? 'cursor-move' : 'cursor-default'} ${className}`}
      onClick={handleClick}
      onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); onEdit?.() }}
      onPointerDown={startMove}
      onPointerMove={onMoveDrag}
      onPointerUp={stopDrag}
      onPointerCancel={stopDrag}
    >
      {children}

      {selected && (
        <>
          <div className="absolute -top-8 left-0 flex gap-1 z-20 bg-gray-800 rounded-md px-1 py-0.5">
            <span className="text-white text-[10px] leading-5 px-1 truncate max-w-[110px]">
              {count}x {mode === 'resize' ? '↘ resize' : '✥ move'}{locked ? ' 🔒' : ''} · {element.label}
            </span>
            <div className="w-px bg-white/20" />
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Lock/Unlock" onClick={(e) => { e.stopPropagation(); setLocked(!locked) }}><Icons.Lock className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Duplicate" onClick={(e) => { e.stopPropagation(); onDuplicate() }}><Icons.Copy className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-red-400 hover:bg-red-500/20 rounded flex items-center justify-center" title="Delete" onClick={(e) => { e.stopPropagation(); onDelete() }}><Icons.Trash2 className="w-3 h-3" /></button>
          </div>
          {mode === 'resize' && (
            <div className="absolute -bottom-2 -right-2 w-5 h-5 bg-blue-500 rounded-full cursor-nwse-resize z-20 touch-none select-none"
              onPointerDown={startResize}
              onPointerMove={onResizeDrag}
              onPointerUp={stopDrag} onPointerCancel={stopDrag}
            />
          )}
        </>
      )}
    </div>
  )
}

export default function WhatsAppMockup({ elements, wallpaper, screen, device, onScreen, selectedId, onSelect, onDelete, onDuplicate, onMove, onResize, onEdit }: Props) {
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
        <div className="relative w-full h-full bg-white overflow-hidden" style={{ pointerEvents: 'auto' }}>
          {wallpaper && <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: `url(${wallpaper})` }} />}
          {screenElements.map((elem) => (
            <Box key={elem.id} element={elem} selected={isSel(elem.id)} onClick={() => sel(elem.id)}
              onDelete={() => onDelete(elem.id)} onDuplicate={() => onDuplicate(elem.id)}
              onMove={(dx, dy) => onMove(elem.id, dx, dy)}
              onResize={(w, h) => onResize(elem.id, w, h)}
              onEdit={() => onEdit(elem.id)}
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
