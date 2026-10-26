import { httpClient } from '../../shared/httpClient'
import { parsePage, readInteger, readRecord, readString } from '../../shared/responseReader'
import { isWorks } from '../works/workStorage'
import type { Analysis, Chapter, Review } from '../works/workTypes'

export interface AnalysisInput {
  chapterId: string
  chapterRevision?: number
  workspaceVersion: number
  settingIds: string[]
  previousIds: string[]
  consistency: boolean
  motivation: boolean
}
export interface InputSnapshot {
  chapter: Chapter
  previous: Chapter[]
  settings: {
    id: string
    title: string
    content: string
    kind: string
    status: string
    version: number
  }[]
  decisions: Review[]
  consistency: boolean
  motivation: boolean
}
export interface Preview {
  previewToken: string
  policyVersion: string
  expiresAt: string
  input: InputSnapshot
  provider: string
  model: string
  retentionNotice: string
  deletionNotice: string
  providerPolicyUrl: string
  request: AnalysisInput & { requestId: string }
}
export interface Job {
  id: string
  status: string
  error: string | null
  result: Analysis | null
}
export interface JobSummary {
  id: string
  chapterId: string | null
  chapterTitle: string | null
  status: string
  createdAt: string
  updatedAt: string
  previewConfirmed: boolean
}
export interface Snapshot {
  jobId: string
  input: InputSnapshot
  legacy: boolean
  stale: boolean
}
const statuses = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED']
export const isActiveJob = (job: { status: string }) =>
  job.status === 'QUEUED' || job.status === 'RUNNING'
const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/analysis'
function parseStatus(value: Record<string, unknown>) {
  const status = readString(value, 'status')
  if (!statuses.includes(status)) throw new Error('분석 상태가 올바르지 않습니다.')
  return status
}
function parseAnalysis(value: unknown): Analysis {
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
  return candidate[0].analyses[0]
}
export function parseJob(raw: unknown): Job {
  const value = readRecord(raw)
  if (value.error !== null && typeof value.error !== 'string')
    throw new Error('분석 오류 응답이 올바르지 않습니다.')
  if (value.status === 'SUCCEEDED' && value.result === null)
    throw new Error('완료된 분석의 결과가 없습니다. 다시 조회해 주세요.')
  return {
    id: readString(value, 'id'),
    status: parseStatus(value),
    error: value.error,
    result: value.result === null ? null : parseAnalysis(value.result),
  }
}
function parseChapter(raw: unknown): Chapter {
  const value = readRecord(raw)
  const id = readString(value, 'id'),
    title = readString(value, 'title'),
    content = readString(value, 'content')
  if (
    !id.trim() ||
    id.length > 64 ||
    !title.trim() ||
    title.length > 100 ||
    content.length > 200000
  )
    throw new Error('전송 원고 형식이 올바르지 않습니다.')
  return { id, title, content, revision: readInteger(value, 'revision', 1) }
}
export function parseInput(raw: unknown): InputSnapshot {
  const input = readRecord(raw)
  if (
    !Array.isArray(input.previous) ||
    !Array.isArray(input.settings) ||
    !Array.isArray(input.decisions) ||
    typeof input.consistency !== 'boolean' ||
    typeof input.motivation !== 'boolean'
  )
    throw new Error('분석 입력 응답이 올바르지 않습니다.')
  if (input.previous.length > 100 || input.settings.length > 1000 || input.decisions.length > 100)
    throw new Error('전송 자료 범위를 초과했습니다.')
  const chapter = parseChapter(input.chapter)
  const settings = input.settings.map((raw) => {
    const value = readRecord(raw),
      status = readString(value, 'status')
    if (status !== '확정') throw new Error('확정되지 않은 설정은 분석 기준으로 사용할 수 없습니다.')
    return {
      id: readString(value, 'id'),
      title: readString(value, 'title'),
      content: readString(value, 'content'),
      kind: readString(value, 'kind'),
      status,
      version: readInteger(value, 'version'),
    }
  })
  const decisions = parseAnalysis({
    chapterId: chapter.id,
    revision: chapter.revision,
    source: chapter.content,
    settingIds: settings.map((setting) => setting.id),
    previousIds: [],
    reviews: input.decisions,
    createdAt: '',
  }).reviews
  return {
    chapter,
    settings,
    previous: input.previous.map(parseChapter),
    decisions,
    consistency: input.consistency,
    motivation: input.motivation,
  }
}
export const analysisClient = {
  async preview(workId: string, input: AnalysisInput, signal?: AbortSignal): Promise<Preview> {
    const request = { ...input, requestId: crypto.randomUUID() }
    const value = readRecord(await httpClient.post(path(workId) + '/preview', request, { signal }))
    const policyUrl = readString(value, 'providerPolicyUrl')
    if (policyUrl !== 'https://ai.google.dev/gemini-api/terms')
      throw new Error('공급자 정책 주소가 올바르지 않습니다.')
    return {
      previewToken: readString(value, 'previewToken'),
      policyVersion: readString(value, 'policyVersion'),
      expiresAt: readString(value, 'expiresAt'),
      input: parseInput(value.input),
      provider: readString(value, 'provider'),
      model: readString(value, 'model'),
      retentionNotice: readString(value, 'retentionNotice'),
      deletionNotice: readString(value, 'deletionNotice'),
      providerPolicyUrl: policyUrl,
      request,
    }
  },
  async create(workId: string, preview: Preview, signal?: AbortSignal): Promise<Job> {
    return parseJob(
      await httpClient.post(
        path(workId),
        {
          ...preview.request,
          previewToken: preview.previewToken,
          consentPolicyVersion: preview.policyVersion,
        },
        { signal },
      ),
    )
  },
  async get(workId: string, jobId: string, signal?: AbortSignal): Promise<Job> {
    return parseJob(
      await httpClient.get(path(workId) + '/' + encodeURIComponent(jobId), { signal }),
    )
  },
  async list(workId: string, chapterId: string, page = 0, signal?: AbortSignal) {
    return parsePage(
      await httpClient.get(path(workId), { signal, params: { chapterId, page, size: 10 } }),
      (raw) => {
        const value = readRecord(raw)
        if (
          (value.chapterId !== null && typeof value.chapterId !== 'string') ||
          (value.chapterTitle !== null && typeof value.chapterTitle !== 'string') ||
          typeof value.previewConfirmed !== 'boolean'
        )
          throw new Error('분석 이력 응답이 올바르지 않습니다.')
        return {
          id: readString(value, 'id'),
          chapterId: value.chapterId,
          chapterTitle: value.chapterTitle,
          status: parseStatus(value),
          createdAt: readString(value, 'createdAt'),
          updatedAt: readString(value, 'updatedAt'),
          previewConfirmed: value.previewConfirmed,
        } satisfies JobSummary
      },
    )
  },
  async snapshot(workId: string, jobId: string, signal?: AbortSignal): Promise<Snapshot> {
    const value = readRecord(
      await httpClient.get(path(workId) + '/' + encodeURIComponent(jobId) + '/snapshot', {
        signal,
      }),
    )
    if (typeof value.legacy !== 'boolean' || typeof value.stale !== 'boolean')
      throw new Error('스냅샷 응답이 올바르지 않습니다.')
    return {
      jobId: readString(value, 'jobId'),
      input: parseInput(value.input),
      legacy: value.legacy,
      stale: value.stale,
    }
  },
  async cancel(workId: string, jobId: string): Promise<Job> {
    return parseJob(
      await httpClient.post(path(workId) + '/' + encodeURIComponent(jobId) + '/cancel'),
    )
  },
}
