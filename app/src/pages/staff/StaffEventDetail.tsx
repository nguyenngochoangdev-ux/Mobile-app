import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { AttendanceService } from '../../api/generated'
import type { AttendanceResponse } from '../../api/generated'
import { fetchPage } from '../../lib/paged'
import { messageOf } from '../LoginPage'

export default function StaffEventDetail() {
  const { eventId } = useParams()
  const id = Number(eventId)

  const list = useQuery({
    queryKey: ['attendance', 'event', id],
    queryFn: () =>
      fetchPage<AttendanceResponse>(`/api/attendance/event/${id}`, { size: 100 }),
  })

  const stats = useQuery({
    queryKey: ['attendance', 'stats', id],
    queryFn: () => AttendanceService.stats(id),
  })

  if (list.isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (list.error) return <p className="text-rose-400">{messageOf(list.error)}</p>

  const items = list.data?.content ?? []
  // String(): a.method là enum sinh tự động, so sánh trực tiếp với literal sẽ lỗi kiểu.
  const manualCount = items.filter((a) => String(a.method) === 'MANUAL').length

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">Điểm danh sự kiện #{id}</h2>
        {/* Luồng đảo chiều (PROJECT.md §2.4 phương án 3) — lối vào phải ở ngay đây,
            vì cán bộ chỉ cần tới nó khi luồng thường đã hỏng và đang có người xếp hàng. */}
        <Link
          to={`/cb/su-kien/${id}/quet`}
          className="ml-auto rounded-lg bg-sky-600 px-3 py-1.5 text-sm font-medium hover:bg-sky-500"
        >
          Quét mã sinh viên
        </Link>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <Stat label="Tổng" value={stats.data?.total ?? items.length} />
        {/* Hai chỉ số dưới đây là chất lượng dữ liệu, đưa thẳng vào báo cáo:
            chúng đo phần mà hệ thống KHÔNG bảo đảm được. */}
        <Stat label="Ngoài khu vực" value={stats.data?.outsideGeofence ?? 0} tone="amber" />
        <Stat label="Nhập tay" value={manualCount} tone="amber" />
      </div>

      {items.length === 0 && (
        <p className="text-sm text-slate-400">Chưa có ai điểm danh.</p>
      )}

      <ul className="space-y-1.5">
        {items.map((a) => (
          <li
            key={a.id}
            className="rounded-lg border border-slate-800 bg-slate-900/60 px-3 py-2 text-sm flex items-center gap-3"
          >
            <div className="min-w-0">
              <p className="text-slate-100 truncate">{a.fullName}</p>
              <p className="text-xs text-slate-500 font-mono">{a.mssv}</p>
            </div>
            <div className="ml-auto text-right shrink-0">
              <p className="text-xs text-slate-400">
                {a.checkinAt ? new Date(a.checkinAt).toLocaleTimeString('vi-VN') : ''}
              </p>
              <p className="text-[11px] flex gap-1.5 justify-end mt-0.5">
                <span className={a.verified ? 'text-emerald-400' : 'text-amber-400'}>
                  {a.method}
                </span>
                {a.geofenceOk === false && <span className="text-amber-400">⚠ xa</span>}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: 'amber' }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-3 py-3 text-center">
      <p className={`text-2xl font-semibold ${tone === 'amber' ? 'text-amber-400' : 'text-slate-100'}`}>
        {value}
      </p>
      <p className="text-xs text-slate-400 mt-0.5">{label}</p>
    </div>
  )
}
