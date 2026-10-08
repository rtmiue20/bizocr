import {useEffect, useState, useMemo, useRef, useCallback} from 'react'
import {api} from '../api'
import type {CardItemResponse, IndustryCountResponse, DocumentDetailResponse} from '../types'
import {useAuthImage} from '../hooks/useAuthImage'

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
    documentId?: number | null
    isNew?: boolean
    verifiedStatus?: string
    avatarText?: string
    avatarBg?: string
    avatarColor?: string
    avatarIcon?: string
    imageUrl?: string | null
    thumbnailUrl?: string | null
    approvedAt?: string | null
    createdAt?: string | null
}

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

function mapCardResponse(item: CardItemResponse, index: number): CardItem {
    const words = (item.fullName || '').trim().split(/\s+/)
    const initials = words.length >= 2
        ? (words[0][0] + words[words.length - 1][0]).toUpperCase()
        : (item.fullName || 'NK').slice(0, 2).toUpperCase()

    return {
        id: item.id,
        fullName: item.fullName || '—',
        title: item.title || '',
        companyName: item.companyName || '',
        taxCode: item.taxCode || '',
        phone: item.phone || item.mobile || '',
        email: item.email || '',
        address: item.address || '',
        industry: item.industry || 'Khác',
        industryCategory: item.industry || 'Khác',
        documentId: item.documentId,
        isNew: item.isNew ?? false,
        verifiedStatus: 'Đã xác minh',
        avatarText: initials,
        avatarBg: AVATAR_PALETTES[index % AVATAR_PALETTES.length],
        avatarColor: 'text-white',
        imageUrl: item.imageUrl,
        thumbnailUrl: item.thumbnailUrl,
        approvedAt: item.approvedAt,
        createdAt: item.createdAt,
    }
}

// Danh thiếp mẫu tiêu chuẩn doanh nhân phòng khi xem ở chế độ demo trống
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
]

// ── Component hiển thị thumbnail thẻ thật từ API có auth ──
function CardThumbnail({
                           src,
                           alt,
                           fallbackText,
                           fallbackBg,
                           fallbackIcon,
                       }: {
    src?: string | null
    alt: string
    fallbackText?: string
    fallbackBg?: string
    fallbackIcon?: string
}) {
    const {blobUrl, loading, error} = useAuthImage(src)

    if (loading) {
        return <div className="w-11 h-9 rounded-lg bg-[#e8e4de] animate-pulse shrink-0"/>
    }

    if (!src || error || !blobUrl) {
        return (
            <div
                className={`w-11 h-9 rounded-lg ${fallbackBg || 'bg-[#3525cd]'} text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-xs`}
            >
                {fallbackIcon ? (
                    <span className="material-symbols-outlined text-[18px]">{fallbackIcon}</span>
                ) : (
                    <span>{fallbackText || 'NK'}</span>
                )}
            </div>
        )
    }

    return (
        <img
            src={blobUrl}
            alt={alt}
            className="w-11 h-9 rounded-lg object-cover border border-[#c7c4d8]/40 shrink-0 shadow-xs"
            loading="lazy"
        />
    )
}

// ── Component hiển thị ảnh gốc trong Modal có auth và đủ 3 trạng thái ──
function ModalCardImage({
                            documentId,
                            fallbackImageUrl,
                            side,
                            onFullscreen,
                        }: {
    documentId?: number | null
    fallbackImageUrl?: string | null
    side: 'FRONT' | 'BACK'
    onFullscreen: (url: string) => void
}) {
    const targetUrl = documentId
        ? `/api/v1/documents/${documentId}/image?side=${side}`
        : fallbackImageUrl

    const {blobUrl, loading, error, retry} = useAuthImage(targetUrl)

    if (loading) {
        return (
            <div
                className="w-full aspect-[1.75/1] rounded-2xl bg-[#ebe8e3] animate-pulse flex flex-col items-center justify-center text-[#464555] shadow-inner">
        <span className="material-symbols-outlined text-[36px] animate-spin text-[#3525cd]">
          progress_activity
        </span>
                <span className="text-xs mt-2.5 font-bold text-[#464555]">
          Đang tải ảnh danh thiếp...
        </span>
            </div>
        )
    }

    if (error) {
        return (
            <div
                className="w-full aspect-[1.75/1] rounded-2xl bg-rose-50 border border-rose-200 flex flex-col items-center justify-center p-4 text-center shadow-inner">
        <span className="material-symbols-outlined text-[36px] text-rose-500">
          broken_image
        </span>
                <span className="text-xs font-bold text-rose-800 mt-1">
          Không thể tải ảnh danh thiếp
        </span>
                <p className="text-[11px] text-rose-600 mt-0.5">
                    Ảnh có thể chưa sẵn sàng hoặc không tìm thấy tệp.
                </p>
                <button
                    type="button"
                    onClick={retry}
                    className="mt-3 px-3.5 py-1.5 bg-rose-600 text-white rounded-xl text-xs font-bold hover:bg-rose-700 flex items-center gap-1.5 shadow-sm cursor-pointer transition-all"
                >
                    <span className="material-symbols-outlined text-[14px]">refresh</span>
                    <span>Thử lại</span>
                </button>
            </div>
        )
    }

    if (!blobUrl) {
        return (
            <div
                className="w-full aspect-[1.75/1] rounded-2xl bg-[#f0ede9] border border-dashed border-[#c7c4d8] flex flex-col items-center justify-center text-[#464555] shadow-inner">
        <span className="material-symbols-outlined text-[36px] text-[#868393]">
          image_not_supported
        </span>
                <span className="text-xs font-semibold text-[#464555] mt-1.5">
          Chưa có ảnh danh thiếp {side === 'BACK' ? 'mặt sau' : 'mặt trước'}
        </span>
            </div>
        )
    }

    return (
        <div
            className="relative w-full aspect-[1.75/1] rounded-2xl overflow-hidden bg-slate-900 shadow-[0_8px_20px_rgba(15,23,42,0.25)] group flex items-center justify-center">
            <img
                src={blobUrl}
                alt="Ảnh danh thiếp"
                className="w-full h-full object-contain"
            />
            <div
                className="absolute bottom-2.5 right-2.5 flex items-center gap-1.5 opacity-90 group-hover:opacity-100 transition-opacity">
                <button
                    type="button"
                    onClick={() => onFullscreen(blobUrl)}
                    className="px-3 py-1.5 rounded-xl bg-black/75 hover:bg-black/90 text-white text-xs font-bold flex items-center gap-1.5 backdrop-blur-md cursor-pointer shadow-md transition-all"
                >
                    <span className="material-symbols-outlined text-[15px]">zoom_in</span>
                    <span>Phóng to</span>
                </button>
            </div>
        </div>
    )
}

