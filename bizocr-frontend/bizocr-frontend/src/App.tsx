import { useEffect, useState } from 'react'
import { getToken, setToken } from './api'
import type { Step } from './lib'
import Login from './login/Login'
import Shell from './layout/Shell'
import Step1Upload from './pages/Step1Upload'
import Step2Track from './pages/Step2Track'
import Step3Compare from './pages/Step3Compare'
import Step4Classify from './pages/Step4Classify'
import Step5Edit from './pages/Step5Edit'
import Step6Approve from './pages/Step6Approve'
import ArchivePage from "./pages/ArchivePage";
import ConfigPage from "./pages/ConfigPage";

export default function App() {
  const [authed, setAuthed] = useState(!!getToken())
  const [step, setStep] = useState<Step>(1)
  const [docId, setDocId] = useState<number | null>(null)

  useEffect(() => {
    const onLogout = () => setAuthed(false)
    window.addEventListener('logout', onLogout)
    return () => window.removeEventListener('logout', onLogout)
  }, [])

  if (!authed) return <Login onDone={() => setAuthed(true)} />

  return (
    <Shell step={step} onStep={setStep} onLogout={() => { setToken(null); setAuthed(false) }}>
      {step === 1 && <Step1Upload onUploaded={(id) => { setDocId(id); setStep(2) }} />}
      {step === 2 && <Step2Track docId={docId} onSelect={setDocId} onNext={() => setStep(3)} />}
      {step === 3 && <Step3Compare docId={docId} onSelect={setDocId} onNext={() => setStep(4)} />}
      {step === 4 && <Step4Classify docId={docId} onSelect={setDocId} onNext={() => setStep(5)} />}
      {step === 5 && <Step5Edit docId={docId} onSelect={setDocId} onNext={() => setStep(6)} />}
      {step === 6 && <Step6Approve docId={docId} onSelect={setDocId} onBack={() => setStep(5)} />}
      {step === 8 && <ArchivePage />}
      {step === 9 && <ConfigPage />}
    </Shell>
  )
}
