export type Status = 'PENDING' | 'NEED_REVIEW' | 'APPROVED' | 'REJECTED'

export interface Doc {
    id: number
    fileName: string
    filePath?: string | null
    status: string
    documentType?: string | null
    receivedAt?: string | null
    rawOcrText?: string | null
    extractedFields?: string | null
    predictedFieldsJson?: string | null
    contactName?: string | null
    contactTitle?: string | null
    taxCode?: string | null
    confidenceScore?: number | null
    matchedBy?: string | null
    usedAiFallback?: boolean | null
    modelVersion?: string | null
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

// ── Contact / Namecard Types ──────────────────────────────
export type DedupStatus = 'OK' | 'POSSIBLE_DUPLICATE'

export interface Contact {
    id: number
    ownerCompanyId: number
    createdBy?: number
    documentId?: number
    companyId?: number
    fullName: string
    normalizedName?: string
    title?: string
    department?: string
    phone?: string
    mobile?: string
    email?: string
    website?: string
    metAt?: string
    metDate?: string
    notes?: string
    tags?: string
    lastContacted?: string
    dedupStatus: DedupStatus
    possibleDuplicateId?: number
    createdAt?: string
    updatedAt?: string
}

export interface ContactSummary {
    id: number
    ownerCompanyId: number
    documentId?: number
    companyId?: number
    companyName?: string
    fullName: string
    title?: string
    department?: string
    phone?: string
    mobile?: string
    email?: string
    website?: string
    tags?: string
    metAt?: string
    lastContacted?: string
    dedupStatus: DedupStatus
    possibleDuplicateId?: number
    frontThumbKey?: string
    frontStorageKey?: string
    createdAt?: string
}

export interface ContactSearchResult {
    items: ContactSummary[]
    totalElements: number
    page: number
    size: number
    totalPages: number
}

export interface DocumentImage {
    id: number
    documentId: number
    side: 'FRONT' | 'BACK'
    storageKey: string
    thumbKey?: string
    ocrStatus: string
    rawTextBack?: string
    uploadedAt?: string
}

export interface Company {
    id: number
    taxCode?: string
    legalName: string
    shortName?: string
    address?: string
    phone?: string
    email?: string
    website?: string
    industry?: string
    legalRepresentative?: string
    verificationStatus?: string
}

export interface ContactDetailResponse {
    contact: Contact
    company?: Company | null
    images: DocumentImage[]
    possibleDuplicateContact?: Contact | null
}
