// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { TimelinePanel } from '../src/features/timeline/TimelinePanel'
import { ExtractionFlow } from '../src/features/timeline/ExtractionFlow'
import {
  eventLocation,
  orderedEvents,
  sourceLines,
} from '../src/features/timeline/timelineEvidence'
import {
  extractionClient,
  type EventCandidate,
  type ExtractionJob,
} from '../src/features/timeline/extractionClient'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { ApiError } from '../src/shared/httpClient'
import type { Work, StoryEvent } from '../src/features/works/workTypes'

const base = useWorksStore.getState()
const text = '린은 떠났다.\n\n린은 떠났다.'
const work: Work = {
  id: '1',
  source: 'server',
  title: '작품',
  genre: '판타지',
  era: '',
  description: '',
  updatedAt: '',
  workspaceVersion: 7,
  chapters: [{ id: 'c', title: '1화', content: text, revision: 1, version: 0 }],
  settings: [],
  analyses: [],
  events: [],
}
const candidate: EventCandidate = {
  id: 'candidate',
  title: '왕도로 출발',
  description: '린의 여행',
  time: '사흘 뒤',
  characters: '린',
  chapterId: 'c',
  quote: '린은 떠났다.',
  sourceRevision: 1,
  startOffset: text.lastIndexOf('린은 떠났다.'),
  endOffset: text.length,
  sequence: 1,
  timeStatus: 'UNRESOLVED',
  decision: 'HOLD',
}
const job: ExtractionJob = {
  id: 'job',
  status: 'SUCCEEDED',
  version: 2,
  createdAt: '2026-11-02T00:00:00Z',
  error: '',
  candidates: [candidate],
}
const page = (items: ExtractionJob[]) => ({
  items,
  page: 0,
  size: 5,
  totalPages: 1,
  totalElements: items.length,
})
beforeEach(() => {
  useWorksStore.setState({
    ...base,
    account: 'writer@example.com',
    works: [work],
    flushWorkspace: vi.fn().mockResolvedValue(undefined),
    reloadWorkspace: vi.fn().mockResolvedValue(undefined),
    updateWork: vi.fn(),
  })
  sessionStorage.clear()
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  vi.spyOn(extractionClient, 'list').mockResolvedValue(page([]))
})
afterEach(() => {
  cleanup()
  base.reset()
  vi.restoreAllMocks()
})

test('사건은 시간 표현의 이름순 대신 작가가 지정한 순서로 정렬한다', () => {
  const first: StoryEvent = { ...candidate, id: 'first', time: '깊은 밤', sequence: 1 }
  const second: StoryEvent = { ...candidate, id: 'second', time: '아침', sequence: 0 }
  expect(orderedEvents([first, second]).map((event) => event.id)).toEqual(['second', 'first'])
})
test('반복 문장과 과거 버전은 정확한 위치와 원고 버전으로 구분한다', () => {
  expect(sourceLines(text).map((line) => line.start)).toEqual([
    0,
    text.lastIndexOf(candidate.quote),
  ])
  expect(eventLocation(candidate, work.chapters[0])?.start).toBe(text.lastIndexOf(candidate.quote))
  expect(eventLocation({ ...candidate, sourceRevision: 0 }, work.chapters[0])).toBeNull()
  expect(
    eventLocation({ ...candidate, startOffset: null, endOffset: null }, work.chapters[0]),
  ).toBeNull()
})
test('수동 사건 등록은 반복된 두 번째 문장의 위치를 저장한다', () => {
  render(
    <MemoryRouter>
      <TimelinePanel work={work} />
    </MemoryRouter>,
  )
  fireEvent.click(screen.getByRole('button', { name: '사건 추가', exact: true }))
  fireEvent.change(screen.getByLabelText('사건명'), { target: { value: '두 번째 출발' } })
  fireEvent.change(screen.getAllByLabelText('근거 회차')[1], { target: { value: 'c' } })
  fireEvent.change(screen.getByLabelText('근거 문장'), {
    target: { value: String(candidate.startOffset) },
  })
  fireEvent.click(screen.getByRole('button', { name: '사건 저장' }))
  expect(useWorksStore.getState().updateWork).toHaveBeenCalledWith(
    expect.objectContaining({
      events: [
        expect.objectContaining({
          title: '두 번째 출발',
          startOffset: candidate.startOffset,
          endOffset: candidate.endOffset,
          sourceRevision: 1,
          timeStatus: 'UNRESOLVED',
        }),
      ],
    }),
  )
})
test('원고가 바뀐 사건의 제목을 수정해도 추출 당시 근거를 보존한다', () => {
  const event = { ...candidate, extractionId: 'job' },
    changed = {
      ...work,
      chapters: [{ ...work.chapters[0], content: '새 원고', revision: 2 }],
      events: [event],
    }
  render(
    <MemoryRouter>
      <TimelinePanel work={changed} />
    </MemoryRouter>,
  )
  fireEvent.click(screen.getByRole('button', { name: '순서 정하기' }))
  fireEvent.change(screen.getByLabelText('사건명'), { target: { value: '수정된 사건명' } })
  fireEvent.click(screen.getByRole('button', { name: '사건 저장' }))
  expect(useWorksStore.getState().updateWork).toHaveBeenCalledWith(
    expect.objectContaining({
      events: [
        expect.objectContaining({
          title: '수정된 사건명',
          quote: candidate.quote,
          sourceRevision: 1,
          startOffset: candidate.startOffset,
          extractionId: 'job',
        }),
      ],
    }),
  )
})

