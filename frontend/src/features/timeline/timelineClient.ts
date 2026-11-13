import { httpClient } from '../../shared/httpClient'
import { isWorks } from '../works/workStorage'
import type { StoryEvent } from '../works/workTypes'

function parseEvents(raw: unknown): StoryEvent[] {
  if (!Array.isArray(raw)) throw new Error('타임라인 응답 형식이 올바르지 않습니다.')
  const candidate: unknown = [
    {
      id: 'validation',
      title: '',
      genre: '',
      era: '',
      description: '',
      updatedAt: '',
      settings: [],
      chapters: [],
      analyses: [],
      events: raw,
    },
  ]
  if (!isWorks(candidate)) throw new Error('타임라인 사건 형식이 올바르지 않습니다.')
  return candidate[0].events
}

const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/timeline'

export const timelineClient = {
  async list(workId: string, signal?: AbortSignal): Promise<StoryEvent[]> {
    return parseEvents(await httpClient.get(path(workId), { signal }))
  },
  async create(workId: string, event: StoryEvent): Promise<StoryEvent> {
    const events = parseEvents([
      await httpClient.post(path(workId), {
        ...event,
        description: event.description ?? '',
      }),
    ])
    return events[0]
  },
  async update(workId: string, event: StoryEvent): Promise<StoryEvent> {
    const events = parseEvents([
      await httpClient.put(path(workId) + '/' + encodeURIComponent(event.id), {
        ...event,
        description: event.description ?? '',
      }),
    ])
    return events[0]
  },
  async delete(workId: string, eventId: string): Promise<void> {
    await httpClient.delete(path(workId) + '/' + encodeURIComponent(eventId))
  },
  async replaceAll(workId: string, events: StoryEvent[]): Promise<StoryEvent[]> {
    return parseEvents(
      await httpClient.put(path(workId), {
        events: events.map((event, index) => ({
          ...event,
          description: event.description ?? '',
          sequence: event.sequence ?? index,
        })),
      }),
    )
  },
}
