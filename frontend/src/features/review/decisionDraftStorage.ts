import {
  parseDecision,
  type DecisionRequest,
  type DecisionScope,
  type SavedDecision,
} from './decisionClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'
import type { Decision } from '../works/workTypes'

export interface DecisionDraft {
  baseline: SavedDecision
  status: Decision
  reason: string
  scopes: Omit<DecisionScope, 'id'>[]
  reconfirm: boolean
  pending: DecisionRequest | null
}
const prefix = (account: string | null, workId: string) =>
  'magister.decision-draft.v1:' +
  encodeURIComponent(account ?? '') +
  ':' +
  encodeURIComponent(workId) +
  ':'
export const decisionDraftKey = (account: string | null, workId: string, reviewId: string) =>
  prefix(account, workId) + encodeURIComponent(reviewId)
function decisionStatus(value: Record<string, unknown>): Decision {
  const status = readString(value, 'status')
  if (status !== '미검토' && status !== '수용' && status !== '보류' && status !== '의도된 설정')
    throw new Error('판단 초안 상태 오류')
  return status
}
// 작성 중인 빈 범위도 복구한다. 실제 저장 시에는 서버가 완성된 범위와 소유권을 검증한다.
function draftScope(raw: unknown): Omit<DecisionScope, 'id'> {
  const value = readRecord(raw)
  const scopeType = readString(value, 'scopeType')
  if (scopeType !== 'SCENE' && scopeType !== 'CHAPTER_RANGE') throw new Error('판단 초안 범위 오류')
  return {
    scopeType,
    fromChapterId: value.fromChapterId === null ? null : readString(value, 'fromChapterId'),
    toChapterId: value.toChapterId === null ? null : readString(value, 'toChapterId'),
    sceneSourceId: value.sceneSourceId === null ? null : readInteger(value, 'sceneSourceId', 1),
    startOffset: value.startOffset === null ? null : readInteger(value, 'startOffset'),
    endOffset: value.endOffset === null ? null : readInteger(value, 'endOffset'),
    conditionText: readString(value, 'conditionText'),
  }
}
function fields(raw: unknown) {
  const value = readRecord(raw)
  if (
    typeof value.reconfirm !== 'boolean' ||
    !Array.isArray(value.scopes) ||
    value.scopes.length > 10
  )
    throw new Error('판단 초안 형식 오류')
  return {
    status: decisionStatus(value),
    reason: readString(value, 'reason'),
    scopes: value.scopes.map(draftScope),
    reconfirm: value.reconfirm,
  }
}
export function readDecisionDraft(key: string): DecisionDraft | null {
  const raw = sessionStorage.getItem(key)
  if (!raw) return null
  const value = readRecord(JSON.parse(raw))
  const baseline = parseDecision(value.baseline)
  let pending: DecisionRequest | null = null
  if (value.pending !== null) {
    const request = readRecord(value.pending)
    const basisHash = readString(request, 'basisHash')
    const mutationId = readString(request, 'mutationId')
    if (!/^[a-f0-9]{64}$/.test(basisHash) || !mutationId || mutationId.length > 64)
      throw new Error('판단 초안 요청 오류')
    pending = {
      ...fields(request),
      expectedVersion: readInteger(request, 'expectedVersion'),
      mutationId,
      basisHash,
    }
  }
  return { ...fields(value), baseline, pending }
}
export function writeDecisionDraft(key: string, draft: DecisionDraft) {
  sessionStorage.setItem(key, JSON.stringify(draft))
}
export function clearDecisionDrafts(account: string | null, workId: string) {
  const workPrefix = prefix(account, workId)
  const keys = Array.from({ length: sessionStorage.length }, (_, index) =>
    sessionStorage.key(index),
  )
  for (const key of keys) if (key?.startsWith(workPrefix)) sessionStorage.removeItem(key)
}