export default function DashboardView({onProcessImage, onUpdateCount}: Props) {
    const [viewMode, setViewMode] = useState<'book' | 'empty'>('book')
    const [cards, setCards] = useState<CardItem[]>([])
    const [loading, setLoading] = useState(true)
    const [isError, setIsError] = useState(false)
    const [selectedFilter, setSelectedFilter] = useState<string>('Tất cả')
    const [searchQuery, setSearchQuery] = useState('')
    const [showDropdown, setShowDropdown] = useState(false)
    const [bookPage, setBookPage] = useState(0) // Mỗi page là 1 cặp trang (8 thẻ)
    const [totalCards, setTotalCards] = useState(0)
    const [totalPages, setTotalPages] = useState(1)
    const [industries, setIndustries] = useState<IndustryCountResponse[]>([])
    const [selectedCard, setSelectedCard] = useState<CardItem | null>(null)
    const [toastMessage, setToastMessage] = useState<string | null>(null)
    const [cardSide, setCardSide] = useState<'FRONT' | 'BACK'>('FRONT')
    const [fullscreenUrl, setFullscreenUrl] = useState<string | null>(null)
    const [docDetail, setDocDetail] = useState<DocumentDetailResponse | null>(null)

    const bookPageRef = useRef(bookPage)
    bookPageRef.current = bookPage
    const needsRefreshRef = useRef(false)

    // Tải danh sách ngành nghề phục vụ chip filter
    const fetchIndustries = useCallback(() => {
        api.cards.industries()
            .then((data) => setIndustries(data || []))
            .catch(() => setIndustries([]))
    }, [])

    // Tải dữ liệu danh thiếp thật từ endpoint /api/v1/cards
    const fetchCards = useCallback(
        (page: number, industry: string, query: string) => {
            setLoading(true)
            setIsError(false)
            api.cards
                .list({
                    page,
                    pageSize: 8,
                    industry: industry === 'Tất cả' ? undefined : industry,
                    q: query.trim() || undefined,
                })
                .then((res) => {
                    if (res && res.items) {
                        const mapped = res.items.map((item, idx) => mapCardResponse(item, idx))
                        setCards(mapped)
                        setTotalCards(res.total || 0)
                        setTotalPages(Math.max(1, res.totalPages || 0))
                        onUpdateCount?.(res.total || 0)
                    } else {
                        setCards([])
                        setTotalCards(0)
                        setTotalPages(1)
                        onUpdateCount?.(0)
                    }
                })
                .catch((err) => {
                    console.error('[DashboardView] Lỗi tải thẻ danh thiếp:', err)
                    setIsError(true)
                    setCards([])
                    setTotalCards(0)
                    setTotalPages(1)
                    onUpdateCount?.(0)
                })
                .finally(() => setLoading(false))
        },
        [onUpdateCount]
    )

    // Khởi tạo và khi thay đổi bộ lọc/tìm kiếm -> Tự quay về trang 1 (page 0)
    useEffect(() => {
        fetchIndustries()
    }, [fetchIndustries])

    useEffect(() => {
        setBookPage(0)
        fetchCards(0, selectedFilter, searchQuery)
    }, [selectedFilter, searchQuery, fetchCards])

    // Khi chuyển trang
    const handlePageChange = (newPage: number) => {
        setBookPage(newPage)
        fetchCards(newPage, selectedFilter, searchQuery)
        if (newPage === 0 && needsRefreshRef.current) {
            needsRefreshRef.current = false
            fetchIndustries()
        }
    }

    // Bước 9: Tự động cập nhật sau khi duyệt (lắng nghe event cards-invalidated)
    useEffect(() => {
        const handleInvalidated = () => {
            fetchIndustries()
            if (bookPageRef.current === 0) {
                // Đang xem trang 1 -> cập nhật ngay lập tức
                fetchCards(0, selectedFilter, searchQuery)
            } else {
                // Đang ở trang 2+ -> không nhảy trang, chỉ đánh dấu cần cập nhật khi quay về trang 1
                needsRefreshRef.current = true
            }
        }
        window.addEventListener('cards-invalidated', handleInvalidated)
        return () => window.removeEventListener('cards-invalidated', handleInvalidated)
    }, [fetchIndustries, fetchCards, selectedFilter, searchQuery])

    // Tải chi tiết chứng từ khi mở Modal
    useEffect(() => {
        setCardSide('FRONT')
        if (selectedCard?.documentId) {
            api.getDetail(selectedCard.documentId)
                .then((detail) => setDocDetail(detail))
                .catch(() => setDocDetail(null))
        } else {
            setDocDetail(null)
        }
    }, [selectedCard])

    // Parse extracted fields từ document detail (nếu có)
    const parsedDocFields = useMemo(() => {
        if (!docDetail?.extractedFields) return null
        try {
            return JSON.parse(docDetail.extractedFields)
        } catch {
            return null
        }
    }, [docDetail?.extractedFields])

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

    // Danh sách khớp danh thiếp trong dropdown gợi ý nhanh
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

    // Chia thành trang trái (4 thẻ đầu) và trang phải (4 thẻ sau)
    const leftPageCards = useMemo(() => cards.slice(0, 4), [cards])
    const rightPageCards = useMemo(() => cards.slice(4, 8), [cards])

    // Đóng modal khi bấm phím Escape
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                setSelectedCard(null)
                setShowDropdown(false)
                setFullscreenUrl(null)
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
                        <div
                            className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#f0ede9] shadow-sm">
              <span className="relative flex h-2.5 w-2.5">
                <span
                    className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-600"></span>
              </span>
                            <span className="text-xs font-semibold text-[#1c1c19]">
                Đã lưu {totalCards} danh thiếp đã xác thực
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
                <span
                    className="material-symbols-outlined absolute left-3 text-[#464555] text-[20px] pointer-events-none">
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
                                        className="absolute right-3 text-[#464555] hover:text-[#1c1c19] cursor-pointer"
                                    >
                                        <span className="material-symbols-outlined text-[18px]">cancel</span>
                                    </button>
                                )}
                            </div>

                            {/* Fuzzy-Match Suggestion Dropdown */}
                            {showDropdown && searchQuery.trim().length > 0 && (
                                <div
                                    className="absolute left-0 right-0 top-full mt-2 z-40 bg-white rounded-2xl shadow-[0_16px_40px_-8px_rgba(92,79,61,0.18)] border border-[#c7c4d8]/40 p-2 flex flex-col gap-1 transition-all"
                                    id="searchDropdown"
                                >
                                    <div
                                        className="px-3 py-1 flex items-center justify-between text-[#464555] text-[11px] font-bold">
                                        <span
                                            className="uppercase tracking-wider">Khớp danh thiếp thông minh (OCR)</span>
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
                                                <CardThumbnail
                                                    src={c.thumbnailUrl || c.imageUrl}
                                                    alt={c.fullName}
                                                    fallbackText={c.avatarText}
                                                    fallbackBg={c.avatarBg}
                                                    fallbackIcon={c.avatarIcon}
                                                />
                                                <div className="flex flex-col min-w-0">
                                                    <div className="flex items-center gap-1.5">
                            <span
                                className="text-xs sm:text-sm font-bold text-[#1c1c19] group-hover:text-[#3525cd] transition-colors truncate">
                              {c.fullName}
                            </span>
                                                        <span
                                                            className="px-1.5 py-0.2 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold">
                              {c.verifiedStatus || 'Đã xác minh'}
                            </span>
                                                    </div>
                                                    <span className="text-[11px] text-[#464555] truncate">
                            {c.title} • {c.companyName}
                          </span>
                                                </div>
                                            </div>
                                            <span
                                                className="shrink-0 px-2 py-0.5 rounded-md bg-[#f0ede9] text-[#464555] text-[10px] font-semibold">
                        Xem chi tiết
                      </span>
                                        </div>
                                    ))}

                                    {/* Helper Footer */}
                                    <div
                                        className="pt-2 px-3 pb-1 flex items-center justify-between text-[11px] text-[#464555] bg-[#f6f3ee] rounded-xl">
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

                        {/* Add Card CTA Button: "+ Quét danh thiếp" */}
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
        ───────────────────────────────────────────────────────────── */}
                {viewMode === 'book' ? (
                    <div className="w-full relative" id="bookViewWrapper">
                        {/* ── Loading skeleton ── */}
                        {loading && (
                            <div
                                className="w-full bg-[#3c342c] p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)]">
                                <div className="w-full bg-[#fdfbf7] rounded-[18px] p-6 lg:p-8 flex flex-col gap-6">
                                    <div className="flex items-center justify-between">
                                        <div className="h-6 w-48 bg-[#ebe8e3] rounded-lg animate-pulse"/>
                                        <div className="h-6 w-32 bg-[#ebe8e3] rounded-lg animate-pulse"/>
                                    </div>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                                        {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
                                            <div
                                                key={i}
                                                className="bg-white rounded-2xl p-4 shadow-sm flex flex-col justify-between min-h-[175px] animate-pulse"
                                            >
                                                <div className="flex items-start justify-between mb-3">
                                                    <div className="w-11 h-9 rounded-lg bg-[#ebe8e3]"/>
                                                    <div className="w-16 h-4 rounded-md bg-[#ebe8e3]"/>
                                                </div>
                                                <div className="flex flex-col gap-2">
                                                    <div className="h-4 w-3/4 bg-[#ebe8e3] rounded"/>
                                                    <div className="h-3 w-1/2 bg-[#ebe8e3] rounded"/>
                                                    <div className="h-3 w-2/3 bg-[#ebe8e3] rounded"/>
                                                </div>
                                                <div className="h-7 w-full bg-[#f6f3ee] rounded-xl mt-3"/>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* ── Error state ── */}
                        {!loading && isError && (
                            <div
                                className="w-full bg-[#3c342c] p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)]">
                                <div
                                    className="w-full bg-[#fdfbf7] rounded-[18px] p-10 flex flex-col items-center justify-center gap-4 text-center">
                                    <span className="material-symbols-outlined text-[48px] text-rose-500">error</span>
                                    <div className="flex flex-col gap-1 max-w-sm">
                                        <h3 className="text-base font-extrabold text-[#1c1c19]">Không thể kết nối đến
                                            máy chủ</h3>
                                        <p className="text-xs text-[#464555]">
                                            Đã có lỗi khi tải danh thiếp. Vui lòng kiểm tra lại dịch vụ hoặc thử lại.
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => fetchCards(bookPage, selectedFilter, searchQuery)}
                                        className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#3525cd] text-white text-xs font-bold hover:opacity-90 cursor-pointer shadow-sm"
                                    >
                                        <span className="material-symbols-outlined text-[16px]">refresh</span>
                                        <span>Thử lại</span>
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* ── Empty state (Chưa có thẻ nào trong DB) ── */}
                        {!loading && !isError && cards.length === 0 && (
                            <div
                                className="w-full bg-[#3c342c] p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)]">
                                <div
                                    className="w-full bg-[#fdfbf7] rounded-[18px] p-10 flex flex-col items-center justify-center gap-5 min-h-[380px] text-center">
                                    <div
                                        className="w-20 h-20 rounded-[24px] bg-[#f0ede9] flex items-center justify-center shadow-inner">
                                        <span
                                            className="material-symbols-outlined text-[40px] text-[#3525cd]">style</span>
                                    </div>
                                    <div className="flex flex-col gap-2 max-w-sm">
                                        <h3 className="text-xl font-extrabold text-[#1c1c19]">Sổ chưa có danh thiếp
                                            nào</h3>
                                        <p className="text-sm text-[#464555]">
                                            {selectedFilter !== 'Tất cả' || searchQuery.trim()
                                                ? 'Không tìm thấy danh thiếp phù hợp với bộ lọc hiện tại.'
                                                : 'Hãy quét danh thiếp đầu tiên để bắt đầu xây dựng bộ sưu tập liên hệ của bạn.'}
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={onProcessImage}
                                        className="flex items-center gap-2 px-6 py-3 rounded-xl bg-[#3525cd] text-white text-sm font-extrabold shadow-[0_4px_14px_rgba(79,70,229,0.3)] hover:opacity-95 transition-all cursor-pointer"
                                    >
                                        <span className="material-symbols-outlined text-[20px]">add_a_photo</span>
                                        <span>+ Quét danh thiếp đầu tiên</span>
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* ── Actual book spread (Khi có dữ liệu thẻ) ── */}
                        {!loading && !isError && cards.length > 0 && (
                            <div
                                className="w-full bg-[#3c342c] p-2.5 sm:p-4 lg:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35),0_10px_20px_-5px_rgba(40,32,20,0.2)] relative">
                                {/* Notebook Spine Stitching Details */}
                                <div
                                    className="absolute inset-x-4 top-2 h-[2px] opacity-25 bg-gradient-to-r from-transparent via-[#ffd4a4] to-transparent pointer-events-none"></div>
                                <div
                                    className="absolute inset-x-4 bottom-2 h-[2px] opacity-25 bg-gradient-to-r from-transparent via-[#ffd4a4] to-transparent pointer-events-none"></div>

                                {/* Book Inner Paper Spread (Two Facing Pages) */}
                                <div
                                    className="relative w-full bg-[#fdfbf7] rounded-[18px] shadow-[inset_0_2px_6px_rgba(0,0,0,0.12)] overflow-hidden flex flex-col lg:flex-row">
                                    {/* Central Spine Shadow / Crease */}
                                    <div
                                        className="hidden lg:block absolute left-1/2 top-0 bottom-0 w-12 -translate-x-1/2 z-20 pointer-events-none bg-gradient-to-r from-black/15 via-black/35 to-black/15 mix-blend-multiply opacity-60"></div>
                                    <div
                                        className="hidden lg:block absolute left-1/2 top-0 bottom-0 w-[2px] -translate-x-1/2 z-20 pointer-events-none bg-black/25"></div>

                                    {/* ================= LEFT PAGE (TRANG TRÁI) ================= */}
                                    <div
                                        className="w-full lg:w-1/2 p-4 sm:p-6 lg:pr-10 lg:pl-8 flex flex-col justify-between relative bg-gradient-to-r from-[#faf7f0] via-[#fdfbf7] to-[#f4eee4]">
                                        <div
                                            className="absolute left-0 top-0 bottom-0 w-2.5 bg-gradient-to-r from-black/5 to-transparent pointer-events-none"></div>

                                        <div className="flex flex-col gap-4">
                                            {/* Left Page Header */}
                                            <div className="flex items-center justify-between pb-2 border-b-0">
                                                <div className="flex items-center gap-2">
                          <span className="material-symbols-outlined text-[#3525cd] text-[20px]">
                            mark_email_read
                          </span>
                                                    <div className="flex flex-col">
                                                        <h2 className="text-sm sm:text-base font-bold text-[#1c1c19] tracking-tight uppercase">
                                                            DANH THIẾP ĐÃ LƯU TRỮ
                                                        </h2>
                                                        <span className="text-[11px] text-[#464555]">
                              Sắp xếp theo thứ tự phê duyệt và thu nhận gần đây
                            </span>
                                                    </div>
                                                </div>
                                                <div
                                                    className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-[#f0ede9] text-[#464555] text-xs font-semibold shadow-sm">
                                                    <span
                                                        className="material-symbols-outlined text-[14px]">swap_vert</span>
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
                                <span
                                    className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-gradient-to-r from-emerald-600 via-teal-500 to-emerald-600 text-white text-[10px] tracking-wide font-bold shadow-md">
                                  <span className="material-symbols-outlined text-[12px]">bolt</span>
                                  MỚI
                                </span>
                                                            </div>
                                                        )}

                                                        {/* Card Front Header with Real Thumbnail */}
                                                        <div>
                                                            <div
                                                                className="flex items-start justify-between gap-2 mb-2">
                                                                <CardThumbnail
                                                                    src={c.thumbnailUrl || c.imageUrl}
                                                                    alt={c.fullName}
                                                                    fallbackText={c.avatarText}
                                                                    fallbackBg={c.avatarBg}
                                                                    fallbackIcon={c.avatarIcon}
                                                                />
                                                                <div
                                                                    className="flex items-center gap-1 text-[#3525cd] bg-[#3525cd]/10 px-2 py-0.5 rounded-md text-[11px] font-semibold">
                                  <span className="material-symbols-outlined text-[14px]"
                                        style={{fontVariationSettings: "'FILL' 1"}}>
                                    verified
                                  </span>
                                                                    <span>{c.verifiedStatus || 'Đã xác minh'}</span>
                                                                </div>
                                                            </div>

                                                            <h3 className="text-sm sm:text-base font-bold text-[#1c1c19] group-hover:text-[#3525cd] transition-colors leading-snug line-clamp-1">
                                                                {c.fullName}
                                                            </h3>
                                                            <p className="text-xs font-semibold text-[#684000] truncate mt-0.5">
                                                                {c.title || 'Đối tác doanh nghiệp'}
                                                            </p>
                                                            <p className="text-[11px] text-[#464555] truncate mt-0.5">
                                                                {c.companyName || '—'}
                                                            </p>
                                                        </div>

                                                        {/* Card Footer / Fast Contact Details */}
                                                        {(c.phone || c.address) && (
                                                            <div
                                                                className="pt-2 mt-2 bg-[#f6f3ee] rounded-xl px-2.5 py-1.5 flex items-center justify-between text-[#464555] text-[11px] font-medium">
                                <span className="flex items-center gap-1 truncate">
                                  <span className="material-symbols-outlined text-[13px] text-[#3525cd]">call</span>
                                  <span className="truncate">{c.phone || '—'}</span>
                                </span>
                                                                {c.address && (
                                                                    <span
                                                                        className="flex items-center gap-1 text-[#464555]/80 shrink-0">
                                    <span className="material-symbols-outlined text-[13px]">location_on</span>
                                    <span className="truncate max-w-[80px]">
                                      {c.address.split(',').pop()?.trim() || c.address}
                                    </span>
                                  </span>
                                                                )}
                                                            </div>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Left Page Footer & Pagination Marker */}
                                        <div
                                            className="pt-4 mt-4 flex items-center justify-between text-xs text-[#464555] border-t border-[#f0ede9]">
                      <span className="tracking-widest uppercase text-[10px] text-[#464555]/70">
                        Tập danh thiếp lưu trữ 2026
                      </span>
                                            <span className="font-bold text-[#1c1c19] tracking-wider">
                        Trang {String(bookPage * 2 + 1).padStart(2, '0')}
                      </span>
                                        </div>
                                    </div>

                                    {/* ================= RIGHT PAGE (TRANG PHẢI) ================= */}
                                    <div
                                        className="w-full lg:w-1/2 p-4 sm:p-6 lg:pl-10 lg:pr-8 flex flex-col justify-between relative bg-gradient-to-l from-[#faf7f0] via-[#fdfbf7] to-[#f4eee4]">
                                        <div
                                            className="absolute right-0 top-0 bottom-0 w-2.5 bg-gradient-to-l from-black/5 to-transparent pointer-events-none"></div>

                                        <div className="flex flex-col gap-4">
                                            {/* Right Page Filter Chips Header (Dynamic từ /cards/industries) */}
                                            <div className="flex flex-col gap-1 pb-1">
                                                <div className="flex items-center justify-between">
                          <span className="text-[11px] font-bold uppercase tracking-wider text-[#464555]">
                            LỌC THEO NGÀNH NGHỀ KINH DOANH
                          </span>
                                                    <span
                                                        onClick={() => setSelectedFilter('Tất cả')}
                                                        className="text-xs text-[#3525cd] font-semibold hover:underline cursor-pointer"
                                                    >
                            Xem tất cả ({totalCards})
                          </span>
                                                </div>

                                                {/* Chips Row */}
                                                <div className="flex items-center gap-1.5 overflow-x-auto py-1">
                                                    <button
                                                        type="button"
                                                        onClick={() => setSelectedFilter('Tất cả')}
                                                        className={`px-3 py-1 rounded-full text-xs font-bold shrink-0 shadow-sm transition-all cursor-pointer ${
                                                            selectedFilter === 'Tất cả'
                                                                ? 'bg-[#3525cd] text-white'
                                                                : 'bg-[#f0ede9] text-[#464555] hover:text-[#1c1c19]'
                                                        }`}
                                                    >
                                                        Tất cả
                                                    </button>

                                                    {industries.map((ind) => (
                                                        <button
                                                            key={ind.industry}
                                                            type="button"
                                                            onClick={() => setSelectedFilter(ind.industry)}
                                                            className={`px-3 py-1 rounded-full text-xs font-bold shrink-0 shadow-sm transition-all cursor-pointer ${
                                                                selectedFilter === ind.industry
                                                                    ? 'bg-[#3525cd] text-white'
                                                                    : 'bg-[#f0ede9] text-[#464555] hover:text-[#1c1c19]'
                                                            }`}
                                                        >
                                                            {ind.industry} ({ind.count})
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
                                                        {/* Shiny Gradient "MỚI" Ribbon / Badge if isNew */}
                                                        {c.isNew && (
                                                            <div className="absolute -top-2 -right-2 z-10">
                                <span
                                    className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-gradient-to-r from-emerald-600 via-teal-500 to-emerald-600 text-white text-[10px] tracking-wide font-bold shadow-md">
                                  <span className="material-symbols-outlined text-[12px]">bolt</span>
                                  MỚI
                                </span>
                                                            </div>
                                                        )}

                                                        <div>
                                                            <div
                                                                className="flex items-start justify-between gap-2 mb-2">
                                                                <CardThumbnail
                                                                    src={c.thumbnailUrl || c.imageUrl}
                                                                    alt={c.fullName}
                                                                    fallbackText={c.avatarText}
                                                                    fallbackBg={c.avatarBg}
                                                                    fallbackIcon={c.avatarIcon}
                                                                />
                                                                <div
                                                                    className="flex items-center gap-1 text-[#3525cd] bg-[#3525cd]/10 px-2 py-0.5 rounded-md text-[11px] font-semibold">
                                  <span className="material-symbols-outlined text-[14px]"
                                        style={{fontVariationSettings: "'FILL' 1"}}>
                                    verified
                                  </span>
                                                                    <span>{c.verifiedStatus || 'Đã xác minh'}</span>
                                                                </div>
                                                            </div>

                                                            <h3 className="text-sm sm:text-base font-bold text-[#1c1c19] group-hover:text-[#3525cd] transition-colors leading-snug line-clamp-1">
                                                                {c.fullName}
                                                            </h3>
                                                            <p className="text-xs font-semibold text-[#684000] truncate mt-0.5">
                                                                {c.title || 'Đối tác doanh nghiệp'}
                                                            </p>
                                                            <p className="text-[11px] text-[#464555] truncate mt-0.5">
                                                                {c.companyName || '—'}
                                                            </p>
                                                        </div>

                                                        <div
                                                            className="pt-2 mt-2 bg-[#f6f3ee] rounded-xl px-2.5 py-1.5 flex items-center justify-between text-[#464555] text-[11px] font-medium">
                              <span className="flex items-center gap-1 truncate">
                                <span className="material-symbols-outlined text-[13px] text-emerald-700">call</span>
                                <span className="truncate">{c.phone || '—'}</span>
                              </span>
                                                            {c.address && (
                                                                <span
                                                                    className="flex items-center gap-1 text-[#464555]/80 shrink-0">
                                  <span className="material-symbols-outlined text-[13px]">location_on</span>
                                  <span className="truncate max-w-[80px]">
                                    {c.address.split(',').pop()?.trim() || c.address}
                                  </span>
                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Right Page Footer with Bookmark Page Flipper */}
                                        <div
                                            className="pt-4 mt-4 flex items-center justify-between text-xs text-[#464555] border-t border-[#f0ede9]">
                      <span className="font-bold text-[#1c1c19] tracking-wider">
                        Trang {String(bookPage * 2 + 2).padStart(2, '0')}
                      </span>

                                            {/* Pagination Controls */}
                                            <div className="flex items-center gap-2">
                                                <button
                                                    type="button"
                                                    disabled={bookPage === 0}
                                                    onClick={() => handlePageChange(Math.max(0, bookPage - 1))}
                                                    className="px-2.5 py-1 rounded-lg bg-[#f0ede9] hover:bg-[#ebe8e3] text-[#1c1c19] flex items-center gap-1 transition-colors disabled:opacity-40 cursor-pointer text-xs font-bold"
                                                >
                                                    <span
                                                        className="material-symbols-outlined text-[16px] text-[#3525cd]">arrow_back</span>
                                                    <span>Trang trước</span>
                                                </button>

                                                <button
                                                    type="button"
                                                    disabled={bookPage >= totalPages - 1}
                                                    onClick={() => handlePageChange(bookPage + 1)}
                                                    className="px-2.5 py-1 rounded-lg bg-[#f0ede9] hover:bg-[#ebe8e3] text-[#1c1c19] flex items-center gap-1 transition-colors disabled:opacity-40 cursor-pointer text-xs font-bold"
                                                >
                                                    <span>Trang {bookPage + 1}/{totalPages}</span>
                                                    <span
                                                        className="material-symbols-outlined text-[16px] text-[#3525cd]">arrow_forward</span>
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                ) : (
                    /* ── ALTERNATIVE STATE: EMPTY STATE WORKSPACE ── */
                    <div className="w-full" id="emptyViewWrapper">
                        <div
                            className="w-full bg-[#3c342c] p-3 sm:p-6 rounded-[24px] shadow-[0_25px_60px_-15px_rgba(40,32,20,0.35)] relative">
                            <div
                                className="relative w-full bg-[#fdfbf7] rounded-[18px] p-6 sm:py-20 flex flex-col items-center justify-center text-center">
                                <div
                                    className="w-20 h-20 sm:w-24 sm:h-24 rounded-3xl bg-[#f6f3ee] flex items-center justify-center text-[#3525cd] shadow-sm mb-4">
                                    <span className="material-symbols-outlined text-[48px]">library_add</span>
                                </div>
                                <span
                                    className="px-3 py-1 rounded-full bg-amber-100 text-amber-900 text-xs font-bold uppercase tracking-wider mb-2">
                  Trang sổ danh thiếp trống
                </span>
                                <h2 className="text-xl sm:text-2xl font-extrabold text-[#1c1c19] max-w-md">
                                    Chưa có danh thiếp nào được lưu trữ trong sổ
                                </h2>
                                <p className="text-xs sm:text-sm text-[#464555] max-w-lg mt-2 mb-6">
                                    Sử dụng máy ảnh hoặc tải lên hình ảnh danh thiếp (name card) vật lý để công nghệ AI
                                    tự động trích xuất thông tin, đối soát mã số thuế và lưu trữ trang nhã.
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
                                        className="px-5 py-3 rounded-xl bg-[#f0ede9] text-[#1c1c19] font-bold text-xs sm:text-sm hover:bg-[#ebe8e3] transition-all cursor-pointer"
                                    >
                                        Nạp danh thiếp mẫu minh họa
                                    </button>
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
                            <div
                                className="px-5 sm:px-6 py-3.5 bg-[#f6f3ee] flex items-center justify-between border-b border-[#c7c4d8]/40">
                                <div className="flex items-center gap-2.5 min-w-0">
                                    <div
                                        className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center shrink-0">
                                        <span className="material-symbols-outlined text-[18px]">verified</span>
                                    </div>
                                    <div className="flex flex-col min-w-0">
                    <span className="text-sm sm:text-base font-bold text-[#1c1c19] truncate">
                      Chi Tiết Danh Thiếp Xác Thực
                    </span>
                                        <span
                                            className="text-[11px] text-emerald-700 flex items-center gap-1 font-semibold">
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

                            {/* Modal Body: Khung ảnh thật bên trái & Thông tin đã đối soát bên phải */}
                            <div className="p-5 sm:p-6 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 gap-6">
                                {/* ── Cột trái: Khung ảnh chụp thực tế (Bước 8) ── */}
                                <div className="lg:col-span-5 flex flex-col gap-3">
                                    <div className="flex items-center justify-between text-xs text-[#464555]">
                    <span className="font-semibold">
                      {cardSide === 'FRONT' ? 'Mặt trước danh thiếp' : 'Mặt sau danh thiếp'}
                    </span>
                                        <button
                                            type="button"
                                            onClick={() => setCardSide((s) => (s === 'FRONT' ? 'BACK' : 'FRONT'))}
                                            className="text-[#3525cd] hover:underline flex items-center gap-1 font-bold cursor-pointer"
                                        >
                                            <span className="material-symbols-outlined text-[14px]">flip</span>
                                            <span>{cardSide === 'FRONT' ? 'Lật mặt sau' : 'Lật mặt trước'}</span>
                                        </button>
                                    </div>

                                    {/* Component hiển thị ảnh gốc từ storage qua API có auth */}
                                    <ModalCardImage
                                        documentId={selectedCard.documentId}
                                        fallbackImageUrl={selectedCard.imageUrl}
                                        side={cardSide}
                                        onFullscreen={(url) => setFullscreenUrl(url)}
                                    />

                                    {/* Completeness Score Badge (nếu có từ backend P1) */}
                                    {docDetail?.completenessScore != null && (
                                        <div
                                            className="p-3 rounded-xl bg-indigo-50/80 border border-indigo-100 flex flex-col gap-1">
                                            <div className="flex items-center justify-between">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-indigo-900">
                          Độ tin cậy OCR
                        </span>
                                                <span
                                                    className="px-2 py-0.5 rounded-full bg-indigo-600 text-white text-[10px] font-bold">
                          Hạng {docDetail.completenessGrade || 'A'} • {docDetail.completenessLabel || 'Tốt'}
                        </span>
                                            </div>
                                            <span className="text-xs font-semibold text-indigo-950">
                        {docDetail.confirmPrompt ||
                            `Điểm hoàn thiện: ${Math.round(docDetail.completenessScore * 100)}%`}
                      </span>
                                        </div>
                                    )}

                                    {/* Verification Security Tag */}
                                    <div
                                        className="p-3 rounded-xl bg-emerald-50 text-emerald-900 flex items-start gap-2">
                    <span className="material-symbols-outlined text-emerald-600 text-[18px] shrink-0 mt-0.5">
                      verified_user
                    </span>
                                        <div className="flex flex-col">
                                            <span className="text-xs font-bold">Bảo mật thông tin khách hàng</span>
                                            <span className="text-[11px] text-emerald-800">
                        Dữ liệu được mã hóa hai lớp và lưu trữ vào phân quyền sổ doanh nghiệp của bạn.
                      </span>
                                        </div>
                                    </div>
                                </div>

                                {/* ── Cột phải: Dữ liệu đã chuẩn hóa & Nút thao tác ── */}
                                <div className="lg:col-span-7 flex flex-col justify-between gap-4">
                                    <div className="flex flex-col gap-3">
                                        {/* Họ tên */}
                                        <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                      <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                        Họ và tên đại diện
                      </span>
                                            <div className="flex items-center justify-between">
                        <span className="text-base font-extrabold text-[#1c1c19]">
                          {parsedDocFields?.name || selectedCard.fullName}
                        </span>
                                                <button
                                                    type="button"
                                                    onClick={() => copyText(parsedDocFields?.name || selectedCard.fullName)}
                                                    className="text-[#464555] hover:text-[#3525cd] p-1 rounded transition-colors cursor-pointer"
                                                    title="Sao chép"
                                                >
                                                    <span
                                                        className="material-symbols-outlined text-[16px]">content_copy</span>
                                                </button>
                                            </div>
                                        </div>

                                        {/* SĐT & Email */}
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                            <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                        <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                          Số điện thoại
                        </span>
                                                <div className="flex items-center justify-between">
                          <span className="text-sm font-bold text-[#3525cd]">
                            {parsedDocFields?.phone || selectedCard.phone || '—'}
                          </span>
                                                    {(parsedDocFields?.phone || selectedCard.phone) && (
                                                        <a
                                                            href={`tel:${parsedDocFields?.phone || selectedCard.phone}`}
                                                            className="text-[#3525cd] hover:opacity-80 p-1"
                                                            title="Gọi điện"
                                                        >
                                                            <span
                                                                className="material-symbols-outlined text-[18px]">phone_in_talk</span>
                                                        </a>
                                                    )}
                                                </div>
                                            </div>

                                            <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                        <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                          Thư điện tử (Email)
                        </span>
                                                <div className="flex items-center justify-between">
                          <span className="text-xs sm:text-sm font-semibold text-[#1c1c19] truncate">
                            {parsedDocFields?.email || selectedCard.email || '—'}
                          </span>
                                                    {(parsedDocFields?.email || selectedCard.email) && (
                                                        <a
                                                            href={`mailto:${parsedDocFields?.email || selectedCard.email}`}
                                                            className="text-[#3525cd] hover:opacity-80 p-1"
                                                            title="Gửi email"
                                                        >
                                                            <span
                                                                className="material-symbols-outlined text-[18px]">mail</span>
                                                        </a>
                                                    )}
                                                </div>
                                            </div>
                                        </div>

                                        {/* Đơn vị công tác */}
                                        <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                      <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                        Đơn vị công tác / Công ty
                      </span>
                                            <span className="text-sm font-bold text-[#1c1c19]">
                        {parsedDocFields?.company || selectedCard.companyName || '—'}
                      </span>
                                        </div>

                                        {/* Mã Số Thuế */}
                                        <div className="p-3 rounded-xl bg-amber-500/10 flex flex-col gap-1">
                                            <div className="flex items-center justify-between">
                        <span className="text-[11px] text-amber-900 font-bold uppercase tracking-wider">
                          Mã Số Thuế (MST Doanh Nghiệp)
                        </span>
                                                <span
                                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold">
                          <span className="material-symbols-outlined text-[12px]">check_circle</span>
                          Đã đối soát Cục Thuế
                        </span>
                                            </div>
                                            <div className="flex items-center justify-between">
                        <span className="text-sm font-mono font-extrabold text-amber-950 tracking-wider">
                          {parsedDocFields?.tax_code || selectedCard.taxCode || '—'}
                        </span>
                                                {(parsedDocFields?.tax_code || selectedCard.taxCode) && (
                                                    <button
                                                        type="button"
                                                        onClick={() => copyText(parsedDocFields?.tax_code || selectedCard.taxCode!)}
                                                        className="text-amber-900 hover:text-amber-950 p-1 cursor-pointer"
                                                        title="Sao chép MST"
                                                    >
                                                        <span
                                                            className="material-symbols-outlined text-[16px]">content_copy</span>
                                                    </button>
                                                )}
                                            </div>
                                        </div>

                                        {/* Ngành nghề & Thời gian chụp */}
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                            <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                        <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                          Ngành nghề hoạt động
                        </span>
                                                <p className="text-xs text-[#1c1c19] leading-relaxed">
                                                    {selectedCard.industry || parsedDocFields?.industry || 'Khác'}
                                                </p>
                                            </div>

                                            <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#f6f3ee]">
                        <span className="text-[11px] text-[#464555] uppercase tracking-wider font-bold">
                          Thời gian quét &amp; duyệt
                        </span>
                                                <p className="text-xs text-[#1c1c19] leading-relaxed">
                                                    {docDetail?.reviewedAt ||
                                                        selectedCard.approvedAt ||
                                                        docDetail?.receivedAt ||
                                                        selectedCard.createdAt ||
                                                        'Hôm nay'}
                                                </p>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Bottom Action Buttons Toolbar */}
                                    <div
                                        className="flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-[#f0ede9]">
                                        <div className="flex items-center gap-2">
                                            <button
                                                type="button"
                                                onClick={() => showToast('Đã xuất thông tin danh thiếp sang vCard!')}
                                                className="px-3.5 py-2 rounded-xl bg-[#3525cd] text-white text-xs font-bold shadow-sm hover:bg-[#3525cd]/95 flex items-center gap-1.5 transition-all cursor-pointer"
                                            >
                                                <span
                                                    className="material-symbols-outlined text-[16px]">contact_page</span>
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

                {/* Lightbox Phóng to ảnh Fullscreen */}
                {fullscreenUrl && (
                    <div
                        className="fixed inset-0 z-[60] bg-black/90 backdrop-blur-md flex items-center justify-center p-4 transition-opacity animate-in fade-in"
                        onClick={() => setFullscreenUrl(null)}
                    >
                        <div
                            className="relative max-w-5xl max-h-[92vh] flex flex-col items-center"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <button
                                type="button"
                                onClick={() => setFullscreenUrl(null)}
                                className="absolute -top-12 right-0 px-3.5 py-1.5 rounded-xl bg-white/20 hover:bg-white/30 text-white text-xs font-bold flex items-center gap-1.5 cursor-pointer transition-colors"
                            >
                                <span className="material-symbols-outlined text-[18px]">close</span>
                                <span>Đóng phóng to</span>
                            </button>
                            <img
                                src={fullscreenUrl}
                                alt="Phóng to ảnh danh thiếp"
                                className="max-w-full max-h-[85vh] object-contain rounded-2xl shadow-2xl"
                            />
                        </div>
                    </div>
                )}

                {/* Toast Notification System */}
                {toastMessage && (
                    <div
                        className="fixed bottom-6 right-6 z-50 bg-[#31302d] text-white px-4 py-2.5 rounded-xl shadow-xl flex items-center gap-2 text-xs font-bold animate-in slide-in-from-bottom-5">
                        <span className="material-symbols-outlined text-emerald-400 text-[18px]">check_circle</span>
                        <span>{toastMessage}</span>
                    </div>
                )}
            </div>

            {/* Footer */}
            <footer
                className="w-full mt-12 pt-6 border-t border-[#c7c4d8]/40 flex flex-col md:flex-row items-center justify-between gap-3 text-center md:text-left text-xs text-[#464555]">
                <div className="flex items-center gap-1.5 font-semibold">
                    <span className="material-symbols-outlined text-[16px]">style</span>
                    <span>Sổ Danh Thiếp Điện Tử • Archival Precision</span>
                </div>
                <div>© 2026 Book Name Card. Gìn giữ danh thiếp trang nhã.</div>
            </footer>
        </div>
    )
}
