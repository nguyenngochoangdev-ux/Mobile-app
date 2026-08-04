import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './lib/auth'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import StudentHome from './pages/student/StudentHome'
import ScanPage from './pages/student/ScanPage'
import DevicesPage from './pages/student/DevicesPage'
import StaffEvents from './pages/staff/StaffEvents'
import StaffEventDetail from './pages/staff/StaffEventDetail'
import StaffDeviceApproval from './pages/staff/StaffDeviceApproval'
import PresenterPage from './pages/presenter/PresenterPage'

function RequireRole({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const me = useAuth((s) => s.me)
  const token = useAuth((s) => s.accessToken)

  if (!token) return <Navigate to="/login" replace />
  if (me && !roles.includes(me.role ?? '')) return <Navigate to="/" replace />
  return <>{children}</>
}

function HomeRedirect() {
  const me = useAuth((s) => s.me)
  const token = useAuth((s) => s.accessToken)

  if (!token) return <Navigate to="/login" replace />
  if (me?.role === 'STUDENT') return <Navigate to="/sv" replace />
  return <Navigate to="/cb/su-kien" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Màn hình trình chiếu tách riêng, không dùng Layout: nó chạy toàn màn hình
          trên máy chiếu và không cần thanh điều hướng. */}
      <Route
        path="/trinh-chieu/:eventId"
        element={
          <RequireRole roles={['STAFF', 'ADMIN']}>
            <PresenterPage />
          </RequireRole>
        }
      />

      <Route element={<Layout />}>
        <Route path="/" element={<HomeRedirect />} />

        <Route path="/sv" element={<RequireRole roles={['STUDENT']}><StudentHome /></RequireRole>} />
        <Route path="/sv/quet" element={<RequireRole roles={['STUDENT']}><ScanPage /></RequireRole>} />
        <Route path="/sv/thiet-bi" element={<RequireRole roles={['STUDENT']}><DevicesPage /></RequireRole>} />

        <Route path="/cb/su-kien" element={<RequireRole roles={['STAFF', 'ADMIN']}><StaffEvents /></RequireRole>} />
        <Route path="/cb/su-kien/:eventId" element={<RequireRole roles={['STAFF', 'ADMIN']}><StaffEventDetail /></RequireRole>} />
        <Route path="/cb/thiet-bi" element={<RequireRole roles={['STAFF', 'ADMIN']}><StaffDeviceApproval /></RequireRole>} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
