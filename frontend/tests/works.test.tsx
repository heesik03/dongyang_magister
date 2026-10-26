// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { workspaceClient } from '../src/features/workspace/workspaceClient'
import { chapterClient, type SavedChapter } from '../src/features/manuscript/chapterClient'
import { workClient } from '../src/features/works/workClient'
import { settingClient } from '../src/features/settings/settingClient'
import { parseWork } from '../src/features/works/workResponse'
import { parseSetting } from '../src/features/settings/settingResponse'
import { createSampleWorks } from './fixtures/sampleWorks'
import type { Work } from '../src/features/works/workTypes'
import { ApiError } from '../src/shared/httpClient'

vi.mock('../src/features/works/workClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/features/works/workClient')>()
  return {
    ...original,
    workClient: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn() },
  }
})
vi.mock('../src/features/settings/settingClient', () => ({
  settingClient: { list: vi.fn(), listAll: vi.fn(), create: vi.fn(), update: vi.fn() },
}))
vi.mock('../src/features/workspace/workspaceClient', () => ({
  workspaceClient: { get: vi.fn(), save: vi.fn() },
}))
vi.mock('../src/features/manuscript/chapterClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/features/manuscript/chapterClient')>()
  return {
    ...original,
    chapterClient: {
      listAll: vi.fn(),
      get: vi.fn(),
      save: vi.fn(),
      reset: vi.fn(),
      rebase: vi.fn(),
    },
  }
})
let savedChapters: SavedChapter[] = []
const remote: Work = {
  id: '1',
  title: '서버 작품',
  genre: '판타지',
  era: '',
  description: '',
  updatedAt: '2026-10-25T00:00:00Z',
  source: 'server',
  version: 0,
  settings: [],
  chapters: [],
  analyses: [],
  events: [],
}
const page = { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }
beforeEach(() => {
  useWorksStore.getState().reset()
  localStorage.clear()
  vi.resetAllMocks()
  savedChapters = []
  vi.mocked(chapterClient.listAll).mockImplementation(async () => savedChapters)
  vi.mocked(chapterClient.save).mockImplementation(async (_workId, chapter) => {
    const saved: SavedChapter = {
      ...chapter,
      chapterNo: chapter.chapterNo ?? savedChapters.length + 1,
      version: chapter.version === undefined ? 0 : chapter.version + 1,
      revision: chapter.version === undefined ? 1 : chapter.version + 2,
      updatedAt: '2026-10-26T00:00:00Z',
      workVersion: (chapter.version ?? 0) + 1,
    }
    savedChapters = [...savedChapters.filter((item) => item.id !== saved.id), saved]
    return saved
  })
  vi.mocked(workClient.get).mockResolvedValue(remote)
  vi.mocked(settingClient.list).mockResolvedValue({ items: [], hasNext: false, nextCursor: null })
  vi.mocked(settingClient.listAll).mockResolvedValue([])
  vi.mocked(workspaceClient.get).mockResolvedValue({
    version: 0,
    workVersion: 0,
    updatedAt: '2026-10-25T00:00:00Z',
    content: { chapters: [], analyses: [], events: [] },
  })
  vi.mocked(workspaceClient.save).mockImplementation(async (_id, version, _mutation, content) => ({
    version: version + 1,
    workVersion: version + 1,
    updatedAt: '2026-10-25T00:00:00Z',
    content,
  }))
  useWorksStore.getState().initialize('writer@example.com')
})

test('브라우저 작품을 자동 업로드하거나 원본을 변경하지 않는다', () => {
  const original = JSON.stringify(createSampleWorks())
  localStorage.setItem('magister.works.v1:writer@example.com', original)
  useWorksStore.getState().initialize('writer@example.com')
  expect(useWorksStore.getState().works.length).toBeGreaterThan(0)
  expect(useWorksStore.getState().works.every((work) => work.source === 'local')).toBe(true)
  expect(localStorage.getItem('magister.works.v1:writer@example.com')).toBe(original)
  expect(workClient.create).not.toHaveBeenCalled()
})

