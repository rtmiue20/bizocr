import {useState, type ChangeEvent, type FormEvent, type InputHTMLAttributes} from 'react'
import {api, setToken, type RegisterData} from '../api'

type FieldProps = InputHTMLAttributes<HTMLInputElement> & { label: string; icon: string; mono?: boolean }

function Field({label, icon, mono, ...input}: FieldProps) {
    return (
        <label className="lg-label">
            <span>{label}{input.required && <b> *</b>}</span>
            <span className="lg-field">
        <input className={`lg-input${mono ? ' mono' : ''}`} {...input} />
        <span className="material-symbols-outlined" aria-hidden>{icon}</span>
      </span>
        </label>
    )
}

const EMPTY: RegisterData = {username: '', password: '', email: '', phoneNumber: '', taxCode: '', companyName: ''}
type Tab = 'login' | 'register'

export default function AuthCard({onDone}: { onDone: () => void }) {
    const [tab, setTab] = useState<Tab>('login')
    const [login, setLogin] = useState({username: '', password: '', remember: true})
    const [reg, setReg] = useState<RegisterData>(EMPTY)
    const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
    const [busy, setBusy] = useState(false)

    const r = (k: keyof RegisterData) => (e: ChangeEvent<HTMLInputElement>) => setReg({...reg, [k]: e.target.value})
    const go = (t: Tab) => {
        setTab(t);
        setMsg(null)
    }

    async function submit(e: FormEvent) {
        e.preventDefault()
        setBusy(true)
        setMsg(null)
        try {
            if (tab === 'login') {
                const res = await api.login(login.username, login.password)
                setToken(res.access_token, login.remember)
                onDone()
            } else {
                const res = await api.register(reg)
                setLogin({...login, username: reg.username, password: ''})
                setReg(EMPTY)
                setTab('login')
                setMsg({ok: true, text: res.message + ' Hãy đăng nhập để tiếp tục.'})
            }
        } catch (err) {
            setMsg({ok: false, text: (err as Error).message})
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="lg-rgb">
            <form className="lg-glass" onSubmit={submit}>
                <div className="lg-welcome">
                    <h2>WELCOME</h2>
                    <p>Hệ thống trích xuất &amp; số hóa chứng từ BIZOCR AI</p>
                </div>

                <div className="lg-tabs" role="tablist">
                    <button type="button" role="tab" aria-selected={tab === 'register'}
                            className={`lg-tab${tab === 'register' ? ' on' : ''}`} onClick={() => go('register')}>
                        <span className="material-symbols-outlined">person_add</span>Đăng ký
                    </button>
                    <button type="button" role="tab" aria-selected={tab === 'login'}
                            className={`lg-tab${tab === 'login' ? ' on' : ''}`} onClick={() => go('login')}>
                        <span className="material-symbols-outlined">login</span>Đăng nhập
                    </button>
                </div>

                {tab === 'login' ? (
                    <div className="lg-form">
                        <p className="lg-sub">Truy cập bộ công cụ trích xuất hợp đồng &amp; hóa đơn</p>
                        <Field label="Tên đăng nhập" icon="person" required autoComplete="username"
                               placeholder="Nhập tên đăng nhập"
                               value={login.username} onChange={(e) => setLogin({...login, username: e.target.value})}/>
                        <Field label="Mật khẩu" icon="key" required type="password" autoComplete="current-password"
                               placeholder="••••••••"
                               value={login.password} onChange={(e) => setLogin({...login, password: e.target.value})}/>
                        <label className="lg-check">
                            <input type="checkbox" checked={login.remember}
                                   onChange={(e) => setLogin({...login, remember: e.target.checked})}/>
                            Ghi nhớ đăng nhập
                        </label>
                    </div>
                ) : (
                    <div className="lg-form">
                        <p className="lg-sub">Tạo tài khoản BIZOCR</p>
                        <div className="lg-row">
                            <Field label="Tên đăng nhập" icon="badge" required autoComplete="username"
                                   placeholder="Nhập tên đăng nhập" value={reg.username} onChange={r('username')}/>
                            <Field label="Mật khẩu" icon="lock" required type="password" autoComplete="new-password"
                                   placeholder="Mật khẩu bảo mật" value={reg.password} onChange={r('password')}/>
                        </div>
                        <div className="lg-row">
                            <Field label="Email" icon="mail" required type="email" placeholder="ten@doanhnghiep.vn"
                                   value={reg.email} onChange={r('email')}/>
                            <Field label="Số điện thoại" icon="call" type="tel" placeholder="0901234567"
                                   value={reg.phoneNumber} onChange={r('phoneNumber')}/>
                        </div>
                        <div className="lg-row">
                            <Field label="Mã số thuế doanh nghiệp" icon="receipt_long" mono placeholder="0312345678"
                                   value={reg.taxCode} onChange={r('taxCode')}/>
                            <Field label="Tên công ty" icon="apartment" placeholder="Tên doanh nghiệp"
                                   value={reg.companyName} onChange={r('companyName')}/>
                        </div>
                        <p className="lg-note">Tài khoản mới có vai trò Reviewer: duyệt và gán nhãn OCR.</p>
                    </div>
                )}

                {msg && <p className={`lg-msg ${msg.ok ? 'ok' : 'err'}`} role="alert">{msg.text}</p>}

                <button className="lg-gold" disabled={busy}>
                    {busy ? 'Đang xử lý…' : tab === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
                </button>
                <p className="lg-foot-note">Hệ thống tự động liên kết dữ liệu doanh nghiệp và cấp quyền theo vai
                    trò.</p>
            </form>
        </div>
    )
}