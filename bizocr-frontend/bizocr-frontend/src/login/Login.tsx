import {useState} from 'react'
import Lamp from './Lamp'
import AuthCard from './AuthCard'
import './login.css'

export default function Login({onDone}: { onDone: () => void }) {
    const [on, setOn] = useState(true)
    const toggle = () => setOn((v) => !v)

    return (
        <div className={`lg${on ? '' : ' off'}`}>
            <div className="lg-shell">
                <header className="lg-head lg-hide">
                    <div className="lg-brand">
                        <div className="lg-logo"><span className="material-symbols-outlined">document_scanner</span>
                        </div>
                        <div>
                            <strong>BIZOCR AI</strong>
                            <p>Hệ thống trích xuất &amp; số hóa chứng từ thông minh</p>
                        </div>
                    </div>
                    <button type="button" className="lg-badge" onClick={toggle}>
                        <i className={on ? 'pulse' : ''}/>
                        {on ? 'Đèn bàn đang BẬT' : 'Đèn bàn đang TẮT'}
                    </button>
                </header>

                <main className="lg-main">
                    <section className="lg-left">
                        <div className="lg-beam"/>
                        <Lamp on={on} onToggle={toggle}/>
                        <div className="lg-caption lg-hide">
                            <h3>Bàn làm việc số hóa AI</h3>
                            <p>Chiếu sáng thông minh và bảo mật dữ liệu hợp đồng, hóa đơn, danh thiếp tự động.</p>
                        </div>
                    </section>
                    <section className="lg-right lg-hide">
                        <AuthCard onDone={onDone}/>
                    </section>
                </main>

                <footer className="lg-foot">
                    <span>Bản quyền © {new Date().getFullYear()} BIZOCR AI Platform. Tất cả quyền được bảo lưu.</span>
                </footer>
            </div>
        </div>
    )
}