test('서버 작품 원고를 재접속 후 복원하고 서버 설정을 로컬 저장으로 덮어쓰지 않는다', async () => {
  await useWorksStore.getState().loadWork('1')
  const setting = {
    id: '2',
    title: '능력',
    kind: '인물' as const,
    status: '확정' as const,
    content: '마법 불가',
    version: 0,
  }
  vi.mocked(settingClient.create).mockResolvedValue(setting)
  await useWorksStore.getState().saveSetting(remote, setting)
  useWorksStore.getState().updateWork({
    ...remote,
    title: '오래된 제목',
    chapters: [{ id: 'chapter', title: '1화', content: '새 원고', revision: 1 }],
  })
  expect(useWorksStore.getState().works[0].title).toBe('서버 작품')
  expect(useWorksStore.getState().works[0].settings).toEqual([setting])
  await useWorksStore.getState().flushWorkspace('1')
  const stored = useWorksStore.getState().works[0]
  vi.mocked(workspaceClient.get).mockResolvedValue({
    version: 1,
    workVersion: 1,
    updatedAt: '2026-10-25T00:00:00Z',
    content: { chapters: stored.chapters, analyses: stored.analyses, events: stored.events },
  })
  useWorksStore.getState().initialize('writer@example.com')
  await useWorksStore.getState().loadWork('1')
  expect(useWorksStore.getState().works[0].chapters[0].content).toBe('새 원고')
})

test('작품 조회 실패는 빈 목록으로 바꾸지 않고 오류로 표시한다', async () => {
  vi.mocked(workClient.list).mockRejectedValue(new Error('서버 연결 실패'))
  await useWorksStore.getState().loadWorks()
  expect(useWorksStore.getState().listError).toBe('서버 연결 실패')
})

test('계정 변경 전에 시작한 생성 응답이 다른 계정에 나타나지 않는다', async () => {
  let finish: (work: Work) => void = () => {}
  vi.mocked(workClient.create).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const pending = useWorksStore.getState().createWork(remote)
  const rejected = expect(pending).rejects.toThrow('로그인 정보가 변경')
  useWorksStore.getState().initialize('other@example.com')
  finish(remote)
  await rejected
  expect(useWorksStore.getState().works).toEqual([])
})

test('버전 충돌 시 화면의 기존 설정을 변경하지 않는다', async () => {
  await useWorksStore.getState().loadWork('1')
  vi.mocked(settingClient.create).mockRejectedValue(new Error('버전 충돌'))
  await expect(
    useWorksStore
      .getState()
      .saveSetting(remote, { title: '설정', kind: '인물', status: '확정', content: '내용' }),
  ).rejects.toThrow('버전 충돌')
  expect(useWorksStore.getState().works[0].settings).toEqual([])
})

test('서버 저장 실패 시 원고를 메모리에 보존한다', async () => {
  await useWorksStore.getState().loadWork('1')
  vi.mocked(chapterClient.save).mockRejectedValue(new Error('서버 연결 실패'))
  useWorksStore.getState().updateWork({
    ...remote,
    chapters: [{ id: '1', title: '1화', content: '보존할 내용', revision: 1 }],
  })
  await useWorksStore.getState().flushWorkspace('1')
  expect(useWorksStore.getState().workspaceErrors['1']).toBe('서버 연결 실패')
  expect(useWorksStore.getState().works[0].chapters[0].content).toBe('보존할 내용')
  expect(useWorksStore.getState().dirtyIds).toContain('1')
})

test('서버 응답의 잘못된 ID와 설정 상태를 거절한다', () => {
  expect(() => parseWork({ ...remote, id: -1 })).toThrow()
  expect(() =>
    parseSetting({
      id: 1,
      title: '설정',
      content: '내용',
      kind: 'CHARACTER',
      status: 'INVALID',
      version: 0,
    }),
  ).toThrow()
})

