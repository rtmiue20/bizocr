import type { Doc } from './types'

export type Step = 1 | 2 | 3 | 4 | 5 | 6 | 8 | 9
export type Fields = Record<string, unknown>

export const parseFields = (s?: string | null): Fields => {
  if (!s) return {}
  try {
    const v = JSON.parse(s)
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch {
    return {}
  }
}
export const show = (v: unknown) => (v == null ? '' : typeof v === 'object' ? JSON.stringify(v, null, 2) : String(v))

// Giữ kiểu gốc (số / object) khi người dùng sửa, tránh biến 100 thành "100" trong JSON.
export function restore(orig: unknown, text: string): unknown {
  if (typeof orig === 'number' && text.trim() !== '' && !isNaN(Number(text))) return Number(text)
  if (orig && typeof orig === 'object') { try { return JSON.parse(text) } catch { return text } }
  return text
}

export const STATUS_CLS: Record<string, string> = {
  PENDING: 'bg-surface-container-highest text-on-surface-variant',
  NEED_REVIEW: 'bg-tertiary-container text-on-tertiary-container',
  APPROVED: 'bg-secondary-container text-on-secondary-fixed',
  REJECTED: 'bg-error-container text-error',
}

/** Tìm giá trị đầu tiên có tên trường khớp regex (tên trường phụ thuộc template nên phải dò). */
export function pick(f: Fields, re: RegExp): string {
  for (const [k, v] of Object.entries(f)) {
    if (re.test(k) && v != null && typeof v !== 'object' && String(v).trim()) return String(v).trim()
  }
  return ''
}
const num = (s: string) => (s ? Number(s.replace(/\D/g, '')) : NaN)

// ---- 6 chặng: suy ra từ dữ liệu thật của Document, không phải số liệu giả ----
export type StageState = 'done' | 'active' | 'wait' | 'fail'
export interface Stage { name: string; note: string; state: StageState }

export function stages(d: Doc): Stage[] {
  const f = parseFields(d.extractedFields)
  const n = Object.keys(f).length
  const pending = d.status === 'PENDING'
  const done = [true, !!d.rawOcrText, !!d.documentType, n > 0, !pending, d.status === 'APPROVED']
  const firstOpen = done.indexOf(false)
  const st = (i: number): StageState =>
    done[i] ? 'done' : i === 5 && d.status === 'REJECTED' ? 'fail' : pending && i === firstOpen ? 'active' : 'wait'
  const notes = [
    'Đã lưu file, vào hàng đợi',
    d.rawOcrText ? `Đọc được ${d.rawOcrText.length} ký tự` : 'Đang đọc quang học',
    d.documentType ?? 'Chưa nhận diện loại',
    n ? `${n} trường đã bóc tách` : 'Chưa có trường nào',
    pending ? 'Chờ các chặng trước' : 'Đã chạy xong quy tắc',
    d.status === 'APPROVED' ? 'Đã duyệt' : d.status === 'REJECTED' ? 'Đã từ chối' : 'Chờ kế toán duyệt',
  ]
  const names = ['Tiếp nhận file', 'Quét quang học', 'Nhận diện mẫu', 'Trích xuất số liệu', 'Đối soát quy tắc', 'Phê duyệt cuối']
  return names.map((name, i) => ({ name, note: notes[i], state: st(i) }))
}

// ---- Kiểm tra nhanh phía trình duyệt (không thay thế validator ở backend) ----
export interface Rule { title: string; ok: boolean | null; detail: string }

export function rules(f: Fields): Rule[] {
  const mst = pick(f, /tax_?code|mst|ma_?so_?thue/i).replace(/\s/g, '')
  const pre = num(pick(f, /sub_?total|before_?tax|pre_?tax|truoc_?thue/i))
  const vat = num(pick(f, /vat|gtgt|tax_?amount|tien_?thue/i))
  const total = num(pick(f, /^(grand_?)?total|^tong/i))
  const entries = Object.values(f).filter((v) => typeof v !== 'object')
  const filled = entries.filter((v) => v != null && String(v).trim() !== '').length
  const hasMath = [pre, vat, total].every((x) => !isNaN(x))
  return [
    {
      title: 'Định dạng mã số thuế',
      ok: mst ? /^\d{10}(-\d{3})?$/.test(mst) : null,
      detail: mst ? `MST đọc được: ${mst} (cần 10 số, hoặc 13 số dạng 10-3)` : 'Chưa tìm thấy trường mã số thuế',
    },
    {
      title: 'Đối chiếu số học',
      ok: hasMath ? pre + vat === total : null,
      detail: hasMath ? `${pre.toLocaleString('vi-VN')} + ${vat.toLocaleString('vi-VN')} ${pre + vat === total ? '=' : '≠'} ${total.toLocaleString('vi-VN')}` : 'Chưa đủ trường tiền hàng / thuế / tổng',
    },
    {
      title: 'Độ đầy đủ trường',
      ok: entries.length ? filled === entries.length : null,
      detail: entries.length ? `${filled}/${entries.length} trường có giá trị` : 'Chưa có trường nào',
    },
  ]
}

// ---- Tìm trường theo tên (tên trường phụ thuộc template) ----
export const RE = {
  mst: /tax_?code|mst|ma_?so_?thue/i,
  sub: /sub_?total|before_?tax|pre_?tax|truoc_?thue/i,
  vat: /vat|gtgt|tax_?amount|tien_?thue/i,
  total: /^(grand_?)?total|^tong/i,
  company: /company|seller|vendor|supplier|ten_?(cong_?ty|don_?vi)/i,
  address: /address|dia_?chi/i,
}
export const keyOf = (f: object, re: RegExp) => Object.keys(f).find((k) => re.test(k))
export const digits = (s: string) => Number(s.replace(/\D/g, ''))
export const fmtDate = (s?: string | null) => (s ? new Date(s).toLocaleString('vi-VN') : '—')
