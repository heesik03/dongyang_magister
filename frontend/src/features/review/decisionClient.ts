import { httpClient } from '../../shared/httpClient'
import { parsePage, readInteger, readRecord, readString } from '../../shared/responseReader'
import type { Decision } from '../works/workTypes'

export interface DecisionScope {
  fromChapterTitle?: string | null
  toChapterTitle?: string | null
  id?: number
  scopeType: 'CHAPTER_RANGE' | 'SCENE'
  fromChapterId: string | null
  toChapterId: string | null
  sceneSourceId: number | null
  startOffset: number | null
  endOffset: number | null
  conditionText: string
}
export interface SavedDecision {
  id: number | null
  reviewId: string
  quote: string
  version: number
  status: Decision
  reason: string
  basisHash: string
  stale: boolean
  canReconfirm: boolean
  createdAt: string | null
  scopes: DecisionScope[]
}
export interface DecisionRequest {
  status: Decision
  reason: string
  expectedVersion: number
  mutationId: string
  basisHash: string
  reconfirm: boolean
  scopes: Omit<DecisionScope, 'id'>[]
}
export interface DecisionReference {
  fromChapterTitle?: string | null
  toChapterTitle?: string | null
  scopeId: number
  decisionId: number
  reviewId: string
  version: number
  status: Decision
  reason: string
  basisHash: string
  quote: string
  scopeType: string
  fromChapterId: string | null
  toChapterId: string | null
  sceneSourceId: number | null
  startOffset: number | null
  endOffset: number | null
  conditionText: string
  evidenceQuotes: string[]
}
function status(value: Record<string, unknown>): Decision {
  const status = readString(value, 'status')
  if (status !== '미검토' && status !== '수용' && status !== '보류' && status !== '의도된 설정')
    throw new Error('판단 상태가 올바르지 않습니다.')
  return status
}
function nullableString(value: Record<string, unknown>, key: string) {
  return value[key] === null ? null : readString(value, key)
}
function nullableInteger(value: Record<string, unknown>, key: string, min = 0) {
  return value[key] === null ? null : readInteger(value, key, min)
}
function scope(raw: unknown): DecisionScope {
  const value = readRecord(raw)
  const scopeType = readString(value, 'scopeType')
  if (scopeType !== 'CHAPTER_RANGE' && scopeType !== 'SCENE')
    throw new Error('판단 범위가 올바르지 않습니다.')
  const result = {
    fromChapterTitle:
      value.fromChapterTitle === undefined ? null : nullableString(value, 'fromChapterTitle'),
    toChapterTitle:
      value.toChapterTitle === undefined ? null : nullableString(value, 'toChapterTitle'),
    scopeType,
    fromChapterId: nullableString(value, 'fromChapterId'),
    toChapterId: nullableString(value, 'toChapterId'),
    sceneSourceId: nullableInteger(value, 'sceneSourceId', 1),
    startOffset: nullableInteger(value, 'startOffset'),
    endOffset: nullableInteger(value, 'endOffset', 1),
    conditionText: readString(value, 'conditionText'),
  }
  if (
    scopeType === 'CHAPTER_RANGE'
      ? !result.fromChapterId ||
        result.sceneSourceId !== null ||
        result.startOffset !== null ||
        result.endOffset !== null
      : result.fromChapterId !== null ||
        result.toChapterId !== null ||
        result.sceneSourceId === null ||
        result.startOffset === null ||
        result.endOffset === null ||
        result.endOffset <= result.startOffset
  )
    throw new Error('판단 범위 필드가 올바르지 않습니다.')
  return { ...result, scopeType }
}
export function parseDecision(raw: unknown): SavedDecision {
  const value = readRecord(raw)
  if (
    !Array.isArray(value.scopes) ||
    value.scopes.length > 10 ||
    typeof value.stale !== 'boolean' ||
    typeof value.canReconfirm !== 'boolean'
  )
    throw new Error('판단 응답이 올바르지 않습니다.')
  const basisHash = readString(value, 'basisHash')
  if (!/^[a-f0-9]{64}$/.test(basisHash)) throw new Error('판단 근거 버전이 올바르지 않습니다.')
  return {
    id: nullableInteger(value, 'id', 1),
    reviewId: readString(value, 'reviewId'),
    quote: readString(value, 'quote'),
    version: readInteger(value, 'version'),
    status: status(value),
    reason: readString(value, 'reason'),
    basisHash,
    stale: value.stale,
    canReconfirm: value.canReconfirm,
    createdAt: nullableString(value, 'createdAt'),
    scopes: value.scopes.map((raw) => {
      const value = readRecord(raw)
      return { ...scope(raw), id: readInteger(value, 'id', 1) }
    }),
  }
}
export function parseDecisionReference(raw: unknown): DecisionReference {
  const value = readRecord(raw)
  if (
    !Array.isArray(value.evidenceQuotes) ||
    value.evidenceQuotes.length === 0 ||
    !value.evidenceQuotes.every((quote) => typeof quote === 'string') ||
    value.evidenceQuotes.length > 10
  )
    throw new Error('참고 판단 근거가 올바르지 않습니다.')
  const basisHash = readString(value, 'basisHash')
  if (!/^[a-f0-9]{64}$/.test(basisHash) || status(value) === '미검토')
    throw new Error('참고 판단 버전이 올바르지 않습니다.')
  return {
    ...scope(value),
    scopeId: readInteger(value, 'scopeId', 1),
    decisionId: readInteger(value, 'decisionId', 1),
    reviewId: readString(value, 'reviewId'),
    version: readInteger(value, 'version', 1),
    status: status(value),
    reason: readString(value, 'reason'),
    basisHash,
    quote: readString(value, 'quote'),
    evidenceQuotes: value.evidenceQuotes,
  }
}
const path = (workId: string, reviewId: string) =>
  '/work/' + encodeURIComponent(workId) + '/review/' + encodeURIComponent(reviewId) + '/decision'
export const decisionClient = {
  async get(workId: string, reviewId: string, signal?: AbortSignal) {
    return parseDecision(await httpClient.get(path(workId, reviewId), { signal }))
  },
  async save(workId: string, reviewId: string, request: DecisionRequest) {
    return parseDecision(await httpClient.put(path(workId, reviewId), request))
  },
  async history(workId: string, reviewId: string, page = 0, signal?: AbortSignal) {
    return parsePage(
      await httpClient.get(path(workId, reviewId) + '/history', {
        signal,
        params: { page, size: 20 },
      }),
      parseDecision,
    )
  },
  async list(workId: string, page = 0, status?: string, signal?: AbortSignal) {
    return parsePage(
      await httpClient.get('/work/' + encodeURIComponent(workId) + '/decision', {
        signal,
        params: { page, size: 20, status: status || undefined },
      }),
      parseDecision,
    )
  },
}
