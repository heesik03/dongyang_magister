import { create } from 'zustand'
import { ApiError, authApi } from './api'

export interface User {
  id: number
  name: string
  email: string
}
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
let revision = 0
export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  loading: true,
  error: '',
  async initialize(signal) {
    const current = ++revision
    set({ loading: true, error: '' })
    try {
      const user = await authApi.me(signal)
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
    const user = await authApi.login(email, password)
    set({ user, loading: false, error: '' })
  },
  async logout() {
    try {
      await authApi.logout()
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
