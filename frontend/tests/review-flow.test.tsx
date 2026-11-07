// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ManuscriptPanel } from '../src/features/manuscript/ManuscriptPanel'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { reviewClient } from '../src/features/review/reviewClient'
import type { Analysis, Work } from '../src/features/works/workTypes'
import type { ReviewReport } from '../src/features/review/reviewClient'

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
vi.mock('../src/features/review/reviewClient', async (original) => ({
  ...(await original<typeof import('../src/features/review/reviewClient')>()),
  reviewClient: {
    source: vi.fn(),
    getOverview: vi.fn(),
    compare: vi.fn(),
  },
}))
vi.mock('../src/features/analysis/analysisClient', () => ({
  analysisClient: { snapshot: vi.fn().mockRejectedValue(new Error('과거 자료 미조회')) },
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
  summary: {
    scopeNotice: '선택한 확정 설정 1개와 이전 회차 1개만 바탕으로 검토했습니다.',
    settingCount: 1,
    previousChapterCount: 1,
    totalCandidates: 1,
    conflictCount: 1,
    supplementCount: 0,
    unreviewedCount: 1,
    issueGroupCount: 1,
    priorityItems: [
      {
        groupKey: 'magic-conflict',
        title: '봉인된 마법 능력 발현 조건',
        type: '설정 충돌 후보',
        summary: '마법 불가 설정과 현재 장면의 마법 사용 서술이 부딪힙니다.',
        candidateIds: ['candidate'],
        sourceTitles: ['능력', '1화'],
      },
    ],
  },
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
      issueGroupKey: 'magic-conflict',
      issueGroupTitle: '봉인된 마법 능력 발현 조건',
      confirmedFact: '확정 설정에는 마법 불가로 기록되어 있고 원고에서는 마법을 사용했습니다.',
      interpretation: '봉인 해제나 일시적 각성 장면으로 읽힐 수 있습니다.',
      missingInfo: '능력 제약이 풀린 계기 문장이 선택 범위 안에 없습니다.',
      options: [
        {
          title: '봉인 해제 복선 추가',
          direction: '마법 발현 직전에 제약이 흔들리는 단서를 덧붙입니다.',
          premise: '일시적 각성 전제',
          rationale: '기존 설정을 유지하면서 현재 장면을 살릴 수 있습니다.',
        },
      ],
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
    <MemoryRouter>
      <ManuscriptPanel work={current} />
    </MemoryRouter>,
  )
}

test('반복 인용문의 두 번째 UTF-16 위치를 선택하고 다중 근거를 표시한다', async () => {
  vi.mocked(reviewClient.source).mockResolvedValue({
    id: 10,
    type: 'TARGET',
    sourceKey: 'target',
    title: '2화',
    content,
    revision: 1,
  })
  show()
  expect(screen.getByText('확정 능력과 비교')).toBeTruthy()
  expect(screen.getByText('이전 행동과 비교')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '원문으로 이동' }))
  const editor = screen.getByRole('textbox', { name: '회차 원고' })
  await waitFor(() => {
    expect(editor).toHaveProperty('selectionStart', 11)
    expect(editor).toHaveProperty('selectionEnd', 16)
    expect(document.activeElement).toBe(editor)
  })
})

test('편집된 원고를 덮어쓰지 않고 분석 당시 스냅샷을 선택한다', async () => {
  vi.mocked(reviewClient.source).mockResolvedValue({
    id: 10,
    type: 'TARGET',
    sourceKey: 'target',
    title: '2화',
    content,
    revision: 1,
  })
  const changed = {
    ...work,
    chapters: [work.chapters[0], { ...work.chapters[1], content: '새 원고', revision: 2 }],
  }
  show(changed)
  fireEvent.click(screen.getByRole('button', { name: '원문으로 이동' }))
  const original = await screen.findByLabelText('분석 당시 원문')
  await waitFor(() => expect(original).toHaveProperty('selectionStart', 11))
  expect(screen.getByRole('textbox', { name: '회차 원고' })).toHaveProperty('value', '새 원고')
  expect(original).toHaveProperty('readOnly', true)
  expect(screen.getByText(/이전 결과입니다/)).toBeTruthy()
})

test('설정 근거를 키보드로 확인할 수 있는 고정 원문에 연결한다', async () => {
  vi.mocked(reviewClient.source).mockResolvedValue({
    id: 11,
    type: 'SETTING',
    sourceKey: '1',
    title: '능력',
    content: '마법 불가',
    revision: 0,
  })
  show()
  screen.getByRole('button', { name: '근거 원문으로 이동 · 능력' }).focus()
  fireEvent.click(screen.getByRole('button', { name: '근거 원문으로 이동 · 능력' }))
  const original = await screen.findByLabelText('분석 당시 원문')
  await waitFor(() => expect(document.activeElement).toBe(original))
  expect(original).toHaveProperty('selectionEnd', 5)
  fireEvent.click(screen.getByRole('button', { name: '원문 닫기' }))
  expect(document.activeElement).toBe(
    screen.getByRole('button', { name: '근거 원문으로 이동 · 능력' }),
  )
})

test('잘못된 인용 위치와 원문 조회 실패를 빈 결과로 숨기지 않는다', async () => {
  vi.mocked(reviewClient.source).mockResolvedValue({
    id: 10,
    type: 'TARGET',
    sourceKey: 'target',
    title: '2화',
    content: '다른 원문',
    revision: 1,
  })
  show()
  fireEvent.click(screen.getByRole('button', { name: '원문으로 이동' }))
  await screen.findByText(/원문과 인용 위치가 일치하지 않습니다/)
  vi.mocked(reviewClient.source).mockRejectedValue(new Error('원문 조회 실패'))
  fireEvent.click(screen.getByRole('button', { name: '원문으로 이동' }))
  await screen.findByText('원문 조회 실패')
  expect(screen.getByRole('textbox', { name: '회차 원고' })).toHaveProperty('value', content)
})

