import {useCallback, useEffect, useState} from 'react'
import {api} from '../api'
import DocSelect from '../components/DocSelect'
import {STATUS_CLS, parseFields, pick, rules, show, stages, type StageState} from '../lib'
import {STATUS_LABEL, type Doc} from '../types'

const STAGE_UI: Record<StageState, { box: string; icon: string; chip: string; label: string }> = {
    done: {box: 'bg-surface-container', icon: 'check', chip: 'bg-secondary/20 text-secondary', label: 'text-secondary'},
    active: {
        box: 'bg-tertiary-container/20 shadow-[0_0_18px_rgba(255,185,95,0.2)]',
        icon: 'rotate_right',
        chip: 'bg-tertiary-container text-on-tertiary-container',
        label: 'text-tertiary-fixed-dim'
    },
    wait: {
        box: 'bg-surface-container-lowest/60 opacity-70',
        icon: 'hourglass_empty',
        chip: 'bg-surface-container text-outline',
        label: 'text-outline'
    },
    fail: {box: 'bg-error-container/20', icon: 'close', chip: 'bg-error-container text-error', label: 'text-error'},
}

export default function Step2Track({docId, onSelect, onNext}: {
    docId: number | null;
    onSelect: (id: number) => void;
    onNext: () => void
}) {
    const [doc, setDoc] = useState<Doc | null>(null)
    const [err, setErr] = useState('')

    const load = useCallback(async () => {
        if (docId == null) return
        try {
            setDoc(await api.get(docId));
            setErr('')
        } catch (e) {
            setErr((e as Error).message)
        }
    }, [docId])

    useEffect(() => {
        setDoc(null);
        load()
    }, [load])

    // OCR chạy nền qua Redis → hỏi lại mỗi 3 giây cho tới khi hết PENDING
    const pending = doc?.status === 'PENDING'
    useEffect(() => {
        if (!pending) return
        const t = setInterval(load, 3000)
        return () => clearInterval(t)
    }, [pending, load])

    const fields = parseFields(doc?.extractedFields)
    const st = doc ? stages(doc) : []
    const doneCount = st.filter((s) => s.state === 'done').length
    const info = doc && [
        ['Mã hồ sơ', `#${doc.id}`],
        ['Phân loại chứng từ', doc.documentType ?? 'Chưa nhận diện'],
        ['Đơn vị phát hành', pick(fields, /company|seller|vendor|supplier|ten_?(cong_?ty|don_?vi)/i) || '—'],
        ['Thời gian nhận', doc.receivedAt ? new Date(doc.receivedAt).toLocaleString('vi-VN') : '—'],
    ]

    return (
        <div className="flex w-full flex-col gap-space-lg">
            <div
                className="flex flex-col justify-between gap-space-md rounded-xl bg-surface-container-low p-space-lg shadow-xl lg:flex-row lg:items-center">
                <div className="flex flex-col gap-space-xs">
                    <h1 className="font-headline-lg text-headline-lg tracking-tight text-primary">Bước 2: Theo dõi tiến
                        trình xử lý</h1>
                    <p className="max-w-3xl font-body-md text-body-md text-on-surface-variant">
                        Xem chứng từ đang ở chặng nào và các quy tắc kiểm tra nhanh trên dữ liệu đã bóc tách.
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-space-sm">
                    <DocSelect value={docId} onChange={onSelect}/>
                    <div
                        className="flex items-center gap-space-xs rounded-xl bg-surface-container-high px-space-md py-space-sm">
            <span className="relative flex h-3 w-3">
              {pending && <span
                  className="absolute inline-flex h-full w-full animate-ping rounded-full bg-secondary opacity-75"/>}
                <span
                    className={`relative inline-flex h-3 w-3 rounded-full ${pending ? 'bg-secondary' : 'bg-outline'}`}/>
            </span>
                        <span
                            className="font-body-sm text-body-sm font-semibold text-on-surface-variant">{pending ? 'Tự cập nhật mỗi 3s' : 'Đã dừng đồng bộ'}</span>
                    </div>
                    <button type="button" onClick={load} title="Làm mới"
                            className="rounded-xl bg-surface-container-high p-space-sm text-on-surface hover:text-primary">
                        <span className="material-symbols-outlined text-[20px]">sync</span>
                    </button>
                </div>
            </div>

            {err && <p role="alert" className="font-body-md text-body-md text-error">{err}</p>}
            {!doc && !err &&
                <p className="text-on-surface-variant">{docId == null ? 'Chưa có chứng từ nào. Hãy tải lên ở Bước 1.' : 'Đang tải…'}</p>}

            {doc && info && (
                <>
                    <div className="grid grid-cols-1 gap-space-md md:grid-cols-2 lg:grid-cols-5">
                        {info.map(([k, v]) => (
                            <div key={k}
                                 className="flex flex-col rounded-xl bg-surface-container-low p-space-md shadow-sm">
                                <span
                                    className="font-label-sm text-label-sm tracking-wider text-on-surface-variant">{k}</span>
                                <span className="mt-1 truncate font-title-md text-body-md font-semibold text-on-surface"
                                      title={v}>{v}</span>
                            </div>
                        ))}
                        <div className="flex flex-col rounded-xl bg-surface-container-low p-space-md shadow-sm">
                            <span className="font-label-sm text-label-sm tracking-wider text-on-surface-variant">Trạng thái</span>
                            <span
                                className={`mt-1 w-fit rounded-full px-3 py-0.5 font-label-md text-label-md ${STATUS_CLS[doc.status] ?? ''}`}>{STATUS_LABEL[doc.status] ?? doc.status}</span>
                        </div>
                    </div>

                    <div
                        className="flex flex-col gap-space-md rounded-xl bg-surface-container-low p-space-lg shadow-xl">
                        <div className="flex items-center justify-between">
                            <span className="font-title-lg text-title-lg font-bold text-primary">Quy trình xử lý (6 chặng)</span>
                            <span
                                className="rounded-full bg-surface-container-highest px-space-sm py-1 font-label-sm text-label-sm font-semibold text-secondary">
                Tiến độ: {Math.round((doneCount / 6) * 100)}%
              </span>
                        </div>
                        <ol className="grid grid-cols-1 gap-space-md md:grid-cols-2 lg:grid-cols-6">
                            {st.map((s, i) => {
                                const u = STAGE_UI[s.state]
                                return (
                                    <li key={s.name} className={`flex flex-col rounded-xl p-space-md ${u.box}`}>
                                        <div className="flex items-center justify-between">
                                            <span
                                                className="font-label-sm text-label-sm text-on-surface-variant">Chặng {String(i + 1).padStart(2, '0')}</span>
                                            <span
                                                className={`flex h-6 w-6 items-center justify-center rounded-full ${u.chip}`}>
                        <span
                            className={`material-symbols-outlined text-[16px] ${s.state === 'active' ? 'animate-spin' : ''}`}>{u.icon}</span>
                      </span>
                                        </div>
                                        <span
                                            className="mt-space-sm font-title-md text-body-md font-semibold text-on-surface">{s.name}</span>
                                        <span className={`font-body-sm text-body-sm ${u.label}`}>{s.note}</span>
                                    </li>
                                )
                            })}
                        </ol>
                    </div>

                    <div className="grid grid-cols-1 gap-space-lg lg:grid-cols-12">
                        <div
                            className="flex flex-col gap-space-md rounded-xl bg-surface-container-low p-space-lg shadow-xl lg:col-span-7">
                            <h2 className="font-title-lg text-title-lg font-bold text-primary">Dữ liệu đã bóc tách</h2>
                            {Object.keys(fields).length === 0 ? (
                                <p className="text-on-surface-variant">{pending ? 'OCR đang chạy nền, dữ liệu sẽ hiện ở đây khi xong.' : 'Chưa có trường nào được bóc tách.'}</p>
                            ) : (
                                <dl className="flex flex-col gap-space-xs">
                                    {Object.entries(fields).map(([k, v]) => (
                                        <div key={k}
                                             className="flex flex-col justify-between gap-1 rounded-xl bg-surface-container p-space-md sm:flex-row sm:items-baseline">
                                            <dt className="font-label-sm text-label-sm text-on-surface-variant">{k}</dt>
                                            <dd className="whitespace-pre-wrap break-words font-title-md text-body-md font-semibold text-on-surface sm:text-right">{show(v) || '—'}</dd>
                                        </div>
                                    ))}
                                </dl>
                            )}
                        </div>

                        <div className="flex flex-col gap-space-md lg:col-span-5">
                            <div
                                className="flex flex-col gap-space-md rounded-xl bg-surface-container-low p-space-lg shadow-xl">
                                <h2 className="font-title-lg text-title-lg font-bold text-primary">Kiểm tra nhanh</h2>
                                {rules(fields).map((r) => (
                                    <div key={r.title}
                                         className={`flex flex-col gap-space-xs rounded-xl p-space-md ${r.ok === false ? 'bg-tertiary-container/15' : 'bg-surface-container'}`}>
                                        <div
                                            className={`flex items-center gap-space-xs font-title-md text-body-md font-bold ${r.ok === true ? 'text-secondary' : r.ok === false ? 'text-tertiary-fixed-dim' : 'text-on-surface-variant'}`}>
                                            <span
                                                className="material-symbols-outlined text-[20px]">{r.ok === true ? 'check_circle' : r.ok === false ? 'warning' : 'help'}</span>
                                            {r.title}
                                        </div>
                                        <p className="font-body-md text-body-md text-on-surface-variant">{r.detail}</p>
                                    </div>
                                ))}
                                <p className="font-body-sm text-body-sm text-outline">Kiểm tra chạy trên trình duyệt,
                                    chỉ để tham khảo. Kết quả chính thức do backend quyết định.</p>
                            </div>
                            <button
                                type="button"
                                onClick={onNext}
                                className="flex w-full items-center justify-center gap-space-sm rounded-xl bg-gradient-to-r from-primary-container to-secondary px-space-lg py-3 font-title-md text-body-md font-bold text-on-secondary-fixed shadow-[0_0_20px_rgba(0,242,254,0.35)] transition-all hover:shadow-[0_0_28px_rgba(0,242,254,0.5)]"
                            >
                                Sang Bước 3: So sánh tài liệu gốc
                                <span className="material-symbols-outlined text-[20px]">arrow_forward</span>
                            </button>
                        </div>
                    </div>
                </>
            )}
        </div>
    )
}
