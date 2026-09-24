import { useEffect, useState } from 'react'
import { api } from '../api'
import DocSelect from '../components/DocSelect'
import { STATUS_CLS, fmtDate, show } from '../lib'
import { STATUS_LABEL, type Doc } from '../types'

export default function Step4Classify({ docId, onSelect, onNext }: { docId: number | null; onSelect: (id: number) => void; onNext: () => void }) {
  const [doc, setDoc] = useState<Doc | null>(null)
  const [res, setRes] = useState<Record<string, unknown> | null>(null)
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (docId == null) return
    setDoc(null); setRes(null); setErr('')
    api.get(docId).then(setDoc).catch((e) => setErr(e.message))
  }, [docId])

  // Chỉ gọi khi bấm: classifier có thể phải chạy tới tầng AI dự phòng nên không tự chạy.
  async function run() {
    if (!doc?.rawOcrText) return
    setBusy(true); setErr('')
    try { setRes(await api.classify(doc.rawOcrText)) }
    catch (e) { setErr((e as Error).message) }
    finally { setBusy(false) }
  }

  const resType = res ? Object.entries(res).find(([k, v]) => /type|loai/i.test(k) && typeof v === 'string')?.[1] as string | undefined : undefined
  const differs = !!resType && !!doc?.documentType && resType !== doc.documentType

  return (
    <div className="flex w-full flex-col gap-space-lg pb-space-xl">
      <div className="flex flex-col justify-between gap-space-md rounded-xl bg-surface-container-low p-space-lg shadow-xl lg:flex-row lg:items-center">
        <div className="flex flex-col gap-space-xs">
          <h1 className="font-headline-lg text-headline-lg tracking-tight text-primary">Bước 4: Phân loại chứng từ</h1>
          <p className="max-w-3xl font-body-md text-body-md text-on-surface-variant">
            Xem loại chứng từ hệ thống đã nhận diện và chạy lại bộ phân loại trên văn bản OCR để đối chiếu.
          </p>
        </div>
        <DocSelect value={docId} onChange={onSelect} />
      </div>

      {err && <p role="alert" className="font-body-md text-body-md text-error">{err}</p>}
      {docId == null && <p className="text-on-surface-variant">Chưa có chứng từ nào. Hãy tải lên ở Bước 1.</p>}

      {doc && (
        <div className="grid grid-cols-1 gap-space-lg lg:grid-cols-12">
          <div className="flex flex-col gap-space-md lg:col-span-5">
            <div className="flex flex-col gap-space-md rounded-xl bg-surface-container-low p-space-lg shadow-xl">
              <div className="flex items-center gap-space-sm">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-surface-container-highest text-primary-container shadow-md"><span className="material-symbols-outlined text-[28px]">category</span></div>
                <div className="flex min-w-0 flex-col">
                  <span className="font-label-sm text-label-sm uppercase tracking-widest text-on-surface-variant">Loại đã lưu</span>
                  <span className="truncate font-title-lg text-title-lg font-bold text-primary">{doc.documentType ?? 'Chưa nhận diện'}</span>
                </div>
              </div>
              <dl className="flex flex-col gap-space-xs font-body-sm text-body-sm">
                {[['Mã chứng từ', `#${doc.id}`], ['Tên file', doc.fileName], ['Nhận lúc', fmtDate(doc.receivedAt)]].map(([k, v]) => (
                  <div key={k} className="flex justify-between gap-3 rounded-xl bg-surface-container p-space-sm"><dt className="text-outline">{k}</dt><dd className="truncate font-medium text-on-surface">{v}</dd></div>
                ))}
                <div className="flex items-center justify-between gap-3 rounded-xl bg-surface-container p-space-sm"><dt className="text-outline">Trạng thái</dt><dd><span className={`rounded-full px-2 py-0.5 font-label-md text-label-md ${STATUS_CLS[doc.status] ?? ''}`}>{STATUS_LABEL[doc.status] ?? doc.status}</span></dd></div>
              </dl>
              {doc.status === 'PENDING' && <p className="rounded-lg bg-surface-container-high p-space-sm font-body-sm text-body-sm text-on-surface-variant">OCR còn đang chạy nên có thể chưa có loại chứng từ. Theo dõi ở Bước 2.</p>}
            </div>

            <div className="flex flex-col gap-space-sm rounded-xl bg-surface-container p-space-md shadow-xl">
              <span className="font-title-md text-body-md font-semibold text-on-surface">Văn bản OCR dùng để phân loại</span>
              <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-lowest p-space-sm font-body-sm text-body-sm text-on-surface-variant">{doc.rawOcrText || '(chưa có văn bản OCR)'}</pre>
            </div>
          </div>

          <div className="flex flex-col gap-space-md lg:col-span-7">
            <div className="relative flex flex-col gap-space-md overflow-hidden rounded-xl bg-surface-container-low p-space-lg shadow-xl">
              <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-primary-container/10 blur-3xl" />
              <div className="relative z-10 flex flex-wrap items-center justify-between gap-space-sm">
                <span className="flex items-center gap-space-xs font-title-lg text-title-lg font-bold text-on-surface"><span className="material-symbols-outlined text-[22px] text-primary-container">psychology</span>Kết quả phân loại nhanh</span>
                <button type="button" disabled={busy || !doc.rawOcrText} onClick={run} className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-primary-container to-secondary px-space-lg py-2.5 font-title-md text-body-md font-bold text-on-primary-fixed shadow-lg transition-all hover:brightness-110 active:scale-95 disabled:opacity-40">
                  <span className={`material-symbols-outlined text-[20px] ${busy ? 'animate-spin' : ''}`}>{busy ? 'progress_activity' : 'play_arrow'}</span>{busy ? 'Đang phân loại…' : res ? 'Phân loại lại' : 'Chạy phân loại'}
                </button>
              </div>

              {!res && !busy && <p className="relative z-10 text-on-surface-variant">Bấm “Chạy phân loại” để gửi văn bản OCR tới bộ phân loại của backend.</p>}
              {res && (
                <div className="relative z-10 flex flex-col gap-space-sm">
                  {resType && (
                    <div className={`flex items-center gap-space-xs rounded-xl p-space-md ${differs ? 'bg-tertiary-container/15 text-tertiary-fixed-dim' : 'bg-secondary/10 text-secondary'}`}>
                      <span className="material-symbols-outlined text-[22px]">{differs ? 'warning' : 'check_circle'}</span>
                      <span className="font-title-md text-body-md font-bold">{differs ? `Kết quả “${resType}” khác loại đã lưu “${doc.documentType}”` : `Kết quả “${resType}” khớp loại đã lưu`}</span>
                    </div>
                  )}
                  <dl className="flex flex-col gap-space-xs">
                    {Object.entries(res).map(([k, v]) => (
                      <div key={k} className="flex flex-col justify-between gap-1 rounded-xl bg-surface-container p-space-md sm:flex-row sm:items-baseline">
                        <dt className="font-label-sm text-label-sm text-on-surface-variant">{k}</dt>
                        <dd className="whitespace-pre-wrap break-words font-title-md text-body-md font-semibold text-on-surface sm:text-right">{show(v) || '—'}</dd>
                      </div>
                    ))}
                  </dl>
                  {differs && <p className="font-body-sm text-body-sm text-outline">Backend chưa có endpoint đổi loại chứng từ, nên kết quả này chỉ để đối chiếu, chưa lưu lại được.</p>}
                </div>
              )}
            </div>

            <button type="button" onClick={onNext} className="flex w-full items-center justify-center gap-space-sm rounded-xl bg-gradient-to-r from-primary-container to-secondary px-space-lg py-3 font-title-md text-body-md font-bold text-on-secondary-fixed shadow-[0_0_20px_rgba(0,242,254,0.35)] transition-all hover:shadow-[0_0_28px_rgba(0,242,254,0.5)]">
              Sang Bước 5: Kiểm tra &amp; Sửa<span className="material-symbols-outlined text-[20px]">arrow_forward</span>
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
