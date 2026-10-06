import { useEffect, useState, useRef } from 'react'
import { api } from '../api'
import type { ContactDetailResponse, Contact } from '../types'

interface Props {
  contactId: number
  onClose: () => void
  onUpdated?: () => void
}

export default function ContactDetailModal({ contactId, onClose, onUpdated }: Props) {
  const [detail, setDetail] = useState<ContactDetailResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isEditing, setIsEditing] = useState(false)
  const [form, setForm] = useState<Partial<Contact>>({})
  const [saving, setSaving] = useState(false)
  const [copiedField, setCopiedField] = useState<string | null>(null)
  const [activeSide, setActiveSide] = useState<'FRONT' | 'BACK'>('FRONT')

  const fetchDetail = () => {
    setLoading(true)
    api.contacts.get(contactId)
      .then((res) => {
        setDetail(res)
        setForm(res.contact)
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchDetail()
  }, [contactId])

  const copyToClipboard = (text: string, fieldName: string) => {
    if (!text) return
    navigator.clipboard.writeText(text)
    setCopiedField(fieldName)
    setTimeout(() => setCopiedField(null), 2000)
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      await api.contacts.update(contactId, form)
      setIsEditing(false)
      fetchDetail()
      onUpdated?.()
    } catch (err: any) {
      alert(err.message)
    } finally {
      setSaving(false)
    }
  }

  const handleMarkContacted = async () => {
    try {
      await api.contacts.markContacted(contactId)
      fetchDetail()
      onUpdated?.()
    } catch (err: any) {
      alert(err.message)
    }
  }

  const contact = detail?.contact
  const company = detail?.company
  const images = detail?.images ?? []
  const frontImage = images.find((i) => i.side === 'FRONT')
  const backImage = images.find((i) => i.side === 'BACK')

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm transition-opacity animate-in fade-in"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-4xl max-h-[92vh] overflow-y-auto rounded-3xl bg-[#fcf9f4] border border-[#c7c4d8]/60 shadow-2xl text-[#1c1c19] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Modal */}
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-[#fcf9f4]/95 backdrop-blur-md border-b border-[#c7c4d8]/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#FFE7A3] via-[#E5B869] to-[#996515] p-[1.5px] shadow-sm flex items-center justify-center shrink-0">
              <div className="w-full h-full rounded-[10px] bg-[#1E1B4B] flex items-center justify-center">
                <span
                  className="material-symbols-outlined text-[#ffddb8] text-[20px]"
                  style={{ fontVariationSettings: "'FILL' 1" }}
                >
                  badge
                </span>
              </div>
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-[#1E1B4B]">
                {contact?.fullName || 'Chi Tiết Danh Thiếp'}
              </h2>
              <p className="text-xs text-[#464555]">
                {company?.legalName || contact?.notes || 'Hồ sơ đối tác doanh nghiệp'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {!loading && !isEditing && (
              <button
                type="button"
                onClick={() => setIsEditing(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#ebe8e3] hover:bg-[#e5e2dd] text-xs font-semibold text-[#1c1c19] transition-all cursor-pointer"
              >
                <span className="material-symbols-outlined text-[16px] text-[#3525cd]">edit</span>
                <span>Chỉnh sửa</span>
              </button>
            )}

            <button
              type="button"
              onClick={onClose}
              className="w-9 h-9 rounded-xl flex items-center justify-center text-[#464555] hover:bg-[#ebe8e3] hover:text-[#1c1c19] transition-all cursor-pointer"
            >
              <span className="material-symbols-outlined text-[20px]">close</span>
            </button>
          </div>
        </div>

        {/* Content Modal */}
        <div className="p-6">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20 gap-3 text-[#464555]">
              <span className="material-symbols-outlined text-4xl text-[#3525cd] animate-spin">
                progress_activity
              </span>
              <p className="text-sm font-medium">Đang tải thông tin chi tiết danh thiếp...</p>
            </div>
          ) : error ? (
            <div className="rounded-2xl bg-[#ffdad6]/40 p-6 text-center text-[#ba1a1a]">
              <span className="material-symbols-outlined text-3xl mb-2">error</span>
              <p className="font-semibold text-sm">{error}</p>
            </div>
          ) : contact ? (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* CỘT TRÁI: Ảnh NameCard thật */}
              <div className="lg:col-span-5 flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-[#464555]">
                    Ảnh Danh Thiếp Gốc
                  </span>
                  {backImage && (
                    <div className="flex rounded-lg bg-[#ebe8e3] p-0.5 text-xs font-bold">
                      <button
                        onClick={() => setActiveSide('FRONT')}
                        className={`px-2.5 py-1 rounded-md transition-all ${
                          activeSide === 'FRONT'
                            ? 'bg-[#1E1B4B] text-[#ffddb8]'
                            : 'text-[#464555]'
                        }`}
                      >
                        Mặt trước
                      </button>
                      <button
                        onClick={() => setActiveSide('BACK')}
                        className={`px-2.5 py-1 rounded-md transition-all ${
                          activeSide === 'BACK'
                            ? 'bg-[#1E1B4B] text-[#ffddb8]'
                            : 'text-[#464555]'
                        }`}
                      >
                        Mặt sau
                      </button>
                    </div>
                  )}
                </div>

                {/* Card Canvas */}
                <div className="relative w-full aspect-[1.586/1] rounded-2xl overflow-hidden border border-[#c7c4d8]/60 bg-gradient-to-br from-[#1E1B4B] to-[#2A2374] shadow-lg flex items-center justify-center group">
                  {contact.documentId ? (
                    <img
                      src={`/api/v1/documents/${contact.documentId}/file`}
                      alt="Ảnh danh thiếp"
                      className="w-full h-full object-contain bg-black/30 group-hover:scale-105 transition-transform duration-300"
                    />
                  ) : (
                    <div className="flex flex-col items-center gap-2 text-[#ffddb8]/80 p-6 text-center">
                      <span className="material-symbols-outlined text-4xl">contact_mail</span>
                      <span className="text-xs">Không có file ảnh gốc đính kèm</span>
                    </div>
                  )}

                  {/* Overlay button xem to */}
                  {contact.documentId && (
                    <a
                      href={`/api/v1/documents/${contact.documentId}/file`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="absolute bottom-3 right-3 flex items-center gap-1 px-3 py-1.5 rounded-xl bg-black/60 hover:bg-black/80 backdrop-blur-md text-white text-xs font-semibold shadow-md transition-all"
                    >
                      <span className="material-symbols-outlined text-[15px]">zoom_in</span>
                      <span>Xem ảnh to</span>
                    </a>
                  )}
                </div>

                {/* Khung trạng thái & Tương tác nhanh */}
                <div className="rounded-2xl bg-[#f0ede9] p-4 border border-[#c7c4d8]/40 flex flex-col gap-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[#464555] font-medium">Trạng thái xác thực:</span>
                    <span className="flex items-center gap-1 font-bold text-[#16A34A] bg-[#16A34A]/10 px-2.5 py-1 rounded-full">
                      <span className="material-symbols-outlined text-[14px]">verified</span>
                      Đã số hóa chuẩn
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[#464555] font-medium">Lần liên hệ gần nhất:</span>
                    <span className="font-semibold text-[#1c1c19]">
                      {contact.lastContacted
                        ? new Date(contact.lastContacted).toLocaleDateString('vi-VN')
                        : 'Chưa liên hệ'}
                    </span>
                  </div>

                  <button
                    type="button"
                    onClick={handleMarkContacted}
                    className="w-full mt-1 flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-[#ebe8e3] hover:bg-[#ded9d2] text-xs font-bold text-[#1E1B4B] transition-all cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[16px] text-[#3525cd]">
                      mark_chat_read
                    </span>
                    <span>Đánh dấu vừa trao đổi / liên hệ</span>
                  </button>
                </div>
              </div>

              {/* CỘT PHẢI: Toàn bộ thông tin chính xác của NameCard */}
              <div className="lg:col-span-7 flex flex-col gap-4">
                {isEditing ? (
                  /* Form chỉnh sửa */
                  <div className="flex flex-col gap-3.5 bg-white p-5 rounded-2xl border border-[#c7c4d8]/60 shadow-sm">
                    <h3 className="text-sm font-bold text-[#3525cd] uppercase tracking-wider mb-1">
                      Hiệu chỉnh thông tin thẻ
                    </h3>

                    <div>
                      <label className="block text-xs font-bold text-[#464555] mb-1">
                        Họ và tên
                      </label>
                      <input
                        type="text"
                        value={form.fullName ?? ''}
                        onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                        className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                      />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-bold text-[#464555] mb-1">
                          Chức danh / Vị trí
                        </label>
                        <input
                          type="text"
                          value={form.title ?? ''}
                          onChange={(e) => setForm({ ...form, title: e.target.value })}
                          className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-[#464555] mb-1">
                          Phòng ban / Đơn vị
                        </label>
                        <input
                          type="text"
                          value={form.department ?? ''}
                          onChange={(e) => setForm({ ...form, department: e.target.value })}
                          className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-bold text-[#464555] mb-1">
                          Số điện thoại
                        </label>
                        <input
                          type="text"
                          value={form.phone ?? form.mobile ?? ''}
                          onChange={(e) =>
                            setForm({ ...form, phone: e.target.value, mobile: e.target.value })
                          }
                          className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-[#464555] mb-1">Email</label>
                        <input
                          type="email"
                          value={form.email ?? ''}
                          onChange={(e) => setForm({ ...form, email: e.target.value })}
                          className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                        />
                      </div>
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-[#464555] mb-1">Website</label>
                      <input
                        type="text"
                        value={form.website ?? ''}
                        onChange={(e) => setForm({ ...form, website: e.target.value })}
                        className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-[#464555] mb-1">
                        Ghi chú / Nơi gặp gỡ
                      </label>
                      <textarea
                        rows={2}
                        value={form.notes ?? ''}
                        onChange={(e) => setForm({ ...form, notes: e.target.value })}
                        className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] px-3.5 py-2 text-sm focus:border-[#3525cd] focus:outline-none"
                        placeholder="Ví dụ: Gặp tại Hội nghị Xúc tiến Đầu tư..."
                      />
                    </div>

                    <div className="flex items-center justify-end gap-3 pt-2">
                      <button
                        type="button"
                        onClick={() => setIsEditing(false)}
                        className="px-4 py-2 rounded-xl border border-[#c7c4d8] text-xs font-semibold hover:bg-[#ebe8e3] transition-all cursor-pointer"
                      >
                        Hủy
                      </button>
                      <button
                        type="button"
                        onClick={handleSave}
                        disabled={saving}
                        className="flex items-center gap-1.5 px-5 py-2 rounded-xl bg-[#3525cd] text-white text-xs font-bold shadow hover:opacity-90 transition-all cursor-pointer"
                      >
                        {saving ? (
                          <>
                            <span className="material-symbols-outlined text-[16px] animate-spin">
                              progress_activity
                            </span>
                            <span>Đang lưu...</span>
                          </>
                        ) : (
                          <>
                            <span className="material-symbols-outlined text-[16px]">check</span>
                            <span>Lưu thay đổi</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                ) : (
                  /* Hiển thị chi tiết dạng Key - Value cao cấp */
                  <div className="flex flex-col gap-4">
                    {/* Header profile info */}
                    <div className="rounded-2xl bg-white p-5 border border-[#c7c4d8]/50 shadow-sm flex flex-col gap-1.5">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <h1 className="text-xl sm:text-2xl font-extrabold text-[#1E1B4B]">
                            {contact.fullName}
                          </h1>
                          <p className="text-sm font-semibold text-[#684000] mt-0.5">
                            {contact.title || 'Chưa cập nhật chức danh'}
                            {contact.department ? ` • ${contact.department}` : ''}
                          </p>
                        </div>

                        {company?.taxCode && (
                          <div className="flex flex-col items-end">
                            <span className="text-[10px] uppercase font-bold text-[#464555]">
                              Mã số thuế
                            </span>
                            <span className="font-mono font-bold text-xs bg-[#ebe8e3] px-2 py-0.5 rounded text-[#1E1B4B]">
                              {company.taxCode}
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Công ty */}
                      <div className="flex items-center gap-2 mt-2 pt-2 border-t border-[#f0ede9] text-xs text-[#1c1c19]">
                        <span className="material-symbols-outlined text-[#3525cd] text-[18px]">
                          domain
                        </span>
                        <strong className="text-sm text-[#1E1B4B]">
                          {company?.legalName || 'Doanh nghiệp'}
                        </strong>
                      </div>

                      {company?.industry && (
                        <div className="flex items-center gap-2 text-xs text-[#464555]">
                          <span className="material-symbols-outlined text-[#684000] text-[16px]">
                            work
                          </span>
                          <span>Ngành nghề: {company.industry}</span>
                        </div>
                      )}
                    </div>

                    {/* Danh sách trường liên hệ (Key - Value) */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {/* SĐT */}
                      <div className="rounded-2xl bg-white p-4 border border-[#c7c4d8]/40 shadow-sm flex items-center justify-between">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-9 h-9 rounded-xl bg-[#3525cd]/10 flex items-center justify-center shrink-0">
                            <span className="material-symbols-outlined text-[#3525cd] text-[18px]">
                              call
                            </span>
                          </div>
                          <div className="flex flex-col min-w-0">
                            <span className="text-[10px] uppercase tracking-wider text-[#464555] font-bold">
                              Điện thoại
                            </span>
                            <a
                              href={`tel:${contact.phone || contact.mobile}`}
                              className="text-xs sm:text-sm font-bold text-[#1E1B4B] hover:underline truncate"
                            >
                              {contact.phone || contact.mobile || '—'}
                            </a>
                          </div>
                        </div>

                        {(contact.phone || contact.mobile) && (
                          <button
                            type="button"
                            onClick={() =>
                              copyToClipboard(contact.phone || contact.mobile || '', 'phone')
                            }
                            className="p-1.5 rounded-lg hover:bg-[#ebe8e3] text-[#464555] cursor-pointer"
                            title="Sao chép"
                          >
                            <span className="material-symbols-outlined text-[16px]">
                              {copiedField === 'phone' ? 'check' : 'content_copy'}
                            </span>
                          </button>
                        )}
                      </div>

                      {/* Email */}
                      <div className="rounded-2xl bg-white p-4 border border-[#c7c4d8]/40 shadow-sm flex items-center justify-between">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-9 h-9 rounded-xl bg-[#684000]/10 flex items-center justify-center shrink-0">
                            <span className="material-symbols-outlined text-[#684000] text-[18px]">
                              mail
                            </span>
                          </div>
                          <div className="flex flex-col min-w-0">
                            <span className="text-[10px] uppercase tracking-wider text-[#464555] font-bold">
                              Email
                            </span>
                            <a
                              href={`mailto:${contact.email}`}
                              className="text-xs sm:text-sm font-bold text-[#1E1B4B] hover:underline truncate"
                            >
                              {contact.email || '—'}
                            </a>
                          </div>
                        </div>

                        {contact.email && (
                          <button
                            type="button"
                            onClick={() => copyToClipboard(contact.email || '', 'email')}
                            className="p-1.5 rounded-lg hover:bg-[#ebe8e3] text-[#464555] cursor-pointer"
                            title="Sao chép"
                          >
                            <span className="material-symbols-outlined text-[16px]">
                              {copiedField === 'email' ? 'check' : 'content_copy'}
                            </span>
                          </button>
                        )}
                      </div>

                      {/* Website */}
                      <div className="rounded-2xl bg-white p-4 border border-[#c7c4d8]/40 shadow-sm flex items-center justify-between sm:col-span-2">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 flex items-center justify-center shrink-0">
                            <span className="material-symbols-outlined text-emerald-600 text-[18px]">
                              language
                            </span>
                          </div>
                          <div className="flex flex-col min-w-0">
                            <span className="text-[10px] uppercase tracking-wider text-[#464555] font-bold">
                              Website
                            </span>
                            {contact.website ? (
                              <a
                                href={
                                  contact.website.startsWith('http')
                                    ? contact.website
                                    : `https://${contact.website}`
                                }
                                target="_blank"
                                rel="noreferrer"
                                className="text-xs sm:text-sm font-bold text-emerald-700 hover:underline truncate"
                              >
                                {contact.website}
                              </a>
                            ) : (
                              <span className="text-xs text-[#464555] font-medium">—</span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Địa chỉ công ty */}
                      <div className="rounded-2xl bg-white p-4 border border-[#c7c4d8]/40 shadow-sm flex items-start gap-3 sm:col-span-2">
                        <div className="w-9 h-9 rounded-xl bg-amber-500/10 flex items-center justify-center shrink-0 mt-0.5">
                          <span className="material-symbols-outlined text-amber-600 text-[18px]">
                            location_on
                          </span>
                        </div>
                        <div className="flex flex-col">
                          <span className="text-[10px] uppercase tracking-wider text-[#464555] font-bold">
                            Địa chỉ trụ sở / Văn phòng
                          </span>
                          <span className="text-xs sm:text-sm text-[#1c1c19] mt-0.5 leading-relaxed">
                            {company?.address || '—'}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Ghi chú & Thẻ tag */}
                    {contact.notes && (
                      <div className="rounded-2xl bg-[#ebe8e3]/70 p-4 border border-[#c7c4d8]/30">
                        <span className="text-[10px] uppercase font-bold text-[#464555] block mb-1">
                          Ghi chú sự kiện / Gặp gỡ
                        </span>
                        <p className="text-xs text-[#1c1c19] italic">{contact.notes}</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
