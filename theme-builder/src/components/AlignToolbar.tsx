import * as Icons from 'lucide-react'
import type { ThemeElement } from '../types'

interface Props {
  elements: ThemeElement[]
  selectedIds: string[]
  onBatchUpdate: (updates: { id: string; top?: number; left?: number }[]) => void
}

interface Rect {
  left: number
  top: number
  width: number
  height: number
}

function rectOf(e: ThemeElement): Rect {
  return {
    left: e.style.left ?? 10,
    top: e.style.top ?? 10,
    width: e.style.width ?? 120,
    height: e.style.height ?? 40,
  }
}

function IconBtn({ title, onClick, children }: { title: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      className="p-1.5 rounded hover:bg-gray-100 text-gray-600"
    >
      {children}
    </button>
  )
}

export default function AlignToolbar({ elements, selectedIds, onBatchUpdate }: Props) {
  if (selectedIds.length < 2) return null

  const selected = elements.filter((e) => selectedIds.includes(e.id))
  const rects = selected.map(rectOf)

  const minLeft = Math.min(...rects.map((r) => r.left))
  const minTop = Math.min(...rects.map((r) => r.top))
  const maxLeft = Math.max(...rects.map((r) => r.left + r.width))
  const maxTop = Math.max(...rects.map((r) => r.top + r.height))
  const bboxW = maxLeft - minLeft
  const bboxH = maxTop - minTop

  const align = (fn: (r: Rect) => { top?: number; left?: number }) => {
    onBatchUpdate(selected.map((e) => ({ id: e.id, ...fn(rectOf(e)) })))
  }

  const distributeH = () => {
    const sorted = [...selected].sort((a, b) => rectOf(a).left - rectOf(b).left)
    const first = rectOf(sorted[0])
    const last = rectOf(sorted[sorted.length - 1])
    const gap = (last.left - (first.left + first.width)) / (sorted.length - 1)
    onBatchUpdate(sorted.map((e, i) => ({
      id: e.id,
      left: first.left + first.width + gap * i,
    })))
  }

  const distributeV = () => {
    const sorted = [...selected].sort((a, b) => rectOf(a).top - rectOf(b).top)
    const first = rectOf(sorted[0])
    const last = rectOf(sorted[sorted.length - 1])
    const gap = (last.top - (first.top + first.height)) / (sorted.length - 1)
    onBatchUpdate(sorted.map((e, i) => ({
      id: e.id,
      top: first.top + first.height + gap * i,
    })))
  }

  return (
    <div className="flex items-center gap-1 bg-white rounded-lg shadow-sm border border-gray-200 px-1.5 py-1 z-40">
      <IconBtn title="Align Left" onClick={() => align((_r) => ({ left: minLeft }))}>
        <Icons.AlignStartVertical className="w-4 h-4" />
      </IconBtn>
      <IconBtn title="Align Center Horizontal" onClick={() => align((r) => ({ left: minLeft + bboxW / 2 - r.width / 2 }))}>
        <Icons.AlignCenterVertical className="w-4 h-4" />
      </IconBtn>
      <IconBtn title="Align Right" onClick={() => align((r) => ({ left: maxLeft - r.width }))}>
        <Icons.AlignEndVertical className="w-4 h-4" />
      </IconBtn>

      <div className="w-px h-5 bg-gray-200" />

      <IconBtn title="Align Top" onClick={() => align((_r) => ({ top: minTop }))}>
        <Icons.AlignStartHorizontal className="w-4 h-4" />
      </IconBtn>
      <IconBtn title="Align Middle Vertical" onClick={() => align((r) => ({ top: minTop + bboxH / 2 - r.height / 2 }))}>
        <Icons.AlignCenterHorizontal className="w-4 h-4" />
      </IconBtn>
      <IconBtn title="Align Bottom" onClick={() => align((r) => ({ top: maxTop - r.height }))}>
        <Icons.AlignEndHorizontal className="w-4 h-4" />
      </IconBtn>

      {selectedIds.length >= 3 && (
        <>
          <div className="w-px h-5 bg-gray-200" />
          <IconBtn title="Distribute Horizontal" onClick={distributeH}>
            <Icons.AlignHorizontalDistributeCenter className="w-4 h-4" />
          </IconBtn>
          <IconBtn title="Distribute Vertical" onClick={distributeV}>
            <Icons.AlignVerticalDistributeCenter className="w-4 h-4" />
          </IconBtn>
        </>
      )}
    </div>
  )
}
