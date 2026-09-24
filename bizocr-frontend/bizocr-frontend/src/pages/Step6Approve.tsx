import {useEffect, useState} from 'react'
import {api, download} from '../api'
import DocSelect from '../components/DocSelect'
import {whoami} from '../layout/Shell'
import {RE, STATUS_CLS, digits, keyOf, parseFields, pick, rules, show} from '../lib'
import {STATUS_LABEL, type Doc} from '../types'

export default function Step6Approve({docId, onSelect, onBack}: {
    docId: number | null;
    onSelect: (id: number) => void;
    onBack: () => void
}) {
    const [doc, setDoc] = useState<Doc | null>(null)
    const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
    const [busy, setBusy] = useState(false)
    const me = whoami()

    async function load(id: number) {
        setDoc(await api.get(id))
    }

    useEffect(() => {
        if (docId == null) return
        setDoc(null);
        setMsg(null)
        load(docId).catch((e) => setMsg({ok: false, text: e.message}))
    }, [docId])

    const fields = parseFields(doc?.extractedFields)
    const rs = rules(fields)
    const bad = rs.filter((r) => r.ok === false)
    // Vẫn giữ logic lấy tên công ty/địa chỉ lên thẻ Highlight ở trên
    const seller = pick(fields, RE.company) || show(fields['company_name']) || show(fields['companyName']) || show(fields['contact_name'])
    const address = pick(fields, RE.address) || show(fields['address'])
    const mstK = keyOf(fields, RE.mst) || (fields['tax_code'] !== undefined ? 'tax_code' : (fields['taxCode'] !== undefined ? 'taxCode' : undefined))
    const totK = keyOf(fields, RE.total) || (fields['total_amount'] !== undefined ? 'total_amount' : (fields['totalAmount'] !== undefined ? 'totalAmount' : undefined))

    const total = totK ? show(fields[totK]) : ''
    const locked = doc?.status === 'APPROVED'
    const pending = doc?.status === 'PENDING'

    async function act(fn: () => Promise<unknown>, ok: string) {
        if (docId == null) return
        setBusy(true);
        setMsg(null)
        try {
            await fn();
            await load(docId);
            setMsg({ok: true, text: ok})
        } catch (e) {
            setMsg({ok: false, text: (e as Error).message})
        } finally {
            setBusy(false)
        }
    }

    const approve = () => act(() => api.approve(docId!, doc?.extractedFields ?? '{}'), 'Đã phê duyệt và khóa chứng từ')
    const reject = () => act(() => api.update(docId!, {status: 'REJECTED'}), 'Đã từ chối chứng từ')
    const exportXlsx = async () => {
        try {
            download(await api.exportXlsx(), 'bao_cao_ke_toan.xlsx')
        } catch (e) {
            setMsg({ok: false, text: (e as Error).message})
        }
    }

    const legal = [
        ['Mã số thuế doanh nghiệp', (mstK && show(fields[mstK])) || '—'],
        ['Loại chứng từ', doc?.documentType ?? 'Chưa nhận diện'],
    ]

    return (
        <div className="flex w-full flex-col">
            <div className="mb-space-lg flex flex-col justify-between gap-space-md md:flex-row md:items-end">
                <div className="flex flex-col gap-space-xs">
                    <span
                        className="w-fit rounded-full bg-secondary/10 px-2 py-0.5 font-label-sm text-label-sm font-bold uppercase tracking-widest text-secondary">Bước 6 trên 7</span>
                    <h1 className="font-headline-lg text-headline-lg font-bold tracking-tight text-primary">Phê duyệt
                        chính thức &amp; Khóa chứng từ</h1>
                    <p className="max-w-3xl font-body-md text-body-md text-on-surface-variant">Xem lại thông tin pháp lý
                        và kết quả kiểm tra. Sau khi duyệt, chứng từ được đánh dấu APPROVED và đưa vào báo cáo Excel ở
                        Bước 7.</p>
                </div>
                <div className="flex flex-wrap items-center gap-space-sm">
                    <DocSelect value={docId} onChange={onSelect}/>
                    {doc && <span
                        className={`rounded-xl px-space-md py-space-sm font-label-md text-label-md font-bold ${STATUS_CLS[doc.status] ?? ''}`}>{STATUS_LABEL[doc.status] ?? doc.status}</span>}
                </div>
            </div>

            {msg && <p role="alert"
                       className={`mb-space-md font-body-md text-body-md ${msg.ok ? 'text-secondary' : 'text-error'}`}>{msg.text}</p>}
            {docId == null && <p className="text-on-surface-variant">Chưa có chứng từ nào. Hãy tải lên ở Bước 1.</p>}

            {doc && (
                <>
                    <div className="mb-space-lg grid grid-cols-1 gap-space-lg lg:grid-cols-12">
                        <div className="flex flex-col gap-space-md lg:col-span-7">
                            <div
                                className="relative overflow-hidden rounded-xl bg-surface-container-low p-space-lg shadow-xl">
                                <div
                                    className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-primary-container/10 blur-3xl"/>
                                <div className="relative z-10 mb-space-md flex items-center gap-space-sm">
                                    <div
                                        className="flex h-12 w-12 items-center justify-center rounded-xl bg-surface-container-highest text-primary-container shadow-md">
                                        <span className="material-symbols-outlined text-[28px]">verified_user</span>
                                    </div>
                                    <div className="flex min-w-0 flex-col">
                                        <span
                                            className="font-label-sm text-label-sm uppercase tracking-widest text-on-surface-variant">Hồ sơ pháp lý doanh nghiệp</span>
                                        <span
                                            className="truncate font-title-lg text-title-lg font-bold text-primary">{seller || 'Chưa có tên đơn vị'}</span>
                                    </div>
                                </div>
                                <div
                                    className="relative z-10 grid grid-cols-1 gap-space-md rounded-xl bg-surface-container-lowest/80 p-space-md md:grid-cols-2">
                                    {legal.map(([k, v]) => (
                                        <div key={k} className="flex flex-col gap-1"><span
                                            className="font-label-sm text-label-sm uppercase tracking-wider text-outline">{k}</span><span
                                            className="font-title-md text-title-md font-bold text-on-surface">{v}</span>
                                        </div>
                                    ))}
                                    <div className="flex flex-col gap-1 md:col-span-2"><span
                                        className="font-label-sm text-label-sm uppercase tracking-wider text-outline">Địa chỉ</span><span
                                        className="font-body-md text-body-md font-medium text-on-surface">{address || '—'}</span>
                                    </div>
                                </div>
                            </div>

                            {/* KHU VỰC HIỂN THỊ TRỰC TIẾP TỪ BƯỚC 5 (fields) */}
                            <div
                                className="flex flex-col gap-space-sm rounded-xl bg-surface-container-low p-space-lg shadow-xl">
                <span className="flex items-center gap-space-xs font-title-md text-body-md font-bold text-primary">
                  <span
                      className="material-symbols-outlined text-[18px] text-primary-container">format_list_bulleted</span>
                  Thông tin chi tiết từ Bước 5
                </span>

                                {Object.keys(fields).length === 0 && (
                                    <p className="font-body-sm text-body-sm text-on-surface-variant">Chưa có dữ liệu
                                        trích xuất nào từ OCR.</p>
                                )}

                                {Object.entries(fields).map(([k, v]) => (
                                    <div key={k}
                                         className="flex justify-between gap-3 rounded-xl bg-surface-container p-space-sm font-body-sm text-body-sm">
                                        <span className="text-outline font-medium">{k}</span>
                                        <span
                                            className="whitespace-pre-wrap break-words text-right font-medium text-on-surface">
                      {show(v) || '—'}
                    </span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="flex flex-col gap-space-md lg:col-span-5">
                            <div
                                className="relative flex flex-col gap-space-md overflow-hidden rounded-xl bg-surface-container-low p-space-lg shadow-xl">
                                <div
                                    className="pointer-events-none absolute -bottom-20 -right-20 h-64 w-64 rounded-full bg-primary-container/15 blur-3xl"/>
                                <div
                                    className="relative z-10 flex flex-col items-center rounded-xl bg-surface-container-lowest p-space-md text-center">
                                    <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">Tổng tiền thanh toán</span>
                                    <span
                                        className="font-headline-lg text-headline-lg font-bold tracking-tight text-primary">{total || '—'}</span>
                                    {totK && !isNaN(digits(total)) && <span
                                        className="font-body-sm text-body-sm text-on-surface-variant">{digits(total).toLocaleString('vi-VN')} (đọc từ trường {totK})</span>}
                                </div>
                                <div className="relative z-10 flex flex-col gap-space-xs">
                                    <span className="font-title-md text-body-md font-bold text-on-surface">Kết quả kiểm tra nhanh</span>
                                    {rs.map((r) => (
                                        <div key={r.title}
                                             className="flex items-start gap-space-xs rounded-xl bg-surface-container p-space-sm">
                                            <span
                                                className={`material-symbols-outlined text-[20px] ${r.ok === true ? 'text-secondary' : r.ok === false ? 'text-tertiary-fixed-dim' : 'text-on-surface-variant'}`}>{r.ok === true ? 'check_circle' : r.ok === false ? 'warning' : 'help'}</span>
                                            <div className="flex flex-col"><span
                                                className="font-label-md text-label-md font-semibold text-on-surface">{r.title}</span><span
                                                className="font-body-sm text-body-sm text-on-surface-variant">{r.detail}</span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                                {bad.length > 0 &&
                                    <p className="relative z-10 rounded-lg bg-tertiary-container/15 p-space-sm font-body-sm text-body-sm text-tertiary-fixed-dim">Còn {bad.length} quy
                                        tắc chưa đạt. Quay lại Bước 5 để sửa, hoặc duyệt nếu bạn đã đối chiếu bản
                                        gốc.</p>}
                                {pending &&
                                    <p className="relative z-10 rounded-lg bg-surface-container-high p-space-sm font-body-sm text-body-sm text-on-surface-variant">OCR
                                        chưa xong nên chưa thể duyệt. Theo dõi ở Bước 2.</p>}
                            </div>
                        </div>
                    </div>

                    <div
                        className="sticky bottom-4 z-30 flex flex-col items-center justify-between gap-space-md rounded-xl bg-surface-container-low/95 p-space-md shadow-2xl backdrop-blur-2xl md:flex-row">
                        <div className="flex w-full items-center gap-space-md md:w-auto">
                            <div
                                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-secondary/15 text-secondary">
                                <span className="material-symbols-outlined text-[24px]">gavel</span></div>
                            <div className="flex flex-col">
                                <span
                                    className="font-title-md text-body-md font-bold text-on-surface">{locked ? 'Chứng từ đã được khóa' : 'Xác nhận phê duyệt & khóa chứng từ'}</span>
                                <span
                                    className="font-body-sm text-body-sm text-on-surface-variant">Người duyệt: {me.name || '—'}{me.role && ` • ${me.role}`}</span>
                            </div>
                        </div>
                        <div className="flex w-full flex-wrap items-center justify-end gap-space-sm md:w-auto">
                            <button type="button" onClick={onBack}
                                    className="flex items-center gap-space-xs rounded-xl bg-surface-container px-space-md py-2.5 font-title-md text-body-md font-semibold text-on-surface shadow-sm hover:bg-surface-container-high">
                                <span className="material-symbols-outlined text-[18px]">arrow_back</span>Về Bước 5
                            </button>
                            {locked
                                ? <button type="button" onClick={exportXlsx}
                                          className="flex items-center gap-space-xs rounded-xl bg-gradient-to-r from-primary-container to-secondary px-space-lg py-2.5 font-title-md text-body-md font-bold text-on-primary-fixed shadow-lg hover:brightness-110">
                                    <span className="material-symbols-outlined text-[20px]">ios_share</span>Bước 7: Tải
                                    Excel đã duyệt</button>
                                : <>
                                    <button type="button" disabled={busy} onClick={reject}
                                            className="flex items-center gap-space-xs rounded-xl bg-error-container/30 px-space-md py-2.5 font-title-md text-body-md font-semibold text-error hover:bg-error-container/50 disabled:opacity-40">
                                        <span className="material-symbols-outlined text-[18px]">block</span>Từ chối
                                    </button>
                                    <button type="button" disabled={busy || pending} onClick={approve}
                                            className="flex items-center gap-space-xs rounded-xl bg-gradient-to-r from-secondary to-secondary-container px-space-lg py-2.5 font-title-md text-title-md font-bold tracking-tight text-on-secondary-container shadow-[0_0_24px_rgba(78,222,163,0.45)] transition-all hover:shadow-[0_0_32px_rgba(78,222,163,0.7)] disabled:opacity-40">
                                        <span
                                            className="material-symbols-outlined text-[22px]">{busy ? 'progress_activity' : 'task_alt'}</span>{busy ? 'Đang xử lý…' : 'Phê duyệt chính thức & Khóa'}
                                    </button>
                                </>}
                        </div>
                    </div>
                </>
            )}
        </div>
    )
}