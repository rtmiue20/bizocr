import { useState, useEffect } from 'react'
import { api } from '../api'
import type { ContactSummary, ContactSearchResult } from '../types'
import ContactDetailModal from './ContactDetailModal'

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

export default function SearchView() {
  const [data, setData] = useState<ContactSearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState('')
  const [industry, setIndustry] = useState('')
  const [page, setPage] = useState(0)
  const [selectedContactId, setSelectedContactId] = useState<number | null>(null)

  const doSearch = () => {
    setLoading(true)
    api.contacts.search({
      q: query.trim() || undefined,
      industry: industry || undefined,
      page,
      size: 18,
    })
      .then(setData)
      .catch(console.error)
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    doSearch()
  }, [page, industry])

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setPage(0)
    doSearch()
  }

  return (
    <div className="w-full flex flex-col gap-6 py-4">
      {/* Title */}
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-extrabold text-[#1E1B4B]">
          Tìm Kiếm Nâng Cao Danh Thiếp
        </h1>
        <p className="text-xs sm:text-sm text-[#464555]">
          Tra cứu nhanh chóng theo họ tên đối tác, tên doanh nghiệp, số điện thoại, email hoặc mã số thuế.
        </p>
      </div>

      {/* Form tìm kiếm */}
      <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-3">
        <div className="relative flex-1">
          <span className="material-symbols-outlined absolute left-3.5 top-3.5 text-[20px] text-[#464555]">
            search
          </span>
          <input
            type="text"
            placeholder="Nhập tên đối tác, công ty, chức danh, SĐT..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="w-full rounded-2xl border border-[#c7c4d8]/60 bg-white pl-11 pr-10 py-3.5 text-sm text-[#1c1c19] placeholder-[#777587] shadow-sm focus:border-[#3525cd] focus:outline-none"
          />
          {query && (
            <button
              type="button"
              onClick={() => { setQuery(''); setPage(0); }}
              className="absolute right-3.5 top-3.5 text-[#777587] hover:text-[#1c1c19]"
            >
              ✕
            </button>
          )}
        </div>

        <select
          value={industry}
          onChange={(e) => { setIndustry(e.target.value); setPage(0); }}
          className="rounded-2xl border border-[#c7c4d8]/60 bg-white px-4 py-3.5 text-sm text-[#1c1c19] shadow-sm focus:border-[#3525cd] focus:outline-none md:w-64 font-medium"
        >
          {INDUSTRIES.map((ind) => (
            <option key={ind.code} value={ind.code}>
              {ind.name}
            </option>
          ))}
        </select>

        <button
          type="submit"
          className="flex items-center justify-center gap-2 rounded-2xl bg-[#1E1B4B] px-6 py-3.5 text-sm font-bold text-[#ffddb8] shadow hover:opacity-95 transition-all cursor-pointer"
        >
          <span className="material-symbols-outlined text-[18px]">search</span>
          <span>Tìm kiếm</span>
        </button>
      </form>

      {/* Kết quả */}
      <div className="flex items-center justify-between text-xs text-[#464555]">
        <span>Tìm thấy: <strong className="text-[#1c1c19]">{data?.totalElements ?? 0} kết quả</strong></span>
      </div>

      {loading ? (
        <div className="flex justify-center py-20 text-[#464555]">
          <span className="material-symbols-outlined text-4xl animate-spin text-[#3525cd]">
            progress_activity
          </span>
        </div>
      ) : data?.items.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-[#c7c4d8] p-12 text-center bg-white text-[#464555]">
          <span className="material-symbols-outlined text-4xl mb-2 text-[#777587]">search_off</span>
          <p className="font-bold text-sm">Không tìm thấy danh thiếp phù hợp</p>
          <p className="text-xs mt-1">Hãy thử với từ khóa ngắn gọn hơn hoặc chọn tất cả ngành nghề.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {data?.items.map((card: ContactSummary) => (
            <div
              key={card.id}
              onClick={() => setSelectedContactId(card.id)}
              className="cursor-pointer rounded-2xl border border-[#c7c4d8]/60 bg-white p-4 shadow-sm hover:border-[#3525cd] hover:shadow-lg transition-all flex flex-col justify-between"
            >
              <div>
                <div className="relative w-full aspect-[1.586/1] rounded-xl overflow-hidden bg-[#1E1B4B] border border-[#c7c4d8]/40 mb-3 flex items-center justify-center">
                  {card.documentId ? (
                    <img
                      src={`/api/v1/documents/${card.documentId}/file`}
                      alt={card.fullName}
                      className="w-full h-full object-contain"
                    />
                  ) : (
                    <span className="material-symbols-outlined text-3xl text-[#ffddb8]/60">badge</span>
                  )}
                </div>

                <h3 className="font-extrabold text-base text-[#1E1B4B] truncate">
                  {card.fullName}
                </h3>
                {card.title && (
                  <p className="text-xs font-semibold text-[#684000] truncate">{card.title}</p>
                )}
                <p className="text-xs text-[#1c1c19] truncate mt-1 flex items-center gap-1">
                  <span className="material-symbols-outlined text-[14px] text-[#3525cd]">domain</span>
                  <span>{card.companyName || 'Doanh nghiệp'}</span>
                </p>
              </div>

              <div className="mt-3 pt-2.5 border-t border-[#f0ede9] text-xs text-[#464555] flex justify-between items-center">
                <span>{card.phone || card.mobile || card.email || '—'}</span>
                <span className="font-bold text-[#3525cd]">Xem chi tiết →</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {selectedContactId != null && (
        <ContactDetailModal
          contactId={selectedContactId}
          onClose={() => setSelectedContactId(null)}
          onUpdated={doSearch}
        />
      )}
    </div>
  )
}
