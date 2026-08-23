import * as Icons from 'lucide-react'
import type { ThemeElement } from '../types'
import { styleToTailwind } from '../data'

interface Props {
  elements: ThemeElement[]
  wallpaper: string | null
}

export default function WhatsAppMockup({ elements, wallpaper }: Props) {
  const style = (id: string) => {
    const el = elements.find((e) => e.id === id)
    return styleToTailwind(el?.style ?? {})
  }
  const icon = (id: string) => {
    const el = elements.find((e) => e.id === id)
    const name = el?.style.icon
    if (!name) return null
    const Icon = (Icons as unknown as Record<string, typeof Icons.MessageCircle>)[
      name.charAt(0).toUpperCase() + name.slice(1).replace(/-(\w)/g, (_, c) => c.toUpperCase())
    ]
    return Icon ? <Icon className="w-5 h-5" /> : null
  }

  return (
    <div className="relative w-[380px] h-[720px] rounded-3xl overflow-hidden border border-gray-300 shadow-2xl bg-white flex flex-col">
      {/* Wallpaper */}
      {wallpaper && (
        <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: `url(${wallpaper})` }} />
      )}

      {/* Toolbar */}
      <div id="toolbar" className={`relative flex items-center gap-2 px-2 py-3 ${style('#toolbar')}`}>
        <div className="w-8 h-8 rounded-full bg-gray-400/50" />
        <div className="flex-1">
          <div id="toolbar-text" className={style('#toolbar TextView')}>WhatsApp</div>
          <div className="text-[10px] text-white/70">online</div>
        </div>
        <div className="flex gap-2 text-white">
          <span>🔍</span>
          <span>⋮</span>
        </div>
      </div>

      {/* Search */}
      <div id="search" className={`relative flex items-center gap-2 ${style('#search_bar_inner_layout')}`}>
        <span className="text-gray-400 text-sm">🔍</span>
        <span className="text-gray-400 text-sm">Search</span>
      </div>

      {/* Chat list */}
      <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
        {['Aisyah', 'Rizki', 'Budi', 'Sari'].map((name, i) => (
          <div key={name} id={`row-${i}`} className={`flex items-center gap-2 ${style('#conversations_row_content')}`}>
            <div className="w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center text-gray-600">
              {name[0]}
            </div>
            <div className="flex-1">
              <div className={style('#conversations_row_contact_name')}>{name}</div>
              <div className={style('#single_msg_tv')}>Pesan terakhir {i + 1}</div>
            </div>
            <div className={style('#conversations_row_date')}>10:{String(i + 1).padStart(2, '0')}</div>
          </div>
        ))}
      </div>

      {/* Bottom nav */}
      <div id="bottom-nav" className={`relative flex justify-around items-center ${style('#bottom_nav')}`}>
        {['chat', 'group', 'status', 'call'].map((t) => (
          <div key={t} className="flex flex-col items-center text-gray-500">
            <div className="w-5 h-5 rounded bg-gray-300/70" />
            <span className="text-[8px]">{t}</span>
          </div>
        ))}
      </div>

      {/* FAB */}
      <div id="fab" className={`absolute right-4 bottom-24 w-12 h-12 flex items-center justify-center ${style('#fab')}`}>
        {icon('#fab') ?? <span className="text-white text-xl">+</span>}
      </div>
    </div>
  )
}
