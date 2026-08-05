import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { AttendanceService } from '../../api/generated'
import { messageOf } from '../LoginPage'

/**
 * Mã QR của chính sinh viên, để cán bộ quét — LUỒNG ĐẢO CHIỀU.
 *
 * PROJECT.md §2.4 phương án 3. Đây là phương án cứu, dùng khi:
 *   - hội trường mất sóng, máy sinh viên không gọi được API check-in
 *   - camera máy sinh viên hỏng, hoặc bị iOS chặn trong PWA standalone
 *   - máy sinh viên sắp hết pin (hiện mã rồi tắt màn hình nhanh hơn nhiều so với quét)
 *
 * VÌ SAO PHẢI CACHE MÃ VÀO localStorage: nếu trang này chỉ hoạt động khi có mạng thì nó
 * vô dụng đúng vào lúc cần nhất. Mã cũ vẫn được backend chấp nhận trong cửa sổ offline,
 * nhưng bản ghi sẽ mang `verified = false` — và màn hình phải nói thẳng điều đó với sinh
 * viên, chứ không giả vờ mọi thứ bình thường.
 */

const CACHE_KEY = 'drl.myQr'

type Cached = { payload: string; slot: number; savedAt: number }

function readCache(): Cached | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? (JSON.parse(raw) as Cached) : null
  } catch {
    return null
  }
}

export default function MyQrPage() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const timerRef = useRef<number | null>(null)

  const [slot, setSlot] = useState<number | null>(null)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [stale, setStale] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function draw(payload: string) {
      if (!canvasRef.current) return
      await QRCode.toCanvas(canvasRef.current, payload, {
        width: 320,
        margin: 1,
        color: { dark: '#0f172a', light: '#ffffff' },
      })
    }

    async function tick() {
      try {
        const qr = await AttendanceService.myQr()
        if (cancelled) return

        const payload = qr.payload ?? ''
        await draw(payload)
        localStorage.setItem(
          CACHE_KEY,
          JSON.stringify({ payload, slot: qr.slot, savedAt: Date.now() } satisfies Cached),
        )

        setSlot(qr.slot ?? null)
        setStale(false)
        setError(null)

        // Hẹn lần lấy tiếp theo đúng lúc mã hết hạn "tươi", cộng 300ms đệm — cùng cách
        // với màn hình trình chiếu, để mã không bao giờ hiển thị ở trạng thái vừa hết hạn.
        const msLeft = qr.freshUntil
          ? new Date(qr.freshUntil).getTime() - Date.now()
          : 10_000
        setSecondsLeft(Math.max(0, Math.round(msLeft / 1000)))
        timerRef.current = window.setTimeout(tick, Math.max(1000, msLeft + 300))
      } catch (err) {
        if (cancelled) return

        // Mất mạng: hiện lại mã đã lưu. Đây chính là lý do trang này tồn tại.
        const cached = readCache()
        if (cached) {
          await draw(cached.payload)
          setSlot(cached.slot)
          setStale(true)
          setError(null)
        } else {
          setError(messageOf(err))
        }
        timerRef.current = window.setTimeout(tick, 5000)
      }
    }

    void tick()
    return () => {
      cancelled = true
      if (timerRef.current) window.clearTimeout(timerRef.current)
    }
  }, [])

  useEffect(() => {
    const id = window.setInterval(() => setSecondsLeft((s) => (s > 0 ? s - 1 : 0)), 1000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Mã của tôi</h2>
      <p className="text-sm text-slate-400">
        Đưa màn hình này cho cán bộ quét. Dùng khi hội trường mất sóng hoặc camera máy bạn
        không bật được.
      </p>

      <div className="grid place-items-center">
        <div className="inline-block rounded-2xl bg-white p-4">
          <canvas ref={canvasRef} />
        </div>
      </div>

      {stale ? (
        <div className="rounded-xl border border-amber-900 bg-amber-950/50 px-4 py-3 text-sm text-amber-200">
          <p className="font-medium">Đang dùng mã đã lưu — máy bạn không có mạng.</p>
          <p className="mt-1 opacity-80">
            Cán bộ vẫn quét được, nhưng bản ghi sẽ được đánh dấu <b>chưa xác thực bằng máy</b>{' '}
            và cán bộ cần đối chiếu thẻ sinh viên.
          </p>
        </div>
      ) : (
        <p className="text-center text-sm text-slate-400">
          Mã tự đổi sau <span className="font-mono text-sky-300">{secondsLeft}s</span>
          {slot !== null && <span className="ml-2 font-mono text-xs text-slate-600">slot {slot}</span>}
        </p>
      )}

      {error && (
        <p className="rounded-lg border border-rose-900 bg-rose-950/40 px-3 py-2 text-sm text-rose-400">
          {error}
        </p>
      )}

      <p className="text-xs text-slate-500">
        Mã này gắn với tài khoản của bạn và đổi liên tục. Nhưng nó <b>không</b> chứng minh
        bạn có mặt — cán bộ vẫn phải nhìn mặt bạn khi quét, giống như kiểm tra thẻ sinh viên.
      </p>
    </div>
  )
}
