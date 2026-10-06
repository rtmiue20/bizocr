import { useEffect, useState } from 'react'
import { api } from '../api'
import { whoami } from '../layout/Shell'

interface Props {
  onOpenBook: () => void
}

export default function BookWelcomeCover({ onOpenBook }: Props) {
  const [totalCards, setTotalCards] = useState<number>(24)
  const [isOpening, setIsOpening] = useState(false)
  const user = whoami()

  useEffect(() => {
    // Lấy số lượng danh thiếp thực tế từ backend
    api.contacts.search({ page: 0, size: 1 })
      .then((res) => {
        if (res && typeof res.totalElements === 'number') {
          setTotalCards(res.totalElements)
        }
      })
      .catch(() => {
        // Fallback số mặc định
      })
  }, [])

  // Lấy 2 chữ cái viết tắt của tên người dùng
  const initials = (() => {
    if (!user.name) return 'NA'
    const parts = user.name.trim().split(/\s+/)
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  })()

  const displayName = user.name || 'Nguyễn Văn An'
  const displayRole = user.role ? (user.role === 'ADMIN' ? 'Tổng Giám Đốc • Quản Trị' : 'Chủ Doanh Nghiệp • Sáng Lập') : 'Chủ Doanh Nghiệp • Sáng Lập'

  const handleOpenBook = () => {
    if (isOpening) return
    setIsOpening(true)

    // Chờ animation lật sổ 3D chạy xong rồi chuyển sang Dashboard
    setTimeout(() => {
      onOpenBook()
    }, 700)
  }

  return (
    <div className="min-h-screen w-full bg-[#fcf9f4] text-[#1c1c19] flex flex-col justify-center items-center py-6 px-4 selection:bg-[#4f46e5] selection:text-white">
      <main className="w-full max-w-lg mx-auto flex flex-col justify-center items-center">
        <div className="flex flex-col w-full items-center justify-between min-h-[580px] py-4 select-none">
          {/* Subtle ambient top header / status badge */}
          <div className="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#f0ede9] shadow-sm mb-4">
            <span className="w-2.5 h-2.5 rounded-full bg-[#3525cd] animate-pulse"></span>
            <span className="font-semibold text-xs text-[#464555] tracking-wider uppercase">
              Phiên bản Doanh nhân 2025
            </span>
          </div>

          {/* Central Tactile Notebook Canvas */}
          <div className="relative w-full max-w-[340px] my-auto py-6 flex items-center justify-center" style={{ perspective: 1200 }}>
            {/* Cast Shadow on Tabletop */}
            <div className="absolute -bottom-2 w-[85%] h-8 bg-black/15 blur-xl rounded-full scale-y-50 transform rotate-1"></div>
            <div className="absolute bottom-1 w-[72%] h-4 bg-black/20 blur-md rounded-full"></div>

            {/* The 3D Book Container */}
            <div
              aria-label={`Mở sổ danh thiếp điện tử của ${displayName}`}
              className={`relative w-full aspect-[1/1.38] rounded-2xl cursor-pointer transition-all duration-700 ease-out transform group ${
                isOpening
                  ? '-translate-y-2 scale-105 shadow-2xl'
                  : 'hover:-translate-y-1.5 hover:rotate-0 -rotate-1'
              }`}
              id="executive-notebook"
              role="button"
              tabIndex={0}
              onClick={handleOpenBook}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  handleOpenBook()
                }
              }}
              style={{
                transformStyle: 'preserve-3d',
                transform: isOpening
                  ? 'perspective(1200px) rotateY(-40deg) rotateX(4deg) translateY(-8px) scale(1.03)'
                  : undefined,
                boxShadow: isOpening
                  ? '-20px 25px 40px -10px rgba(30, 27, 75, 0.45)'
                  : undefined,
              }}
            >
              {/* Realistic Multi-Layered Paper Block Edges (Right Depth Trim) */}
              <div className="absolute right-0 top-3 bottom-3 w-5 bg-gradient-to-r from-[#e5e2dd] via-[#f6f3ee] to-[#fcf9f4] rounded-r-lg shadow-inner flex flex-col justify-between overflow-hidden pointer-events-none transform translate-x-2.5 translate-z-[-6px]">
                <div className="w-full h-full opacity-40 bg-[repeating-linear-gradient(0deg,#dcdad5,#dcdad5_1px,transparent_1px,transparent_3px)]"></div>
              </div>
              <div className="absolute right-0 top-4 bottom-4 w-3 bg-[#ebe8e3] rounded-r-sm transform translate-x-3.5 translate-z-[-12px] opacity-70"></div>

              {/* Leather Front Cover Slab */}
              <div className="relative w-full h-full rounded-2xl bg-gradient-to-br from-[#1E1B4B] via-[#2A2374] to-[#141236] p-6 shadow-2xl flex flex-col justify-between overflow-hidden text-white">
                {/* Subtle Leather Texture Overlay */}
                <div className="absolute inset-0 opacity-10 bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:12px_12px] pointer-events-none"></div>

                {/* Blind Deboss Frame Border */}
                <div className="absolute inset-2.5 rounded-xl pointer-events-none bg-gradient-to-b from-white/10 to-transparent p-[1px] opacity-40">
                  <div className="w-full h-full rounded-xl bg-transparent"></div>
                </div>

                {/* Left Spine Accent Ribbon / Stitched Detail */}
                <div className="absolute left-0 top-0 bottom-0 w-8 bg-gradient-to-r from-black/40 via-white/5 to-transparent pointer-events-none flex flex-col justify-around items-center py-4">
                  <div className="w-[1.5px] h-[92%] border-l border-dashed border-white/25"></div>
                </div>

                {/* Spine Fold Glow Shadow */}
                <div className="absolute left-8 top-0 bottom-0 w-4 bg-gradient-to-r from-black/30 to-transparent pointer-events-none"></div>

                {/* Cover Header: Gold Crest & Branding */}
                <div className="relative z-10 pl-5 pt-2 flex flex-col items-start">
                  {/* Minimalist Business Card Foil Emblem */}
                  <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-[#FFE7A3] via-[#E5B869] to-[#996515] p-[1.5px] shadow-md flex items-center justify-center mb-4">
                    <div className="w-full h-full rounded-[10px] bg-[#1E1B4B]/80 backdrop-blur-sm flex items-center justify-center">
                      <span className="material-symbols-outlined text-[#ffddb8] text-[22px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                        contact_mail
                      </span>
                    </div>
                  </div>

                  {/* Main Title */}
                  <h1 className="text-xl md:text-2xl text-[#ffddb8] tracking-wider font-bold drop-shadow-sm uppercase">
                    Book Name Card
                  </h1>
                  <p className="text-xs text-[#dad7ff]/90 tracking-widest uppercase mt-0.5 font-medium">
                    Sổ Danh Thiếp Điện Tử
                  </p>
                </div>

                {/* Card Cover Center Motif / Debossed Bookmark Placeholder */}
                <div className="relative z-10 pl-5 my-auto">
                  <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-md bg-white/10 backdrop-blur-md border border-white/10">
                    <span className="material-symbols-outlined text-[#ffb95f] text-[16px]">verified</span>
                    <span className="text-xs font-semibold text-white/95">Hồ Sơ Xác Thực Cao Cấp</span>
                  </div>
                </div>

                {/* Near Bottom: Greeting & Personalized User Plate */}
                <div className="relative z-10 pl-5 pb-1">
                  {/* Welcome Tag */}
                  <div className="inline-block mb-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-widest text-[#FFE7A3] bg-white/15 px-2 py-0.5 rounded">
                      Welcome
                    </span>
                  </div>

                  {/* Personalized User Box */}
                  <div className="flex items-center gap-3 bg-white/10 backdrop-blur-md rounded-xl p-2.5 shadow-sm border border-white/10">
                    <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-[#ffddb8] to-[#e2dfff] flex items-center justify-center text-[#3525cd] font-bold shadow-inner">
                      <span className="text-sm font-extrabold">{initials}</span>
                    </div>
                    <div className="flex flex-col min-w-0">
                      <div className="text-sm text-white font-semibold truncate leading-tight">
                        {displayName}
                      </div>
                      <div className="text-xs text-[#dad7ff] truncate">
                        {displayRole}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Corner Metallic Corner Guard Accent */}
                <div className="absolute bottom-0 right-0 w-8 h-8 pointer-events-none overflow-hidden rounded-br-2xl">
                  <div className="absolute bottom-0 right-0 w-12 h-12 bg-gradient-to-tl from-[#FFE7A3] via-[#C99A45] to-transparent transform rotate-45 translate-x-6 translate-y-6 opacity-60"></div>
                </div>
              </div>

              {/* Tactile Flip Indicator Ribbon / Tab on Right Outer Edge */}
              <div
                className={`absolute -right-3.5 top-1/2 -translate-y-1/2 z-20 flex items-center justify-center transition-all duration-300 ${
                  isOpening ? 'opacity-0 scale-75 translate-x-2' : 'group-hover:translate-x-1'
                }`}
                id="flip-tab"
              >
                <div className="relative flex items-center gap-1 bg-gradient-to-r from-[#684000] to-[#885500] text-white shadow-lg rounded-full py-2 pl-3.5 pr-2.5 active:scale-95 animate-pulse">
                  <span className="text-xs font-bold uppercase tracking-wider text-[#ffddb8]">Lật</span>
                  <span className="material-symbols-outlined text-[18px] text-[#ffddb8] transform transition-transform group-hover:translate-x-0.5">
                    auto_stories
                  </span>
                  {/* Tactile Paper Fold Tail */}
                  <div className="absolute -left-1.5 top-1/2 -translate-y-1/2 w-0 h-0 border-y-[6px] border-y-transparent border-r-[6px] border-r-[#684000]"></div>
                </div>
              </div>
            </div>
          </div>

          {/* Bottom Helper Section & Sync Stat */}
          <div className="w-full flex flex-col items-center gap-3 pt-4 text-center">
            {/* Action Trigger Instruction */}
            <button
              onClick={handleOpenBook}
              disabled={isOpening}
              className="flex items-center gap-2 px-6 py-2.5 rounded-full bg-[#ebe8e3] hover:bg-[#e5e2dd] active:scale-95 transition-all text-[#1c1c19] shadow-sm font-semibold text-sm cursor-pointer border border-[#c7c4d8]/40"
              id="open-book-btn"
            >
              <span className="material-symbols-outlined text-[20px] text-[#3525cd]">touch_app</span>
              <span>Chạm vào bìa để mở sổ</span>
              <span className="material-symbols-outlined text-[18px] text-[#464555]">arrow_forward</span>
            </button>

            {/* Synced Cards Status */}
            <div className="flex items-center gap-2 text-[#464555]">
              <span className="material-symbols-outlined text-[18px] text-[#16A34A]" style={{ fontVariationSettings: "'FILL' 1" }}>
                check_circle
              </span>
              <span className="text-xs">
                Đã đồng bộ <strong className="font-bold text-[#1c1c19]">{totalCards} danh thiếp</strong> đã xác thực
              </span>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
