import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Doc, DocPage, ContactSummary, ContactSearchResult } from '../types'
import ContactDetailPage from './ContactDetailPage'
import { fmtDate } from '../lib'

const INDUSTRIES = [
    { code: '', name: 'Tất cả ngành nghề' },
    { code: '6201', name: 'Công nghệ thông tin / Phần mềm' },
    { code: '4100', name: 'Xây dựng & Kiến trúc' },
    { code: '6810', name: 'Bất động sản' },
    { code: '4600', name: 'Thương mại & Bán buôn' },
    { code: '7020', name: 'Tư vấn quản lý & Dịch vụ' },
    { code: '8610', name: 'Y tế & Chăm sóc sức khỏe' },
    { code: '6419', name: 'Tài chính - Ngân hàng' },
]

export default function ArchivePage() {
    const [activeTab, setActiveTab] = useState<'NAMECARD' | 'DOCUMENTS'>('NAMECARD')

    // Namecard State
    const [cardData, setCardData] = useState<ContactSearchResult | null>(null)
    const [cardPage, setCardPage] = useState(0)
    const [cardQuery, setCardQuery] = useState('')
    const [cardIndustry, setCardIndustry] = useState('')
    const [cardLoading, setCardLoading] = useState(false)
    const [selectedContactId, setSelectedContactId] = useState<number | null>(null)

    // Document State (chứng từ cũ)
    const [docData, setDocData] = useState<DocPage | null>(null)
    const [docPage, setDocPage] = useState(0)
    const [docLoading, setDocLoading] = useState(false)

    // Load Namecards
    const fetchContacts = () => {
        setCardLoading(true)
        api.contacts.search({
            q: cardQuery.trim() || undefined,
            industry: cardIndustry || undefined,
            page: cardPage,
            size: 12,
        })
            .then(setCardData)
            .catch((err) => alert(err.message))
            .finally(() => setCardLoading(false))
    }

    useEffect(() => {
        if (activeTab === 'NAMECARD') {
            fetchContacts()
        }
    }, [cardPage, cardIndustry, activeTab])

    const handleSearchSubmit = (e: React.FormEvent) => {
        e.preventDefault()
        setCardPage(0)
        fetchContacts()
    }

    // Load Documents
    useEffect(() => {
        if (activeTab === 'DOCUMENTS') {
            setDocLoading(true)
            api.list({ page: docPage, size: 10 })
                .then(setDocData)
                .catch((err) => alert(err.message))
                .finally(() => setDocLoading(false))
        }
    }, [docPage, activeTab])

    // Nếu đang xem chi tiết một Contact
    if (selectedContactId != null) {
        return (
            <ContactDetailPage
                contactId={selectedContactId}
                onBack={() => setSelectedContactId(null)}
                onUpdated={() => fetchContacts()}
            />
        )
    }

    return (
        <div className="flex flex-col gap-6">
            {/* Header & Tabs */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-outline-variant pb-4">
                <div>
                    <h1 className="font-headline-md text-2xl font-bold text-primary">
                        Lưu trữ & Tra cứu Danh thiếp
                    </h1>
                    <p className="text-sm text-on-surface-variant mt-1">
                        Thay thế cuốn sổ namecard vật lý — Bảo quản không hao mòn, số hóa trích xuất dữ liệu và kết nối thông minh.
                    </p>
                </div>

                <div className="flex rounded-lg bg-surface-container-high p-1 text-sm font-medium">
                    <button
                        onClick={() => setActiveTab('NAMECARD')}
                        className={`rounded-md px-4 py-2 transition-colors ${activeTab === 'NAMECARD' ? 'bg-surface text-primary shadow-sm font-bold' : 'text-on-surface-variant hover:text-on-surface'}`}
                    >
                        📇 Sổ Namecard ({cardData?.totalElements ?? '...'})
                    </button>
                    <button
                        onClick={() => setActiveTab('DOCUMENTS')}
                        className={`rounded-md px-4 py-2 transition-colors ${activeTab === 'DOCUMENTS' ? 'bg-surface text-primary shadow-sm font-bold' : 'text-on-surface-variant hover:text-on-surface'}`}
                    >
                        📄 Tất cả Chứng từ ({docData?.totalElements ?? '...'})
                    </button>
                </div>
            </div>

            {/* TAB 1: SỔ NAMECARD SỐ HÓA */}
            {activeTab === 'NAMECARD' && (
                <div className="flex flex-col gap-6">
                    {/* Thanh tìm kiếm & Lọc */}
                    <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-3">
                        <div className="relative flex-1">
                            <input
                                type="text"
                                placeholder="🔍 Tìm theo Tên người, Tên công ty, SĐT, Email, chức vụ..."
                                value={cardQuery}
                                onChange={(e) => setCardQuery(e.target.value)}
                                className="w-full rounded-xl border border-outline bg-surface-container-lowest px-4 py-3 text-sm shadow-sm focus:border-primary focus:outline-none"
                            />
                            {cardQuery && (
                                <button
                                    type="button"
                                    onClick={() => { setCardQuery(''); setCardPage(0); }}
                                    className="absolute right-3 top-3 text-xs text-on-surface-variant hover:text-on-surface"
                                >
                                    ✕
                                </button>
                            )}
                        </div>

                        <select
                            value={cardIndustry}
                            onChange={(e) => { setCardIndustry(e.target.value); setCardPage(0); }}
                            className="rounded-xl border border-outline bg-surface-container-lowest px-4 py-3 text-sm shadow-sm focus:border-primary focus:outline-none md:w-64"
                        >
                            {INDUSTRIES.map((ind) => (
                                <option key={ind.code} value={ind.code}>
                                    {ind.name}
                                </option>
                            ))}
                        </select>

                        <button
                            type="submit"
                            className="rounded-xl bg-primary px-6 py-3 text-sm font-semibold text-on-primary shadow hover:opacity-90"
                        >
                            Tìm kiếm
                        </button>
                    </form>

                    {/* Danh sách Thẻ Namecard dạng Grid Card */}
                    {cardLoading ? (
                        <div className="flex h-48 items-center justify-center text-on-surface-variant">
                            Đang tải danh bạ thẻ...
                        </div>
                    ) : cardData?.items.length === 0 ? (
                        <div className="rounded-xl border border-dashed border-outline-variant p-12 text-center text-on-surface-variant bg-surface-container-lowest">
                            <span className="text-4xl block mb-2">📇</span>
                            <p className="font-semibold text-base">Chưa tìm thấy danh thiếp nào phù hợp</p>
                            <p className="text-sm mt-1">Hãy thử tìm với từ khóa khác hoặc quét thêm thẻ mới ở bước 1.</p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                            {cardData?.items.map((c: ContactSummary) => (
                                <div
                                    key={c.id}
                                    onClick={() => setSelectedContactId(c.id)}
                                    className="group relative cursor-pointer rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-all hover:border-primary hover:shadow-md flex flex-col justify-between"
                                >
                                    <div>
                                        {/* Header Card: Thumbnail thẻ & Cờ */}
                                        <div className="flex items-start justify-between gap-3 mb-3">
                                            <div className="h-14 w-20 shrink-0 overflow-hidden rounded-md border border-outline-variant bg-surface-container-high">
                                                {c.documentId ? (
                                                    <img
                                                        src={`/api/v1/documents/${c.documentId}/file`}
                                                        alt="Thẻ"
                                                        className="h-full w-full object-cover group-hover:scale-105 transition-transform"
                                                    />
                                                ) : (
                                                    <div className="flex h-full w-full items-center justify-center text-xs text-on-surface-variant">
                                                        Card
                                                    </div>
                                                )}
                                            </div>

                                            <div className="flex flex-col items-end gap-1">
                                                {c.dedupStatus === 'POSSIBLE_DUPLICATE' && (
                                                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">
                                                        ⚠️ Nghi trùng
                                                    </span>
                                                )}
                                                {c.title && (
                                                    <span className="rounded-md bg-secondary-container px-2 py-0.5 text-xs text-on-secondary-container truncate max-w-[140px]">
                                                        {c.title}
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {/* Tên người & Công ty */}
                                        <h3 className="font-bold text-base text-primary group-hover:underline">
                                            {c.fullName}
                                        </h3>
                                        <p className="text-sm font-medium text-on-surface line-clamp-1 mt-0.5">
                                            {c.companyName || 'Chưa liên kết công ty'}
                                        </p>

                                        {/* Liên hệ ngắn */}
                                        <div className="mt-3 flex flex-col gap-1 text-xs text-on-surface-variant">
                                            {c.phone && (
                                                <div className="flex items-center gap-1.5">
                                                    <span>📞</span>
                                                    <span>{c.phone}</span>
                                                </div>
                                            )}
                                            {c.email && (
                                                <div className="flex items-center gap-1.5 truncate">
                                                    <span>✉️</span>
                                                    <span className="truncate">{c.email}</span>
                                                </div>
                                            )}
                                            {c.metAt && (
                                                <div className="flex items-center gap-1.5 text-tertiary">
                                                    <span>📍</span>
                                                    <span className="truncate">Gặp tại: {c.metAt}</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>

                                    {/* Footer Card */}
                                    <div className="mt-4 pt-3 border-t border-outline-variant/40 flex items-center justify-between text-[11px] text-on-surface-variant">
                                        <span>
                                            {c.lastContacted ? `Liên hệ: ${fmtDate(c.lastContacted)}` : 'Chưa liên hệ'}
                                        </span>
                                        <span className="font-medium text-primary group-hover:translate-x-0.5 transition-transform">
                                            Chi tiết →
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Phân trang Namecard */}
                    {cardData && cardData.totalPages > 1 && (
                        <div className="flex items-center justify-center gap-4 mt-2">
                            <button
                                disabled={cardData.page === 0}
                                onClick={() => setCardPage((p) => Math.max(0, p - 1))}
                                className="rounded-lg bg-surface-container-high px-4 py-2 text-sm disabled:opacity-50"
                            >
                                Trang trước
                            </button>
                            <span className="text-sm font-medium">
                                Trang {cardData.page + 1} / {cardData.totalPages}
                            </span>
                            <button
                                disabled={cardData.page >= cardData.totalPages - 1}
                                onClick={() => setCardPage((p) => p + 1)}
                                className="rounded-lg bg-surface-container-high px-4 py-2 text-sm disabled:opacity-50"
                            >
                                Trang sau
                            </button>
                        </div>
                    )}
                </div>
            )}

            {/* TAB 2: TẤT CẢ CHỨNG TỪ (BẢNG TRUYỀN THỐNG) */}
            {activeTab === 'DOCUMENTS' && (
                <div className="flex flex-col gap-4">
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
                        {docLoading ? (
                            <p className="text-on-surface-variant">Đang tải dữ liệu...</p>
                        ) : (
                            <table className="w-full text-left font-body-md text-sm">
                                <thead>
                                    <tr className="border-b border-outline-variant text-on-surface-variant">
                                        <th className="pb-3">ID</th>
                                        <th className="pb-3">Tên file</th>
                                        <th className="pb-3">Loại tài liệu</th>
                                        <th className="pb-3">Trạng thái</th>
                                        <th className="pb-3">Thời gian nhận</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {docData?.items.map((doc: Doc) => (
                                        <tr key={doc.id} className="border-b border-outline-variant/40 last:border-0 hover:bg-surface-container-low">
                                            <td className="py-3 font-semibold">#{doc.id}</td>
                                            <td className="py-3 font-medium">{doc.fileName}</td>
                                            <td className="py-3">
                                                <span className="rounded-md bg-surface-container-high px-2 py-1 text-xs">
                                                    {doc.documentType || 'Chưa phân loại'}
                                                </span>
                                            </td>
                                            <td className="py-3">
                                                <span className="rounded-full bg-primary-container px-2.5 py-1 text-xs font-semibold text-on-primary-container">
                                                    {doc.status}
                                                </span>
                                            </td>
                                            <td className="py-3 text-xs text-on-surface-variant">
                                                {doc.receivedAt ? fmtDate(doc.receivedAt) : '—'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        )}
                    </div>

                    {docData && docData.totalPages > 1 && (
                        <div className="flex items-center justify-center gap-4">
                            <button
                                disabled={docData.page === 0}
                                onClick={() => setDocPage((p) => Math.max(0, p - 1))}
                                className="rounded-lg bg-surface-container-high px-4 py-2 text-sm disabled:opacity-50"
                            >
                                Trang trước
                            </button>
                            <span className="text-sm">
                                Trang {docData.page + 1} / {docData.totalPages}
                            </span>
                            <button
                                disabled={docData.page >= docData.totalPages - 1}
                                onClick={() => setDocPage((p) => p + 1)}
                                className="rounded-lg bg-surface-container-high px-4 py-2 text-sm disabled:opacity-50"
                            >
                                Trang sau
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    )
}