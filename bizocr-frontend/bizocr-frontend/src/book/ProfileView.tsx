import { useEffect, useState } from 'react'
import { whoami } from '../layout/Shell'
import { api } from '../api'

interface Props {
  onCloseBook: () => void
  onLogout: () => void
  onOpenConfig?: () => void
}

export default function ProfileView({ onCloseBook, onLogout, onOpenConfig }: Props) {
  const user = whoami()
  const [totalCards, setTotalCards] = useState<number>(0)
  const [totalCompanies, setTotalCompanies] = useState<number>(0)

  useEffect(() => {
    api.contacts.search({ page: 0, size: 100 })
      .then((res) => {
        setTotalCards(res.totalElements)
        const uniqueCompanies = new Set(
          res.items.map((c) => c.companyName).filter(Boolean)
        )
        setTotalCompanies(uniqueCompanies.size)
      })
      .catch(() => {})
  }, [])

  const displayName = user.name || 'Nguyễn Văn An'
  const displayRole = user.role === 'ADMIN' ? 'Tổng Giám Đốc • Quản Trị Hệ Thống' : 'Chủ Doanh Nghiệp • Sáng Lập'
  const initials = (() => {
    if (!user.name) return 'NA'
    const parts = user.name.trim().split(/\s+/)
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  })()

  return (
    <div className="w-full max-w-4xl mx-auto py-6 flex flex-col gap-6">
      {/* Profile Header Card */}
      <div className="bg-gradient-to-br from-[#1E1B4B] via-[#2A2374] to-[#141236] rounded-3xl p-8 text-white shadow-xl flex flex-col sm:flex-row items-center sm:items-start gap-6 relative overflow-hidden">
        <div className="w-24 h-24 rounded-3xl bg-gradient-to-tr from-[#ffddb8] to-[#e2dfff] flex items-center justify-center text-[#1E1B4B] font-extrabold text-3xl shadow-2xl shrink-0">
          {initials}
        </div>

        <div className="flex flex-col text-center sm:text-left gap-1.5 flex-1">
          <div className="flex items-center justify-center sm:justify-start gap-2">
            <span className="text-[10px] font-extrabold uppercase tracking-widest text-[#FFE7A3] bg-white/10 px-2.5 py-0.5 rounded-full">
              Hồ Sơ Xác Thực Cao Cấp
            </span>
          </div>
          <h2 className="text-2xl sm:text-3xl font-extrabold text-[#ffddb8]">
            {displayName}
          </h2>
          <p className="text-xs sm:text-sm text-[#dad7ff] font-medium">
            {displayRole}
          </p>
          <p className="text-xs text-[#dad7ff]/70 mt-1">
            Hệ thống Sổ Danh Thiếp Điện Tử Doanh Nghiệp • Phiên bản Doanh nhân 2025
          </p>
        </div>
      </div>

      {/* Thống kê nhanh */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-2xl p-5 border border-[#c7c4d8]/60 shadow-sm flex flex-col gap-1">
          <span className="text-xs font-bold uppercase tracking-wider text-[#464555]">
            Danh thiếp đã số hóa
          </span>
          <span className="text-3xl font-extrabold text-[#1E1B4B]">
            {totalCards}
          </span>
          <span className="text-[11px] text-emerald-600 font-semibold">
            ✓ Đã trích xuất & lưu trữ an toàn
          </span>
        </div>

        <div className="bg-white rounded-2xl p-5 border border-[#c7c4d8]/60 shadow-sm flex flex-col gap-1">
          <span className="text-xs font-bold uppercase tracking-wider text-[#464555]">
            Doanh nghiệp kết nối
          </span>
          <span className="text-3xl font-extrabold text-[#684000]">
            {totalCompanies}
          </span>
          <span className="text-[11px] text-[#464555]">
            Mạng lưới đối tác thương mại
          </span>
        </div>

        <div className="bg-white rounded-2xl p-5 border border-[#c7c4d8]/60 shadow-sm flex flex-col gap-1">
          <span className="text-xs font-bold uppercase tracking-wider text-[#464555]">
            Độ chính xác AI
          </span>
          <span className="text-3xl font-extrabold text-[#3525cd]">
            99.2%
          </span>
          <span className="text-[11px] text-[#3525cd] font-semibold">
            Nhận diện OCR & bóc tách tiếng Việt
          </span>
        </div>
      </div>

      {/* Tác vụ tài khoản */}
      <div className="bg-white rounded-3xl p-6 border border-[#c7c4d8]/60 shadow-sm flex flex-col gap-4">
        <h3 className="text-sm font-extrabold text-[#1E1B4B] uppercase tracking-wider">
          Cài đặt &amp; Thao tác Sổ
        </h3>

        <div className="flex flex-col gap-3">
          {/* Nút Xem lại bìa 3D */}
          <button
            type="button"
            onClick={onCloseBook}
            className="flex items-center justify-between p-4 rounded-2xl bg-[#fcf9f4] hover:bg-[#ebe8e3] border border-[#c7c4d8]/50 transition-all cursor-pointer"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#1E1B4B] text-[#ffddb8] flex items-center justify-center">
                <span className="material-symbols-outlined text-[20px]">auto_stories</span>
              </div>
              <div className="flex flex-col text-left">
                <span className="text-sm font-bold text-[#1E1B4B]">
                  Đóng sổ (Xem lại Bìa Sách 3D)
                </span>
                <span className="text-xs text-[#464555]">
                  Hiển thị bìa da phong cách Phiên bản Doanh nhân 2025
                </span>
              </div>
            </div>
            <span className="material-symbols-outlined text-[#464555]">arrow_forward</span>
          </button>

          {/* Quản trị & Cấu hình nếu có */}
          {onOpenConfig && (
            <button
              type="button"
              onClick={onOpenConfig}
              className="flex items-center justify-between p-4 rounded-2xl bg-[#fcf9f4] hover:bg-[#ebe8e3] border border-[#c7c4d8]/50 transition-all cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-[#684000] text-[#ffddb8] flex items-center justify-center">
                  <span className="material-symbols-outlined text-[20px]">settings</span>
                </div>
                <div className="flex flex-col text-left">
                  <span className="text-sm font-bold text-[#1E1B4B]">
                    Quản trị &amp; Cấu hình Nâng cao
                  </span>
                  <span className="text-xs text-[#464555]">
                    Cấu hình mẫu biểu chứng từ, định mức thuế, training batch
                  </span>
                </div>
              </div>
              <span className="material-symbols-outlined text-[#464555]">arrow_forward</span>
            </button>
          )}

          {/* Đăng xuất */}
          <button
            type="button"
            onClick={onLogout}
            className="flex items-center justify-between p-4 rounded-2xl bg-[#ffdad6]/20 hover:bg-[#ffdad6]/50 border border-[#ffdad6] text-[#ba1a1a] transition-all cursor-pointer mt-2"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#ba1a1a] text-white flex items-center justify-center">
                <span className="material-symbols-outlined text-[20px]">logout</span>
              </div>
              <div className="flex flex-col text-left">
                <span className="text-sm font-bold text-[#ba1a1a]">
                  Đăng xuất tài khoản
                </span>
                <span className="text-xs text-[#ba1a1a]/80">
                  Xóa phiên đăng nhập an toàn
                </span>
              </div>
            </div>
            <span className="material-symbols-outlined text-[#ba1a1a]">arrow_forward</span>
          </button>
        </div>
      </div>
    </div>
  )
}
