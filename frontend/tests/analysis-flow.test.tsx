// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ManuscriptPanel } from '../src/features/manuscript/ManuscriptPanel'
import {
  analysisClient,
  type Job,
  type JobSummary,
  type Preview,
  type InputSnapshot,
} from '../src/features/analysis/analysisClient'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { ApiError } from '../src/shared/httpClient'
import type { Work, Analysis } from '../src/features/works/workTypes'

vi.mock('../src/features/analysis/analysisClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/features/analysis/analysisClient')>()
  return {
    ...original,
    analysisClient: {
      preview: vi.fn(),
      create: vi.fn(),
      list: vi.fn(),
      get: vi.fn(),
      snapshot: vi.fn(),
      cancel: vi.fn(),
    },
  }
})
const base = useWorksStore.getState()
const work: Work = {
  id: '1',
  source: 'server',
  title: '검토 작품',
  genre: '판타지',
  era: '',
  description: '',
  updatedAt: '2026-10-26T00:00:00Z',
  version: 0,
  workspaceVersion: 0,
  settings: [
    { id: '1', title: '능력', kind: '인물', status: '확정', content: '마법 불가', version: 0 },
  ],
  chapters: [
    { id: 'previous', title: '1화', content: '이전 원고', revision: 1, version: 0 },
    { id: 'target', title: '2화', content: '검토 원고', revision: 1, version: 0 },
  ],
  analyses: [],
  events: [],
}
const input: InputSnapshot = {
  chapter: work.chapters[1],
  previous: [work.chapters[0]],
  settings: [{ ...work.settings[0], version: 0 }],
  decisions: [],
  consistency: true,
  motivation: true,
}
const preview: Preview = {
  previewToken: 'token',
  policyVersion: 'policy',
  expiresAt: '2026-10-26T00:10:00Z',
  input,
  provider: 'Google Gemini API',
  model: 'test-model',
  retentionNotice: '서버 보관 안내',
  deletionNotice: '삭제 안내',
  providerPolicyUrl: 'https://ai.google.dev/gemini-api/terms',
  request: {
    requestId: 'request-1',
    chapterId: 'target',
    chapterRevision: 1,
    workspaceVersion: 0,
    settingIds: ['1'],
    previousIds: ['previous'],
    consistency: true,
    motivation: true,
  },
}
let history: JobSummary[], currentJob: Job
function page(items: JobSummary[]) {
  return { items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}
function summary(status: string): JobSummary {
  return {
    id: 'job-1',
    chapterId: 'target',
    chapterTitle: '2화',
    status,
    createdAt: work.updatedAt,
    updatedAt: work.updatedAt,
    previewConfirmed: true,
  }
}
function show() {
  return render(
    <MemoryRouter>
      <ManuscriptPanel work={work} />
    </MemoryRouter>,
  )
}
beforeEach(() => {
  vi.resetAllMocks()
  base.reset()
  useWorksStore.setState({
    ...base,
    account: 'writer@example.com',
    works: [work],
    loadedIds: ['1'],
    flushWorkspace: vi.fn().mockResolvedValue(undefined),
  })
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  history = []
  currentJob = { id: 'job-1', status: 'QUEUED', error: null, result: null }
  vi.mocked(analysisClient.list).mockImplementation(async () => page(history))
  vi.mocked(analysisClient.get).mockImplementation(async () => currentJob)
  vi.mocked(analysisClient.snapshot).mockResolvedValue({
    jobId: 'job-1',
    input,
    legacy: false,
    stale: false,
  })
  vi.mocked(analysisClient.preview).mockResolvedValue(preview)
  vi.mocked(analysisClient.create).mockImplementation(async () => {
    history = [summary('QUEUED')]
    return currentJob
  })
})
afterEach(() => {
  cleanup()
  base.reset()
})

test('실제 전송 자료를 확인하고 동의하기 전에는 분석을 접수하지 않는다', async () => {
  show()
  fireEvent.click(screen.getByRole('button', { name: 'AI 검토', exact: true }))
  fireEvent.click(screen.getByRole('checkbox', { name: '1화', exact: true }))
  fireEvent.click(screen.getByRole('button', { name: '전송 자료 미리보기' }))
  await screen.findByRole('dialog', { name: 'AI 전송 자료 확인' })
  expect(analysisClient.preview).toHaveBeenCalledWith(
    '1',
    expect.objectContaining({ chapterId: 'target', chapterRevision: 1, previousIds: ['previous'] }),
    expect.any(AbortSignal),
  )
  expect(analysisClient.create).not.toHaveBeenCalled()
  expect(
    (screen.getByRole('button', { name: '동의하고 분석 시작' }) as HTMLButtonElement).disabled,
  ).toBe(true)
  fireEvent.click(screen.getByRole('checkbox', { name: /보관 안내를 확인/ }))
  fireEvent.click(screen.getByRole('button', { name: '동의하고 분석 시작' }))
  await waitFor(() => expect(analysisClient.create).toHaveBeenCalledTimes(1))
  expect(analysisClient.create).toHaveBeenCalledWith('1', preview, expect.any(AbortSignal))
})

test('화면 이동 뒤 진행 중 작업과 완료 결과를 서버에서 복원하고 POST를 재실행하지 않는다', async () => {
  history = [summary('RUNNING')]
  currentJob = { ...currentJob, status: 'RUNNING' }
  const first = show()
  await screen.findByRole('button', { name: 'AI 검토 취소' })
  first.unmount()
  const result: Analysis = {
    chapterId: 'target',
    revision: 1,
    source: '검토 원고',
    settingIds: ['1'],
    previousIds: ['previous'],
    createdAt: work.updatedAt,
    reviews: [
      {
        id: 'review-1',
        type: '설정 충돌 후보',
        quote: '검토 원고',
        settingId: '1',
        evidence: '마법 불가',
        reason: '연결 확인',
        suggestion: '설명 보완',
        decision: '미검토',
        note: '',
      },
    ],
  }
  currentJob = { ...currentJob, status: 'SUCCEEDED', result }
  history = [summary('SUCCEEDED')]
  show()
  await screen.findByText('연결 확인')
  expect(analysisClient.create).not.toHaveBeenCalled()
  expect(useWorksStore.getState().flushWorkspace).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: '분석 당시 전송 자료' })).toBeTruthy()
})

