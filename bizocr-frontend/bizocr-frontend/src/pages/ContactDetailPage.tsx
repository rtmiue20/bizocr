import { useEffect, useState, useRef } from 'react'
import { api } from '../api'
import type { ContactDetailResponse, Contact } from '../types'
import { fmtDate } from '../lib'

interface Props {
    contactId: number
    onBack: () => void
    onUpdated?: () => void
}

export default function ContactDetailPage({ contactId, onBack, onUpdated }: Props) {
    const [detail, setDetail] = useState<ContactDetailResponse | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [editing, setEditing] = useState(false)
    const [form, setForm] = useState<Partial<Contact>>({})
    const [saving, setSaving] = useState(false)
    const [uploadingBack, setUploadingBack] = useState(false)
    const backInputRef = useRef<HTMLInputElement>(null)

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

    const handleSave = async () => {
        try {
            setSaving(true)
            await api.contacts.update(contactId, form)
            setEditing(false)
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

    const handleConfirmMerge = async () => {
        if (!confirm('Bạn có chắc chắn muốn gộp thông tin người này vào danh bạ gốc?')) return
        try {
            await api.contacts.confirmMerge(contactId)
            alert('Đã gộp liên hệ thành công')
            onBack()
            onUpdated?.()
        } catch (err: any) {
            alert(err.message)
        }
    }

    const handleDismissDuplicate = async () => {
        try {
            await api.contacts.dismissDuplicate(contactId)
            fetchDetail()
            onUpdated?.()
        } catch (err: any) {
            alert(err.message)
        }
    }

    const handleUploadBack = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0]
        if (!file || !detail?.contact.documentId) return
        try {
            setUploadingBack(true)
            await api.uploadImage(detail.contact.documentId, 'BACK', file)
            fetchDetail()
        } catch (err: any) {
            alert('Lỗi tải mặt sau: ' + err.message)
        } finally {
            setUploadingBack(false)
            if (backInputRef.current) backInputRef.current.value = ''
        }
    }

    if (loading) {
        return (
            <div className="flex h-64 items-center justify-center text-on-surface-variant">
                Đang tải thông tin chi tiết danh thiếp...
            </div>
        )
    }

    if (error || !detail) {
        return (
            <div className="rounded-xl border border-error bg-error-container p-6 text-on-error-container">
                <p className="font-semibold">Lỗi tải dữ liệu</p>
                <p className="text-sm mt-1">{error || 'Không tìm thấy thông tin'}</p>
                <button onClick={onBack} className="mt-4 rounded-lg bg-surface px-4 py-2 text-sm text-on-surface shadow">
                    Quay lại
                </button>
            </div>
        )
    }

    const { contact, company, images, possibleDuplicateContact } = detail
    const frontImage = images.find(img => img.side === 'FRONT')
    const backImage = images.find(img => img.side === 'BACK')

    return (
        <div className="flex flex-col gap-6 pb-12">
            {/* Header & Nav */}
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <button
                        onClick={onBack}
                        className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-container-high hover:bg-surface-container-highest"
                        title="Quay lại danh bạ"
                    >
                        ←
                    </button>
                    <div>
                        <h1 className="font-headline-md text-2xl font-bold text-primary flex items-center gap-3">
                            {contact.fullName}
                            {contact.title && (
                                <span className="rounded-full bg-secondary-container px-3 py-1 text-xs font-medium text-on-secondary-container">
                                    {contact.title}
                                </span>
                            )}
                        </h1>
                        <p className="text-sm text-on-surface-variant">
                            {company?.legalName || 'Chưa liên kết công ty'}
                        </p>
                    </div>
                </div>

                <div className="flex items-center gap-3">
                    <button
                        onClick={handleMarkContacted}
                        className="rounded-lg bg-tertiary px-4 py-2 text-sm font-semibold text-on-tertiary shadow hover:opacity-90 flex items-center gap-2"
                    >
                        <span>✓</span> Đã liên hệ hôm nay
                    </button>
                    <button
                        onClick={() => setEditing(!editing)}
                        className="rounded-lg border border-outline px-4 py-2 text-sm font-semibold text-on-surface hover:bg-surface-container-low"
                    >
                        {editing ? 'Hủy sửa' : 'Chỉnh sửa'}
                    </button>
                </div>
            </div>

            {/* Cảnh báo nghi trùng lặp (Dedup) */}
            {contact.dedupStatus === 'POSSIBLE_DUPLICATE' && possibleDuplicateContact && (
                <div className="rounded-xl border border-amber-300 bg-amber-50 p-5 text-amber-900 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                    <div className="flex items-start gap-3">
                        <span className="text-2xl">⚠️</span>
                        <div>
                            <p className="font-bold text-base">Phát hiện nghi trùng lặp thông tin</p>
                            <p className="text-sm mt-0.5">
                                Hệ thống nhận thấy danh thiếp này có tên và đơn vị rất giống với liên hệ: <strong>{possibleDuplicateContact.fullName}</strong> ({possibleDuplicateContact.title || 'Không rõ chức vụ'} - {possibleDuplicateContact.phone || possibleDuplicateContact.email || 'Không có liên hệ'}).
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        <button
                            onClick={handleConfirmMerge}
                            className="rounded-lg bg-amber-600 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-700 shadow-sm"
                        >
                            Xác nhận gộp
                        </button>
                        <button
                            onClick={handleDismissDuplicate}
                            className="rounded-lg border border-amber-400 bg-white px-3 py-2 text-sm font-medium text-amber-800 hover:bg-amber-100"
                        >
                            Bác bỏ (Hai người khác nhau)
                        </button>
                    </div>
                </div>
            )}

            {/* Grid 2 cột: Cột trái ảnh thẻ, Cột phải thông tin */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                {/* 1. Phần hình ảnh thẻ (Bảo tồn vật lý, không hao mòn theo thời gian) */}
                <div className="lg:col-span-5 flex flex-col gap-4">
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
                        <h2 className="font-title-md font-bold text-primary mb-3 flex items-center justify-between">
                            <span>Bản scan Namecard vật lý</span>
                            <span className="text-xs font-normal text-on-surface-variant">Lưu trữ kỹ thuật số</span>
                        </h2>

                        {/* Mặt trước */}
                        <div className="mb-4">
                            <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wider block mb-1">
                                Mặt trước
                            </span>
                            <div className="relative aspect-[16/10] w-full overflow-hidden rounded-lg border border-outline-variant bg-surface-container-high flex items-center justify-center">
                                {contact.documentId ? (
                                    <img
                                        src={`/api/v1/documents/${contact.documentId}/file`}
                                        alt="Mặt trước danh thiếp"
                                        className="h-full w-full object-contain"
                                    />
                                ) : (
                                    <div className="text-center text-sm text-on-surface-variant">
                                        Không tìm thấy ảnh mặt trước
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Mặt sau */}
                        <div>
                            <div className="flex items-center justify-between mb-1">
                                <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
                                    Mặt sau
                                </span>
                                <input
                                    type="file"
                                    ref={backInputRef}
                                    accept="image/*"
                                    className="hidden"
                                    onChange={handleUploadBack}
                                />
                                <button
                                    onClick={() => backInputRef.current?.click()}
                                    disabled={uploadingBack || !contact.documentId}
                                    className="text-xs text-primary font-medium hover:underline disabled:opacity-50"
                                >
                                    {uploadingBack ? 'Đang tải lên...' : backImage ? 'Đổi ảnh mặt sau' : '+ Thêm ảnh mặt sau'}
                                </button>
                            </div>

                            <div className="relative aspect-[16/10] w-full overflow-hidden rounded-lg border border-outline-variant bg-surface-container-high flex items-center justify-center">
                                {backImage ? (
                                    <img
                                        src={`/api/v1/documents/${contact.documentId}/file`} // nếu cùng cơ chế file load
                                        alt="Mặt sau danh thiếp"
                                        className="h-full w-full object-contain"
                                    />
                                ) : (
                                    <div className="text-center p-4">
                                        <p className="text-xs text-on-surface-variant mb-2">Chưa lưu ảnh mặt sau</p>
                                        <button
                                            onClick={() => backInputRef.current?.click()}
                                            disabled={uploadingBack}
                                            className="rounded-lg border border-dashed border-outline px-3 py-1.5 text-xs text-primary font-medium hover:bg-surface-container-low"
                                        >
                                            + Tải lên mặt sau
                                        </button>
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Lịch sử kết nối / Metadata */}
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm text-sm">
                        <h3 className="font-semibold text-on-surface mb-2">Thông tin hệ thống</h3>
                        <div className="flex justify-between py-1 border-b border-outline-variant/40">
                            <span className="text-on-surface-variant">Ngày số hóa:</span>
                            <span className="font-medium">{fmtDate(contact.createdAt)}</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-outline-variant/40">
                            <span className="text-on-surface-variant">Liên hệ lần cuối:</span>
                            <span className="font-medium text-primary">
                                {contact.lastContacted ? fmtDate(contact.lastContacted) : 'Chưa có nhật ký'}
                            </span>
                        </div>
                        <div className="flex justify-between py-1">
                            <span className="text-on-surface-variant">Mã tài liệu gốc:</span>
                            <span className="font-medium">#{contact.documentId || '—'}</span>
                        </div>
                    </div>
                </div>

                {/* 2. Cột phải: Thông tin người, công ty và CRM */}
                <div className="lg:col-span-7 flex flex-col gap-6">
                    {/* Thông tin cá nhân */}
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
                        <h2 className="font-title-md font-bold text-primary mb-4">Thông tin người liên hệ</h2>

                        {editing ? (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Họ và tên *</label>
                                    <input
                                        type="text"
                                        value={form.fullName || ''}
                                        onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Chức danh / Vị trí</label>
                                    <input
                                        type="text"
                                        value={form.title || ''}
                                        onChange={(e) => setForm({ ...form, title: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Số điện thoại</label>
                                    <input
                                        type="text"
                                        value={form.phone || ''}
                                        onChange={(e) => setForm({ ...form, phone: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Email</label>
                                    <input
                                        type="email"
                                        value={form.email || ''}
                                        onChange={(e) => setForm({ ...form, email: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Phòng ban</label>
                                    <input
                                        type="text"
                                        value={form.department || ''}
                                        onChange={(e) => setForm({ ...form, department: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Website</label>
                                    <input
                                        type="text"
                                        value={form.website || ''}
                                        onChange={(e) => setForm({ ...form, website: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Chức vụ & Phòng ban</span>
                                    <span className="font-semibold text-on-surface">
                                        {contact.title || 'Chưa cập nhật'} {contact.department ? `(${contact.department})` : ''}
                                    </span>
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Điện thoại liên hệ</span>
                                    {contact.phone || contact.mobile ? (
                                        <a href={`tel:${contact.phone || contact.mobile}`} className="font-semibold text-primary hover:underline">
                                            {contact.phone || contact.mobile}
                                        </a>
                                    ) : (
                                        <span className="text-on-surface-variant italic">Chưa có số</span>
                                    )}
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Hộp thư điện tử</span>
                                    {contact.email ? (
                                        <a href={`mailto:${contact.email}`} className="font-semibold text-primary hover:underline">
                                            {contact.email}
                                        </a>
                                    ) : (
                                        <span className="text-on-surface-variant italic">Chưa có email</span>
                                    )}
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Website</span>
                                    {contact.website ? (
                                        <a href={contact.website.startsWith('http') ? contact.website : `https://${contact.website}`} target="_blank" rel="noreferrer" className="font-semibold text-primary hover:underline">
                                            {contact.website}
                                        </a>
                                    ) : (
                                        <span className="text-on-surface-variant italic">Chưa có website</span>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Thông tin công ty & Tra cứu công khai */}
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
                        <div className="flex items-center justify-between mb-3">
                            <h2 className="font-title-md font-bold text-primary">Thông tin tổ chức / Doanh nghiệp</h2>
                            {company?.verificationStatus && (
                                <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${company.verificationStatus === 'VERIFIED' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-700'}`}>
                                    {company.verificationStatus === 'VERIFIED' ? '✓ Đã đối soát Thuế' : 'Chưa đối soát'}
                                </span>
                            )}
                        </div>

                        {company ? (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                                <div className="md:col-span-2">
                                    <span className="text-xs text-on-surface-variant block">Tên pháp lý công ty</span>
                                    <span className="font-bold text-on-surface text-base">{company.legalName}</span>
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Mã số thuế</span>
                                    <span className="font-semibold">{company.taxCode || 'Chưa có MST'}</span>
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Người đại diện pháp luật</span>
                                    <span className="font-semibold text-primary">{company.legalRepresentative || 'Chưa cập nhật'}</span>
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Lĩnh vực / Ngành nghề</span>
                                    <span className="font-medium">{company.industry || 'Chưa phân loại ngành'}</span>
                                </div>
                                <div>
                                    <span className="text-xs text-on-surface-variant block">Số điện thoại công ty</span>
                                    <span>{company.phone || '—'}</span>
                                </div>
                                <div className="md:col-span-2">
                                    <span className="text-xs text-on-surface-variant block">Địa chỉ trụ sở</span>
                                    <span>{company.address || 'Chưa có địa chỉ'}</span>
                                </div>
                            </div>
                        ) : (
                            <p className="text-sm text-on-surface-variant italic">Chưa liên kết hồ sơ doanh nghiệp.</p>
                        )}
                    </div>

                    {/* Nhật ký quan hệ & CRM nhẹ */}
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
                        <h2 className="font-title-md font-bold text-primary mb-3">Nhật ký gặp gỡ & Ghi chú đối tác</h2>

                        {editing ? (
                            <div className="flex flex-col gap-3 text-sm">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-medium text-on-surface-variant mb-1">Gặp ở đâu / Dịp nào?</label>
                                        <input
                                            type="text"
                                            placeholder="VD: Hội thảo Vietbuild 2026, Cà phê giới thiệu..."
                                            value={form.metAt || ''}
                                            onChange={(e) => setForm({ ...form, metAt: e.target.value })}
                                            className="w-full rounded-lg border border-outline p-2 bg-surface"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-medium text-on-surface-variant mb-1">Ngày gặp gỡ</label>
                                        <input
                                            type="date"
                                            value={form.metDate || ''}
                                            onChange={(e) => setForm({ ...form, metDate: e.target.value })}
                                            className="w-full rounded-lg border border-outline p-2 bg-surface"
                                        />
                                    </div>
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Thẻ phân loại (Tags, cách nhau bởi dấu phẩy)</label>
                                    <input
                                        type="text"
                                        placeholder="VD: VIP, Đối tác tiềm năng, BĐS, Triển vọng..."
                                        value={form.tags || ''}
                                        onChange={(e) => setForm({ ...form, tags: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-on-surface-variant mb-1">Ghi chú chi tiết</label>
                                    <textarea
                                        rows={3}
                                        placeholder="Ghi chú sở thích, nhu cầu hợp tác, dự án trao đổi..."
                                        value={form.notes || ''}
                                        onChange={(e) => setForm({ ...form, notes: e.target.value })}
                                        className="w-full rounded-lg border border-outline p-2 bg-surface"
                                    />
                                </div>
                                <div className="flex justify-end gap-3 mt-2">
                                    <button
                                        onClick={() => setEditing(false)}
                                        className="rounded-lg border border-outline px-4 py-2 font-medium"
                                    >
                                        Hủy
                                    </button>
                                    <button
                                        onClick={handleSave}
                                        disabled={saving}
                                        className="rounded-lg bg-primary px-5 py-2 font-semibold text-on-primary hover:opacity-90"
                                    >
                                        {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div className="flex flex-col gap-3 text-sm">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <span className="text-xs text-on-surface-variant block">Địa điểm / Bối cảnh gặp gỡ</span>
                                        <span className="font-medium text-on-surface">{contact.metAt || 'Chưa ghi chú'}</span>
                                    </div>
                                    <div>
                                        <span className="text-xs text-on-surface-variant block">Ngày gặp</span>
                                        <span className="font-medium text-on-surface">{contact.metDate || '—'}</span>
                                    </div>
                                </div>

                                {contact.tags && (
                                    <div>
                                        <span className="text-xs text-on-surface-variant block mb-1">Tags</span>
                                        <div className="flex flex-wrap gap-1.5">
                                            {contact.tags.split(',').map((tag, idx) => (
                                                <span key={idx} className="rounded-full bg-surface-container-high px-2.5 py-0.5 text-xs text-on-surface">
                                                    #{tag.trim()}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                <div>
                                    <span className="text-xs text-on-surface-variant block mb-1">Ghi chú đối tác</span>
                                    <div className="rounded-lg bg-surface-container-low p-3 text-on-surface whitespace-pre-wrap text-sm">
                                        {contact.notes || 'Chưa có ghi chú nào về liên hệ này.'}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}
