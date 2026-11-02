import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { StoryEvent, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function TimelinePanel({ work }: { work: Work }) {
  const updateWork = useWorksStore((state) => state.updateWork)
  const [editing, setEditing] = useState<StoryEvent | 'new' | null>(null)
  const [filterChapter, setFilterChapter] = useState('')
  const [chapterId, setChapterId] = useState(work.chapters[0]?.id || '')
  const draft = editing && editing !== 'new' ? editing : null
  const paragraphs =
    work.chapters
      .find((chapter) => chapter.id === chapterId)
      ?.content.split('\n')
      .filter((text) => text.trim()) || []
  function open(event: StoryEvent | 'new') {
    setEditing(event)
    setChapterId(event === 'new' ? work.chapters[0]?.id || '' : event.chapterId)
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    if (!title) return
    const next: StoryEvent = {
      id: draft?.id || crypto.randomUUID(),
      title,
      description: String(data.get('description') || ''),
      time: String(data.get('time')).trim(),
      characters: String(data.get('characters')).trim(),
      chapterId,
      quote: String(data.get('quote') || ''),
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
  const known = displayed
    .filter((event) => event.time)
    .sort((a, b) => a.time.localeCompare(b.time, 'ko', { numeric: true }))
  const unknown = displayed.filter((event) => !event.time)
  function renderEvent(event: StoryEvent, index: number) {
    const chapter = work.chapters.find((item) => item.id === event.chapterId)
    // 원고에서 인용문이 사라졌다면 기존 위치로 이동시키지 않고 근거 변경을 표시한다.
    const hasSource = chapter?.content.includes(event.quote) && !!event.quote
    return (
      <article className="event-card" key={event.id}>
        <div className="event-order">
          <strong>{String(index + 1).padStart(2, '0')}</strong>
          <span>{event.time || '시점 미상'}</span>
          <span className="small muted">사건 순서 {index + 1}</span>
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
            {hasSource ? (
              <Link
                className="button"
                to={`/works/${work.id}/manuscript?chapter=${event.chapterId}&quote=${encodeURIComponent(event.quote)}`}
              >
                원문으로 이동 <Icon name="arrow" />
              </Link>
            ) : (
              <span className="badge warning">원문 확인 필요</span>
            )}
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
        사건 시점은 숫자를 포함한 이름 순으로 정렬합니다. ‘1일차 밤’, ‘2일차 아침’처럼 기록하세요.
        시점이 비어 있는 사건은 별도로 표시합니다.
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
              등장인물
              <input name="characters" defaultValue={draft?.characters} maxLength={200} />
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
                defaultValue={
                  draft?.chapterId === chapterId && paragraphs.includes(draft.quote)
                    ? draft.quote
                    : ''
                }
              >
                <option value="">원문 미지정</option>
                {paragraphs.map((text, index) => (
                  <option key={`${chapterId}-${index}`} value={text}>
                    {text}
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
