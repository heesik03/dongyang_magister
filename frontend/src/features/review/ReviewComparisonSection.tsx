import { useEffect, useState } from 'react'
import { reviewClient, type ComparedCandidate, type ReviewComparison } from './reviewClient'

export function ReviewComparisonSection({
  workId,
  jobId,
  onSelectCandidate,
  onClose,
}: {
  workId: string
  jobId: string
  onSelectCandidate?: (candidateId: string) => void
  onClose: () => void
}) {
  const [reloadKey, setReloadKey] = useState(0)
  const [state, setState] = useState<{
    key: string
    comparison: ReviewComparison | null
    error: string
  } | null>(null)
  const requestKey = `${workId}:${jobId}:${reloadKey}`

  useEffect(() => {
    const controller = new AbortController()
    void reviewClient
      .compare(workId, jobId, undefined, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setState({ key: requestKey, comparison: result, error: '' })
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setState({
            key: requestKey,
            comparison: null,
            error:
              failure instanceof Error
                ? failure.message
                : '수정 전·후 검토 비교를 불러오지 못했습니다.',
          })
        }
      })
    return () => controller.abort()
  }, [workId, jobId, requestKey])

  const current = state?.key === requestKey ? state : null
  const comparison = current?.comparison ?? null
  const loading = !current
  const error = current?.error ?? ''

  function renderCandidateCard(item: ComparedCandidate, allowJump: boolean) {
    return (
      <details className="event-list-item" key={`${item.category}:${item.candidateId}`}>
        <summary className="workspace-list-row">
          <span>
            {
              {
                RETAINED: '계속 나옴',
                ADDED: '새로 나옴',
                NOT_REAPPEARED: '이번에는 나오지 않음',
                INCOMPARABLE: '비교할 수 없음',
              }[item.category]
            }
          </span>
          <strong>{item.quote}</strong>
          <span>{item.comparisonNote || item.reason}</span>
        </summary>
        <div className="comparison-card">
          <div className="row">
            <span className={`badge ${item.type === '설정 충돌 후보' ? 'danger' : 'warning'}`}>
              {item.type}
            </span>
            {item.sourceTitles.length > 0 && (
              <span className="small muted">근거: {item.sourceTitles.join(', ')}</span>
            )}
          </div>
          <p className="small">{item.reason}</p>
          {item.suggestion && <p className="small muted">{item.suggestion}</p>}
          {allowJump && onSelectCandidate && (
            <button
              type="button"
              className="text-button"
              onClick={() => onSelectCandidate(item.candidateId)}
            >
              현재 후보 상세 열기 →
            </button>
          )}
        </div>
      </details>
    )
  }

  return (
    <section className="review-comparison-box" aria-label="수정 전·후 검토 비교">
      <div className="review-overview-header">
        <div>
          <strong>수정 전·후 검토 결과 비교</strong>
          <p className="small muted">
            같은 회차의 직전 완료 분석과 현재 완료 분석의 후보 변화를 비교합니다.
          </p>
        </div>
        <button type="button" className="ghost small" onClick={onClose}>
          접기
        </button>
      </div>

      {loading && <p role="status">수정 전·후 비교 결과를 불러오고 있습니다.</p>}

      {error && (
        <div role="alert" className="notice">
          <p className="error">{error}</p>
          <button type="button" onClick={() => setReloadKey((value) => value + 1)}>
            다시 시도
          </button>
        </div>
      )}

      {comparison && !loading && !error && (
        <>
          {!comparison.hasBaseJob ? (
            <p className="notice small">{comparison.scopeComparisonNote}</p>
          ) : (
            <>
              <div className="comparison-meta small">
                <span>
                  {!comparison.manuscriptModified && '같은 원고 내용의 재분석 · '}
                  이전 분석 원고 버전 {comparison.baseChapterRevision ?? '-'} → 현재 분석 원고 버전{' '}
                  {comparison.currentChapterRevision}
                </span>
                {comparison.scopeChanged && <span className="badge warning">참고 범위 변경됨</span>}
              </div>
              <p className="notice small">{comparison.scopeComparisonNote}</p>

              <p className="notice small">{comparison.resolutionCautionNote}</p>
              <div className="workspace-list comparison-list">
                <div className="workspace-list-row workspace-list-header" aria-hidden="true">
                  <span>변화</span>
                  <span>검토 항목</span>
                  <span>확인할 내용</span>
                </div>
                {[
                  ...comparison.retained,
                  ...comparison.added,
                  ...comparison.notReappeared,
                  ...comparison.incomparable,
                ].map((item) =>
                  renderCandidateCard(
                    item,
                    item.category === 'RETAINED' || item.category === 'ADDED',
                  ),
                )}
              </div>
            </>
          )}
        </>
      )}
    </section>
  )
}
