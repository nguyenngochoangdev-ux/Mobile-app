import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchPage } from '../../lib/paged'
import type { AttendanceResponse } from '../../api/generated'

export default function StudentHome() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['attendance', 'me'],
    queryFn: () => fetchPage<AttendanceResponse>('/api/attendance/me', { size: 50 }),
  })

  if (isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (error) return <p className="text-rose-400">{(error as Error).message}</p>

  const items = data?.content ?? []

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">Hoạt động đã tham gia</h2>
        <span className="text-sm text-slate-400">{data?.totalElements ?? 0} hoạt động</span>
        <Link
          to="/sv/quet"
          className="ml-auto rounded-lg bg-sky-600 hover:bg-sky-500 px-3 py-2 text-sm font-medium"
        >
          Quét QR
        </Link>
      </div>

      {items.length === 0 && (
        <p className="text-slate-400 text-sm">
          Chưa có hoạt động nào. Quét mã QR tại sự kiện để điểm danh.
        </p>
      )}

      <ul className="space-y-2">
        {items.map((a) => (
          <li
            key={a.id}
            className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3"
          >
            <div className="flex items-start gap-3">
              <div className="min-w-0">
                <p className="font-medium text-slate-100 truncate">{a.eventTitle}</p>
                <p className="text-xs text-slate-400 mt-0.5">
                  {a.checkinAt ? new Date(a.checkinAt).toLocaleString('vi-VN') : ''}
                  {a.checkoutAt ? ' → đã check-out' : ''}
                </p>
              </div>
              <div className="ml-auto flex flex-col items-end gap-1 shrink-0">
                <Badge method={a.method} verified={a.verified} />
                {/* anchored = đã có leaf_hash, tức bản ghi đã vào cây Merkle và
                    được neo lên chuỗi. Job neo làm việc này từ tuần 4. */}
                <span className={`text-[11px] ${a.anchored ? 'text-emerald-400' : 'text-slate-500'}`}>
                  {a.anchored ? '⛓ đã neo' : 'chờ neo'}
                </span>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Badge({ method, verified }: { method?: string; verified?: boolean }) {
  if (method === 'MANUAL') {
    return (
      <span className="text-[11px] rounded-full bg-amber-950 text-amber-300 border border-amber-900 px-2 py-0.5">
        cán bộ nhập tay
      </span>
    )
  }
  return (
    <span
      className={`text-[11px] rounded-full px-2 py-0.5 border ${
        verified
          ? 'bg-emerald-950 text-emerald-300 border-emerald-900'
          : 'bg-slate-800 text-slate-400 border-slate-700'
      }`}
    >
      {method === 'OFFLINE_SYNC' ? 'quét offline' : 'đã xác thực'}
    </span>
  )
}
