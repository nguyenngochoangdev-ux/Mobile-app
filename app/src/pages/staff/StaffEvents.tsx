import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchPage } from '../../lib/paged'
import type { EventResponse } from '../../api/generated'
import { messageOf } from '../LoginPage'

export default function StaffEvents() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['events'],
    queryFn: () => fetchPage<EventResponse>('/api/events', { size: 50, sort: 'startAt,desc' }),
  })

  if (isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (error) return <p className="text-rose-400">{messageOf(error)}</p>

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Sự kiện</h2>

      <ul className="space-y-2">
        {(data?.content ?? []).map((e) => (
          <li key={e.id} className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3">
            <div className="flex items-start gap-3">
              <div className="min-w-0">
                <p className="font-medium text-slate-100">{e.title}</p>
                <p className="text-xs text-slate-400 mt-0.5">
                  {e.startAt ? new Date(e.startAt).toLocaleString('vi-VN') : ''}
                  {e.location ? ` · ${e.location}` : ''}
                  {e.criteriaCode ? ` · ${e.criteriaCode}` : ''}
                </p>
              </div>
              <span className="ml-auto text-[11px] rounded-full border border-slate-700 bg-slate-800 px-2 py-0.5 text-slate-300 shrink-0">
                {e.status}
              </span>
            </div>

            <div className="flex gap-2 mt-3">
              <Link
                to={`/cb/su-kien/${e.id}`}
                className="rounded-lg border border-slate-700 hover:border-slate-600 px-3 py-1.5 text-sm"
              >
                Danh sách điểm danh
              </Link>
              {/* Mở tab mới: màn hình trình chiếu chạy trên máy chiếu, cán bộ vẫn
                  giữ tab quản lý trên máy mình. */}
              <a
                href={`/trinh-chieu/${e.id}`}
                target="_blank"
                rel="noreferrer"
                className="rounded-lg bg-sky-600 hover:bg-sky-500 px-3 py-1.5 text-sm font-medium"
              >
                Mở màn hình QR ↗
              </a>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
