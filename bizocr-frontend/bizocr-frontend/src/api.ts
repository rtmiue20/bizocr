import type {Doc, DocPage} from './types'

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
    // Lưu bản sửa (ghi audit từng field ở backend). status bỏ trống = giữ nguyên.
    update: (id: number, d: { status?: string; extractedFields?: string }) =>
        json<Doc>(`/documents/${id}`, body('PUT', d)),
    // ⚠ Body của DocumentApproveRequest chưa thấy trong code – xem ghi chú ở Review.tsx
    approve: (id: number, extractedFields: string) =>
        json<unknown>(`/documents/${id}/approve`, body('POST', {extractedFields})),

    // Đã thêm hàm companySuggestion vào đây:
    companySuggestion: (id: number) => json<Record<string, unknown>>(`/documents/${id}/company-suggestion`),
    classify: (text: string) => json<any>('/documents/classify', body('POST', { text })),
    exportXlsx: () => req('/documents/export').then((r) => r.blob()),
}

export function download(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    a.click()
    URL.revokeObjectURL(url)
}
