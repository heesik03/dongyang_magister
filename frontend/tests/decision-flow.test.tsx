// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { DecisionEditor } from '../src/features/review/DecisionEditor'
import { DecisionLedger } from '../src/features/review/DecisionLedger'
import { decisionClient, type SavedDecision } from '../src/features/review/decisionClient'
import type { LinkedReview } from '../src/features/review/reviewClient'
import type { Work } from '../src/features/works/workTypes'

vi.mock('../src/features/review/decisionClient', () => ({
  decisionClient: { get: vi.fn(), save: vi.fn(), history: vi.fn(), list: vi.fn() },
}))
const review: LinkedReview = {
  id: 'review',
  type: '설정 충돌 후보',
  quote: '마법 사용',
  targetSourceId: 1,
  startOffset: 0,
  endOffset: 5,
  reason: '계기 확인',
  suggestion: '설명 보완',
  evidences: [],
}
const work: Work = {
  id: '1',
  title: '작품',
  genre: '',
  era: '',
  description: '',
  updatedAt: '',
  settings: [],
  analyses: [],
  events: [],
  chapters: [
    { id: 'c1', title: '1화', content: '마법 사용', revision: 1 },
    { id: 'c2', title: '2화', content: '마법 사용', revision: 1 },
    { id: 'c3', title: '3화', content: '마법 사용', revision: 1 },
  ],
}
const initial: SavedDecision = {
  id: null,
  reviewId: 'review',
  quote: '마법 사용',
  version: 0,
  status: '미검토',
  reason: '',
  basisHash: 'a'.repeat(64),
  stale: false,
  canReconfirm: true,
  createdAt: null,
  scopes: [],
}
const onSaved = vi.fn(),
  onClose = vi.fn()
beforeEach(() => {
  vi.resetAllMocks()
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  vi.mocked(decisionClient.get).mockResolvedValue(initial)
  vi.mocked(decisionClient.save).mockResolvedValue({
    ...initial,
    id: 1,
    version: 1,
    status: '의도된 설정',
  })
})
afterEach(cleanup)
async function show() {
  render(
    <DecisionEditor
      work={work}
      review={review}
      initialStatus="의도된 설정"
      onSaved={onSaved}
      onClose={onClose}
    />,
  )
  await screen.findByLabelText('의도와 판단 사유')
}
function fill() {
  fireEvent.change(screen.getByLabelText('의도와 판단 사유'), {
    target: { value: '의도한 능력 변화' },
  })
  fireEvent.change(screen.getByLabelText('적용 조건'), {
    target: { value: '봉인 해제 후 주인공만' },
  })
}

test('의도된 설정에 사유·범위를 받고 회차 범위 계약으로 저장한다', async () => {
  await show()
  expect(screen.getByLabelText('의도와 판단 사유')).toHaveProperty('required', true)
  expect(screen.getByLabelText('적용 조건')).toHaveProperty('required', true)
  fill()
  fireEvent.change(screen.getByLabelText('범위 유형'), { target: { value: 'CHAPTER_RANGE' } })
  fireEvent.change(screen.getByLabelText('시작 회차'), { target: { value: 'c2' } })
  fireEvent.change(screen.getByLabelText('종료 회차'), { target: { value: 'c3' } })
  fireEvent.click(screen.getByRole('button', { name: '판단 저장', exact: true }))
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
  expect(decisionClient.save).toHaveBeenCalledWith(
    '1',
    'review',
    expect.objectContaining({
      status: '의도된 설정',
      reason: '의도한 능력 변화',
      expectedVersion: 0,
      scopes: [
        {
          scopeType: 'CHAPTER_RANGE',
          fromChapterId: 'c2',
          toChapterId: 'c3',
          sceneSourceId: null,
          startOffset: null,
          endOffset: null,
          conditionText: '봉인 해제 후 주인공만',
        },
      ],
    }),
  )
})

test('응답 유실 재시도는 같은 mutation을 유지하고 작성 내용이 남는다', async () => {
  vi.mocked(decisionClient.save).mockRejectedValueOnce(new Error('네트워크 오류'))
  await show()
  fill()
  fireEvent.click(screen.getByRole('button', { name: '판단 저장', exact: true }))
  await screen.findByText('네트워크 오류')
  expect(screen.getByLabelText('의도와 판단 사유')).toHaveProperty('value', '의도한 능력 변화')
  fireEvent.click(screen.getByRole('button', { name: '판단 저장', exact: true }))
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
  expect(vi.mocked(decisionClient.save).mock.calls[0][2]).toEqual(
    vi.mocked(decisionClient.save).mock.calls[1][2],
  )
})

