import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'

export default function Layout() {
  const me = useAuth((s) => s.me)
  const logout = useAuth((s) => s.logout)
  const navigate = useNavigate()

  const links =
    me?.role === 'STUDENT'
      ? [
          { to: '/sv', label: 'Sổ tay' },
          { to: '/sv/quet', label: 'Quét QR' },
          // Luồng đảo chiều (PROJECT.md §2.4 phương án 3) — phải ở thanh điều hướng chính,
          // không giấu trong menu con: sinh viên cần tới nó đúng lúc mất sóng hoặc camera
          // hỏng, tức là lúc không đủ kiên nhẫn đi tìm.
          { to: '/sv/ma-cua-toi', label: 'Mã của tôi' },
          { to: '/sv/diem', label: 'Điểm RL' },
          { to: '/sv/chung-nhan', label: 'Chứng nhận' },
          { to: '/sv/thiet-bi', label: 'Thiết bị' },
        ]
      : [
          { to: '/cb/su-kien', label: 'Sự kiện' },
          { to: '/cb/cham-diem', label: 'Chấm điểm' },
          { to: '/cb/chung-nhan', label: 'Cấp CN' },
          { to: '/cb/thiet-bi', label: 'Duyệt thiết bị' },
        ]

  return (
    <div className="min-h-full flex flex-col">
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-10">
        <div className="mx-auto max-w-3xl px-4 py-3 flex items-center gap-3">
          <span className="font-semibold text-slate-100">Sổ tay hoạt động</span>
          <span className="ml-auto text-sm text-slate-400 truncate max-w-[45%]">
            {me?.fullName ?? me?.username}
            {me?.mssv ? ` · ${me.mssv}` : ''}
          </span>
          <button
            onClick={() => {
              logout()
              navigate('/login', { replace: true })
            }}
            className="text-sm text-slate-400 hover:text-slate-200 shrink-0"
          >
            Thoát
          </button>
        </div>
      </header>

      <nav className="border-b border-slate-800 bg-slate-900/40">
        <div className="mx-auto max-w-3xl px-4 flex gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.to === '/sv'}
              className={({ isActive }) =>
                `px-3 py-2.5 text-sm border-b-2 -mb-px transition ${
                  isActive
                    ? 'border-sky-400 text-sky-300'
                    : 'border-transparent text-slate-400 hover:text-slate-200'
                }`
              }
            >
              {l.label}
            </NavLink>
          ))}
        </div>
      </nav>

      <main className="mx-auto w-full max-w-3xl px-4 py-5 flex-1">
        <Outlet />
      </main>
    </div>
  )
}
