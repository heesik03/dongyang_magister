import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { StoryEvent, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'
import { eventLocation, orderedEvents, sourceLines } from './timelineEvidence'
import { ExtractionFlow } from './ExtractionFlow'
import { ExtractionSourceButton } from './ExtractionSourceButton'

function isPlaced(event: StoryEvent) {
  return event.timeStatus === 'CONFIRMED' || (event.timeStatus == null && Boolean(event.time))
}

export function TimelinePanel({ work }: { work: Work }) {
  const updateWork = useWorksStore((state) => state.updateWork)
  const [editing, setEditing] = useState<StoryEvent | 'new' | null>(null)
  const [selectedId, setSelectedId] = useState('')
  const [filterChapter, setFilterChapter] = useState('')
  const [chapterId, setChapterId] = useState('')
  const [placement, setPlacement] = useState('later')
  const draft = editing && editing !== 'new' ? editing : null
  const chapterMap = new Map(
    work.chapters.map((chapter, index) => [chapter.id, { chapter, index }]),
  )
  const sourceChapter = chapterMap.get(chapterId)?.chapter
  const paragraphs = sourceLines(sourceChapter?.content ?? '')
  const ordered = orderedEvents(work.events)
  const placed = ordered.filter(isPlaced)
  const ranks = new Map(placed.map((event, index) => [event.id, index + 1]))
  const matches = (event: StoryEvent) => !filterChapter || event.chapterId === filterChapter
  const visible = placed.filter(matches)
  const pending = ordered.filter((event) => !isPlaced(event) && matches(event))
  const displayed = [...visible, ...pending]
  const selected = displayed.find((event) => event.id === selectedId) ?? displayed[0]
  const anchors = placed.filter((event) => event.id !== draft?.id)
  const sources = new Map(
    ordered.map((event) => [
      event.id,
      eventLocation(event, chapterMap.get(event.chapterId)?.chapter),
    ]),
  )
  const manuscript = ordered
    .filter((event) => chapterMap.has(event.chapterId))
    .sort(
      (left, right) =>
        (chapterMap.get(left.chapterId)?.index ?? 0) -
          (chapterMap.get(right.chapterId)?.index ?? 0) ||
        (sources.get(left.id)?.start ?? Infinity) - (sources.get(right.id)?.start ?? Infinity),
    )
  const earlier = new Set<string>()
  let highest = 0
  for (const event of manuscript) {
    const rank = ranks.get(event.id)
    if (!rank) continue
    if (rank < highest) earlier.add(event.id)
    highest = Math.max(highest, rank)
  }

  function open(event: StoryEvent | 'new') {
    setPlacement(event !== 'new' && isPlaced(event) ? 'keep' : 'later')
    setEditing(event)
    setChapterId(event === 'new' ? filterChapter : event.chapterId)
  }

  function persist(events: StoryEvent[]) {
    updateWork({ ...work, events: events.map((event, sequence) => ({ ...event, sequence })) })
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    if (!title) return
    const keepEvidence = data.get('quote') === 'stored' && draft?.chapterId === chapterId
    const selectedLine = paragraphs.find((line) => String(line.start) === data.get('quote'))
    const next: StoryEvent = {
      id: draft?.id ?? crypto.randomUUID(),
      title,
      description: String(data.get('description') || ''),
      time: String(data.get('time')).trim(),
      characters: String(data.get('characters')).trim(),
      chapterId,
      quote: keepEvidence ? draft.quote : (selectedLine?.quote ?? ''),
      sourceRevision: keepEvidence
        ? draft.sourceRevision
        : selectedLine
          ? sourceChapter?.revision
          : undefined,
      startOffset: keepEvidence
        ? draft.startOffset
        : sourceChapter?.revision == null
          ? undefined
          : selectedLine?.start,
      endOffset: keepEvidence
        ? draft.endOffset
        : sourceChapter?.revision == null
          ? undefined
          : selectedLine?.end,
      extractionId: keepEvidence ? draft.extractionId : null,
      timeStatus: placement === 'later' ? 'UNRESOLVED' : 'CONFIRMED',
    }
    if (placement === 'keep') {
      persist(ordered.map((item) => (item.id === next.id ? next : item)))
    } else {
      const remaining = ordered.filter((item) => item.id !== next.id)
      const anchorIndex = remaining.findIndex(
        (item) => isPlaced(item) && item.id === data.get('anchor'),
      )
      if ((placement === 'before' || placement === 'after') && anchorIndex < 0) return
      const index =
        placement === 'before'
          ? anchorIndex
          : placement === 'after'
            ? anchorIndex + 1
            : remaining.length
      remaining.splice(index, 0, next)
      persist(remaining)
    }
    setSelectedId(next.id)
    setEditing(null)
  }

  function move(event: StoryEvent, offset: number) {
    const rank = ranks.get(event.id)
    if (rank === undefined) return
    const neighbor = placed[rank - 1 + offset]
    if (!neighbor) return
    persist(
      ordered.map((item) =>
        item.id === event.id ? neighbor : item.id === neighbor.id ? event : item,
      ),
    )
  }

  function sourceLink(event: StoryEvent) {
    const location = sources.get(event.id)
    if (!event.quote) return <span className="small muted">원문 미지정</span>
    if (!location) return <span className="badge warning">원문 재확인 필요</span>
    const query = new URLSearchParams({
      chapter: event.chapterId,
      quote: event.quote,
      start: String(location.start),
      end: String(location.end),
    })
    return (
      <Link className="button" to={'/works/' + work.id + '/manuscript?' + query}>
        원문으로 이동 <Icon name="arrow" />
      </Link>
    )
  }

  function renderEvent(event: StoryEvent) {
    const rank = ranks.get(event.id)
    return (
      <li className="event-list-item" key={event.id}>
        <button
          type="button"
          className="workspace-list-row"
          aria-pressed={selected?.id === event.id}
          onClick={() => setSelectedId(event.id)}
        >
          <span>{rank === undefined ? '순서 미정' : rank + '번째'}</span>
          <strong>{event.title}</strong>
          <span>{event.time || '시점 미정'}</span>
          <span>{chapterMap.get(event.chapterId)?.chapter.title || '회차 미지정'}</span>
        </button>
      </li>
    )
  }

  function eventDetail(event: StoryEvent) {
    const rank = ranks.get(event.id)
    return (
      <article className="timeline-detail" aria-label="선택한 사건">
        <header className="editorial-section-title">
          <h2>
            <Icon name="clock" />
            {event.title}
          </h2>
          <p className="muted">
            {event.time || '시점 미정'} ·{' '}
            {chapterMap.get(event.chapterId)?.chapter.title || '회차 미지정'}
          </p>
        </header>
        <div className="event-body">
          {event.description && <p>{event.description}</p>}
          {event.characters && <p className="small muted">등장인물 · {event.characters}</p>}
          {event.quote && (
            <section className="timeline-source">
              <h3>
                <Icon name="file" />
                근거 원문
              </h3>
              <blockquote>{event.quote}</blockquote>
            </section>
          )}
          <div className="timeline-event-actions">
            {sourceLink(event)}
            {event.extractionId && <ExtractionSourceButton workId={work.id} event={event} />}
            <button onClick={() => open(event)}>
              <Icon name="edit" />
              {rank === undefined ? '순서 정하기' : '사건 수정'}
            </button>
            {rank !== undefined && (
              <>
                <button
                  disabled={rank === 1}
                  onClick={() => move(event, -1)}
                  aria-label={event.title + ' 앞 순서로'}
                >
                  ↑ 앞
                </button>
                <button
                  disabled={rank === placed.length}
                  onClick={() => move(event, 1)}
                  aria-label={event.title + ' 뒤 순서로'}
                >
                  ↓ 뒤
                </button>
              </>
            )}
          </div>
        </div>
      </article>
    )
  }

  return (
    <section className="timeline-panel">
      <p className="muted">소설 속에서 먼저 일어난 일부터 정리하세요.</p>
      <div className="timeline-actions">
        <div className="timeline-event-actions">
          <button className="primary" onClick={() => open('new')}>
            <Icon name="plus" /> 사건 추가
          </button>
          <ExtractionFlow work={work} />
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
      </div>
      {filterChapter && !visible.length && !pending.length ? (
        <div className="empty">
          <p>이 회차에 연결된 사건이 없습니다.</p>
          <button onClick={() => setFilterChapter('')}>회차 필터 해제</button>
        </div>
      ) : !work.events.length ? (
        <div className="empty">
          <Icon name="clock" />
          <h3>아직 등록한 사건이 없습니다</h3>
          <p>사건을 직접 추가하거나 원고에서 찾아 등록하세요.</p>
        </div>
      ) : (
        <div className="timeline-workbench editorial-surface">
          <div className="timeline-list-pane">
            {visible.length > 0 && (
              <section aria-labelledby="story-order-heading">
                <h2 id="story-order-heading">이야기 속 순서</h2>
                <ul className="workspace-list timeline-list timeline-rows">
                  <li className="workspace-list-row workspace-list-header" aria-hidden="true">
                    <span>순서</span>
                    <span>사건</span>
                    <span>시점</span>
                    <span>등장 회차</span>
                  </li>
                  {visible.map(renderEvent)}
                </ul>
              </section>
            )}
            {pending.length > 0 && (
              <section aria-labelledby="pending-story-order">
                <h2 id="pending-story-order">
                  순서를 정할 사건 <span className="badge warning">{pending.length}개</span>
                </h2>
                <ul className="workspace-list timeline-list timeline-rows">
                  {pending.map(renderEvent)}
                </ul>
              </section>
            )}
            <details className="workspace-disclosure timeline-help">
              <summary>원고에 나온 순서와 비교</summary>
              <p>
                회차와 원문 위치 순으로 표시합니다. 회상처럼 먼저 일어난 사건이 뒤에서 공개될 수
                있습니다.
              </p>
              <ol className="timeline-comparison-list">
                {manuscript.filter(matches).map((event) => (
                  <li key={event.id}>
                    <div>
                      <span className="small muted">
                        {chapterMap.get(event.chapterId)?.chapter.title}
                      </span>
                      <strong>{event.title}</strong>
                      <span className="small">
                        {ranks.has(event.id)
                          ? '이야기 속 ' + ranks.get(event.id) + '번째'
                          : '이야기 속 순서 미정'}
                      </span>
                      {earlier.has(event.id) && (
                        <span className="badge warning">앞서 일어난 사건</span>
                      )}
                    </div>
                    {sourceLink(event)}
                  </li>
                ))}
              </ol>
              {!manuscript.some(matches) && (
                <p className="small muted">회차에 연결된 사건이 없습니다.</p>
              )}
            </details>
          </div>
          {selected && eventDetail(selected)}
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
              이야기 속 위치
              <select value={placement} onChange={(event) => setPlacement(event.target.value)}>
                <option value="later">나중에 정하기</option>
                {draft && isPlaced(draft) && <option value="keep">현재 순서 유지</option>}
                <option value="last">{anchors.length ? '맨 뒤에 배치' : '첫 사건으로 배치'}</option>
                {anchors.length > 0 && <option value="before">기준 사건 앞에 배치</option>}
                {anchors.length > 0 && <option value="after">기준 사건 뒤에 배치</option>}
              </select>
            </label>
            {(placement === 'before' || placement === 'after') && (
              <label>
                기준 사건
                <select name="anchor" required defaultValue="">
                  <option value="" disabled>
                    사건 선택
                  </option>
                  {anchors.map((event) => (
                    <option key={event.id} value={event.id}>
                      {ranks.get(event.id)}번째 · {event.title}
                    </option>
                  ))}
                </select>
              </label>
            )}
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
