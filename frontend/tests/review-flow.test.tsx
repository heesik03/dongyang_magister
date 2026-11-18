// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ManuscriptPanel } from '../src/features/manuscript/ManuscriptPanel'
import { useWorksStore } from '../src/features/works/useWorksStore'
import type { Analysis, Work } from '../src/features/works/workTypes'
import type { ReviewReport } from '../src/features/review/reviewClient'
import { revisionSuggestionClient } from '../src/features/review/revisionSuggestionClient'

const fixture = vi.hoisted(() => ({ report: undefined as ReviewReport | undefined }))
vi.mock('../src/features/analysis/useAnalysisJobs', () => ({
  useAnalysisJobs: () => ({
    job: { id: 'job-1', status: 'SUCCEEDED', result: analysis },
    history: [],
    active: false,
    page: 0,
    totalPages: 0,
    loading: false,
    error: '',
    context: '1:target',
    refresh: vi.fn(),
  }),
}))
vi.mock('../src/features/review/useReviewReport', () => ({
  useReviewReport: () => ({ report: fixture.report, error: '', loading: false, retry: vi.fn() }),
}))
const content = '😀장면\n마법 사용\n마법 사용'
const analysis: Analysis = {
  chapterId: 'target',
  revision: 1,
  source: content,
  settingIds: ['1'],
  previousIds: ['previous'],
  reviews: [
    {
      id: 'candidate',
      type: '설정 충돌 후보',
      quote: '마법 사용',
      settingId: '1',
      evidence: '마법 불가',
      reason: '능력 변화 계기 확인',
      suggestion: '설명 보완',
      decision: '미검토',
      note: '',
    },
  ],
  createdAt: '2026-10-27T00:00:00Z',
}
const work: Work = {
  id: '1',
  source: 'server',
  title: '검토 작품',
  genre: '',
  era: '',
  description: '',
  updatedAt: '',
  settings: [
    { id: '1', title: '능력', kind: '인물', status: '확정', content: '마법 불가', version: 0 },
  ],
  chapters: [
    { id: 'previous', title: '1화', content: '이전에는 마법 불가', revision: 1 },
    { id: 'target', title: '2화', content, revision: 1 },
  ],
  analyses: [],
  events: [],
}
const report: ReviewReport = {
  legacy: false,
  stale: false,
  reviews: [
    {
      id: 'candidate',
      type: '설정 충돌 후보',
      quote: '마법 사용',
      targetSourceId: 10,
      startOffset: 11,
      endOffset: 16,
      reason: '능력 변화 계기 확인',
      suggestion: '설명 보완',
      evidences: [
        {
          sourceId: 11,
          sourceType: 'SETTING',
          sourceKey: '1',
          title: '능력',
          revision: 0,
          quote: '마법 불가',
          startOffset: 0,
          endOffset: 5,
          explanation: '확정 능력과 비교',
        },
        {
          sourceId: 12,
          sourceType: 'PREVIOUS',
          sourceKey: 'previous',
          title: '1화',
          revision: 1,
          quote: '마법 불가',
          startOffset: 5,
          endOffset: 10,
          explanation: '이전 행동과 비교',
        },
      ],
    },
  ],
}
const base = useWorksStore.getState()
beforeEach(() => {
  vi.clearAllMocks()
  fixture.report = report
  useWorksStore.setState({ ...base, works: [work] })
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  HTMLElement.prototype.scrollIntoView = vi.fn()
})
afterEach(cleanup)
function show(current = work) {
  return render(
    <MemoryRouter initialEntries={['/?view=review']}>
      <ManuscriptPanel work={current} />
    </MemoryRouter>,
  )
}

async function requestRevision() {
  const preview = vi.spyOn(revisionSuggestionClient, 'preview').mockResolvedValue({
    target: {
      chapterId: 'target',
      revision: 1,
      quote: '마법 사용',
      startOffset: 11,
      endOffset: 16,
    },
    contextHash: 'a'.repeat(64),
    before: '앞 문맥',
    after: '',
    reason: '능력 확인',
    direction: '단서 보완',
    settings: [],
  })
  const generate = vi.spyOn(revisionSuggestionClient, 'generate').mockResolvedValue('걸음을 멈췄다')
  show()
  fireEvent.click(screen.getByRole('button', { name: '보완 문장 제안받기' }))
  await screen.findByText('앞 문맥')
  expect(preview).toHaveBeenCalledOnce()
  expect(generate).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Gemini에 제안 요청' }))
  return screen.findByRole('button', { name: '원고에 적용' })
}

