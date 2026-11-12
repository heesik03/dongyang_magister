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
    <Dialog size="wide" title="내가 남긴 판단" onClose={onClose}>
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
          <div className="workspace-list decision-ledger-list">
            {current.result.items.length > 0 && (
              <div className="workspace-list-row workspace-list-header" aria-hidden="true">
                <span>검토 항목</span>
                <span>내 판단</span>
                <span>적용 범위</span>
              </div>
            )}
            {current.result.items.map((decision) => (
              <details className="event-list-item" key={decision.id ?? decision.reviewId}>
                <summary className="workspace-list-row">
                  <strong>{decision.quote}</strong>
                  <span>{decision.status}</span>
                  <span>
                    {decision.scopes.length
                      ? decision.scopes
                          .map((scope) =>
                            scope.scopeType === 'SCENE'
                              ? '특정 장면'
                              : (scope.fromChapterTitle ?? '회차 범위'),
                          )
                          .join(', ')
                      : '범위 없음'}
                  </span>
                </summary>
                <div className="notice ledger-details">
                  <strong>
                    버전 {decision.version}
                    {decision.stale ? ' · 근거 변경, 재확인 필요' : ''}
                  </strong>
                  <p>{decision.reason || '사유 없음'}</p>
                  {decision.scopes.map((scope) => (
                    <p key={scope.id}>
                      {scope.scopeType === 'SCENE'
                        ? '특정 장면'
                        : (scope.fromChapterTitle ?? '시작 회차') +
                          ' ~ ' +
                          (scope.toChapterTitle ?? '이후 회차')}
                      {' · '}
                      {scope.conditionText}
                    </p>
                  ))}
                </div>
              </details>
            ))}
          </div>
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
