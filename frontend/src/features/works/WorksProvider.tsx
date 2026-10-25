import { useEffect, type ReactNode } from 'react'
import { useWorks } from './store'

export function WorksProvider({ email, children }: { email: string; children: ReactNode }) {
  const account = useWorks((state) => state.account)
  useEffect(() => {
    useWorks.getState().initialize(email)
    return () => useWorks.getState().reset()
  }, [email])
  if (account !== email) return <p role="status">작품을 준비하고 있습니다.</p>
  return children
}
