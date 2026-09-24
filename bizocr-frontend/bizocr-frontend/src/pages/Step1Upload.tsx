import {useEffect, useRef, useState} from 'react'
import {api} from '../api'
import {STATUS_LABEL} from '../types'

const KPI = [
    {s: 'PENDING', icon: 'hourglass_top', tone: 'text-primary-container', bg: 'bg-primary-container/20'},
    {s: 'NEED_REVIEW', icon: 'edit_note', tone: 'text-tertiary-fixed-dim', bg: 'bg-tertiary-container/30'},
    {s: 'APPROVED', icon: 'verified', tone: 'text-secondary', bg: 'bg-secondary-container/20'},
    {s: 'REJECTED', icon: 'block', tone: 'text-error', bg: 'bg-error-container/30'},
]
const ACCEPT = 'image/*,application/pdf'
const size = (n: number) => (n > 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.ceil(n / 1024)} KB`)

export default function Step1Upload({onUploaded}: { onUploaded: (firstId: number) => void }) {
    const [files, setFiles] = useState<File[]>([])
    const [drag, setDrag] = useState(false)
    const [busy, setBusy] = useState(false)
    const [err, setErr] = useState('')
    const [stats, setStats] = useState<Record<string, number>>({})
    const picker = useRef<HTMLInputElement>(null)

    // Camera states & refs
    const [cameraOpen, setCameraOpen] = useState(false)
    const [facingMode, setFacingMode] = useState<'environment' | 'user'>('environment')
    const videoRef = useRef<HTMLVideoElement>(null)
    const canvasRef = useRef<HTMLCanvasElement>(null)
    const streamRef = useRef<MediaStream | null>(null)

    useEffect(() => {
        Promise.all(KPI.map((k) => api.list({
            status: k.s,
            page: 0,
            size: 1
        }).then((p) => [k.s, p.totalElements] as const)))
            .then((r) => setStats(Object.fromEntries(r)))
            .catch(() => {
            })

        // Cleanup camera khi unmount component
        return () => stopCamera();
    }, [])

    const add = (list: FileList | File[] | null) => {
        if (!list) return
        const arrayList = list instanceof FileList ? Array.from(list) : list;
        const ok = arrayList.filter((f) => f.type.startsWith('image/') || f.type === 'application/pdf')
        setFiles((cur) => [...cur, ...ok])
    }

    async function start() {
        setBusy(true)
        setErr('')
        try {
            const ids: number[] = []
            for (const f of files) {
                ids.push((await api.upload(f)).documentId)
                setFiles((cur) => cur.slice(1))
            }
            onUploaded(ids[0])
        } catch (e) {
            setErr((e as Error).message)
        } finally {
            setBusy(false)
        }
    }

    // --- LOGIC XỬ LÝ CAMERA TRỰC TIẾP ---
    const startCamera = async (mode = facingMode) => {
        setCameraOpen(true)
        setErr('')
        stopCamera() // Dừng luồng cũ nếu có

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            setErr('Trình duyệt không hỗ trợ mở camera hoặc bạn chưa truy cập qua HTTPS/localhost.')
            setCameraOpen(false)
            return
        }

        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                video: {facingMode: {ideal: mode}, width: {ideal: 1920}, height: {ideal: 1080}},
                audio: false
            })
            streamRef.current = stream
            if (videoRef.current) {
                videoRef.current.srcObject = stream
            }
        } catch (error) {
            setErr('Không thể truy cập camera: ' + (error as Error).message)
            setCameraOpen(false)
        }
    }

    const stopCamera = () => {
        if (streamRef.current) {
            streamRef.current.getTracks().forEach(track => track.stop())
            streamRef.current = null
        }
    }

    const switchCamera = () => {
        const newMode = facingMode === 'environment' ? 'user' : 'environment'
        setFacingMode(newMode)
        startCamera(newMode)
    }

    const snapPhoto = () => {
        if (!videoRef.current || !canvasRef.current) return
        const video = videoRef.current
        const canvas = canvasRef.current
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight

        const ctx = canvas.getContext('2d')
        if (ctx) {
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
            canvas.toBlob((blob) => {
                if (blob) {
                    const file = new File([blob], `cam-scan-${Date.now()}.jpg`, {type: 'image/jpeg'})
                    add([file])
                    closeCamera()
                }
            }, 'image/jpeg', 0.95) // Nén JPEG 95% giữ chất lượng OCR tốt
        }
    }

    const closeCamera = () => {
        stopCamera()
        setCameraOpen(false)
    }

    return (
        <div className="flex w-full flex-col gap-space-lg">
            <div className="flex flex-col gap-space-xs">
                <h1 className="font-headline-xl text-headline-xl font-bold tracking-tight text-primary">Bước 1: Tiếp
                    nhận &amp; Tải lên chứng từ</h1>
                <p className="max-w-3xl font-body-lg text-body-lg text-on-surface-variant">
                    Hệ thống căn thẳng góc ảnh, làm sạch, đọc OCR rồi bóc tách trường dữ liệu từ hóa đơn, hợp đồng và
                    danh thiếp.
                </p>
            </div>

            <div className="grid grid-cols-1 gap-space-md sm:grid-cols-2 xl:grid-cols-4">
                {KPI.map((k) => (
                    <div key={k.s} className="rounded-2xl bg-surface-container-high/90 p-space-md shadow-lg">
                        <div className="mb-space-xs flex items-center justify-between">
                            <span
                                className="font-label-sm text-label-sm tracking-wider text-outline">{STATUS_LABEL[k.s]}</span>
                            <div className={`flex h-8 w-8 items-center justify-center rounded-xl ${k.bg} ${k.tone}`}>
                                <span className="material-symbols-outlined text-[20px]">{k.icon}</span>
                            </div>
                        </div>
                        <span
                            className={`font-headline-lg text-headline-lg font-bold ${k.tone}`}>{stats[k.s] ?? '–'}</span>
                        <span className="ml-2 font-body-md text-body-md text-on-surface">chứng từ</span>
                    </div>
                ))}
            </div>

            <div className="grid grid-cols-1 gap-space-lg lg:grid-cols-12">
                <div className="flex flex-col gap-space-md lg:col-span-7">
                    <div
                        role="button"
                        tabIndex={0}
                        onClick={() => picker.current?.click()}
                        onKeyDown={(e) => e.key === 'Enter' && picker.current?.click()}
                        onDragOver={(e) => {
                            e.preventDefault();
                            setDrag(true)
                        }}
                        onDragLeave={() => setDrag(false)}
                        onDrop={(e) => {
                            e.preventDefault();
                            setDrag(false);
                            add(e.dataTransfer.files)
                        }}
                        className={`cursor-pointer rounded-3xl bg-gradient-to-br from-primary-container via-secondary to-tertiary-fixed-dim p-1 transition-all duration-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                            drag ? 'scale-[1.01] shadow-[0_0_40px_rgba(0,242,254,0.4)]' : 'shadow-[0_0_30px_rgba(0,242,254,0.15)] hover:shadow-[0_0_40px_rgba(0,242,254,0.3)]'
                        }`}
                    >
                        <div
                            className="flex flex-col items-center justify-center rounded-[22px] bg-surface-container-low/95 p-space-xl text-center">
                            <div
                                className="mb-space-md flex h-20 w-20 items-center justify-center rounded-2xl bg-gradient-to-tr from-primary-container to-secondary shadow-[0_0_24px_rgba(0,242,254,0.4)]">
                                <span
                                    className="material-symbols-outlined text-[40px] text-surface-container-lowest">cloud_upload</span>
                            </div>
                            <h3 className="mb-space-xs font-headline-md text-headline-md font-bold text-primary">Kéo thả
                                chứng từ vào đây</h3>
                            <p className="max-w-md font-body-lg text-body-lg text-on-surface-variant">
                                hoặc <span
                                className="font-semibold text-primary-container underline underline-offset-4">bấm để chọn tệp</span> (ảnh
                                JPG, PNG hoặc PDF)
                            </p>
                        </div>
                    </div>

                    <input ref={picker} type="file" multiple accept={ACCEPT} hidden onChange={(e) => {
                        add(e.target.files);
                        e.target.value = ''
                    }}/>

                    {files.length > 0 && (
                        <div
                            className="flex flex-col gap-space-md rounded-3xl bg-surface-container-low/95 p-space-lg shadow-xl">
                            {files.map((f, i) => (
                                <div key={`${f.name}-${i}`} className="flex items-center justify-between gap-space-sm">
                                    <div className="flex min-w-0 items-center gap-space-sm">
                                        <span
                                            className="material-symbols-outlined text-secondary">{f.type === 'application/pdf' ? 'picture_as_pdf' : 'image'}</span>
                                        <span
                                            className="truncate font-title-md text-body-md font-semibold text-on-surface">{f.name}</span>
                                        <span
                                            className="rounded bg-surface-container-high px-2 py-0.5 font-label-sm text-label-sm text-on-surface-variant">{size(f.size)}</span>
                                    </div>
                                    <button
                                        type="button"
                                        disabled={busy}
                                        onClick={() => setFiles(files.filter((_, j) => j !== i))}
                                        className="flex items-center gap-1 rounded-lg bg-error-container/20 px-3 py-1.5 font-body-sm text-body-sm text-error hover:bg-error-container/40 disabled:opacity-40"
                                    >
                                        <span className="material-symbols-outlined text-[16px]">delete</span>Xóa
                                    </button>
                                </div>
                            ))}
                            {err && <p role="alert" className="font-body-md text-body-md text-error">{err}</p>}
                            <button
                                type="button"
                                disabled={busy}
                                onClick={start}
                                className="flex items-center justify-center gap-2 self-end rounded-xl bg-gradient-to-r from-primary-container via-secondary to-secondary-fixed px-space-xl py-3 font-title-md text-title-md font-bold text-surface-container-lowest shadow-[0_0_24px_rgba(0,242,254,0.4)] transition-all hover:brightness-110 active:scale-95 disabled:cursor-wait disabled:opacity-60"
                            >
                                <span className="material-symbols-outlined text-[22px]">rocket_launch</span>
                                {busy ? 'Đang tải lên…' : `Bắt đầu phân tích ${files.length} tài liệu`}
                            </button>
                        </div>
                    )}
                </div>

                <div className="lg:col-span-5">
                    <div
                        className="flex h-full flex-col justify-between gap-space-md rounded-3xl bg-surface-container-low/90 p-space-lg shadow-xl overflow-hidden relative">
                        {!cameraOpen ? (
                            <>
                                <div className="flex flex-col gap-space-md">
                                    <div className="flex items-center gap-space-xs">
                                        <span
                                            className="material-symbols-outlined text-[24px] text-secondary">photo_camera</span>
                                        <h3 className="font-title-lg text-title-lg font-bold text-on-surface">Chụp trực
                                            tiếp</h3>
                                    </div>
                                    <p className="font-body-md text-body-md text-on-surface-variant">
                                        Sử dụng camera của thiết bị để chụp hóa đơn, hệ thống sẽ tự động chuyển đổi
                                        thành tệp ảnh chất lượng cao.
                                    </p>
                                    <div className="grid grid-cols-2 gap-2">
                                        <div
                                            className="flex flex-col items-center gap-1 rounded-xl bg-surface-container-high/60 p-space-md text-center">
                                            <span
                                                className="material-symbols-outlined text-[20px] text-secondary">crop_free</span>
                                            <span className="font-label-sm text-label-sm font-semibold text-on-surface">Căn thẳng &amp; cắt viền</span>
                                            <span className="text-[11px] leading-tight text-on-surface-variant">Chống méo lệch</span>
                                        </div>
                                        <div
                                            className="flex flex-col items-center gap-1 rounded-xl bg-surface-container-high/60 p-space-md text-center">
                                            <span
                                                className="material-symbols-outlined text-[20px] text-primary-container">contrast</span>
                                            <span className="font-label-sm text-label-sm font-semibold text-on-surface">Làm sạch ảnh</span>
                                            <span className="text-[11px] leading-tight text-on-surface-variant">Tăng tương phản</span>
                                        </div>
                                    </div>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => startCamera()}
                                    className="flex w-full cursor-pointer items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-primary-container to-secondary px-space-md py-3.5 font-title-md text-title-md font-bold text-surface-container-lowest shadow-[0_0_20px_rgba(0,242,254,0.35)] transition-all hover:brightness-110 active:scale-[0.99]"
                                >
                                    <span className="material-symbols-outlined text-[22px]">photo_camera</span>
                                    Mở máy ảnh quét hóa đơn
                                </button>
                            </>
                        ) : (
                            <div className="flex flex-col h-full bg-black/10 rounded-2xl relative">
                                <video
                                    ref={videoRef}
                                    autoPlay
                                    playsInline
                                    muted
                                    className="w-full h-full object-cover rounded-xl border border-outline/20"
                                    style={{minHeight: '260px'}}
                                />
                                <canvas ref={canvasRef} className="hidden"/>

                                <div className="absolute top-2 right-2 flex gap-2">
                                    <button onClick={switchCamera}
                                            className="bg-surface-container-highest/80 p-2 rounded-full text-on-surface backdrop-blur hover:bg-surface-container-highest">
                                        <span className="material-symbols-outlined">cameraswitch</span>
                                    </button>
                                    <button onClick={closeCamera}
                                            className="bg-error-container/80 p-2 rounded-full text-error backdrop-blur hover:bg-error-container">
                                        <span className="material-symbols-outlined">close</span>
                                    </button>
                                </div>

                                <div className="absolute bottom-4 left-0 right-0 flex justify-center">
                                    <button
                                        onClick={snapPhoto}
                                        className="h-16 w-16 bg-white rounded-full border-4 border-primary/50 shadow-lg flex items-center justify-center active:scale-95 transition-transform"
                                    >
                                        <div className="h-12 w-12 bg-primary rounded-full"></div>
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}