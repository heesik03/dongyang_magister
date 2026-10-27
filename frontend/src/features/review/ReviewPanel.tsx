import { useState } from 'react'
import type { Analysis, Decision, Work } from '../works/workTypes'
import { DecisionEditor } from './DecisionEditor'
import { Icon } from '../../shared/Icon'
import type { ReviewReport, SourceLocation } from './reviewClient'
import type { InputSnapshot } from '../analysis/analysisClient'

export function ReviewPanel({
  work,
  analysis,
  stale,
  analyzing,
  error,
  retry,
  onJump,
  report,
  reportLoading,
  reportError,
  retryReport,
  input,
  onDecisionSaved,
}: {
  work: Work
  analysis?: Analysis
  stale: boolean
  analyzing: boolean
  error: string
  retry: () => void
  onJump: (location: SourceLocation) => void
  report?: ReviewReport
  reportLoading?: boolean
  reportError?: string
  retryReport?: () => void
  input?: InputSnapshot
  onDecisionSaved?: () => void
}) {
  const [selectedId, setSelectedId] = useState('')
  const [filter, setFilter] = useState('전체')
  const [editing, setEditing] = useState<{ id: string; status: Decision } | null>(null)
  const filtered =
    analysis?.reviews
      .map((review) => {
        const saved = report?.decisions?.[review.id]
        return saved ? { ...review, decision: saved.status, note: saved.reason } : review
      })
      .filter(
        (review) =>
          filter === '전체' ||
          (filter === '미검토' ? review.decision === '미검토' : review.decision !== '미검토'),
      ) || []
  const selected = filtered.find((review) => review.id === selectedId) || filtered[0]
  const linked = report?.reviews.find((review) => review.id === selected?.id)
  const editingReview = report?.reviews.find((review) => review.id === editing?.id)
  return (
    <aside className="review-panel" aria-label="검토 결과">
      <div className="section-line">
        <h2>
          <Icon name="spark" />
          검토 노트
        </h2>
        <span className="badge info">AI 검토</span>
      </div>
      <p className="small muted">근거를 읽고, 이야기의 의도를 알려 주세요.</p>
      {report && !report.legacy && <button onClick={onDecisionSaved}>판단 상태 새로 확인</button>}
      {reportLoading && <p role="status">검토 근거를 불러오고 있습니다.</p>}
      {reportError && (
        <div role="alert">
          <p>{reportError}</p>
          <button onClick={retryReport}>근거 다시 조회</button>
        </div>
      )}
      {analysis && (!report || report.legacy) && !reportLoading && !reportError && (
        <p className="notice">
          이전 형식의 결과로 정확한 위치를 확인할 수 없습니다. 다시 분석하면 위치가 연결됩니다.
        </p>
      )}
      <div role="status" aria-live="polite">
        {analyzing && (
          <div className="notice analyzing">
            <Icon name="spark" />
            <strong>선택한 원고와 설정을 비교하고 있어요.</strong>
            <p className="small">서버 작업 상태를 확인하고 있습니다.</p>
          </div>
        )}
      </div>
      {error && (
        <div role="alert" className="notice">
          <p className="error">{error}</p>
          <button onClick={retry}>다시 분석</button>
        </div>
      )}
      {!analysis && !analyzing && !error && (
        <div className="empty">
          <Icon name="spark" />
          <h3>이야기를 함께 살펴볼까요?</h3>
          <p>
            확정 설정과 참고 범위를 고른 뒤<br />
            AI 검토를 실행해 주세요.
          </p>
        </div>
      )}
      {analysis && !analyzing && (
        <>
          <div className="analysis-scope">
            <strong>검토 범위</strong>
            <p>
              {input?.chapter.title ??
                work.chapters.find((chapter) => chapter.id === analysis.chapterId)?.title}{' '}
              · 원고 버전 {analysis.revision}
              <br />
              확정 설정 {analysis.settingIds.length}개 · 이전 원고 {analysis.previousIds.length}개
            </p>
            <p>
              참고 설정:{' '}
              {analysis.settingIds
                .map(
                  (id) =>
                    (input?.settings ?? work.settings).find((setting) => setting.id === id)
                      ?.title || '삭제된 설정',
                )
                .join(', ') || '없음'}
            </p>
            <p>
              이전 원고:{' '}
              {analysis.previousIds
                .map(
                  (id) =>
                    (input?.previous ?? work.chapters).find((chapter) => chapter.id === id)?.title,
                )
                .join(', ') || '선택하지 않음'}
            </p>
            <span>선택한 이전 원고와 확정 설정을 함께 참고한 결과입니다.</span>
          </div>
          {stale && (
            <p className="notice stale" role="status">
              원고·이전 회차 또는 참고 설정이 바뀌었습니다. 이전 결과입니다. 근거를 선택하면 분석
              당시 자료를 확인할 수 있습니다.
            </p>
          )}
          <div className="filter-tabs review-filters">
            {['전체', '미검토', '처리됨'].map((item) => (
              <button
                key={item}
                onClick={() => setFilter(item)}
                aria-pressed={filter === item}
                className={filter === item ? 'selected' : ''}
              >
                {item}
              </button>
            ))}
          </div>
          {filtered.length === 0 ? (
            <div className="empty">
              <Icon name="check" />
              <h3>
                {analysis.reviews.length
                  ? '이 상태의 항목이 없습니다'
                  : '확인된 검토 후보가 없어요'}
              </h3>
              <p>
                {analysis.reviews.length
                  ? '다른 필터에서 검토 항목을 확인하세요.'
                  : '이 결과는 고증·개연성 검토의 통과 판정이 아닙니다. 선택한 범위에 근거가 부족하면 AI가 후보를 제시하지 않을 수 있습니다.'}
              </p>
            </div>
          ) : (
            <>
              <div className="review-list">
                {filtered.map((review) => (
                  <button
                    className={`review-summary ${selected?.id === review.id ? 'selected' : ''}`}
                    key={review.id}
                    onClick={() => {
                      setSelectedId(review.id)
                      const candidate = report?.reviews.find((item) => item.id === review.id)
                      if (candidate) onJump({ ...candidate, sourceId: candidate.targetSourceId })
                    }}
                  >
                    <div className="row">
                      <span
                        className={`badge ${review.type === '설정 충돌 후보' ? 'danger' : 'warning'}`}
                      >
                        {review.type}
                      </span>
                      <span className="badge">{review.decision}</span>
                    </div>
                    <p>{review.quote}</p>
                  </button>
                ))}
              </div>
              {selected && (
                <article className="review-detail">
                  <span className="small muted">
                    {selected.type === '설정 충돌 후보'
                      ? '확인 필요 · 능력 설정'
                      : '판단 유보 · 행동의 동기'}
                  </span>
                  <h3>원문과 설정 사이의 연결</h3>
                  <span className="detail-label">
                    검토 원문 ·{' '}
                    {input?.chapter.title ??
                      work.chapters.find((chapter) => chapter.id === analysis.chapterId)?.title}
                  </span>
                  <blockquote>{selected.quote}</blockquote>
                  <button
                    className="text-button"
                    disabled={!linked}
                    onClick={() => {
                      if (linked) onJump({ ...linked, sourceId: linked.targetSourceId })
                    }}
                  >
                    <Icon name="arrow" />
                    원문으로 이동
                  </button>
                  {linked ? (
                    linked.evidences.map((evidence) => (
                      <section
                        key={`${evidence.sourceId}:${evidence.startOffset}:${evidence.endOffset}:${evidence.explanation}`}
                      >
                        <span className="detail-label">
                          {evidence.sourceType === 'SETTING' ? '확정 설정' : '이전 회차'} ·{' '}
                          {evidence.title} · 버전 {evidence.revision}
                        </span>
                        <blockquote className="evidence" style={{ overflowWrap: 'anywhere' }}>
                          {evidence.quote}
                        </blockquote>
                        <p>{evidence.explanation}</p>
                        <button className="text-button" onClick={() => onJump(evidence)}>
                          근거 원문으로 이동 · {evidence.title}
                        </button>
                      </section>
                    ))
                  ) : (
                    <>
                      <span className="detail-label">
                        확정 설정 ·{' '}
                        {work.settings.find((setting) => setting.id === selected.settingId)?.title}
                      </span>
                      <blockquote className="evidence">{selected.evidence}</blockquote>
                    </>
                  )}
                  <span className="detail-label">확인 질문과 판단 이유</span>
                  <p>{selected.reason}</p>
                  <span className="detail-label">수정 방향</span>
                  <p>{selected.suggestion}</p>
                  {selected.note && (
                    <div className="notice small">
                      <strong>작가 판단 · 지정한 범위와 조건에서 참고</strong>
                      <p>{selected.note}</p>
                    </div>
                  )}
                  {report?.decisions?.[selected.id] && (
                    <div className="small notice">
                      <strong>판단 버전 {report.decisions[selected.id].version}</strong>
                      {report.decisions[selected.id].stale && <p>근거 변경 · 재확인 필요</p>}
                      {report.decisions[selected.id].scopes.map((scope) => (
                        <p key={scope.id}>
                          {scope.scopeType === 'SCENE'
                            ? '이 장면'
                            : `${scope.fromChapterTitle ?? '시작 회차'} ~ ${scope.toChapterTitle ?? '이후 회차'}`}{' '}
                          · {scope.conditionText}
                        </p>
                      ))}
                    </div>
                  )}
                  <div className="decision-actions">
                    <button
                      disabled={!linked}
                      onClick={() => setEditing({ id: selected.id, status: '수용' })}
                    >
                      수용
                    </button>
                    <button
                      disabled={!linked}
                      onClick={() => setEditing({ id: selected.id, status: '보류' })}
                    >
                      보류
                    </button>
                    <button
                      disabled={!linked}
                      onClick={() => setEditing({ id: selected.id, status: '의도된 설정' })}
                    >
                      의도된 설정
                    </button>
                    <button
                      disabled={!linked}
                      onClick={() => setEditing({ id: selected.id, status: '미검토' })}
                    >
                      판단 철회
                    </button>
                  </div>
                  <p className="small muted">
                    수용은 처리 상태만 바꿉니다. 원고는 자동으로 수정되지 않습니다.
                  </p>
                </article>
              )}
            </>
          )}
        </>
      )}
      {editing && editingReview && (
        <DecisionEditor
          key={editing.id}
          work={work}
          review={editingReview}
          initialStatus={editing.status}
          onSaved={() => onDecisionSaved?.()}
          onClose={() => setEditing(null)}
        />
      )}
    </aside>
  )
}
