import { useRef, useState } from 'react'
import { Icon, type IconName } from '../../shared/Icon'
import './historyReview.css'
import type { Chapter } from '../works/workTypes'
import { reviewClient, type SourceLocation } from './reviewClient'
import {
  choices,
  historyProgress,
  historyStatuses,
  type HistoryDecision,
  type HistoryReport,
} from './historyReport'

type Props = {
  workId: string
  jobId: string
  chapter?: Chapter
  report: HistoryReport
  stale: boolean
  onJump: (location: SourceLocation) => void
  onSaved?: () => void
  workTitle?: string
  onHistory?: () => void
  onRetry?: () => void
  onBack?: () => void
}

export function HistoryReviewPanel({
  workId,
  jobId,
  chapter,
  report,
  stale,
  onJump,
  onSaved,
  workTitle,
  onHistory,
  onRetry,
  onBack,
}: Props) {
  const [selection, setSelection] = useState<number | null>(null)
  const [filter, setFilter] = useState<(typeof filters)[number]>('전체')
  const listHeading = useRef<HTMLHeadingElement>(null)
  const detailHeading = useRef<HTMLHeadingElement>(null)
  const visible = report.findings
    .map((item, index) => ({ item, index }))
    .filter(
      ({ item }) => filter === '전체' || (filter === '처리됨' ? !!item.decision : !item.decision),
    )
  const active = visible.find(({ index }) => index === selection) ?? visible[0]
  const finding = active?.item
  const matching =
    !!chapter &&
    !!finding &&
    !stale &&
    chapter.revision === report.revision &&
    chapter.content.slice(finding.startOffset, finding.endOffset) === finding.quote
  const conflicts = report.findings.filter((item) => item.status === 'POSSIBLE_CONFLICT').length

  function showList() {
    setSelection(null)
    requestAnimationFrame(() => listHeading.current?.focus())
  }

  return (
    <section
      className={`history-review ${selection === null || !finding ? '' : 'history-review--detail'}`}
      aria-label="역사 고증 결과"
    >
      <header className="history-result-header">
        <div>
          {onBack && (
            <button className="text-button history-mobile-back" onClick={onBack}>
              ‹ 뒤로
            </button>
          )}
          <h2 ref={listHeading} tabIndex={-1}>
            AI 검토 결과
          </h2>
          <p className="muted">{[workTitle, chapter?.title].filter(Boolean).join(' / ')}</p>
          <p className="small muted">
            원고 버전 {report.revision} · {new Date(report.checkedAt).toLocaleString('ko-KR')} ·{' '}
            {historyProgress[report.historicalStatus]}
          </p>
        </div>
        <div className="history-result-actions history-result-actions--desktop">
          {onHistory && (
            <button onClick={onHistory}>
              <Icon name="history" /> 분석 작업 이력
            </button>
          )}
          {onRetry && (
            <button className="primary" onClick={onRetry}>
              <Icon name="check" /> 다시 검토
            </button>
          )}
        </div>
      </header>
      {report.standardStatus === 'FAILED' && (
        <p className="notice">함께 요청한 설정·개연성 검토에 실패했습니다.</p>
      )}
      {report.historicalStatus === 'FAILED' && (
        <p className="notice">역사 고증을 완료하지 못했습니다. 다시 검토해 주세요.</p>
      )}
      <div className="history-result-grid">
        <div className="history-result-list">
          <h3>
            <Icon name="check" /> 검토 후보
          </h3>
          <p className="muted">
            역사 고증 {report.findings.length}건 · 충돌 후보 {conflicts}건
          </p>
          <div className="history-filters" aria-label="역사 고증 판단 필터">
            {filters.map((label) => (
              <button
                key={label}
                type="button"
                aria-pressed={filter === label}
                onClick={() => {
                  setFilter(label)
                  setSelection(null)
                }}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="history-candidates">
            {visible.map(({ item, index }) => (
              <button
                key={index}
                type="button"
                aria-pressed={active?.index === index}
                onClick={() => {
                  setSelection(index)
                  requestAnimationFrame(() => detailHeading.current?.focus())
                }}
              >
                <Icon name="file" />
                <span>
                  <strong>{item.assertion}</strong>
                  <small>
                    {historyStatuses[item.status]}
                    {item.decision && ` · ${choices[item.decision.choice]}`}
                  </small>
                </span>
                <Icon name="arrow" />
              </button>
            ))}
          </div>
          {!visible.length && (
            <p>
              {report.findings.length
                ? '이 상태의 항목이 없습니다.'
                : '표시할 역사 고증 항목이 없습니다.'}
            </p>
          )}
          <div className="history-result-actions history-result-actions--mobile">
            {onHistory && <button onClick={onHistory}>분석 작업 이력</button>}
            {onRetry && (
              <button className="primary" onClick={onRetry}>
                다시 검토
              </button>
            )}
          </div>
        </div>
        {finding && (
          <article className="history-result-detail">
            <button className="text-button history-mobile-back" onClick={showList}>
              ‹ 뒤로
            </button>
            <h3 ref={detailHeading} tabIndex={-1}>
              <Icon name="book" /> {finding.assertion}
            </h3>
            <p className="history-mobile-caption muted">역사 고증 상세</p>
            <div className="history-finding-reason">
              <strong>
                {historyStatuses[finding.status]}
                {finding.sceneTime && ` · ${finding.sceneTime}`}
              </strong>
              <p>{finding.reason}</p>
            </div>
            <div className="history-evidence-grid">
              <section className="history-evidence-card">
                <h4>
                  <Icon name="file" /> 검토 원문
                </h4>
                {chapter && <p className="muted">{chapter.title}</p>}
                <blockquote>{finding.quote}</blockquote>
                <button
                  type="button"
                  disabled={!matching}
                  onClick={() =>
                    onJump({
                      quote: finding.quote,
                      startOffset: finding.startOffset,
                      endOffset: finding.endOffset,
                      sourceKey: chapter?.id,
                    })
                  }
                >
                  <Icon name="arrow" /> 원고에서 확인
                </button>
                {!matching && (
                  <p className="small muted">
                    현재 원고와 검토본이 달라졌습니다. 다시 검토해 주세요.
                  </p>
                )}
              </section>
              <section className="history-evidence-card">
                <h4>
                  <Icon name="book" /> 공식 근거
                </h4>
                {!finding.evidence.length && <p>연결된 공식 자료가 없습니다.</p>}
                {finding.evidence.map((source, index) => (
                  <div className="history-source" key={index}>
                    <p>
                      <a href={source.url} target="_blank" rel="noreferrer">
                        {source.institution} · {source.title} ↗
                      </a>
                    </p>
                    {source.passage ? (
                      <blockquote>{source.passage}</blockquote>
                    ) : (
                      <p>해당 주장을 대조할 구절을 찾지 못했습니다.</p>
                    )}
                  </div>
                ))}
              </section>
              <details className="history-revision-direction">
                <summary>보완 방향 보기</summary>
                <p>{revisionDirections[finding.status]}</p>
              </details>
            </div>
            <HistoryDecisionForm
              key={JSON.stringify([active.index, finding.decision])}
              saved={finding.decision}
              disabled={!matching}
              onSave={async (decision) => {
                await reviewClient.saveHistoryDecision(workId, jobId, active.index, decision)
                onSaved?.()
              }}
            />
          </article>
        )}
      </div>
    </section>
  )
}

const filters = ['전체', '미검토', '처리됨'] as const
const decisionIcons: Record<HistoryDecision['choice'], IconName> = {
  ACCEPT: 'check',
  HOLD: 'clock',
  INTENDED: 'edit',
}
const revisionDirections = {
  POSSIBLE_CONFLICT:
    '공식 근거의 시점을 원고의 장면 시점과 비교해 보세요. 창작상 시점을 바꾼 경우 판단 이유에 기록할 수 있습니다.',
  NO_CONFLICT_FOUND:
    '확인한 자료의 범위를 살펴보고, 원고에 다른 역사 주장이 있다면 별도로 검토해 보세요.',
  INSUFFICIENT_EVIDENCE: '장면의 시점과 검토 대상을 구체화한 뒤 관련 자료를 더 찾아보세요.',
  LOOKUP_FAILED: '자료 조회를 다시 시도한 뒤 공식 근거가 확보되면 원고와 대조해 보세요.',
}

function HistoryDecisionForm({
  saved,
  disabled,
  onSave,
}: {
  saved?: HistoryDecision
  disabled: boolean
  onSave: (decision: HistoryDecision | { choice: 'WITHDRAWN'; reason: string }) => Promise<void>
}) {
  const [choice, setChoice] = useState<HistoryDecision['choice']>(saved?.choice ?? 'HOLD')
  const [reason, setReason] = useState(saved?.reason ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function save(decision: Parameters<typeof onSave>[0]) {
    if (busy || disabled) return
    setBusy(true)
    setError('')
    try {
      await onSave(decision)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '판단을 저장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <form
      className="history-decision"
      onSubmit={(event) => {
        event.preventDefault()
        if (reason.trim()) void save({ choice, reason: reason.trim() })
      }}
    >
      <fieldset disabled={disabled || busy}>
        <legend>
          <Icon name="edit" /> 내 판단 남기기
        </legend>
        {saved && (
          <p>
            기록된 판단 · {choices[saved.choice]} · {saved.reason}
          </p>
        )}
        <div className="history-decision-options" aria-label="판단">
          {Object.keys(decisionIcons).map((key) => {
            const option = key as HistoryDecision['choice']
            return (
              <button
                type="button"
                key={option}
                aria-pressed={choice === option}
                onClick={() => setChoice(option)}
              >
                <Icon name={decisionIcons[option]} /> {choices[option]}
              </button>
            )
          })}
          <button
            type="button"
            className="history-withdraw"
            disabled={!saved}
            onClick={() =>
              void save({ choice: 'WITHDRAWN', reason: '작가가 판단을 철회했습니다.' })
            }
          >
            <Icon name="close" /> 판단 철회
          </button>
        </div>
        <label>
          판단 이유
          <textarea
            value={reason}
            required
            maxLength={500}
            placeholder="예: 이 작품에서는 창건 시점을 앞당겼습니다."
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        <button className="primary" disabled={!reason.trim()}>
          {busy ? '저장 중…' : '판단 저장'}
        </button>
      </fieldset>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}
