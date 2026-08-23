import { useEffect, useRef, useState } from 'react'
import * as Icons from 'lucide-react'
import { IPhoneMockup, AndroidMockup, IPadMockup } from 'react-device-mockup'
import type { ThemeElement, DeviceId, ScreenId } from '../types'
import { styleToTailwind, SCREENS } from '../data'
import AlignToolbar from './AlignToolbar'
import { getBootstrapSvg, getFAPath } from '../lib/iconRegistry'

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
  onBatchUpdate?: (updates: { id: string; top?: number; left?: number }[]) => void
  onSetParent?: (updates: { id: string; parentId: string | undefined }[]) => void
  onUnnest?: (updates: { id: string; parentId: string | undefined }[]) => void
}

function iconFor(name?: string) {
  if (!name) return null
  if (name.trim().startsWith('<svg')) {
    return <div className="w-full h-full p-0.5" dangerouslySetInnerHTML={{ __html: name }} />
  }
  if (name.startsWith('bi-')) {
    const svg = getBootstrapSvg(name.slice(3))
    if (svg) {
      return <div className="w-full h-full p-0.5" dangerouslySetInnerHTML={{ __html: svg }} />
    }
    return null
  }
  if (name.startsWith('fa-')) {
    const fa = getFAPath(name.slice(3))
    if (fa) {
      return (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${fa.width} ${fa.height}`} className="w-full h-full p-0.5">
          <path d={fa.path} />
        </svg>
      )
    }
    return null
  }
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  return Icon ? <Icon className="w-full h-full p-0.5" /> : null
}

function Box({ element, selected, selectedIds, siblings, onClick, onDelete, onDuplicate, onMove, onResize, onEdit, onGuides, onDrop, onUnnest, className = '', children }: {
  element: ThemeElement; selected: boolean; selectedIds: string[]; siblings: ThemeElement[]; onClick: () => void
  onDelete: (ids: string[]) => void; onDuplicate: (ids: string[]) => void
  onMove?: (id: string, dx: number, dy: number) => void
  onResize?: (id: string, w: number, h: number) => void
  onEdit?: () => void
  onGuides?: (g: { x?: number; y?: number }[]) => void
  onDrop?: (id: string) => void
  onUnnest?: () => void
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
    const THRESHOLD = 4
    const curLeft = element.style.left ?? 10
    const curTop = element.style.top ?? 10
    const targetLeft = curLeft + (e.clientX - dragRef.current.x)
    const targetTop = curTop + (e.clientY - dragRef.current.y)
    let snappedLeft = targetLeft
    let snappedTop = targetTop

    const edgeL = targetLeft
    const edgeC = targetLeft + w / 2
    const edgeR = targetLeft + w
    const edgeT = targetTop
    const edgeM = targetTop + h / 2
    const edgeB = targetTop + h
    const targets = siblings.filter((s) => s.id !== element.id)
    let gx: number | undefined
    let gy: number | undefined
    for (const s of targets) {
      const sL = s.style.left ?? 10
      const sT = s.style.top ?? 10
      const sW = s.style.width ?? 120
      const sH = s.style.height ?? 40
      if (Math.abs(edgeL - sL) <= THRESHOLD) { snappedLeft = sL; gx = sL }
      if (Math.abs(edgeC - (sL + sW / 2)) <= THRESHOLD) { snappedLeft = sL + sW / 2 - w / 2; gx = sL + sW / 2 }
      if (Math.abs(edgeR - (sL + sW)) <= THRESHOLD) { snappedLeft = sL + sW - w; gx = sL + sW }
      if (Math.abs(edgeT - sT) <= THRESHOLD) { snappedTop = sT; gy = sT }
      if (Math.abs(edgeM - (sT + sH / 2)) <= THRESHOLD) { snappedTop = sT + sH / 2 - h / 2; gy = sT + sH / 2 }
      if (Math.abs(edgeB - (sT + sH)) <= THRESHOLD) { snappedTop = sT + sH - h; gy = sT + sH }
    }

    onGuides?.([{ x: gx }, { y: gy }].filter((g) => g.x !== undefined || g.y !== undefined))
    onMove?.(element.id, snappedLeft - curLeft, snappedTop - curTop)
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
    const isLine = element.type === 'line'
    const nh = isLine ? Math.max(2, Math.min(8, dragRef.current.h + dh)) : Math.max(20, dragRef.current.h + dh)
    onResize?.(element.id, Math.max(20, dragRef.current.w + dw), nh)
  }

  const stopDrag = () => {
    if (dragRef.current && onDrop) onDrop(element.id)
    dragRef.current = null
    resizingRef.current = false
    onGuides?.([])
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
            {element.parentId && (
              <button className="w-5 h-5 text-amber-300 hover:bg-white/20 rounded flex items-center justify-center" title="Remove from container" onClick={(e) => { e.stopPropagation(); onUnnest?.() }}><Icons.ArrowUpFromLine className="w-3 h-3" /></button>
            )}
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

export default function WhatsAppMockup({ elements, wallpaper, screen, device, onScreen, selectedIds, onSelect, onDelete, onDuplicate, onMove, onResize, onEdit, onCanvasHeight, onBatchUpdate, onSetParent, onUnnest }: Props) {
  const isSel = (id: string) => selectedIds.includes(id)
  const el = (id: string) => elements.find((e) => e.screen === screen && e.id === id)
  const cls = (id: string) => styleToTailwind(el(id)?.style ?? {})

  const [marquee, setMarquee] = useState<{ x: number; y: number; w: number; h: number } | null>(null)
  const marqueeRef = useRef<{ startX: number; startY: number } | null>(null)
  const canvasRef = useRef<HTMLDivElement>(null)
  const [guides, setGuides] = useState<{ x?: number; y?: number }[]>([])

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

  const handleDropToNest = (id: string) => {
    const dragged = elements.find((e) => e.id === id)
    if (!dragged) return
    const dL = dragged.style.left ?? 10
    const dT = dragged.style.top ?? 10
    const dW = dragged.style.width ?? 120
    const dH = dragged.style.height ?? 40
    const dCenterX = dL + dW / 2
    const dCenterY = dT + dH / 2
    const container = screenElements.find((e) => {
      if (e.id === id || e.type !== 'container') return false
      if (e.parentId !== undefined) return false
      const cL = e.style.left ?? 10
      const cT = e.style.top ?? 10
      const cW = e.style.width ?? 120
      const cH = e.style.height ?? 40
      return dCenterX >= cL && dCenterX <= cL + cW && dCenterY >= cT && dCenterY <= cT + cH
    })
    if (!container) return
    const cL = container.style.left ?? 10
    const cT = container.style.top ?? 10
    onBatchUpdate?.([
      { id, top: dT - cT, left: dL - cL },
    ])
    onSetParent?.([{ id, parentId: container.id }])
  }

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
      <div className="relative">
        <div className="absolute left-1/2 -translate-x-1/2 top-2 z-40">
          <AlignToolbar elements={elements} selectedIds={selectedIds} onBatchUpdate={onBatchUpdate ?? (() => {})} />
        </div>
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
          {screenElements.filter((el) => !el.parentId).map((elem) => (
            <Box key={elem.id} element={elem} selected={isSel(elem.id)} selectedIds={selectedIds} siblings={screenElements.filter((e) => !e.parentId)} onClick={() => onSelect([elem.id])}
              onDelete={(ids) => onDelete(ids)} onDuplicate={(ids) => onDuplicate(ids)}
              onMove={(id, dx, dy) => onMove(id, dx, dy)}
              onResize={(id, w, h) => onResize(id, w, h)}
              onEdit={() => onEdit(elem.id)}
              onGuides={setGuides}
              onDrop={handleDropToNest}
              onUnnest={elem.parentId ? () => onUnnest?.([{ id: elem.id, parentId: undefined }]) : undefined}
            >
              <div className={`w-full h-full flex items-center justify-center ${cls(elem.id)} ${elem.type === 'circle' ? 'rounded-full' : ''} ${elem.type === 'line' ? 'rounded-full' : ''}`}>
                {elem.type === 'icon-btn' && elem.style.icon ? iconFor(elem.style.icon) : null}
                {elem.type === 'text' && <span className={cls(elem.id)}>{elem.label}</span>}
                {elem.type === 'box' && <span className="text-gray-400 text-[10px]">{elem.id}</span>}
                {elem.type === 'image' && <Icons.Image className="w-6 h-6 text-gray-400" />}
                {elem.type === 'rectangle' && <span className="text-gray-400 text-[10px]">{elem.id}</span>}
                {elem.type === 'circle' && <span className="text-gray-400 text-[10px]">{elem.id}</span>}
                {elem.type === 'line' && null}
                {elem.style.customClass && isSel(elem.id) && (
                  <div className="absolute -bottom-5 left-0 text-[8px] bg-yellow-100 text-yellow-800 px-1 rounded leading-4 whitespace-nowrap max-w-[200px] truncate pointer-events-none">
                    custom: {elem.style.customClass}
                  </div>
                )}
              </div>
              {elem.type === 'container' && (
                <div className="absolute inset-0">
                  {screenElements.filter((e) => e.parentId === elem.id).map((child) => (
                    <Box key={child.id} element={child} selected={isSel(child.id)} selectedIds={selectedIds} siblings={screenElements.filter((e) => e.parentId === elem.id)} onClick={() => onSelect([child.id])}
                      onDelete={(ids) => onDelete(ids)} onDuplicate={(ids) => onDuplicate(ids)}
                      onMove={(id, dx, dy) => onMove(id, dx, dy)}
                      onResize={(id, w, h) => onResize(id, w, h)}
                      onEdit={() => onEdit(child.id)}
                      onGuides={setGuides}
                      onDrop={handleDropToNest}
                      onUnnest={() => onUnnest?.([{ id: child.id, parentId: undefined }])}
                    >
                      <div className={`w-full h-full flex items-center justify-center ${cls(child.id)} ${child.type === 'circle' ? 'rounded-full' : ''} ${child.type === 'line' ? 'rounded-full' : ''}`}>
                        {child.type === 'icon-btn' && child.style.icon ? iconFor(child.style.icon) : null}
                        {child.type === 'text' && <span className={cls(child.id)}>{child.label}</span>}
                        {child.type === 'box' && <span className="text-gray-400 text-[10px]">{child.id}</span>}
                        {child.type === 'image' && <Icons.Image className="w-6 h-6 text-gray-400" />}
                        {child.type === 'rectangle' && <span className="text-gray-400 text-[10px]">{child.id}</span>}
                        {child.type === 'circle' && <span className="text-gray-400 text-[10px]">{child.id}</span>}
                        {child.type === 'line' && null}
                        {child.style.customClass && isSel(child.id) && (
                          <div className="absolute -bottom-5 left-0 text-[8px] bg-yellow-100 text-yellow-800 px-1 rounded leading-4 whitespace-nowrap max-w-[200px] truncate pointer-events-none">
                            custom: {child.style.customClass}
                          </div>
                        )}
                      </div>
                    </Box>
                  ))}
                </div>
              )}
            </Box>
          ))}
          {guides.map((g, i) => (
            <div key={i}>
              {g.x !== undefined && <div className="absolute bg-pink-500 pointer-events-none z-40" style={{ left: g.x, top: 0, width: 1, height: '100%' }} />}
              {g.y !== undefined && <div className="absolute bg-pink-500 pointer-events-none z-40" style={{ top: g.y, left: 0, height: 1, width: '100%' }} />}
            </div>
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
    </div>
  )
}
