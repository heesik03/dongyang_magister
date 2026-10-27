// @vitest-environment jsdom
import { beforeEach, expect, test } from 'vitest'
import {
  decisionDraftKey,
  readDecisionDraft,
  clearDecisionDrafts,
  writeDecisionDraft,
  type DecisionDraft,
} from '../src/features/review/decisionDraftStorage'
beforeEach(() => sessionStorage.clear())
const draft: DecisionDraft = {
  baseline: {
    id: null,
    reviewId: 'review',
    quote: '합성 원고',
    version: 0,
    status: '미검토',
    reason: '',
    basisHash: 'a'.repeat(64),
    stale: false,
    canReconfirm: true,
    createdAt: null,
    scopes: [],
  },
  status: '의도된 설정',
  reason: '작성 중',
  scopes: [
    {
      scopeType: 'CHAPTER_RANGE',
      fromChapterId: '',
      toChapterId: null,
      sceneSourceId: null,
      startOffset: null,
      endOffset: null,
      conditionText: '',
    },
  ],
  reconfirm: false,
  pending: null,
}
test('계정·작품별로 분리하며 미완성 범위도 복구하고 삭제는 해당 작품에만 적용한다', () => {
  const first = decisionDraftKey('first@example.com', '1', 'review')
  const otherAccount = decisionDraftKey('second@example.com', '1', 'review')
  const otherWork = decisionDraftKey('first@example.com', '2', 'review')
  writeDecisionDraft(first, draft)
  writeDecisionDraft(otherAccount, { ...draft, reason: '다른 계정' })
  writeDecisionDraft(otherWork, { ...draft, reason: '다른 작품' })
  expect(readDecisionDraft(first)).toEqual(draft)
  clearDecisionDrafts('first@example.com', '1')
  expect(readDecisionDraft(first)).toBeNull()
  expect(readDecisionDraft(otherAccount)?.reason).toBe('다른 계정')
  expect(readDecisionDraft(otherWork)?.reason).toBe('다른 작품')
})
