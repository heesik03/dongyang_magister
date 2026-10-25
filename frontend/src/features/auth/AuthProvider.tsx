import { useEffect, type ReactNode } from 'react'
import { SESSION_EXPIRED_EVENT } from '../../shared/httpClient'
import { useAuthStore } from './useAuthStore'

export function AuthProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    const controller = new AbortController()
    const expire = () => useAuthStore.getState().expire()
    window.addEventListener(SESSION_EXPIRED_EVENT, expire)
    void useAuthStore.getState().initialize(controller.signal)
    return () => {
      controller.abort()
      window.removeEventListener(SESSION_EXPIRED_EVENT, expire)
    }
  }, [])
  return children
}
