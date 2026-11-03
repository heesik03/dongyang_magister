import { httpClient } from '../../shared/httpClient'
import { parsePage, readRecord, readString, readInteger } from '../../shared/responseReader'
import type { StoryEvent, Work } from '../works/workTypes'

export interface ExtractionSource {
  id: string
  title: string
  content: string
  revision: number
  chapterNo: number
}
export interface EventCandidate extends StoryEvent {
  description: string
  sourceRevision: number
  startOffset: number
  endOffset: number
  sequence: number
  timeStatus: 'CONFIRMED' | 'UNRESOLVED'
  decision: 'HOLD' | 'INCLUDE' | 'EXCLUDE'
}
export interface ExtractionJob {
  id: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  version: number
  createdAt: string
  error: string
  candidates: EventCandidate[]
}
export interface ExtractionPreview {
  sources: ExtractionSource[]
  token: string
  model: string
}
export function parseSource(raw: unknown): ExtractionSource {
  const value = readRecord(raw)
  const source = {
    id: readString(value, 'id'),
    title: readString(value, 'title'),
    content: readString(value, 'content'),
    revision: readInteger(value, 'revision', 1),
    chapterNo: readInteger(value, 'chapterNo', 1),
  }
  if (!source.id || source.content.length > 200000)
    throw new Error('추출 원고 형식이 올바르지 않습니다.')
  return source
}
export function parseCandidate(raw: unknown): EventCandidate {
  const value = readRecord(raw)
  const decision = readString(value, 'decision'),
    timeStatus = readString(value, 'timeStatus')
  if (decision !== 'HOLD' && decision !== 'INCLUDE' && decision !== 'EXCLUDE')
    throw new Error('후보 판단 형식 오류')
  if (timeStatus !== 'CONFIRMED' && timeStatus !== 'UNRESOLVED') throw new Error('시점 형식 오류')
  const candidate: EventCandidate = {
    id: readString(value, 'id'),
    title: readString(value, 'title'),
    description: readString(value, 'description'),
    time: readString(value, 'time'),
    characters: readString(value, 'characters'),
    chapterId: readString(value, 'chapterId'),
    quote: readString(value, 'quote'),
    sourceRevision: readInteger(value, 'sourceRevision', 1),
    startOffset: readInteger(value, 'startOffset'),
    endOffset: readInteger(value, 'endOffset', 1),
    sequence: readInteger(value, 'sequence'),
    decision,
    timeStatus,
  }
  if (
    !candidate.id ||
    candidate.title.length > 100 ||
    candidate.description.length > 2000 ||
    candidate.characters.length > 500 ||
    candidate.time.length > 100 ||
    candidate.quote.length > 10000 ||
    !candidate.quote ||
    candidate.endOffset - candidate.startOffset !== candidate.quote.length
  )
    throw new Error('사건 후보 형식이 올바르지 않습니다.')
  return candidate
}
function parseJob(raw: unknown): ExtractionJob {
  const value = readRecord(raw),
    status = readString(value, 'status')
  if (
    status !== 'QUEUED' &&
    status !== 'RUNNING' &&
    status !== 'SUCCEEDED' &&
    status !== 'FAILED' &&
    status !== 'CANCELLED'
  )
    throw new Error('추출 상태 오류')
  if (!Array.isArray(value.candidates) || value.candidates.length > 100)
    throw new Error('후보 목록 오류')
  return {
    id: readString(value, 'id'),
    status,
    version: readInteger(value, 'version'),
    createdAt: readString(value, 'createdAt'),
    error: value.error == null ? '' : readString(value, 'error'),
    candidates: value.candidates.map(parseCandidate),
  }
}
const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/event-extraction'
export const extractionClient = {
  async list(workId: string, page = 0, signal?: AbortSignal) {
    return parsePage(await httpClient.get(path(workId), { params: { page }, signal }), parseJob)
  },
  async get(workId: string, id: string, signal?: AbortSignal) {
    return parseJob(await httpClient.get(path(workId) + '/' + encodeURIComponent(id), { signal }))
  },
  async preview(work: Work, ids: string[], signal?: AbortSignal): Promise<ExtractionPreview> {
    const value = readRecord(
      await httpClient.post(
        path(work.id) + '/preview',
        {
          chapters: work.chapters
            .filter((chapter) => ids.includes(chapter.id))
            .map(({ id, revision }) => ({ id, revision })),
        },
        { signal },
      ),
    )
    if (!Array.isArray(value.sources) || !value.sources.length || value.sources.length > 10)
      throw new Error('전송 자료 확인 실패')
    return {
      sources: value.sources.map(parseSource),
      token: readString(value, 'token'),
      model: readString(value, 'model'),
    }
  },
  async create(workId: string, preview: ExtractionPreview, requestId: string) {
    return parseJob(
      await httpClient.post(path(workId), {
        chapters: preview.sources.map(({ id, revision }) => ({ id, revision })),
        token: preview.token,
        consent: true,
        requestId,
      }),
    )
  },
  async save(workId: string, job: ExtractionJob, candidates: EventCandidate[]) {
    return parseJob(
      await httpClient.put(path(workId) + '/' + encodeURIComponent(job.id) + '/candidate', {
        version: job.version,
        candidates,
      }),
    )
  },
  async confirm(workId: string, job: ExtractionJob, workspaceVersion: number, mutationId: string) {
    await httpClient.post(path(workId) + '/' + encodeURIComponent(job.id) + '/confirmation', {
      version: job.version,
      workspaceVersion,
      mutationId,
    })
  },
  async cancel(workId: string, id: string) {
    return parseJob(
      await httpClient.put(path(workId) + '/' + encodeURIComponent(id) + '/cancellation', {}),
    )
  },
  async sources(workId: string, id: string, signal?: AbortSignal) {
    const value = readRecord(
      await httpClient.get(path(workId) + '/' + encodeURIComponent(id) + '/source', { signal }),
    )
    if (!Array.isArray(value.sources) || value.sources.length > 10)
      throw new Error('근거 원고 형식 오류')
    return value.sources.map(parseSource)
  },
}
