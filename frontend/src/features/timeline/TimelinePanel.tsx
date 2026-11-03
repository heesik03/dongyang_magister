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
    return (
      <article className="event-card" key={event.id}>
        <div className="event-order">
          <strong>{String(index + 1).padStart(2, '0')}</strong>
          <span>{event.time || '시점 미상'}</span>
          <span className="small muted">사건 순서 {index + 1}</span>
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
