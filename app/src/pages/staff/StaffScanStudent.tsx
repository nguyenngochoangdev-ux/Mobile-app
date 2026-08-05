import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Html5Qrcode } from 'html5-qrcode'
import { AttendanceService } from '../../api/generated'
import { messageOf } from '../LoginPage'

/**
 * Cán bộ quét mã QR của sinh viên — LUỒNG ĐẢO CHIỀU.
 *
 * PROJECT.md §2.4 phương án 3. Sự kiện lấy từ URL nên cán bộ không phải chọn lại giữa hàng
 * người đang xếp hàng.
 *
 * KHÁC màn hình quét của sinh viên ở một điểm quan trọng: máy quét KHÔNG dừng sau mỗi lần
 * quét. Cán bộ đứng quét liên tục vài chục người, dừng rồi bật lại camera mỗi lượt là không
 * dùng được trong thực tế. Chống quét trùng bằng cách nhớ mã vừa xử lý.
 */

const READER_ID = 'staff-qr-reader'

/** Bỏ qua cùng một mã trong 3 giây — html5-qrcode bắn callback mỗi khung hình. */
const DEDUPE_MS = 3000

type Entry = {
  at: number
  ok: boolean
  text: string
  note?: string
  warning?: string
}

export default function StaffScanStudent() {
  const { eventId } = useParams()
  const [scanning, setScanning] = useState(false)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [log, setLog] = useState<Entry[]>([])

  const scannerRef = useRef<Html5Qrcode | null>(null)
  const lastRef = useRef<{ text: string; at: number } | null>(null)
  const coordsRef = useRef<{ lat: number; lng: number } | null>(null)

  useEffect(() => {
    // Lấy toạ độ MỘT LẦN khi mở màn hình: cán bộ đứng yên một chỗ suốt buổi, và gọi GPS
    // cho từng lượt quét sẽ làm chậm hàng người xếp hàng.
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => { coordsRef.current = { lat: pos.coords.latitude, lng: pos.coords.longitude } },
        () => { /* không có toạ độ thì thôi, geofence là cảnh báo mềm */ },
        { timeout: 5000, enableHighAccuracy: true },
      )
    }
    return () => { void stopScanner() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function startScanner() {
    setCameraError(null)
    try {
      const scanner = new Html5Qrcode(READER_ID)
      scannerRef.current = scanner
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (text) => void onScan(text),
        () => { /* khung hình không đọc được — bỏ qua */ },
      )
      setScanning(true)
    } catch (err) {
      setCameraError(messageOf(err))
    }
  }

  async function stopScanner() {
    const s = scannerRef.current
    scannerRef.current = null
    setScanning(false)
    if (s) {
      try {
        await s.stop()
        s.clear()
      } catch { /* đã dừng rồi */ }
    }
  }

  function push(entry: Entry) {
    setLog((prev) => [entry, ...prev].slice(0, 30))
  }

  async function onScan(text: string) {
    const now = Date.now()
    if (lastRef.current && lastRef.current.text === text && now - lastRef.current.at < DEDUPE_MS) {
      return
    }
    lastRef.current = { text, at: now }

    try {
      const res = await AttendanceService.scanStudent({
        eventId: Number(eventId),
        payload: text,
        lat: coordsRef.current?.lat,
        lng: coordsRef.current?.lng,
      })
      const a = res.attendance
      push({
        at: now,
        ok: true,
        text: `${a?.mssv ?? ''} · ${a?.fullName ?? ''}`,
        note: a?.geofenceOk === false ? 'Ngoài khu vực sự kiện' : undefined,
        warning: res.warning ?? undefined,
      })
      if (navigator.vibrate) navigator.vibrate(res.warning ? [60, 60, 60] : 40)
    } catch (err) {
      push({ at: now, ok: false, text: messageOf(err) })
      if (navigator.vibrate) navigator.vibrate([120, 60, 120])
    }
  }

  const ok = log.filter((e) => e.ok).length

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">Quét mã sinh viên</h2>
        <Link to={`/cb/su-kien/${eventId}`} className="ml-auto text-sm text-sky-400 hover:underline">
          ← Về sự kiện
        </Link>
      </div>

      <p className="text-sm text-slate-400">
        Luồng dự phòng: sinh viên mở màn hình <b>“Mã của tôi”</b>, bạn quét bằng máy này.
        Dùng khi hội trường mất sóng hoặc camera máy sinh viên không bật được.
      </p>

      <div id={READER_ID} className="min-h-[80px] overflow-hidden rounded-xl bg-slate-900" />

      {!scanning ? (
        <button
          onClick={() => void startScanner()}
          className="w-full rounded-lg bg-sky-600 px-3 py-3 font-medium hover:bg-sky-500"
        >
          Bật camera
        </button>
      ) : (
        <button
          onClick={() => void stopScanner()}
          className="w-full rounded-lg border border-slate-700 px-3 py-3"
        >
          Dừng
        </button>
      )}

      {cameraError && (
        <p className="rounded-lg border border-rose-900 bg-rose-950/40 px-3 py-2 text-sm text-rose-400">
          {cameraError}
        </p>
      )}

      {log.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm text-slate-400">
            Đã ghi nhận <b className="text-emerald-300">{ok}</b> / {log.length} lượt quét
          </p>
          <ul className="space-y-1">
            {log.map((e) => (
              <li
                key={e.at}
                className={`rounded-lg border px-3 py-2 text-sm ${
                  !e.ok
                    ? 'border-rose-900 bg-rose-950/40 text-rose-200'
                    : e.warning
                      ? 'border-amber-900 bg-amber-950/40 text-amber-200'
                      : 'border-emerald-900 bg-emerald-950/40 text-emerald-200'
                }`}
              >
                <p className="font-medium">{e.text}</p>
                {e.warning && <p className="mt-1 opacity-80">{e.warning}</p>}
                {e.note && <p className="mt-1 opacity-80">{e.note}</p>}
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-xs text-slate-500">
        Mã QR của sinh viên không giả được, nhưng nó <b>không</b> chứng minh sự có mặt — một
        ảnh chụp mã của bạn khác vẫn quét được. Hãy nhìn mặt người đưa mã, giống như kiểm tra
        thẻ sinh viên. Bản ghi được lưu với phương thức <code>QR_SHOW</code>.
      </p>
    </div>
  )
}
