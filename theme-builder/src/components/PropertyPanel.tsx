import { useState } from 'react'
import type { ElementStyle } from '../types'
import { TAILWIND_COLORS, SPACING, RADIUS, SHADOWS, FONT_SIZES, FONT_WEIGHTS, ID_OPTIONS, styleToTailwind } from '../data'
import IconPicker from './IconPicker'

interface Props {
  label: string
  id: string
  customId?: boolean
  screen: string
  style: ElementStyle
  onChange: (patch: Partial<ElementStyle>) => void
  onIdChange: (id: string) => void
  parentId?: string
  onSelectParent?: () => void
}

function Section({ title, defaultOpen = false, children }: { title: string; defaultOpen?: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="mb-1 border-b border-gray-100 pb-1">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between py-1.5 text-xs font-semibold text-gray-400 uppercase hover:text-gray-600"
      >
        {title}
        <svg
          className={`w-3.5 h-3.5 transition-transform ${open ? 'rotate-180' : ''}`}
          viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
        >
          <path d="m6 9 6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && <div className="pb-2">{children}</div>}
    </div>
  )
}

function Btn({ active, onClick, children }: { active?: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-1.5 py-0.5 rounded text-[10px] border ${
        active ? 'bg-blue-500 text-white border-blue-500' : 'border-gray-300 text-gray-600 hover:bg-gray-100'
      }`}
    >
      {children}
    </button>
  )
}

export default function PropertyPanel({ label, id, customId, screen, style, onChange, onIdChange, parentId, onSelectParent }: Props) {
  const hasCustomCorner = !!(style.cornerRadius && Object.keys(style.cornerRadius).length > 0)
  const [customCorner, setCustomCorner] = useState(hasCustomCorner)
  const [showIconPicker, setShowIconPicker] = useState(false)

  const cornerSet = (corner: 'tl' | 'tr' | 'bl' | 'br', v: string) => {
    const cr = { ...(style.cornerRadius ?? {}), [corner]: v }
    onChange({ cornerRadius: cr })
  }

  const renderCornerControls = () => (
    <div className="grid grid-cols-2 gap-1 mt-1">
      {(['tl', 'tr', 'bl', 'br'] as const).map((c) => (
        <div key={c} className="flex items-center gap-1">
          <span className="text-[9px] text-gray-400 w-5">{c.toUpperCase()}</span>
          <select
            value={style.cornerRadius?.[c] ?? 'md'}
            onChange={(e) => cornerSet(c, e.target.value)}
            className="flex-1 border border-gray-300 rounded px-1 py-0.5 text-[10px]"
          >
            {RADIUS.map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
        </div>
      ))}
    </div>
  )

  return (
    <div className="p-3 text-sm">
      <div className="text-base font-bold mb-2">{label}</div>
      {parentId && (
        <div className="mb-2 text-[10px] text-gray-500 flex items-center gap-1">
          Inside: <span className="font-mono text-gray-700">{parentId}</span>
          <button onClick={onSelectParent} className="text-blue-500 hover:underline text-[10px]">Select parent</button>
        </div>
      )}

      <Section title="Element ID (CSS selector)">
        <select
          value={customId ? '__custom__' : id}
          onChange={(e) => {
            if (e.target.value === '__custom__') return
            onIdChange(e.target.value)
          }}
          className="w-full border border-gray-300 rounded px-1.5 py-1 text-xs mb-1"
        >
          <option value="__custom__">{customId ? id : '— custom —'}</option>
          {ID_OPTIONS[screen as keyof typeof ID_OPTIONS]?.map((o) => (
            <option key={o} value={o}>{o}</option>
          ))}
        </select>
        <input
          value={id}
          onChange={(e) => onIdChange(e.target.value)}
          className="w-full border border-gray-300 rounded px-1.5 py-1 text-xs font-mono"
          placeholder="#custom_id"
        />
      </Section>

      <Section title="Size (px)">
        <div className="flex gap-2 items-center">
          <label className="text-xs text-gray-500">W</label>
          <input type="number" value={style.width ?? 120} onChange={(e) => onChange({ width: Number(e.target.value) || 0 })}
            className="w-16 border border-gray-300 rounded px-1 py-0.5 text-xs" />
          <label className="text-xs text-gray-500">H</label>
          <input type="number" value={style.height ?? 40} onChange={(e) => onChange({ height: Number(e.target.value) || 0 })}
            className="w-16 border border-gray-300 rounded px-1 py-0.5 text-xs" />
        </div>
      </Section>

      <Section title="Background">
        <div className="flex flex-wrap gap-1">
          {TAILWIND_COLORS.map((c) =>
            c.shades.length === 0 ? (
              <Btn key={c.name} active={style.bg === `bg-${c.name}`} onClick={() => onChange({ bg: `bg-${c.name}` })}>
                {c.name}
              </Btn>
            ) : (
              c.shades.filter((s) => s === 50 || s === 500 || s === 900).map((s) => (
                <Btn key={`${c.name}-${s}`} active={style.bg === `bg-${c.name}-${s}`} onClick={() => onChange({ bg: `bg-${c.name}-${s}` })}>
                  {c.name}-{s}
                </Btn>
              ))
            )
          )}
          <input
            type="color"
            value={style.bg?.match(/#[0-9a-fA-F]{6}/)?.[0] ?? '#000000'}
            onChange={(e) => onChange({ bg: `bg-[${e.target.value}]` })}
            className="w-6 h-6"
            title="Custom hex"
          />
        </div>
      </Section>

      <Section title="Text Color">
        <div className="flex flex-wrap gap-1">
          {['white', 'black', 'gray-500', 'gray-900', 'blue-500', 'green-600', 'red-500'].map((c) => (
            <Btn key={c} active={style.textColor === `text-${c}`} onClick={() => onChange({ textColor: `text-${c}` })}>
              {c}
            </Btn>
          ))}
          <input
            type="color"
            value={style.textColor?.match(/#[0-9a-fA-F]{6}/)?.[0] ?? '#000000'}
            onChange={(e) => onChange({ textColor: `text-[${e.target.value}]` })}
            className="w-6 h-6"
          />
        </div>
      </Section>

      <Section title="Radius">
        {customCorner ? (
          renderCornerControls()
        ) : (
          <div className="flex flex-wrap gap-1">
            {RADIUS.map((r) => (
              <Btn key={r} active={style.rounded === `rounded-${r}`} onClick={() => onChange({ rounded: `rounded-${r}` })}>
                {r}
              </Btn>
            ))}
          </div>
        )}
        <div className="mt-1">
          <label className="flex items-center gap-1 text-[10px] text-gray-500">
            <input
              type="checkbox"
              checked={customCorner}
              onChange={(e) => {
                setCustomCorner(e.target.checked)
                if (!e.target.checked) onChange({ cornerRadius: undefined })
              }}
            />
            Custom per corner
          </label>
        </div>
      </Section>

      <Section title="Border">
        <div className="flex flex-wrap gap-1">
          {['border-0', 'border-1', 'border-2', 'border-4', 'border-8'].map((b) => (
            <Btn key={b} active={style.borderWidth === b} onClick={() => onChange({ borderWidth: b === 'border-0' ? undefined : b })}>
              {b.replace('border-', '')}
            </Btn>
          ))}
        </div>
        <div className="flex flex-wrap gap-1 mt-1 items-center">
          {['border-gray-300', 'border-gray-500', 'border-gray-900', 'border-blue-500', 'border-red-500', 'border-white', 'border-black'].map((c) => (
            <Btn key={c} active={style.borderColor === c} onClick={() => onChange({ borderColor: c })}>
              {c.replace('border-', '')}
            </Btn>
          ))}
          <input
            type="color"
            value={style.borderColor?.match(/#[0-9a-fA-F]{6}/)?.[0] ?? '#000000'}
            onChange={(e) => onChange({ borderColor: `border-[${e.target.value}]` })}
            className="w-6 h-6"
            title="Custom border color"
          />
        </div>
        <div className="flex flex-wrap gap-1 mt-1">
          {['border-solid', 'border-dashed', 'border-dotted'].map((s) => (
            <Btn key={s} active={style.borderStyle === s} onClick={() => onChange({ borderStyle: s })}>
              {s.replace('border-', '')}
            </Btn>
          ))}
        </div>
      </Section>

      <Section title="Rotation">
        <input
          type="range"
          min={0}
          max={360}
          value={parseInt(style.rotate?.match(/(\d+)/)?.[1] ?? '0')}
          onChange={(e) => {
            const v = Number(e.target.value)
            onChange({ rotate: v === 0 ? undefined : `rotate-[${v}deg]` })
          }}
          className="w-full"
        />
        <div className="text-xs text-gray-500 text-center">{parseInt(style.rotate?.match(/(\d+)/)?.[1] ?? '0')}°</div>
      </Section>

      <Section title="Shadow">
        <div className="flex flex-wrap gap-1">
          {SHADOWS.map((s) => (
            <Btn key={s} active={style.shadow === `shadow-${s}`} onClick={() => onChange({ shadow: `shadow-${s}` })}>
              {s}
            </Btn>
          ))}
        </div>
      </Section>

      <Section title="Padding">
        <div className="flex flex-wrap gap-1">
          {SPACING.filter((s) => Number.isInteger(s)).slice(0, 14).map((s) => (
            <Btn key={s} active={style.padding === `p-${s}`} onClick={() => onChange({ padding: `p-${s}` })}>
              {s}
            </Btn>
          ))}
        </div>
      </Section>


      <Section title="Font Size / Weight">
        <div className="flex flex-wrap gap-1">
          {FONT_SIZES.map((s) => (
            <Btn key={s} active={style.fontSize === `text-${s}`} onClick={() => onChange({ fontSize: `text-${s}` })}>
              {s}
            </Btn>
          ))}
        </div>
        <div className="flex flex-wrap gap-1 mt-1">
          {FONT_WEIGHTS.map((w) => (
            <Btn key={w} active={style.fontWeight === w} onClick={() => onChange({ fontWeight: w })}>
              {w.replace('font-', '')}
            </Btn>
          ))}
        </div>
      </Section>

      <Section title="Opacity">
        <div className="flex flex-wrap gap-1">
          {[0, 25, 50, 75, 100].map((o) => (
            <Btn key={o} active={style.opacity === `opacity-${o}`} onClick={() => onChange({ opacity: `opacity-${o}` })}>
              {o}%
            </Btn>
          ))}
        </div>
      </Section>

      <Section title="Icon">
        <button
          onClick={() => setShowIconPicker(true)}
          className="w-full text-left px-2 py-1.5 rounded-lg border border-gray-300 text-sm text-gray-600 hover:bg-gray-50"
        >
          {style.icon ? `Current: ${style.icon}` : 'Choose icon...'}
        </button>
        {style.icon && (
          <button
            onClick={() => onChange({ icon: undefined })}
            className="text-xs text-red-500 mt-1 hover:underline"
          >
            Clear icon
          </button>
        )}
      </Section>

      {showIconPicker && (
        <IconPicker
          value={style.icon}
          onSelect={(label) => { onChange({ icon: label }); setShowIconPicker(false) }}
          onClose={() => setShowIconPicker(false)}
        />
      )}

      <Section title="Custom Tailwind Class" defaultOpen={true}>
        <textarea
          value={styleToTailwind(style)}
          onChange={(e) => {
            const v = e.target.value
            const patch: Partial<ElementStyle> = { customClass: v || undefined }
            const wMatch = v.match(/w-\[(\d+)px\]/)
            const hMatch = v.match(/h-\[(\d+)px\]/)
            if (wMatch) patch.width = parseInt(wMatch[1], 10)
            if (hMatch) patch.height = parseInt(hMatch[1], 10)
            onChange(patch)
          }}
          className="w-full border border-gray-300 rounded px-1.5 py-1 text-xs font-mono"
          rows={2}
          placeholder="bg-[#ff0000] w-[200px] ..."
        />
        {style.customClass && (
          <button
            onClick={() => onChange({ customClass: undefined })}
            className="text-xs text-blue-500 mt-1 hover:underline"
          >
            Reset ke auto (dari panel)
          </button>
        )}
        <div className="text-[9px] text-gray-400 mt-1">
          Auto dari panel: {styleToTailwind({ ...style, customClass: undefined }) || '(kosong)'}
        </div>
      </Section>
    </div>
  )
}
