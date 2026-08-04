import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthService } from '../api/generated'
import { useAuth } from '../lib/auth'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const setTokens = useAuth((s) => s.setTokens)
  const setMe = useAuth((s) => s.setMe)
  const navigate = useNavigate()

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const tokens = await AuthService.login({ username, password })
      setTokens(tokens)
      // Lấy hồ sơ ngay: điều hướng phụ thuộc role, không đoán từ username.
      const me = await AuthService.me()
      setMe(me)
      navigate(me.role === 'STUDENT' ? '/sv' : '/cb/su-kien', { replace: true })
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-full grid place-items-center px-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-100">Sổ tay hoạt động sinh viên</h1>
          <p className="text-sm text-slate-400 mt-1">Đăng nhập để tiếp tục</p>
        </div>

        <input
          className="w-full rounded-lg bg-slate-800 border border-slate-700 px-3 py-2.5 outline-none focus:border-sky-500"
          placeholder="Tài khoản (MSSV hoặc tên đăng nhập)"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <input
          className="w-full rounded-lg bg-slate-800 border border-slate-700 px-3 py-2.5 outline-none focus:border-sky-500"
          placeholder="Mật khẩu"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {error && (
          <p className="text-sm text-rose-400 bg-rose-950/40 border border-rose-900 rounded-lg px-3 py-2">
            {error}
          </p>
        )}

        <button
          disabled={busy || !username || !password}
          className="w-full rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 disabled:hover:bg-sky-600 px-3 py-2.5 font-medium transition"
        >
          {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </button>
      </form>
    </div>
  )
}

export function messageOf(err: unknown): string {
  const body = (err as { body?: { message?: string } })?.body
  if (body?.message) return body.message
  if (err instanceof Error) return err.message
  return 'Có lỗi xảy ra'
}
