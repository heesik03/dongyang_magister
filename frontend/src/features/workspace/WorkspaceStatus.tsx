import { useState } from 'react'
import { useWorksStore } from '../works/useWorksStore'
import type { Work } from '../works/workTypes'
export function WorkspaceStatus({ work }: { work: Work }) {
  const dirty = useWorksStore((state) => state.dirtyIds.includes(work.id))
  const saving = useWorksStore((state) => state.savingIds.includes(work.id))
  const conflict = useWorksStore((state) => state.conflicts.includes(work.id))
  const error = useWorksStore((state) => state.workspaceErrors[work.id])
  const [notice, setNotice] = useState('')
  function download() {
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(work, null, 2)], { type: 'application/json' }),
    )
    const link = document.createElement('a')
    link.href = url
    link.download = 'magister-' + work.id + '-recovery.json'
    link.click()
    URL.revokeObjectURL(url)
  }
  return (
    <section aria-label="원고 저장 상태" className="notice">
      <span role="status">
        {saving
          ? '서버 저장 중…'
          : dirty
            ? '저장 대기 · 현재 입력은 화면에 보존됩니다.'
            : '서버 저장 완료'}
      </span>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {dirty && (
        <button
          disabled={saving || conflict}
          onClick={() => void useWorksStore.getState().flushWorkspace(work.id)}
        >
          저장 재시도
        </button>
      )}
      <button onClick={download}>현재 입력 내려받기</button>
      {conflict && (
        <button
          onClick={() => {
            download()
            void useWorksStore
              .getState()
              .reloadWorkspace(work.id)
              .catch((error) => setNotice(error instanceof Error ? error.message : '조회 실패'))
          }}
        >
          입력을 파일로 보관하고 서버본 불러오기
        </button>
      )}
      {
        <button
          onClick={() => {
            useWorksStore.getState().recoverDraft(work.id)
            setNotice(
              '보관된 초안이 있으면 화면에 불러옵니다. 자동으로 기존 서버 문서를 덮어쓰지 않습니다.',
            )
          }}
        >
          이전 초안·탭 복구본 불러오기
        </button>
      }
    </section>
  )
}
