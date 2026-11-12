import { useState } from 'react'
import type { Analysis, Decision, Work } from '../works/workTypes'
import { DecisionEditor } from './DecisionEditor'
import { Icon } from '../../shared/Icon'
import { ReviewOverviewModal } from './ReviewOverviewModal'
import { ReviewComparisonSection } from './ReviewComparisonSection'
import {
  REVIEW_FILTERS,
  type ReviewFilter,
  type ReviewReport,
  type SourceLocation,
} from './reviewClient'
import type { InputSnapshot } from '../analysis/analysisClient'

export function ReviewPanel({
  work,
  hidden = false,
  jobId,
  currentChapterId,
  onSelectChapter,
  initialFilter = '전체',
  onOpenLedger,
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
  onOpenLedger?: () => void
  work: Work
  hidden?: boolean
  jobId?: string
  currentChapterId?: string
  onSelectChapter?: (chapterId: string, jobId?: string, filter?: ReviewFilter) => void
  initialFilter?: ReviewFilter
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
  const [chosenFilter, setFilter] = useState<ReviewFilter | null>(null)
  const filter = chosenFilter ?? initialFilter
  const [editing, setEditing] = useState<{ id: string; status: Decision } | null>(null)
  const [showOverview, setShowOverview] = useState(false)
  const [showComparison, setShowComparison] = useState(false)
  const [showSummary, setShowSummary] = useState(true)

  const filtered =
    analysis?.reviews
      .map((review) => {
        const saved = report?.decisions?.[review.id]
        return saved ? { ...review, decision: saved.status, note: saved.reason } : review
      })
      .filter((review) => {
        if (filter === '전체') return true
        if (filter === '미검토') return review.decision === '미검토'
        if (filter === '처리됨') return review.decision !== '미검토'
        return review.type === filter
      }) || []
  const selected = filtered.find((review) => review.id === selectedId) || filtered[0]
  const linked = report?.reviews.find((review) => review.id === selected?.id)
  const editingReview = report?.reviews.find((review) => review.id === editing?.id)
  const summary = report?.summary

  const groupMap = new Map<string, { title: string; ids: string[] }>()
  if (report?.reviews) {
    for (const candidate of report.reviews) {
      const key = candidate.issueGroupKey || candidate.id
      const existing = groupMap.get(key)
      if (existing) {
        existing.ids.push(candidate.id)
      } else {
        groupMap.set(key, {
          title: candidate.issueGroupTitle || candidate.reason,
          ids: [candidate.id],
        })
      }
    }
  }
  const repeatedGroups = Array.from(groupMap.entries()).filter(([, item]) => item.ids.length > 1)

  return (
    <aside className="review-panel" aria-label="검토 결과" hidden={hidden}>
      <div className="section-line">
        <h2>회차 검토</h2>
        <span className="badge info">AI 검토</span>
      </div>

      {work.source === 'server' && (
        <div className="review-utility-bar">
          <button
            type="button"
            className={showOverview ? 'selected' : 'ghost small'}
            aria-pressed={showOverview}
            onClick={() => setShowOverview((prev) => !prev)}
          >
            <Icon name="bar-chart" />
            회차별 검토 현황
          </button>
          {jobId && analysis && !analyzing && (
            <button
              type="button"
              className={showComparison ? 'selected' : 'ghost small'}
              aria-pressed={showComparison}
              onClick={() => setShowComparison((prev) => !prev)}
            >
              <Icon name="compare" />
              수정 전·후 비교
            </button>
          )}
          {onOpenLedger && (
            <button type="button" className="ghost small" onClick={onOpenLedger}>
              <Icon name="edit" />
              내가 남긴 판단
            </button>
          )}
        </div>
      )}

      {showOverview && work.source === 'server' && (
        <ReviewOverviewModal
          workId={work.id}
          currentChapterId={currentChapterId ?? analysis?.chapterId}
          onSelectChapter={(chapterId, selectedJobId, nextFilter) => {
            setFilter(null)
            setSelectedId('')
            setShowOverview(false)
            onSelectChapter?.(chapterId, selectedJobId, nextFilter)
          }}
          onClose={() => setShowOverview(false)}
        />
      )}

      {showComparison && jobId && analysis && !analyzing && work.source === 'server' && (
        <ReviewComparisonSection
          workId={work.id}
          jobId={jobId}
          onSelectCandidate={(candidateId) => {
            setFilter('전체')
            setSelectedId(candidateId)
          }}
          onClose={() => setShowComparison(false)}
        />
      )}

      {report && !report.legacy && <button onClick={onDecisionSaved}>판단 상태 새로 확인</button>}
      {report && !report.legacy && !report.summary && (
        <p role="status" className="notice">
          종합 요약을 불러오지 못했습니다. 개별 검토 결과는 계속 확인할 수 있습니다.
          {retryReport && (
            <button type="button" onClick={retryReport}>
              요약 다시 조회
            </button>
          )}
        </p>
      )}
      {reportLoading && <p role="status">검토 근거를 불러오고 있습니다.</p>}
      {reportError && (
        <div role="alert" className="workspace-error-notice">
          <h3>검토 결과를 불러오지 못했습니다</h3>
          <p>{reportError}</p>
          <button onClick={retryReport}>다시 불러오기</button>
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
            <span className="analysis-spinner" aria-hidden="true" />
            <span className="analysis-state-label">AI 검토 진행 중</span>
            <strong>선택한 원고와 설정을 비교하고 있어요.</strong>
            <p>
              결과가 준비되면 자동으로 표시됩니다.
              <br />
              자료의 양에 따라 시간이 걸릴 수 있습니다.
            </p>
            <div className="analysis-progress-track" aria-hidden="true">
              <span />
            </div>
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
          <Icon name="book-empty" />
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

          {summary && (
            <section className="scope-summary-card" aria-label="선택 범위 종합 요약">
              <div className="row">
                <strong>선택 범위 종합 요약</strong>
                <button
                  type="button"
                  className="text-button"
                  onClick={() => setShowSummary((prev) => !prev)}
                >
                  {showSummary ? '요약 접기' : '요약 펼치기'}
                </button>
              </div>
              {showSummary && (
                <>
                  <p className="small muted">{summary.scopeNotice}</p>
                  <div className="scope-summary-badges">
                    <span className="badge danger">설정 충돌 후보 {summary.conflictCount}건</span>
                    <span className="badge warning">설명 보완 {summary.supplementCount}건</span>
                    <span className="badge">쟁점 그룹 {summary.issueGroupCount}개</span>
                    <span className="badge info">미검토 {summary.unreviewedCount}건</span>
                  </div>
                  {summary.priorityItems.length > 0 && (
                    <div className="priority-issues">
                      <span className="detail-label">우선 확인 쟁점</span>
                      {summary.priorityItems.map((item) => (
                        <div className="priority-issue-item" key={item.groupKey}>
                          <div className="row">
                            <strong>{item.title}</strong>
                            <span
                              className={`badge ${
                                item.type === '설정 충돌 후보' ? 'danger' : 'warning'
                              }`}
                            >
                              {item.type} · {item.candidateIds.length}건
                            </span>
                          </div>
                          <p className="small">{item.summary}</p>
                          {item.sourceTitles.length > 0 && (
                            <p className="small muted">관련 출처: {item.sourceTitles.join(', ')}</p>
                          )}
                          {item.candidateIds[0] && (
                            <button
                              type="button"
                              className="text-button"
                              onClick={() => {
                                setFilter('전체')
                                setSelectedId(item.candidateIds[0])
                              }}
                            >
                              관련 후보 확인 ({item.candidateIds.length}건) →
                            </button>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </section>
          )}

          {repeatedGroups.length > 0 && (
            <details className="issue-groups-box">
              <summary>
                반복 쟁점 묶음 ({repeatedGroups.length}개 그룹 · 개별 근거 분리 유지)
              </summary>
              <div className="issue-groups-list">
                {repeatedGroups.map(([groupKey, group]) => (
                  <div key={groupKey} className="issue-group-item">
                    <strong>
                      {group.title} ({group.ids.length}건)
                    </strong>
                    <div className="issue-group-candidates">
                      {group.ids.map((id, index) => (
                        <button
                          key={id}
                          type="button"
                          className={`ghost small ${selected?.id === id ? 'selected' : ''}`}
                          onClick={() => {
                            setFilter('전체')
                            setSelectedId(id)
                          }}
                        >
                          위치 {index + 1} 열기
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </details>
          )}

          {stale && (
            <p className="notice stale" role="status">
              원고·이전 회차 또는 참고 설정이 바뀌었습니다. 이전 결과입니다. 근거를 선택하면 분석
              당시 자료를 확인할 수 있습니다.
            </p>
          )}
          <div className="review-workbench">
            <div className="review-candidates">
              <h3 className="review-candidate-heading">
                확인할 항목 <span className="badge warning">{filtered.length}개</span>
              </h3>
              <div className="filter-tabs review-filters">
                {REVIEW_FILTERS.map((item) => (
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
                    {filtered.map((review) => {
                      const candidate = report?.reviews.find((item) => item.id === review.id)
                      return (
                        <button
                          className={`review-summary ${selected?.id === review.id ? 'selected' : ''}`}
                          key={review.id}
                          aria-pressed={selected?.id === review.id}
                          onClick={() => {
                            setSelectedId(review.id)
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
                          {candidate?.issueGroupTitle && (
                            <span className="small muted">{candidate.issueGroupTitle}</span>
                          )}
                          <p>{review.quote}</p>
                        </button>
                      )
                    })}
                  </div>
                </>
              )}
            </div>
            {selected && (
              <article className="review-detail">
                <span className="small muted">
                  {selected.type === '설정 충돌 후보'
                    ? '확인 필요 · 능력 설정'
                    : '판단 유보 · 행동의 동기'}
                </span>
                <h3>{linked?.issueGroupTitle || '원문과 설정 사이의 연결'}</h3>
                <section className="review-evidence review-evidence--manuscript">
                  <span className="detail-label">
                    원고 ·{' '}
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
                    원고에서 확인
                  </button>
                </section>
                {linked ? (
                  linked.evidences.map((evidence) => (
                    <section
                      className={`review-evidence review-evidence--${evidence.sourceType === 'SETTING' ? 'setting' : 'manuscript'}`}
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
                <details className="workspace-disclosure">
                  <summary>보완 방향 보기</summary>
                  <p>{selected.suggestion}</p>
                </details>

                {linked &&
                  (linked.confirmedFact ||
                    linked.interpretation ||
                    linked.missingInfo ||
                    (linked.options && linked.options.length > 0)) && (
                    <section className="structured-review-stages" aria-label="4단계 검토 구조">
                      <span className="detail-label">사실 · 해석 · 부족 정보 · 보완 선택지</span>
                      {linked.confirmedFact && (
                        <div className="review-stage-block">
                          <strong>1. 확인된 사실</strong>
                          <p>{linked.confirmedFact}</p>
                        </div>
                      )}
                      {linked.interpretation && (
                        <div className="review-stage-block">
                          <strong>2. 해석이 필요한 부분</strong>
                          <p>{linked.interpretation}</p>
                        </div>
                      )}
                      {linked.missingInfo && (
                        <div className="review-stage-block">
                          <strong>3. 부족한 정보</strong>
                          <p>{linked.missingInfo}</p>
                        </div>
                      )}
                      {linked.options && linked.options.length > 0 && (
                        <div className="review-stage-block">
                          <strong>4. 가능한 보완 방향 (원고 자동 수정 없음)</strong>
                          <div className="revision-options-list">
                            {linked.options.map((option, index) => (
                              <div
                                className="revision-option-card"
                                key={`${option.title}:${index}`}
                              >
                                <strong>{option.title}</strong>
                                <p>{option.direction}</p>
                                <p className="small muted">전제: {option.premise}</p>
                                {option.rationale && (
                                  <p className="small muted">이유: {option.rationale}</p>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </section>
                  )}

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
          </div>
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
