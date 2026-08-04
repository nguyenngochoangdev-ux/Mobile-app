import { useEffect, useRef, useState } from 'react'
import { Html5Qrcode } from 'html5-qrcode'
import { useQueryClient } from '@tanstack/react-query'
import { AttendanceService } from '../../api/generated'
import { getDeviceFp } from '../../lib/device'
import { enqueue, flush, peek } from '../../lib/offlineQueue'
import { messageOf } from '../LoginPage'

type QrPayload = { e: number; s: number; t: string }

type Result =
  | { kind: 'ok'; title: string; note?: string }
  | { kind: 'queued'; title: string }
  | { kind: 'error'; title: string }

const READER_ID = 'qr-reader'

export default function ScanPage() {
  const [result, setResult] = useState<Result | null>(null)
  const [scanning, setScanning] = useState(false)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [pending, setPending] = useState(0)

  const scannerRef = useRef<Html5Qrcode | null>(null)
  // Chặn xử lý trùng: html5-qrcode bắn callback liên tục khi mã còn trong khung hình.
  const busyRef = useRef(false)
  const queryClient = useQueryClient()

  useEffect(() => {
    peek().then((q) => setPending(q.length))
    return () => {
      stopScanner()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Có mạng trở lại thì đẩy hàng đợi lên. Đây là lý do chế độ offline dùng được thật:
  // sinh viên không phải nhớ bấm đồng bộ.
  useEffect(() => {
    const onOnline = () => void doFlush()
    window.addEventListener('online', onOnline)
    return () => window.removeEventListener('online', onOnline)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function doFlush() {
    try {
      const r = await flush()
      if (r) {
        setPending(0)
        void queryClient.invalidateQueries({ queryKey: ['attendance', 'me'] })
        setResult({
          kind: r.rejected > 0 ? 'error' : 'ok',
          title: `Đồng bộ: ${r.accepted} thành công, ${r.rejected} bị từ chối`,
          note: r.errors[0],
        })
      }
    } catch {
      /* vẫn mất mạng — để nguyên hàng đợi, thử lại lần sau */
    }
  }

  async function startScanner() {
    setCameraError(null)
    setResult(null)
    try {
      const scanner = new Html5Qrcode(READER_ID)
      scannerRef.current = scanner
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (text) => void onScan(text),
        () => { /* mỗi khung hình không đọc được đều gọi vào đây — bỏ qua */ },
      )
      setScanning(true)
    } catch (err) {
      // Trên iOS, PWA chạy standalone hay bị chặn camera. Nói rõ cách xử lý thay vì
      // để màn hình trắng. Xem PROJECT.md §2.4.
      setCameraError(
        `${messageOf(err)}. Nếu bạn đang mở app từ màn hình chính trên iPhone, ` +
        `hãy thử mở bằng Safari.`,
      )
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

  async function onScan(text: string) {
    if (busyRef.current) return
    busyRef.current = true

    let payload: QrPayload
    try {
      payload = JSON.parse(text)
      if (typeof payload.e !== 'number' || typeof payload.s !== 'number' || !payload.t) {
        throw new Error('thiếu trường')
      }
    } catch {
      setResult({ kind: 'error', title: 'Mã QR không đúng định dạng của hệ thống' })
      busyRef.current = false
      return
    }

    await stopScanner()

    const deviceFp = getDeviceFp()
    const coords = await currentPosition()
    const scannedAt = new Date().toISOString()

    try {
      const res = await AttendanceService.checkin({
        eventId: payload.e,
        slot: payload.s,
        token: payload.t,
        deviceFp,
        lat: coords?.lat,
        lng: coords?.lng,
      })
      void queryClient.invalidateQueries({ queryKey: ['attendance', 'me'] })
      setResult({
        kind: 'ok',
        title: `Điểm danh thành công: ${res.eventTitle}`,
        note: res.geofenceOk === false
          ? 'Ghi nhận vị trí ngoài khu vực sự kiện — bản ghi sẽ được cán bộ xem lại.'
          : undefined,
      })
    } catch (err) {
      // Phân biệt "mất mạng" với "server từ chối".
      // Mất mạng -> vào hàng đợi. Server từ chối -> báo lỗi, KHÔNG xếp hàng, vì
      // gửi lại cũng sẽ bị từ chối y hệt.
      if (isNetworkError(err)) {
        await enqueue({ ...payloadToScan(payload, deviceFp, coords, scannedAt) })
        setPending((n) => n + 1)
        setResult({
          kind: 'queued',
          title: 'Không có mạng — đã lưu lần quét, sẽ tự gửi khi có kết nối',
        })
      } else {
        setResult({ kind: 'error', title: messageOf(err) })
      }
    } finally {
      busyRef.current = false
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">Quét mã điểm danh</h2>
        {pending > 0 && (
          <button
            onClick={() => void doFlush()}
            className="ml-auto text-xs rounded-full bg-amber-950 text-amber-300 border border-amber-900 px-3 py-1"
          >
            {pending} lần quét chờ gửi · bấm để thử
          </button>
        )}
      </div>

      <div id={READER_ID} className="rounded-xl overflow-hidden bg-slate-900 min-h-[80px]" />

      {!scanning && (
        <button
          onClick={() => void startScanner()}
          className="w-full rounded-lg bg-sky-600 hover:bg-sky-500 px-3 py-3 font-medium"
        >
          Bật camera
        </button>
      )}
      {scanning && (
        <button
          onClick={() => void stopScanner()}
          className="w-full rounded-lg border border-slate-700 px-3 py-3"
        >
          Dừng
        </button>
      )}

      {cameraError && (
        <p className="text-sm text-rose-400 bg-rose-950/40 border border-rose-900 rounded-lg px-3 py-2">
          {cameraError}
        </p>
      )}

      {result && (
        <div
          className={`rounded-xl px-4 py-3 border text-sm ${
            result.kind === 'ok'
              ? 'bg-emerald-950/50 border-emerald-900 text-emerald-200'
              : result.kind === 'queued'
                ? 'bg-amber-950/50 border-amber-900 text-amber-200'
                : 'bg-rose-950/50 border-rose-900 text-rose-200'
          }`}
        >
          <p className="font-medium">{result.title}</p>
          {'note' in result && result.note && (
            <p className="mt-1 opacity-80">{result.note}</p>
          )}
        </div>
      )}

      <p className="text-xs text-slate-500">
        Mã QR đổi mỗi 10 giây. Ảnh chụp màn hình gửi cho người khác sẽ hết hạn trước khi
        dùng được.
      </p>
    </div>
  )
}

function payloadToScan(
  p: QrPayload,
  deviceFp: string,
  coords: { lat: number; lng: number } | null,
  scannedAt: string,
) {
  return {
    eventId: p.e,
    slot: p.s,
    token: p.t,
    deviceFp,
    lat: coords?.lat,
    lng: coords?.lng,
    scannedAt,
    queuedAt: new Date().toISOString(),
  }
}

/** Không chặn luồng check-in vì GPS: hết 4 giây thì đi tiếp không toạ độ. */
function currentPosition(): Promise<{ lat: number; lng: number } | null> {
  if (!navigator.geolocation) return Promise.resolve(null)
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => resolve(null),
      { timeout: 4000, enableHighAccuracy: true },
    )
  })
}

function isNetworkError(err: unknown): boolean {
  // ApiError của client sinh tự động có status; lỗi mạng thật thì không có.
  const status = (err as { status?: number })?.status
  if (typeof status === 'number' && status > 0) return false
  return !navigator.onLine || err instanceof TypeError
}
