import { useState } from 'react'
import { useWorksStore } from '../works/useWorksStore'
import type { Work } from '../works/workTypes'
import { Dialog } from '../../shared/Dialog'
import { chapterClient, chapterSignature, type SavedChapter } from '../manuscript/chapterClient'
export function WorkspaceStatus({ work }: { work: Work }) {
  const dirty = useWorksStore((state) => state.dirtyIds.includes(work.id))
  const saving = useWorksStore((state) => state.savingIds.includes(work.id))
  const conflict = useWorksStore((state) => state.conflicts.includes(work.id))
  const error = useWorksStore((state) => state.workspaceErrors[work.id])
  const [notice, setNotice] = useState('')
  const [comparison, setComparison] = useState<SavedChapter[] | null>(null)
  const [comparing, setComparing] = useState(false)
  async function compareChapters() {
    setComparing(true)
    setNotice('')
    try {
      const server = await chapterClient.listAll(work.id)
      setComparison(
        server.filter((chapter) =>
          work.chapters.some(
            (draft) =>
              draft.id === chapter.id && chapterSignature(draft) !== chapterSignature(chapter),
          ),
        ),
      )
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '원고 비교 실패')
    } finally {
      setComparing(false)
    }
  }
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
        <button disabled={saving || comparing} onClick={() => void compareChapters()}>
          {comparing ? '서버 원고 조회 중…' : '충돌 원고 비교·복구'}
        </button>
      )}
      {conflict && (
        <button
          disabled={saving}
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
          disabled={saving}
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
      {comparison && (
        <Dialog title="충돌 원고 비교" onClose={() => setComparison(null)}>
          <div className="stack">
            <p>
              두 원고를 확인하고 선택한 회차만 다시 저장합니다. 비교 이후 다른 수정이 발생하면 다시
              충돌을 안내합니다.
            </p>
            {!comparison.length && (
              <p>
                본문·제목이 다른 회차가 없습니다. 검토 판단·사건의 충돌은 입력을 보관한 뒤 서버본을
                불러와 확인해 주세요.
              </p>
            )}
            {comparison.map((server) => {
              const draft = work.chapters.find((chapter) => chapter.id === server.id)
              return (
                draft && (
                  <section className="stack" key={server.id}>
                    <h3>{draft.title}</h3>
                    <label>
                      현재 입력 · {draft.title}
                      <textarea readOnly rows={5} value={draft.content} />
                    </label>
                    <label>
                      서버 원고 · {server.title}
                      <textarea readOnly rows={5} value={server.content} />
                    </label>
                    <button
                      disabled={saving}
                      onClick={() => {
                        try {
                          useWorksStore.getState().resolveChapterConflict(work.id, server)
                          void useWorksStore.getState().flushWorkspace(work.id)
                          setComparison(null)
                        } catch (error) {
                          setNotice(error instanceof Error ? error.message : '복구 실패')
                        }
                      }}
                    >
                      이 회차를 내 입력으로 저장
                    </button>
                  </section>
                )
              )
            })}
          </div>
        </Dialog>
      )}
    </section>
  )
}
