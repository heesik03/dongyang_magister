import { httpClient } from '../../shared/httpClient'
import { readRecord, readString } from '../../shared/responseReader'
import { isWorks } from '../works/workStorage'
import type { Analysis } from '../works/workTypes'
export interface AnalysisInput {
  chapterId: string
  workspaceVersion: number
  settingIds: string[]
  previousIds: string[]
  consistency: boolean
  motivation: boolean
}
interface Job {
  id: string
  status: string
  error: string | null
  result: Analysis | null
}
const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/analysis'
function parseJob(raw: unknown): Job {
  const job = readRecord(raw),
    status = readString(job, 'status')
  if (
    !['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status) ||
    (job.error !== null && typeof job.error !== 'string')
  )
    throw new Error('AI 작업 응답 형식이 올바르지 않습니다.')
  let result: Analysis | null = null
  if (job.result !== null) {
    const value = readRecord(job.result)
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
        events: [],
        analyses: [value],
      },
    ]
    if (!isWorks(candidate)) throw new Error('AI 검토 응답 형식이 올바르지 않습니다.')
    result = candidate[0].analyses[0]
  }
  return { id: readString(job, 'id'), status, error: job.error, result }
}
function delay(signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('요청 취소', 'AbortError'))
      return
    }
    const abort = () => {
      clearTimeout(timer)
      reject(new DOMException('요청 취소', 'AbortError'))
    }
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, 1000)
    signal.addEventListener('abort', abort, { once: true })
  })
}
export const analysisClient = {
  async run(workId: string, input: AnalysisInput, signal: AbortSignal): Promise<Analysis> {
    const key = 'magister.analysis:' + workId + ':' + input.chapterId,
      body = JSON.stringify(input)
    let requestId: string = crypto.randomUUID()
    const previous = sessionStorage.getItem(key)
    if (previous) {
      try {
        const value: unknown = JSON.parse(previous)
        if (
          typeof value === 'object' &&
          value !== null &&
          'body' in value &&
          value.body === body &&
          'requestId' in value &&
          typeof value.requestId === 'string'
        )
          requestId = value.requestId
      } catch {
        sessionStorage.removeItem(key)
      }
    }
    sessionStorage.setItem(key, JSON.stringify({ body, requestId }))
    let job = parseJob(await httpClient.post(path(workId), { ...input, requestId }, { signal }))
    sessionStorage.setItem(key, JSON.stringify({ body, requestId, id: job.id }))
    while (job.status === 'QUEUED' || job.status === 'RUNNING') {
      await delay(signal)
      job = parseJob(
        await httpClient.get(path(workId) + '/' + encodeURIComponent(job.id), { signal }),
      )
    }
    sessionStorage.removeItem(key)
    if (job.status !== 'SUCCEEDED' || !job.result)
      throw new Error(job.error || 'AI 검토가 취소되었습니다.')
    return job.result
  },
  async cancel(workId: string, chapterId: string): Promise<void> {
    const key = 'magister.analysis:' + workId + ':' + chapterId,
      stored = sessionStorage.getItem(key)
    if (!stored) return
    const value = readRecord(JSON.parse(stored)),
      id = readString(value, 'id')
    await httpClient.post(path(workId) + '/' + encodeURIComponent(id) + '/cancel')
    sessionStorage.removeItem(key)
  },
}
