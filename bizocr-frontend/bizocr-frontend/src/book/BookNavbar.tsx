import { whoami } from '../layout/Shell'

export type BookTab = 'DASHBOARD' | 'PROCESS_IMAGE' | 'SEARCH' | 'PROFILE'

interface Props {
  activeTab: BookTab
  onTabChange: (tab: BookTab) => void
  onCloseBook: () => void
  onLogout: () => void
  cardCount?: number
}

const NAV_ITEMS: {
  id: BookTab
  label: string
  shortLabel: string
  icon: string
}[] = [
  { id: 'DASHBOARD',     label: 'Dashboard',      shortLabel: 'Home',    icon: 'home' },
  { id: 'PROCESS_IMAGE', label: 'Process Image',   shortLabel: 'Quét',   icon: 'document_scanner' },
  { id: 'SEARCH',        label: 'Search',          shortLabel: 'Search',  icon: 'search' },
  { id: 'PROFILE',       label: 'Profile',         shortLabel: 'Profile', icon: 'person' },
]

export default function BookNavbar({
  activeTab,
  onTabChange,
  onCloseBook,
  onLogout,
}: Props) {
  const user = whoami()

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-[#fcf9f4]/90 backdrop-blur-md shadow-[0_1px_8px_rgba(92,79,61,0.06)] border-b border-[#c7c4d8]/40">
      <div className="h-16 w-full max-w-7xl mx-auto px-4 sm:px-6 flex items-center justify-between gap-3">

        {/* Logo & Brand */}
        <div
          onClick={onCloseBook}
          title="Bấm để đóng sổ (Xem lại bìa 3D)"
          className="flex items-center gap-2 shrink-0 cursor-pointer group"
        >
          <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-[#f0ede9] group-hover:bg-[#ebe8e3] flex items-center justify-center text-[#3525cd] shadow-[0_1px_2px_0_rgba(92,79,61,0.04)] transition-colors">
            <span className="material-symbols-outlined text-[20px] sm:text-[22px]">menu_book</span>
          </div>
          {/* Hide full brand text on very small screens to save space */}
          <div className="hidden xs:flex flex-col">
            <span className="font-extrabold text-sm tracking-tight text-[#1c1c19] leading-tight">
              BOOK NAME CARD
            </span>
            <span className="text-[10px] text-[#464555] uppercase tracking-wider font-semibold">
              Sổ Danh Thiếp
            </span>
          </div>
        </div>

        {/* Navigation Tabs — center, scrollable on tiny screens */}
        <nav className="flex-1 flex justify-center">
          <div className="flex items-center gap-0.5 bg-[#f6f3ee] p-1 rounded-xl shadow-[inset_0_1px_2px_rgba(30,41,59,0.04)] overflow-x-auto scrollbar-none max-w-full">
            {NAV_ITEMS.map((item) => {
              const active = activeTab === item.id
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onTabChange(item.id)}
                  className={`flex items-center gap-1.5 whitespace-nowrap rounded-lg text-xs font-semibold transition-all cursor-pointer
                    px-2.5 py-1.5 sm:px-3 sm:py-1.5 md:px-4
                    ${active
                      ? 'bg-[#4f46e5] text-white shadow-[0_2px_4px_rgba(79,70,229,0.25)]'
                      : 'text-[#464555] hover:text-[#1c1c19] hover:bg-[#f0ede9]'
                    }`}
                >
                  {/* Icon: always visible */}
                  <span
                    className="material-symbols-outlined"
                    style={{ fontSize: '16px', lineHeight: 1 }}
                  >
                    {item.icon}
                  </span>
                  {/* Label: hidden on < sm, short label sm, full label md+ */}
                  <span className="hidden sm:inline md:hidden">{item.shortLabel}</span>
                  <span className="hidden md:inline">{item.label}</span>
                </button>
              )
            })}
          </div>
        </nav>

        {/* User avatar & logout */}
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={() => onTabChange('PROFILE')}
            title={`Tài khoản: ${user.name || 'Người dùng'}`}
            className="w-8 h-8 rounded-full bg-[#3525cd] flex items-center justify-center shrink-0 shadow-[0_1px_2px_0_rgba(92,79,61,0.04)] text-white hover:scale-105 transition-transform cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">person</span>
          </button>

          <button
            type="button"
            onClick={onLogout}
            title="Đăng xuất"
            className="w-8 h-8 rounded-full text-[#ba1a1a] hover:bg-[#ffdad6]/40 flex items-center justify-center transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">logout</span>
          </button>
        </div>
      </div>
    </header>
  )
}
