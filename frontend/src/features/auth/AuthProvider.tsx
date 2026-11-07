import { useEffect, type ReactNode } from 'react'
import { clearCsrfToken, SESSION_EXPIRED_EVENT } from '../../shared/httpClient'
import {
  ACCOUNT_WITHDRAWN_STORAGE_PREFIX,
  purgeAccountBrowserStorage,
} from '../account/accountClient'
import { useAuthStore } from './useAuthStore'

export function AuthProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    const controller = new AbortController()
    const expire = () => useAuthStore.getState().expire()
    function accountWithdrawn(event: StorageEvent) {
      if (!event.newValue || !event.key?.startsWith(ACCOUNT_WITHDRAWN_STORAGE_PREFIX)) return
      const email = event.key.slice(ACCOUNT_WITHDRAWN_STORAGE_PREFIX.length)
      if (!email) return
      purgeAccountBrowserStorage(email)
      if (useAuthStore.getState().user?.email === email) {
        controller.abort()
        clearCsrfToken()
        expire()
      }
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, expire)
    window.addEventListener('storage', accountWithdrawn)
    void useAuthStore.getState().initialize(controller.signal)
    return () => {
      controller.abort()
      window.removeEventListener(SESSION_EXPIRED_EVENT, expire)
      window.removeEventListener('storage', accountWithdrawn)
    }
  }, [])
  return children
}
