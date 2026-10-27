import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Dialog } from '../../shared/Dialog'
import { ApiError } from '../../shared/httpClient'
import { workClient } from './workClient'
import { useWorksStore } from './useWorksStore'
import type { Work } from './workTypes'

export function DeleteWorkDialog({ work, onClose }: { work: Work; onClose: () => void }) {
  const navigate = useNavigate()
  const [latest, setLatest] = useState<Work | null>(null)
  const [title, setTitle] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    void workClient
      .get(work.id, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setLatest(value)
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setError(failure instanceof Error ? failure.message : '작품 조회 실패')
      })
    return () => controller.abort()
  }, [work.id, reload])
  async function remove(event: FormEvent) {
    event.preventDefault()
    if (busy || !latest || title !== latest.title) return
    setBusy(true)
    setError('')
    try {
      await useWorksStore.getState().deleteWork(work.id, latest.version ?? 0)
      navigate('/', { replace: true })
      onClose()
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        setLatest(null)
        setError(
          '다른 변경이 저장되어 삭제를 중단했습니다. 최신 작품을 확인한 뒤 다시 삭제해 주세요. 작성 중인 내용은 유지됩니다.',
        )
      } else setError(failure instanceof Error ? failure.message : '작품 삭제 실패')
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog
      title="작품 영구 삭제"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <form className="stack" onSubmit={remove}>
        <p>
          작품의 원고·설정·분석 원본·검토·판단 이력·타임라인과 이 탭의 복구 자료를 삭제합니다.
          되돌릴 수 없습니다.
        </p>
        <p>
          이미 외부 AI에 전송한 자료와 다른 브라우저에 보관한 복사본의 삭제는 보장하지 않습니다.
        </p>
        {latest && (
          <p>
            삭제할 최신 작품: <strong>{latest.title}</strong> · 버전 {latest.version ?? 0}
          </p>
        )}
        <label>
          삭제할 작품 제목을 입력해 주세요
          <input
            autoFocus
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            disabled={busy}
            autoComplete="off"
          />
        </label>
        {error && <p role="alert">{error}</p>}
        {!latest && (
          <button type="button" disabled={busy} onClick={() => setReload((value) => value + 1)}>
            최신 작품 확인
          </button>
        )}
        <div className="dialog-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            취소
          </button>
          <button disabled={busy || !latest || title !== latest.title}>
            {busy ? '삭제 중…' : '작품 영구 삭제'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
