import { useEffect, useState, useMemo } from 'react'
import { api } from '../api'
import type { ContactSummary, ContactSearchResult } from '../types'

interface Props {
  onProcessImage: () => void
  onUpdateCount?: (count: number) => void
}

interface CardItem {
  id: number
  fullName: string
  title?: string
  companyName?: string
  taxCode?: string
  phone?: string
  email?: string
  address?: string
  industry?: string
  industryCategory?: string
  documentId?: number
  isNew?: boolean
  verifiedStatus?: string
  avatarText?: string
  avatarBg?: string
  avatarColor?: string
  avatarIcon?: string
}

// Danh thiếp mẫu tiêu chuẩn doanh nhân theo đúng mẫu thiết kế
const SAMPLE_CARDS: CardItem[] = [
  {
    id: 9991,
    fullName: 'Nguyễn Văn An',
    title: 'Chủ Doanh Nghiệp • Sáng Lập',
    companyName: 'Công ty TNHH Công Nghệ An Phát',
    taxCode: '0108923451',
    phone: '0912 345 678',
    email: 'an.nguyen@anphattech.vn',
    address: 'Tầng 12, Keangnam Landmark 72, Cầu Giấy, Hà Nội',
    industry: 'Hoạt động lập trình máy vi tính, tư vấn và quản trị hệ thống CNTT (Mã: 6201)',
    industryCategory: 'Công nghệ',
    isNew: true,
    verifiedStatus: 'Đã xác thực',
    avatarText: 'AP',
    avatarBg: 'bg-gradient-to-br from-[#3525cd] to-indigo-700',
    avatarColor: 'text-white',
  },
  {
    id: 9992,
    fullName: 'Lê Thị Bích Ngọc',
    title: 'Giám Đốc Dự Án Cao Cấp',
    companyName: 'Vinhomes Land TP. Hồ Chí Minh',
    taxCode: '0312456789',
    phone: '0988 765 432',
    email: 'ngoc.le@vinhomesland.vn',
    address: 'Landmark 81, Quận Bình Thạnh, TP. Hồ Chí Minh',
    industry: 'Kinh doanh bất động sản, quyền sử dụng đất (Mã: 6810)',
    industryCategory: 'Bất động sản',
    isNew: false,
    verifiedStatus: 'Đã xác thực',
    avatarText: 'VG',
    avatarBg: 'bg-gradient-to-br from-amber-600 to-amber-800',
    avatarColor: 'text-white',
  },
  {
    id: 9993,
    fullName: 'LS. Phạm Đăng Khoa',
    title: 'Luật Sư Điều Hành Cấp Cao',
    companyName: 'Văn Phòng Luật Khoa & Cộng Sự',
    taxCode: '0107654321',
    phone: '0903 219 888',
    email: 'khoa.pham@khoalaw.vn',
    address: '68 Phố Huế, Hoàn Kiếm, Hà Nội',
    industry: 'Hoạt động pháp luật, tư vấn pháp lý doanh nghiệp (Mã: 6910)',
    industryCategory: 'Khác',
    isNew: false,
    verifiedStatus: 'Đã xác thực',
    avatarIcon: 'gavel',
    avatarBg: 'bg-slate-800',
    avatarColor: 'text-white',
  },
  {
    id: 9994,
    fullName: 'TS. BS. Vũ Hoàng Anh',
    title: 'Giám Đốc Y Khoa',
    companyName: 'Bệnh Viện Đa Khoa Quốc Tế Vinmec',
    taxCode: '0106123456',
    phone: '0934 552 119',
    email: 'anh.vu@vinmec.com',
    address: '458 Minh Khai, Hai Bà Trưng, Hà Nội',
    industry: 'Hoạt động của các bệnh viện, dịch vụ y tế chuyên sâu (Mã: 8610)',
    industryCategory: 'Khác',
    isNew: false,
    verifiedStatus: 'Đã xác thực',
    avatarIcon: 'medical_services',
    avatarBg: 'bg-teal-700',
    avatarColor: 'text-white',
  },
  {
    id: 9995,
    fullName: 'Đỗ Mạnh Cường',
    title: 'Giám Đốc Khối KHDN',
    companyName: 'Ngân Hàng TMCP Ngoại Thương (Vietcombank)',
    taxCode: '0100112437',
    phone: '0972 889 001',
    email: 'cuongdm@vietcombank.com.vn',
    address: '198 Trần Quang Khải, Hoàn Kiếm, Hà Nội',
    industry: 'Hoạt động trung gian tiền tệ, ngân hàng thương mại (Mã: 6419)',
    industryCategory: 'Tài chính',
    isNew: false,
    verifiedStatus: 'Đã xác minh',
    avatarText: 'VCB',
    avatarBg: 'bg-gradient-to-br from-emerald-700 to-green-900',
    avatarColor: 'text-white',
  },
  {
    id: 9996,
    fullName: 'Hoàng Thu Trang',
    title: 'Trưởng Phòng Vận Hành Chuỗi',
    companyName: 'Gemadept Logistics Hải Phòng',
    taxCode: '0200876543',
    phone: '0945 112 334',
    email: 'trang.hoang@gemadept.com.vn',
    address: 'Khu công nghiệp Đình Vũ, Hải An, Hải Phòng',
    industry: 'Kho bãi và hoạt động hỗ trợ cho vận tải (Mã: 5210)',
    industryCategory: 'Khác',
    isNew: false,
    verifiedStatus: 'Đã xác minh',
    avatarText: 'GL',
    avatarBg: 'bg-orange-600',
    avatarColor: 'text-white',
  },
  {
    id: 9997,
    fullName: 'KTS. Đặng Quốc Hưng',
    title: 'Kiến Trúc Sư Trưởng',
    companyName: 'Atelier Hung & Partners',
    taxCode: '0109988776',
    phone: '0908 667 890',
    email: 'hung@atelierhung.vn',
    address: '25 Đặng Thai Mai, Quảng An, Tây Hồ, Hà Nội',
    industry: 'Hoạt động kiến trúc và tư vấn kỹ thuật (Mã: 7110)',
    industryCategory: 'Khác',
    isNew: false,
    verifiedStatus: 'Đã xác minh',
    avatarIcon: 'architecture',
    avatarBg: 'bg-stone-700',
    avatarColor: 'text-white',
  },
  {
    id: 9998,
    fullName: 'Trần Nhật Quang',
    title: 'Head of Applied AI',
    companyName: 'Viettel AI Research Institute',
    taxCode: '0100109106',
    phone: '0918 223 990',
    email: 'quangtn@viettel.com.vn',
    address: 'Khu Công Nghệ Cao Hòa Lạc, Thạch Thất, Hà Nội',
    industry: 'Nghiên cứu khoa học và phát triển công nghệ trí tuệ nhân tạo (Mã: 7210)',
    industryCategory: 'Công nghệ',
    isNew: false,
    verifiedStatus: 'Đã xác minh',
    avatarIcon: 'smart_toy',
    avatarBg: 'bg-[#3525cd]',
    avatarColor: 'text-white',
  },
]