test('서버 오류 후 브라우저 작품으로 이동하면 이전 오류가 남지 않는다', async () => {
  const original = JSON.stringify(createSampleWorks())
  localStorage.setItem('magister.works.v1:writer@example.com', original)
  useWorksStore.getState().initialize('writer@example.com')
  vi.mocked(workClient.get).mockRejectedValueOnce(new Error('작품 없음'))
  await useWorksStore.getState().loadWork('999')
  expect(useWorksStore.getState().detailError).toBe('작품 없음')
  const legacy = useWorksStore.getState().works[0]
  await useWorksStore.getState().loadWork(legacy.id)
  expect(useWorksStore.getState().detailError).toBe('')
})

test('뒤늦은 설정 조회가 이미 저장한 최신 버전을 덮어쓰지 않는다', async () => {
  await useWorksStore.getState().loadWork('1')
  const latest = {
    id: '2',
    title: '최신 설정',
    kind: '인물' as const,
    status: '확정' as const,
    content: '최신 내용',
    version: 1,
  }
  vi.mocked(settingClient.create).mockResolvedValue(latest)
  await useWorksStore.getState().saveSetting(remote, latest)
  vi.mocked(settingClient.list).mockResolvedValue({
    hasNext: false,
    nextCursor: null,
    items: [{ ...latest, title: '이전 설정', version: 0 }],
    totalElements: 1,
    totalPages: 1,
  })
  await useWorksStore.getState().loadSettings('1')
  expect(useWorksStore.getState().works[0].settings[0].title).toBe('최신 설정')
})

test('빠른 원고 입력은 서버 저장을 묶어서 실행한다', async () => {
  vi.useFakeTimers()
  try {
    await useWorksStore.getState().loadWork('1')
    for (let i = 1; i <= 20; i++)
      useWorksStore.getState().updateWork({
        ...remote,
        chapters: [{ id: 'c', title: '1화', content: '문장'.repeat(i), revision: i }],
      })
    expect(chapterClient.save).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(600)
    expect(chapterClient.save).toHaveBeenCalledTimes(1)
    expect(workspaceClient.save).not.toHaveBeenCalled()
    expect(useWorksStore.getState().dirtyIds).toEqual([])
  } finally {
    vi.useRealTimers()
  }
})