test('시점 없이 기준 사건 앞에 배치하고 결과가 없는 회차의 필터를 해제한다', () => {
  const anchor: StoryEvent = { ...candidate, id: 'anchor', timeStatus: 'CONFIRMED' }
  const emptyChapter = { id: 'empty', title: '2화', content: '', revision: 1 }
  render(
    <MemoryRouter>
      <TimelinePanel
        work={{ ...work, chapters: [...work.chapters, emptyChapter], events: [anchor] }}
      />
    </MemoryRouter>,
  )
  fireEvent.change(screen.getByLabelText('근거 회차'), { target: { value: emptyChapter.id } })
  fireEvent.click(screen.getByRole('button', { name: '회차 필터 해제' }))
  fireEvent.click(screen.getByRole('button', { name: '사건 추가', exact: true }))
  fireEvent.change(screen.getByLabelText('사건명'), { target: { value: '이전 사건' } })
  fireEvent.change(screen.getByLabelText('이야기 속 위치'), { target: { value: 'before' } })
  fireEvent.change(screen.getByLabelText('기준 사건'), { target: { value: anchor.id } })
  fireEvent.click(screen.getByRole('button', { name: '사건 저장' }))
  expect(useWorksStore.getState().updateWork).toHaveBeenCalledWith(
    expect.objectContaining({
      events: [
        expect.objectContaining({
          title: '이전 사건',
          time: '',
          timeStatus: 'CONFIRMED',
          sequence: 0,
        }),
        expect.objectContaining({ id: anchor.id, sequence: 1 }),
      ],
    }),
  )
})
test('전송할 원고를 확인하고 동의한 뒤에만 추출을 접수한다', async () => {
  vi.spyOn(extractionClient, 'preview').mockResolvedValue({
    sources: [{ ...work.chapters[0], chapterNo: 1 }],
    token: 'signed',
    model: 'synthetic-model',
  })
  const create = vi
    .spyOn(extractionClient, 'create')
    .mockResolvedValue({ ...job, status: 'QUEUED', candidates: [] })
  render(<ExtractionFlow work={work} />)
  fireEvent.click(screen.getByRole('button', { name: '원고에서 사건 찾기' }))
  fireEvent.click(screen.getByRole('button', { name: '전송할 원고 확인' }))
  await screen.findByRole('dialog', { name: '사건 추출 전송 확인' })
  expect(create).not.toHaveBeenCalled()
  expect(
    (screen.getByRole('button', { name: '사건 후보 추출' }) as HTMLButtonElement).disabled,
  ).toBe(true)
  fireEvent.click(
    screen.getByRole('checkbox', { name: '선택한 원고의 외부 AI 전송에 동의합니다.' }),
  )
  fireEvent.click(screen.getByRole('button', { name: '사건 후보 추출' }))
  await waitFor(() => expect(create).toHaveBeenCalledOnce())
  expect(create.mock.calls[0][1].sources[0].content).toBe(text)
})
test('후보 저장이 충돌하면 수정 내용과 보류 상태를 유지한다', async () => {
  vi.mocked(extractionClient.list).mockResolvedValue(page([job]))
  const save = vi.spyOn(extractionClient, 'save').mockRejectedValue(new ApiError('충돌', 409))
  render(<ExtractionFlow work={work} />)
  fireEvent.click(await screen.findByRole('button', { name: '사건 후보 확인' }))
  fireEvent.change(screen.getByLabelText('사건명'), { target: { value: '작가의 수정' } })
  fireEvent.click(screen.getByRole('button', { name: '후보 저장' }))
  await screen.findByRole('alert')
  expect((screen.getByLabelText('사건명') as HTMLInputElement).value).toBe('작가의 수정')
  expect(save.mock.calls[0][2][0].decision).toBe('HOLD')
  expect(sessionStorage.getItem('magister.event-draft:writer@example.com:1:job')).toContain(
    '작가의 수정',
  )
})
test('재방문 시 새 서버 버전으로 오래된 초안을 덮어쓰거나 자동 저장하지 않는다', async () => {
  sessionStorage.setItem(
    'magister.event-draft:writer@example.com:1:job',
    JSON.stringify({ version: 1, candidates: [{ ...candidate, title: '복구 초안' }] }),
  )
  vi.mocked(extractionClient.list).mockResolvedValue(page([job]))
  const save = vi.spyOn(extractionClient, 'save').mockRejectedValue(new ApiError('충돌', 409))
  render(<ExtractionFlow work={work} />)
  fireEvent.click(await screen.findByRole('button', { name: '사건 후보 확인' }))
  expect((screen.getByLabelText('사건명') as HTMLInputElement).value).toBe('복구 초안')
  expect(save).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: '후보 저장' }))
  await waitFor(() => expect(save).toHaveBeenCalledOnce())
  expect(save.mock.calls[0][1].version).toBe(1)
})
test('확정 전에 후보를 저장하고 현재 Workspace 버전으로 확정한다', async () => {
  vi.mocked(extractionClient.list).mockResolvedValue(page([job]))
  vi.spyOn(extractionClient, 'save').mockResolvedValue({
    ...job,
    version: 3,
    candidates: [{ ...candidate, decision: 'INCLUDE' }],
  })
  const confirm = vi.spyOn(extractionClient, 'confirm').mockResolvedValue(undefined)
  render(<ExtractionFlow work={work} />)
  fireEvent.click(await screen.findByRole('button', { name: '사건 후보 확인' }))
  fireEvent.click(screen.getByRole('checkbox', { name: candidate.title + ' 추가 선택' }))
  expect(screen.getByLabelText('작가 선택')).toHaveProperty('value', 'INCLUDE')
  fireEvent.click(screen.getByRole('button', { name: '선택한 사건 확정' }))
  await waitFor(() => expect(confirm).toHaveBeenCalledOnce())
  expect(confirm.mock.calls[0][1].version).toBe(3)
  expect(confirm.mock.calls[0][2]).toBe(7)
  expect(useWorksStore.getState().reloadWorkspace).toHaveBeenCalledWith('1')
})

