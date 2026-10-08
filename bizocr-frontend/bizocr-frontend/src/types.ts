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

export interface DocumentDetailResponse {
    documentId: number
    fileName: string
    documentType?: string | null
    status: string
    matchedBy?: string | null
    confidenceScore?: number | null
    receivedAt?: string | null
    reviewedBy?: string | null
    reviewedAt?: string | null
    ocrEngine?: string | null
    extractedFields?: string | null
    completenessScore?: number | null
    completenessGrade?: string | null
    completenessLabel?: string | null
    mandatoryFieldsRatio?: number | null
    contourAreaRatio?: number | null
    avgOcrConfidence?: number | null
    mandatoryFilled?: number | null
    mandatoryTotal?: number | null
    confirmPrompt?: string | null
}

export interface DocPage {
    items: Doc[]
    totalElements: number
    page: number
    size: number
    totalPages: number
}

export interface CardItemResponse {
    id: number
    documentId?: number | null
    companyId?: number | null
    fullName: string
    title?: string | null
    department?: string | null
    companyName?: string | null
    taxCode?: string | null
    phone?: string | null
    mobile?: string | null
    email?: string | null
    companyPhone?: string | null
    companyEmail?: string | null
    fax?: string | null
    website?: string | null
    address?: string | null
    industry?: string | null
    imageUrl?: string | null
    thumbnailUrl?: string | null
    isNew?: boolean
    approvedAt?: string | null
    createdAt?: string | null
}

export interface CardPageResponse {
    items: CardItemResponse[]
    total: number
    page: number
    pageSize: number
    totalPages: number
}

export interface IndustryCountResponse {
    industry: string
    count: number
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
    companyPhone?: string
    companyEmail?: string
    fax?: string
    website?: string
    industry?: string
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
    companyPhone?: string
    companyEmail?: string
    fax?: string
    website?: string
    industry?: string
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
