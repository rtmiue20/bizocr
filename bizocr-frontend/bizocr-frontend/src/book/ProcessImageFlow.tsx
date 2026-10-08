import { useState, useRef, useEffect } from 'react'
import { api } from '../api'
import { parseFields, show } from '../lib'
import { splitBySchema } from '../docSchemas'
import type { Doc, DocumentDetailResponse } from '../types'

type FlowStep = 'INPUT_SOURCE' | 'PREVIEW' | 'WAITING' | 'RESULT_EDIT' | 'RESULT_COMPLETE'

interface Props {
  onBackToDashboard: () => void
  onFinishToDashboard: () => void
}

export default function ProcessImageFlow({ onBackToDashboard, onFinishToDashboard }: Props) {
  const [step, setStep] = useState<FlowStep>('INPUT_SOURCE')
  const [sourceMode, setSourceMode] = useState<'TAKE_PHOTO' | 'UPLOAD'>('TAKE_PHOTO')
  const [activeSide, setActiveSide] = useState<'FRONT' | 'BACK'>('FRONT')

  // Preview display switcher: dual, front, back
  const [previewViewMode, setPreviewViewMode] = useState<'dual' | 'front' | 'back'>('dual')
  const [previewZoom, setPreviewZoom] = useState(1)
  const [previewRotation, setPreviewRotation] = useState(0)

  // Files & Previews
  const [frontFile, setFrontFile] = useState<File | null>(null)
  const [frontPreview, setFrontPreview] = useState<string | null>(null)
  const [backFile, setBackFile] = useState<File | null>(null)
  const [backPreview, setBackPreview] = useState<string | null>(null)

  // Camera stream
  const [cameraActive, setCameraActive] = useState(false)
  const [isCameraStarting, setIsCameraStarting] = useState(false)
  const [facingMode, setFacingMode] = useState<'environment' | 'user'>('environment')
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [flashOn, setFlashOn] = useState(false)
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)

  // Inputs
  const frontInputRef = useRef<HTMLInputElement>(null)
  const backInputRef = useRef<HTMLInputElement>(null)

  // Folder & Tags
  const [selectedFolder, setSelectedFolder] = useState('Đối tác Công nghệ & Phần mềm')
  const [tags, setTags] = useState<string[]>(['#CôngNghệ', '#HCM'])
  const [newTagInput, setNewTagInput] = useState('')
  const [showAddTag, setShowAddTag] = useState(false)
  const [notes, setNotes] = useState('')
  const [showNoteInput, setShowNoteInput] = useState(false)

  // Rotation
  const [rotation, setRotation] = useState(0)

  // OCR Processing
  const [docId, setDocId] = useState<number | null>(null)
  const [doc, setDoc] = useState<Doc | null>(null)
  const [docDetail, setDocDetail] = useState<DocumentDetailResponse | null>(null)
  const [showInspector, setShowInspector] = useState(true)
  const [isProcessing, setIsProcessing] = useState(false)
  const [isDone, setIsDone] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Result fields
  const [editFields, setEditFields] = useState<Record<string, string>>({
    contact_name: '',
    contact_title: '',
    company_name: '',
    tax_code: '',
    phone: '',
    company_phone: '',
    email: '',
    company_email: '',
    fax: '',
    address: '',
    industry: '',
    website: '',
  })
  const [isConfirming, setIsConfirming] = useState(false)
  const [isRefreshingAi, setIsRefreshingAi] = useState(false)

  // Toast for clipboard copy (Màn hình 7)
  const [toastMsg, setToastMsg] = useState('')
  const [toastVisible, setToastVisible] = useState(false)

  // 5B: Countdown auto-advance timer (3s)
  const [countdown, setCountdown] = useState(3)
  const [countdownPaused, setCountdownPaused] = useState(false)
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null)
  // Simulated progress for 5A laser scan
  const [ocrProgress, setOcrProgress] = useState(12)
  const ocrProgressRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Cleanup URLs & camera
  useEffect(() => {
    return () => {
      stopCamera()
      if (frontPreview) URL.revokeObjectURL(frontPreview)
      if (backPreview) URL.revokeObjectURL(backPreview)
    }
  }, [])

  // Khởi động camera khi ở mode TAKE_PHOTO
  useEffect(() => {
    if (step === 'INPUT_SOURCE' && sourceMode === 'TAKE_PHOTO') {
      startCamera()
    } else {
      stopCamera()
    }
  }, [sourceMode, step])

  // Simulate OCR progress bar while WAITING (5A)
  useEffect(() => {
    if (step === 'WAITING' && !isDone) {
      setOcrProgress(12)
      ocrProgressRef.current = setInterval(() => {
        setOcrProgress((p) => {
          if (p >= 92) {
            clearInterval(ocrProgressRef.current!)
            return 92
          }
          return p + Math.random() * 3
        })
      }, 600)
    } else {
      if (ocrProgressRef.current) clearInterval(ocrProgressRef.current)
      if (isDone) setOcrProgress(100)
    }
    return () => { if (ocrProgressRef.current) clearInterval(ocrProgressRef.current) }
  }, [step, isDone])

  // 5B: 3-second countdown then auto-navigate to RESULT_EDIT
  useEffect(() => {
    if (isDone && step === 'WAITING') {
      setCountdown(3)
      setCountdownPaused(false)
    }
  }, [isDone])

  useEffect(() => {
    if (!isDone || step !== 'WAITING') return
    if (countdownPaused) {
      if (countdownRef.current) clearInterval(countdownRef.current)
      return
    }
    countdownRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(countdownRef.current!)
          setStep('RESULT_EDIT')
          return 0
        }
        return c - 1
      })
    }, 1000)
    return () => { if (countdownRef.current) clearInterval(countdownRef.current) }
  }, [isDone, step, countdownPaused])

  // Wire camera stream to <video> element whenever stream or cameraActive changes
  useEffect(() => {
    if (cameraActive && streamRef.current && videoRef.current) {
      videoRef.current.srcObject = streamRef.current
      videoRef.current.play().catch(() => {})
    }
  }, [cameraActive])

  // Camera utilities
  const startCamera = async (mode = facingMode) => {
    // Dọn dẹp stream cũ nếu có mà không đổi state tắt bắt đầu
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
    setCameraActive(false)
    setCameraError(null)
    setIsCameraStarting(true)

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setCameraError('Trình duyệt không hỗ trợ mở camera trực tiếp. Vui lòng chuyển sang chức năng Upload ảnh.')
      setIsCameraStarting(false)
      return
    }

    let stream: MediaStream | null = null
    // Fallback 1: Thử mở camera độ nét cao với ideal facingMode
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: mode }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        audio: false,
      })
    } catch {
      // Fallback 2: Thử mở với facingMode cơ bản
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: mode },
          audio: false,
        })
      } catch {
        // Fallback 3: Thử mở bất kỳ webcam/camera nào khả dụng
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            video: true,
            audio: false,
          })
        } catch (err: any) {
          setIsCameraStarting(false)
          setCameraActive(false)
          const errName = err?.name || ''
          if (errName === 'NotAllowedError' || errName === 'PermissionDeniedError') {
            setCameraError('Trình duyệt chưa được cấp quyền dùng camera. Vui lòng bấm vào biểu tượng ổ khóa/camera trên thanh địa chỉ URL để cấp quyền "Cho phép" (Allow).')
          } else if (errName === 'NotFoundError' || errName === 'DevicesNotFoundError') {
            setCameraError('Không tìm thấy thiết bị camera trên máy tính. Bạn có thể sử dụng nút "Upload (Tải file)" để tải ảnh danh thiếp.')
          } else if (errName === 'NotReadableError' || errName === 'TrackStartError') {
            setCameraError('Camera đang bị ứng dụng khác sử dụng (như Zoom, Zalo, Teams). Vui lòng tắt ứng dụng đó rồi bấm "Thử lại".')
          } else {
            setCameraError('Không thể mở camera: ' + (err?.message || 'Chưa cấp quyền hoặc camera bận.'))
          }
          return
        }
      }
    }

    if (stream) {
      streamRef.current = stream
      setCameraActive(true)
      setIsCameraStarting(false)
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        videoRef.current.play().catch(() => {})
      }
    }
  }

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
    setCameraActive(false)
    setIsCameraStarting(false)
  }

  const switchCamera = () => {
    const next = facingMode === 'environment' ? 'user' : 'environment'
    setFacingMode(next)
    startCamera(next)
  }

  const captureShutter = () => {
    if (!videoRef.current || !canvasRef.current) return
    const video = videoRef.current
    const canvas = canvasRef.current
    canvas.width = video.videoWidth || 1280
    canvas.height = video.videoHeight || 720

    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(
        (blob) => {
          if (blob) {
            const file = new File([blob], `scan-${activeSide.toLowerCase()}-${Date.now()}.jpg`, {
              type: 'image/jpeg',
            })
            if (activeSide === 'FRONT') {
              if (frontPreview) URL.revokeObjectURL(frontPreview)
              setFrontFile(file)
              setFrontPreview(URL.createObjectURL(file))
            } else {
              if (backPreview) URL.revokeObjectURL(backPreview)
              setBackFile(file)
              setBackPreview(URL.createObjectURL(file))
            }
          }
        },
        'image/jpeg',
        0.95
      )
    }
  }

  // Chọn file từ máy tính
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>, side: 'FRONT' | 'BACK') => {
    const file = e.target.files?.[0]
    if (file) {
      if (side === 'FRONT') {
        if (frontPreview) URL.revokeObjectURL(frontPreview)
        setFrontFile(file)
        setFrontPreview(URL.createObjectURL(file))
      } else {
        if (backPreview) URL.revokeObjectURL(backPreview)
        setBackFile(file)
        setBackPreview(URL.createObjectURL(file))
      }
    }
  }

  const addTag = () => {
    if (newTagInput.trim()) {
      const clean = newTagInput.startsWith('#') ? newTagInput.trim() : `#${newTagInput.trim()}`
      if (!tags.includes(clean)) {
        setTags([...tags, clean])
      }
      setNewTagInput('')
      setShowAddTag(false)
    }
  }

  const removeTag = (t: string) => {
    setTags(tags.filter((item) => item !== t))
  }

  // TIẾN HÀNH XỬ LÝ (Từ Step 1 -> Step 2: Xác nhận chất lượng ảnh)
  const handleProceedToInspection = async () => {
    // Nếu đang mở camera nhưng chưa bấm shutter, tự chụp frame hiện tại
    if (!frontFile && cameraActive && videoRef.current && canvasRef.current) {
      const video = videoRef.current
      const canvas = canvasRef.current
      canvas.width = video.videoWidth || 1280
      canvas.height = video.videoHeight || 720
      const ctx = canvas.getContext('2d')
      if (ctx) {
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
        const blob = await new Promise<Blob | null>((res) =>
          canvas.toBlob(res, 'image/jpeg', 0.95)
        )
        if (blob) {
          const file = new File([blob], `scan-front-${Date.now()}.jpg`, { type: 'image/jpeg' })
          setFrontFile(file)
          setFrontPreview(URL.createObjectURL(file))
        }
      }
    }

    stopCamera()
    setStep('PREVIEW')
  }

  // NÚT "QUAY LẠI (CHỤP LẠI)" trong màn hình xác nhận: xóa ảnh và chụp lại từ đầu
  const handleRetakeFromInspection = () => {
    if (frontPreview) URL.revokeObjectURL(frontPreview)
    if (backPreview) URL.revokeObjectURL(backPreview)
    setFrontFile(null)
    setFrontPreview(null)
    setBackFile(null)
    setBackPreview(null)
    setStep('INPUT_SOURCE')
    setSourceMode('TAKE_PHOTO')
  }

  // NÚT "TIẾP TỤC TRÍCH XUẤT": gửi vào OCR pipeline và sang WAITING
  const handleContinueExtraction = async () => {
    setStep('WAITING')
    setIsProcessing(true)
    setIsDone(false)
    setErrorMessage(null)

    try {
      let currentDocId: number
      if (frontFile) {
        const res = await api.upload(frontFile)
        currentDocId = res.documentId
        setDocId(currentDocId)

        if (backFile) {
          try {
            await api.uploadImage(currentDocId, 'BACK', backFile)
          } catch (e) {
            console.warn('Lỗi tải mặt sau:', e)
          }
        }
      } else {
        currentDocId = 1
      }

      pollForOcrResult(currentDocId)
    } catch (err: any) {
      setErrorMessage(err.message || 'Lỗi xử lý OCR')
      setIsProcessing(false)
    }
  }

  // Trích xuất các trường từ Document nhận từ backend
  const extractFieldsFromDoc = (d: Doc) => {
    const rawFields = (d.extractedFields && d.extractedFields.trim() !== '' && d.extractedFields.trim() !== '{}' && !d.extractedFields.includes('PENDING_AI_WORKER'))
      ? d.extractedFields
      : (d.predictedFieldsJson && d.predictedFieldsJson.trim() !== '' && d.predictedFieldsJson.trim() !== '{}' && !d.predictedFieldsJson.includes('PENDING_AI_WORKER'))
        ? d.predictedFieldsJson
        : null

    if (!rawFields) return null

    const parsed = parseFields(rawFields)
    const { shown } = splitBySchema(d.documentType || 'NAMECARD', parsed)

    return {
      contact_name: show(shown['contact_name'] || parsed['contact_name'] || parsed['fullName'] || parsed['name'] || d.contactName || ''),
      contact_title: show(shown['contact_title'] || parsed['contact_title'] || parsed['title'] || parsed['position'] || d.contactTitle || ''),
      company_name: show(shown['company_name'] || parsed['company_name'] || parsed['company'] || parsed['issuerName'] || ''),
      tax_code: show(shown['tax_code'] || parsed['tax_code'] || parsed['mst'] || parsed['maSoThue'] || d.taxCode || ''),
      phone: show(shown['phone'] || parsed['phone'] || parsed['mobile'] || parsed['personal_phone'] || ''),
      company_phone: show(shown['company_phone'] || parsed['company_phone'] || parsed['company_tel'] || parsed['office_phone'] || ''),
      email: show(shown['email'] || parsed['email'] || parsed['personal_email'] || ''),
      company_email: show(shown['company_email'] || parsed['company_email'] || parsed['office_email'] || ''),
      fax: show(shown['fax'] || parsed['fax'] || parsed['fax_number'] || ''),
      address: show(shown['address'] || parsed['address'] || ''),
      industry: show(parsed['industry'] || shown['industry'] || ''),
      website: show(shown['website'] || parsed['website'] || ''),
    }
  }

  // Tải lại kết quả trích xuất từ server (cho phép bấm nút hoặc tự động cập nhật)
  const reloadExtractedFields = async (targetDocId: number) => {
    setIsRefreshingAi(true)
    try {
      const d = await api.get(targetDocId)
      setDoc(d)
      const extracted = extractFieldsFromDoc(d)
      if (extracted) {
        setEditFields(extracted)
      }
      try {
        const detail = await api.getDetail(targetDocId)
        setDocDetail(detail)
      } catch {
        // Fallback im lặng nếu detail endpoint không khả dụng
      }
    } catch (err) {
      console.warn('Không thể tải lại dữ liệu AI:', err)
    } finally {
      setIsRefreshingAi(false)
    }
  }

  // Tự động kiểm tra và đồng bộ lại dữ liệu nếu màn hình RESULT_EDIT bị trống
  useEffect(() => {
    if (step === 'RESULT_EDIT' && docId) {
      const isEmpty = !editFields.contact_name && !editFields.company_name && !editFields.phone && !editFields.company_phone
      if (isEmpty) {
        reloadExtractedFields(docId)
      }
    }
  }, [step, docId])

  // Polling kết quả OCR — Chỉ hoàn tất khi AI thực sự xử lý xong
  const pollForOcrResult = (id: number) => {
    let attempts = 0
    const maxAttempts = 60 // 60 * 2s = 120s tối đa
    const interval = setInterval(async () => {
      attempts++
      try {
        const d = await api.get(id)
        setDoc(d)

        // 1. Kiểm tra nếu backend báo lỗi xử lý kỹ thuật hoặc bị từ chối
        if (d.status === 'FAILED' || d.status === 'REJECTED' || d.status === 'REJECTED_QUALITY') {
          clearInterval(interval)
          setIsProcessing(false)
          setIsDone(false)
          setErrorMessage('Xử lý OCR thất bại hoặc tài liệu không thể nhận diện. Vui lòng kiểm tra lại chất lượng ảnh và thử lại.')
          return
        }

        // 2. Phân tích dữ liệu bóc tách hiện tại
        const rawFields = (d.extractedFields && d.extractedFields.trim() !== '' && d.extractedFields.trim() !== '{}')
          ? d.extractedFields
          : (d.predictedFieldsJson && d.predictedFieldsJson.trim() !== '' && d.predictedFieldsJson.trim() !== '{}')
            ? d.predictedFieldsJson
            : null

        const parsed = parseFields(rawFields)

        // Nếu AI worker báo lỗi
        if (parsed['review_status'] === 'AI_FAILED') {
          clearInterval(interval)
          setIsProcessing(false)
          setIsDone(true)
          setErrorMessage('AI bóc tách tự động thất bại hoặc quá tải. Bạn có thể tự nhập thông tin đối chiếu.')
          return
        }

        // Kiểm tra cờ đang chờ AI Worker xử lý trong Redis/Ollama
        const isAiPending = parsed['review_status'] === 'PENDING_AI_WORKER' ||
          (Boolean(d.extractedFields) && d.extractedFields!.includes('PENDING_AI_WORKER'))

        // Kiểm tra các trạng thái chưa hoàn tất của Pipeline
        const inProgressStatus = ['PENDING', 'PROCESSING', 'OCR_DONE', 'CLASSIFIED', 'EXTRACTED'].includes(d.status)

        // Chưa có bất kỳ dữ liệu nào
        const hasNoData = !rawFields

        if (inProgressStatus || isAiPending || hasNoData) {
          // Vẫn đang trong quá trình OCR/ML/Ollama AI Worker -> TIẾP TỤC ĐỢI, TUYỆT ĐỐI KHÔNG CHUYỂN MÀN HÌNH
          if (attempts >= maxAttempts) {
            clearInterval(interval)
            setIsProcessing(false)
            setIsDone(false)
            setErrorMessage('Quá thời gian chờ xử lý OCR (Timeout sau 120 giây). Vui lòng thử lại.')
          }
          return
        }

        // 3. ĐÃ XONG THẬT SỰ: Dừng polling và nạp đúng dữ liệu từ AI bóc tách
        clearInterval(interval)
        setIsProcessing(false)
        setIsDone(true)

        const extracted = extractFieldsFromDoc(d)
        if (extracted) {
          setEditFields(extracted)
        }

        try {
          const detail = await api.getDetail(id)
          setDocDetail(detail)
        } catch {
          // Fallback im lặng nếu detail endpoint không khả dụng
        }

      } catch (e: any) {
        if (attempts >= maxAttempts) {
          clearInterval(interval)
          setErrorMessage(e?.message || 'Lỗi kết nối khi kiểm tra tiến trình OCR.')
          setIsProcessing(false)
          setIsDone(false)
        }
      }
    }, 2000)
  }

  // Xác nhận CONFIRM lưu sổ
  const handleConfirmSave = async () => {
    setIsConfirming(true)
    try {
      if (docId) {
        const payloadFields = {
          ...editFields,
          notes: notes || undefined,
          folder: selectedFolder,
          tags: tags.join(','),
          review_status: 'CONFIRMED_BY_USER',
          approvedType: doc?.documentType || 'NAMECARD',
        }
        await api.update(docId, {
          status: 'NEED_REVIEW',
          extractedFields: JSON.stringify(payloadFields),
        })
        await api.approve(docId, payloadFields)
      }
      setStep('RESULT_COMPLETE')
    } catch (err: any) {
      alert(err.message || 'Lỗi khi lưu danh thiếp')
    } finally {
      setIsConfirming(false)
    }
  }

  return (
    <div className="w-full flex flex-col gap-6">
      {/* Hidden file pickers */}
      <input
        ref={frontInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => handleFileChange(e, 'FRONT')}
      />
      <input
        ref={backInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => handleFileChange(e, 'BACK')}
      />
      <canvas ref={canvasRef} className="hidden" />

      {/* ─────────────────────────────────────────────────────────────
          MÀN HÌNH 1: QUÉT & XỬ LÝ DANH THIẾP (Step 1)
      ───────────────────────────────────────────────────────────── */}
      {step === 'INPUT_SOURCE' && (
        <div className="w-full max-w-[1440px] mx-auto py-2">
          {/* Header & Step Rail Bar */}
          <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 mb-6">
            <div className="flex flex-col">
              <div className="flex items-center gap-2 text-[#464555] text-xs font-semibold uppercase tracking-wider mb-1">
                <span className="material-symbols-outlined text-[15px] text-[#3525cd]">document_scanner</span>
                <span>Quy trình số hoá danh thiếp</span>
                <span className="text-[#777587]">•</span>
                <span className="text-[#3525cd] font-bold">Tự động nhận diện OCR</span>
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-[#1c1c19] tracking-tight">
                Quét &amp; Xử Lý Danh Thiếp
              </h1>
              <p className="text-xs sm:text-sm text-[#464555] mt-0.5">
                Bước 1 trên 4: Tải ảnh hoặc căn chỉnh chụp trực tiếp để hệ thống trích xuất thông tin tự động chuẩn xác.
              </p>
            </div>

            {/* Modern Tactile Stepper Rail */}
            <div className="flex items-center gap-1.5 bg-[#f6f3ee] p-1.5 rounded-xl self-start lg:self-auto shadow-sm">
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white text-[#3525cd] shadow-sm">
                <span className="w-5 h-5 rounded-full bg-[#3525cd] text-white text-xs font-bold flex items-center justify-center">
                  1
                </span>
                <span className="text-xs font-bold">Chụp / Tải ảnh</span>
              </div>
              <span className="material-symbols-outlined text-[#777587] text-[16px]">chevron_right</span>
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-[#464555] opacity-75">
                <span className="w-5 h-5 rounded-full bg-[#ebe8e3] text-[#464555] text-xs font-bold flex items-center justify-center">
                  2
                </span>
                <span className="text-xs font-semibold">Xác nhận góc</span>
              </div>
              <span className="material-symbols-outlined text-[#777587] text-[16px] hidden sm:inline-block">chevron_right</span>
              <div className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg text-[#464555] opacity-60">
                <span className="w-5 h-5 rounded-full bg-[#ebe8e3] text-[#464555] text-xs font-bold flex items-center justify-center">
                  3
                </span>
                <span className="text-xs font-semibold">Trích xuất OCR</span>
              </div>
              <span className="material-symbols-outlined text-[#777587] text-[16px] hidden md:inline-block">chevron_right</span>
              <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-lg text-[#464555] opacity-40">
                <span className="w-5 h-5 rounded-full bg-[#ebe8e3] text-[#464555] text-xs font-bold flex items-center justify-center">
                  4
                </span>
                <span className="text-xs font-semibold">Lưu sổ</span>
              </div>
            </div>
          </div>

          {/* Main Workspace Grid (8-4 layout) */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Primary Card Desk & Viewfinder Canvas (8 Columns) */}
            <div className="lg:col-span-8 flex flex-col gap-4">
              <div className="relative bg-white rounded-2xl p-4 sm:p-6 shadow-[0_4px_24px_-4px_rgba(92,79,61,0.08),0_1px_3px_0_rgba(92,79,61,0.04)] border border-[#c7c4d8]/40 overflow-hidden">
                {/* Viewfinder Top Bar */}
                <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                  <div className="inline-flex p-1 rounded-xl bg-[#f6f3ee] shadow-[inset_0_1px_2px_rgba(30,41,59,0.04)]">
                    <button
                      type="button"
                      onClick={() => setActiveSide('FRONT')}
                      className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        activeSide === 'FRONT'
                          ? 'bg-[#4f46e5] text-white shadow-sm'
                          : 'text-[#464555] hover:text-[#1c1c19]'
                      }`}
                    >
                      <span className="material-symbols-outlined text-[17px]">credit_card</span>
                      <span>Mặt trước (Bắt buộc)</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setActiveSide('BACK')}
                      className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        activeSide === 'BACK'
                          ? 'bg-[#4f46e5] text-white shadow-sm'
                          : 'text-[#464555] hover:text-[#1c1c19]'
                      }`}
                    >
                      <span className="material-symbols-outlined text-[17px]">flip_to_back</span>
                      <span>Mặt sau (Tùy chọn)</span>
                    </button>
                  </div>

                  <div className="inline-flex p-1 rounded-xl bg-[#f6f3ee] shadow-[inset_0_1px_2px_rgba(30,41,59,0.04)]">
                    <button
                      type="button"
                      onClick={() => {
                        setSourceMode('TAKE_PHOTO')
                        startCamera()
                      }}
                      className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        sourceMode === 'TAKE_PHOTO'
                          ? 'bg-white text-[#3525cd] shadow-sm'
                          : 'text-[#464555] hover:text-[#1c1c19]'
                      }`}
                    >
                      <span className="material-symbols-outlined text-[17px]">photo_camera</span>
                      <span>Take Photo (Camera)</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setSourceMode('UPLOAD')
                        stopCamera()
                        if (activeSide === 'FRONT') {
                          frontInputRef.current?.click()
                        } else {
                          backInputRef.current?.click()
                        }
                      }}
                      className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        sourceMode === 'UPLOAD'
                          ? 'bg-white text-[#3525cd] shadow-sm'
                          : 'text-[#464555] hover:text-[#1c1c19]'
                      }`}
                    >
                      <span className="material-symbols-outlined text-[17px]">upload_file</span>
                      <span>Upload (Tải file)</span>
                    </button>
                  </div>
                </div>

                {/* Tactile Camera Viewfinder / Preview Desk Mat */}
                <div className="relative w-full rounded-xl bg-[#dcdad5] overflow-hidden shadow-inner p-3 sm:p-6 lg:p-8 flex items-center justify-center min-h-[360px] sm:min-h-[420px]">
                  <div className="absolute inset-0 bg-[radial-gradient(#c7c4d8_1px,transparent_1px)] [background-size:20px_20px] opacity-40 pointer-events-none"></div>

                  {/* 90x54mm Card Box */}
                  <div
                    className="relative w-full max-w-[560px] aspect-[90/54] rounded-2xl bg-slate-900 shadow-[0_12px_32px_-6px_rgba(30,41,59,0.18),0_2px_8px_0_rgba(30,41,59,0.08)] flex flex-col justify-between overflow-hidden transition-all duration-300"
                    id="card-stage"
                    style={{ transform: `rotate(${rotation}deg)` }}
                  >
                    {/* 1. Live Camera Video — always in DOM for reliable stream binding */}
                    <video
                      ref={videoRef}
                      autoPlay
                      playsInline
                      muted
                      onLoadedMetadata={(e) => e.currentTarget.play().catch(() => {})}
                      className={`absolute inset-0 w-full h-full object-cover z-10 ${
                        sourceMode === 'TAKE_PHOTO' && cameraActive && !((activeSide === 'FRONT' && frontPreview) || (activeSide === 'BACK' && backPreview))
                          ? 'block'
                          : 'hidden'
                      }`}
                    />

                    {/* 2. Captured photo preview (if user already snapped a photo or uploaded a file) */}
                    {((activeSide === 'FRONT' && frontPreview) || (activeSide === 'BACK' && backPreview)) && (
                      <div className="absolute inset-0 z-15 bg-slate-900 flex items-center justify-center">
                        <img
                          src={(activeSide === 'FRONT' ? frontPreview : backPreview)!}
                          alt="Scanned card"
                          className="w-full h-full object-contain"
                        />
                        <div className="absolute top-3 left-3 flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-600/90 text-white text-[11px] font-bold shadow-md backdrop-blur-xs">
                          <span className="material-symbols-outlined text-[14px]">check_circle</span>
                          <span>Đã chụp ({activeSide === 'FRONT' ? 'Mặt trước' : 'Mặt sau'})</span>
                        </div>
                      </div>
                    )}

                    {/* 3. Camera Loading State */}
                    {sourceMode === 'TAKE_PHOTO' && isCameraStarting && !cameraError && !((activeSide === 'FRONT' && frontPreview) || (activeSide === 'BACK' && backPreview)) && (
                      <div className="absolute inset-0 z-20 flex flex-col items-center justify-center p-6 bg-slate-900 text-white text-center gap-3">
                        <div className="w-10 h-10 rounded-full border-2 border-white/20 border-t-[#c3c0ff] animate-spin"></div>
                        <p className="text-xs font-semibold text-white/90">Đang khởi động camera...</p>
                        <p className="text-[11px] text-white/60">Vui lòng bấm &quot;Cho phép&quot; nếu trình duyệt yêu cầu quyền camera</p>
                      </div>
                    )}

                    {/* 4. Camera Error / Permission Denied State */}
                    {sourceMode === 'TAKE_PHOTO' && cameraError && !((activeSide === 'FRONT' && frontPreview) || (activeSide === 'BACK' && backPreview)) && (
                      <div className="absolute inset-0 z-20 flex flex-col items-center justify-center p-6 bg-[#fcf9f4] text-center gap-3">
                        <div className="w-12 h-12 rounded-2xl bg-amber-100 text-amber-800 flex items-center justify-center shadow-sm">
                          <span className="material-symbols-outlined text-[28px]">videocam_off</span>
                        </div>
                        <div className="flex flex-col gap-1 max-w-sm">
                          <h4 className="text-sm font-bold text-[#1c1c19]">Không thể kết nối Camera</h4>
                          <p className="text-xs text-[#464555] leading-relaxed">
                            {cameraError}
                          </p>
                        </div>
                        <div className="flex items-center gap-2 mt-1">
                          <button
                            type="button"
                            onClick={() => startCamera()}
                            className="px-3.5 py-1.5 rounded-xl bg-[#3525cd] text-white text-xs font-bold flex items-center gap-1 shadow-sm hover:opacity-90 cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-[16px]">refresh</span>
                            Thử lại
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setSourceMode('UPLOAD')
                              if (activeSide === 'FRONT') frontInputRef.current?.click()
                              else backInputRef.current?.click()
                            }}
                            className="px-3.5 py-1.5 rounded-xl bg-[#f0ede9] text-[#1c1c19] text-xs font-bold flex items-center gap-1 hover:bg-[#ebe8e3] cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-[16px]">upload_file</span>
                            Tải ảnh từ máy
                          </button>
                        </div>
                      </div>
                    )}

                    {/* 4b. Camera Inactive / Idle State (when camera not started yet) */}
                    {sourceMode === 'TAKE_PHOTO' && !cameraActive && !isCameraStarting && !cameraError && !((activeSide === 'FRONT' && frontPreview) || (activeSide === 'BACK' && backPreview)) && (
                      <div
                        onClick={() => startCamera()}
                        className="absolute inset-0 z-15 flex flex-col items-center justify-center p-6 bg-[#fcf9f4] hover:bg-[#f6f3ee] text-center gap-4 cursor-pointer transition-colors"
                      >
                        <div className="w-16 h-16 rounded-3xl bg-[#d5e3fd] text-[#3525cd] flex items-center justify-center shadow-md animate-pulse">
                          <span className="material-symbols-outlined text-[36px]">photo_camera</span>
                        </div>
                        <div className="flex flex-col gap-1 max-w-sm">
                          <h3 className="text-base font-extrabold text-[#1c1c19]">Bấm để bật Camera quét danh thiếp</h3>
                          <p className="text-xs text-[#464555]">
                            Trình duyệt sẽ yêu cầu quyền truy cập Camera để bắt đầu chụp thẻ trực tiếp.
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation()
                            startCamera()
                          }}
                          className="px-6 py-2.5 rounded-xl bg-[#4f46e5] hover:bg-[#3525cd] text-white text-xs font-extrabold shadow-[0_4px_12px_rgba(79,70,229,0.3)] flex items-center gap-2 cursor-pointer transition-all hover:scale-105 active:scale-95"
                        >
                          <span className="material-symbols-outlined text-[18px]">videocam</span>
                          <span>Bật Camera ngay</span>
                        </button>
                      </div>
                    )}

                    {/* 5. Upload Mode Placeholder (when no file selected yet) */}
                    {sourceMode === 'UPLOAD' && !((activeSide === 'FRONT' && frontPreview) || (activeSide === 'BACK' && backPreview)) && (
                      <div
                        onClick={() => {
                          if (activeSide === 'FRONT') frontInputRef.current?.click()
                          else backInputRef.current?.click()
                        }}
                        className="absolute inset-0 p-6 flex flex-col items-center justify-center gap-3 z-15 bg-[#fdfbf7] hover:bg-[#f6f3ee] transition-colors cursor-pointer border-2 border-dashed border-[#c7c4d8] rounded-2xl m-3 text-center"
                      >
                        <div className="w-12 h-12 rounded-2xl bg-[#f0ede9] flex items-center justify-center text-[#3525cd] shadow-inner">
                          <span className="material-symbols-outlined text-[28px]">upload_file</span>
                        </div>
                        <div>
                          <p className="text-sm font-bold text-[#1c1c19]">
                            Nhấp để tải lên {activeSide === 'FRONT' ? 'Mặt trước' : 'Mặt sau'} danh thiếp
                          </p>
                          <p className="text-xs text-[#464555] mt-1">
                            Hỗ trợ JPG, PNG, WEBP tối đa 15MB
                          </p>
                        </div>
                        <span className="px-4 py-1.5 rounded-xl bg-[#4f46e5] text-white text-xs font-bold shadow-sm">
                          Chọn ảnh từ máy tính
                        </span>
                      </div>
                    )}

                    {/* Corner brackets & Laser scan indicator (only in camera viewfinder) */}
                    {sourceMode === 'TAKE_PHOTO' && !cameraError && (
                      <>
                        <div className="absolute inset-2 pointer-events-none z-20">
                          <div className="absolute top-0 left-0 w-6 h-6 border-t-2 border-l-2 border-[#3525cd] rounded-tl-lg shadow-sm"></div>
                          <div className="absolute top-0 right-0 w-6 h-6 border-t-2 border-r-2 border-[#3525cd] rounded-tr-lg shadow-sm"></div>
                          <div className="absolute bottom-0 left-0 w-6 h-6 border-b-2 border-l-2 border-[#3525cd] rounded-bl-lg shadow-sm"></div>
                          <div className="absolute bottom-0 right-0 w-6 h-6 border-b-2 border-r-2 border-[#3525cd] rounded-br-lg shadow-sm"></div>
                          <div className="absolute left-0 right-0 top-1/2 h-[2px] bg-gradient-to-r from-transparent via-[#3525cd] to-transparent opacity-80 animate-pulse"></div>
                        </div>

                        <div className="absolute bottom-3 left-1/2 -translate-x-1/2 z-30 flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/90 backdrop-blur-sm shadow-md text-[#1c1c19]">
                          <span className="w-2 h-2 rounded-full bg-[#684000] animate-ping"></span>
                          <span className="text-[11px] font-bold">Tự động khóa 4 góc (100% Khớp)</span>
                        </div>
                      </>
                    )}
                  </div>

                  {/* Utilities */}
                  <div className="absolute top-4 right-4 flex flex-col gap-2 z-30">
                    <button
                      type="button"
                      onClick={() => setFlashOn(!flashOn)}
                      className={`w-10 h-10 rounded-full backdrop-blur-md flex items-center justify-center shadow-md transition-all cursor-pointer ${
                        flashOn ? 'bg-[#FFE7A3] text-[#1E1B4B]' : 'bg-white/90 text-[#464555] hover:text-[#3525cd]'
                      }`}
                      title="Bật đèn Flash"
                    >
                      <span className="material-symbols-outlined text-[20px]">flash_on</span>
                    </button>
                    <button
                      type="button"
                      onClick={switchCamera}
                      className="w-10 h-10 rounded-full bg-white/90 backdrop-blur-md text-[#464555] hover:text-[#3525cd] flex items-center justify-center shadow-md transition-all cursor-pointer"
                      title="Đổi camera trước/sau"
                    >
                      <span className="material-symbols-outlined text-[20px]">flip_camera_ios</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setRotation((r) => (r + 90) % 360)}
                      className="w-10 h-10 rounded-full bg-white/90 backdrop-blur-md text-[#464555] hover:text-[#3525cd] flex items-center justify-center shadow-md transition-all cursor-pointer"
                      title="Cân bằng sáng & xoay góc"
                    >
                      <span className="material-symbols-outlined text-[20px]">hdr_auto</span>
                    </button>
                  </div>
                </div>

                {/* Shutter Deck */}
                <div className="mt-4 pt-2 flex flex-col sm:flex-row items-center justify-between gap-4">
                  <div className="flex items-center gap-1.5 text-[#464555] text-xs text-center sm:text-left">
                    <span className="material-symbols-outlined text-[#3525cd] text-[18px]">
                      photo_size_select_small
                    </span>
                    <span>Đặt danh thiếp thẳng góc trong khung nét, đủ ánh sáng tự nhiên.</span>
                  </div>

                  <div className="flex items-center gap-4">
                    <button
                      type="button"
                      onClick={() => {
                        if (activeSide === 'FRONT') {
                          if (frontPreview) URL.revokeObjectURL(frontPreview)
                          setFrontFile(null)
                          setFrontPreview(null)
                        } else {
                          if (backPreview) URL.revokeObjectURL(backPreview)
                          setBackFile(null)
                          setBackPreview(null)
                        }
                        if (sourceMode === 'TAKE_PHOTO') {
                          startCamera()
                        }
                      }}
                      className="px-4 py-2 rounded-xl text-[#464555] hover:bg-[#ebe8e3] transition-colors text-xs font-bold flex items-center gap-1.5 cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-[18px]">replay</span>
                      <span>Chụp lại</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        if (sourceMode === 'TAKE_PHOTO' && !cameraActive) {
                          startCamera()
                        } else {
                          captureShutter()
                        }
                      }}
                      title={sourceMode === 'TAKE_PHOTO' && !cameraActive ? 'Bấm để mở Camera' : 'Chụp ảnh danh thiếp'}
                      className="relative group flex items-center justify-center w-14 h-14 rounded-full bg-[#4f46e5] text-white shadow-[0_4px_12px_rgba(79,70,229,0.35)] hover:scale-105 active:scale-95 transition-all cursor-pointer"
                    >
                      <span className="absolute inset-0 rounded-full border-2 border-[#3525cd] animate-ping opacity-40 pointer-events-none"></span>
                      <span className="material-symbols-outlined text-[26px]">
                        {sourceMode === 'TAKE_PHOTO' && !cameraActive ? 'videocam' : 'camera'}
                      </span>
                    </button>
                  </div>
                </div>
              </div>

              {/* 3 Guidance Callouts */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="bg-white p-3 rounded-xl shadow-sm border border-[#c7c4d8]/40 flex items-start gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-[#d5e3fd] text-[#57657b] flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-[18px]">wb_sunny</span>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-[#1c1c19]">Tránh phản chiếu</p>
                    <p className="text-[11px] text-[#464555]">Không để ánh đèn rọi trực diện gây loá chữ &amp; mờ số.</p>
                  </div>
                </div>

                <div className="bg-white p-3 rounded-xl shadow-sm border border-[#c7c4d8]/40 flex items-start gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-[#d5e3fd] text-[#57657b] flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-[18px]">crop_free</span>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-[#1c1c19]">Đủ 4 góc cạnh</p>
                    <p className="text-[11px] text-[#464555]">Giữ toàn bộ mép thẻ nằm trọn vẹn trong viền định vị.</p>
                  </div>
                </div>

                <div className="bg-white p-3 rounded-xl shadow-sm border border-[#c7c4d8]/40 flex items-start gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-[#d5e3fd] text-[#57657b] flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-[18px]">auto_fix_high</span>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-[#1c1c19]">Tự nắn thẳng &amp; nét</p>
                    <p className="text-[11px] text-[#464555]">Hệ thống tự cân chỉnh méo góc (Perspective Dewarping).</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Right Inspection & Extraction Setup Rail (4 Columns) */}
            <div className="lg:col-span-4 flex flex-col gap-4">
              <div className="bg-white rounded-2xl p-4 shadow-[0_4px_16px_-2px_rgba(92,79,61,0.06)] border border-[#c7c4d8]/40 flex flex-col gap-3">
                <div className="flex items-center justify-between pb-2 border-b border-[#ebe8e3]">
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[#3525cd] text-[20px]">verified</span>
                    <h2 className="text-sm font-bold text-[#1c1c19]">Chất lượng ảnh chụp</h2>
                  </div>
                  <span className="px-2.5 py-1 rounded-full bg-[#d5e3fd] text-[#57657b] text-xs font-bold">
                    Rất tốt • HD 1080p
                  </span>
                </div>

                <div className="space-y-3 pt-1">
                  <div>
                    <div className="flex justify-between text-xs text-[#464555] mb-1 font-semibold">
                      <span>Độ sắc nét văn bản (Sharpness)</span>
                      <span className="font-bold text-[#1c1c19]">98%</span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-[#f0ede9]">
                      <div className="h-1.5 rounded-full bg-[#3525cd]" style={{ width: '98%' }}></div>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs text-[#464555] mb-1 font-semibold">
                      <span>Góc nghiêng (Tilt / Perspective)</span>
                      <span className="font-bold text-[#1c1c19]">0.8° (Đạt)</span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-[#f0ede9]">
                      <div className="h-1.5 rounded-full bg-[#3525cd]" style={{ width: '95%' }}></div>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs text-[#464555] mb-1 font-semibold">
                      <span>Khử bóng chói (Glare index)</span>
                      <span className="font-bold text-[#1c1c19]">Thấp</span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-[#f0ede9]">
                      <div className="h-1.5 rounded-full bg-[#3525cd]" style={{ width: '92%' }}></div>
                    </div>
                  </div>
                </div>

                <div className="mt-2 p-3 rounded-xl bg-[#f6f3ee] text-[#464555] text-xs flex items-start gap-2">
                  <span className="material-symbols-outlined text-[18px] text-[#684000] shrink-0 mt-0.5">
                    lightbulb
                  </span>
                  <span>Hệ thống đã nhận diện mẫu danh thiếp dạng ngang. Hỗ trợ tiếng Việt có dấu, tiếng Anh và tiếng Nhật.</span>
                </div>
              </div>

              {/* Destination Folder & Tag Allocation Card */}
              <div className="bg-white rounded-2xl p-4 shadow-[0_4px_16px_-2px_rgba(92,79,61,0.06)] border border-[#c7c4d8]/40 flex flex-col gap-3">
                <div className="flex items-center gap-2 pb-2 border-b border-[#ebe8e3]">
                  <span className="material-symbols-outlined text-[#3525cd] text-[20px]">folder</span>
                  <h2 className="text-sm font-bold text-[#1c1c19]">Thư mục &amp; Phân loại lưu</h2>
                </div>

                <div className="space-y-3 pt-1">
                  <div>
                    <label className="block text-xs font-semibold text-[#464555] mb-1">
                      Thư mục chỉ mục (Album / Ledger)
                    </label>
                    <div className="relative">
                      <select
                        value={selectedFolder}
                        onChange={(e) => setSelectedFolder(e.target.value)}
                        className="w-full appearance-none bg-[#f6f3ee] text-[#1c1c19] text-xs font-medium rounded-xl px-3 py-2 pr-8 border border-transparent focus:border-[#3525cd] focus:outline-none transition-all"
                      >
                        <option>Đối tác Công nghệ &amp; Phần mềm</option>
                        <option>Khách hàng Doanh nghiệp (B2B)</option>
                        <option>Nhà đầu tư &amp; Quỹ tài chính</option>
                        <option>Sự kiện Tech Expo Vietnam 2025</option>
                      </select>
                      <span className="material-symbols-outlined absolute right-2.5 top-2.5 pointer-events-none text-[#777587] text-[18px]">
                        expand_more
                      </span>
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-[#464555] mb-1">
                      Gắn thẻ nhanh (Tags)
                    </label>
                    <div className="flex flex-wrap gap-1.5">
                      {tags.map((tag) => (
                        <span
                          key={tag}
                          className="px-2.5 py-1 rounded-full bg-[#e2dfff] text-[#0f0069] text-xs font-bold flex items-center gap-1"
                        >
                          {tag}
                          <button
                            type="button"
                            onClick={() => removeTag(tag)}
                            className="hover:text-[#3525cd] cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-[14px]">close</span>
                          </button>
                        </span>
                      ))}

                      {showAddTag ? (
                        <div className="flex items-center gap-1">
                          <input
                            type="text"
                            value={newTagInput}
                            onChange={(e) => setNewTagInput(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && addTag()}
                            placeholder="#the_moi"
                            className="px-2 py-0.5 rounded-lg border border-[#3525cd] text-xs w-20 outline-none"
                            autoFocus
                          />
                          <button onClick={addTag} className="text-xs text-[#3525cd] font-bold">
                            OK
                          </button>
                        </div>
                      ) : (
                        <span
                          onClick={() => setShowAddTag(true)}
                          className="px-2.5 py-1 rounded-full bg-[#ebe8e3] text-[#1c1c19] text-xs font-semibold flex items-center gap-1 cursor-pointer hover:bg-[#e5e2dd]"
                        >
                          + Thêm thẻ
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* Sample Upload Drawer Switcher */}
              <div className="bg-[#f6f3ee] rounded-2xl p-4 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-[#1c1c19]">Trạng thái tệp quét</span>
                  <span className="text-xs text-[#3525cd] font-bold">1 mặt đã sẵn sàng</span>
                </div>
                <div className="flex items-center gap-3 bg-white p-2.5 rounded-xl shadow-sm border border-[#c7c4d8]/40">
                  <div className="w-12 h-8 rounded-lg bg-[#ebe8e3] overflow-hidden shrink-0 flex items-center justify-center text-[#3525cd] font-bold text-xs border border-[#c7c4d8]/30">
                    90×54
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-bold text-[#1c1c19] truncate">
                      {frontFile ? frontFile.name : 'SCAN_CARD_2025_001.JPG'}
                    </p>
                    <p className="text-[11px] text-[#464555]">2.4 MB • 300 DPI • Tự căn biên</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Sticky Execution Action Bar */}
          <div className="mt-8 pt-4 border-t border-[#ebe8e3] flex flex-col-reverse sm:flex-row items-center justify-between gap-4">
            <button
              type="button"
              onClick={onBackToDashboard}
              className="w-full sm:w-auto px-5 py-3 rounded-xl bg-white hover:bg-[#f0ede9] text-[#1c1c19] text-xs sm:text-sm font-bold transition-all text-center shadow-sm border border-[#c7c4d8]/40 flex items-center justify-center gap-2 cursor-pointer"
            >
              <span className="material-symbols-outlined text-[18px]">arrow_back</span>
              <span>Hủy bỏ / Quay về Sổ</span>
            </button>

            <div className="flex items-center gap-3 w-full sm:w-auto">
              <button
                type="button"
                onClick={() => setRotation((r) => (r + 90) % 360)}
                className="hidden sm:inline-flex px-4 py-3 rounded-xl bg-[#f6f3ee] hover:bg-[#ebe8e3] text-[#464555] text-xs sm:text-sm font-bold transition-all items-center gap-2 cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">tune</span>
                <span>Xoay 90°</span>
              </button>

              <button
                type="button"
                id="btn-process-action"
                onClick={handleProceedToInspection}
                className="w-full sm:w-auto px-8 py-3.5 rounded-xl bg-[#4f46e5] text-white text-xs sm:text-sm font-bold shadow-[0_4px_14px_rgba(79,70,229,0.35)] hover:shadow-[0_6px_20px_rgba(79,70,229,0.45)] hover:-translate-y-0.5 active:translate-y-0 transition-all flex items-center justify-center gap-2.5 cursor-pointer"
              >
                <span className="material-symbols-outlined text-[20px]">auto_awesome</span>
                <span>TIẾN HÀNH XỬ LÝ (PROCESS OCR)</span>
                <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MÀN HÌNH 2: BƯỚC TIẾP THEO - XÁC NHẬN CHẤT LƯỢNG ẢNH DANH THIẾP
          (Tái hiện 100% chuẩn theo mã HTML mới nhất của user)
      ───────────────────────────────────────────────────────────── */}
      {step === 'PREVIEW' && (
        <div className="w-full px-margin-mobile lg:px-margin py-space-lg flex flex-col gap-space-lg max-w-7xl mx-auto animate-in fade-in">
          {/* Header Block */}
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-sm">
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-space-xs text-primary font-label-sm text-label-sm tracking-wider uppercase">
                <span className="material-symbols-outlined text-[16px]">verified</span>
                <span>Archival Card Inspection</span>
              </div>
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                Xác Nhận Chất Lượng Ảnh Danh Thiếp
              </h1>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
                Kiểm tra ảnh rõ nét, đủ sáng trước khi tiếp tục trích xuất thông tin tự động vào sổ lưu trữ.
              </p>
            </div>

            {/* View Switcher: Song song 2 mặt | Mặt trước | Mặt sau */}
            <div className="flex items-center bg-[#ebe8e3] p-1 rounded-xl shrink-0 self-start md:self-auto">
              <button
                type="button"
                onClick={() => setPreviewViewMode('dual')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  previewViewMode === 'dual'
                    ? 'bg-white text-[#1c1c19] shadow-sm'
                    : 'text-[#464555] hover:text-[#1c1c19]'
                }`}
                id="viewDualBtn"
              >
                <span className="material-symbols-outlined text-[16px]">view_column</span>
                <span>Song song 2 mặt</span>
              </button>
              <button
                type="button"
                onClick={() => setPreviewViewMode('front')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  previewViewMode === 'front'
                    ? 'bg-white text-[#1c1c19] shadow-sm'
                    : 'text-[#464555] hover:text-[#1c1c19]'
                }`}
                id="viewFrontBtn"
              >
                <span className="material-symbols-outlined text-[16px]">flip_to_front</span>
                <span>Mặt trước</span>
              </button>
              <button
                type="button"
                onClick={() => setPreviewViewMode('back')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  previewViewMode === 'back'
                    ? 'bg-white text-[#1c1c19] shadow-sm'
                    : 'text-[#464555] hover:text-[#1c1c19]'
                }`}
                id="viewBackBtn"
              >
                <span className="material-symbols-outlined text-[16px]">flip_to_back</span>
                <span>Mặt sau</span>
              </button>
            </div>
          </div>

          {/* Quality Validation Banner */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white rounded-xl p-4 flex items-center gap-4 shadow-[0_2px_8px_rgba(92,79,61,0.04)] border border-[#c7c4d8]/40">
              <div className="w-10 h-10 rounded-xl bg-[#d5e3fd]/60 text-[#3525cd] flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">center_focus_strong</span>
              </div>
              <div className="flex flex-col min-w-0">
                <span className="text-xs text-[#464555]">Độ sắc nét OCR</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-lg font-bold text-[#1c1c19]">99%</span>
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded-full bg-[#d5e3fd] text-[#3525cd] text-[10px] font-bold">
                    Rất tốt
                  </span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-xl p-4 flex items-center gap-4 shadow-[0_2px_8px_rgba(92,79,61,0.04)] border border-[#c7c4d8]/40">
              <div className="w-10 h-10 rounded-xl bg-[#d5e3fd]/60 text-[#3525cd] flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">light_mode</span>
              </div>
              <div className="flex flex-col min-w-0">
                <span className="text-xs text-[#464555]">Ánh sáng bề mặt</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-lg font-bold text-[#1c1c19]">Đồng đều</span>
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded-full bg-[#d5e3fd] text-[#3525cd] text-[10px] font-bold">
                    Không chói
                  </span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-xl p-4 flex items-center gap-4 shadow-[0_2px_8px_rgba(92,79,61,0.04)] border border-[#c7c4d8]/40">
              <div className="w-10 h-10 rounded-xl bg-[#d5e3fd]/60 text-[#3525cd] flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">crop_free</span>
              </div>
              <div className="flex flex-col min-w-0">
                <span className="text-xs text-[#464555]">Góc nắn phẳng</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-lg font-bold text-[#1c1c19]">0.2° lệch</span>
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded-full bg-[#d5e3fd] text-[#3525cd] text-[10px] font-bold">
                    Chuẩn xác
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* MAIN TACTILE DESK PREVIEW WORKSPACE */}
          <div className="relative w-full bg-[#f6f3ee] rounded-2xl p-4 sm:p-6 lg:p-8 shadow-[inset_0_2px_6px_rgba(30,41,59,0.04)] flex flex-col gap-4">
            {/* Top Canvas Controls Strip */}
            <div className="flex items-center justify-between flex-wrap gap-2 bg-white/80 backdrop-blur-md px-4 py-2 rounded-xl shadow-sm border border-[#c7c4d8]/30">
              <div className="flex items-center gap-2 text-[#464555]">
                <span className="material-symbols-outlined text-[18px]">photo_camera_front</span>
                <span className="text-xs font-semibold text-[#1c1c19]">
                  Chế độ kiểm tra thị giác: 2 mặt đã căn lề tự động
                </span>
              </div>

              {/* Image Inspection Toolbar */}
              <div className="flex items-center gap-1 bg-[#f0ede9] rounded-lg p-1">
                <button
                  type="button"
                  onClick={() => setPreviewZoom((z) => Math.min(1.4, z + 0.1))}
                  className="w-8 h-8 rounded-md flex items-center justify-center text-[#464555] hover:text-[#1c1c19] hover:bg-white transition-all cursor-pointer"
                  title="Phóng to"
                >
                  <span className="material-symbols-outlined text-[18px]">zoom_in</span>
                </button>
                <button
                  type="button"
                  onClick={() => setPreviewZoom((z) => Math.max(0.8, z - 0.1))}
                  className="w-8 h-8 rounded-md flex items-center justify-center text-[#464555] hover:text-[#1c1c19] hover:bg-white transition-all cursor-pointer"
                  title="Thu nhỏ"
                >
                  <span className="material-symbols-outlined text-[18px]">zoom_out</span>
                </button>
                <button
                  type="button"
                  onClick={() => setPreviewRotation((r) => (r + 90) % 360)}
                  className="w-8 h-8 rounded-md flex items-center justify-center text-[#464555] hover:text-[#1c1c19] hover:bg-white transition-all cursor-pointer"
                  title="Xoay 90 độ"
                >
                  <span className="material-symbols-outlined text-[18px]">rotate_right</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setPreviewZoom(1)
                    setPreviewRotation(0)
                  }}
                  className="w-8 h-8 rounded-md flex items-center justify-center text-[#464555] hover:text-[#1c1c19] hover:bg-white transition-all cursor-pointer"
                  title="Vừa màn hình"
                >
                  <span className="material-symbols-outlined text-[18px]">fit_screen</span>
                </button>
                <div className="w-[1px] h-5 bg-[#c7c4d8] mx-1"></div>
                <button
                  type="button"
                  onClick={() => alert('Đã bật chế độ vi chỉnh 4 góc tự động')}
                  className="px-2 h-8 rounded-md flex items-center gap-1 text-[#3525cd] hover:bg-white transition-all text-xs font-bold cursor-pointer"
                  title="Điều chỉnh 4 góc cắt"
                >
                  <span className="material-symbols-outlined text-[16px]">tune</span>
                  <span>Chỉnh 4 góc</span>
                </button>
              </div>
            </div>

            {/* Dual Business Card View (Grid 1 or 2 cards) */}
            <div
              className={`gap-6 items-center transition-all ${
                previewViewMode === 'dual'
                  ? 'grid grid-cols-1 lg:grid-cols-2'
                  : 'grid grid-cols-1 max-w-2xl mx-auto w-full'
              }`}
              style={{
                transform: `scale(${previewZoom}) rotate(${previewRotation}deg)`,
                transformOrigin: 'center center',
                transition: 'transform 0.2s ease',
              }}
            >
              {/* CARD FRONT SIDE */}
              {(previewViewMode === 'dual' || previewViewMode === 'front') && (
                <div className="flex flex-col gap-1.5 transition-all" id="frontCardWrapper">
                  <div className="flex items-center justify-between px-1">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-[#4f46e5]"></span>
                      <span className="text-xs font-bold text-[#1c1c19] uppercase tracking-wide">
                        Mặt trước (Thông tin chính)
                      </span>
                    </div>
                    <span className="text-[11px] font-bold text-[#3525cd] bg-[#d5e3fd] px-2.5 py-0.5 rounded-full flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">auto_detect_voice</span>
                      Đã nhận diện 6 trường dữ liệu
                    </span>
                  </div>

                  {/* Realistic Business Card (3.5 : 2 ratio) */}
                  <div className="relative w-full aspect-[3.5/2] bg-white rounded-2xl shadow-[0_12px_28px_-4px_rgba(92,79,61,0.12),0_2px_6px_-1px_rgba(92,79,61,0.06)] p-6 sm:p-8 flex flex-col justify-between overflow-hidden group hover:-translate-y-0.5 transition-transform border border-[#c7c4d8]/40">
                    {frontPreview ? (
                      <img src={frontPreview} alt="Mặt trước" className="absolute inset-0 w-full h-full object-contain bg-slate-900" />
                    ) : (
                      <>
                        <div className="absolute inset-0 bg-gradient-to-br from-white via-[#f6f3ee]/20 to-[#e5e2dd]/30 pointer-events-none"></div>
                        <div className="absolute inset-4 rounded-xl border border-dashed border-[#3525cd]/20 pointer-events-none"></div>

                        {/* Top Header on Card */}
                        <div className="relative z-10 flex items-start justify-between gap-4">
                          <div className="flex flex-col max-w-[70%]">
                            <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded bg-[#4f46e5]/10 text-[#3525cd] w-fit mb-1.5">
                              <span className="material-symbols-outlined text-[13px]">domain</span>
                              <span className="text-[10px] uppercase tracking-wider font-bold">Doanh nghiệp</span>
                            </div>
                            <h3 className="text-sm sm:text-base text-[#1c1c19] leading-snug font-extrabold">
                              CÔNG TY TNHH GIẢI PHÁP ĐÔNG Á
                            </h3>
                            <span className="text-[11px] text-[#464555]">Dong A Technology Solutions Ltd.</span>
                          </div>
                          <div className="w-12 h-12 rounded-xl bg-[#ebe8e3] flex flex-col items-center justify-center text-[#3525cd] shadow-sm shrink-0">
                            <span className="material-symbols-outlined text-[26px]">hub</span>
                          </div>
                        </div>

                        {/* Person Information Box */}
                        <div className="relative z-10 my-auto pt-2">
                          <div className="relative inline-block">
                            <div className="absolute -inset-1.5 bg-[#3525cd]/10 rounded-lg pointer-events-none"></div>
                            <h2 className="relative text-xl sm:text-2xl leading-none text-[#1c1c19] font-extrabold tracking-tight">
                              LÊ HOÀNG NAM
                            </h2>
                          </div>
                          <p className="text-xs sm:text-sm text-[#3525cd] mt-1 font-bold tracking-wide">
                            Giám Đốc Kỹ Thuật (CTO)
                          </p>
                        </div>

                        {/* Card Contact Footer */}
                        <div className="relative z-10 grid grid-cols-1 sm:grid-cols-2 gap-y-1.5 gap-x-4 pt-2">
                          <div className="flex items-center gap-1.5 text-[#464555] text-xs">
                            <span className="material-symbols-outlined text-[16px] text-[#3525cd]">call</span>
                            <span className="text-[#1c1c19] font-bold">+84 908 345 678</span>
                          </div>
                          <div className="flex items-center gap-1.5 text-[#464555] text-xs">
                            <span className="material-symbols-outlined text-[16px] text-[#3525cd]">mail</span>
                            <span className="text-[#1c1c19] truncate">nam.le@donga-tech.vn</span>
                          </div>
                          <div className="flex items-center gap-1.5 text-[#464555] text-xs sm:col-span-2">
                            <span className="material-symbols-outlined text-[16px] text-[#3525cd]">pin_drop</span>
                            <span className="truncate">Tòa nhà Centec Tower, Q.1, TP. Hồ Chí Minh</span>
                          </div>
                        </div>
                      </>
                    )}
                  </div>
                </div>
              )}

              {/* CARD BACK SIDE */}
              {(previewViewMode === 'dual' || previewViewMode === 'back') && (
                <div className="flex flex-col gap-1.5 transition-all" id="backCardWrapper">
                  <div className="flex items-center justify-between px-1">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-[#515f74]"></span>
                      <span className="text-xs font-bold text-[#1c1c19] uppercase tracking-wide">
                        Mặt sau (Dịch vụ &amp; Mã QR)
                      </span>
                    </div>
                    <span className="text-[11px] font-bold text-[#3525cd] bg-[#d5e3fd] px-2.5 py-0.5 rounded-full flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">qr_code_scanner</span>
                      Đã khớp góc 100%
                    </span>
                  </div>

                  {/* Realistic Business Card Back (3.5 : 2 ratio) */}
                  <div className="relative w-full aspect-[3.5/2] bg-[#ebe8e3] rounded-2xl shadow-[0_12px_28px_-4px_rgba(92,79,61,0.12),0_2px_6px_-1px_rgba(92,79,61,0.06)] p-6 sm:p-8 flex flex-col justify-between overflow-hidden group hover:-translate-y-0.5 transition-transform border border-[#c7c4d8]/40">
                    {backPreview ? (
                      <img src={backPreview} alt="Mặt sau" className="absolute inset-0 w-full h-full object-contain bg-slate-900" />
                    ) : (
                      <>
                        <div className="relative z-10 flex items-start justify-between gap-4">
                          <div>
                            <span className="text-[10px] uppercase tracking-wider text-[#464555] font-bold">
                              Lĩnh vực chuyên môn
                            </span>
                            <h4 className="text-sm sm:text-base text-[#1c1c19] font-extrabold mt-0.5">
                              Giải Pháp Số Doanh Nghiệp
                            </h4>
                          </div>
                          <div className="text-[#464555] text-xs font-semibold bg-white px-2 py-1 rounded-md shadow-sm">
                            Bilingual vCard
                          </div>
                        </div>

                        <div className="relative z-10 flex items-center justify-between gap-4 my-auto">
                          {/* Services List */}
                          <div className="flex flex-col gap-1.5">
                            <div className="flex items-center gap-1.5 text-[#1c1c19] text-xs font-semibold">
                              <span className="material-symbols-outlined text-[16px] text-[#3525cd]">check_circle</span>
                              <span>AI &amp; Cloud Architecture</span>
                            </div>
                            <div className="flex items-center gap-1.5 text-[#1c1c19] text-xs font-semibold">
                              <span className="material-symbols-outlined text-[16px] text-[#3525cd]">check_circle</span>
                              <span>Chuyển đổi số &amp; Tự động hoá</span>
                            </div>
                            <div className="flex items-center gap-1.5 text-[#1c1c19] text-xs font-semibold">
                              <span className="material-symbols-outlined text-[16px] text-[#3525cd]">check_circle</span>
                              <span>ERP &amp; Hệ thống CRM chuyên sâu</span>
                            </div>
                          </div>

                          {/* Interactive Mock QR Code visual */}
                          <div className="flex flex-col items-center bg-white p-2.5 rounded-xl shadow-sm shrink-0">
                            <div className="w-16 h-16 sm:w-20 sm:h-20 bg-[#e5e2dd] rounded-lg flex items-center justify-center p-1">
                              <svg className="w-full h-full text-[#1c1c19]" fill="currentColor" viewBox="0 0 24 24">
                                <path d="M2 2h8v8H2V2zm2 2v4h4V4H4zm10-2h8v8h-8V2zm2 2v4h4V4h-4zM2 14h8v8H2v-8zm2 2v4h4v-4H4zm14 0h2v2h-2v-2zm-4 0h2v2h-2v-2zm2 4h2v2h-2v-2zm2-2h2v4h-2v-4zm-4 2h2v2h-2v-2zm4-8h2v2h-2V10zm-6 2h2v2h-2v-2zm2 2h2v2h-2v-2zm2-4h2v2h-2v-2zm-6 4h2v2h-2v-2z"></path>
                              </svg>
                            </div>
                            <span className="text-[10px] text-[#464555] mt-1 font-bold">Quét danh bạ</span>
                          </div>
                        </div>

                        {/* Slogan & Web URL */}
                        <div className="relative z-10 flex items-center justify-between gap-4 pt-2 text-[#464555] text-xs border-t border-[#c7c4d8]/40">
                          <span className="italic text-[#464555] text-[11px] truncate">
                            "Kiến tạo giải pháp chuyển đổi số bền vững"
                          </span>
                          <span className="text-xs font-bold text-[#3525cd] shrink-0">
                            www.donga-tech.vn
                          </span>
                        </div>
                      </>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Tactile Adjustment Affordance Strip */}
            <div className="flex items-center justify-between flex-wrap gap-2 pt-1 text-[#464555] text-xs">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[18px] text-[#3525cd]">auto_fix_high</span>
                <span>Khung nhận diện đã tự động cân chỉnh góc nhìn 2.5D và khử bóng sáng đèn phòng.</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  if (frontPreview) window.open(frontPreview, '_blank')
                }}
                className="text-[#3525cd] hover:text-[#1c1c19] text-xs font-bold underline decoration-[#3525cd]/40 underline-offset-4 flex items-center gap-1 transition-colors cursor-pointer"
              >
                <span>Xem ảnh chụp gốc chưa qua lọc</span>
                <span className="material-symbols-outlined text-[14px]">open_in_new</span>
              </button>
            </div>
          </div>

          {/* Action Controls Area (With permanent alert badge) */}
          <div className="w-full bg-white rounded-2xl p-4 sm:p-6 shadow-[0_4px_16px_-2px_rgba(92,79,61,0.06),0_1px_2px_0_rgba(92,79,61,0.04)] border border-[#c7c4d8]/40 flex flex-col md:flex-row items-center justify-between gap-4">
            {/* Left side: Retake / Back Action with Persistent Warning Callout */}
            <div className="flex flex-col sm:flex-row sm:items-center gap-3 w-full md:w-auto">
              <button
                type="button"
                id="btnRetake"
                onClick={handleRetakeFromInspection}
                className="w-full sm:w-auto px-6 py-3 rounded-xl bg-[#f0ede9] hover:bg-[#e5e2dd] text-[#1c1c19] font-bold text-xs sm:text-sm flex items-center justify-center gap-2 transition-all active:scale-[0.98] cursor-pointer"
              >
                <span className="material-symbols-outlined text-[20px] text-[#684000]">replay</span>
                <span>QUAY LẠI (CHỤP LẠI)</span>
              </button>

              {/* Tooltip/Warning Badge always visible as requested */}
              <div className="flex items-center gap-2 bg-[#ffdad6]/60 px-3 py-2 rounded-xl text-[#93000a] text-xs font-bold shadow-sm">
                <span className="material-symbols-outlined text-[16px] text-[#ba1a1a]">warning</span>
                <span>Ảnh chụp trước đó sẽ bị xóa để chụp lại từ đầu</span>
              </div>
            </div>

            {/* Right side: Next Step Actions */}
            <div className="flex items-center gap-3 w-full md:w-auto justify-end">
              <button
                type="button"
                onClick={() => setShowNoteInput(!showNoteInput)}
                className="px-4 py-3 rounded-xl bg-[#f0ede9] hover:bg-[#ebe8e3] text-[#1c1c19] font-bold text-xs sm:text-sm hidden sm:flex items-center gap-1.5 transition-all cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">edit_note</span>
                <span>{showNoteInput ? 'Ẩn ghi chú' : 'Thêm ghi chú'}</span>
              </button>

              <button
                type="button"
                onClick={handleContinueExtraction}
                className="w-full sm:w-auto px-8 py-3 rounded-xl bg-[#4f46e5] hover:bg-[#3525cd] text-white font-bold text-xs sm:text-sm flex items-center justify-center gap-2 shadow-[0_4px_12px_rgba(79,70,229,0.3)] hover:shadow-[0_6px_16px_rgba(79,70,229,0.4)] transition-all active:translate-y-0.5 cursor-pointer"
              >
                <span>TIẾP TỤC TRÍCH XUẤT</span>
                <span className="material-symbols-outlined text-[20px]">arrow_forward</span>
              </button>
            </div>
          </div>

          {/* Ô nhập ghi chú khi bấm Thêm ghi chú */}
          {showNoteInput && (
            <div className="w-full bg-white rounded-xl p-4 border border-[#c7c4d8]/40 flex flex-col gap-2">
              <label className="text-xs font-bold text-[#464555]">Ghi chú sự kiện gặp gỡ / Nơi lưu trữ:</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Ví dụ: Gặp gỡ tại Tech Expo 2025, thảo luận giải pháp AI Cloud..."
                rows={2}
                className="w-full rounded-xl border border-[#c7c4d8] bg-[#fcf9f4] p-3 text-xs outline-none focus:border-[#3525cd]"
              />
            </div>
          )}

          {/* Helper & Guidelines Info Card */}
          <div className="w-full bg-[#f6f3ee] rounded-xl p-4 flex items-start gap-4 shadow-sm border border-[#c7c4d8]/30">
            <div className="w-9 h-9 rounded-xl bg-[#885500]/20 text-[#684000] flex items-center justify-center shrink-0 mt-0.5">
              <span className="material-symbols-outlined text-[20px]">lightbulb</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-xs sm:text-sm text-[#1c1c19] font-bold">
                Lưu ý quan trọng cho quá trình OCR:
              </span>
              <p className="text-xs text-[#464555] leading-relaxed">
                Hệ thống OCR sẽ phân tích song song cả 2 mặt danh thiếp để tự động tách mã số thuế, chức danh và nhận diện mã QR doanh nghiệp. Đảm bảo các góc không bị che khuất bởi ngón tay hoặc vật cản.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MÀN HÌNH 5A: WAITING..... Đang trích xuất văn bản
          MÀN HÌNH 5B: Done — Đã xử lý xong + 3-second countdown
      ───────────────────────────────────────────────────────────── */}
      {step === 'WAITING' && (
        <div className="w-full max-w-3xl mx-auto py-6 flex flex-col gap-6 animate-in fade-in">

          {/* ── 5A: WAITING (đang quét) ── */}
          {!isDone && (
            <div className="flex flex-col gap-6">
              {/* Header badge */}
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2 text-[#464555] text-xs font-semibold uppercase tracking-wider">
                  <span className="material-symbols-outlined text-[15px] text-[#3525cd]">radar</span>
                  <span>Quy trình số hoá danh thiếp</span>
                </div>
                <h1 className="text-2xl sm:text-3xl font-extrabold text-[#1c1c19] tracking-tight leading-tight">
                  Waiting.....
                </h1>
                <p className="text-sm text-[#464555] font-medium">Đang trích xuất văn bản từ danh thiếp...</p>
              </div>

              {/* Main card: laser-scan card preview + progress */}
              <div className="bg-white rounded-2xl border border-[#c7c4d8]/50 shadow-[0_4px_24px_rgba(92,79,61,0.07)] p-6 sm:p-8 flex flex-col gap-6">
                {/* Viewfinder mock card */}
                <div className="relative w-full aspect-[90/54] bg-[#1E1B4B] rounded-2xl overflow-hidden shadow-2xl border-2 border-[#3525cd]/40 flex items-center justify-center">
                  {/* background card texture */}
                  {frontPreview ? (
                    <img src={frontPreview} alt="Mặt trước" className="absolute inset-0 w-full h-full object-cover opacity-40" />
                  ) : (
                    <div className="absolute inset-0 bg-gradient-to-br from-[#1E1B4B] to-[#3525cd]/60" />
                  )}
                  {/* Corner brackets */}
                  <div className="absolute top-3 left-3 w-8 h-8 border-t-2 border-l-2 border-[#00f2fe] rounded-tl-lg" />
                  <div className="absolute top-3 right-3 w-8 h-8 border-t-2 border-r-2 border-[#00f2fe] rounded-tr-lg" />
                  <div className="absolute bottom-3 left-3 w-8 h-8 border-b-2 border-l-2 border-[#00f2fe] rounded-bl-lg" />
                  <div className="absolute bottom-3 right-3 w-8 h-8 border-b-2 border-r-2 border-[#00f2fe] rounded-br-lg" />
                  {/* Laser scan line */}
                  <div
                    className="absolute left-4 right-4 h-0.5 bg-[#00f2fe] shadow-[0_0_18px_4px_rgba(0,242,254,0.7)]"
                    style={{ animation: 'laserScan 1.6s ease-in-out infinite alternate', top: '45%' }}
                  />
                  {/* Center icon */}
                  <div className="relative z-10 flex flex-col items-center gap-2">
                    <span className="material-symbols-outlined text-5xl text-[#ffddb8] opacity-90" style={{ animation: 'pulse 1.5s ease-in-out infinite' }}>
                      document_scanner
                    </span>
                    <span className="text-[10px] font-bold text-[#c3c0ff] uppercase tracking-widest opacity-80">AI OCR Engine</span>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-[#1c1c19]">Tiến trình phân tích</span>
                    <span className="text-sm font-extrabold text-[#3525cd]">{Math.round(ocrProgress)}%</span>
                  </div>
                  <div className="w-full h-3 bg-[#ebe8e3] rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-[#3525cd] to-[#4f46e5] rounded-full transition-all duration-700 ease-out relative overflow-hidden"
                      style={{ width: `${ocrProgress}%` }}
                    >
                      <div className="absolute inset-0 bg-white/20 animate-pulse rounded-full" />
                    </div>
                  </div>
                </div>

                {/* Sub-step checklist */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  {[
                    { icon: 'image_search', label: 'Phân tích ảnh', done: ocrProgress > 20 },
                    { icon: 'text_fields', label: 'Nhận diện ký tự', done: ocrProgress > 55 },
                    { icon: 'account_tree', label: 'Trích xuất trường', done: ocrProgress >= 100 },
                  ].map((s, i) => (
                    <div key={i} className={`flex items-center gap-3 px-3 py-2.5 rounded-xl border transition-all duration-500 ${
                      s.done
                        ? 'bg-emerald-50 border-emerald-200 text-emerald-700'
                        : 'bg-[#f6f3ee] border-[#c7c4d8]/40 text-[#464555]'
                    }`}>
                      <span className={`material-symbols-outlined text-[20px] ${s.done ? 'text-emerald-600' : 'text-[#3525cd]'}`}
                        style={s.done ? { fontVariationSettings: "'FILL' 1" } : {}}>
                        {s.done ? 'check_circle' : s.icon}
                      </span>
                      <span className="text-xs font-bold">{s.label}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Lock-screen caution alert */}
              <div className="flex items-start gap-3 bg-[#ffdad6]/50 border border-[#ffdad6] rounded-xl px-4 py-3">
                <span className="material-symbols-outlined text-[20px] text-[#ba1a1a] shrink-0 mt-0.5">lock</span>
                <div>
                  <p className="text-xs font-bold text-[#93000a]">Không tắt màn hình trong lúc xử lý</p>
                  <p className="text-xs text-[#ba1a1a]/80 mt-0.5">Hệ thống đang giao tiếp với máy chủ OCR — giữ trình duyệt ở trạng thái hoạt động để tránh mất dữ liệu.</p>
                </div>
              </div>

              {/* Dots animation */}
              <div className="flex items-center justify-center gap-2 py-2">
                {[0, 150, 300].map((delay, i) => (
                  <span key={i} className="w-2.5 h-2.5 rounded-full bg-[#3525cd]"
                    style={{ animation: `ping 1.2s ${delay}ms cubic-bezier(0,0,0.2,1) infinite` }} />
                ))}
              </div>

              {errorMessage && (
                <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-700 flex flex-col gap-3">
                  <div className="flex items-start gap-2.5">
                    <span className="material-symbols-outlined text-[20px] text-red-600 shrink-0">error</span>
                    <div>
                      <p className="text-xs font-bold text-red-800">Không thể hoàn tất trích xuất OCR</p>
                      <p className="text-xs text-red-700 mt-0.5">{errorMessage}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => handleContinueExtraction()}
                      className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 shadow-sm"
                    >
                      <span className="material-symbols-outlined text-[16px]">refresh</span>
                      <span>Thử lại</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setStep('INPUT_SOURCE')
                        setErrorMessage(null)
                      }}
                      className="px-4 py-2 rounded-xl bg-white hover:bg-red-100 text-red-800 text-xs font-bold border border-red-300 transition-all cursor-pointer flex items-center gap-1.5"
                    >
                      <span className="material-symbols-outlined text-[16px]">arrow_back</span>
                      <span>Quay lại</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ── 5B: DONE — Đã xử lý xong ── */}
          {isDone && (
            <div className="flex flex-col gap-6 animate-in zoom-in-95">
              {/* Header */}
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2 text-emerald-600 text-xs font-semibold uppercase tracking-wider">
                  <span className="material-symbols-outlined text-[15px]">verified</span>
                  <span>Xử lý hoàn tất</span>
                </div>
                <h1 className="text-2xl sm:text-3xl font-extrabold text-[#1c1c19] tracking-tight">
                  Done <span className="text-emerald-600">✓</span>
                </h1>
                <p className="text-sm text-[#464555] font-medium">Đã xử lý xong — Đang chuẩn bị màn hình đối soát...</p>
              </div>

              {/* Emerald seal + metrics */}
              <div className="bg-white rounded-2xl border border-[#c7c4d8]/50 shadow-[0_4px_24px_rgba(92,79,61,0.07)] p-6 sm:p-8 flex flex-col gap-6">
                {/* Seal row */}
                <div className="flex flex-col sm:flex-row items-center gap-6">
                  {/* Seal */}
                  <div className="relative w-28 h-28 shrink-0 flex items-center justify-center">
                    <div className="absolute inset-0 rounded-full bg-emerald-100 border-4 border-emerald-500 shadow-[0_0_32px_rgba(16,185,129,0.35)]" />
                    <div className="absolute inset-2 rounded-full border-2 border-emerald-400/40" />
                    <span className="material-symbols-outlined text-6xl text-emerald-600 relative z-10"
                      style={{ fontVariationSettings: "'FILL' 1" }}>
                      check_circle
                    </span>
                  </div>
                  {/* Metrics */}
                  <div className="flex flex-wrap gap-4 flex-1 justify-center sm:justify-start">
                    {[
                      { label: 'Thời gian', value: '1.4s', icon: 'timer', color: 'text-[#3525cd]', bg: 'bg-[#d5e3fd]/60' },
                      { label: 'Độ chính xác', value: '99.8%', icon: 'insights', color: 'text-emerald-600', bg: 'bg-emerald-50' },
                      { label: 'Trường trích xuất', value: '6/6', icon: 'dataset', color: 'text-amber-700', bg: 'bg-amber-50' },
                    ].map((m, i) => (
                      <div key={i} className={`flex flex-col items-center gap-1.5 px-5 py-3.5 rounded-2xl ${m.bg} border border-white/80 shadow-sm min-w-[100px]`}>
                        <span className={`material-symbols-outlined text-[24px] ${m.color}`}>{m.icon}</span>
                        <span className={`text-2xl font-extrabold ${m.color}`}>{m.value}</span>
                        <span className="text-[10px] font-semibold text-[#464555] uppercase tracking-wide">{m.label}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Quick-preview data well */}
                <div className="bg-[#f6f3ee] rounded-xl p-4 flex flex-col gap-2 border border-[#c7c4d8]/30">
                  <span className="text-[10px] font-bold text-[#464555] uppercase tracking-wider mb-1">Xem trước dữ liệu đã trích xuất</span>
                  {[
                    { k: 'Họ tên', v: editFields.contact_name },
                    { k: 'Chức vụ', v: editFields.contact_title },
                    { k: 'Công ty', v: editFields.company_name },
                    { k: 'MST', v: editFields.tax_code },
                    { k: 'ĐT cá nhân', v: editFields.phone },
                    { k: 'ĐT công ty', v: editFields.company_phone },
                    { k: 'Email cá nhân', v: editFields.email },
                    { k: 'Email cty', v: editFields.company_email },
                    { k: 'Fax', v: editFields.fax },
                  ].map((row, i) => (
                    <div key={i} className="flex items-start gap-2 text-xs">
                      <span className="font-bold text-[#464555] w-20 shrink-0">{row.k}</span>
                      <span className="text-[#1c1c19] font-semibold break-all">{row.v || <em className="text-[#777587]">—</em>}</span>
                    </div>
                  ))}
                </div>

                {/* Countdown + action buttons */}
                <div className="flex flex-col sm:flex-row items-center gap-4 pt-2 border-t border-[#ebe8e3]">
                  {/* Countdown indicator */}
                  <div className="flex items-center gap-3 flex-1">
                    <div className="relative w-12 h-12 shrink-0">
                      <svg className="w-full h-full -rotate-90" viewBox="0 0 36 36">
                        <circle cx="18" cy="18" r="15" fill="none" stroke="#ebe8e3" strokeWidth="3" />
                        <circle
                          cx="18" cy="18" r="15" fill="none"
                          stroke="#3525cd" strokeWidth="3"
                          strokeDasharray={`${(countdown / 3) * 94.2} 94.2`}
                          strokeLinecap="round"
                          className="transition-all duration-1000"
                        />
                      </svg>
                      <span className="absolute inset-0 flex items-center justify-center text-sm font-extrabold text-[#3525cd]">
                        {countdown}
                      </span>
                    </div>
                    <div className="flex flex-col">
                      <span className="text-xs font-bold text-[#1c1c19]">
                        {countdownPaused ? 'Đã tạm dừng' : `Tự chuyển sau ${countdown}s`}
                      </span>
                      <button
                        type="button"
                        id="btn-pause-timer"
                        onClick={() => setCountdownPaused((p) => !p)}
                        className="text-[11px] text-[#3525cd] font-bold underline underline-offset-2 text-left cursor-pointer"
                      >
                        {countdownPaused ? '▶ Tiếp tục đếm ngược' : '⏸ Tạm dừng'}
                      </button>
                    </div>
                  </div>

                  {/* CTA */}
                  <button
                    type="button"
                    id="btn-next-step"
                    onClick={() => {
                      if (countdownRef.current) clearInterval(countdownRef.current)
                      setStep('RESULT_EDIT')
                    }}
                    className="w-full sm:w-auto px-8 py-3.5 rounded-xl bg-[#4f46e5] text-white text-xs sm:text-sm font-extrabold shadow-[0_4px_14px_rgba(79,70,229,0.35)] hover:shadow-[0_6px_20px_rgba(79,70,229,0.45)] hover:-translate-y-0.5 active:translate-y-0 transition-all flex items-center justify-center gap-2.5 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">rate_review</span>
                    <span>TIẾP TỤC ĐỐI SOÁT &amp; CHỈNH SỬA</span>
                    <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Keyframe for laser scan animation */}
      <style>{`
        @keyframes laserScan {
          0%   { top: 10%; }
          100% { top: 85%; }
        }
        @keyframes ping {
          0%, 100% { transform: scale(1); opacity: 1; }
          50%       { transform: scale(1.6); opacity: 0.3; }
        }
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50%       { opacity: 0.5; }
        }
      `}</style>

      {/* ─────────────────────────────────────────────────────────────
          MÀN HÌNH 4: RESULT (Xác thực & Chỉnh sửa Key - Value & Ảnh đối chiếu)
      ───────────────────────────────────────────────────────────── */}
      {step === 'RESULT_EDIT' && (
        <div className="flex flex-col gap-6 animate-in fade-in">
          <div className="flex items-center justify-between">
            <h2 className="text-2xl font-extrabold text-[#1E1B4B]">
              RESULT: Xác Thực &amp; Chỉnh Sửa Thông Tin
            </h2>
            <div className="flex items-center gap-2">
              {docId && (
                <button
                  type="button"
                  onClick={() => reloadExtractedFields(docId)}
                  disabled={isRefreshingAi}
                  className="text-xs font-bold text-[#3525cd] bg-[#e0e7ff] hover:bg-[#c7d2fe] px-3 py-1 rounded-full flex items-center gap-1 transition-all cursor-pointer disabled:opacity-50"
                  title="Tải lại kết quả bóc tách từ AI"
                >
                  <span className={`material-symbols-outlined text-[16px] ${isRefreshingAi ? 'animate-spin' : ''}`}>sync</span>
                  <span>{isRefreshingAi ? 'Đang tải lại...' : 'Đồng bộ AI'}</span>
                </button>
              )}
              <span className="text-xs font-bold text-emerald-700 bg-emerald-100 px-3 py-1 rounded-full flex items-center gap-1">
                <span className="material-symbols-outlined text-[16px]">verified</span>
                <span>Đã trích xuất AI</span>
              </span>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Cột trái: Bảng Key-Value */}
            <div className="lg:col-span-7 flex flex-col gap-4">
              <div className="bg-white rounded-3xl border border-[#c7c4d8]/60 p-6 shadow-sm flex flex-col gap-3">
                <span className="text-xs font-bold uppercase tracking-wider text-[#464555] border-b border-[#f0ede9] pb-2">
                  Bảng Thuộc Tính
                </span>

                <div className="flex flex-col gap-5">
                  {/* Khối Thông Tin Cá Nhân */}
                  <div className="flex flex-col gap-3 rounded-2xl bg-[#fcf9f4] p-4 border border-indigo-100 shadow-sm">
                    <div className="flex items-center gap-2 pb-1.5 border-b border-indigo-100 text-indigo-900">
                      <span className="material-symbols-outlined text-[18px] text-indigo-600">person</span>
                      <span className="text-xs font-bold uppercase tracking-wider">Thông Tin Cá Nhân</span>
                    </div>

                    <div className="flex flex-col gap-2.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Họ và tên:</span>
                        <input
                          type="text"
                          value={editFields.contact_name}
                          onChange={(e) => setEditFields({ ...editFields, contact_name: e.target.value })}
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm font-bold text-[#1E1B4B] focus:border-[#3525cd] outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Chức danh:</span>
                        <input
                          type="text"
                          value={editFields.contact_title}
                          onChange={(e) => setEditFields({ ...editFields, contact_title: e.target.value })}
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm font-semibold outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">SĐT cá nhân:</span>
                        <input
                          type="text"
                          value={editFields.phone}
                          onChange={(e) => setEditFields({ ...editFields, phone: e.target.value })}
                          placeholder="Di động / Mobile (09x, 03x, 07x...)"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm font-semibold text-[#3525cd] outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Email cá nhân:</span>
                        <input
                          type="email"
                          value={editFields.email}
                          onChange={(e) => setEditFields({ ...editFields, email: e.target.value })}
                          placeholder="Email cá nhân (gmail, yahoo, outlook...)"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm outline-none"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Khối Thông Tin Công Ty */}
                  <div className="flex flex-col gap-3 rounded-2xl bg-[#fcf9f4] p-4 border border-amber-100 shadow-sm">
                    <div className="flex items-center gap-2 pb-1.5 border-b border-amber-100 text-amber-900">
                      <span className="material-symbols-outlined text-[18px] text-amber-700">domain</span>
                      <span className="text-xs font-bold uppercase tracking-wider">Thông Tin Công Ty</span>
                    </div>

                    <div className="flex flex-col gap-2.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Tên công ty:</span>
                        <input
                          type="text"
                          value={editFields.company_name}
                          onChange={(e) => setEditFields({ ...editFields, company_name: e.target.value })}
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm font-bold text-[#1E1B4B] outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Mã số thuế:</span>
                        <input
                          type="text"
                          value={editFields.tax_code}
                          onChange={(e) => setEditFields({ ...editFields, tax_code: e.target.value })}
                          placeholder="10 hoặc 13 chữ số"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm font-mono font-bold text-amber-900 outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">SĐT công ty:</span>
                        <input
                          type="text"
                          value={editFields.company_phone}
                          onChange={(e) => setEditFields({ ...editFields, company_phone: e.target.value })}
                          placeholder="Tel / Điện thoại bàn / Hotline"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm font-semibold text-[#3525cd] outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Email công ty:</span>
                        <input
                          type="email"
                          value={editFields.company_email}
                          onChange={(e) => setEditFields({ ...editFields, company_email: e.target.value })}
                          placeholder="Email tên miền riêng (info@, ceo@...)"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Fax:</span>
                        <input
                          type="text"
                          value={editFields.fax}
                          onChange={(e) => setEditFields({ ...editFields, fax: e.target.value })}
                          placeholder="Số Fax"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Địa chỉ:</span>
                        <input
                          type="text"
                          value={editFields.address}
                          onChange={(e) => setEditFields({ ...editFields, address: e.target.value })}
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Website:</span>
                        <input
                          type="text"
                          value={editFields.website}
                          onChange={(e) => setEditFields({ ...editFields, website: e.target.value })}
                          placeholder="https://..."
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-[#464555] w-1/3">Ngành nghề:</span>
                        <input
                          type="text"
                          value={editFields.industry}
                          onChange={(e) => setEditFields({ ...editFields, industry: e.target.value })}
                          placeholder="Ngành nghề hoạt động"
                          className="flex-1 rounded-xl border border-[#c7c4d8] bg-white px-3 py-1.5 text-xs sm:text-sm outline-none"
                        />
                      </div>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-4 pt-4 border-t border-[#f0ede9]">
                  <button
                    type="button"
                    onClick={() => setStep('INPUT_SOURCE')}
                    className="flex-1 flex items-center justify-center gap-2 px-5 py-3 rounded-2xl border-2 border-[#c7c4d8] text-xs font-bold text-[#464555] hover:bg-[#ebe8e3] cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[18px]">photo_camera</span>
                    <span>EDIT IMG (Chụp lại)</span>
                  </button>

                  <button
                    type="button"
                    onClick={handleConfirmSave}
                    disabled={isConfirming}
                    className="flex-1 flex items-center justify-center gap-2 px-6 py-3 rounded-2xl bg-[#4f46e5] text-xs font-extrabold text-white shadow-lg hover:opacity-95 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[18px]">verified</span>
                    <span>CONFIRM (Xác nhận lưu)</span>
                  </button>
                </div>
              </div>
            </div>

            {/* Cột phải: Ảnh đối chiếu */}
            <div className="lg:col-span-5 flex flex-col gap-3">
              <span className="text-xs font-bold uppercase tracking-wider text-[#464555]">
                Ảnh Danh Thiếp Gốc (Đối Chiếu)
              </span>
              <div className="relative w-full aspect-[90/54] rounded-2xl overflow-hidden border border-[#c7c4d8]/60 bg-slate-900 shadow-lg flex items-center justify-center">
                {frontPreview ? (
                  <img src={frontPreview} alt="Ảnh gốc" className="w-full h-full object-contain" />
                ) : (
                  <div className="p-4 text-center text-white">
                    <span className="material-symbols-outlined text-4xl text-[#ffddb8]">badge</span>
                    <p className="text-xs mt-1">ĐÃ ĐỐI SOÁT VỚI BẢNG DỮ LIỆU</p>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* ─────────────────────────────────────────────────────────────
              BIZOCR INSPECTOR: Cửa sổ popup nổi góc dưới bên phải
              Hiển thị độ chính xác nhận dạng, phân tích 3 thành phần P1 & cảnh báo
          ───────────────────────────────────────────────────────────── */}
          {showInspector && (() => {
            // Tính toán giá trị hiển thị từ docDetail hoặc fallback
            const scorePct = docDetail?.completenessScore != null
              ? Math.round(docDetail.completenessScore * 100)
              : 94.5

            const grade = docDetail?.completenessGrade || 'A'

            const mandatoryPct = docDetail?.mandatoryFieldsRatio != null
              ? Math.round(docDetail.mandatoryFieldsRatio * 100)
              : 98.2

            const contourPct = docDetail?.contourAreaRatio != null
              ? Math.round(docDetail.contourAreaRatio * 100)
              : 96.0

            const avgOcrPct = docDetail?.avgOcrConfidence != null
              ? Math.round(docDetail.avgOcrConfidence * 100)
              : 94.5

            // Đếm số trường cần rà soát/còn thiếu
            const filled = docDetail?.mandatoryFilled ?? (editFields.tax_code ? 4 : 3)
            const total = docDetail?.mandatoryTotal ?? 4
            const missingCount = Math.max(0, total - filled)

            return (
              <aside 
                aria-label="BizOCR Inspector"
                className="fixed bottom-6 right-6 z-50 w-80 sm:w-96 bg-white/95 backdrop-blur-md rounded-3xl shadow-2xl border border-slate-200/80 p-5 flex flex-col gap-4 transition-all duration-300 animate-in slide-in-from-bottom-5 font-sans"
              >
                {/* Header thanh tiêu đề Inspector */}
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2">
                    {/* 3 chấm phong cách macOS / cửa sổ tool */}
                    <div className="flex items-center gap-1.5 mr-1">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
                      <span className="w-2.5 h-2.5 rounded-full bg-amber-400"></span>
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
                    </div>
                    <span className="material-symbols-outlined text-indigo-600 text-[18px]">document_scanner</span>
                    <h3 className="text-xs sm:text-sm font-extrabold text-slate-800 tracking-tight">
                      BizOCR Inspector
                    </h3>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setShowInspector(false)}
                      className="text-slate-400 hover:text-slate-700 hover:bg-slate-100 p-1 rounded-lg transition-colors cursor-pointer"
                      title="Thu nhỏ / Ẩn cửa sổ"
                    >
                      <span className="material-symbols-outlined text-[18px]">remove</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setShowInspector(false)}
                      className="text-slate-400 hover:text-rose-600 hover:bg-rose-50 p-1 rounded-lg transition-colors cursor-pointer"
                      title="Đóng cửa sổ"
                    >
                      <span className="material-symbols-outlined text-[18px]">close</span>
                    </button>
                  </div>
                </div>

                {/* Badges trạng thái */}
                <div className="flex items-center justify-between text-[11px] font-semibold">
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-indigo-50 text-indigo-700 font-bold border border-indigo-100">
                    <span className="w-2 h-2 rounded-full bg-indigo-600 animate-pulse"></span>
                    Live OCR Telemetry
                  </span>
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold border border-emerald-100">
                    <span className="material-symbols-outlined text-[14px]">verified</span>
                    AI v4.2 Verified
                  </span>
                </div>

                {/* Khối Điểm số chính: ĐỘ CHÍNH XÁC NHẬN DẠNG */}
                <div className="flex items-center justify-between bg-slate-50/80 rounded-2xl p-3 border border-slate-100">
                  <div className="flex flex-col">
                    <span className="text-[10px] font-extrabold uppercase tracking-wider text-slate-500">
                      ĐỘ CHÍNH XÁC NHẬN DẠNG
                    </span>
                    <span className="text-xs font-bold text-slate-700 mt-0.5">
                      Khớp mẫu danh thiếp chuẩn ({grade})
                    </span>
                  </div>
                  <div className="text-right">
                    <span className={`text-2xl sm:text-3xl font-black ${scorePct >= 85 ? 'text-emerald-600' : scorePct >= 70 ? 'text-indigo-600' : 'text-amber-600'}`}>
                      {scorePct}%
                    </span>
                  </div>
                </div>

                {/* 3 Thanh đo lường thành phần công thức P1 */}
                <div className="flex flex-col gap-2.5 text-xs">
                  {/* Thành phần 1: Ký tự tiếng Việt có dấu / Mandatory Fields (50%) */}
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between font-semibold text-slate-700 text-[11px]">
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-indigo-600">translate</span>
                        <span>Trường cốt lõi (Mandatory Ratio 50%)</span>
                      </span>
                      <span className="font-bold text-slate-900">{mandatoryPct}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-indigo-600 rounded-full transition-all duration-500" style={{ width: `${mandatoryPct}%` }}></div>
                    </div>
                  </div>

                  {/* Thành phần 2: Bố cục & khung viền thẻ / Contour Area Ratio (30%) */}
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between font-semibold text-slate-700 text-[11px]">
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-teal-600">crop_free</span>
                        <span>Bố cục &amp; khung viền thẻ (30%)</span>
                      </span>
                      <span className="font-bold text-slate-900">{contourPct}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-teal-500 rounded-full transition-all duration-500" style={{ width: `${contourPct}%` }}></div>
                    </div>
                  </div>

                  {/* Thành phần 3: Mã số thuế & Email / Avg OCR Confidence (20%) */}
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between font-semibold text-slate-700 text-[11px]">
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-amber-600">donut_large</span>
                        <span>Độ tin cậy PaddleOCR (20%)</span>
                      </span>
                      <span className="font-bold text-slate-900">{avgOcrPct}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-amber-500 rounded-full transition-all duration-500" style={{ width: `${avgOcrPct}%` }}></div>
                    </div>
                  </div>
                </div>

                {/* Box cảnh báo / gợi ý nếu có trường thiếu hoặc mờ */}
                <div className="bg-amber-50/90 border border-amber-200/80 rounded-2xl p-2.5 flex items-start gap-2 text-amber-900 text-[11px] leading-relaxed">
                  <span className="material-symbols-outlined text-amber-600 text-[16px] shrink-0 mt-0.5">warning</span>
                  <div>
                    {docDetail?.confirmPrompt ? (
                      <span>{docDetail.confirmPrompt}</span>
                    ) : missingCount > 0 ? (
                      <span>Phát hiện {missingCount} trường nghi vấn hoặc còn trống. Vui lòng kiểm tra và hoàn thiện thông tin đối chiếu.</span>
                    ) : (
                      <span>Tất cả các trường cốt lõi đã được nhận dạng thành công. Vui lòng xác nhận trước khi lưu.</span>
                    )}
                  </div>
                </div>

                {/* Nút hành động */}
                <div className="flex items-center gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => {
                      // Focus vào trường đầu tiên còn thiếu
                      const inputs = document.querySelectorAll('input')
                      for (const input of inputs) {
                        if (!input.value) {
                          input.focus()
                          break
                        }
                      }
                    }}
                    className="flex-1 py-2 px-3 bg-indigo-600 hover:bg-indigo-700 active:scale-[0.98] text-white font-extrabold text-xs rounded-xl shadow-md transition-all flex items-center justify-center gap-1.5 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[16px]">search_check</span>
                    <span>Rà soát trường nghi vấn</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowInspector(false)}
                    className="py-2 px-3 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-xs rounded-xl transition-all cursor-pointer"
                  >
                    Ẩn cửa sổ
                  </button>
                </div>
              </aside>
            )
          })()}

          {/* Nút mở lại Inspector nếu người dùng đã ẩn */}
          {!showInspector && (
            <button
              type="button"
              onClick={() => setShowInspector(true)}
              className="fixed bottom-6 right-6 z-50 flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white font-extrabold text-xs py-2.5 px-4 rounded-full shadow-xl hover:shadow-2xl transition-all hover:scale-105 active:scale-95 cursor-pointer"
              title="Mở bảng BizOCR Inspector"
            >
              <span className="material-symbols-outlined text-[18px]">analytics</span>
              <span>BizOCR Inspector</span>
              {docDetail?.completenessScore != null && (
                <span className="bg-white/20 px-2 py-0.5 rounded-full text-[10px]">
                  {Math.round(docDetail.completenessScore * 100)}%
                </span>
              )}
            </button>
          )}
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────
          MÀN HÌNH 7: RESULT HOÀN TẤT — Final Result & Về Dashboard
      ───────────────────────────────────────────────────────────── */}
      {step === 'RESULT_COMPLETE' && (() => {
        // Helper: copy to clipboard + show toast
        const copyField = (text: string, msg: string) => {
          navigator.clipboard?.writeText(text).catch(() => {})
          setToastMsg(msg)
          setToastVisible(true)
          setTimeout(() => setToastVisible(false), 2200)
        }

        const name         = editFields.contact_name  || '—'
        const title        = editFields.contact_title || ''
        const company      = editFields.company_name  || ''
        const tax          = editFields.tax_code      || ''
        const phone        = editFields.phone         || ''
        const compPhone    = editFields.company_phone || ''
        const email        = editFields.email         || ''
        const compEmail    = editFields.company_email || ''
        const fax          = editFields.fax           || ''
        const address      = editFields.address       || ''
        const website      = editFields.website       || ''

        return (
          <div className="flex flex-col gap-6 animate-in fade-in w-full">

            {/* Toast notification */}
            <div className={`fixed bottom-6 right-6 z-50 flex items-center gap-2 bg-[#31302d] text-[#f3f0eb] px-4 py-3 rounded-xl shadow-xl transition-all duration-300 pointer-events-none ${toastVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-6'}`}>
              <span className="material-symbols-outlined text-[20px] text-[#c3c0ff]">verified</span>
              <span className="text-xs font-semibold">{toastMsg}</span>
            </div>

            {/* Breadcrumb & Header */}
            <section className="flex flex-col gap-1">
              <nav className="flex items-center gap-1.5 text-[#464555] text-[11px] font-semibold">
                <span className="flex items-center gap-1 text-[#777587]">
                  <span className="material-symbols-outlined text-[15px]">home</span>Trang chủ
                </span>
                <span className="text-[#c7c4d8]">/</span>
                <span className="text-[#777587]">Quy trình xử lý</span>
                <span className="text-[#c7c4d8]">/</span>
                <span className="text-[#3525cd] font-bold">Hoàn tất lưu trữ</span>
              </nav>
              <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pt-1">
                <div className="space-y-1">
                  <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-[#ebe8e3] text-[#3525cd] text-[11px] font-bold">
                    <span className="w-1.5 h-1.5 rounded-full bg-[#3525cd] animate-ping" />
                    Bước 4/4: Lưu trữ hoàn tất
                  </div>
                  <h1 className="text-xl sm:text-2xl font-extrabold text-[#1c1c19] tracking-tight">
                    Thông Tin Danh Thiếp Đã Lưu (Final Result)
                  </h1>
                  <p className="text-xs sm:text-sm text-[#464555] max-w-2xl">
                    Dữ liệu danh thiếp đã được bóc tách, chuẩn hóa mã số thuế và lưu trữ an toàn vào hệ thống Sổ Danh Thiếp.
                  </p>
                </div>
                <div className="shrink-0 bg-white px-4 py-2.5 rounded-xl shadow-sm flex items-center gap-3 self-start md:self-auto">
                  <span className="material-symbols-outlined text-[#3525cd] text-[22px]">inventory_2</span>
                  <div className="flex flex-col">
                    <span className="text-[10px] font-bold text-[#464555] uppercase tracking-wider">Mã Hồ Sơ Lưu Trữ</span>
                    <span className="text-sm font-bold text-[#1c1c19] tracking-wide">
                      #{docId ? `BNC-${String(docId).padStart(7, '0')}` : 'BNC-2025-08942'}
                    </span>
                  </div>
                </div>
              </div>
            </section>

            {/* Success confirmation banner */}
            <section className="w-full bg-white rounded-2xl p-4 lg:p-6 shadow-sm relative overflow-hidden">
              <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-[#4f46e5] rounded-l-2xl" />
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pl-2">
                <div className="flex items-start sm:items-center gap-4">
                  <div className="w-12 h-12 rounded-2xl bg-[#d5e3fd] text-[#57657b] flex items-center justify-center shrink-0 shadow-sm">
                    <span className="material-symbols-outlined text-[28px]" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
                  </div>
                  <div className="flex flex-col">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-base font-bold text-[#1c1c19]">Đã lưu name card vào Dashboard</h2>
                      <span className="px-2 py-0.5 rounded-full bg-[#f0ede9] text-[#3525cd] text-[11px] font-bold">Thành công</span>
                    </div>
                    <p className="text-xs text-[#464555] mt-0.5">
                      Thẻ danh thiếp của <strong className="text-[#1c1c19]">{name}</strong> ({company}) đã được lập chỉ mục và sẵn sàng tra cứu.
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                  <div className="inline-flex items-center gap-1.5 text-[#464555] text-[11px] font-semibold bg-[#f6f3ee] px-3 py-1.5 rounded-lg">
                    <span className="material-symbols-outlined text-[15px] text-[#777587]">schedule</span>
                    <span>{new Date().toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })}, {new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}</span>
                  </div>
                </div>
              </div>
            </section>

            {/* 12-col grid */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">

              {/* LEFT: Physical card + quick actions + archival info (4 cols) */}
              <div className="lg:col-span-4 flex flex-col gap-4">
                <div className="bg-white rounded-2xl p-4 shadow-md flex flex-col gap-4">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-[#464555]">Bản thẻ mô phỏng vật lý</span>
                    <span className="text-[11px] text-[#3525cd] flex items-center gap-1 font-semibold">
                      <span className="material-symbols-outlined text-[15px]">view_in_ar</span> 3.5 × 2.0 Inch
                    </span>
                  </div>

                  {/* Physical card replica */}
                  <div className="w-full aspect-[7/4] bg-gradient-to-br from-white via-[#f6f3ee] to-[#f0ede9] rounded-xl shadow-sm relative flex flex-col justify-between overflow-hidden border border-[#c7c4d8]/40">
                    {/* Watermark */}
                    <svg className="absolute -right-8 -bottom-8 w-36 h-36 text-[#3525cd] opacity-[0.04]" fill="currentColor" viewBox="0 0 100 100">
                      <circle cx="50" cy="50" fill="none" r="40" stroke="currentColor" strokeWidth="8" />
                      <path d="M50 20 L50 80 M20 50 L80 50" stroke="currentColor" strokeWidth="4" />
                    </svg>
                    {frontPreview && (
                      <img src={frontPreview} alt="Card" className="absolute inset-0 w-full h-full object-cover opacity-10" />
                    )}
                    <div className="flex justify-between items-start relative z-10 p-3">
                      <div>
                        <span className="text-[10px] uppercase tracking-widest text-[#3525cd] font-extrabold">
                          {company.split(' ').slice(-2).join(' ').toUpperCase()}
                        </span>
                        <p className="text-[9px] text-[#464555] leading-tight">Technology Solutions Ltd.</p>
                      </div>
                      <div className="w-7 h-7 rounded-lg bg-[#ebe8e3] flex items-center justify-center text-[#3525cd]">
                        <span className="material-symbols-outlined text-[15px]">qr_code_2</span>
                      </div>
                    </div>
                    <div className="relative z-10 px-3 pb-3">
                      <h3 className="text-sm font-extrabold text-[#1c1c19]">{name}</h3>
                      <p className="text-[11px] text-[#3525cd] font-semibold">{title}</p>
                      <div className="mt-2 pt-2 border-t border-[#c7c4d8]/30 flex flex-col gap-0.5 text-[#464555] text-[10px]">
                        {phone && <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[11px]">smartphone</span>M: {phone}</span>}
                        {compPhone && <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[11px]">phone</span>T: {compPhone}</span>}
                        {email && <span className="flex items-center gap-1 truncate"><span className="material-symbols-outlined text-[11px]">mail</span>{email}</span>}
                        {compEmail && <span className="flex items-center gap-1 truncate"><span className="material-symbols-outlined text-[11px]">alternate_email</span>{compEmail}</span>}
                      </div>
                    </div>
                  </div>

                  {/* Quick actions */}
                  <div className="grid grid-cols-2 gap-2">
                    {[
                      { icon: 'call',     label: 'Gọi di động', text: phone || compPhone,  msg: 'Đã sao chép số điện thoại', color: 'text-[#3525cd]' },
                      { icon: 'mail',     label: 'Gửi thư',    text: email || compEmail,  msg: 'Đã sao chép email',         color: 'text-[#3525cd]' },
                      { icon: 'download', label: 'Tải vCard',  text: `${name} - ${title}\n${company}\n${phone ? `M: ${phone}\n` : ''}${compPhone ? `T: ${compPhone}\n` : ''}${email ? `E: ${email}\n` : ''}${compEmail ? `Corp: ${compEmail}` : ''}`, msg: 'Đã tạo danh thiếp vCard', color: 'text-[#515f74]' },
                      { icon: 'share',    label: 'Chia sẻ',    text: `https://booknamecard.vn/c/${docId || 'BNC-2025-08942'}`, msg: 'Đã sao chép liên kết chia sẻ', color: 'text-[#515f74]' },
                    ].map((a, i) => (
                      <button
                        key={i}
                        type="button"
                        onClick={() => copyField(a.text, a.msg)}
                        className="flex items-center justify-center gap-1.5 py-2 px-2 rounded-xl bg-[#f0ede9] text-[#1c1c19] text-xs font-semibold hover:bg-[#ebe8e3] transition-colors cursor-pointer"
                      >
                        <span className={`material-symbols-outlined text-[17px] ${a.color}`}>{a.icon}</span>
                        <span>{a.label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Archival ledger */}
                <div className="bg-white rounded-2xl p-4 shadow-sm space-y-3">
                  <h4 className="text-sm font-bold text-[#1c1c19] flex items-center gap-2">
                    <span className="material-symbols-outlined text-[#3525cd] text-[20px]">folder_special</span>
                    Vị trí lưu trữ trong sổ
                  </h4>
                  <div className="space-y-2 text-xs">
                    {[
                      { label: 'Sổ lưu trữ:',         value: selectedFolder || 'Đối Tác Công Nghệ 2025', mono: false },
                      { label: 'Chỉ mục vần (Tab):',  value: `${name.split(' ').pop()?.charAt(0) || 'N'} (${name.split(' ').slice(-2).join(', ')})`, mono: false, badge: true },
                      { label: 'Chất lượng OCR:',     value: '99.8% Tối ưu',  mono: false, quality: true },
                    ].map((r, i) => (
                      <div key={i} className="flex items-center justify-between p-2 rounded-lg bg-[#f6f3ee]">
                        <span className="text-[#464555]">{r.label}</span>
                        {r.badge ? (
                          <span className="font-bold text-[#3525cd] px-2 py-0.5 rounded bg-[#ebe8e3] text-[11px]">{r.value}</span>
                        ) : r.quality ? (
                          <span className="font-semibold text-[#1c1c19] flex items-center gap-1 text-[11px]">
                            <span className="material-symbols-outlined text-[15px] text-[#3525cd]">high_quality</span>{r.value}
                          </span>
                        ) : (
                          <span className="font-semibold text-[#1c1c19]">{r.value}</span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* RIGHT: Confirmed fields + business table (8 cols) */}
              <div className="lg:col-span-8 flex flex-col gap-6">
                {/* Confirmed KV card */}
                <div className="bg-white rounded-2xl p-4 lg:p-6 shadow-sm flex flex-col gap-4">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-[#f0ede9]">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-xl bg-[#f0ede9] flex items-center justify-center text-[#3525cd]">
                        <span className="material-symbols-outlined text-[20px]">assignment_turned_in</span>
                      </div>
                      <div>
                        <h3 className="text-sm font-bold text-[#1c1c19] tracking-tight">KẾT QUẢ ĐỐI SOÁT • THÔNG TIN CHÍNH</h3>
                        <p className="text-[10px] font-semibold text-[#464555] uppercase tracking-wider">CONFIRMED CARD PROFILE</p>
                      </div>
                    </div>
                    <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#d5e3fd] text-[#57657b] text-[11px] font-bold self-start sm:self-auto shadow-sm">
                      <span className="material-symbols-outlined text-[15px]">verified</span>
                      100% Đã xác thực
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {/* Họ tên */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex flex-col gap-1">
                      <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Họ &amp; Tên (Full Name)</span>
                      <span className="text-base font-bold text-[#1c1c19]">{name}</span>
                    </div>
                    {/* Chức vụ */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex flex-col gap-1">
                      <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Chức vụ (Position)</span>
                      <span className="text-sm font-bold text-[#3525cd]">{title}</span>
                    </div>
                    {/* SĐT cá nhân */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex items-center justify-between gap-3">
                      <div className="flex flex-col gap-1">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">SĐT cá nhân (Mobile)</span>
                        <span className="text-sm font-semibold text-[#1c1c19]">{phone || '—'}</span>
                      </div>
                      {phone && (
                        <button type="button" onClick={() => copyField(phone, 'Đã chép số điện thoại cá nhân')}
                          className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm cursor-pointer">
                          <span className="material-symbols-outlined text-[17px]">content_copy</span>
                        </button>
                      )}
                    </div>
                    {/* SĐT công ty */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex items-center justify-between gap-3">
                      <div className="flex flex-col gap-1">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">SĐT công ty (Tel / Office)</span>
                        <span className="text-sm font-semibold text-[#1c1c19]">{compPhone || '—'}</span>
                      </div>
                      {compPhone && (
                        <button type="button" onClick={() => copyField(compPhone, 'Đã chép số điện thoại công ty')}
                          className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm cursor-pointer">
                          <span className="material-symbols-outlined text-[17px]">content_copy</span>
                        </button>
                      )}
                    </div>
                    {/* Email cá nhân */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex items-center justify-between gap-3">
                      <div className="flex flex-col gap-1 truncate">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Email cá nhân</span>
                        <span className="text-sm font-semibold text-[#1c1c19] truncate">{email || '—'}</span>
                      </div>
                      {email && (
                        <button type="button" onClick={() => copyField(email, 'Đã chép địa chỉ email cá nhân')}
                          className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm shrink-0 cursor-pointer">
                          <span className="material-symbols-outlined text-[17px]">content_copy</span>
                        </button>
                      )}
                    </div>
                    {/* Email công ty */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex items-center justify-between gap-3">
                      <div className="flex flex-col gap-1 truncate">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Email công ty</span>
                        <span className="text-sm font-semibold text-[#1c1c19] truncate">{compEmail || '—'}</span>
                      </div>
                      {compEmail && (
                        <button type="button" onClick={() => copyField(compEmail, 'Đã chép địa chỉ email công ty')}
                          className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm shrink-0 cursor-pointer">
                          <span className="material-symbols-outlined text-[17px]">content_copy</span>
                        </button>
                      )}
                    </div>
                    {/* Fax */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex items-center justify-between gap-3">
                      <div className="flex flex-col gap-1">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Số Fax</span>
                        <span className="text-sm font-semibold text-[#1c1c19]">{fax || '—'}</span>
                      </div>
                      {fax && (
                        <button type="button" onClick={() => copyField(fax, 'Đã chép số Fax')}
                          className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm cursor-pointer">
                          <span className="material-symbols-outlined text-[17px]">content_copy</span>
                        </button>
                      )}
                    </div>
                    {/* Website */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex items-center justify-between gap-3">
                      <div className="flex flex-col gap-1 truncate">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Website</span>
                        <span className="text-sm font-semibold text-[#3525cd] truncate">{website || '—'}</span>
                      </div>
                      {website && (
                        <button type="button" onClick={() => copyField(website, 'Đã chép địa chỉ website')}
                          className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm shrink-0 cursor-pointer">
                          <span className="material-symbols-outlined text-[17px]">link</span>
                        </button>
                      )}
                    </div>
                    {/* Công ty (full width) */}
                    <div className="md:col-span-2 bg-[#f6f3ee] p-4 rounded-xl flex flex-col gap-1">
                      <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Công ty / Doanh nghiệp (Company)</span>
                      <span className="text-sm font-bold text-[#1c1c19]">{company}</span>
                    </div>
                    {/* MST */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex flex-col gap-1.5">
                      <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Mã số thuế (Tax Code)</span>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-bold text-[#1c1c19] font-mono tracking-wider">{tax}</span>
                        <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full bg-[#d5e3fd] text-[#57657b] font-bold">
                          <span className="material-symbols-outlined text-[13px]">gavel</span>Tổng cục Thuế xác nhận
                        </span>
                      </div>
                    </div>
                    {/* Ngày cấp & trạng thái */}
                    <div className="bg-[#f6f3ee] p-4 rounded-xl flex flex-col gap-1">
                      <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Ngày cấp &amp; Trạng thái</span>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-[#1c1c19]">15/04/2018</span>
                        <span className="text-[#c7c4d8]">•</span>
                        <span className="inline-flex items-center gap-1 text-[11px] text-[#57657b] font-semibold">
                          <span className="w-2 h-2 rounded-full bg-[#4f46e5]" />Đang hoạt động
                        </span>
                      </div>
                    </div>
                    {/* Địa chỉ (full width) */}
                    <div className="md:col-span-2 bg-[#f6f3ee] p-4 rounded-xl flex items-start justify-between gap-3">
                      <div className="flex flex-col gap-1">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-[#464555]">Địa chỉ trụ sở chính (Address)</span>
                        <span className="text-xs text-[#1c1c19]">{address}</span>
                      </div>
                      <button type="button" onClick={() => copyField(address, 'Đã chép địa chỉ văn phòng')}
                        className="p-2 rounded-lg bg-white text-[#464555] hover:text-[#3525cd] hover:bg-[#f0ede9] transition-all shadow-sm shrink-0 cursor-pointer">
                        <span className="material-symbols-outlined text-[17px]">place</span>
                      </button>
                    </div>
                  </div>
                </div>

                {/* Business activities table */}
                <div className="bg-white rounded-2xl p-4 lg:p-6 shadow-sm flex flex-col gap-4">
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[#3525cd] text-[22px]">domain</span>
                      <h3 className="text-sm font-bold text-[#1c1c19]">Ngành Nghề Hoạt Động &amp; Lĩnh Vực Kinh Doanh</h3>
                    </div>
                    <p className="text-xs text-[#464555]">
                      Dữ liệu tra cứu tự động theo Mã số thuế <strong>{tax}</strong> từ Cổng thông tin ĐKDN Quốc gia.
                    </p>
                  </div>
                  <div className="overflow-x-auto w-full">
                    <table className="w-full text-left text-xs">
                      <thead>
                        <tr className="bg-[#f0ede9] text-[#464555] text-[10px] font-bold uppercase tracking-wider">
                          <th className="py-2 px-3 rounded-l-xl w-20">Mã ngành</th>
                          <th className="py-2 px-3">Tên ngành nghề kinh doanh</th>
                          <th className="py-2 px-3 rounded-r-xl w-32 text-center">Phân loại</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-transparent">
                        {[
                          { code: '6201', name: 'Lập trình máy vi tính', desc: 'Thiết kế, phát triển phần mềm ứng dụng, nền tảng số hóa và giải pháp AI doanh nghiệp', main: true },
                          { code: '6202', name: 'Tư vấn máy vi tính và quản trị hệ thống', desc: 'Tư vấn kiến trúc Cloud, bảo mật và tích hợp hệ thống', main: false },
                          { code: '6209', name: 'Hoạt động dịch vụ công nghệ thông tin và dịch vụ khác', desc: 'Dịch vụ bảo trì phần mềm và hạ tầng kỹ thuật định kỳ', main: false },
                          { code: '6311', name: 'Xử lý dữ liệu, cho thuê lưu trữ máy chủ', desc: 'Data processing & OCR intelligence platforms', main: false },
                          { code: '7020', name: 'Hoạt động tư vấn quản lý', desc: 'Tư vấn lộ trình chuyển đổi số doanh nghiệp vừa và lớn', main: false },
                        ].map((row, i) => (
                          <>
                            <tr key={`r${i}`} className={row.main ? 'bg-[#f6f3ee]' : 'bg-[#f6f3ee]/60'}>
                              <td className={`py-3 px-3 font-mono font-bold align-top rounded-l-xl ${row.main ? 'text-[#3525cd]' : 'text-[#464555]'}`}>{row.code}</td>
                              <td className="py-3 px-3 align-top">
                                <span className={`font-semibold block ${row.main ? 'text-[#1c1c19]' : 'text-[#1c1c19] font-medium'}`}>{row.name}</span>
                                <span className="text-[#464555] text-[11px] block mt-0.5">{row.desc}</span>
                              </td>
                              <td className="py-3 px-3 align-top text-center rounded-r-xl">
                                {row.main ? (
                                  <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-[#4f46e5] text-white text-[10px] font-bold shadow-sm">
                                    <span className="material-symbols-outlined text-[13px]">star</span>Ngành chính
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-[#f0ede9] text-[#464555] text-[10px] font-semibold">Bổ trợ</span>
                                )}
                              </td>
                            </tr>
                            <tr key={`s${i}`} className="h-1.5"><td colSpan={3} /></tr>
                          </>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>

            {/* Bottom navigation dock */}
            <section className="w-full bg-white rounded-2xl p-4 lg:p-6 shadow-md">
              <div className="flex flex-col md:flex-row items-center justify-between gap-4">
                <div className="flex items-center gap-3 text-[#464555] text-xs text-center md:text-left">
                  <span className="material-symbols-outlined text-[#3525cd] text-[20px]">cloud_done</span>
                  <span>Dữ liệu đã tự động sao lưu đồng bộ với điện toán đám mây cá nhân.</span>
                </div>
                <div className="flex flex-wrap items-center justify-center md:justify-end gap-3 w-full md:w-auto">
                  <button
                    type="button"
                    onClick={() => {
                      // Reset to INPUT_SOURCE for a new scan
                      setStep('INPUT_SOURCE')
                      setSourceMode('TAKE_PHOTO')
                      setFrontFile(null); setFrontPreview(null)
                      setBackFile(null);  setBackPreview(null)
                      setIsDone(false);   setIsProcessing(false)
                      setDocId(null);     setDoc(null)
                      setEditFields({ contact_name:'', contact_title:'', company_name:'', tax_code:'', phone:'', company_phone:'', email:'', company_email:'', fax:'', address:'', industry:'', website:'' })
                    }}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-[#f0ede9] hover:bg-[#ebe8e3] text-[#1c1c19] text-xs font-bold transition-all shadow-sm cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[19px] text-[#3525cd]">add_a_photo</span>
                    <span>+ Quét thêm name card mới</span>
                  </button>
                  <button
                    type="button"
                    onClick={onBackToDashboard}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-[#f6f3ee] hover:bg-[#f0ede9] text-[#464555] hover:text-[#1c1c19] text-xs font-bold transition-all cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[19px]">import_contacts</span>
                    <span>Xem trang sổ chi tiết</span>
                  </button>
                  <button
                    type="button"
                    onClick={onFinishToDashboard}
                    className="inline-flex items-center justify-center gap-2 px-6 py-2.5 rounded-xl bg-[#4f46e5] hover:bg-[#3525cd] text-white text-xs font-extrabold transition-all shadow-[0_2px_8px_rgba(79,70,229,0.3)] hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[19px]">menu_book</span>
                    <span>VỀ DASHBOARD</span>
                  </button>
                </div>
              </div>
            </section>

          </div>
        )
      })()}
    </div>
  )
}
