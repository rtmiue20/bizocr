import type {Doc, DocPage, Contact, ContactSearchResult, ContactDetailResponse, DocumentImage} from './types'

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
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const res = await fetch(BASE + path, {...init, headers})
    if (!res.ok) {
        if (res.status === 401 && !path.startsWith('/auth')) {
            setToken(null)
            window.dispatchEvent(new Event('logout'))
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
    file: (id: number) => req(`/documents/${id}/file`).then((r) => r.blob()),
    upload: (file: File) => {
        const fd = new FormData()
        fd.append('file', file)
        return json<{ documentId: number; fileName: string; status: string; message: string }>(
            '/documents/upload',
            {method: 'POST', body: fd},
        )
    },
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
    approve: (id: number, data: string | Record<string, unknown>) => {
        const payload = typeof data === 'string'
            ? (() => {
                try {
                    const parsed = JSON.parse(data)
                    return { updatedFields: parsed }
                } catch {
                    return { extractedFields: data }
                }
            })()
            : data
        return json<unknown>(`/documents/${id}/approve`, body('POST', payload))
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
