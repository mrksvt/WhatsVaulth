import { useEffect, useRef, useState } from 'react'
import * as Icons from 'lucide-react'
import { IPhoneMockup, AndroidMockup, IPadMockup } from 'react-device-mockup'
import type { ThemeElement, DeviceId, ScreenId } from '../types'
import { styleToTailwind, SCREENS, isContainerType, SHAPE_CLIP } from '../data'
import type { AnchorPosition } from '../types'
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
  onReorder?: (id: string, dir: -1 | 1) => void
  onReorderZ?: (id: string, mode: 'front' | 'forward' | 'backward' | 'back') => void
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

function Box({ element, selected, selectedIds, siblings, onClick, onDelete, onDuplicate, onMove, onResize, onEdit, onGuides, onDrop, onUnnest, onReorder, onReorderZ, onDropHover, isStackedChild = false, isAnchored = false, className = '', children }: {
  element: ThemeElement; selected: boolean; selectedIds: string[]; siblings: ThemeElement[]; onClick: () => void
  onDelete: (ids: string[]) => void; onDuplicate: (ids: string[]) => void
  onMove?: (id: string, dx: number, dy: number) => void
  onResize?: (id: string, w: number, h: number) => void
  onEdit?: () => void
  onGuides?: (g: { x?: number; y?: number }[]) => void
  onDrop?: (id: string) => void
  onUnnest?: () => void
  onReorder?: (id: string, dir: -1 | 1) => void
  onReorderZ?: (id: string, mode: 'front' | 'forward' | 'backward' | 'back') => void
  onDropHover?: (id: string | null) => void
  isStackedChild?: boolean
  isAnchored?: boolean
  className?: string; children: React.ReactNode
}) {
  const [locked, setLocked] = useState(false)
  const dragRef = useRef<{ x: number; y: number; w: number; h: number } | null>(null)
  const dragStartRef = useRef<{ x: number; y: number } | null>(null)
  const resizingRef = useRef(false)
  const w = element.style.width ?? (isAnchored ? 24 : 120)
  const h = element.style.height ?? (isAnchored ? 24 : 40)

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onClick()
  }

  const startMove = (e: React.PointerEvent) => {
    e.stopPropagation()
    if (!selected || locked) return
    if (isStackedChild || isAnchored) return
    const target = e.target as HTMLElement
    if (target.closest('button')) return
    e.preventDefault()
    resizingRef.current = false
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
    dragStartRef.current = { x: e.clientX, y: e.clientY }
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
    // drop hover: cek overlap dengan shape target (container types)
    if (onDropHover) {
      const draggedRect = {
        left: snappedLeft, top: snappedTop, width: w, height: h,
        centerX: snappedLeft + w / 2, centerY: snappedTop + h / 2,
      }
      const shape = siblings.find((s) => {
        if (s.id === element.id || s.parentId !== undefined) return false
        if (s.type !== 'container' && !isContainerType(s.type)) return false
        const sL = s.style.left ?? 10
        const sT = s.style.top ?? 10
        const sW = s.style.width ?? 120
        const sH = s.style.height ?? 40
        return draggedRect.centerX >= sL && draggedRect.centerX <= sL + sW &&
               draggedRect.centerY >= sT && draggedRect.centerY <= sT + sH
      })
      onDropHover(shape?.id ?? null)
    }
    onMove?.(element.id, snappedLeft - curLeft, snappedTop - curTop)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
  }

  const startResize = (e: React.PointerEvent) => {
    e.stopPropagation()
    e.preventDefault()
    resizingRef.current = true
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    dragRef.current = { x: e.clientX, y: e.clientY, w, h }
    dragStartRef.current = null
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
    if (dragRef.current && onDrop && !resizingRef.current && dragStartRef.current) {
      const dist = Math.hypot(
        dragRef.current.x - dragStartRef.current.x,
        dragRef.current.y - dragStartRef.current.y
      )
      if (dist > 3) onDrop(element.id)
    }
    dragStartRef.current = null
    onDropHover?.(null)
    dragRef.current = null
    resizingRef.current = false
    onGuides?.([])
  }

  return (
    <div
      id={`box-${element.id}`}
      data-box
      style={{
        position: isAnchored ? 'relative' : 'absolute',
        top: isAnchored ? undefined : (element.style.top ?? 10),
        left: isAnchored ? undefined : (element.style.left ?? 10),
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
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Bring to Front" onClick={(e) => { e.stopPropagation(); onReorderZ?.(element.id, 'front') }}><Icons.ChevronsUp className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Forward" onClick={(e) => { e.stopPropagation(); onReorderZ?.(element.id, 'forward') }}><Icons.ChevronUp className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Backward" onClick={(e) => { e.stopPropagation(); onReorderZ?.(element.id, 'backward') }}><Icons.ChevronDown className="w-3 h-3" /></button>
            <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Send to Back" onClick={(e) => { e.stopPropagation(); onReorderZ?.(element.id, 'back') }}><Icons.ChevronsDown className="w-3 h-3" /></button>
            {isStackedChild && (
              <>
                <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Move up in stack" onClick={(e) => { e.stopPropagation(); onReorder?.(element.id, -1) }}><Icons.ChevronUp className="w-3 h-3" /></button>
                <button className="w-5 h-5 text-white hover:bg-white/20 rounded flex items-center justify-center" title="Move down in stack" onClick={(e) => { e.stopPropagation(); onReorder?.(element.id, 1) }}><Icons.ChevronDown className="w-3 h-3" /></button>
              </>
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

export default function WhatsAppMockup({ elements, wallpaper, screen, device, onScreen, selectedIds, onSelect, onDelete, onDuplicate, onMove, onResize, onEdit, onCanvasHeight, onBatchUpdate, onSetParent, onUnnest, onReorder, onReorderZ }: Props) {  const isSel = (id: string) => selectedIds.includes(id)
  const el = (id: string) => elements.find((e) => e.screen === screen && e.id === id)

  const [marquee, setMarquee] = useState<{ x: number; y: number; w: number; h: number } | null>(null)
  const marqueeRef = useRef<{ startX: number; startY: number } | null>(null)
  const canvasRef = useRef<HTMLDivElement>(null)
  const [guides, setGuides] = useState<{ x?: number; y?: number }[]>([])
  const [dropTarget, setDropTarget] = useState<string | null>(null)

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
  const hasChildren = (id: string) => screenElements.some((e) => e.parentId === id)

  const orderByAfterChain = (group: ThemeElement[]): ThemeElement[] => {
    if (group.length <= 1) return group
    const byId = new Map(group.map((c) => [c.id, c]))
    const result: ThemeElement[] = []
    const used = new Set<string>()
    const heads = group.filter((c) => !c.afterElementId || !byId.has(c.afterElementId))
    for (const head of heads) {
      let cur: ThemeElement | undefined = head
      while (cur && !used.has(cur.id)) {
        used.add(cur.id)
        result.push(cur)
        cur = cur.afterElementId ? byId.get(cur.afterElementId) : undefined
      }
    }
    for (const c of group) if (!used.has(c.id)) result.push(c)
    return result
  }

  const byZOrder = (a: ThemeElement, b: ThemeElement) => (a.zOrder ?? 0) - (b.zOrder ?? 0)

  const renderElement = (elem: ThemeElement, depth = 0): React.ReactNode => {
    const children = screenElements
      .filter((e) => e.parentId === elem.id)
      .sort(byZOrder)
    const isContainer = elem.type === 'container' || isContainerType(elem.type) || elem.type === 'group'

    const anchors = new Map<AnchorPosition, ThemeElement[]>()
    for (const c of children) {
      const a = c.placement ?? 'center'
      if (!anchors.has(a)) anchors.set(a, [])
      anchors.get(a)!.push(c)
    }
    const ANCHOR_STYLE: Record<AnchorPosition, React.CSSProperties> = {
      center: { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' },
      top: { top: 4, left: '50%', transform: 'translateX(-50%)' },
      bottom: { bottom: 4, left: '50%', transform: 'translateX(-50%)' },
      left: { left: 4, top: '50%', transform: 'translateY(-50%)' },
      right: { right: 4, top: '50%', transform: 'translateY(-50%)' },
      'top-left': { top: 4, left: 4 },
      'top-right': { top: 4, right: 4 },
      'bottom-left': { bottom: 4, left: 4 },
      'bottom-right': { bottom: 4, right: 4 },
    }

    return (
      <Box key={elem.id} element={elem} selected={isSel(elem.id)} selectedIds={selectedIds} siblings={elem.parentId ? children : screenElements.filter((e) => !e.parentId)} onClick={() => onSelect([elem.id])}
        onDelete={(ids) => onDelete(ids)} onDuplicate={(ids) => onDuplicate(ids)}
        onMove={(id, dx, dy) => onMove(id, dx, dy)}
        onResize={(id, w, h) => onResize(id, w, h)}
        onEdit={() => onEdit(elem.id)}
        onGuides={setGuides}
        onDropHover={setDropTarget}
        onDrop={handleDropToNest}
        onUnnest={elem.parentId ? () => onUnnest?.([{ id: elem.id, parentId: undefined }]) : undefined}
        onReorder={onReorder}
        onReorderZ={onReorderZ}
        isAnchored={!!elem.parentId}
        isStackedChild={!!elem.parentId}
      >
        {elem.type !== 'group' && (
          <div className={`w-full h-full flex items-center justify-center ${cls(elem.id)} ${elem.type === 'circle' ? 'rounded-full' : ''} ${elem.type === 'line' ? 'rounded-full' : ''}`}
            style={SHAPE_CLIP[elem.type] ? { clipPath: SHAPE_CLIP[elem.type] } : undefined}>
            {elem.type === 'icon-btn' && elem.style.icon ? iconFor(elem.style.icon) : null}
            {elem.type === 'text' && <span className={cls(elem.id)}>{elem.label}</span>}
            {elem.type === 'image' && (elem.style.fillImage
              ? <img src={elem.style.fillImage} alt={elem.label} className="w-full h-full object-cover" />
              : <Icons.Image className="w-6 h-6 text-gray-400" />)}
            {(elem.type === 'box' || elem.type === 'rectangle' || elem.type === 'circle') && isSel(elem.id) && !hasChildren(elem.id) && !elem.style.fillImage && (
              <span className="text-gray-400 text-[10px]">{elem.id}</span>
            )}
            {elem.type === 'line' && null}
          </div>
        )}
        {isContainer && (
          <div className={`absolute inset-0 ${elem.type === 'circle' ? 'rounded-full overflow-hidden' : ''}`}>
            {Array.from(anchors.entries()).map(([anchor, group]) => (
              <div
                key={anchor}
                style={{ position: 'absolute', ...ANCHOR_STYLE[anchor], display: 'flex', gap: 4, zIndex: 5 }}
              >
                {orderByAfterChain(group).map((child) => renderElement(child, depth + 1))}
              </div>
            ))}
          </div>
        )}
        {dropTarget === elem.id && (
          <>
            <div className="absolute inset-0 ring-2 ring-blue-500 ring-offset-1 bg-blue-500/10 pointer-events-none z-30" style={{ borderRadius: elem.type === 'circle' ? '9999px' : undefined }} />
            <div className="absolute left-0 right-0 top-1/2 h-px bg-blue-500 pointer-events-none z-30" />
            <div className="absolute top-0 bottom-0 left-1/2 w-px bg-blue-500 pointer-events-none z-30" />
          </>
        )}
      </Box>
    )
  }

  const cls = (id: string) => styleToTailwind(el(id)?.style ?? {})

  const handleDropToNest = (id: string) => {
    const canvasEl = canvasRef.current
    if (!canvasEl) return
    const canvasRect = canvasEl.getBoundingClientRect()
    const dragged = elements.find((e) => e.id === id)
    if (!dragged) return
    const draggedEl = document.getElementById(`box-${id}`)
    if (!draggedEl) return
    const dRect = draggedEl.getBoundingClientRect()
    const dCenterX = dRect.left - canvasRect.left + dRect.width / 2
    const dCenterY = dRect.top - canvasRect.top + dRect.height / 2
    const container = screenElements.find((e) => {
      if (e.id === id || !isContainerType(e.type)) return false
      if (e.parentId === dragged.id) return false
      const el = document.getElementById(`box-${e.id}`)
      if (!el) return false
      const r = el.getBoundingClientRect()
      const cL = r.left - canvasRect.left
      const cT = r.top - canvasRect.top
      const cW = r.width
      const cH = r.height
      return dCenterX >= cL && dCenterX <= cL + cW && dCenterY >= cT && dCenterY <= cT + cH
    })
    if (!container) return
    const cEl = document.getElementById(`box-${container.id}`)
    if (!cEl) return
    const cRect = cEl.getBoundingClientRect()
    const cL = cRect.left - canvasRect.left
    const cT = cRect.top - canvasRect.top
    const isAutoCenter = dragged.type === 'icon-btn' || dragged.type === 'text'
    onBatchUpdate?.([
      { id, top: isAutoCenter ? 0 : dRect.top - canvasRect.top - cT, left: isAutoCenter ? 0 : dRect.left - canvasRect.left - cL },
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
          {screenElements.filter((el) => !el.parentId).sort(byZOrder).map((elem) => renderElement(elem))}
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