test('위치 없는 과거 결과는 추정해서 이동하지 않는다', () => {
  fixture.report = { reviews: [], legacy: true, stale: false }
  show()
  expect(screen.getByText(/이전 형식의 결과/)).toBeTruthy()
  expect(screen.getByRole('button', { name: '원문으로 이동' })).toHaveProperty('disabled', true)
  expect(reviewClient.source).not.toHaveBeenCalled()
})

test('4단계 구조화 검토, 범위 요약, 회차별 검토 현황, 수정 전후 비교를 표시한다', async () => {
  vi.mocked(reviewClient.getOverview).mockResolvedValue({
    workId: 1,
    totalChapters: 2,
    analyzedChapters: 1,
    currentRevisionChapters: 1,
    outdatedRevisionChapters: 0,
    unanalyzedChapters: 1,
    totalConflictCount: 1,
    totalSupplementCount: 0,
    totalUnreviewedCount: 1,
    aggregationBasisNote: '각 회차의 가장 최근 완료된 분석 기준으로 집계했습니다.',
    chapters: [
      {
        chapterId: 'previous',
        chapterNo: 1,
        chapterTitle: '1화',
        currentRevision: 1,
        analysisStatus: 'UNANALYZED',
        latestJobId: null,
        analyzedRevision: null,
        analyzedAt: null,
        settingCount: 0,
        previousChapterCount: 0,
        analysisRunCount: 0,
        totalCandidateCount: 0,
        conflictCount: 0,
        supplementCount: 0,
        unreviewedCount: 0,
        acceptedCount: 0,
        holdCount: 0,
        intendedCount: 0,
      },
      {
        chapterId: 'target',
        chapterNo: 2,
        chapterTitle: '2화',
        currentRevision: 1,
        analysisStatus: 'CURRENT',
        latestJobId: 'job-1',
        analyzedRevision: 1,
        analyzedAt: '2026-10-27T00:00:00Z',
        settingCount: 1,
        previousChapterCount: 1,
        analysisRunCount: 1,
        totalCandidateCount: 1,
        conflictCount: 1,
        supplementCount: 0,
        unreviewedCount: 1,
        acceptedCount: 0,
        holdCount: 0,
        intendedCount: 0,
      },
    ],
  })

  vi.mocked(reviewClient.compare).mockResolvedValue({
    currentJobId: 'job-1',
    baseJobId: 'job-0',
    hasBaseJob: true,
    chapterId: 'target',
    chapterTitle: '2화',
    currentChapterRevision: 2,
    baseChapterRevision: 1,
    currentAnalyzedAt: '2026-10-27T01:00:00Z',
    baseAnalyzedAt: '2026-10-27T00:00:00Z',
    scopeChanged: false,
    manuscriptModified: true,
    scopeComparisonNote: '동일한 참고 범위에서 수정 전·후 원고를 비교했습니다.',
    resolutionCautionNote:
      '이번 결과에서 다시 나오지 않은 후보는 자동 해결 판정이 아니며 작가 확인이 필요합니다.',
    retained: [],
    added: [],
    notReappeared: [
      {
        category: 'NOT_REAPPEARED',
        candidateId: 'old-cand-1',
        matchedPreviousCandidateId: null,
        type: '설정 충돌 후보',
        quote: '이전 마법 폭주',
        startOffset: 0,
        endOffset: 8,
        previousStartOffset: 0,
        previousEndOffset: 8,
        reason: '이전 폭주 장면 확인',
        suggestion: '문장 정리',
        sourceTitles: ['능력'],
        comparisonNote: '이번 분석 결과에서 다시 검출되지 않았습니다.',
      },
    ],
    incomparable: [],
  })

  show()

  // 범위 종합 요약과 4단계 구조화 검토 표시 확인
  expect(screen.getByText('선택 범위 종합 요약')).toBeTruthy()
  expect(
    screen.getByText('확정 설정에는 마법 불가로 기록되어 있고 원고에서는 마법을 사용했습니다.'),
  ).toBeTruthy()
  expect(screen.getByText('봉인 해제 복선 추가')).toBeTruthy()
  expect(screen.getByText(/전제: 일시적 각성 전제/)).toBeTruthy()

  // 회차별 검토 현황 시각화 및 표 전환 확인
  fireEvent.click(screen.getByRole('button', { name: '회차별 검토 현황' }))
  expect(
    await screen.findByText(/각 회차의 가장 최근 완료된 분석 기준으로 집계했습니다/),
  ).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '목록 표' }))
  expect(screen.getByText('회차별 검토 후보 및 판단 집계 표')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '2화 설정 충돌 후보 1건' }))
  expect(
    screen
      .getByRole('button', { name: '설정 충돌 후보', exact: true })
      .getAttribute('aria-pressed'),
  ).toBe('true')

  // 수정 전·후 비교 확인
  fireEvent.click(screen.getByRole('button', { name: '수정 전·후 비교' }))
  expect(
    await screen.findByText(/이번 결과에서 다시 나오지 않은 후보는 자동 해결 판정이 아니며/),
  ).toBeTruthy()
  expect(screen.getByText('이전 마법 폭주')).toBeTruthy()
})
