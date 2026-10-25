import { useState, type FormEvent } from 'react'
import type { Analysis, Decision, Review, Work } from '../works/workTypes'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function ReviewPanel({
  work,
  analysis,
  stale,
  analyzing,
  error,
  retry,
  onJump,
  onDecision,
}: {
  work: Work
  analysis?: Analysis
  stale: boolean
  analyzing: boolean
  error: string
  retry: () => void
  onJump: (quote: string) => void
  onDecision: (id: string, decision: Decision, note: string) => void
}) {
  const [selectedId, setSelectedId] = useState('')
  const [filter, setFilter] = useState('전체')
  const [intent, setIntent] = useState<Review | null>(null)
  const filtered =
    analysis?.reviews.filter(
      (review) =>
        filter === '전체' ||
        (filter === '미검토' ? review.decision === '미검토' : review.decision !== '미검토'),
    ) || []
  const selected = filtered.find((review) => review.id === selectedId) || filtered[0]
  // 의도된 전개는 오류로 확정하지 않는다. 작가가 남긴 사유를 해당 후보에 함께 보관한다.
  function saveIntent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const note = String(new FormData(event.currentTarget).get('note')).trim()
    if (!intent || !note) return
    onDecision(intent.id, '의도된 설정', note)
    setIntent(null)
  }
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
              {work.chapters.find((chapter) => chapter.id === analysis.chapterId)?.title} · 원고
              버전 {analysis.revision}
              <br />
              확정 설정 {analysis.settingIds.length}개 · 이전 원고 {analysis.previousIds.length}개
            </p>
            <p>
              참고 설정:{' '}
              {analysis.settingIds
                .map(
                  (id) =>
                    work.settings.find((setting) => setting.id === id)?.title || '삭제된 설정',
                )
                .join(', ') || '없음'}
            </p>
            <p>
              이전 원고:{' '}
              {analysis.previousIds
                .map((id) => work.chapters.find((chapter) => chapter.id === id)?.title)
                .join(', ') || '선택하지 않음'}
            </p>
            <span>선택한 이전 원고와 확정 설정을 함께 참고한 결과입니다.</span>
          </div>
          {stale && (
            <p className="notice stale" role="status">
              원고 또는 참고 설정이 바뀌었습니다. 이전 결과입니다. 다시 분석해 주세요.
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
                      onJump(review.quote)
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
                    {work.chapters.find((chapter) => chapter.id === analysis.chapterId)?.title}
                  </span>
                  <blockquote>{selected.quote}</blockquote>
                  <button className="text-button" onClick={() => onJump(selected.quote)}>
                    <Icon name="arrow" />
                    원문으로 이동
                  </button>
                  <span className="detail-label">
                    확정 설정 ·{' '}
                    {work.settings.find((setting) => setting.id === selected.settingId)?.title}
                  </span>
                  <blockquote className="evidence">{selected.evidence}</blockquote>
                  <span className="detail-label">확인 질문과 판단 이유</span>
                  <p>{selected.reason}</p>
                  <span className="detail-label">수정 방향</span>
                  <p>{selected.suggestion}</p>
                  {selected.note && (
                    <div className="notice small">
                      <strong>작가의 의도 · 이 회차의 해당 장면에 적용</strong>
                      <p>{selected.note}</p>
                    </div>
                  )}
                  <div className="decision-actions">
                    <button disabled={stale} onClick={() => onDecision(selected.id, '수용', '')}>
                      수용
                    </button>
                    <button disabled={stale} onClick={() => onDecision(selected.id, '보류', '')}>
                      보류
                    </button>
                    <button disabled={stale} onClick={() => setIntent(selected)}>
                      의도된 설정
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
      {intent && (
        <Dialog title="의도된 설정으로 기록" onClose={() => setIntent(null)}>
          <form className="stack" onSubmit={saveIntent}>
            <blockquote>{intent.quote}</blockquote>
            <p className="small muted">
              현재 회차의 이 장면에 적용합니다. 확정 설정을 덮어쓰거나 다른 장면에 자동 적용하지
              않습니다.
            </p>
            <label>
              의도와 적용 이유
              <textarea
                name="note"
                rows={4}
                required
                defaultValue={intent.note}
                placeholder="예: 2화에서 봉인이 풀려 능력을 얻는 반전. 다음 회차에 계기를 설명할 예정."
                autoFocus
              />
            </label>
            <div className="dialog-actions">
              <button type="button" onClick={() => setIntent(null)}>
                취소
              </button>
              <button className="primary">판단 저장</button>
            </div>
          </form>
        </Dialog>
      )}
    </aside>
  )
}