test('확정 충돌 뒤 후보를 유지하면서 최신 타임라인 버전으로 재시도한다', async () => {
  vi.mocked(extractionClient.list).mockResolvedValue(page([job]))
  vi.spyOn(extractionClient, 'save').mockResolvedValue({
    ...job,
    version: 3,
    candidates: [{ ...candidate, title: '유지할 후보', decision: 'INCLUDE' }],
  })
  const confirm = vi
    .spyOn(extractionClient, 'confirm')
    .mockRejectedValueOnce(new ApiError('충돌', 409))
    .mockResolvedValueOnce(undefined)
  useWorksStore.setState({
    reloadWorkspace: vi.fn().mockImplementation(async () => {
      useWorksStore.setState({ works: [{ ...work, workspaceVersion: 8 }] })
    }),
  })
  render(<ExtractionFlow work={work} />)
  fireEvent.click(await screen.findByRole('button', { name: '사건 후보 확인' }))
  fireEvent.change(screen.getByLabelText('사건명'), { target: { value: '유지할 후보' } })
  fireEvent.change(screen.getByLabelText('작가 선택'), { target: { value: 'INCLUDE' } })
  fireEvent.click(screen.getByRole('button', { name: '선택한 사건 확정' }))
  await screen.findByRole('alert')
  fireEvent.click(screen.getByRole('button', { name: '타임라인 최신 상태 확인' }))
  await screen.findByText('최신 타임라인을 불러왔습니다. 후보 입력은 유지됩니다.')
  expect((screen.getByLabelText('사건명') as HTMLInputElement).value).toBe('유지할 후보')
  fireEvent.click(screen.getByRole('button', { name: '선택한 사건 확정' }))
  await waitFor(() => expect(confirm).toHaveBeenCalledTimes(2))
  expect(confirm.mock.calls[0][2]).toBe(7)
  expect(confirm.mock.calls[1][2]).toBe(8)
})
test('미저장 입력이 있으면 최신 타임라인 조회로 덮어쓰지 않는다', async () => {
  vi.mocked(extractionClient.list).mockResolvedValue(page([job]))
  useWorksStore.setState({ dirtyIds: ['1'] })
  render(<ExtractionFlow work={work} />)
  fireEvent.click(await screen.findByRole('button', { name: '사건 후보 확인' }))
  fireEvent.click(screen.getByRole('button', { name: '타임라인 최신 상태 확인' }))
  await screen.findByRole('alert')
  expect(useWorksStore.getState().reloadWorkspace).not.toHaveBeenCalled()
})