test('다른 탭 충돌을 비교하고 초안을 유지한 채 최신 버전으로 다시 준비한다', async () => {
  vi.mocked(decisionClient.save).mockRejectedValueOnce(new Error('다른 탭에서 판단 변경'))
  await show()
  fill()
  fireEvent.click(screen.getByRole('button', { name: '판단 저장', exact: true }))
  await screen.findByText('다른 탭에서 판단 변경')
  vi.mocked(decisionClient.get).mockResolvedValue({
    ...initial,
    id: 2,
    version: 2,
    status: '보류',
    reason: '다른 탭의 사유',
  })
  fireEvent.click(screen.getByRole('button', { name: '최신 판단 비교' }))
  await screen.findByText('다른 탭의 사유')
  expect(screen.getByLabelText('의도와 판단 사유')).toHaveProperty('value', '의도한 능력 변화')
  fireEvent.click(
    screen.getByRole('button', { name: '작성 내용 유지하고 최신 버전을 기준으로 준비' }),
  )
  fireEvent.click(screen.getByRole('button', { name: '판단 저장', exact: true }))
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
  expect(vi.mocked(decisionClient.save).mock.calls[1][2].expectedVersion).toBe(2)
  expect(vi.mocked(decisionClient.save).mock.calls[1][2].reason).toBe('의도한 능력 변화')
})

test('변경된 근거는 명시적으로 재확인하고 인용이 달라졌으면 철회만 허용한다', async () => {
  vi.mocked(decisionClient.get).mockResolvedValue({ ...initial, stale: true })
  await show()
  fill()
  expect(screen.getByRole('button', { name: '판단 저장', exact: true })).toHaveProperty(
    'disabled',
    true,
  )
  fireEvent.click(
    screen.getByRole('checkbox', { name: '현재 근거를 다시 확인했으며 작성한 판단을 적용합니다.' }),
  )
  expect(screen.getByRole('button', { name: '판단 저장', exact: true })).toHaveProperty(
    'disabled',
    false,
  )
  cleanup()
  vi.mocked(decisionClient.get).mockResolvedValue({
    ...initial,
    id: 1,
    version: 1,
    stale: true,
    canReconfirm: false,
  })
  await show()
  expect(screen.getByRole('button', { name: '판단 저장', exact: true })).toHaveProperty(
    'disabled',
    true,
  )
  fireEvent.change(screen.getByLabelText('판단 상태'), { target: { value: '미검토' } })
  fireEvent.click(screen.getByRole('button', { name: '판단 철회 저장' }))
  await waitFor(() => expect(decisionClient.save).toHaveBeenCalled())
  expect(vi.mocked(decisionClient.save).mock.calls[0][2]).toMatchObject({
    status: '미검토',
    expectedVersion: 1,
    scopes: [],
  })
})

test('저장 이력과 작품 전체의 최신 판단을 별도로 다시 확인한다', async () => {
  vi.mocked(decisionClient.history).mockResolvedValue({
    items: [{ ...initial, id: 1, version: 1, status: '보류', reason: '추가 확인' }],
    page: 0,
    size: 20,
    totalPages: 1,
    totalElements: 1,
  })
  await show()
  fireEvent.click(screen.getByRole('button', { name: '변경 이력 보기' }))
  await screen.findByText('버전 1 · 보류')
  cleanup()
  vi.mocked(decisionClient.list).mockResolvedValue({
    items: [
      {
        ...initial,
        id: 2,
        version: 2,
        status: '의도된 설정',
        reason: '이전 회차의 반전',
        stale: true,
      },
    ],
    page: 0,
    size: 20,
    totalPages: 1,
    totalElements: 1,
  })
  render(<DecisionLedger workId="1" onClose={onClose} />)
  await screen.findByText('이전 회차의 반전')
  expect(screen.getByText(/근거 변경, 재확인 필요/)).toBeTruthy()
  fireEvent.change(screen.getByLabelText('판단 필터'), { target: { value: '의도된 설정' } })
  await waitFor(() =>
    expect(decisionClient.list).toHaveBeenLastCalledWith(
      '1',
      0,
      '의도된 설정',
      expect.any(AbortSignal),
    ),
  )
})
