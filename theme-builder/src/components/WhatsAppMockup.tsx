import * as Icons from 'lucide-react'
import type { ThemeElement } from '../types'
import { styleToTailwind } from '../data'

interface Props {
  elements: ThemeElement[]
  wallpaper: string | null
  selectedId: string | null
  onSelect: (id: string) => void
  onDelete: (id: string) => void
  onDuplicate: (id: string) => void
}

function iconFor(name?: string) {
  if (!name) return null
  const key = name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
  const Icon = (Icons as unknown as Record<string, typeof Icons.Send>)[key]
  return Icon ? <Icon className="w-5 h-5" /> : null
}

function ElementBox({
  element, selected, onClick, onDelete, onDuplicate, className = '', children,
}: {
  element: ThemeElement; selected: boolean; onClick: () => void
  onDelete: () => void; onDuplicate: () => void
  className?: string; children: React.ReactNode
}) {
  return (
    <div
      className={`relative group cursor-pointer outline outline-2 outline-offset-1 transition-all ${
        selected ? 'outline-blue-500' : 'outline-transparent hover:outline-blue-300'
      } ${className}`}
      onClick={(e) => { e.stopPropagation(); onClick() }}
    >
      {children}
      {selected && (
        <>
          <div className="absolute -top-3 -right-3 flex gap-1 z-20">
            {element.removable && (
              <button
                className="w-6 h-6 bg-red-500 text-white rounded-full flex items-center justify-center shadow"
                onClick={(e) => { e.stopPropagation(); onDelete() }}
                title="Delete"
              >
                <Icons.Trash2 className="w-3.5 h-3.5" />
              </button>
            )}
            {element.removable && (
              <button
                className="w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center shadow"
                onClick={(e) => { e.stopPropagation(); onDuplicate() }}
                title="Duplicate"
              >
                <Icons.Copy className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
          <div className="absolute -top-3 left-0 bg-blue-500 text-white text-[9px] px-1.5 py-0.5 rounded z-20">
            {element.label}
          </div>
        </>
      )}
    </div>
  )
}

export default function WhatsAppMockup({ elements, wallpaper, selectedId, onSelect, onDelete, onDuplicate }: Props) {
  const el = (id: string) => elements.find((e) => e.id === id)
  const cls = (id: string) => styleToTailwind(el(id)?.style ?? {})
  const isSel = (id: string) => selectedId === id
  const sel = (id: string) => onSelect(id)

  const search = el('#search_bar_inner_layout')
  const rows = elements.filter((e) => e.type === 'chat-row')
  const fab = el('#fab')
  const bottomNav = el('#bottom_nav')

  return (
    <div className="relative w-[380px] h-[720px] rounded-3xl overflow-hidden border border-gray-300 shadow-2xl bg-white flex flex-col">
      {wallpaper && (
        <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: `url(${wallpaper})` }} />
      )}

      {/* Toolbar */}
      {el('#toolbar') && (
        <ElementBox element={el('#toolbar')!} selected={isSel('#toolbar')} onClick={() => sel('#toolbar')} onDelete={() => onDelete('#toolbar')} onDuplicate={() => onDuplicate('#toolbar')}>
          <div className={`relative flex items-center gap-2 px-2 py-3 ${cls('#toolbar')}`}>
            <ElementBox element={el('#toolbar TextView')!} selected={isSel('#toolbar TextView')} onClick={() => sel('#toolbar TextView')} onDelete={() => onDelete('#toolbar TextView')} onDuplicate={() => onDuplicate('#toolbar TextView')} className="flex-1">
              <div className={cls('#toolbar TextView')}>WhatsApp</div>
              <div className="text-[10px] text-white/70">online</div>
            </ElementBox>
            <div className="flex gap-2 text-white"><span>🔍</span><span>⋮</span></div>
          </div>
        </ElementBox>
      )}

      {/* Search */}
      {search && (
        <ElementBox element={search} selected={isSel('#search_bar_inner_layout')} onClick={() => sel('#search_bar_inner_layout')} onDelete={() => onDelete('#search_bar_inner_layout')} onDuplicate={() => onDuplicate('#search_bar_inner_layout')} className="mx-2">
          <div className={`flex items-center gap-2 ${cls('#search_bar_inner_layout')}`}>
            <span className="text-gray-400 text-sm">🔍</span>
            <span className="text-gray-400 text-sm">Search</span>
          </div>
        </ElementBox>
      )}

      {/* Chat list */}
      <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
        {rows.map((row, i) => (
          <ElementBox key={row.id} element={row} selected={isSel(row.id)} onClick={() => sel(row.id)} onDelete={() => onDelete(row.id)} onDuplicate={() => onDuplicate(row.id)} className="mx-1">
            <div className={`flex items-center gap-2 ${cls('#conversations_row_content')}`}>
              <div className="w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center text-gray-600">{row.label.replace('Chat Row ', '')}</div>
              <div className="flex-1">
                <div className={cls('#conversations_row_contact_name')}>{row.label.replace('Chat Row ', '')}</div>
                <div className={cls('#single_msg_tv')}>Pesan terakhir {i + 1}</div>
              </div>
              <div className={cls('#conversations_row_date')}>10:{String(i + 1).padStart(2, '0')}</div>
            </div>
          </ElementBox>
        ))}
      </div>

      {/* Bottom nav */}
      {bottomNav && (
        <ElementBox element={bottomNav} selected={isSel('#bottom_nav')} onClick={() => sel('#bottom_nav')} onDelete={() => onDelete('#bottom_nav')} onDuplicate={() => onDuplicate('#bottom_nav')}>
          <div className={`flex justify-around items-center ${cls('#bottom_nav')}`}>
            {['chat', 'group', 'status', 'call'].map((t) => (
              <div key={t} className="flex flex-col items-center text-gray-500">
                <div className="w-5 h-5 rounded bg-gray-300/70" />
                <span className="text-[8px]">{t}</span>
              </div>
            ))}
          </div>
        </ElementBox>
      )}

      {/* FAB */}
      {fab && (
        <ElementBox element={fab} selected={isSel('#fab')} onClick={() => sel('#fab')} onDelete={() => onDelete('#fab')} onDuplicate={() => onDuplicate('#fab')} className="absolute right-4 bottom-24">
          <div className={`w-12 h-12 flex items-center justify-center ${cls('#fab')}`}>
            {iconFor(fab.style.icon) ?? <span className="text-white text-xl">+</span>}
          </div>
        </ElementBox>
      )}
    </div>
  )
}
