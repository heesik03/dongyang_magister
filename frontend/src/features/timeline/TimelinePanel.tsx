import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { StoryEvent, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'
import { eventLocation, orderedEvents, sourceLines } from './timelineEvidence'
import { ExtractionFlow } from './ExtractionFlow'
import { ExtractionSourceButton } from './ExtractionSourceButton'

export function TimelinePanel({ work }: { work: Work }) {
  const updateWork = useWorksStore((state) => state.updateWork)
  const [editing, setEditing] = useState<StoryEvent | 'new' | null>(null)
  const [filterChapter, setFilterChapter] = useState('')
  const [chapterId, setChapterId] = useState(work.chapters[0]?.id || '')
  const [flowOpen, setFlowOpen] = useState(true)
  const [flowMode, setFlowMode] = useState<'matrix' | 'table'>('matrix')
  const draft = editing && editing !== 'new' ? editing : null
  const sourceChapter = work.chapters.find((chapter) => chapter.id === chapterId)
  const paragraphs = sourceLines(sourceChapter?.content || '')
  function open(event: StoryEvent | 'new') {
    setEditing(event)
    setChapterId(event === 'new' ? work.chapters[0]?.id || '' : event.chapterId)
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    if (!title) return
    const keepEvidence = data.get('quote') === 'stored' && !!draft
    const selectedLine = paragraphs.find((line) => String(line.start) === data.get('quote'))
    const next: StoryEvent = {
      ...draft,
      id: draft?.id || crypto.randomUUID(),
      title,
      description: String(data.get('description') || ''),
      time: String(data.get('time')).trim(),
      characters: String(data.get('characters')).trim(),
      chapterId,
      quote: keepEvidence ? draft.quote : selectedLine?.quote || '',
      sourceRevision: keepEvidence
        ? draft.sourceRevision
        : selectedLine
          ? sourceChapter?.revision
          : undefined,
      startOffset: keepEvidence ? draft.startOffset : selectedLine?.start,
      endOffset: keepEvidence ? draft.endOffset : selectedLine?.end,
      extractionId: keepEvidence ? draft.extractionId : null,
      sequence: Number(data.get('sequence')),
      timeStatus: data.get('timeConfirmed') ? 'CONFIRMED' : 'UNRESOLVED',
    }
    updateWork({
      ...work,
      events: draft
        ? work.events.map((item) => (item.id === next.id ? next : item))
        : [...work.events, next],
    })
    setEditing(null)
  }
  // 시점이 없는 사건을 임의로 시간순에 끼워 넣지 않고 별도 목록으로 표시한다.
  const displayed = work.events.filter(
    (event) => !filterChapter || event.chapterId === filterChapter,
  )
  const known = orderedEvents(
    displayed.filter((event) => event.time && event.timeStatus !== 'UNRESOLVED'),
  )
  const unknown = orderedEvents(
    displayed.filter((event) => !event.time || event.timeStatus === 'UNRESOLVED'),
  )

  // 서술 회차 순서와 이야기 사건 순서(sequence)를 대조하여 회상·역순 배치를 탐지한다.
  const chapterIndexMap = new Map(work.chapters.map((chapter, index) => [chapter.id, index]))
  const knownOrderMap = new Map(known.map((event, index) => [event.id, index + 1]))
  const reversedEventIds = new Set<string>()
  let maxSeenSequence = -1
  for (const chapter of work.chapters) {
    const chapterConfirmedEvents = known.filter((event) => event.chapterId === chapter.id)
    for (const event of chapterConfirmedEvents) {
      const seq = event.sequence ?? 0
      if (seq < maxSeenSequence) {
        reversedEventIds.add(event.id)
      }
      if (seq > maxSeenSequence) {
        maxSeenSequence = seq
      }
    }
  }
  // 반대로 이야기 시간순에서는 앞인데 뒤쪽 회차에서 서술된 경우도 함께 표시한다.
  let maxSeenChapterIndex = -1
  for (const event of known) {
    const chIdx = chapterIndexMap.get(event.chapterId) ?? -1
    if (chIdx >= 0) {
      if (chIdx < maxSeenChapterIndex) {
        reversedEventIds.add(event.id)
      }
      if (chIdx > maxSeenChapterIndex) {
        maxSeenChapterIndex = chIdx
      }
    }
  }

  function move(event: StoryEvent, direction: number) {
    const sorted = orderedEvents(work.events)
    const index = sorted.findIndex((item) => item.id === event.id)
    const target = index + direction
    if (target < 0 || target >= sorted.length) return
    ;[sorted[index], sorted[target]] = [sorted[target], sorted[index]]
    updateWork({ ...work, events: sorted.map((item, sequence) => ({ ...item, sequence })) })
  }
  function renderEvent(event: StoryEvent, index: number) {
    const chapter = work.chapters.find((item) => item.id === event.chapterId)
    // 원고에서 인용문이 사라졌다면 기존 위치로 이동시키지 않고 근거 변경을 표시한다.
    const location = eventLocation(event, chapter)
    const isReversed = reversedEventIds.has(event.id)
    return (
      <article className="event-card" key={event.id}>
        <div className="event-order">
          <strong>{String(index + 1).padStart(2, '0')}</strong>
          <span>{event.time || '시점 미상'}</span>
          <span className="small muted">사건 순서 {index + 1}</span>
          {isReversed && <span className="badge warning">역순·회상 서술</span>}
          <button onClick={() => move(event, -1)} aria-label={`${event.title} 앞 순서로`}>
            ↑
          </button>
          <button onClick={() => move(event, 1)} aria-label={`${event.title} 뒤 순서로`}>
            ↓
          </button>
        </div>
        <div className="event-body">
          <div className="row">
            <h2>{event.title}</h2>
            <button className="text-button" onClick={() => open(event)}>
              사건 수정
            </button>
          </div>
          {event.description && <p>{event.description}</p>}
          <p className="small muted">등장인물 · {event.characters || '미정'}</p>
          {event.quote && <blockquote>{event.quote}</blockquote>}
          <div className="row">
            <strong className="event-source">근거 회차 · {chapter?.title || '회차 미지정'}</strong>
            {location ? (
              <Link
                className="button"
                to={`/works/${work.id}/manuscript?chapter=${encodeURIComponent(event.chapterId)}&quote=${encodeURIComponent(event.quote)}&start=${location.start}&end=${location.end}`}
              >
                원문으로 이동 <Icon name="arrow" />
              </Link>
            ) : (
              <span className="badge warning">
                {event.sourceRevision != null && event.sourceRevision !== chapter?.revision
                  ? '근거 변경 · 재확인 필요'
                  : '원문 위치 확인 필요'}
              </span>
            )}
            {event.extractionId && <ExtractionSourceButton workId={work.id} event={event} />}
          </div>
        </div>
      </article>
    )
  }
  return (
    <section className="timeline-panel">
      <div className="section-line">
        <div>
          <h1>사건 타임라인</h1>
          <p className="small muted">
            등록한 사건을 시간순으로 살펴보고, 시점과 원문을 직접 정리하세요.
          </p>
        </div>
        <button className="primary" onClick={() => open('new')}>
          <Icon name="plus" />
          사건 추가
        </button>
      </div>
      <ExtractionFlow work={work} />

      {work.events.length > 0 && (
        <section className="timeline-flow-comparison" aria-label="서술 순서와 사건 순서 비교">
          <div className="review-overview-header">
            <div>
              <strong>회차 서술 순서 ↔ 이야기 시간순 비교</strong>
              <p className="small muted">
                각 회차에 서술된 사건이 이야기 시간순(순번)에서 어디에 놓이는지 비교하고, 회상·역순
                서술과 시점 미확정 사건을 구분합니다.
              </p>
            </div>
            <div className="review-overview-actions">
              <div className="filter-tabs" role="group" aria-label="사건 흐름 보기 방식">
                <button
                  type="button"
                  aria-pressed={flowMode === 'matrix'}
                  className={flowMode === 'matrix' ? 'selected' : ''}
                  onClick={() => setFlowMode('matrix')}
                >
                  회차별 흐름도
                </button>
                <button
                  type="button"
                  aria-pressed={flowMode === 'table'}
                  className={flowMode === 'table' ? 'selected' : ''}
                  onClick={() => setFlowMode('table')}
                >
                  대조 목록 표
                </button>
              </div>
              <button
                type="button"
                className="ghost small"
                onClick={() => setFlowOpen((prev) => !prev)}
              >
                {flowOpen ? '흐름도 접기' : '흐름도 펼치기'}
              </button>
            </div>
          </div>

          {flowOpen && (
            <>
              <div className="review-overview-legend" aria-label="사건 흐름 범례">
                <span>
                  <i className="legend-dot legend-dot--normal" aria-hidden="true" /> 정순 서술 (회차
                  순서 = 사건 시간순)
                </span>
                <span>
                  <i className="legend-dot legend-dot--supplement" aria-hidden="true" /> 역순·회상
                  서술 (회차 순서 ≠ 사건 시간순)
                </span>
                <span>
                  <i className="legend-dot legend-dot--unreviewed" aria-hidden="true" /> 시점 미확정
                  (작가 확인 필요 · 시간순 미배치)
                </span>
              </div>

              {flowMode === 'matrix' ? (
                <div className="timeline-flow-lanes" role="list">
                  {work.chapters.map((chapter, chapterIdx) => {
                    const chapterEvents = displayed.filter((item) => item.chapterId === chapter.id)
                    return (
                      <div className="timeline-flow-lane" role="listitem" key={chapter.id}>
                        <div className="timeline-flow-lane-header">
                          <strong>
                            서술 {chapterIdx + 1}순 · {chapter.title}
                          </strong>
                          <span className="small muted">사건 {chapterEvents.length}건</span>
                        </div>
                        {chapterEvents.length === 0 ? (
                          <p className="small muted">이 회차에 연결된 사건이 없습니다.</p>
                        ) : (
                          <div className="timeline-flow-chips">
                            {chapterEvents.map((event) => {
                              const isUnresolved = !event.time || event.timeStatus === 'UNRESOLVED'
                              const isReversed = reversedEventIds.has(event.id)
                              const orderNo = knownOrderMap.get(event.id)
                              return (
                                <button
                                  type="button"
                                  key={event.id}
                                  className={`timeline-flow-chip ${
                                    isUnresolved
                                      ? 'timeline-flow-chip--unresolved'
                                      : isReversed
                                        ? 'timeline-flow-chip--reversed'
                                        : 'timeline-flow-chip--normal'
                                  }`}
                                  onClick={() => open(event)}
                                >
                                  <span className="timeline-chip-order">
                                    {isUnresolved
                                      ? '시점 미확정'
                                      : `시간순 #${orderNo ?? (event.sequence ?? 0) + 1}`}
                                  </span>
                                  <strong>{event.title}</strong>
                                  <span className="small muted">
                                    {event.time || '시점 미상'}
                                    {isReversed ? ' · 역순/회상' : ''}
                                  </span>
                                </button>
                              )
                            })}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              ) : (
                <div className="review-overview-table-wrap">
                  <table className="review-overview-table">
                    <caption>서술 회차 순서와 이야기 시간순 대조 표</caption>
                    <thead>
                      <tr>
                        <th scope="col">사건명</th>
                        <th scope="col">서술 회차</th>
                        <th scope="col">이야기 시간순</th>
                        <th scope="col">시점 표현</th>
                        <th scope="col">배치 구분</th>
                      </tr>
                    </thead>
                    <tbody>
                      {displayed.map((event) => {
                        const chapterIdx = chapterIndexMap.get(event.chapterId)
                        const chapterTitle =
                          work.chapters.find((c) => c.id === event.chapterId)?.title ||
                          '회차 미지정'
                        const isUnresolved = !event.time || event.timeStatus === 'UNRESOLVED'
                        const isReversed = reversedEventIds.has(event.id)
                        const orderNo = knownOrderMap.get(event.id)
                        return (
                          <tr key={event.id}>
                            <td>{event.title}</td>
                            <td>
                              {chapterIdx !== undefined ? `${chapterIdx + 1}순 · ` : ''}
                              {chapterTitle}
                            </td>
                            <td>
                              {isUnresolved
                                ? '미배치 (확인 필요)'
                                : `시간순 #${orderNo ?? (event.sequence ?? 0) + 1}`}
                            </td>
                            <td>{event.time || '시점 미상'}</td>
                            <td>
                              {isUnresolved
                                ? '시점 미확정'
                                : isReversed
                                  ? '역순·회상 서술'
                                  : '정순 서술'}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      )}

      <label className="timeline-filter">
        근거 회차
        <select value={filterChapter} onChange={(event) => setFilterChapter(event.target.value)}>
          <option value="">전체 회차</option>
          {work.chapters.map((chapter) => (
            <option key={chapter.id} value={chapter.id}>
              {chapter.title}
            </option>
          ))}
        </select>
      </label>
      <p className="notice small">
        사건은 작가가 지정한 이야기 순서로 배치합니다. 회차의 서술 순서와 다를 수 있습니다. 기준이
        불명확한 상대 시점·회상은 확인 전까지 시점 미상으로 남겨 주세요.
      </p>
      <div className="timeline-list">{known.map(renderEvent)}</div>
      {unknown.length > 0 && (
        <>
          <h3 className="unknown-heading">시점 미상 · 작가의 확인이 필요한 사건</h3>
          <div className="timeline-list">{unknown.map(renderEvent)}</div>
        </>
      )}
      {!work.events.length && (
        <div className="empty">
          <Icon name="clock" />
          <h3>아직 기록한 사건이 없어요</h3>
          <p>주요 사건을 추가하고 근거 문장과 연결해 보세요.</p>
        </div>
      )}
      {editing && (
        <Dialog title={draft ? '사건 수정' : '사건 추가'} onClose={() => setEditing(null)}>
          <form className="stack" onSubmit={save}>
            <label>
              사건명
              <input name="title" required defaultValue={draft?.title} maxLength={100} autoFocus />
            </label>
            <label>
              사건 설명
              <textarea
                name="description"
                maxLength={2000}
                rows={3}
                defaultValue={draft?.description}
                placeholder="이 사건에서 일어난 일을 기록하세요."
              />
            </label>
            <label>
              시점
              <input
                name="time"
                defaultValue={draft?.time}
                placeholder="예: 2일차 아침 · 비워 두면 시점 미상"
                maxLength={100}
              />
            </label>
            <label>
              이야기 순서
              <input
                name="sequence"
                type="number"
                min={0}
                max={10000}
                required
                defaultValue={
                  draft?.sequence ??
                  (draft
                    ? work.events.findIndex((item) => item.id === draft.id)
                    : work.events.length)
                }
              />
            </label>
            <label>
              <input
                name="timeConfirmed"
                type="checkbox"
                defaultChecked={!!draft?.time && draft.timeStatus !== 'UNRESOLVED'}
              />
              시점과 이야기 속 순서를 확인했습니다
            </label>
            <label>
              등장인물
              <input name="characters" defaultValue={draft?.characters} maxLength={500} />
            </label>
            <label>
              근거 회차
              <select value={chapterId} onChange={(event) => setChapterId(event.target.value)}>
                <option value="">회차 미지정</option>
                {work.chapters.map((chapter) => (
                  <option key={chapter.id} value={chapter.id}>
                    {chapter.title}
                  </option>
                ))}
              </select>
            </label>
            <label>
              근거 문장
              <select
                name="quote"
                key={chapterId}
                defaultValue={draft?.chapterId === chapterId && draft.quote ? 'stored' : ''}
              >
                <option value="">원문 미지정</option>
                {draft?.chapterId === chapterId && draft.quote && (
                  <option value="stored">기존 근거 유지 · {draft.quote}</option>
                )}
                {paragraphs.map((line) => (
                  <option key={`${chapterId}-${line.start}`} value={line.start}>
                    {line.start}–{line.end} · {line.quote}
                  </option>
                ))}
              </select>
            </label>
            <div className="dialog-actions">
              <button type="button" onClick={() => setEditing(null)}>
                취소
              </button>
              <button className="primary">사건 저장</button>
            </div>
          </form>
        </Dialog>
      )}
    </section>
  )
}