test('설정 더 보기는 커서를 전달하고 필터 변경은 이전 커서를 초기화한다', async () => {
  await useWorksStore.getState().loadWork('1')
  vi.mocked(settingClient.list).mockResolvedValueOnce({
    items: [],
    hasNext: true,
    nextCursor: 'next-characters',
  })
  await useWorksStore.getState().loadSettings('1', '인물')
  expect(useWorksStore.getState().settingView.nextCursor).toBe('next-characters')

  vi.mocked(settingClient.list).mockResolvedValue({ items: [], hasNext: false, nextCursor: null })
  await useWorksStore.getState().loadSettings('1', '인물', 'next-characters')
  expect(settingClient.list).toHaveBeenLastCalledWith(
    '1',
    expect.objectContaining({ kind: '인물', cursor: 'next-characters' }),
  )

  await useWorksStore.getState().loadSettings('1', '세계관', 'next-characters')
  expect(settingClient.list).toHaveBeenLastCalledWith(
    '1',
    expect.objectContaining({ kind: '세계관', cursor: undefined }),
  )
  expect(useWorksStore.getState().settingView.hasNext).toBe(false)
})
test('다른 탭의 새 버전이 도착하면 입력을 보존하고 자동 저장을 멈춘다', async () => {
  await useWorksStore.getState().loadWork('1')
  useWorksStore.getState().updateWork({
    ...remote,
    chapters: [{ id: 'c', title: '1화', content: '내 원고', revision: 1 }],
  })
  await useWorksStore.getState().handleRemoteChapterSave('1', 'c', 2)
  await useWorksStore.getState().flushWorkspace('1')
  expect(workspaceClient.save).not.toHaveBeenCalled()
  expect(useWorksStore.getState().conflicts).toContain('1')
  expect(useWorksStore.getState().works[0].chapters[0].content).toBe('내 원고')
})
test('저장 중 입력한 새 원고는 첫 저장 응답으로 사라지지 않는다', async () => {
  await useWorksStore.getState().loadWork('1')
  let finish: (response: SavedChapter) => void = () => {}
  vi.mocked(chapterClient.save).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve
      }),
  )
  const first = { id: 'c', title: '1화', content: '첫 문장', revision: 1 }
  useWorksStore.getState().updateWork({ ...remote, chapters: [first] })
  const pending = useWorksStore.getState().flushWorkspace('1')
  useWorksStore
    .getState()
    .updateWork({ ...remote, chapters: [{ ...first, content: '이어서 작성', revision: 2 }] })
  finish({
    ...first,
    chapterNo: 1,
    version: 0,
    workVersion: 1,
    updatedAt: '2026-10-25T00:00:00Z',
  })
  await pending
  expect(chapterClient.save).toHaveBeenCalledTimes(2)
  expect(useWorksStore.getState().works[0].chapters[0].content).toBe('이어서 작성')
  expect(useWorksStore.getState().works[0].chapters[0].version).toBe(1)
  expect(useWorksStore.getState().works[0].workspaceVersion).toBe(0)
})
test('빈 보관함 응답은 샘플로 대체하지 않고 서버 총 개수를 저장한다', async () => {
  vi.mocked(workClient.list).mockResolvedValue({
    ...page,
    items: [remote],
    size: 20,
    totalElements: 25,
    totalPages: 2,
  })
  await useWorksStore.getState().loadWorks()
  expect(useWorksStore.getState().totalElements).toBe(25)
  expect(useWorksStore.getState().listedIds).toEqual(['1'])
})

test('원고 저장 중 실패해도 가장 마지막 입력을 복구본에 보존한다', async () => {
  await useWorksStore.getState().loadWork('1')
  let rejectSave: (error: Error) => void = () => {}
  vi.mocked(chapterClient.save).mockImplementationOnce(
    () =>
      new Promise((_resolve, reject) => {
        rejectSave = reject
      }),
  )
  const first = { id: 'c', title: '1화', content: '첫 입력', revision: 1 }
  useWorksStore.getState().updateWork({ ...remote, chapters: [first] })
  const saving = useWorksStore.getState().flushWorkspace('1')
  useWorksStore
    .getState()
    .updateWork({ ...remote, chapters: [{ ...first, content: '마지막 입력' }] })
  rejectSave(new Error('연결 끊김'))
  await saving
  expect(sessionStorage.getItem('magister.recovery:writer@example.com:1')).toContain('마지막 입력')
  expect(useWorksStore.getState().works[0].chapters[0].content).toBe('마지막 입력')
  expect(useWorksStore.getState().dirtyIds).toContain('1')
})

test('저장 API가 버전 충돌을 반환하면 초안과 기준 버전을 유지한다', async () => {
  savedChapters = [
    {
      id: 'c',
      title: '1화',
      content: '서버 원고',
      revision: 3,
      version: 2,
      chapterNo: 1,
      updatedAt: remote.updatedAt,
      workVersion: 1,
    },
  ]
  await useWorksStore.getState().loadWork('1')
  const current = useWorksStore.getState().works[0]
  useWorksStore
    .getState()
    .updateWork({ ...current, chapters: [{ ...current.chapters[0], content: '내 초안' }] })
  vi.mocked(chapterClient.save).mockRejectedValueOnce(new ApiError('버전 충돌', 409))
  await useWorksStore.getState().flushWorkspace('1')
  expect(useWorksStore.getState().works[0].chapters[0]).toMatchObject({
    content: '내 초안',
    version: 2,
  })
  expect(useWorksStore.getState().conflicts).toContain('1')
  expect(sessionStorage.getItem('magister.recovery:writer@example.com:1')).toContain('내 초안')
})

