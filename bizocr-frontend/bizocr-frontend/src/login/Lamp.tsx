import {useRef} from 'react'

// Tiếng "tách" khi kéo dây đèn (Web Audio, không cần file âm thanh)
function click(turnOn: boolean) {
    try {
        const ctx = new (window.AudioContext || (window as any).webkitAudioContext)()
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        const t = ctx.currentTime
        osc.type = turnOn ? 'triangle' : 'sine'
        osc.frequency.setValueAtTime(turnOn ? 580 : 320, t)
        osc.frequency.exponentialRampToValueAtTime(turnOn ? 920 : 150, t + 0.08)
        gain.gain.setValueAtTime(0.25, t)
        gain.gain.exponentialRampToValueAtTime(0.001, t + 0.11)
        osc.connect(gain).connect(ctx.destination)
        osc.start()
        osc.stop(t + 0.12)
        osc.onended = () => ctx.close()
    } catch { /* trình duyệt chặn âm thanh thì bỏ qua */
    }
}

export default function Lamp({on, onToggle}: { on: boolean; onToggle: () => void }) {
    const cord = useRef<HTMLButtonElement>(null)

    function pull() {
        const el = cord.current
        if (el) {
            el.classList.remove('pull')
            void el.offsetWidth // ép reflow để chạy lại animation
            el.classList.add('pull')
        }
        click(!on)
        onToggle()
    }

    return (
        <div className="lg-lamp">
            <div className="lg-lampbody">
                <div className="lg-dome" onClick={pull} title="Bấm vào đèn để bật/tắt"/>
                <button
                    ref={cord}
                    type="button"
                    className="lg-cord"
                    onClick={pull}
                    aria-pressed={on}
                    aria-label="Kéo dây để bật/tắt đèn"
                >
                    <span className="line"/>
                    <span className="bead"/>
                </button>
                <div className="lg-stem"/>
                <div className="lg-base"/>
            </div>
        </div>
    )
}