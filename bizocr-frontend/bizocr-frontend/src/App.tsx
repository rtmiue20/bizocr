import { useEffect, useState } from 'react'
import { getToken, setToken } from './api'
import Login from './login/Login'
import BookWelcomeCover from './book/BookWelcomeCover'
import BookNavbar, { type BookTab } from './book/BookNavbar'
import DashboardView from './book/DashboardView'
import ProcessImageFlow from './book/ProcessImageFlow'
import SearchView from './book/SearchView'
import ProfileView from './book/ProfileView'
import ConfigPage from './pages/ConfigPage'

export default function App() {
  const [authed, setAuthed] = useState(!!getToken())
  // Trạng thái đã lật mở sổ hay đang ở bìa chào đón
  const [bookOpened, setBookOpened] = useState(false)
  // Tab hiện tại trên thanh công cụ: Dashboard | ProcessImage | Search | Profile
  const [activeTab, setActiveTab] = useState<BookTab>('DASHBOARD')
  // Số lượng thẻ để hiển thị trên badge navbar
  const [cardCount, setCardCount] = useState<number>()
  // Mở trang cấu hình (nếu admin mở từ Profile)
  const [showConfig, setShowConfig] = useState(false)

  useEffect(() => {
    const onLogout = () => {
      setAuthed(false)
      setBookOpened(false)
    }
    window.addEventListener('logout', onLogout)
    return () => window.removeEventListener('logout', onLogout)
  }, [])

  const handleLogout = () => {
    setToken(null)
    setAuthed(false)
    setBookOpened(false)
  }

  // 1. Chưa đăng nhập -> Hiện giao diện Login
  if (!authed) {
    return <Login onDone={() => setAuthed(true)} />
  }

  // 2. Đã đăng nhập nhưng chưa mở sổ -> Hiện Bìa Sách Book Name Card 3D theo đúng code của user
  if (!bookOpened) {
    return (
      <BookWelcomeCover
        onOpenBook={() => {
          setBookOpened(true)
          setActiveTab('DASHBOARD')
        }}
      />
    )
  }

  // 3. Đã mở sổ -> Hiển thị Hệ Thống Sổ Danh Thiếp Điện Tử với thanh công cụ Top Nav
  return (
    <div className="min-h-screen bg-[#fcf9f4] text-[#1c1c19] flex flex-col font-['Be_Vietnam_Pro',sans-serif] selection:bg-[#4f46e5] selection:text-white">
      {/* Thanh công cụ trên đầu theo bản vẽ: Dashboard | ProcessImage | Search | Profile */}
      <BookNavbar
        activeTab={activeTab}
        onTabChange={(tab) => {
          setShowConfig(false)
          setActiveTab(tab)
        }}
        onCloseBook={() => setBookOpened(false)}
        onLogout={handleLogout}
        cardCount={cardCount}
      />

      {/* Main Content Area */}
      <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 pt-20 pb-8">
        {showConfig ? (
          <div className="flex flex-col gap-4">
            <button
              onClick={() => setShowConfig(false)}
              className="flex items-center gap-1.5 text-xs font-bold text-[#464555] hover:text-[#1c1c19] w-fit cursor-pointer"
            >
              <span className="material-symbols-outlined text-[16px]">arrow_back</span>
              <span>Quay lại Sổ Danh Thiếp</span>
            </button>
            <ConfigPage />
          </div>
        ) : (
          <>
            {/* Tab 1: DASHBOARD (Trang chủ sổ danh thiếp) */}
            {activeTab === 'DASHBOARD' && (
              <DashboardView
                onProcessImage={() => setActiveTab('PROCESS_IMAGE')}
                onUpdateCount={setCardCount}
              />
            )}

            {/* Tab 2: PROCESS_IMAGE (Quy trình Quét & Số hóa ảnh theo bản vẽ) */}
            {activeTab === 'PROCESS_IMAGE' && (
              <ProcessImageFlow
                onBackToDashboard={() => setActiveTab('DASHBOARD')}
                onFinishToDashboard={() => setActiveTab('DASHBOARD')}
              />
            )}

            {/* Tab 3: SEARCH (Tìm kiếm nâng cao) */}
            {activeTab === 'SEARCH' && <SearchView />}

            {/* Tab 4: PROFILE (Hồ sơ người dùng & Cài đặt) */}
            {activeTab === 'PROFILE' && (
              <ProfileView
                onCloseBook={() => setBookOpened(false)}
                onLogout={handleLogout}
                onOpenConfig={() => setShowConfig(true)}
              />
            )}
          </>
        )}
      </main>
    </div>
  )
}
