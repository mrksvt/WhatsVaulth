import { useState } from 'react'
import * as Icons from 'lucide-react'
import type { ThemeElement, AnchorPosition } from '../types'

interface Props {
  sourceElement: ThemeElement
  shapes: ThemeElement[]
  childrenOfShape: (shapeId: string, anchor: AnchorPosition) => ThemeElement[]
  onConfirm: (targetShapeId: string, placement: AnchorPosition, afterElementId?: string) => void
  onClose: () => void
}

const ANCHOR_GRID: { pos: AnchorPosition; row: number; col: number }[] = [
  { pos: 'top-left', row: 0, col: 0 },
  { pos: 'top', row: 0, col: 1 },
  { pos: 'top-right', row: 0, col: 2 },
  { pos: 'left', row: 1, col: 0 },
  { pos: 'center', row: 1, col: 1 },
  { pos: 'right', row: 1, col: 2 },
  { pos: 'bottom-left', row: 2, col: 0 },
  { pos: 'bottom', row: 2, col: 1 },
  { pos: 'bottom-right', row: 2, col: 2 },
]

const ANCHOR_LABEL: Record<AnchorPosition, string> = {
  center: 'Center',
  top: 'Top',
  bottom: 'Bottom',
  left: 'Left',
  right: 'Right',
  'top-left': 'Top Left',
  'top-right': 'Top Right',
  'bottom-left': 'Bottom Left',
  'bottom-right': 'Bottom Right',
}

export default function InsertIntoShapeDialog({ sourceElement, shapes, childrenOfShape, onConfirm, onClose }: Props) {
  const [step, setStep] = useState(1)
  const [targetShapeId, setTargetShapeId] = useState<string | null>(null)
  const [placement, setPlacement] = useState<AnchorPosition>('center')
  const [orderMode, setOrderMode] = useState<'standalone' | 'after'>('standalone')
  const [afterElementId, setAfterElementId] = useState<string | undefined>(undefined)

  const existingChildren = targetShapeId ? childrenOfShape(targetShapeId, placement) : []

  const confirm = () => {
    if (!targetShapeId) return
    onConfirm(targetShapeId, placement, orderMode === 'after' ? afterElementId : undefined)
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl w-[440px] max-h-[90vh] overflow-y-auto p-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex justify-between items-center mb-3">
          <h3 className="font-bold text-gray-800">
            Insert {sourceElement.label} {step > 1 && `→ ${targetShapeId}`}
          </h3>
          <button className="text-gray-400 hover:text-gray-600" onClick={onClose}><Icons.X className="w-5 h-5" /></button>
        </div>
        <div className="flex gap-1 mb-4">
          {[1, 2, 3].map((s) => (
            <div key={s} className={`h-1 flex-1 rounded ${s <= step ? 'bg-blue-500' : 'bg-gray-200'}`} />
          ))}
        </div>

        {step === 1 && (
          <div>
            <div className="text-sm font-semibold text-gray-600 mb-2">Pilih shape tujuan</div>
            {shapes.length === 0 && <p className="text-sm text-gray-400">Tidak ada shape di screen ini</p>}
            <div className="flex flex-col gap-1">
              {shapes.map((s) => (
                <button
                  key={s.id}
                  onClick={() => { setTargetShapeId(s.id); setStep(2) }}
                  className={`w-full text-left px-3 py-2 rounded-lg border flex items-center gap-2 ${targetShapeId === s.id ? 'border-blue-400 bg-blue-50' : 'border-gray-200 hover:bg-gray-50'}`}
                >
                  <span className="text-sm font-medium text-gray-800">{s.label}</span>
                  <span className="text-[10px] text-gray-400 font-mono">{s.id}</span>
                  <span className="ml-auto text-[10px] text-gray-400">{s.type}</span>
                </button>
              ))}
            </div>
            {shapes.length > 0 && <button onClick={() => setStep(2)} className="mt-3 w-full py-2 rounded-lg bg-blue-500 text-white text-sm font-semibold disabled:opacity-40" disabled={!targetShapeId}>Lanjut</button>}
          </div>
        )}

        {step === 2 && (
          <div>
            <div className="text-sm font-semibold text-gray-600 mb-2">Pilih posisi</div>
            <div className="grid grid-cols-3 gap-2 w-56 mx-auto">
              {ANCHOR_GRID.map(({ pos, row, col }) => (
                <button
                  key={pos}
                  onClick={() => { setPlacement(pos); setAfterElementId(undefined) }}
                  className={`w-14 h-12 rounded-lg border-2 flex items-center justify-center ${placement === pos ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-blue-300'}`}
                  style={{ gridRow: row + 1, gridColumn: col + 1 }}
                  title={ANCHOR_LABEL[pos]}
                >
                  <span className={`w-2 h-2 rounded-full ${placement === pos ? 'bg-blue-500' : 'bg-gray-300'}`} />
                </button>
              ))}
            </div>
            <div className="text-center text-xs text-gray-500 mt-2">{ANCHOR_LABEL[placement]}</div>
            <div className="flex gap-2 mt-4">
              <button onClick={() => setStep(1)} className="flex-1 py-2 rounded-lg border border-gray-300 text-sm text-gray-600">Back</button>
              <button
                onClick={() => { if (existingChildren.length > 0) setStep(3); else confirm() }}
                className="flex-1 py-2 rounded-lg bg-blue-500 text-white text-sm font-semibold"
              >
                {existingChildren.length > 0 ? 'Lanjut' : 'Konfirmasi'}
              </button>
            </div>
          </div>
        )}

        {step === 3 && (
          <div>
            <div className="text-sm font-semibold text-gray-600 mb-2">Urutan di anchor "{ANCHOR_LABEL[placement]}"</div>
            <label className="flex items-center gap-2 text-sm mb-2">
              <input type="radio" checked={orderMode === 'after'} onChange={() => setOrderMode('after')} />
              Taruh setelah:
              <select
                value={afterElementId ?? ''}
                onChange={(e) => setAfterElementId(e.target.value || undefined)}
                className="border border-gray-300 rounded px-1 py-0.5 text-xs"
              >
                <option value="">— pilih —</option>
                {existingChildren.map((c) => <option key={c.id} value={c.id}>{c.label} ({c.id})</option>)}
              </select>
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input type="radio" checked={orderMode === 'standalone'} onChange={() => setOrderMode('standalone')} />
              Berdiri sendiri (akhir urutan)
            </label>
            <div className="flex gap-2 mt-4">
              <button onClick={() => setStep(2)} className="flex-1 py-2 rounded-lg border border-gray-300 text-sm text-gray-600">Back</button>
              <button onClick={confirm} className="flex-1 py-2 rounded-lg bg-green-600 text-white text-sm font-semibold">Konfirmasi</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}