test('확인한 제안만 두 번째 인용 위치에 적용해 일반 저장 대상으로 만든다', async () => {
  const apply = await requestRevision()
  expect(useWorksStore.getState().works[0].chapters[1].content).toBe(content)
  fireEvent.click(apply)
  const state = useWorksStore.getState()
  expect(state.works[0].chapters[1]).toMatchObject({
    content: '😀장면\n마법 사용\n걸음을 멈췄다',
    revision: 2,
  })
  expect(state.dirtyIds).toContain(work.id)
})

test('제안 비교 중 편집 내용이 바뀌면 최신 원고를 보존한다', async () => {
  const apply = await requestRevision()
  const edited = { ...work.chapters[1], revision: 2, content: '새 원고' }
  useWorksStore.setState({ works: [{ ...work, chapters: [work.chapters[0], edited] }] })
  fireEvent.click(apply)
  await screen.findByText(/원고가 변경되어 적용할 수 없습니다/)
  expect(useWorksStore.getState().works[0].chapters[1]).toEqual(edited)
})

test.each(['호출 한도를 초과했습니다.', '서버에 연결하지 못했습니다.'])(
  '다시 제안이 실패해도 기존 제안과 원고를 보존한다 · %s',
  async (message) => {
    await requestRevision()
    vi.mocked(revisionSuggestionClient.generate).mockRejectedValueOnce(new Error(message))
    fireEvent.click(screen.getByRole('button', { name: '다시 제안' }))
    expect(await screen.findByText(message)).toBeTruthy()
    expect(screen.getByText('걸음을 멈췄다')).toBeTruthy()
    expect(useWorksStore.getState().works[0].chapters[1].content).toBe(content)
    fireEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(screen.queryByRole('button', { name: '원고에 적용' })).toBeNull()
  },
)

test('반복 인용문의 두 번째 UTF-16 위치를 선택하고 다중 근거를 표시한다', async () => {
  show()
  expect(screen.getByText('확정 능력과 비교')).toBeTruthy()
  expect(screen.getByText('이전 행동과 비교')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '원고에서 확인' }))
  const editor = await screen.findByRole('textbox', { name: '회차 원고' })
  await waitFor(() => {
    expect(editor).toHaveProperty('selectionStart', 11)
    expect(editor).toHaveProperty('selectionEnd', 16)
    expect(document.activeElement).toBe(editor)
  })
})

test('편집된 원고에서 인용 위치가 바뀌면 안내를 표시하고 원고를 덮어쓰지 않는다', async () => {
  const changed = {
    ...work,
    chapters: [work.chapters[0], { ...work.chapters[1], content: '새 원고', revision: 2 }],
  }
  show(changed)
  expect(screen.getByText(/이전 결과입니다/)).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '원고에서 확인' }))
  await screen.findByText(/원고가 수정되어 해당 인용 위치를 찾을 수 없습니다/)
  expect(screen.getByLabelText('회차 원고', { selector: 'textarea' })).toHaveProperty(
    'value',
    '새 원고',
  )
})

test('이전 회차 근거로 이동할 때 해당 회차의 원고 구간을 선택한다', async () => {
  show()
  fireEvent.click(screen.getByRole('button', { name: '근거 원문으로 이동 · 1화' }))
  const editor = await screen.findByRole('textbox', { name: '회차 원고' })
  await waitFor(() => {
    expect(editor).toHaveProperty('value', '이전에는 마법 불가')
    expect(editor).toHaveProperty('selectionStart', 5)
    expect(editor).toHaveProperty('selectionEnd', 10)
  })
})

test('위치 없는 과거 결과는 추정해서 이동하지 않는다', () => {
  fixture.report = { reviews: [], legacy: true, stale: false }
  show()
  expect(screen.getByText(/이전 형식의 결과/)).toBeTruthy()
  expect(screen.getByRole('button', { name: '원고에서 확인' })).toHaveProperty('disabled', true)
})
