import { useEffect, useRef, useState } from 'react'
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
  selectedIds: string[]
  onSelect: (ids: string[]) => void
  onDelete: (ids: string[]) => void
  onDuplicate: (ids: string[]) => void
  onMove: (id: string, dx: number, dy: number) => void
  onResize: (id: string, w: number, h: number) => void
  onEdit: (id: string) => void
  onCanvasHeight?: (h: number) => void
}

function iconFor(name?: string) {
  if (!name) return null
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  return Icon ? <Icon className="w-full h-full p-0.5" /> : null
}

function Box({ element, selected, selectedIds, onClick, onDelete, onDuplicate, onMove, onResize, onEdit, className = '', children }: {
  element: ThemeElement; selected: boolean; selectedIds: string[]; onClick: () => void
  onDelete: (ids: string[]) => void; onDuplicate: (ids: string[]) => void
  onMove?: (id: string, dx: number, dy: number) => void
  onResize?: (id: string, w: number, h: number) => void
  onEdit?: () => void
  className?: string; children: React.ReactNode
}) {
  const [locked, setLocked] = useState(false)
  const dragRef = useRef<{ x: number; y: number; w: number; h: number } | null>(null)
  const resizingRef = useRef(false)
  const w = element.style.width ?? 120
  const h = element.style.height ?? 40

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onClick()
  }

  const startMove = (e: React.PointerEvent) => {
    e.stopPropagation()
    if (!selected || locked) return
    const target = e.target as HTMLElement
    if (target.closest('button')) return
    e.preventDefault()
    resizingRef.current = false
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
  }

  const onMoveDrag = (e: React.PointerEvent) => {
    if (!dragRef.current || resizingRef.current) return
    const dx = e.clientX - dragRef.current.x
    const dy = e.clientY - dragRef.current.y
    onMove?.(element.id, dx, dy)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
  }

  const startResize = (e: React.PointerEvent) => {
    e.stopPropagation()
    e.preventDefault()
    resizingRef.current = true
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
  }

  const onResizeDrag = (e: React.PointerEvent) => {
    if (!dragRef.current) return
    const dw = e.clientX - dragRef.current.x
    const dh = e.clientY - dragRef.current.y
    onResize?.(element.id, Math.max(20, dragRef.current.w + dw), Math.max(20, dragRef.current.h + dh))
  }

  const stopDrag = () => {
    dragRef.current = null
    resizingRef.current = false
  }

  return (
    <div
      id={`box-${element.id}`}
      data-box
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
      } ${selected ? 'cursor-move' : 'cursor-default'} ${className}`}
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
              {element.label}
            </span>
            <div className="w-px bg-white/20" />
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Lock/Unlock" onClick={(e) => { e.stopPropagation(); setLocked(!locked) }}><Icons.Lock className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Duplicate" onClick={(e) => { e.stopPropagation(); onDuplicate(selectedIds) }}><Icons.Copy className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-red-400 hover:bg-red-500/20 rounded flex items-center justify-center" title="Delete" onClick={(e) => { e.stopPropagation(); onDelete(selectedIds) }}><Icons.Trash2 className="w-3 h-3" /></button>
          </div>
          <div className="absolute -bottom-2 -right-2 w-5 h-5 bg-blue-500 rounded-full cursor-nwse-resize z-20 touch-none select-none"
            onPointerDown={startResize}
            onPointerMove={onResizeDrag}
            onPointerUp={stopDrag} onPointerCancel={stopDrag}
          />
        </>
      )}
    </div>
  )
}

export default function WhatsAppMockup({ elements, wallpaper, screen, device, onScreen, selectedIds, onSelect, onDelete, onDuplicate, onMove, onResize, onEdit, onCanvasHeight }: Props) {
  const isSel = (id: string) => selectedIds.includes(id)
  const el = (id: string) => elements.find((e) => e.screen === screen && e.id === id)
  const cls = (id: string) => styleToTailwind(el(id)?.style ?? {})

  const [marquee, setMarquee] = useState<{ x: number; y: number; w: number; h: number } | null>(null)
  const marqueeRef = useRef<{ startX: number; startY: number } | null>(null)
  const canvasRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!canvasRef.current || !onCanvasHeight) return
    const el = canvasRef.current
    const report = () => onCanvasHeight(el.offsetHeight)
    report()
    const ro = new ResizeObserver(report)
    ro.observe(el)
    return () => ro.disconnect()
  }, [device, screen, onCanvasHeight])

  const screenElements = elements.filter((e) => e.screen === screen)

  const handleCanvasPointerDown = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('[data-box]')) return
    e.preventDefault()
    const target = e.currentTarget as HTMLElement
    target.setPointerCapture(e.pointerId)
    const rect = target.getBoundingClientRect()
    marqueeRef.current = {
      startX: e.clientX - rect.left,
      startY: e.clientY - rect.top,
    }
    setMarquee({ x: marqueeRef.current.startX, y: marqueeRef.current.startY, w: 0, h: 0 })
    onSelect([])
  }

  const handleCanvasPointerMove = (e: React.PointerEvent) => {
    if (!marqueeRef.current) return
    const target = e.currentTarget as HTMLElement
    const rect = target.getBoundingClientRect()
    const currentX = e.clientX - rect.left
    const currentY = e.clientY - rect.top
    const x = Math.min(marqueeRef.current.startX, currentX)
    const y = Math.min(marqueeRef.current.startY, currentY)
    const w = Math.abs(currentX - marqueeRef.current.startX)
    const h = Math.abs(currentY - marqueeRef.current.startY)
    setMarquee({ x, y, w, h })
  }

  const handleCanvasPointerUp = (e: React.PointerEvent) => {
    if (!marqueeRef.current || !marquee) return
    const target = e.currentTarget as HTMLElement
    target.releasePointerCapture(e.pointerId)

    const threshold = 5
    if (marquee.w > threshold || marquee.h > threshold) {
      const selected = screenElements.filter((elem) => {
        const elemLeft = elem.style.left ?? 10
        const elemTop = elem.style.top ?? 10
        const elemW = elem.style.width ?? 120
        const elemH = elem.style.height ?? 40
        return !(elemLeft + elemW < marquee.x ||
                 elemLeft > marquee.x + marquee.w ||
                 elemTop + elemH < marquee.y ||
                 elemTop > marquee.y + marquee.h)
      }).map((e) => e.id)
      onSelect(selected)
    } else {
      onSelect([])
    }

    marqueeRef.current = null
    setMarquee(null)
  }

  const handleCanvasPointerCancel = () => {
    marqueeRef.current = null
    setMarquee(null)
  }

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

  const Device = device === 'iphone' ? IPhoneMockup : device === 'ipad' ? IPadMockup : AndroidMockup

  return (
    <div className="flex flex-col items-center">
      <Toolbar />
      <Device screenWidth={375}>
        <div
          ref={canvasRef}
          className="relative w-full h-full bg-white overflow-hidden"
          style={{ pointerEvents: 'auto' }}
          onPointerDown={handleCanvasPointerDown}
          onPointerMove={handleCanvasPointerMove}
          onPointerUp={handleCanvasPointerUp}
          onPointerCancel={handleCanvasPointerCancel}
        >
          {wallpaper && <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: `url(${wallpaper})` }} />}
          {screenElements.map((elem) => (
            <Box key={elem.id} element={elem} selected={isSel(elem.id)} selectedIds={selectedIds} onClick={() => onSelect([elem.id])}
              onDelete={(ids) => onDelete(ids)} onDuplicate={(ids) => onDuplicate(ids)}
              onMove={(id, dx, dy) => onMove(id, dx, dy)}
              onResize={(id, w, h) => onResize(id, w, h)}
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
          {marquee && (
            <div
              className="absolute border-2 border-blue-500 bg-blue-500/10 pointer-events-none"
              style={{
                left: marquee.x,
                top: marquee.y,
                width: marquee.w,
                height: marquee.h,
                zIndex: 50,
              }}
            />
          )}
        </div>
      </Device>
    </div>
  )
}
