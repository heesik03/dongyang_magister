import { useEffect, type ReactNode } from 'react'
import { isRecord } from '../../shared/responseReader'
import { useWorksStore } from './useWorksStore'
export function WorksProvider({ email, children }: { email: string; children: ReactNode }) {
  const account = useWorksStore((state) => state.account)
  useEffect(() => {
    useWorksStore.getState().initialize(email)
    void useWorksStore.getState().loadShortcuts()
    function preserve() {
      const state = useWorksStore.getState()
      for (const id of state.dirtyIds) {
        const work = state.works.find((item) => item.id === id)
        try {
          sessionStorage.setItem('magister.recovery:' + email + ':' + id, JSON.stringify(work))
        } catch {
          useWorksStore.setState({
            storageError: '복구 정보 저장에 실패했습니다. 입력을 파일로 보관해 주세요.',
          })
        }
        void state.flushWorkspace(id)
      }
      state.retrySave()
    }
    function leave(event: BeforeUnloadEvent) {
      if (useWorksStore.getState().dirtyIds.length) {
        preserve()
        event.preventDefault()
      }
    }
    function remote(event: StorageEvent) {
      if (event.key !== 'magister.workspace-saved:' + email || !event.newValue) return
      try {
        const value: unknown = JSON.parse(event.newValue)
        if (isRecord(value) && typeof value.id === 'string' && typeof value.version === 'number')
          useWorksStore.getState().handleRemoteSave(value.id, value.version)
      } catch {
        return
      }
    }
    window.addEventListener('pagehide', preserve)
    window.addEventListener('beforeunload', leave)
    window.addEventListener('storage', remote)
    return () => {
      preserve()
      window.removeEventListener('pagehide', preserve)
      window.removeEventListener('beforeunload', leave)
      window.removeEventListener('storage', remote)
      useWorksStore.getState().reset()
    }
  }, [email])
  if (account !== email) return <p role="status">작품을 준비하고 있습니다.</p>
  return children
}
