import {useEffect, useRef, useState} from 'react'
import {api, download} from '../api'
import DocSelect from '../components/DocSelect'
import {STATUS_CLS, parseFields, show} from '../lib'
import {STATUS_LABEL, type Doc} from '../types'

const tool = 'rounded-lg p-1.5 text-on-surface-variant transition-all hover:bg-surface-container-high hover:text-primary'

export default function Step3Compare({docId, onSelect, onNext}: {
    docId: number | null;
    onSelect: (id: number) => void;
    onNext: () => void
}) {
    const [doc, setDoc] = useState<Doc | null>(null)
    const [blob, setBlob] = useState<Blob | null>(null)
    const [url, setUrl] = useState<string>()
    const [zoom, setZoom] = useState(1)
    const [rot, setRot] = useState(0)
    const [err, setErr] = useState('')
    const viewport = useRef<HTMLDivElement>(null)

    useEffect(() => {
        if (docId == null) return
        setDoc(null);
        setBlob(null);
        setUrl(undefined);
        setErr('');
        setZoom(1);
        setRot(0)
        api.get(docId).then(setDoc).catch((e) => setErr(e.message))
        let u: string | undefined
        let dead = false
        api.file(docId).then((b) => {
            if (!dead) {
                u = URL.createObjectURL(b);
                setBlob(b);
                setUrl(u)
            }
        }).catch(() => {
        })
        return () => {
            dead = true;
            if (u) URL.revokeObjectURL(u)
        }
    }, [docId])

    const fields = parseFields(doc?.extractedFields)
    const isPdf = blob?.type === 'application/pdf'

    return (
        <div className="flex w-full flex-col gap-space-md pb-12">
            <div
                className="flex flex-col justify-between gap-space-sm rounded-xl bg-surface-container-low p-space-md shadow-md md:flex-row md:items-center">
                <div className="flex items-center gap-space-md">
                    <div
                        className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-container text-on-primary-fixed shadow-lg shadow-primary-container/20">
                        <span className="material-symbols-outlined text-[26px]">find_in_page</span>
                    </div>
                    <div className="flex flex-col gap-1">
                        {doc && <span
                            className={`w-fit rounded-full px-2 py-0.5 font-label-sm text-label-sm ${STATUS_CLS[doc.status] ?? ''}`}>{STATUS_LABEL[doc.status] ?? doc.status}</span>}
                        <h1 className="font-headline-md text-headline-md font-bold tracking-tight text-on-surface">Bước
                            3: So sánh tài liệu gốc &amp; dữ liệu số hóa</h1>
                    </div>
                </div>
                <DocSelect value={docId} onChange={onSelect}/>
            </div>

            {err && <p role="alert" className="font-body-md text-body-md text-error">{err}</p>}
            {docId == null && <p className="text-on-surface-variant">Chưa có chứng từ nào. Hãy tải lên ở Bước 1.</p>}

            {docId != null && (
                <div className="grid grid-cols-1 items-start gap-space-md lg:grid-cols-12">
                    <div
                        className="flex flex-col gap-space-sm rounded-xl bg-surface-container-low p-space-md shadow-lg lg:col-span-7">
                        <div
                            className="flex items-center justify-between rounded-xl bg-surface-container px-space-md py-2">
                            <span
                                className="truncate font-title-md text-body-md font-bold text-primary">{doc?.fileName ?? '…'}</span>
                            <div className="flex items-center gap-1">
                                <button type="button" className={tool} title="Phóng to"
                                        onClick={() => setZoom((z) => Math.min(z + 0.25, 3))}><span
                                    className="material-symbols-outlined text-[20px]">zoom_in</span></button>
                                <button type="button" className={tool} title="Thu nhỏ"
                                        onClick={() => setZoom((z) => Math.max(z - 0.25, 0.5))}><span
                                    className="material-symbols-outlined text-[20px]">zoom_out</span></button>
                                <span
                                    className="w-12 text-center font-label-sm text-label-sm text-on-surface">{Math.round(zoom * 100)}%</span>
                                <button type="button" className={tool} title="Xoay 90°"
                                        onClick={() => setRot((r) => (r + 90) % 360)}><span
                                    className="material-symbols-outlined text-[20px]">rotate_right</span></button>
                                <button type="button" className={tool} title="Về mặc định" onClick={() => {
                                    setZoom(1);
                                    setRot(0)
                                }}><span className="material-symbols-outlined text-[20px]">aspect_ratio</span></button>
                                <button type="button" className={tool} title="Toàn màn hình"
                                        onClick={() => viewport.current?.requestFullscreen?.().catch(() => {
                                        })}><span className="material-symbols-outlined text-[20px]">fullscreen</span>
                                </button>
                            </div>
                        </div>
                        <div ref={viewport}
                             className="flex h-[680px] w-full overflow-auto rounded-xl bg-surface-container-lowest p-4">
                            {!url ? (
                                <p className="m-auto text-on-surface-variant">Đang tải tệp…</p>
                            ) : (
                                <div className="m-auto transition-transform duration-200"
                                     style={{transform: `rotate(${rot}deg)`}}>
                                    {isPdf
                                        ? <iframe src={url} title={doc?.fileName} className="rounded-lg bg-white"
                                                  style={{width: 520 * zoom, height: 735 * zoom}}/>
                                        : <img src={url} alt={doc?.fileName}
                                               className="max-w-none rounded-lg bg-white shadow-2xl"
                                               style={{width: `${zoom * 100}%`, minWidth: 320 * zoom}}/>}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="flex flex-col gap-space-sm lg:col-span-5">
                        <div
                            className="flex items-center gap-space-sm rounded-xl bg-surface-container-low p-space-md shadow-md">
                            <span className="material-symbols-outlined text-[22px] text-secondary">fact_check</span>
                            <span
                                className="font-title-lg text-title-lg font-bold text-on-surface">Dữ liệu trích xuất</span>
                        </div>
                        <dl className="flex flex-col gap-space-xs rounded-xl bg-surface-container-low p-space-md shadow-sm">
                            {Object.keys(fields).length === 0 &&
                                <p className="text-on-surface-variant">{doc?.status === 'PENDING' ? 'OCR đang chạy nền. Quay lại Bước 2 để theo dõi.' : 'Chưa có trường nào được bóc tách.'}</p>}
                            {Object.entries(fields).map(([k, v]) => (
                                <div key={k} className="flex flex-col gap-1 rounded-xl bg-surface-container p-space-md">
                                    <dt className="font-label-sm text-label-sm text-on-surface-variant">{k}</dt>
                                    <dd className="whitespace-pre-wrap break-words font-title-md text-body-md font-semibold text-on-surface">{show(v) || '—'}</dd>
                                </div>
                            ))}
                        </dl>
                        <details className="rounded-xl bg-surface-container-low p-space-md shadow-sm">
                            <summary className="cursor-pointer font-label-md text-label-md text-on-surface-variant">Văn
                                bản OCR gốc
                            </summary>
                            <pre
                                className="mt-space-sm max-h-64 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-lowest p-space-sm font-body-sm text-body-sm text-on-surface-variant">{doc?.rawOcrText ?? '(trống)'}</pre>
                        </details>
                    </div>
                </div>
            )}

            {docId != null && (
                <div
                    className="sticky bottom-4 z-30 flex flex-col items-center justify-between gap-space-sm rounded-xl bg-surface-container-low/95 p-space-md shadow-2xl backdrop-blur-xl sm:flex-row">
                    <span className="font-body-sm text-body-sm text-on-surface-variant">Thấy số liệu chưa khớp bản gốc? Sửa ở Bước 5, duyệt ở Bước 6.</span>
                    <div className="flex flex-wrap items-center justify-end gap-space-sm">
                        <button type="button" disabled={!blob}
                                onClick={() => blob && doc && download(blob, doc.fileName)}
                                className="flex items-center gap-1.5 rounded-xl bg-surface-container-high px-space-md py-2.5 font-title-md text-body-md font-semibold text-on-surface hover:bg-surface-bright disabled:opacity-40">
                            <span className="material-symbols-outlined text-[18px]">file_download</span>Tải file gốc
                        </button>
                        <button type="button" onClick={onNext}
                                className="flex items-center gap-2 rounded-xl bg-primary-container px-space-lg py-2.5 font-title-md text-body-md font-bold text-on-primary-fixed shadow-lg shadow-primary-container/30 hover:bg-primary-fixed">
                            Sang Bước 5: Kiểm tra &amp; Sửa
                            <span className="material-symbols-outlined text-[20px]">arrow_forward</span>
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
