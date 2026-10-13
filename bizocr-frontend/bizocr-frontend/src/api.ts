import type {Doc, DocPage, Contact, ContactSearchResult, ContactDetailResponse, DocumentImage, DocumentDetailResponse, CardPageResponse, IndustryCountResponse, CardItemResponse} from './types'

const BASE = '/api/v1'
export const getToken = () => localStorage.getItem('token') ?? sessionStorage.getItem('token')
export const setToken = (t: string | null, remember = true) => {
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
    if (t) (remember ? localStorage : sessionStorage).setItem('token', t)
}

export class ApiError extends Error {
    constructor(public status: number, message: string) {
        super(message)
    }
}

async function req(path: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers)
    const token = getToken()
    if (token && !path.startsWith('/auth')) headers.set('Authorization', `Bearer ${token}`)
    const res = await fetch(BASE + path, {...init, headers})
    if (!res.ok) {
        if (res.status === 401) {
            setToken(null)
            if (!path.startsWith('/auth')) {
                window.dispatchEvent(new Event('logout'))
            }
        }
        let msg = res.statusText
        try {
            const j = await res.clone().json()
            msg = j.error || j.message || msg
        } catch { /* body không phải JSON */
        }
        throw new ApiError(res.status, msg)
    }
    return res
}

const json = <T, >(path: string, init?: RequestInit) => req(path, init).then((r) => r.json() as Promise<T>)
const body = (method: string, data: unknown): RequestInit => ({
    method,
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
})

export interface RegisterData {
    username: string
    password: string
    email: string
    phoneNumber: string
    taxCode: string
    companyName: string
}

export const api = {
    login: (username: string, password: string) =>
        json<{ access_token: string }>('/auth/login', body('POST', {username, password})),
    register: (d: RegisterData) => json<{ message: string }>('/auth/register', body('POST', d)),

    list: (p: { status?: string; documentType?: string; page: number; size: number }) => {
        const q = new URLSearchParams({page: String(p.page), size: String(p.size)})
        if (p.status) q.set('status', p.status)
        if (p.documentType) q.set('documentType', p.documentType)
        return json<DocPage>(`/documents?${q}`)
    },
    get: (id: number) => json<Doc>(`/documents/${id}`),
    getDetail: (id: number) => json<DocumentDetailResponse>(`/documents/${id}/detail`),
    file: (id: number) => req(`/documents/${id}/file`).then((r) => r.blob()),
    upload: (file: File, batchId?: string) => {
        const fd = new FormData()
        fd.append('file', file)
        const url = batchId ? `/documents/upload?batchId=${encodeURIComponent(batchId)}` : '/documents/upload'
        return json<{ documentId: number; fileName: string; status: string; message: string }>(
            url,
            {method: 'POST', body: fd},
        )
    },
    getByBatch: (batchId: string) => json<Doc[]>(`/documents/batch/${encodeURIComponent(batchId)}`),
    uploadImage: (docId: number, side: 'FRONT' | 'BACK', file: File) => {
        const fd = new FormData()
        fd.append('file', file)
        return json<DocumentImage>(
            `/documents/${docId}/images/${side}`,
            {method: 'POST', body: fd},
        )
    },
    getDocumentImages: (docId: number) => json<DocumentImage[]>(`/documents/${docId}/images`),
    // Lưu bản sửa (ghi audit từng field ở backend). status bỏ trống = giữ nguyên.
    update: (id: number, d: { status?: string; extractedFields?: string }) =>
        json<Doc>(`/documents/${id}`, body('PUT', d)),
    approve: async (id: number, data: string | Record<string, unknown>) => {
        let payload: Record<string, unknown>
        if (typeof data === 'string') {
            try {
                const parsed = JSON.parse(data)
                // taxCode / companyName lên top-level để backend DTO bind được
                payload = {
                    updatedFields: parsed,
                    taxCode: parsed['tax_code'] ?? null,
                    companyName: parsed['company_name'] ?? null,
                    contactName: parsed['contact_name'] ?? null,
                    contactTitle: parsed['contact_title'] ?? null,
                    approvedType: parsed['approvedType'] ?? 'NAMECARD',
                }
            } catch {
                payload = { extractedFields: data }
            }
        } else {
            // data là object (Record) — bọc vào updatedFields, tách top-level fields cho DTO
            payload = {
                updatedFields: data,
                taxCode: (data['tax_code'] as string) ?? null,
                companyName: (data['company_name'] as string) ?? null,
                contactName: (data['contact_name'] as string) ?? null,
                contactTitle: (data['contact_title'] as string) ?? null,
                approvedType: (data['approvedType'] as string) ?? 'NAMECARD',
            }
        }
        const res = await json<unknown>(`/documents/${id}/approve`, body('POST', payload))
        window.dispatchEvent(new CustomEvent('cards-invalidated', { detail: { id } }))
        return res
    },

    companySuggestion: (id: number) => json<Record<string, unknown>>(`/documents/${id}/company-suggestion`),
    classify: (text: string) => json<any>('/documents/classify', body('POST', { text })),
    exportXlsx: () => req('/documents/export').then((r) => r.blob()),

    // ── Contacts / Namecard APIs ──────────────────────────────
    contacts: {
        search: (p: { q?: string; industry?: string; page?: number; size?: number }) => {
            const q = new URLSearchParams({
                page: String(p.page ?? 0),
                size: String(p.size ?? 12),
            })
            if (p.q) q.set('q', p.q)
            if (p.industry) q.set('industry', p.industry)
            return json<ContactSearchResult>(`/contacts?${q}`)
        },
        get: (id: number) => json<ContactDetailResponse>(`/contacts/${id}`),
        update: (id: number, d: Partial<Contact>) =>
            json<Contact>(`/contacts/${id}`, body('PUT', d)),
        markContacted: (id: number) =>
            json<Contact>(`/contacts/${id}/contacted`, {method: 'POST'}),
        confirmMerge: (id: number) =>
            req(`/contacts/${id}/confirm-merge`, {method: 'POST'}),
        dismissDuplicate: (id: number) =>
            req(`/contacts/${id}/dismiss-duplicate`, {method: 'DELETE'}),
    },

    // ── Card Book APIs ────────────────────────────────────────
    cards: {
        list: (p: { page?: number; pageSize?: number; industry?: string; q?: string }) => {
            const q = new URLSearchParams()
            if (p.page !== undefined) q.set('page', String(p.page))
            if (p.pageSize !== undefined) q.set('pageSize', String(p.pageSize))
            if (p.industry && p.industry !== 'Tất cả') q.set('industry', p.industry)
            if (p.q && p.q.trim()) q.set('q', p.q.trim())
            return json<CardPageResponse>(`/cards?${q.toString()}`)
        },
        industries: () => json<IndustryCountResponse[]>('/cards/industries'),
    }
}

export function download(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    a.click()
    URL.revokeObjectURL(url)
}