test('다른 회차의 원격 저장은 작성 중인 회차와 충돌하지 않는다', async () => {
  savedChapters = ['c1', 'c2'].map((id, index) => ({
    id,
    title: id,
    content: '원고',
    revision: 1,
    version: 0,
    chapterNo: index + 1,
    updatedAt: remote.updatedAt,
    workVersion: 1,
  }))
  await useWorksStore.getState().loadWork('1')
  const current = useWorksStore.getState().works[0]
  useWorksStore.getState().updateWork({
    ...current,
    chapters: current.chapters.map((chapter) =>
      chapter.id === 'c1' ? { ...chapter, content: '내 1화' } : chapter,
    ),
  })
  vi.mocked(chapterClient.get).mockResolvedValue({
    ...savedChapters[1],
    content: '다른 탭의 2화',
    revision: 2,
    version: 1,
  })
  await useWorksStore.getState().handleRemoteChapterSave('1', 'c2', 1)
  expect(useWorksStore.getState().conflicts).toEqual([])
  expect(useWorksStore.getState().works[0].chapters.map((chapter) => chapter.content)).toEqual([
    '내 1화',
    '다른 탭의 2화',
  ])
  await useWorksStore.getState().flushWorkspace('1')
  expect(chapterClient.save).toHaveBeenCalledTimes(1)
  expect(chapterClient.save).toHaveBeenCalledWith(
    '1',
    expect.objectContaining({ id: 'c1', version: 0 }),
  )
})

test('원격 판단 저장이 작성 중인 원고를 덮어쓰거나 충돌시키지 않는다', async () => {
  await useWorksStore.getState().loadWork('1')
  useWorksStore.getState().updateWork({
    ...remote,
    chapters: [{ id: 'c', title: '1화', content: '작성 중', revision: 1 }],
  })
  vi.mocked(workspaceClient.get).mockResolvedValue({
    version: 2,
    workVersion: 2,
    updatedAt: remote.updatedAt,
    content: {
      chapters: [],
      analyses: [],
      events: [{ id: 'event', title: '사건', time: '', characters: '', chapterId: '', quote: '' }],
    },
  })
  useWorksStore.getState().handleRemoteSave('1', 2)
  await vi.waitFor(() => expect(useWorksStore.getState().works[0].workspaceVersion).toBe(2))
  expect(useWorksStore.getState().conflicts).toEqual([])
  expect(useWorksStore.getState().works[0].chapters[0].content).toBe('작성 중')
})

test('작가가 비교 후 선택한 충돌 회차만 최신 서버 버전으로 다시 저장한다', async () => {
  savedChapters = [
    {
      id: 'c',
      title: '1화',
      content: '기존 원고',
      revision: 1,
      version: 0,
      chapterNo: 1,
      updatedAt: remote.updatedAt,
      workVersion: 1,
    },
  ]
  await useWorksStore.getState().loadWork('1')
  const current = useWorksStore.getState().works[0]
  useWorksStore.getState().updateWork({
    ...current,
    chapters: [{ ...current.chapters[0], content: '작가가 선택한 초안' }],
  })
  await useWorksStore.getState().handleRemoteChapterSave('1', 'c', 1)
  expect(useWorksStore.getState().conflicts).toContain('1')
  useWorksStore.getState().resolveChapterConflict('1', {
    ...savedChapters[0],
    content: '다른 탭의 원고',
    version: 1,
    revision: 2,
  })
  await useWorksStore.getState().flushWorkspace('1')
  expect(chapterClient.rebase).toHaveBeenCalledWith('1', 'c')
  expect(chapterClient.save).toHaveBeenCalledWith(
    '1',
    expect.objectContaining({ version: 1, content: '작가가 선택한 초안' }),
  )
  expect(useWorksStore.getState().conflicts).toEqual([])
  expect(useWorksStore.getState().dirtyIds).toEqual([])
})
