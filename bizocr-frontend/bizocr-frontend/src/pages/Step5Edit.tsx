import {useEffect, useMemo, useState} from 'react'
import {api} from '../api'
import DocSelect from '../components/DocSelect'
import {labelOf, groupOf, splitBySchema} from '../docSchemas'
import {whoami} from '../layout/Shell'
import {RE, STATUS_CLS, digits, fmtDate, keyOf, parseFields, restore, show, type Fields} from '../lib'
import {STATUS_LABEL, type Doc} from '../types'

// Backend chỉ cho đặt tay 3 trạng thái này; APPROVED phải đi qua Bước 6 (POST /approve)
const MANUAL = ['PENDING', 'NEED_REVIEW', 'REJECTED']
const inputCls = 'w-full rounded-xl bg-surface-container-highest/60 px-space-md py-2.5 font-title-md text-body-md text-on-surface transition-all focus:bg-surface-container-highest focus:outline-none focus:ring-1 focus:ring-primary-container disabled:opacity-60'

export default function Step5Edit({docId, onSelect, onNext}: {
    docId: number | null;
    onSelect: (id: number) => void;
    onNext: () => void
}) {
    const [doc, setDoc] = useState<Doc | null>(null)
    const [orig, setOrig] = useState<Fields>({})
    const [hidden, setHidden] = useState<Fields>({}) // key nội bộ (review_status, _warn_*, validator_*): không hiện, nhưng giữ nguyên khi lưu
    const [edit, setEdit] = useState<Record<string, string>>({})
    const [sel, setSel] = useState('NEED_REVIEW')
    const [blob, setBlob] = useState<Blob | null>(null)
    const [url, setUrl] = useState<string>()
    const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
    const [busy, setBusy] = useState(false)
    const me = whoami()

    async function load(id: number) {
        const d = await api.get(id)
        // Form cố định theo loại chứng từ: luôn đủ và đúng thứ tự các trường, trường thiếu = '' để người dùng nhập
        const {shown, hidden: hid} = splitBySchema(d.documentType, parseFields(d.extractedFields))
        setDoc(d)
        setOrig(shown)
        setHidden(hid)
        setEdit(Object.fromEntries(Object.entries(shown).map(([k, v]) => [k, show(v)])))
        setSel(MANUAL.includes(d.status) ? d.status : 'NEED_REVIEW')
    }

    useEffect(() => {
        if (docId == null) return
        setDoc(null)
        setBlob(null)
        setUrl(undefined)
        setMsg(null)
        load(docId).catch((e) => setMsg({ok: false, text: e.message}))
        let u: string | undefined
        let dead = false
        api.file(docId).then((b) => {
            if (!dead) {
                u = URL.createObjectURL(b)
                setBlob(b)
                setUrl(u)
            }
        }).catch(() => {
        })
        return () => {
            dead = true
            if (u) URL.revokeObjectURL(u)
        }
    }, [docId])

    const locked = doc?.status === 'APPROVED'
    const changed = useMemo(() => Object.keys(edit).filter((k) => edit[k] !== show(orig[k])), [edit, orig])
    const dirty = changed.length > 0 || (!!doc && MANUAL.includes(doc.status) && sel !== doc.status)
    const subK = keyOf(edit, RE.sub), vatK = keyOf(edit, RE.vat), totK = keyOf(edit, RE.total),
        mstK = keyOf(edit, RE.mst)
    const calc = subK && vatK ? digits(edit[subK]) + digits(edit[vatK]) : NaN
    const cur = totK ? digits(edit[totK]) : NaN
    const payload = () => JSON.stringify({
        ...hidden,
        ...Object.fromEntries(Object.entries(edit).map(([k, v]) => [k, restore(orig[k], v)])),
    })

    async function run(fn: () => Promise<unknown>, ok: string): Promise<boolean> {
        if (docId == null) return false
        setBusy(true)
        setMsg(null)
        try {
            await fn()
            await load(docId)
            setMsg({ok: true, text: ok})
            return true
        } catch (e) {
            setMsg({ok: false, text: (e as Error).message})
            return false
        } finally {
            setBusy(false)
        }
    }

    const save = () => run(() => api.update(docId!, {status: sel, extractedFields: payload()}), 'Đã lưu thay đổi')

    // Bước 6 đọc dữ liệu từ backend nên phải lưu trước khi sang
    async function goNext() {
        if (!dirty || (await save())) onNext()
    }

    const resetAll = () => {
        setEdit(Object.fromEntries(Object.entries(orig).map(([k, v]) => [k, show(v)])))
        setSel(doc && MANUAL.includes(doc.status) ? doc.status : 'NEED_REVIEW')
    }

    const events = [
        changed.length > 0 && {
            tone: 'bg-tertiary-fixed-dim',
            icon: 'edit',
            title: 'Đang sửa (chưa lưu)',
            time: 'Bây giờ',
            body: `${changed.length} trường: ${changed.map((k) => labelOf(doc?.documentType, k)).join(', ')}`
        },
        doc?.reviewedBy && {
            tone: 'bg-primary-container',
            icon: 'person',
            title: `Lần lưu cuối bởi ${doc.reviewedBy}`,
            time: fmtDate(doc.reviewedAt),
            body: 'Đã ghi vào cơ sở dữ liệu'
        },
        doc && {
            tone: 'bg-secondary',
            icon: 'smart_toy',
            title: 'Hệ thống AI trích xuất',
            time: fmtDate(doc.receivedAt),
            body: `${Object.keys(orig).length} trường${doc.documentType ? ` • loại ${doc.documentType}` : ''}`
        },
    ].filter(Boolean) as { tone: string; icon: string; title: string; time: string; body: string }[]

    return (
        <div className="flex w-full flex-col pb-space-xl">
            <div
                className="relative mb-space-lg overflow-hidden rounded-xl bg-surface-container-high p-space-lg shadow-xl">
                <div
                    className="pointer-events-none absolute -right-20 -top-24 h-80 w-80 rounded-full bg-primary-container/10 blur-3xl"/>
                <div className="relative z-10 flex flex-col justify-between gap-space-md lg:flex-row lg:items-center">
                    <div className="flex flex-col gap-space-xs">
                        <h1 className="font-headline-md text-headline-md tracking-tight text-on-surface">Bước 5: Kiểm
                            tra &amp; Chỉnh sửa thông tin chứng từ</h1>
                        <p className="max-w-3xl font-body-md text-body-md text-on-surface-variant">
                            Rà soát các trường do AI trích xuất và sửa cho khớp bản gốc. Mỗi lần lưu được backend ghi
                            lại từng trường để làm dữ liệu nhãn chuẩn.
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-space-sm">
                        <DocSelect value={docId} onChange={onSelect}/>
                        {doc && (
                            <div
                                className="flex flex-col rounded-xl bg-surface-container-lowest/80 px-space-md py-space-xs text-right">
                                <span className="font-label-sm text-label-sm uppercase text-on-surface-variant">Trạng thái</span>
                                <span
                                    className={`mt-0.5 rounded-full px-2 py-0.5 font-label-md text-label-md ${STATUS_CLS[doc.status] ?? ''}`}>{STATUS_LABEL[doc.status] ?? doc.status}</span>
                            </div>
                        )}
                    </div>
                </div>
                {doc && (
                    <div
                        className="relative z-10 -mx-space-lg -mb-space-lg mt-space-md flex flex-wrap items-center justify-between gap-space-md bg-surface-container-lowest/50 px-space-lg py-space-sm">
                        <div
                            className="flex flex-wrap items-center gap-space-lg font-body-sm text-body-sm text-on-surface-variant">
                            <span>Mã chứng từ: <b
                                className="font-title-md text-body-md text-primary">#{doc.id}</b></span>
                            <span>Người thao tác: <b
                                className="font-title-md text-body-md text-on-surface">{me.name || '—'}</b></span>
                            <span>Loại: <b
                                className="font-title-md text-body-md text-on-surface">{doc.documentType ?? 'Chưa nhận diện'}</b></span>
                        </div>
                        <span
                            className="font-label-sm text-label-sm text-on-surface-variant">Lần lưu cuối: {fmtDate(doc.reviewedAt)}</span>
                    </div>
                )}
            </div>

            {msg && <p role="alert"
                       className={`mb-space-md font-body-md text-body-md ${msg.ok ? 'text-secondary' : 'text-error'}`}>{msg.text}</p>}
            {docId == null && <p className="text-on-surface-variant">Chưa có chứng từ nào. Hãy tải lên ở Bước 1.</p>}

            {doc && (
                <div className="grid grid-cols-1 items-start gap-space-lg xl:grid-cols-12">
                    <div className="xl:col-span-8">
                        <div className="relative overflow-hidden rounded-xl bg-surface-container p-space-lg shadow-xl">
                            <div
                                className="-mx-space-lg -mt-space-lg mb-space-md flex items-center gap-space-xs bg-surface-container-lowest/40 px-space-lg py-space-md">
                                <span
                                    className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-container/10 text-primary-container"><span
                                    className="material-symbols-outlined text-[20px]">edit_document</span></span>
                                <div>
                                    <h2 className="font-title-lg text-title-lg font-semibold text-on-surface">Biểu mẫu
                                        dữ liệu chứng từ</h2>
                                    <p className="font-body-sm text-body-sm text-on-surface-variant">{Object.keys(edit).length} trường
                                        • {changed.length} đã sửa</p>
                                </div>
                            </div>

                            <div className="flex flex-col gap-space-md">
                                <div
                                    className="flex flex-col justify-between gap-space-md rounded-xl bg-surface-container-low p-space-md shadow-sm md:flex-row md:items-center">
                                    <div className="flex max-w-md flex-col">
                                        <label htmlFor="status-sel"
                                               className="flex items-center gap-1.5 font-title-md text-body-md font-semibold text-on-surface"><span
                                            className="material-symbols-outlined text-[18px] text-tertiary-fixed-dim">rule</span>Trạng
                                            thái chứng từ</label>
                                        <span className="mt-0.5 font-body-sm text-body-sm text-on-surface-variant">Trạng thái <b
                                            className="text-secondary">ĐÃ DUYỆT</b> chỉ đặt được ở <span
                                            className="font-medium text-primary">Bước 6</span>.</span>
                                    </div>
                                    <select id="status-sel" value={locked ? 'APPROVED' : sel} disabled={locked}
                                            onChange={(e) => setSel(e.target.value)}
                                            className={`${inputCls} min-w-[240px] cursor-pointer md:w-auto`}>
                                        {locked && <option value="APPROVED">Đã duyệt (APPROVED)</option>}
                                        {MANUAL.map((s) => {
                                            const label = STATUS_LABEL[s] || (s === 'NEED_REVIEW' ? 'Cần xem xét' : s === 'PENDING' ? 'Đang chờ xử lý' : 'Đã từ chối');
                                            return (
                                                <option key={s} value={s}>{label} ({s})</option>
                                            )
                                        })}
                                    </select>
                                </div>

                                {Object.keys(edit).length === 0 &&
                                    <p className="text-on-surface-variant">Chưa có trường nào được bóc tách.</p>}

                                {(() => {
                                    const allKeys = Object.keys(edit)
                                    const hasGroups = allKeys.some((k) => !!groupOf(doc.documentType, k))

                                    const renderField = (k: string) => {
                                        const mstOk = k === mstK ? /^\d{10}(-\d{3})?$/.test(edit[k].replace(/\s/g, '')) : null
                                        const money = k === subK || k === vatK || k === totK
                                        return (
                                            <label key={k}
                                                   className="flex flex-col gap-1.5 rounded-xl bg-surface-container-low p-space-md shadow-sm">
                                                <span className="flex items-center justify-between gap-2">
                                                  <span
                                                      className={`font-label-md text-label-md font-semibold ${changed.includes(k) ? 'text-tertiary-fixed-dim' : 'text-on-surface'}`}>{labelOf(doc.documentType, k)}{changed.includes(k) && ' (đã sửa)'}</span>
                                                    {mstOk !== null && edit[k] && <span
                                                        className={`rounded-lg px-2 py-0.5 font-label-sm text-label-sm ${mstOk ? 'bg-secondary/15 text-secondary' : 'bg-error-container/40 text-error'}`}>{mstOk ? 'Đúng định dạng' : 'Sai định dạng'}</span>}
                                                </span>
                                                {edit[k].includes('\n') || edit[k].length > 60 ? (
                                                    <textarea
                                                        rows={3}
                                                        className={inputCls}
                                                        value={edit[k]}
                                                        disabled={locked}
                                                        onChange={(e) => setEdit({
                                                            ...edit,
                                                            [k]: e.target.value
                                                        })}
                                                    />
                                                ) : (
                                                    <input
                                                        className={`${inputCls} ${money ? 'text-right font-semibold tracking-tight' : ''}`}
                                                        value={edit[k]}
                                                        disabled={locked}
                                                        onChange={(e) => setEdit({...edit, [k]: e.target.value})}
                                                    />
                                                )}
                                            </label>
                                        )
                                    }

                                    if (!hasGroups) {
                                        return (
                                            <div className="grid grid-cols-1 gap-space-md md:grid-cols-2">
                                                {allKeys.map(renderField)}
                                            </div>
                                        )
                                    }

                                    const personKeys = allKeys.filter((k) => groupOf(doc.documentType, k) === 'person')
                                    const companyKeys = allKeys.filter((k) => groupOf(doc.documentType, k) === 'company')
                                    const otherKeys = allKeys.filter((k) => !groupOf(doc.documentType, k))

                                    return (
                                        <div className="flex flex-col gap-6">
                                            {personKeys.length > 0 && (
                                                <div className="flex flex-col gap-3 rounded-2xl bg-surface-container-lowest/70 p-4 border border-primary/20">
                                                    <div className="flex items-center gap-2 pb-1 border-b border-outline-variant/30 text-primary">
                                                        <span className="material-symbols-outlined text-[20px]">person</span>
                                                        <span className="text-xs sm:text-sm font-bold uppercase tracking-wider">Thông tin cá nhân</span>
                                                    </div>
                                                    <div className="grid grid-cols-1 gap-space-md md:grid-cols-2">
                                                        {personKeys.map(renderField)}
                                                    </div>
                                                </div>
                                            )}

                                            {companyKeys.length > 0 && (
                                                <div className="flex flex-col gap-3 rounded-2xl bg-surface-container-lowest/70 p-4 border border-secondary/20">
                                                    <div className="flex items-center gap-2 pb-1 border-b border-outline-variant/30 text-secondary">
                                                        <span className="material-symbols-outlined text-[20px]">apartment</span>
                                                        <span className="text-xs sm:text-sm font-bold uppercase tracking-wider">Thông tin công ty</span>
                                                    </div>
                                                    <div className="grid grid-cols-1 gap-space-md md:grid-cols-2">
                                                        {companyKeys.map(renderField)}
                                                    </div>
                                                </div>
                                            )}

                                            {otherKeys.length > 0 && (
                                                <div className="flex flex-col gap-3 rounded-2xl bg-surface-container-lowest/70 p-4 border border-outline-variant/40">
                                                    <div className="flex items-center gap-2 pb-1 border-b border-outline-variant/30 text-on-surface-variant">
                                                        <span className="material-symbols-outlined text-[20px]">info</span>
                                                        <span className="text-xs sm:text-sm font-bold uppercase tracking-wider">Thông tin bổ sung</span>
                                                    </div>
                                                    <div className="grid grid-cols-1 gap-space-md md:grid-cols-2">
                                                        {otherKeys.map(renderField)}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    )
                                })()}

                                {totK && !isNaN(calc) && (
                                    <div
                                        className="flex flex-col justify-between gap-space-md rounded-xl bg-gradient-to-r from-surface-container-lowest via-surface-container-low to-surface-container-lowest p-space-lg shadow-xl md:flex-row md:items-center">
                                        <div className="flex flex-col gap-1">
                                            <span
                                                className="flex items-center gap-space-xs font-title-lg text-title-lg font-bold text-on-surface"><span
                                                className="material-symbols-outlined text-[22px] text-secondary">payments</span>Đối chiếu tổng tiền</span>
                                            <span className="font-body-sm text-body-sm text-on-surface-variant">Tiền hàng + tiền thuế = <b
                                                className="text-on-surface">{calc.toLocaleString('vi-VN')}</b> · Trường tổng đang là <b
                                                className="text-on-surface">{isNaN(cur) ? '—' : cur.toLocaleString('vi-VN')}</b></span>
                                        </div>
                                        <div className="flex items-center gap-space-sm">
                                              <span
                                                  className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 font-label-md text-label-md font-bold ${calc === cur ? 'bg-secondary/15 text-secondary' : 'bg-tertiary-container/20 text-tertiary-fixed-dim'}`}>
                                                <span
                                                    className="material-symbols-outlined text-[16px]">{calc === cur ? 'check_circle' : 'warning'}</span>{calc === cur ? 'Khớp' : 'Lệch'}
                                              </span>
                                            {calc !== cur && !locked && (
                                                <button type="button" onClick={() => setEdit({
                                                    ...edit,
                                                    [totK]: typeof orig[totK] === 'number' ? String(calc) : calc.toLocaleString('en-US')
                                                })}
                                                        className="rounded-xl bg-surface-container-high px-space-md py-2 font-label-md text-label-md text-on-surface hover:bg-surface-bright">Tính
                                                    lại tổng</button>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>

                            <div
                                className="-mx-space-lg -mb-space-lg mt-space-lg flex flex-wrap items-center justify-between gap-space-md bg-surface-container-lowest/60 px-space-lg py-space-md">
                                <button type="button" disabled={!dirty || locked} onClick={resetAll}
                                        className="flex items-center gap-2 rounded-xl bg-surface-container-high px-space-md py-2.5 font-label-md text-label-md text-on-surface transition-all hover:bg-surface-bright disabled:opacity-40">
                                    <span className="material-symbols-outlined text-[18px]">restore</span>Khôi phục về
                                    bản đã lưu
                                </button>
                                <div className="flex flex-wrap items-center gap-space-sm">
                                    <button type="button" disabled={busy || locked || !dirty} onClick={save}
                                            className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-tertiary-fixed-dim via-tertiary-container to-tertiary-fixed-dim px-space-lg py-2.5 font-title-md text-body-md font-bold text-on-tertiary-fixed shadow-lg transition-all hover:brightness-110 active:scale-95 disabled:opacity-40">
                                        <span className="material-symbols-outlined text-[20px]">save</span>Lưu thay đổi
                                    </button>
                                    <button type="button" disabled={busy} onClick={goNext}
                                            className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-primary-container to-secondary px-space-lg py-2.5 font-title-md text-body-md font-bold text-on-primary-fixed shadow-lg transition-all hover:brightness-110 active:scale-95 disabled:opacity-40">
                                        {dirty && !locked ? 'Lưu & tiếp tục đến Bước 6' : 'Tiếp tục đến Bước 6'}<span
                                        className="material-symbols-outlined text-[20px]">arrow_forward</span>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col gap-space-lg xl:col-span-4">
                        <div
                            className="flex flex-col gap-space-md rounded-xl bg-surface-container p-space-lg shadow-xl">
                            <div className="flex items-center gap-space-xs">
                                <span
                                    className="flex h-8 w-8 items-center justify-center rounded-lg bg-secondary/10 text-secondary"><span
                                    className="material-symbols-outlined text-[20px]">manage_search</span></span>
                                <div><h2 className="font-title-lg text-title-lg font-semibold text-on-surface">Nhật ký
                                    chỉnh sửa</h2><span
                                    className="font-body-sm text-body-sm text-on-surface-variant">{events.length} sự kiện</span>
                                </div>
                            </div>
                            <div
                                className="relative flex flex-col gap-space-lg pl-6 before:absolute before:bottom-2 before:left-2.5 before:top-2 before:w-0.5 before:bg-surface-container-highest before:content-['']">
                                {events.map((ev) => (
                                    <div key={ev.title} className="relative flex flex-col gap-space-xs">
                                        <span
                                            className="absolute -left-6 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-surface-container-lowest"><span
                                            className={`h-2.5 w-2.5 rounded-full ${ev.tone}`}/></span>
                                        <div className="flex items-center justify-between gap-2"><span
                                            className="font-title-md text-body-md font-semibold text-on-surface">{ev.title}</span><span
                                            className="whitespace-nowrap font-label-sm text-label-sm text-on-surface-variant">{ev.time}</span>
                                        </div>
                                        <div
                                            className="rounded-xl bg-surface-container-low p-space-sm font-body-sm text-body-sm text-on-surface-variant">{ev.body}</div>
                                    </div>
                                ))}
                            </div>
                            <p className="font-body-sm text-body-sm text-outline">Lịch sử từng lần sửa đã lưu nằm trong
                                field_correction_audits; cần thêm endpoint đọc để hiện đủ ở đây.</p>
                        </div>

                        <div
                            className="flex flex-col gap-space-sm rounded-xl bg-surface-container p-space-md shadow-xl">
                            <div className="flex items-center justify-between">
                                <span
                                    className="flex items-center gap-1.5 font-title-md text-body-md font-semibold text-on-surface"><span
                                    className="material-symbols-outlined text-[18px] text-primary">find_in_page</span>Bản xem nhanh</span>
                                <span
                                    className="rounded bg-surface-container-high px-2 py-0.5 font-label-sm text-label-sm text-on-surface-variant">{blob?.type === 'application/pdf' ? 'PDF' : 'Ảnh'}</span>
                            </div>
                            <div
                                className="flex h-48 items-center justify-center overflow-hidden rounded-lg bg-surface-container-lowest">
                                {!url ? <span className="text-on-surface-variant">Đang tải…</span>
                                    : blob?.type === 'application/pdf' ?
                                        <iframe src={url} title={doc.fileName} className="h-full w-full bg-white"/>
                                        : <img src={url} alt={doc.fileName}
                                               className="h-full w-full object-cover opacity-80 transition-opacity hover:opacity-100"/>}
                            </div>
                            <div
                                className="flex items-center justify-between px-1 font-body-sm text-body-sm text-on-surface-variant">
                                <span className="truncate">{doc.fileName}</span>
                                <button type="button" disabled={!url} onClick={() => url && window.open(url, '_blank')}
                                        className="whitespace-nowrap font-medium text-primary hover:underline disabled:opacity-40">Mở
                                    toàn màn hình
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
