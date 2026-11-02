import type { Chapter, StoryEvent } from '../works/workTypes'

export function sourceLines(content: string) {
  return [...content.matchAll(/[^\n]+/g)]
    .filter((match) => match[0].trim())
    .map((match) => ({
      quote: match[0],
      start: match.index,
      end: match.index + match[0].length,
    }))
}
export function eventLocation(event: StoryEvent, chapter?: Chapter) {
  if (!chapter || !event.quote) return null
  if (event.sourceRevision != null && event.sourceRevision !== chapter.revision) return null
  if (event.startOffset != null && event.endOffset != null) {
    return chapter.content.slice(event.startOffset, event.endOffset) === event.quote
      ? { start: event.startOffset, end: event.endOffset }
      : null
  }
  const start = chapter.content.indexOf(event.quote)
  // 기존 수동 사건은 같은 문장이 한 번만 나올 때에만 위치를 복원한다.
  return start >= 0 && start === chapter.content.lastIndexOf(event.quote)
    ? { start, end: start + event.quote.length }
    : null
}
export function orderedEvents(events: StoryEvent[]) {
  return events
    .map((event, index) => ({ event, index }))
    .sort(
      (first, second) =>
        (first.event.sequence ?? first.index) - (second.event.sequence ?? second.index) ||
        first.index - second.index,
    )
    .map(({ event }) => event)
}
