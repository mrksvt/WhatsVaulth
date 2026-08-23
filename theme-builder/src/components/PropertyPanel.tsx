import * as Icons from 'lucide-react'
import type { ElementStyle } from '../types'
import { TAILWIND_COLORS, SPACING, RADIUS, SHADOWS, FONT_SIZES, FONT_WEIGHTS } from '../data'

const LUCIDE_ICONS = [
  'send', 'mic', 'camera', 'attach', 'emoji', 'phone', 'video', 'search',
  'message-circle', 'plus', 'check', 'check-check', 'chevron-left', 'chevron-right',
  'more-vertical', 'paperclip', 'image', 'file', 'smile', 'settings', 'users', 'bell',
]

interface Props {
  label: string
  style: ElementStyle
  onChange: (patch: Partial<ElementStyle>) => void
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-3">
      <div className="text-xs font-semibold text-gray-400 uppercase mb-1">{title}</div>
      {children}
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

export default function PropertyPanel({ label, style, onChange }: Props) {
  return (
    <div className="p-3 text-sm">
      <div className="text-base font-bold mb-2">{label}</div>

      {/* Warna */}
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
        <div className="flex flex-wrap gap-1">
          {RADIUS.map((r) => (
            <Btn key={r} active={style.rounded === `rounded-${r}`} onClick={() => onChange({ rounded: `rounded-${r}` })}>
              {r}
            </Btn>
          ))}
        </div>
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

      <Section title="Width / Height">
        <div className="flex flex-wrap gap-1">
          {[4, 6, 8, 10, 12, 14, 16, 20, 24, 32].map((s) => (
            <Btn key={`w${s}`} active={style.width === `w-${s}`} onClick={() => onChange({ width: `w-${s}` })}>
              w{s}
            </Btn>
          ))}
        </div>
        <div className="flex flex-wrap gap-1 mt-1">
          {[4, 6, 8, 10, 12, 14, 16, 20, 24, 32].map((s) => (
            <Btn key={`h${s}`} active={style.height === `h-${s}`} onClick={() => onChange({ height: `h-${s}` })}>
              h{s}
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
        <div className="flex flex-wrap gap-1">
          {LUCIDE_ICONS.map((name) => {
            const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())]
            return (
              <button
                key={name}
                type="button"
                onClick={() => onChange({ icon: name })}
                className={`p-1 rounded border ${style.icon === name ? 'bg-blue-500 text-white border-blue-500' : 'border-gray-300 hover:bg-gray-100'}`}
                title={name}
              >
                {Icon ? <Icon className="w-4 h-4" /> : name}
              </button>
            )
          })}
        </div>
      </Section>
    </div>
  )
}
