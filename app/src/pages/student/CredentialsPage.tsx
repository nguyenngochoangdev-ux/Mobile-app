import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { CredentialService } from '../../api/generated'
import type { CredentialResponse } from '../../api/generated'

/**
 * Credential của sinh viên, và nút tải bundle.
 *
 * Đây là chỗ luồng end-to-end đi ra khỏi hệ thống: mọi thứ trước nó (điểm danh, cấp, neo) đều
 * ở trong máy chủ của trường. Nút "Tải bundle" là đường duy nhất để sinh viên cầm được bằng
 * chứng đi — và bằng chứng đó xác minh được **mà không cần trường nữa**.
 */
export default function CredentialsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['credentials', 'me'],
    queryFn: () => CredentialService.myCredentials(),
  })

  if (isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (error) return <p className="text-rose-400">{(error as Error).message}</p>

  const items = data ?? []

  return (
    <div className="space-y-4">
      <div className="flex items-baseline gap-3">
        <h2 className="text-lg font-semibold">Chứng nhận của tôi</h2>
        <span className="text-sm text-slate-400">{items.length} chứng nhận</span>
      </div>

      {items.length === 0 && (
        <p className="text-slate-400 text-sm">
          Chưa có chứng nhận nào. Cán bộ cấp chứng nhận sau khi tổng kết hoạt động của học kỳ.
        </p>
      )}

      <ul className="space-y-3">
        {items.map((c: CredentialResponse) => (
          <TheCredential key={c.id} c={c} />
        ))}
      </ul>
    </div>
  )
}

function TheCredential({ c }: { c: CredentialResponse }) {
  const [dangTai, setDangTai] = useState(false)
  const [loi, setLoi] = useState<string | null>(null)

  async function taiBundle() {
    setDangTai(true)
    setLoi(null)
    try {
      const bundle = await CredentialService.credentialBundle(c.id!)

      // Tải xuống ngay trong trình duyệt, không qua máy chủ tệp nào. Dùng
      // `JSON.stringify(..., 2)` cho dễ đọc — bundle KHÔNG bị băm nguyên văn, chỉ phần
      // `credential.payload` bên trong mới quan trọng từng byte, và nó là chuỗi con nên
      // định dạng lại vỏ ngoài không đụng tới nó.
      const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${c.studentCode}-${c.semester}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setLoi((e as Error).message)
    } finally {
      setDangTai(false)
    }
  }

  return (
    <li className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3 space-y-3">
      <div className="flex items-start gap-3">
        <div className="min-w-0">
          <p className="font-medium text-slate-100">
            Hoạt động ngoại khóa · học kỳ {c.semester}
          </p>
          <p className="text-sm text-slate-400 mt-0.5">
            {c.activityCount} hoạt động · {c.totalPoints} điểm
          </p>
        </div>

        <div className="ml-auto shrink-0">
          {c.revoked ? (
            <span className="rounded-md bg-rose-950 text-rose-300 border border-rose-900 px-2 py-1 text-xs font-medium">
              Đã thu hồi
            </span>
          ) : (
            <span className="rounded-md bg-emerald-950 text-emerald-300 border border-emerald-900 px-2 py-1 text-xs font-medium">
              Còn hiệu lực
            </span>
          )}
        </div>
      </div>

      <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-xs text-slate-400">
        <dt>Cấp ngày</dt>
        <dd className="text-slate-300">
          {c.issuedAt ? new Date(c.issuedAt).toLocaleString('vi-VN') : '—'}
        </dd>
        <dt>Bên cấp</dt>
        <dd className="font-mono text-slate-300 break-all">{c.issuerAddress}</dd>
        <dt>Leaf hash</dt>
        <dd className="font-mono text-slate-300 break-all">{c.leafHash}</dd>
      </dl>

      <div className="flex flex-wrap items-center gap-2">
        <button
          onClick={taiBundle}
          disabled={dangTai}
          className="rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-50 px-3 py-2 text-sm font-medium"
        >
          {dangTai ? 'Đang tạo…' : 'Tải bundle xác minh'}
        </button>
        <p className="text-xs text-slate-500">
          Tệp này xác minh được <strong className="text-slate-400">mà không cần trường</strong>.
          Đưa cho nhà tuyển dụng cùng địa chỉ trang xác minh.
        </p>
      </div>

      {loi && (
        <p className="text-xs text-rose-400">
          {loi.includes('chưa được neo')
            ? 'Chứng nhận này chưa được neo lên blockchain nên chưa xuất bundle được. Job neo chạy 02:00 hằng đêm.'
            : loi}
        </p>
      )}
    </li>
  )
}
