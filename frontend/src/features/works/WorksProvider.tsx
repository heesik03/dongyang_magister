import { useEffect, type ReactNode } from 'react'
import { useWorksStore } from './useWorksStore'

export function WorksProvider({ email, children }: { email: string; children: ReactNode }) {
  const account = useWorksStore((state) => state.account)
  // 계정별 브라우저 원고를 분리하고, 계정 전환 시 이전 계정의 요청·캐시를 비운다.
  useEffect(() => {
    useWorksStore.getState().initialize(email)
    return () => useWorksStore.getState().reset()
  }, [email])
  if (account !== email) return <p role="status">작품을 준비하고 있습니다.</p>
  return children
}
