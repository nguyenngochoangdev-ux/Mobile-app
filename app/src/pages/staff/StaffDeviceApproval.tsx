import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DevicesService } from '../../api/generated'
import type { DeviceResponse } from '../../api/generated'
import { fetchPage } from '../../lib/paged'
import { messageOf } from '../LoginPage'

export default function StaffDeviceApproval() {
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['devices', 'pending'],
    queryFn: () => fetchPage<DeviceResponse>('/api/devices/pending', { size: 50 }),
  })

  const approve = useMutation({
    mutationFn: (id: number) => DevicesService.approve(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['devices', 'pending'] }),
  })

  const revoke = useMutation({
    mutationFn: (id: number) => DevicesService.revoke(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['devices', 'pending'] }),
  })

  if (isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (error) return <p className="text-rose-400">{messageOf(error)}</p>

  const items = data?.content ?? []

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Thiết bị chờ duyệt</h2>

      {items.length === 0 && (
        <p className="text-sm text-slate-400">Không có yêu cầu nào đang chờ.</p>
      )}

      <ul className="space-y-2">
        {items.map((d) => (
          <li key={d.id} className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3">
            <p className="text-slate-100">{d.fullName}</p>
            <p className="text-xs text-slate-500 font-mono">{d.mssv}</p>
            <p className="text-xs text-slate-400 mt-1">
              {d.label ?? 'Không đặt tên'} ·{' '}
              {d.createdAt ? new Date(d.createdAt).toLocaleString('vi-VN') : ''}
            </p>

            {/* Cảnh báo rõ ràng: duyệt là thu hồi máy cũ. Cán bộ cần biết mình
                đang làm gì, vì đây chính là chốt chặn điểm danh hộ. */}
            <p className="text-xs text-amber-400/80 mt-2">
              Duyệt sẽ thu hồi thiết bị đang hoạt động của sinh viên này.
            </p>

            <div className="flex gap-2 mt-3">
              <button
                onClick={() => approve.mutate(d.id!)}
                disabled={approve.isPending}
                className="rounded-lg bg-emerald-700 hover:bg-emerald-600 disabled:opacity-40 px-3 py-1.5 text-sm font-medium"
              >
                Duyệt
              </button>
              <button
                onClick={() => revoke.mutate(d.id!)}
                disabled={revoke.isPending}
                className="rounded-lg border border-slate-700 hover:border-slate-600 disabled:opacity-40 px-3 py-1.5 text-sm"
              >
                Từ chối
              </button>
            </div>

            {approve.isError && (
              <p className="text-xs text-rose-400 mt-2">{messageOf(approve.error)}</p>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