test('실패한 검토의 선택 범위를 복원하고 다시 미리보기를 거친다', async () => {
  history = [summary('FAILED')]
  currentJob = { ...currentJob, status: 'FAILED', error: '공급자 응답 실패' }
  show()
  fireEvent.click(await screen.findByRole('button', { name: '다시 분석' }))
  await screen.findByRole('dialog', { name: '분석 범위 설정' })
  expect(
    (screen.getByRole('checkbox', { name: '1화', exact: true }) as HTMLInputElement).checked,
  ).toBe(true)
  expect(analysisClient.create).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: '전송 자료 미리보기' }))
  await screen.findByRole('dialog', { name: 'AI 전송 자료 확인' })
  expect(analysisClient.preview).toHaveBeenCalledTimes(1)
})

test('이력 조회 실패를 빈 결과로 표시하지 않는다', async () => {
  vi.mocked(analysisClient.list).mockRejectedValue(new Error('이력 연결 실패'))
  show()
  await screen.findByText('이력 연결 실패')
  expect(screen.queryByText('아직 이 회차의 분석 기록이 없습니다.')).toBeNull()
  expect(screen.getByRole('button', { name: '작업 상태 다시 확인' })).toBeTruthy()
})

test('미리보기 이후 자료가 바뀌면 재확인하며 자동으로 다시 접수하지 않는다', async () => {
  vi.mocked(analysisClient.create).mockRejectedValue(new ApiError('자료가 바뀌었습니다.', 409))
  show()
  fireEvent.click(screen.getByRole('button', { name: 'AI 검토', exact: true }))
  fireEvent.click(screen.getByRole('button', { name: '전송 자료 미리보기' }))
  await screen.findByRole('dialog', { name: 'AI 전송 자료 확인' })
  fireEvent.click(screen.getByRole('checkbox', { name: /보관 안내를 확인/ }))
  fireEvent.click(screen.getByRole('button', { name: '동의하고 분석 시작' }))
  await screen.findByRole('dialog', { name: '분석 범위 설정' })
  expect(analysisClient.create).toHaveBeenCalledTimes(1)
})

test('화면을 떠난 뒤 도착한 폴링 응답을 적용하거나 취소 POST를 보내지 않는다', async () => {
  history = [summary('RUNNING')]
  let finish: (job: Job) => void = () => {}
  vi.mocked(analysisClient.get).mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve
      }),
  )
  const view = show()
  await waitFor(() => expect(analysisClient.get).toHaveBeenCalled())
  const signal = vi.mocked(analysisClient.get).mock.calls[0][2]
  view.unmount()
  expect(signal?.aborted).toBe(true)
  await act(async () => finish(currentJob))
  expect(analysisClient.cancel).not.toHaveBeenCalled()
  expect(analysisClient.create).not.toHaveBeenCalled()
})