test('이야기 순번과 원고 순서를 구분하고 회차를 필터링해도 순번을 유지한다', () => {
  const flowWork: Work = {
    ...work,
    chapters: [
      { id: 'ch-1', title: '1화 현재의 항구', content: '현재 장면', revision: 1 },
      { id: 'ch-2', title: '2화 과거의 맹세', content: '회상 장면', revision: 1 },
    ],
    events: [
      {
        id: 'ev-1',
        title: '항구 도착',
        description: '현재 도착',
        time: '10년 뒤 겨울',
        characters: '린',
        chapterId: 'ch-1',
        quote: '현재 장면',
        sequence: 2,
        timeStatus: 'CONFIRMED',
      },
      {
        id: 'ev-2',
        title: '어린 시절의 서약',
        description: '과거 회상',
        time: '10년 전 봄',
        characters: '린',
        chapterId: 'ch-2',
        quote: '회상 장면',
        sequence: 1,
        timeStatus: 'CONFIRMED',
      },
      {
        id: 'ev-3',
        title: '수수께끼의 편지',
        description: '시점 미정',
        time: '',
        characters: '린',
        chapterId: 'ch-2',
        quote: '회상 장면',
        sequence: 3,
        timeStatus: 'UNRESOLVED',
      },
    ],
  }

  render(
    <MemoryRouter>
      <TimelinePanel work={flowWork} />
    </MemoryRouter>,
  )

  expect(document.querySelector('.timeline-rows button strong')?.textContent).toBe(
    '어린 시절의 서약',
  )
  fireEvent.click(screen.getByText('원고에 나온 순서와 비교'))
  expect(document.querySelector('.timeline-comparison-list strong')?.textContent).toBe('항구 도착')
  expect(screen.getByText('앞서 일어난 사건')).toBeTruthy()
  expect(screen.getByText('이야기 속 순서 미정')).toBeTruthy()
  fireEvent.change(screen.getByLabelText('근거 회차'), { target: { value: 'ch-1' } })
  expect(document.querySelector('.timeline-rows button')?.textContent).toContain('2번째')
})
