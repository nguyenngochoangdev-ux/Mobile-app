import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { OpenAPI } from '../api/generated'
import type { MeResponse, TokenResponse } from '../api/generated'

type AuthState = {
  accessToken: string | null
  refreshToken: string | null
  me: MeResponse | null
  setTokens: (t: TokenResponse) => void
  setMe: (m: MeResponse) => void
  logout: () => void
}

export const useAuth = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      me: null,
      setTokens: (t) => set({ accessToken: t.accessToken, refreshToken: t.refreshToken }),
      setMe: (m) => set({ me: m }),
      logout: () => set({ accessToken: null, refreshToken: null, me: null }),
    }),
    { name: 'drl.auth' },
  ),
)

// Client sinh tự động đọc token qua resolver này, nên mọi lời gọi luôn lấy token
// mới nhất trong store mà không phải truyền tay.
OpenAPI.TOKEN = async () => useAuth.getState().accessToken ?? ''
OpenAPI.BASE = ''   // cùng origin: dev qua proxy Vite, prod sau reverse proxy

export function authHeader(): Record<string, string> {
  const t = useAuth.getState().accessToken
  return t ? { Authorization: `Bearer ${t}` } : {}
}

export function roleOf(): 'STUDENT' | 'STAFF' | 'ADMIN' | null {
  const r = useAuth.getState().me?.role
  return (r as 'STUDENT' | 'STAFF' | 'ADMIN') ?? null
}
