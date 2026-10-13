import { createContext, useContext } from 'react'

export interface User { name: string; email: string }
export const AuthContext = createContext<{ user: User | null; login: (user: User) => void; logout: () => void } | null>(null)
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider가 필요합니다.')
  return context
}
export function readUser(): User | null {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem('magister.session') || 'null')
    if (value && typeof value === 'object' && 'name' in value && typeof value.name === 'string' && 'email' in value && typeof value.email === 'string') return { name: value.name, email: value.email }
  } catch { /* 저장소를 사용할 수 없으면 로그인 화면에서 다시 시작한다. */ }
  return null
}
