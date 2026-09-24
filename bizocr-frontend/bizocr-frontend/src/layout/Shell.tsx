import type {ReactNode} from 'react'
import {api, download, getToken} from '../api'
import type {Step} from '../lib'

const NAV = [
    {n: 1, icon: 'cloud_upload', title: 'Bước 1: Tiếp nhận', sub: 'Upload & Chụp ảnh'},
    {n: 2, icon: 'insights', title: 'Bước 2: Theo dõi AI', sub: 'Tiến trình & Quy tắc'},
    {n: 3, icon: 'visibility', title: 'Bước 3: So sánh tài liệu', sub: 'Xem file & Trích xuất'},
    {n: 4, icon: 'category', title: 'Bước 4: Phân loại chứng từ', sub: 'Nhận diện loại văn bản'},
    {n: 5, icon: 'edit_note', title: 'Bước 5: Kiểm tra & Sửa', sub: 'Hiệu chỉnh thông tin'},
    {n: 6, icon: 'verified', title: 'Bước 6: Phê duyệt & Ký', sub: 'Xác nhận & khóa chứng từ'},
    {n: 7, icon: 'ios_share', title: 'Bước 7: Xuất sổ kế toán', sub: 'Tải Excel các chứng từ đã duyệt'},
    {n: 8, icon: 'inventory_2', title: 'Kho Lưu trữ', sub: 'Tra cứu toàn bộ chứng từ'},
    // Thêm menu cho trang Cấu hình
    {n: 9, icon: 'settings', title: 'Quản trị & Cấu hình', sub: 'Mẫu biểu & Định mức thuế'},
]
// Khai báo bước 9 vào mảng LIVE để nút menu có thể bấm được
const LIVE = [1, 2, 3, 4, 5, 6, 8, 9]

export function whoami() {
    try {
        const p = JSON.parse(atob(getToken()!.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
        return {name: String(p.sub ?? ''), role: String(p.roles?.[0] ?? ''), company: p.companyId}
    } catch {
        return {name: '', role: '', company: ''}
    }
}

export default function Shell({step, onStep, onLogout, children}: {
    step: Step; onStep: (s: Step) => void; onLogout: () => void; children: ReactNode
}) {
    const me = whoami()

    async function exportXlsx() {
        try {
            download(await api.exportXlsx(), 'bao_cao_ke_toan.xlsx')
        } catch (e) {
            alert((e as Error).message)
        }
    }

    return (
        <>
            <aside
                className="fixed left-0 top-0 z-50 hidden h-full w-72 flex-col justify-between bg-surface-container-low/90 backdrop-blur-2xl shadow-[0_1px_8px_rgba(0,0,0,0.4)] lg:flex">
                <div className="flex h-full flex-col overflow-hidden">
                    <div className="flex h-16 items-center gap-space-sm bg-surface-container-lowest/60 px-space-lg">
                        <div
                            className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-container shadow-[0_0_16px_rgba(0,242,254,0.35)]">
                            <span
                                className="material-symbols-outlined text-[20px] text-on-primary-fixed">auto_awesome</span>
                        </div>
                        <div className="flex flex-col">
                            <span
                                className="font-headline-md text-headline-md tracking-tight text-primary">BizOCR</span>
                            <span className="font-body-sm text-body-sm text-on-surface-variant">Trợ lý Kế toán &amp; Vận hành</span>
                        </div>
                    </div>
                    <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-space-md py-space-md">
                        <span
                            className="px-space-sm py-space-xs font-label-sm text-label-sm tracking-wider text-outline">QUY TRÌNH XỬ LÝ (7 BƯỚC)</span>
                        {NAV.map((it) => {
                            const live = LIVE.includes(it.n)
                            const active = live && it.n === step
                            return (
                                <button
                                    key={it.n}
                                    type="button"
                                    disabled={!live && it.n !== 7}
                                    aria-current={active ? 'page' : undefined}
                                    onClick={() => (it.n === 7 ? exportXlsx() : onStep(it.n as Step))}
                                    className={`flex items-center gap-space-sm rounded-xl px-space-md py-space-sm text-left transition-all disabled:cursor-not-allowed disabled:opacity-40 ${
                                        active
                                            ? 'bg-surface-container-high font-bold text-primary-container shadow-[0_0_12px_rgba(0,242,254,0.15)]'
                                            : 'text-on-surface-variant enabled:hover:bg-surface-container enabled:hover:text-on-surface'
                                    }`}
                                >
                                    <span className="material-symbols-outlined text-[18px]">{it.icon}</span>
                                    <span className="flex flex-col">
                    <span className="font-title-md text-body-md">{it.title}</span>
                    <span className="font-body-sm text-body-sm opacity-70">{it.sub}</span>
                  </span>
                                </button>
                            )
                        })}
                    </nav>
                </div>
            </aside>

            <div className="lg:pl-72">
                <header
                    className="fixed left-0 right-0 top-0 z-40 flex h-16 items-center justify-between bg-surface/80 px-gutter backdrop-blur-xl shadow-[0_1px_8px_rgba(0,0,0,0.3)] lg:left-72">
                    <select
                        className="rounded-xl bg-surface-container-low px-space-md py-1.5 font-label-md text-label-md text-on-surface lg:hidden"
                        value={step}
                        onChange={(e) => onStep(Number(e.target.value) as Step)}
                        aria-label="Chọn bước"
                    >
                        {NAV.filter((it) => LIVE.includes(it.n)).map((it) => <option key={it.n}
                                                                                     value={it.n}>{it.title}</option>)}
                    </select>
                    <span className="hidden lg:block"/>
                    <div className="flex items-center gap-space-md">
                        <div className="flex flex-col text-right">
                            <span
                                className="font-title-md text-body-md font-semibold text-on-surface">{me.name || 'Người dùng'}</span>
                            <span className="font-label-sm text-label-sm font-medium text-secondary">
                {me.role}{me.company !== '' && me.company != null ? ` · Công ty #${me.company}` : ''}
              </span>
                        </div>
                        <button
                            type="button"
                            onClick={onLogout}
                            title="Đăng xuất"
                            className="flex items-center justify-center rounded-xl p-2 text-on-surface-variant transition-all hover:bg-surface-container hover:text-on-surface"
                        >
                            <span className="material-symbols-outlined text-[20px]">logout</span>
                        </button>
                    </div>
                </header>
                <main className="min-h-screen w-full bg-surface px-gutter pb-space-xl pt-24">{children}</main>
            </div>
        </>
    )
}