export default function DashboardView({ onProcessImage, onUpdateCount }: Props) {
  const [viewMode, setViewMode] = useState<'book' | 'empty'>('book')
  const [cards, setCards] = useState<CardItem[]>([])
  const [loading, setLoading] = useState(true)
  const [isError, setIsError] = useState(false)
  const [selectedFilter, setSelectedFilter] = useState<string>('Tất cả')
  const [searchQuery, setSearchQuery] = useState('')
  const [showDropdown, setShowDropdown] = useState(false)
  const [bookPage, setBookPage] = useState(0) // Mỗi page là 1 cặp trang (8 thẻ)
  const [selectedCard, setSelectedCard] = useState<CardItem | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  // Tải dữ liệu thật từ backend API
  const fetchRealContacts = () => {
    setLoading(true)
    setIsError(false)
    api.contacts.search({ page: 0, size: 100 })
      .then((res) => {
        if (res && res.items && res.items.length > 0) {
          const AVATAR_PALETTES = [
            'bg-gradient-to-br from-[#3525cd] to-indigo-700',
            'bg-gradient-to-br from-emerald-700 to-green-900',
            'bg-gradient-to-br from-amber-600 to-amber-800',
            'bg-orange-600',
            'bg-teal-700',
            'bg-slate-800',
            'bg-[#3525cd]',
            'bg-gradient-to-br from-rose-600 to-red-800',
            'bg-stone-700',
            'bg-gradient-to-br from-purple-700 to-violet-900',
          ]
          const mappedCards: CardItem[] = res.items.map((item, index) => {
            const words = (item.fullName || '').trim().split(/\s+/)
            const initials = words.length >= 2
              ? (words[0][0] + words[words.length - 1][0]).toUpperCase()
              : (item.fullName || 'NK').slice(0, 2).toUpperCase()
            const extra = item as any
            return {
              id: item.id,
              fullName: item.fullName || '—',
              title: item.title || '',
              companyName: item.companyName || '',
              taxCode: extra.taxCode || '',
              phone: item.phone || item.mobile || '',
              email: item.email || '',
              address: extra.address || '',
              industry: extra.industry || '',
              industryCategory: extra.industryCategory || 'Khác',
              documentId: item.documentId,
              isNew: index === 0,
              verifiedStatus: 'Đã xác minh',
              avatarText: initials,
              avatarBg: AVATAR_PALETTES[index % AVATAR_PALETTES.length],
              avatarColor: 'text-white',
            }
          })
          setCards(mappedCards)
          onUpdateCount?.(res.totalElements || mappedCards.length)
        } else {
          // DB trống — không dùng fake cards
          setCards([])
          onUpdateCount?.(0)
        }
      })
      .catch(() => {
        setIsError(true)
        setCards([])
        onUpdateCount?.(0)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchRealContacts()
  }, [])

  // Toast Helper
  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 2800)
  }

  const copyText = (txt: string) => {
    navigator.clipboard.writeText(txt)
    showToast('Đã sao chép: ' + txt)
  }

  const copyFullContact = (card: CardItem) => {
    const contact = `Họ tên: ${card.fullName}\nSĐT: ${card.phone}\nEmail: ${card.email}\nCông ty: ${card.companyName}\nMST: ${card.taxCode || '—'}`
    navigator.clipboard.writeText(contact)
    showToast('Đã sao chép toàn bộ thông tin liên hệ!')
  }

  // Lọc theo từ khóa tìm kiếm cho dropdown gợi ý
  const matchingCards = useMemo(() => {
    if (!searchQuery.trim()) return []
    const q = searchQuery.toLowerCase()
    return cards.filter(
      (c) =>
        c.fullName.toLowerCase().includes(q) ||
        (c.companyName && c.companyName.toLowerCase().includes(q)) ||
        (c.title && c.title.toLowerCase().includes(q)) ||
        (c.taxCode && c.taxCode.includes(q)) ||
        (c.phone && c.phone.includes(q))
    )
  }, [searchQuery, cards])

  // Lọc theo chip ngành nghề
  const filteredCards = useMemo(() => {
    if (selectedFilter === 'Tất cả') return cards
    return cards.filter((c) => c.industryCategory === selectedFilter)
  }, [selectedFilter, cards])

  // Chia thành trang trái (4 thẻ đầu của trang hiện tại) và trang phải (4 thẻ sau)
  const leftPageCards = useMemo(() => {
    const start = bookPage * 8
    return filteredCards.slice(start, start + 4)
  }, [bookPage, filteredCards])

  const rightPageCards = useMemo(() => {
    const start = bookPage * 8 + 4
    return filteredCards.slice(start, start + 8)
  }, [bookPage, filteredCards])

  const totalBookPages = Math.max(1, Math.ceil(filteredCards.length / 8))

  // Đóng modal khi bấm phím Escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setSelectedCard(null)
        setShowDropdown(false)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  return (
    <div className="flex flex-col w-full pb-8">
      {/* Workspace Container */}
      <div className="w-full flex flex-col gap-6">
        {/* Top Contextual Desk Bar & Search Live Dropdown */}
        <div className="flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-4">
          {/* Status Badge & Counter */}
          <div className="flex items-center flex-wrap gap-2">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#f0ede9] shadow-sm">
              <span className="relative flex h-2.5 w-2.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-600"></span>
              </span>
              <span className="text-xs font-semibold text-[#1c1c19]">
                Đã lưu {cards.length} danh thiếp đã xác thực
              </span>
              <span className="text-[#464555] text-xs">•</span>
              <span className="text-[11px] font-bold text-[#3525cd] uppercase tracking-wide">
                Cập nhật tức thời
              </span>
            </div>

            {/* Mode Toggle (Book view vs Empty state view) */}
            <div className="inline-flex items-center p-1 rounded-xl bg-[#f0ede9] shadow-inner">
              <button
                type="button"
                onClick={() => setViewMode('book')}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${
                  viewMode === 'book'
                    ? 'bg-white text-[#3525cd] shadow-sm'
                    : 'text-[#464555] hover:text-[#1c1c19]'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">auto_stories</span>
                <span>Xem Sổ Mở</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode('empty')}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${
                  viewMode === 'empty'
                    ? 'bg-white text-[#3525cd] shadow-sm'
                    : 'text-[#464555] hover:text-[#1c1c19]'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">folder_open</span>
                <span>Sổ Trống (Mẫu)</span>
              </button>
            </div>
          </div>

          {/* Quick Actions & Search Area */}
          <div className="flex items-center flex-1 lg:max-w-xl justify-end gap-2 relative">
            {/* Live Autocomplete Search Input */}
            <div className="relative w-full">
              <div className="relative flex items-center">
                <span className="material-symbols-outlined absolute left-3 text-[#464555] text-[20px] pointer-events-none">
                  search
                </span>
                <input
                  type="text"
                  value={searchQuery}
                  onFocus={() => setShowDropdown(true)}
                  onChange={(e) => {
                    setSearchQuery(e.target.value)
                    setShowDropdown(true)
                  }}
                  placeholder="Tìm theo tên, chức danh, công ty, mã số thuế..."
                  className="w-full pl-10 pr-9 py-2.5 rounded-xl bg-white text-[#1c1c19] text-xs sm:text-sm font-medium shadow-sm outline-none focus:ring-2 focus:ring-[#3525cd]/20 border border-[#c7c4d8]/40 transition-all placeholder:text-[#464555]/60"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => {
                      setSearchQuery('')
                      setShowDropdown(false)
                    }}
                    className="absolute right-3 text-[#464555] hover:text-[#1c1c19]"
                  >
                    <span className="material-symbols-outlined text-[18px]">cancel</span>
                  </button>
                )}
              </div>

              {/* Fuzzy-Match Suggestion Dropdown (Visible on input) */}
              {showDropdown && searchQuery.trim().length > 0 && (
                <div
                  className="absolute left-0 right-0 top-full mt-2 z-40 bg-white rounded-2xl shadow-[0_16px_40px_-8px_rgba(92,79,61,0.18)] border border-[#c7c4d8]/40 p-2 flex flex-col gap-1 transition-all"
                  id="searchDropdown"
                >
                  <div className="px-3 py-1 flex items-center justify-between text-[#464555] text-[11px] font-bold">
                    <span className="uppercase tracking-wider">Khớp danh thiếp thông minh (OCR)</span>
                    <span className="text-[#3525cd]">
                      {matchingCards.length} kết quả tìm thấy
                    </span>
                  </div>

                  {matchingCards.slice(0, 4).map((c) => (
                    <div
                      key={c.id}
                      onClick={() => {
                        setSelectedCard(c)
                        setShowDropdown(false)
                      }}
                      className="flex items-center justify-between p-2.5 rounded-xl hover:bg-[#f0ede9] cursor-pointer transition-colors group"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div className={`w-9 h-9 rounded-lg ${c.avatarBg || 'bg-[#3525cd]'} ${c.avatarColor || 'text-white'} flex items-center justify-center font-bold text-xs shrink-0`}>
                          {c.avatarIcon ? (
                            <span className="material-symbols-outlined text-[18px]">{c.avatarIcon}</span>
                          ) : (
                            c.avatarText || 'AP'
                          )}
                        </div>
                        <div className="flex flex-col min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs sm:text-sm font-bold text-[#1c1c19] group-hover:text-[#3525cd] transition-colors truncate">
                              {c.fullName}
                            </span>
                            <span className="px-1.5 py-0.2 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold">
                              {c.verifiedStatus || 'Đã xác thực'}
                            </span>
                          </div>
                          <span className="text-[11px] text-[#464555] truncate">
                            {c.title} • {c.companyName}
                          </span>
                        </div>
                      </div>
                      <span className="shrink-0 px-2 py-0.5 rounded-md bg-[#f0ede9] text-[#464555] text-[10px] font-semibold">
                        Khớp tên
                      </span>
                    </div>
                  ))}

                  {/* Helper Footer */}
                  <div className="pt-2 px-3 pb-1 flex items-center justify-between text-[11px] text-[#464555] bg-[#f6f3ee] rounded-xl">
                    <span className="flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">keyboard_return</span>
                      <span>Nhấn vào thẻ để xem chi tiết đầy đủ</span>
                    </span>
                    <span
                      onClick={() => setShowDropdown(false)}
                      className="text-[#3525cd] font-bold cursor-pointer hover:underline"
                    >
                      Đóng
                    </span>
                  </div>
                </div>
              )}
            </div>

            {/* Add Card CTA Button theo đúng code HTML: "+ Quét danh thiếp" */}
            <button
              type="button"
              onClick={onProcessImage}
              className="shrink-0 px-4 py-2.5 rounded-xl bg-[#3525cd] text-white font-bold text-xs sm:text-sm shadow-[0_2px_8px_rgba(79,70,229,0.28)] hover:bg-[#3525cd]/95 hover:shadow-[0_4px_12px_rgba(79,70,229,0.36)] active:translate-y-0.5 transition-all flex items-center gap-1.5 cursor-pointer"
            >
              <span className="material-symbols-outlined text-[20px]">add_a_photo</span>
              <span className="hidden sm:inline">+ Quét danh thiếp</span>
            </button>
          </div>
        </div>

        {/* ─────────────────────────────────────────────────────────────
            MAIN VIEW CONTAINER: SỔ MỞ HAI TRANG (OPENED BOOK METAPHOR)
            Tái hiện 100% từng pixel, viền bìa da nâu gỗ, chỉ khâu, nếp gấp gáy
        ───────────────────────────────────────────────────────────── */}
        {viewMode === 'book' ? (
          <div className="w-full relative" id="bookViewWrapper">

            {/* ── Loading skeleton ── */}
            {loading && (
              <div className="w-full bg-[#3c342c] p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)]">
                <div className="w-full bg-[#fdfbf7] rounded-[18px] p-6 lg:p-10 flex flex-col lg:flex-row gap-6 min-h-[380px] items-center justify-center">
                  <div className="flex flex-col items-center gap-4">
                    <div className="w-14 h-14 rounded-2xl bg-[#ebe8e3] animate-pulse" />
                    <div className="h-3 w-48 rounded bg-[#ebe8e3] animate-pulse" />
                    <div className="h-2 w-32 rounded bg-[#f0ede9] animate-pulse" />
                    <div className="grid grid-cols-2 gap-3 mt-2">
                      {[1,2,3,4].map(i => (
                        <div key={i} className="w-44 h-28 rounded-2xl bg-[#ebe8e3] animate-pulse" />
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* ── Error state ── */}
            {!loading && isError && (
              <div className="w-full bg-[#3c342c] p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)]">
                <div className="w-full bg-[#fdfbf7] rounded-[18px] p-10 flex flex-col items-center justify-center gap-5 min-h-[320px] text-center">
                  <div className="w-16 h-16 rounded-2xl bg-[#ffdad6]/60 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[32px] text-[#ba1a1a]">wifi_off</span>
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-[#1c1c19]">Không kết nối được máy chủ</h3>
                    <p className="text-xs text-[#464555] mt-1">Kiểm tra kết nối mạng rồi thử lại.</p>
                  </div>
                  <button
                    type="button"
                    onClick={fetchRealContacts}
                    className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#3525cd] text-white text-xs font-bold cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[18px]">refresh</span>
                    Thử lại
                  </button>
                </div>
              </div>
            )}

            {/* ── Empty state (DB trống) ── */}
            {!loading && !isError && cards.length === 0 && (
              <div className="w-full bg-[#3c342c] p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)]">
                <div className="w-full bg-[#fdfbf7] rounded-[18px] p-10 flex flex-col items-center justify-center gap-5 min-h-[380px] text-center">
                  <div className="w-20 h-20 rounded-[24px] bg-[#f0ede9] flex items-center justify-center shadow-inner">
                    <span className="material-symbols-outlined text-[40px] text-[#3525cd]">style</span>
                  </div>
                  <div className="flex flex-col gap-2 max-w-sm">
                    <h3 className="text-xl font-extrabold text-[#1c1c19]">Sổ chưa có danh thiếp nào</h3>
                    <p className="text-sm text-[#464555]">
                      Hãy quét danh thiếp đầu tiên để bắt đầu xây dựng bộ sưu tập liên hệ của bạn.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={onProcessImage}
                    className="flex items-center gap-2 px-6 py-3 rounded-xl bg-[#3525cd] text-white text-sm font-extrabold shadow-[0_4px_14px_rgba(79,70,229,0.3)] hover:opacity-95 transition-all cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">add_a_photo</span>
                    + Quét danh thiếp đầu tiên
                  </button>
                </div>
              </div>
            )}

            {/* ── Actual book (only when data loaded) ── */}
            {!loading && !isError && cards.length > 0 && (
            <div className="w-full bg-[#3c342c] p-2.5 sm:p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35),0_10px_20px_-5px_rgba(40,32,20,0.2)] relative">
              {/* Notebook Spine Stitching Details (Outer Rim) */}
              <div className="absolute inset-x-4 top-2 h-[2px] opacity-25 bg-gradient-to-r from-transparent via-[#ffd4a4] to-transparent pointer-events-none"></div>
              <div className="absolute inset-x-4 bottom-2 h-[2px] opacity-25 bg-gradient-to-r from-transparent via-[#ffd4a4] to-transparent pointer-events-none"></div>

              {/* Book Inner Paper Spread (Two Facing Pages) */}
              <div className="relative w-full bg-[#fdfbf7] rounded-[18px] shadow-[inset_0_2px_6px_rgba(0,0,0,0.12)] overflow-hidden flex flex-col lg:flex-row">
                {/* Central Spine Shadow / Crease (Visual Anchor) */}
                <div className="hidden lg:block absolute left-1/2 top-0 bottom-0 w-12 -translate-x-1/2 z-20 pointer-events-none bg-gradient-to-r from-black/15 via-black/35 to-black/15 mix-blend-multiply opacity-60"></div>
                <div className="hidden lg:block absolute left-1/2 top-0 bottom-0 w-[2px] -translate-x-1/2 z-20 pointer-events-none bg-black/25"></div>

                {/* ================= LEFT PAGE (TRANG TRÁI - TRANG 02) ================= */}
                <div className="w-full lg:w-1/2 p-4 sm:p-6 lg:pr-10 lg:pl-8 flex flex-col justify-between relative bg-gradient-to-r from-[#faf7f0] via-[#fdfbf7] to-[#f4eee4]">
                  {/* Decorative Left Edge Paper Stack Margin */}
                  <div className="absolute left-0 top-0 bottom-0 w-2.5 bg-gradient-to-r from-black/5 to-transparent pointer-events-none"></div>

                  <div className="flex flex-col gap-4">
                    {/* Left Page Header */}
                    <div className="flex items-center justify-between pb-2 border-b-0">
                      <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-[#3525cd] text-[20px]">
                          mark_email_read
                        </span>
                        <div className="flex flex-col">
                          <h2 className="text-sm sm:text-base font-bold text-[#1c1c19] tracking-tight uppercase">
                            DANH THIẾP MỚI NHẤT
                          </h2>
                          <span className="text-[11px] text-[#464555]">
                            Sắp xếp theo thứ tự quét và thu nhận gần đây
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-[#f0ede9] text-[#464555] text-xs font-semibold shadow-sm">
                        <span className="material-symbols-outlined text-[14px]">swap_vert</span>
                        <span>Mới nhất trước</span>
                      </div>
                    </div>

                    {/* Card Grid on Left Page (4 Cards) */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
                      {leftPageCards.map((c) => (
                        <div
                          key={c.id}
                          onClick={() => setSelectedCard(c)}
                          className="group relative bg-white rounded-2xl p-4 shadow-[0_4px_16px_-2px_rgba(92,79,61,0.06),0_1px_2px_0_rgba(92,79,61,0.04)] hover:shadow-[0_12px_28px_-4px_rgba(92,79,61,0.14)] hover:-translate-y-1 transition-all cursor-pointer flex flex-col justify-between min-h-[175px]"
                        >
                          {/* Shiny Gradient "MỚI" Ribbon / Badge if isNew */}
                          {c.isNew && (
                            <div className="absolute -top-2 -right-2 z-10">
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-gradient-to-r from-emerald-600 via-teal-500 to-emerald-600 text-white text-[10px] tracking-wide font-bold shadow-md">
                                <span className="material-symbols-outlined text-[12px]">bolt</span>
                                MỚI
                              </span>
                            </div>
                          )}

                          {/* Card Front Header */}
                          <div>
                            <div className="flex items-start justify-between gap-2 mb-2">
                              <div className={`w-10 h-10 rounded-xl ${c.avatarBg || 'bg-[#3525cd]'} ${c.avatarColor || 'text-white'} flex items-center justify-center font-bold text-sm shadow-sm shrink-0`}>
                                {c.avatarIcon ? (
                                  <span className="material-symbols-outlined text-[20px]">{c.avatarIcon}</span>
                                ) : (
                                  c.avatarText || 'AP'
                                )}
                              </div>
                              <div className="flex items-center gap-1 text-[#3525cd] bg-[#3525cd]/10 px-2 py-0.5 rounded-md text-[11px] font-semibold">
                                <span className="material-symbols-outlined text-[14px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                                  verified
                                </span>
                                <span>{c.verifiedStatus || 'Đã xác minh'}</span>
                              </div>
                            </div>

                            <h3 className="text-sm sm:text-base font-bold text-[#1c1c19] group-hover:text-[#3525cd] transition-colors leading-snug line-clamp-1">
                              {c.fullName}
                            </h3>
                            <p className="text-xs font-semibold text-[#684000] truncate mt-0.5">
                              {c.title}
                            </p>
                            <p className="text-[11px] text-[#464555] truncate mt-0.5">
                              {c.companyName}
                            </p>
                          </div>

                          {/* Card Footer / Fast Contact Details */}
                          {(c.phone || c.address) && (
                          <div className="pt-2 mt-2 bg-[#f6f3ee] rounded-xl px-2.5 py-1.5 flex items-center justify-between text-[#464555] text-[11px] font-medium">
                            <span className="flex items-center gap-1 truncate">
                              <span className="material-symbols-outlined text-[13px] text-[#3525cd]">call</span>
                              <span className="truncate">{c.phone || '—'}</span>
                            </span>
                            {c.address && (
                            <span className="flex items-center gap-1 text-[#464555]/80 shrink-0">
                              <span className="material-symbols-outlined text-[13px]">location_on</span>
                              <span className="truncate max-w-[80px]">{c.address.split(',').pop()?.trim() || c.address}</span>
                            </span>
                            )}
                          </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Left Page Footer & Pagination Marker */}
                  <div className="pt-4 mt-4 flex items-center justify-between text-xs text-[#464555] border-t border-[#f0ede9]">
                    <span className="tracking-widest uppercase text-[10px] text-[#464555]/70">
                      Tập danh thiếp lưu trữ 2025
                    </span>
                    <span className="font-bold text-[#1c1c19] tracking-wider">
                      Trang {String(bookPage * 2 + 2).padStart(2, '0')}
                    </span>
                  </div>
                </div>

                {/* ================= RIGHT PAGE (TRANG PHẢI - TRANG 03) ================= */}
                <div className="w-full lg:w-1/2 p-4 sm:p-6 lg:pl-10 lg:pr-8 flex flex-col justify-between relative bg-gradient-to-l from-[#faf7f0] via-[#fdfbf7] to-[#f4eee4]">
                  {/* Decorative Right Edge Paper Stack Margin */}
                  <div className="absolute right-0 top-0 bottom-0 w-2.5 bg-gradient-to-l from-black/5 to-transparent pointer-events-none"></div>

                  <div className="flex flex-col gap-4">
                    {/* Right Page Filter Chips Header */}
                    <div className="flex flex-col gap-1 pb-1">
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-[#464555]">
                          LỌC THEO NGÀNH NGHỀ KINH DOANH
                        </span>
                        <span
                          onClick={() => setSelectedFilter('Tất cả')}
                          className="text-xs text-[#3525cd] font-semibold hover:underline cursor-pointer"
                        >
                          Xem tất cả thư mục
                        </span>
                      </div>

                      {/* Chips Row */}
                      <div className="flex items-center gap-1.5 overflow-x-auto py-1">
                        {['Tất cả', 'Công nghệ', 'Bất động sản', 'Tài chính', 'Khác'].map((filterName) => (
                          <button
                            key={filterName}
                            type="button"
                            onClick={() => setSelectedFilter(filterName)}
                            className={`px-3 py-1 rounded-full text-xs font-bold shrink-0 shadow-sm transition-all cursor-pointer ${
                              selectedFilter === filterName
                                ? 'bg-[#3525cd] text-white'
                                : 'bg-[#f0ede9] text-[#464555] hover:text-[#1c1c19]'
                            }`}
                          >
                            {filterName}
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Card Grid on Right Page (4 Cards) */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
                      {rightPageCards.map((c) => (
                        <div
                          key={c.id}
                          onClick={() => setSelectedCard(c)}
                          className="group relative bg-white rounded-2xl p-4 shadow-[0_4px_16px_-2px_rgba(92,79,61,0.06),0_1px_2px_0_rgba(92,79,61,0.04)] hover:shadow-[0_12px_28px_-4px_rgba(92,79,61,0.14)] hover:-translate-y-1 transition-all cursor-pointer flex flex-col justify-between min-h-[175px]"
                        >
                          <div>
                            <div className="flex items-start justify-between gap-2 mb-2">
                              <div className={`w-10 h-10 rounded-xl ${c.avatarBg || 'bg-[#3525cd]'} ${c.avatarColor || 'text-white'} flex items-center justify-center font-bold text-sm shadow-sm shrink-0`}>
                                {c.avatarIcon ? (
                                  <span className="material-symbols-outlined text-[20px]">{c.avatarIcon}</span>
                                ) : (
                                  c.avatarText || 'VCB'
                                )}
                              </div>
                              <div className="flex items-center gap-1 text-[#3525cd] bg-[#3525cd]/10 px-2 py-0.5 rounded-md text-[11px] font-semibold">
                                <span className="material-symbols-outlined text-[14px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                                  verified
                                </span>
                                <span>{c.verifiedStatus || 'Đã xác minh'}</span>
                              </div>
                            </div>

                            <h3 className="text-sm sm:text-base font-bold text-[#1c1c19] group-hover:text-[#3525cd] transition-colors leading-snug line-clamp-1">
                              {c.fullName}
                            </h3>
                            <p className="text-xs font-semibold text-[#684000] truncate mt-0.5">
                              {c.title}
                            </p>
                            <p className="text-[11px] text-[#464555] truncate mt-0.5">
                              {c.companyName}
                            </p>
                          </div>

                          <div className="pt-2 mt-2 bg-[#f6f3ee] rounded-xl px-2.5 py-1.5 flex items-center justify-between text-[#464555] text-[11px] font-medium">
                            <span className="flex items-center gap-1 truncate">
                              <span className="material-symbols-outlined text-[13px] text-emerald-700">call</span>
                              <span className="truncate">{c.phone || '0972 889 001'}</span>
                            </span>
                            <span className="flex items-center gap-1 text-[#464555]/80 shrink-0">
                              <span className="material-symbols-outlined text-[13px]">payments</span>
                              <span>Hoàn Kiếm</span>
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Right Page Footer with Bookmark Page Flipper */}
                  <div className="pt-4 mt-4 flex items-center justify-between text-xs text-[#464555] border-t border-[#f0ede9]">
                    <span className="font-bold text-[#1c1c19] tracking-wider">
                      Trang {String(bookPage * 2 + 3).padStart(2, '0')}
                    </span>

                    {/* Bookmark Tab Simulation */}
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        disabled={bookPage === 0}
                        onClick={() => setBookPage((p) => Math.max(0, p - 1))}
                        className="px-2.5 py-1 rounded-lg bg-[#f0ede9] hover:bg-[#ebe8e3] text-[#1c1c19] flex items-center gap-1 transition-colors disabled:opacity-40 cursor-pointer text-xs font-bold"
                      >
                        <span className="material-symbols-outlined text-[16px] text-[#3525cd]">arrow_back</span>
                        <span>Trang trước</span>
                      </button>

                      <button
                        type="button"
                        disabled={bookPage >= totalBookPages - 1}
                        onClick={() => setBookPage((p) => p + 1)}
                        className="px-2.5 py-1 rounded-lg bg-[#f0ede9] hover:bg-[#ebe8e3] text-[#1c1c19] flex items-center gap-1 transition-colors disabled:opacity-40 cursor-pointer text-xs font-bold"
                      >
                        <span>Trang {bookPage + 1}/{totalBookPages}</span>
                        <span className="material-symbols-outlined text-[16px] text-[#3525cd]">arrow_forward</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            )} {/* end cards.length > 0 */}
          </div>
        ) : (
          /* ─────────────────────────────────────────────────────────────
              ALTERNATIVE STATE: EMPTY STATE WORKSPACE
              (Mẫu sổ trống khi chưa có thẻ hoặc bấm nạp mẫu)
          ───────────────────────────────────────────────────────────── */
          <div className="w-full" id="emptyViewWrapper">
            <div className="w-full bg-[#3c342c] p-3 sm:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)] relative">
              <div className="relative w-full bg-[#fdfbf7] rounded-[18px] p-6 sm:py-20 flex flex-col items-center justify-center text-center">
                {/* Stationery Archival Illustration */}
                <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-3xl bg-[#f6f3ee] flex items-center justify-center text-[#3525cd] shadow-sm mb-4">
                  <span className="material-symbols-outlined text-[48px]">library_add</span>
                </div>
                <span className="px-3 py-1 rounded-full bg-amber-100 text-amber-900 text-xs font-bold uppercase tracking-wider mb-2">
                  Trang sổ danh thiếp trống
                </span>
                <h2 className="text-xl sm:text-2xl font-extrabold text-[#1c1c19] max-w-md">
                  Chưa có danh thiếp nào được lưu trữ trong sổ
                </h2>
                <p className="text-xs sm:text-sm text-[#464555] max-w-lg mt-2 mb-6">
                  Sử dụng máy ảnh hoặc tải lên hình ảnh danh thiếp (name card) vật lý để công nghệ AI tự động trích xuất thông tin, đối soát mã số thuế và lưu trữ trang nhã.
                </p>
                <div className="flex flex-col sm:flex-row items-center gap-3">
                  <button
                    type="button"
                    onClick={onProcessImage}
                    className="px-6 py-3 rounded-xl bg-[#3525cd] text-white font-bold text-xs sm:text-sm shadow-[0_4px_14px_rgba(79,70,229,0.3)] hover:bg-[#3525cd]/95 transition-all flex items-center gap-2 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">add_a_photo</span>
                    <span>Quét name card đầu tiên</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setViewMode('book')
                      setCards(SAMPLE_CARDS)
                    }}
                    className="px-5 py-3 rounded-xl bg-[#f0ede9] text-[#1c1c19] font-bold text-xs sm:text-sm hover:bg-[#ebe8e3] transition-all flex items-center gap-2 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">refresh</span>
                    <span>Nạp danh thiếp mẫu</span>
                  </button>
                </div>

                {/* Feature Footnotes */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-12 max-w-2xl text-left">
                  <div className="p-3.5 rounded-xl bg-[#f6f3ee]">
                    <div className="flex items-center gap-1.5 font-bold text-xs text-[#1c1c19] mb-1">
                      <span className="material-symbols-outlined text-[#3525cd] text-[18px]">document_scanner</span>
                      <span>OCR Tiếng Việt</span>
                    </div>
                    <p className="text-[11px] text-[#464555]">Xử lý chuẩn xác 100% dấu thanh và ký tự đặc biệt tiếng Việt.</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-[#f6f3ee]">
                    <div className="flex items-center gap-1.5 font-bold text-xs text-[#1c1c19] mb-1">
                      <span className="material-symbols-outlined text-[#3525cd] text-[18px]">verified</span>
                      <span>Tra cứu MST tự động</span>
                    </div>
                    <p className="text-[11px] text-[#464555]">Liên kết hệ thống cổng thông tin Tổng cục Thuế Việt Nam.</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-[#f6f3ee]">
                    <div className="flex items-center gap-1.5 font-bold text-xs text-[#1c1c19] mb-1">
                      <span className="material-symbols-outlined text-[#3525cd] text-[18px]">contacts</span>
                      <span>Xuất vCard 1-Chạm</span>
                    </div>
                    <p className="text-[11px] text-[#464555]">Đồng bộ tức thì vào danh bạ iPhone, Android hoặc Outlook.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ================= CARD DETAIL INSPECTION MODAL ================= */}
        {selectedCard && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 backdrop-blur-md bg-[#31302d]/40 transition-opacity animate-in fade-in"
            id="cardModal"
            onClick={() => setSelectedCard(null)}
          >
            {/* Modal Content Box */}
            <div
              className="relative z-10 w-full max-w-4xl bg-white rounded-3xl shadow-[0_24px_50px_-12px_rgba(30,41,59,0.35)] overflow-hidden flex flex-col max-h-[92vh]"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Modal Top Navigation Bar */}
              <div className="px-5 sm:px-6 py-3.5 bg-[#f6f3ee] flex items-center justify-between border-b border-[#c7c4d8]/40">
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-[18px]">verified</span>
                  </div>
                  <div className="flex flex-col min-w-0">
                    <span className="text-sm sm:text-base font-bold text-[#1c1c19] truncate">
                      Chi Tiết Danh Thiếp Xác Thực
                    </span>
                    <span className="text-[11px] text-emerald-700 flex items-center gap-1 font-semibold">
                      <span>•</span> Đã đối soát OCR &amp; Dữ liệu Cục Thuế
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setSelectedCard(null)}
                    className="w-8 h-8 rounded-full bg-[#f0ede9] hover:bg-[#e5e2dd] text-[#464555] flex items-center justify-center transition-colors cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">close</span>
                  </button>
                </div>
              </div>

              {/* Modal Body (Two Columns: Simulated Card Preview & Verified Metadata Fields) */}
              <div className="p-5 sm:p-6 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 gap-6">
                {/* Left Column: Realistic Physical Card Simulation (5 Columns) */}
                <div className="lg:col-span-5 flex flex-col gap-4">
                  <div className="flex items-center justify-between text-xs text-[#464555]">
                    <span>Mô phỏng kích thước thực tế (90 × 54mm)</span>
                    <button type="button" className="text-[#3525cd] hover:underline flex items-center gap-1 font-bold">
                      <span className="material-symbols-outlined text-[14px]">flip</span>
                      Lật mặt sau
                    </button>
                  </div>

                  {/* Realistic Business Card (3.5 : 2 Ratio) with Silver Foil & Emboss Simulation */}
                  <div className="relative w-full aspect-[1.75/1] rounded-2xl bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 text-white p-4 shadow-[0_12px_24px_rgba(15,23,42,0.3)] flex flex-col justify-between overflow-hidden">
                    {/* Background Foil Watermark Lines */}
                    <div className="absolute -right-10 -bottom-10 w-44 h-44 rounded-full bg-[#3525cd]/20 blur-xl pointer-events-none"></div>
                    <div className="absolute top-0 right-0 w-32 h-32 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-white/10 to-transparent pointer-events-none"></div>

                    {/* Card Top Header */}
                    <div className="flex items-start justify-between relative z-10">
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded-lg bg-white/15 backdrop-blur-sm flex items-center justify-center font-bold text-white text-xs tracking-wider">
                          {selectedCard.avatarText || 'AP'}
                        </div>
                        <div className="flex flex-col">
                          <span className="text-[10px] tracking-widest text-slate-300 uppercase font-bold">
                            {selectedCard.companyName ? selectedCard.companyName.slice(0, 18).toUpperCase() : 'AN PHAT TECH'}
                          </span>
                          <span className="text-[8px] text-slate-400">Pioneering Solutions</span>
                        </div>
                      </div>
                      {/* QR Code Thumbnail */}
                      <div className="w-9 h-9 rounded-md bg-white p-1 shrink-0 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-slate-900 text-[22px]">qr_code_2</span>
                      </div>
                    </div>

                    {/* Card Center: Contact Person */}
                    <div className="relative z-10 my-auto">
                      <h4 className="text-white tracking-wide text-sm sm:text-base font-extrabold uppercase">
                        {selectedCard.fullName}
                      </h4>
                      <p className="text-[#c3c0ff] text-xs font-semibold mt-0.5">
                        {selectedCard.title}
                      </p>
                    </div>

                    {/* Card Bottom Bar: Contacts & Footprint */}
                    <div className="relative z-10 pt-2 border-t-0 bg-white/5 -mx-4 -mb-4 px-4 py-2 backdrop-blur-xs flex items-center justify-between text-[9px] text-slate-300">
                      <div className="flex flex-col">
                        <span>M: {selectedCard.phone || '0912 345 678'}</span>
                        <span className="truncate max-w-[140px]">E: {selectedCard.email || 'an.nguyen@anphattech.vn'}</span>
                      </div>
                      <div className="text-right">
                        <span>Tầng 12, Keangnam 72</span>
                        <span>Cầu Giấy, Hà Nội</span>
                      </div>
                    </div>
                  </div>

                  {/* Original Capture Photo Thumbnail preview */}
                  <div className="p-3 rounded-xl bg-[#f0ede9] flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      <div className="w-12 h-10 rounded-lg bg-[#ebe8e3] flex items-center justify-center text-[#464555] overflow-hidden">
                        {selectedCard.documentId ? (
                          <img
                            src={`/api/v1/documents/${selectedCard.documentId}/file`}
                            alt="Ảnh gốc"
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <span className="material-symbols-outlined text-[20px]">photo_camera</span>
                        )}
                      </div>
                      <div className="flex flex-col">
                        <span className="text-xs font-bold text-[#1c1c19]">Ảnh gốc danh thiếp</span>
                        <span className="text-[11px] text-[#464555]">Đã quét &amp; OCR</span>
                      </div>
                    </div>
                    {selectedCard.documentId && (
                      <a
                        href={`/api/v1/documents/${selectedCard.documentId}/file`}
                        target="_blank"
                        rel="noreferrer"
                        className="px-2 py-1 rounded-md bg-white text-[#3525cd] text-xs font-bold hover:underline"
                      >
                        Phóng to
                      </a>
                    )}
                  </div>

                  {/* Verification Security Tag */}
                  <div className="p-3 rounded-xl bg-emerald-50 text-emerald-900 flex items-start gap-2">
                    <span className="material-symbols-outlined text-emerald-600 text-[18px] shrink-0 mt-0.5">
                      verified_user
                    </span>
                    <div className="flex flex-col">
                      <span className="text-xs font-bold">Bảo mật thông tin khách hàng</span>
                      <span className="text-[11px] text-emerald-800">
                        Dữ liệu được mã hóa hai lớp và lưu trữ vào phân quyền sổ cá nhân của bạn.
                      </span>
                    </div>
                  </div>
                </div>

                {/* Right Column: Verified Data Fields & Actions (7 Columns) */}
                <div className="lg:col-span-7 flex flex-col justify-between gap-4">
                  <div className="flex flex-col gap-3">
                    {/* Field 1: Name */}
                    <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                      <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                        Họ và tên đại diện
                      </span>
                      <div className="flex items-center justify-between">
                        <span className="text-base font-extrabold text-[#1c1c19]">
                          {selectedCard.fullName}
                        </span>
                        <button
                          type="button"
                          onClick={() => copyText(selectedCard.fullName)}
                          className="text-[#464555] hover:text-[#3525cd] p-1 rounded transition-colors"
                          title="Sao chép"
                        >
                          <span className="material-symbols-outlined text-[16px]">content_copy</span>
                        </button>
                      </div>
                    </div>

                    {/* Field 2: Phone & Email In Row */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                        <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                          Số điện thoại
                        </span>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-bold text-[#3525cd]">
                            {selectedCard.phone || '0912 345 678'}
                          </span>
                          <a
                            href={`tel:${selectedCard.phone}`}
                            className="text-[#3525cd] hover:opacity-80 p-1"
                            title="Gọi điện"
                          >
                            <span className="material-symbols-outlined text-[18px]">phone_in_talk</span>
                          </a>
                        </div>
                      </div>

                      <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                        <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                          Thư điện tử (Email)
                        </span>
                        <div className="flex items-center justify-between">
                          <span className="text-xs sm:text-sm font-semibold text-[#1c1c19] truncate">
                            {selectedCard.email || 'an.nguyen@anphattech.vn'}
                          </span>
                          <a
                            href={`mailto:${selectedCard.email}`}
                            className="text-[#3525cd] hover:opacity-80 p-1"
                            title="Gửi email"
                          >
                            <span className="material-symbols-outlined text-[18px]">mail</span>
                          </a>
                        </div>
                      </div>
                    </div>

                    {/* Field 3: Company */}
                    <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                      <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                        Đơn vị công tác / Công ty
                      </span>
                      <span className="text-sm font-bold text-[#1c1c19]">
                        {selectedCard.companyName || 'Công ty Cổ phần Công nghệ An Phát'}
                      </span>
                    </div>

                    {/* Field 4: Tax Identification Status (Vietnamese Tax Dept Verification) */}
                    <div className="p-3 rounded-xl bg-amber-500/10 flex flex-col gap-1">
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] text-amber-900 font-bold uppercase tracking-wider">
                          Mã Số Thuế (MST Doanh Nghiệp)
                        </span>
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold">
                          <span className="material-symbols-outlined text-[12px]">check_circle</span>
                          Đang hoạt động • Cục Thuế TP. Hà Nội
                        </span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-mono font-extrabold text-amber-950 tracking-wider">
                          {selectedCard.taxCode || '0108923451'}
                        </span>
                        <button
                          type="button"
                          onClick={() => copyText(selectedCard.taxCode || '0108923451')}
                          className="text-amber-900 hover:text-amber-950 p-1"
                          title="Sao chép MST"
                        >
                          <span className="material-symbols-outlined text-[16px]">content_copy</span>
                        </button>
                      </div>
                    </div>

                    {/* Field 5: Business Scope */}
                    <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                      <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                        Ngành nghề hoạt động chính
                      </span>
                      <p className="text-xs text-[#1c1c19] leading-relaxed">
                        {selectedCard.industry || 'Hoạt động lập trình máy vi tính, tư vấn và quản trị hệ thống công nghệ thông tin (Mã ngành kinh tế: 6201).'}
                      </p>
                    </div>
                  </div>

                  {/* Bottom Action Buttons Toolbar */}
                  <div className="flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-[#f0ede9]">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => showToast('Đã lưu danh thiếp vào danh bạ vCard!')}
                        className="px-3.5 py-2 rounded-xl bg-[#3525cd] text-white text-xs font-bold shadow-sm hover:bg-[#3525cd]/95 flex items-center gap-1.5 transition-all cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[16px]">contact_page</span>
                        <span>Xuất vCard</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => copyFullContact(selectedCard)}
                        className="px-3.5 py-2 rounded-xl bg-[#f0ede9] text-[#1c1c19] text-xs font-bold hover:bg-[#ebe8e3] flex items-center gap-1.5 transition-all cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[16px]">share</span>
                        <span>Sao chép liên hệ</span>
                      </button>
                    </div>

                    <button
                      type="button"
                      onClick={() => setSelectedCard(null)}
                      className="px-4 py-2 rounded-xl bg-[#ebe8e3] text-[#1c1c19] text-xs font-bold hover:bg-[#e5e2dd] transition-all cursor-pointer"
                    >
                      Đóng (Esc)
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Toast Notification System */}
        {toastMessage && (
          <div className="fixed bottom-6 right-6 z-50 bg-[#31302d] text-white px-4 py-2.5 rounded-xl shadow-xl flex items-center gap-2 text-xs font-bold animate-in slide-in-from-bottom-5">
            <span className="material-symbols-outlined text-emerald-400 text-[18px]">check_circle</span>
            <span>{toastMessage}</span>
          </div>
        )}
      </div>

      {/* Footer */}
      <footer className="w-full mt-12 pt-6 border-t border-[#c7c4d8]/40 flex flex-col md:flex-row items-center justify-between gap-3 text-center md:text-left text-xs text-[#464555]">
        <div className="flex items-center gap-1.5 font-semibold">
          <span className="material-symbols-outlined text-[16px]">style</span>
          <span>Sổ Danh Thiếp Điện Tử • Archival Precision</span>
        </div>
        <div>© 2025 Book Name Card. Gìn giữ danh thiếp trang nhã.</div>
      </footer>
    </div>
  )
}
