import * as Icons from 'lucide-react'
import type { ThemeElement, ScreenId } from '../types'
import { styleToTailwind, SCREENS } from '../data'

interface Props {
  elements: ThemeElement[]
  wallpaper: string | null
  screen: ScreenId
  onScreen: (s: ScreenId) => void
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

function Box({ element, selected, onClick, onDelete, onDuplicate, className = '', children }: {
  element: ThemeElement; selected: boolean; onClick: () => void
  onDelete: () => void; onDuplicate: () => void; className?: string; children: React.ReactNode
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
              <button className="w-6 h-6 bg-red-500 text-white rounded-full flex items-center justify-center shadow" onClick={(e) => { e.stopPropagation(); onDelete() }} title="Delete"><Icons.Trash2 className="w-3.5 h-3.5" /></button>
            )}
            {element.removable && (
              <button className="w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center shadow" onClick={(e) => { e.stopPropagation(); onDuplicate() }} title="Duplicate"><Icons.Copy className="w-3.5 h-3.5" /></button>
            )}
          </div>
          <div className="absolute -top-3 left-0 bg-blue-500 text-white text-[9px] px-1.5 py-0.5 rounded z-20">{element.label}</div>
        </>
      )}
    </div>
  )
}

export default function WhatsAppMockup({ elements, wallpaper, screen, onScreen, selectedId, onSelect, onDelete, onDuplicate }: Props) {
  const by = (type: string) => elements.filter((e) => e.screen === screen && e.type === type)
  const el = (id: string) => elements.find((e) => e.screen === screen && e.id === id)
  const cls = (id: string) => styleToTailwind(el(id)?.style ?? {})
  const sel = (id: string) => onSelect(id)
  const isSel = (id: string) => selectedId === id
  const names = ['Aisyah', 'Rizki', 'Budi', 'Sari', 'Dewi', 'Fajar', 'Gita', 'Hasan']

  const toolbar = by('toolbar')[0]
  const toolbarTitle = by('toolbar-title')[0]
  const search = by('search')[0]
  const rows = by('chat-row')
  const fab = by('fab')[0]
  const nav = by('bottom-nav')[0]
  const callRows = by('call-row')
  const statusRows = by('status-row')
  const statusRing = by('status-ring')[0]
  const convBubbleIn = by('bubble-incoming')[0]
  const convBubbleOut = by('bubble-outgoing')[0]
  const convInput = by('input')[0]
  const convSend = by('send')[0]
  const groupBadge = by('group-badge')[0]
  const comHeader = by('community-header')[0]

  const NavBar = () => (
    nav ? (
      <Box element={nav} selected={isSel(nav.id)} onClick={() => sel(nav.id)} onDelete={() => onDelete(nav.id)} onDuplicate={() => onDuplicate(nav.id)}>
        <div className={`flex justify-around items-center ${cls(nav.id)}`}>
          {SCREENS.slice(0, 4).map((s, i) => (
            <button key={s.id} onClick={() => onScreen(s.id)} className={`flex flex-col items-center text-[8px] ${i === SCREENS.indexOf(SCREENS.find((x) => x.id === screen)!) ? 'text-green-600' : 'text-gray-400'}`}>
              {['chat', 'phone', 'circle', 'users'][i] === 'chat' ? <Icons.MessageCircle className="w-4 h-4" /> : ['chat', 'phone', 'circle', 'users'][i] === 'phone' ? <Icons.Phone className="w-4 h-4" /> : ['chat', 'phone', 'circle', 'users'][i] === 'circle' ? <Icons.Circle className="w-4 h-4" /> : <Icons.Users className="w-4 h-4" />}
              <span>{s.label.split(' ')[0]}</span>
            </button>
          ))}
        </div>
      </Box>
    ) : null
  )

  return (
    <div className="relative w-[380px] h-[720px] rounded-3xl overflow-hidden border border-gray-300 shadow-2xl bg-white flex flex-col">
      {wallpaper && <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: `url(${wallpaper})` }} />}

      {screen === 'home' && (
        <>
          {toolbar && <Box element={toolbar} selected={isSel(toolbar.id)} onClick={() => sel(toolbar.id)} onDelete={() => onDelete(toolbar.id)} onDuplicate={() => onDuplicate(toolbar.id)}>
            <div className={`relative flex items-center gap-2 px-2 py-3 ${cls(toolbar.id)}`}>
              {toolbarTitle && <Box element={toolbarTitle} selected={isSel(toolbarTitle.id)} onClick={() => sel(toolbarTitle.id)} onDelete={() => onDelete(toolbarTitle.id)} onDuplicate={() => onDuplicate(toolbarTitle.id)} className="flex-1">
                <div className={cls(toolbarTitle.id)}>WhatsApp</div>
              </Box>}
              <div className="flex gap-2 text-white"><span>🔍</span><span>⋮</span></div>
            </div>
          </Box>}
          {search && <Box element={search} selected={isSel(search.id)} onClick={() => sel(search.id)} onDelete={() => onDelete(search.id)} onDuplicate={() => onDuplicate(search.id)} className="mx-2">
            <div className={`flex items-center gap-2 ${cls(search.id)}`}><span className="text-gray-400 text-sm">🔍</span><span className="text-gray-400 text-sm">Search</span></div>
          </Box>}
          <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
            {rows.map((row, i) => (
              <Box key={row.id} element={row} selected={isSel(row.id)} onClick={() => sel(row.id)} onDelete={() => onDelete(row.id)} onDuplicate={() => onDuplicate(row.id)} className="mx-1">
                <div className={`flex items-center gap-2 ${cls('home_row')}`}>
                  <div className="w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center text-gray-600">{names[i % names.length][0]}</div>
                  <div className="flex-1">
                    <div className={cls('home_row_name')}>{names[i % names.length]}</div>
                    <div className={cls('home_row_msg')}>Pesan terakhir {i + 1}</div>
                  </div>
                  <div className={cls('home_row_time')}>10:{String(i + 1).padStart(2, '0')}</div>
                </div>
              </Box>
            ))}
          </div>
          <NavBar />
          {fab && <Box element={fab} selected={isSel(fab.id)} onClick={() => sel(fab.id)} onDelete={() => onDelete(fab.id)} onDuplicate={() => onDuplicate(fab.id)} className="absolute right-4 bottom-24">
            <div className={`w-12 h-12 flex items-center justify-center ${cls(fab.id)}`}>{iconFor(fab.style.icon) ?? <span className="text-white text-xl">+</span>}</div>
          </Box>}
        </>
      )}

      {screen === 'calls' && (
        <>
          {toolbar && <Box element={toolbar} selected={isSel(toolbar.id)} onClick={() => sel(toolbar.id)} onDelete={() => onDelete(toolbar.id)} onDuplicate={() => onDuplicate(toolbar.id)}>
            <div className={`relative flex items-center gap-2 px-2 py-3 ${cls(toolbar.id)}`}>
              {toolbarTitle && <Box element={toolbarTitle} selected={isSel(toolbarTitle.id)} onClick={() => sel(toolbarTitle.id)} onDelete={() => onDelete(toolbarTitle.id)} onDuplicate={() => onDuplicate(toolbarTitle.id)} className="flex-1">
                <div className={cls(toolbarTitle.id)}>Panggilan</div>
              </Box>}
              <div className="flex gap-2 text-white"><span>🔍</span></div>
            </div>
          </Box>}
          <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
            {callRows.map((row, i) => (
              <Box key={row.id} element={row} selected={isSel(row.id)} onClick={() => sel(row.id)} onDelete={() => onDelete(row.id)} onDuplicate={() => onDuplicate(row.id)} className="mx-1">
                <div className={`flex items-center gap-2 ${cls('calls_row')}`}>
                  <div className="w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center text-gray-600">{names[i % names.length][0]}</div>
                  <div className="flex-1">
                    <div className={cls('calls_row_name')}>{names[i % names.length]}</div>
                    <div className={cls('calls_row_type')}>Keluar · 10:{i + 1}</div>
                  </div>
                  <div className="text-green-600"><Icons.Phone className="w-5 h-5" /></div>
                </div>
              </Box>
            ))}
          </div>
          <NavBar />
        </>
      )}

      {screen === 'updates' && (
        <>
          {toolbar && <Box element={toolbar} selected={isSel(toolbar.id)} onClick={() => sel(toolbar.id)} onDelete={() => onDelete(toolbar.id)} onDuplicate={() => onDuplicate(toolbar.id)}>
            <div className={`relative flex items-center gap-2 px-2 py-3 ${cls(toolbar.id)}`}>
              {toolbarTitle && <Box element={toolbarTitle} selected={isSel(toolbarTitle.id)} onClick={() => sel(toolbarTitle.id)} onDelete={() => onDelete(toolbarTitle.id)} onDuplicate={() => onDuplicate(toolbarTitle.id)} className="flex-1">
                <div className={cls(toolbarTitle.id)}>Pembaruan</div>
              </Box>}
              <div className="flex gap-2 text-white"><span>🔍</span></div>
            </div>
          </Box>}
          <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
            {statusRows.map((row, i) => (
              <Box key={row.id} element={row} selected={isSel(row.id)} onClick={() => sel(row.id)} onDelete={() => onDelete(row.id)} onDuplicate={() => onDuplicate(row.id)} className="mx-1">
                <div className={`flex items-center gap-2 ${cls('updates_row')}`}>
                  <Box element={statusRing ?? row} selected={isSel(statusRing?.id ?? '')} onClick={() => sel(statusRing?.id ?? row.id)} onDelete={() => onDelete(statusRing?.id ?? row.id)} onDuplicate={() => onDuplicate(statusRing?.id ?? row.id)}>
                    <div className={`w-12 h-12 rounded-full flex items-center justify-center text-gray-600 ${cls('updates_ring')}`}>{names[i % names.length][0]}</div>
                  </Box>
                  <div className="flex-1">
                    <div className={cls('updates_row_name')}>{names[i % names.length]}</div>
                    <div className={cls('updates_row_time')}>Baru saja</div>
                  </div>
                </div>
              </Box>
            ))}
          </div>
          <NavBar />
        </>
      )}

      {screen === 'conversation' && (
        <>
          <div className={`relative flex items-center gap-2 px-2 py-3 ${cls('conv_toolbar')}`}>
            <div className="text-white text-lg">←</div>
            <div className="w-8 h-8 rounded-full bg-gray-400/50" />
            <Box element={el('conv_name') ?? { id: 'conv_name', label: 'Chat Name', type: 'conv-name', screen: 'conversation', style: {} }} selected={isSel('conv_name')} onClick={() => sel('conv_name')} onDelete={() => onDelete('conv_name')} onDuplicate={() => onDuplicate('conv_name')} className="flex-1">
              <div className={cls('conv_name')}>Aisyah</div>
            </Box>
            <div className="flex gap-2 text-white"><span>📞</span><span>⋮</span></div>
          </div>
          <div className="relative flex-1 overflow-hidden flex flex-col gap-2 p-3">
            <Box element={convBubbleIn ?? { id: 'conv_bubble_in', label: 'Incoming', type: 'bubble-incoming', screen: 'conversation', style: {} }} selected={isSel('conv_bubble_in')} onClick={() => sel('conv_bubble_in')} onDelete={() => onDelete('conv_bubble_in')} onDuplicate={() => onDuplicate('conv_bubble_in')} className="self-start max-w-[70%]">
              <div className={cls('conv_bubble_in')}>Halo, apa kabar?</div>
            </Box>
            <Box element={convBubbleOut ?? { id: 'conv_bubble_out', label: 'Outgoing', type: 'bubble-outgoing', screen: 'conversation', style: {} }} selected={isSel('conv_bubble_out')} onClick={() => sel('conv_bubble_out')} onDelete={() => onDelete('conv_bubble_out')} onDuplicate={() => onDuplicate('conv_bubble_out')} className="self-end max-w-[70%]">
              <div className={cls('conv_bubble_out')}>Baik, kamu?</div>
            </Box>
          </div>
          <div className="relative flex items-center gap-2 p-2">
            <Box element={convInput ?? { id: 'conv_input', label: 'Input', type: 'input', screen: 'conversation', style: {} }} selected={isSel('conv_input')} onClick={() => sel('conv_input')} onDelete={() => onDelete('conv_input')} onDuplicate={() => onDuplicate('conv_input')} className="flex-1">
              <div className={`flex items-center gap-2 ${cls('conv_input')}`}><span className="text-gray-400">✚</span><span className="text-gray-400 text-sm">Ketik pesan</span></div>
            </Box>
            <Box element={convSend ?? { id: 'conv_send', label: 'Send', type: 'send', screen: 'conversation', style: {} }} selected={isSel('conv_send')} onClick={() => sel('conv_send')} onDelete={() => onDelete('conv_send')} onDuplicate={() => onDuplicate('conv_send')}>
              <div className={`w-10 h-10 rounded-full flex items-center justify-center ${cls('conv_send')}`}>{iconFor(convSend?.style.icon) ?? <span className="text-white">➤</span>}</div>
            </Box>
          </div>
        </>
      )}

      {screen === 'groups' && (
        <>
          {toolbar && <Box element={toolbar} selected={isSel(toolbar.id)} onClick={() => sel(toolbar.id)} onDelete={() => onDelete(toolbar.id)} onDuplicate={() => onDuplicate(toolbar.id)}>
            <div className={`relative flex items-center gap-2 px-2 py-3 ${cls(toolbar.id)}`}>
              {toolbarTitle && <Box element={toolbarTitle} selected={isSel(toolbarTitle.id)} onClick={() => sel(toolbarTitle.id)} onDelete={() => onDelete(toolbarTitle.id)} onDuplicate={() => onDuplicate(toolbarTitle.id)} className="flex-1">
                <div className={cls(toolbarTitle.id)}>Grup</div>
              </Box>}
              <div className="flex gap-2 text-white"><span>🔍</span></div>
            </div>
          </Box>}
          <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
            {rows.map((row, i) => (
              <Box key={row.id} element={row} selected={isSel(row.id)} onClick={() => sel(row.id)} onDelete={() => onDelete(row.id)} onDuplicate={() => onDuplicate(row.id)} className="mx-1">
                <div className={`flex items-center gap-2 ${cls('groups_row')}`}>
                  <div className="w-10 h-10 rounded-full bg-green-200 flex items-center justify-center text-green-700"><Icons.Users className="w-4 h-4" /></div>
                  <div className="flex-1">
                    <div className={cls('home_row_name')}>Grup {names[i % names.length]}</div>
                    <div className={cls('home_row_msg')}>Pesan grup {i + 1}</div>
                  </div>
                  {groupBadge && <Box element={groupBadge} selected={isSel(groupBadge.id)} onClick={() => sel(groupBadge.id)} onDelete={() => onDelete(groupBadge.id)} onDuplicate={() => onDuplicate(groupBadge.id)}>
                    <div className={cls('groups_badge')}>Grup</div>
                  </Box>}
                </div>
              </Box>
            ))}
          </div>
          <NavBar />
        </>
      )}

      {screen === 'communities' && (
        <>
          {toolbar && <Box element={toolbar} selected={isSel(toolbar.id)} onClick={() => sel(toolbar.id)} onDelete={() => onDelete(toolbar.id)} onDuplicate={() => onDuplicate(toolbar.id)}>
            <div className={`relative flex items-center gap-2 px-2 py-3 ${cls(toolbar.id)}`}>
              {toolbarTitle && <Box element={toolbarTitle} selected={isSel(toolbarTitle.id)} onClick={() => sel(toolbarTitle.id)} onDelete={() => onDelete(toolbarTitle.id)} onDuplicate={() => onDuplicate(toolbarTitle.id)} className="flex-1">
                <div className={cls(toolbarTitle.id)}>Komunitas</div>
              </Box>}
              <div className="flex gap-2 text-white"><span>🔍</span></div>
            </div>
          </Box>}
          {comHeader && <Box element={comHeader} selected={isSel(comHeader.id)} onClick={() => sel(comHeader.id)} onDelete={() => onDelete(comHeader.id)} onDuplicate={() => onDuplicate(comHeader.id)} className="mx-2 mt-1">
            <div className={`flex items-center gap-3 ${cls(comHeader.id)}`}>
              <div className="w-12 h-12 rounded-full bg-green-200 flex items-center justify-center text-green-700"><Icons.Users className="w-6 h-6" /></div>
              <div>
                <div className="font-bold text-gray-900">Komunitas Saya</div>
                <div className="text-xs text-gray-500">3 grup</div>
              </div>
            </div>
          </Box>}
          <div className="relative flex-1 overflow-hidden flex flex-col gap-1 p-1">
            {rows.map((row, i) => (
              <Box key={row.id} element={row} selected={isSel(row.id)} onClick={() => sel(row.id)} onDelete={() => onDelete(row.id)} onDuplicate={() => onDuplicate(row.id)} className="mx-1">
                <div className={`flex items-center gap-2 ${cls('com_row')}`}>
                  <div className="w-10 h-10 rounded-full bg-green-200 flex items-center justify-center text-green-700"><Icons.Users className="w-4 h-4" /></div>
                  <div className="flex-1">
                    <div className={cls('home_row_name')}>Grup {names[i % names.length]}</div>
                    <div className={cls('home_row_msg')}>Pesan {i + 1}</div>
                  </div>
                </div>
              </Box>
            ))}
          </div>
          <NavBar />
        </>
      )}
    </div>
  )
}
