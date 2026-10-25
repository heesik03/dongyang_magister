import { request, mutate } from '../../shared/http'
import type { Work, Setting, SettingKind, SettingStatus } from './types'

export type WorkInput = Pick<Work, 'title' | 'genre' | 'era' | 'description'>
export type SettingInput = Pick<Setting, 'title' | 'kind' | 'status' | 'content'>
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
const kinds: Record<string, SettingKind> = {
  CHARACTER: '인물',
  WORLD: '세계관',
  MAJOR_EVENT: '주요 사건',
  INTENDED_ADAPTATION: '의도한 각색',
}
const statuses: Record<string, SettingStatus> = {
  CONFIRMED: '확정',
  IDEA: '아이디어',
  AI_CANDIDATE: 'AI 후보',
}
const kindCodes: Record<SettingKind, string> = {
  인물: 'CHARACTER',
  세계관: 'WORLD',
  '주요 사건': 'MAJOR_EVENT',
  '의도한 각색': 'INTENDED_ADAPTATION',
}
const statusCodes: Record<SettingStatus, string> = {
  확정: 'CONFIRMED',
  아이디어: 'IDEA',
  'AI 후보': 'AI_CANDIDATE',
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('서버 응답 형식이 올바르지 않습니다.')
  return Object.fromEntries(Object.entries(value))
}
function text(value: Record<string, unknown>, key: string) {
  const result = value[key]
  if (typeof result !== 'string') throw new Error('서버 응답 필드가 올바르지 않습니다.')
  return result
}
function number(value: Record<string, unknown>, key: string, minimum = 0) {
  const result = value[key]
  if (typeof result !== 'number' || !Number.isSafeInteger(result) || result < minimum)
    throw new Error('서버 응답 숫자가 올바르지 않습니다.')
  return result
}
export function parseWork(raw: unknown): Work {
  const value = object(raw)
  const updatedAt = text(value, 'updatedAt')
  if (!Number.isFinite(Date.parse(updatedAt)))
    throw new Error('작품 수정 시각이 올바르지 않습니다.')
  return {
    id: String(number(value, 'id', 1)),
    title: text(value, 'title'),
    genre: text(value, 'genre'),
    era: text(value, 'era'),
    description: text(value, 'description'),
    updatedAt,
    version: number(value, 'version'),
    source: 'server',
    settings: [],
    chapters: [],
    analyses: [],
    events: [],
  }
}
export function parseSetting(raw: unknown): Setting {
  const value = object(raw)
  const kind = kinds[text(value, 'kind')]
  const status = statuses[text(value, 'status')]
  if (!kind || !status) throw new Error('설정 종류 또는 상태가 올바르지 않습니다.')
  return {
    id: String(number(value, 'id', 1)),
    title: text(value, 'title'),
    content: text(value, 'content'),
    kind,
    status,
    version: number(value, 'version'),
  }
}
function page<T>(raw: unknown, parse: (entry: unknown) => T): Page<T> {
  const value = object(raw)
  if (!Array.isArray(value.items)) throw new Error('목록 응답이 올바르지 않습니다.')
  return {
    items: value.items.map(parse),
    page: number(value, 'page'),
    size: number(value, 'size', 1),
    totalElements: number(value, 'totalElements'),
    totalPages: number(value, 'totalPages'),
  }
}
export const workApi = {
  async list(keyword = '', genre = '', pageNumber = 0, signal?: AbortSignal) {
    return page(
      await request('/work', { params: { keyword, genre, page: pageNumber, size: 20 }, signal }),
      parseWork,
    )
  },
  async get(id: string, signal?: AbortSignal) {
    return parseWork(await request(`/work/${encodeURIComponent(id)}`, { signal }))
  },
  async create(input: WorkInput) {
    return parseWork(await mutate('/work', input))
  },
  async update(id: string, input: WorkInput, version: number) {
    return parseWork(await mutate(`/work/${encodeURIComponent(id)}`, { ...input, version }, 'PUT'))
  },
}
export const settingApi = {
  async list(workId: string, kind = '전체', pageNumber = 0, size = 20, signal?: AbortSignal) {
    const kindCode =
      kind === '전체' ? undefined : Object.entries(kindCodes).find(([label]) => label === kind)?.[1]
    return page(
      await request(`/work/${encodeURIComponent(workId)}/setting`, {
        params: { page: pageNumber, size, kind: kindCode },
        signal,
      }),
      parseSetting,
    )
  },
  async save(workId: string, input: SettingInput, existing?: Setting) {
    const path = `/work/${encodeURIComponent(workId)}/setting${existing ? `/${encodeURIComponent(existing.id)}` : ''}`
    const payload = {
      ...input,
      kind: kindCodes[input.kind],
      status: statusCodes[input.status],
      ...(existing ? { version: existing.version } : {}),
    }
    return parseSetting(await mutate(path, payload, existing ? 'PUT' : 'POST'))
  },
}
