import { get, set } from 'idb-keyval'
import { AttendanceService } from '../api/generated'

const KEY = 'drl.offlineScans'

export type PendingScan = {
  eventId: number
  slot: number
  token: string
  deviceFp: string
  lat?: number
  lng?: number
  scannedAt: string
  queuedAt: string
}

/**
 * Hàng đợi check-in offline.
 *
 * Hội trường thường mất sóng nên đây là yêu cầu bắt buộc, không phải tính năng phụ.
 * Điểm quan trọng: lưu lại `scannedAt` VÀ `slot` đúng lúc quét. Máy chủ kiểm tra token
 * theo slot đó, nên sinh viên vẫn phải thật sự có mặt lúc mã QR đang hiển thị — đồng
 * bộ muộn không tạo ra kẽ hở gian lận, chỉ nới thời hạn gửi lên tối đa 24 giờ.
 */
export async function enqueue(scan: PendingScan): Promise<void> {
  const queue = (await get<PendingScan[]>(KEY)) ?? []
  queue.push(scan)
  await set(KEY, queue)
}

export async function peek(): Promise<PendingScan[]> {
  return (await get<PendingScan[]>(KEY)) ?? []
}

export async function clear(): Promise<void> {
  await set(KEY, [])
}

export type FlushResult = { accepted: number; rejected: number; errors: string[] }

/** Trả về null khi hàng đợi rỗng — không gọi API vô ích. */
export async function flush(): Promise<FlushResult | null> {
  const queue = await peek()
  if (queue.length === 0) return null

  const result = await AttendanceService.sync({
    scans: queue.map((s) => ({
      eventId: s.eventId,
      slot: s.slot,
      token: s.token,
      deviceFp: s.deviceFp,
      lat: s.lat,
      lng: s.lng,
      scannedAt: s.scannedAt,
    })),
  })

  // Xoá cả hàng đợi kể cả khi có bản bị từ chối: bản bị từ chối sẽ không bao giờ
  // được chấp nhận ở lần thử sau (token sai, hoặc đã quá cửa sổ 24 giờ). Giữ lại
  // chỉ khiến hàng đợi phình mãi. Lỗi được trả về để hiển thị cho sinh viên.
  await clear()

  return {
    accepted: result.accepted ?? 0,
    rejected: result.rejected ?? 0,
    errors: result.errors ?? [],
  }
}
