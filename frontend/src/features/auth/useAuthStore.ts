import { create } from 'zustand'
import { authClient } from './authClient'
import { ApiError } from '../../shared/httpClient'
import type { User } from './authTypes'

interface AuthState {
  user: User | null
  loading: boolean
  error: string
  initialize: (signal?: AbortSignal) => Promise<void>
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  retry: () => void
  expire: () => void
}
// 로그아웃·세션 만료 이후 도착한 인증 조회로 사용자를 복원하지 않는다.
let revision = 0
export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  loading: true,
  error: '',
  async initialize(signal) {
    const current = ++revision
    set({ loading: true, error: '' })
    try {
      const user = await authClient.me(signal)
      if (!signal?.aborted && current === revision) set({ user, loading: false })
    } catch (error) {
      if (signal?.aborted || current !== revision) return
      if (error instanceof ApiError && error.status === 401) set({ user: null, loading: false })
      else
        set({
          loading: false,
          error: '로그인 상태를 확인하지 못했습니다. 서버 연결을 확인하고 다시 시도해 주세요.',
        })
    }
  },
  async login(email, password) {
    ++revision
    const user = await authClient.login(email, password)
    set({ user, loading: false, error: '' })
  },
  async logout() {
    try {
      await authClient.logout()
      get().expire()
    } catch {
      set({ error: '로그아웃하지 못했습니다. 다시 시도해 주세요.' })
    }
  },
  retry() {
    void get().initialize()
  },
  expire() {
    ++revision
    set({ user: null, loading: false, error: '' })
  },
}))
export function getReturnPath(state: unknown) {
  return state &&
    typeof state === 'object' &&
    'from' in state &&
    typeof state.from === 'string' &&
    state.from.startsWith('/') &&
    !state.from.startsWith('//')
    ? state.from
    : '/'
}
