import { useState, type ReactNode } from 'react'
import { WorksContext, isWorks } from './store'
import { createSampleWorks } from './sample'
import type { Work } from './types'

export function WorksProvider({ email, children }: { email: string; children: ReactNode }) {
  const storageKey = `magister.works.v1:${email}`
  const [initial] = useState(() => {
    try {
      const raw = localStorage.getItem(storageKey)
      if (!raw) return { works: createSampleWorks(), error: '' }
      const value: unknown = JSON.parse(raw)
      if (isWorks(value)) return { works: value, error: '' }
      return {
        works: [] as Work[],
        error:
          '저장된 데이터 형식을 읽을 수 없습니다. 기존 저장 내용은 유지하며 현재 변경은 메모리에만 보관합니다.',
      }
    } catch {
      return {
        works: [] as Work[],
        error: '브라우저 저장 데이터를 읽지 못했습니다. 현재 변경은 메모리에만 보관합니다.',
      }
    }
  })
  const [works, setWorks] = useState(initial.works)
  const [storageError, setStorageError] = useState(initial.error)
  function persist(nextWorks: Work[]) {
    setWorks(nextWorks)
    if (initial.error) return
    try {
      localStorage.setItem(storageKey, JSON.stringify(nextWorks))
      setStorageError('')
    } catch {
      setStorageError(
        '브라우저 저장에 실패했습니다. 입력은 현재 화면에 유지됩니다. 저장 공간을 확인하고 다시 시도해 주세요.',
      )
    }
  }
  function retrySave() {
    if (initial.error) return
    persist(works)
  }
  function updateWork(work: Work) {
    persist(
      works.map((item) =>
        item.id === work.id ? { ...work, updatedAt: new Date().toISOString() } : item,
      ),
    )
  }
  return (
    <WorksContext
      value={{
        works,
        storageError,
        retrySave,
        updateWork,
        addWork: (work) => persist([work, ...works]),
      }}
    >
      {children}
    </WorksContext>
  )
}
