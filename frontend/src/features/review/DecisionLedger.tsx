import { useEffect, useState } from 'react'
import { Dialog } from '../../shared/Dialog'
import { decisionClient, type SavedDecision } from './decisionClient'
import type { Page } from '../../shared/responseReader'

export function DecisionLedger({ workId, onClose }: { workId: string; onClose: () => void }) {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [refresh, setRefresh] = useState(0)
  const key = `${workId}:${page}:${status}:${refresh}`
  const [state, setState] = useState<{
    key: string
    result?: Page<SavedDecision>
    error: string
  } | null>(null)
  const current = state?.key === key ? state : null
  useEffect(() => {
    const controller = new AbortController()
    void decisionClient
      .list(workId, page, status, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setState({ key, result, error: '' })
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setState({ key, error: error instanceof Error ? error.message : '작품 판단 조회 실패' })
      })
    return () => controller.abort()
  }, [workId, page, status, key])
  return (
    <Dialog title="작품 판단 기록" onClose={onClose}>
      <label>
        판단 필터
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value)
            setPage(0)
          }}
        >
          <option value="">전체</option>
          {['수용', '보류', '의도된 설정', '미검토'].map((value) => (
            <option key={value}>{value}</option>
          ))}
        </select>
      </label>
      {!current && <p role="status">판단을 불러오고 있습니다.</p>}
      {current?.error && (
        <div role="alert">
          <p>{current.error}</p>
          <button onClick={() => setRefresh((value) => value + 1)}>판단 다시 조회</button>
        </div>
      )}
      {current?.result && (
        <>
          {!current.result.items.length && <p>이 상태로 저장한 판단이 없습니다.</p>}
          {current.result.items.map((decision) => (
            <article className="notice" key={decision.id} style={{ overflowWrap: 'anywhere' }}>
              <strong>
                {decision.status} · 버전 {decision.version}
                {decision.stale ? ' · 근거 변경, 재확인 필요' : ''}
              </strong>
              <blockquote>{decision.quote}</blockquote>
              <p>{decision.reason || '사유 없음'}</p>
              {decision.scopes.map((scope) => (
                <p key={scope.id}>
                  {scope.scopeType === 'SCENE'
                    ? '특정 장면'
                    : `${scope.fromChapterTitle ?? '시작 회차'} ~ ${scope.toChapterTitle ?? '이후 회차'}`}{' '}
                  · {scope.conditionText}
                </p>
              ))}
            </article>
          ))}
          <div className="dialog-actions">
            <button disabled={page === 0} onClick={() => setPage(page - 1)}>
              이전 판단
            </button>
            <span>
              {page + 1} / {Math.max(1, current.result.totalPages)}
            </span>
            <button
              disabled={page + 1 >= current.result.totalPages}
              onClick={() => setPage(page + 1)}
            >
              다음 판단
            </button>
          </div>
        </>
      )}
    </Dialog>
  )
}
