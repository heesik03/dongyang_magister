import { useState } from 'react'
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
}

export function HistoryReviewPanel({
  workId,
  jobId,
  chapter,
  report,
  stale,
  onJump,
  onSaved,
}: Props) {
  const [index, setIndex] = useState(0)
  const finding = report.findings[index]
  const matches =
    !!chapter &&
    !!finding &&
    !stale &&
    chapter.revision === report.revision &&
    chapter.content.slice(finding.startOffset, finding.endOffset) === finding.quote
  return (
    <section aria-label="역사 고증 결과">
      <h3>역사 고증</h3>
      <p className="small muted">
        원고 버전 {report.revision} · {new Date(report.checkedAt).toLocaleString('ko-KR')} ·{' '}
        {historyProgress[report.historicalStatus]}
      </p>
      {report.standardStatus === 'FAILED' && (
        <p className="notice">함께 요청한 설정·개연성 검토에 실패했습니다.</p>
      )}
      {report.historicalStatus === 'FAILED' && (
        <p className="notice">역사 고증을 완료하지 못했습니다. 다시 검토해 주세요.</p>
      )}
      {report.historicalStatus === 'COMPLETED' && !finding && (
        <p>이 원고에서 공식 자료와 대조할 주장을 찾지 못했습니다.</p>
      )}
      {finding && (
        <div className="review-workbench">
          <div className="review-candidates">
            <h4>검토 항목 {report.findings.length}개</h4>
            <div className="review-list">
              {report.findings.map((item, i) => (
                <button
                  key={i}
                  className={'review-summary ' + (index === i ? 'selected' : '')}
                  aria-pressed={index === i}
                  onClick={() => setIndex(i)}
                >
                  <strong>{item.target}</strong>{' '}
                  <span className="badge">{historyStatuses[item.status]}</span>
                  <p>{item.quote}</p>
                  {item.decision && <span>{choices[item.decision.choice]}</span>}
                </button>
              ))}
            </div>
          </div>
          <article className="review-detail">
            <span className="detail-label">
              {finding.sceneTime} · {historyStatuses[finding.status]}
            </span>
            <h4>{finding.assertion}</h4>
            <blockquote>{finding.quote}</blockquote>
            <button
              className="text-button"
              disabled={!matches}
              onClick={() => onJump({ ...finding, sourceKey: chapter?.id })}
            >
              원고에서 확인
            </button>
            {!matches && (
              <p className="small muted">현재 원고와 검토본이 달라졌습니다. 다시 검토해 주세요.</p>
            )}
            <p>{finding.reason}</p>
            <h4>공식 근거</h4>
            {!finding.evidence.length && <p>연결된 공식 자료가 없습니다.</p>}
            {finding.evidence.map((source, i) => (
              <section key={i} className="review-evidence">
                <span className="detail-label">{source.institution}</span>
                <p>
                  <a href={source.url} target="_blank" rel="noreferrer">
                    {source.title} ↗
                  </a>
                </p>
                {source.passage ? (
                  <blockquote>{source.passage}</blockquote>
                ) : (
                  <p>해당 주장을 대조할 구절을 찾지 못했습니다.</p>
                )}
              </section>
            ))}
            <HistoryDecisionForm
              key={JSON.stringify([index, finding.decision])}
              saved={finding.decision}
              disabled={!matches}
              onSave={async (decision) => {
                await reviewClient.saveHistoryDecision(workId, jobId, index, decision)
                onSaved?.()
              }}
            />
          </article>
        </div>
      )}
    </section>
  )
}

function HistoryDecisionForm({
  saved,
  disabled,
  onSave,
}: {
  saved?: HistoryDecision
  disabled: boolean
  onSave: (decision: HistoryDecision) => Promise<void>
}) {
  const [choice, setChoice] = useState<HistoryDecision['choice']>(saved?.choice ?? 'HOLD')
  const [reason, setReason] = useState(saved?.reason ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  return (
    <form
      className="stack"
      onSubmit={async (event) => {
        event.preventDefault()
        if (busy || disabled || !reason.trim()) return
        setBusy(true)
        setError('')
        try {
          await onSave({ choice, reason: reason.trim() })
        } catch (failure) {
          setError(failure instanceof Error ? failure.message : '판단을 저장하지 못했습니다.')
        } finally {
          setBusy(false)
        }
      }}
    >
      <fieldset disabled={disabled || busy}>
        <legend>작가 판단</legend>
        {saved && (
          <p>
            기록된 판단 · {choices[saved.choice]} · {saved.reason}
          </p>
        )}
        <label>
          판단{' '}
          <select
            value={choice}
            onChange={(event) => setChoice(event.target.value as HistoryDecision['choice'])}
          >
            {Object.entries(choices).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          판단 사유
          <textarea
            value={reason}
            required
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        <button disabled={!reason.trim()}>{busy ? '저장 중…' : '판단 저장'}</button>
      </fieldset>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}
