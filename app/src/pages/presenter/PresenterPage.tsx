import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import QRCode from 'qrcode'
import { AttendanceService } from '../../api/generated'
import { messageOf } from '../LoginPage'

/**
 * Màn hình trình chiếu. Chạy toàn màn hình trên máy chiếu tại hội trường.
 *
 * Poll token mới ngay khi slot hiện tại hết hạn (server trả `validUntil`), thay vì
 * đặt một interval cố định 10 giây. Interval cố định sẽ trôi dần khỏi ranh giới slot
 * và có lúc hiển thị mã đã hết hạn.
 */
export default function PresenterPage() {
  const { eventId } = useParams()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const timerRef = useRef<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [slot, setSlot] = useState<number | null>(null)
  const [secondsLeft, setSecondsLeft] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function tick() {
      try {
        const qr = await AttendanceService.currentQr(Number(eventId))
        if (cancelled) return

        setError(null)
        setSlot(qr.slot ?? null)

        // Payload đúng như backend mong đợi: {e, s, t}
        const payload = JSON.stringify({ e: Number(eventId), s: qr.slot, t: qr.token })
        if (canvasRef.current) {
          await QRCode.toCanvas(canvasRef.current, payload, {
            width: 420,
            margin: 1,
            color: { dark: '#0f172a', light: '#ffffff' },
          })
        }

        // Hẹn lần lấy tiếp theo đúng lúc slot hết hạn, cộng 300ms đệm.
        const msLeft = qr.validUntil
          ? new Date(qr.validUntil).getTime() - Date.now()
          : 10_000
        setSecondsLeft(Math.max(0, Math.round(msLeft / 1000)))
        timerRef.current = window.setTimeout(tick, Math.max(1000, msLeft + 300))
      } catch (err) {
        if (cancelled) return
        setError(messageOf(err))
        timerRef.current = window.setTimeout(tick, 3000)
      }
    }

    void tick()
    return () => {
      cancelled = true
      if (timerRef.current) window.clearTimeout(timerRef.current)
    }
  }, [eventId])

  // Đồng hồ đếm ngược, thuần hiển thị.
  useEffect(() => {
    const id = window.setInterval(() => setSecondsLeft((s) => (s > 0 ? s - 1 : 0)), 1000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div className="min-h-screen grid place-items-center bg-slate-950 p-6">
      <div className="text-center space-y-6">
        <h1 className="text-2xl font-semibold text-slate-100">Quét mã để điểm danh</h1>

        <div className="inline-block rounded-2xl bg-white p-5">
          <canvas ref={canvasRef} />
        </div>

        <div className="space-y-1">
          <p className="text-slate-400">
            Mã tự đổi sau <span className="text-sky-300 font-mono">{secondsLeft}s</span>
          </p>
          {slot !== null && (
            <p className="text-xs text-slate-600 font-mono">slot {slot}</p>
          )}
        </div>

        {error && (
          <p className="text-rose-400 bg-rose-950/50 border border-rose-900 rounded-lg px-4 py-2">
            {error}
          </p>
        )}

        <p className="text-sm text-slate-500 max-w-md mx-auto">
          Ảnh chụp màn hình mã này sẽ hết hạn trước khi gửi được cho người khác.
        </p>
      </div>
    </div>
  )
}
