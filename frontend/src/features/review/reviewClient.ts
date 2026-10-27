import { httpClient } from '../../shared/httpClient'
import { parsePage, readInteger, readRecord, readString } from '../../shared/responseReader'
import { parseDecision, type SavedDecision } from './decisionClient'

export interface SourceLocation {
  sourceId: number
  quote: string
  startOffset: number
  endOffset: number
}
export interface ReviewEvidence extends SourceLocation {
  sourceType: 'SETTING' | 'PREVIOUS'
  sourceKey: string
  title: string
  revision: number
  explanation: string
}
export interface LinkedReview {
  id: string
  type: '설정 충돌 후보' | '설명 보완'
  quote: string
  targetSourceId: number
  startOffset: number
  endOffset: number
  reason: string
  suggestion: string
  evidences: ReviewEvidence[]
}
export interface ReviewReport {
  reviews: LinkedReview[]
  legacy: boolean
  stale: boolean
  decisions?: Record<string, SavedDecision>
}
export interface ReviewSource {
  id: number
  type: string
  sourceKey: string
  title: string
  content: string
  revision: number
}
function range(value: Record<string, unknown>) {
  const quote = readString(value, 'quote')
  const startOffset = readInteger(value, 'startOffset')
  const endOffset = readInteger(value, 'endOffset', 1)
  if (!quote.trim() || endOffset - startOffset !== quote.length)
    throw new Error('근거 인용 위치가 올바르지 않습니다.')
  return { quote, startOffset, endOffset }
}
export function parseLinkedReview(raw: unknown): LinkedReview {
  const value = readRecord(raw)
  const type = readString(value, 'type')
  if (
    (type !== '설정 충돌 후보' && type !== '설명 보완') ||
    !Array.isArray(value.evidences) ||
    value.evidences.length < 1 ||
    value.evidences.length > 10
  )
    throw new Error('검토 후보 형식이 올바르지 않습니다.')
  return {
    id: readString(value, 'id'),
    type,
    ...range(value),
    targetSourceId: readInteger(value, 'targetSourceId', 1),
    reason: readString(value, 'reason'),
    suggestion: readString(value, 'suggestion'),
    evidences: value.evidences.map((raw) => {
      const evidence = readRecord(raw)
      const sourceType = readString(evidence, 'sourceType')
      if (sourceType !== 'SETTING' && sourceType !== 'PREVIOUS')
        throw new Error('근거 출처 종류가 올바르지 않습니다.')
      return {
        ...range(evidence),
        sourceId: readInteger(evidence, 'sourceId', 1),
        sourceType,
        sourceKey: readString(evidence, 'sourceKey'),
        title: readString(evidence, 'title'),
        revision: readInteger(evidence, 'revision'),
        explanation: readString(evidence, 'explanation'),
      }
    }),
  }
}
const path = (workId: string, jobId: string) =>
  '/work/' + encodeURIComponent(workId) + '/analysis/' + encodeURIComponent(jobId)
export const reviewClient = {
  async list(workId: string, jobId: string, signal?: AbortSignal): Promise<ReviewReport> {
    const value = readRecord(
      await httpClient.get(path(workId, jobId) + '/review', {
        signal,
        params: { page: 0, size: 100 },
      }),
    )
    if (typeof value.legacy !== 'boolean' || typeof value.stale !== 'boolean')
      throw new Error('검토 상태가 올바르지 않습니다.')
    const page = parsePage(value.candidates, parseLinkedReview)
    if (page.totalElements > 100 || page.items.length !== page.totalElements)
      throw new Error('검토 결과 범위를 초과했습니다.')
    const decisions: Record<string, SavedDecision> = {}
    if (value.decisions !== undefined) {
      for (const [id, raw] of Object.entries(readRecord(value.decisions))) {
        const decision = parseDecision(raw)
        if (decision.reviewId !== id || !page.items.some((review) => review.id === id))
          throw new Error('판단과 후보가 일치하지 않습니다.')
        decisions[id] = decision
      }
    }
    return { reviews: page.items, legacy: value.legacy, stale: value.stale, decisions }
  },
  async source(
    workId: string,
    jobId: string,
    sourceId: number,
    signal?: AbortSignal,
  ): Promise<ReviewSource> {
    const value = readRecord(
      await httpClient.get(path(workId, jobId) + '/source/' + sourceId, { signal }),
    )
    const id = readInteger(value, 'id', 1)
    const type = readString(value, 'type')
    if (id !== sourceId || !['TARGET', 'SETTING', 'PREVIOUS'].includes(type))
      throw new Error('근거 원문이 올바르지 않습니다.')
    return {
      id,
      type,
      sourceKey: readString(value, 'sourceId'),
      title: readString(value, 'title'),
      content: readString(value, 'content'),
      revision: readInteger(value, 'revision'),
    }
  },
}
