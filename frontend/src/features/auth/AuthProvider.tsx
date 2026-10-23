import { useEffect, type ReactNode } from 'react'
import { useAuth } from './auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    const controller = new AbortController()
    const expire = () => useAuth.getState().expire()
    window.addEventListener('auth-expired', expire)
    void useAuth.getState().initialize(controller.signal)
    return () => {
      controller.abort()
      window.removeEventListener('auth-expired', expire)
    }
  }, [])
  return children
}
