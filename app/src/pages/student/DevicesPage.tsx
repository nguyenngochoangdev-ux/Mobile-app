import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DevicesService } from '../../api/generated'
import { getDeviceFp } from '../../lib/device'
import { messageOf } from '../LoginPage'

export default function DevicesPage() {
  const queryClient = useQueryClient()
  const myFp = getDeviceFp()

  const { data, isLoading, error } = useQuery({
    queryKey: ['devices', 'me'],
    queryFn: () => DevicesService.myDevices(),
  })

  const register = useMutation({
    mutationFn: () =>
      DevicesService.registerDevice({ deviceFp: myFp, label: guessLabel() }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['devices', 'me'] }),
  })

  if (isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (error) return <p className="text-rose-400">{messageOf(error)}</p>

  const devices = data ?? []
  const thisDevice = devices.find((d) => d.deviceFp === myFp)

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Thiết bị của tôi</h2>

      <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3 text-sm">
        <p className="text-slate-300">Thiết bị đang dùng</p>
        <p className="font-mono text-xs text-slate-500 mt-1 break-all">{myFp}</p>
        {thisDevice ? (
          <p className="mt-2">
            Trạng thái: <StatusText status={thisDevice.status} />
          </p>
        ) : (
          <button
            onClick={() => register.mutate()}
            disabled={register.isPending}
            className="mt-3 rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 px-3 py-2 text-sm font-medium"
          >
            {register.isPending ? 'Đang gửi…' : 'Đăng ký thiết bị này'}
          </button>
        )}
        {register.isError && (
          <p className="mt-2 text-rose-400">{messageOf(register.error)}</p>
        )}
      </div>

      {devices.length > 1 && (
        <>
          <h3 className="text-sm font-medium text-slate-300 pt-2">Tất cả thiết bị</h3>
          <ul className="space-y-2">
            {devices.map((d) => (
              <li
                key={d.id}
                className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3 text-sm flex items-center gap-3"
              >
                <div className="min-w-0">
                  <p className="text-slate-200">{d.label ?? 'Không đặt tên'}</p>
                  <p className="font-mono text-xs text-slate-500 truncate">{d.deviceFp}</p>
                </div>
                <span className="ml-auto shrink-0">
                  <StatusText status={d.status} />
                </span>
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="text-xs text-slate-500">
        Mỗi tài khoản chỉ có một thiết bị hoạt động. Đăng ký thiết bị mới phải chờ cán bộ
        duyệt, và thiết bị cũ sẽ bị thu hồi.
      </p>
    </div>
  )
}

function StatusText({ status }: { status?: string }) {
  const map: Record<string, [string, string]> = {
    ACTIVE: ['đang hoạt động', 'text-emerald-400'],
    PENDING: ['chờ cán bộ duyệt', 'text-amber-400'],
    REVOKED: ['đã thu hồi', 'text-slate-500'],
  }
  const [label, cls] = map[status ?? ''] ?? ['không rõ', 'text-slate-500']
  return <span className={cls}>{label}</span>
}

function guessLabel(): string {
  const ua = navigator.userAgent
  if (/iPhone|iPad/i.test(ua)) return 'iPhone/iPad'
  if (/Android/i.test(ua)) return 'Android'
  if (/Windows/i.test(ua)) return 'Windows'
  if (/Mac/i.test(ua)) return 'Mac'
  return 'Thiết bị khác'
}
