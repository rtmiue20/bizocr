export type Status = 'PENDING' | 'NEED_REVIEW' | 'APPROVED' | 'REJECTED'

export interface Doc {
    id: number
    fileName: string
    status: string
    documentType?: string | null
    receivedAt?: string | null
    rawOcrText?: string | null
    extractedFields?: string | null
    reviewedBy?: string | null
    reviewedAt?: string | null
}

export interface DocPage {
    items: Doc[]
    totalElements: number
    page: number
    size: number
    totalPages: number
}

export const STATUS_LABEL: Record<string, string> = {
    PENDING: 'Đang xử lý',
    NEED_REVIEW: 'Cần duyệt',
    APPROVED: 'Đã duyệt',
    REJECTED: 'Từ chối',
}
