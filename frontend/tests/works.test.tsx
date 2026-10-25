// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest'
import { useWorks } from '../src/features/works/store'
import { workApi, settingApi, parseWork, parseSetting } from '../src/features/works/api'
import { createSampleWorks } from '../src/features/works/sample'
import type { Work } from '../src/features/works/types'

vi.mock('../src/features/works/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/features/works/api')>()
  return {
    ...original,
    workApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn() },
    settingApi: { list: vi.fn(), save: vi.fn() },
  }
})
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
  useWorks.getState().reset()
  localStorage.clear()
  vi.resetAllMocks()
  vi.mocked(workApi.get).mockResolvedValue(remote)
  vi.mocked(settingApi.list).mockResolvedValue(page)
  useWorks.getState().initialize('writer@example.com')
})

test('브라우저 작품을 자동 업로드하거나 원본을 변경하지 않는다', () => {
  const original = JSON.stringify(createSampleWorks())
  localStorage.setItem('magister.works.v1:writer@example.com', original)
  useWorks.getState().initialize('writer@example.com')
  expect(useWorks.getState().works.length).toBeGreaterThan(0)
  expect(useWorks.getState().works.every((work) => work.source === 'local')).toBe(true)
  expect(localStorage.getItem('magister.works.v1:writer@example.com')).toBe(original)
  expect(workApi.create).not.toHaveBeenCalled()
})

test('서버 작품 원고를 재접속 후 복원하고 서버 설정을 로컬 저장으로 덮어쓰지 않는다', async () => {
  await useWorks.getState().loadWork('1')
  const setting = {
    id: '2',
    title: '능력',
    kind: '인물' as const,
    status: '확정' as const,
    content: '마법 불가',
    version: 0,
  }
  vi.mocked(settingApi.save).mockResolvedValue(setting)
  await useWorks.getState().saveSetting(remote, setting)
  useWorks.getState().updateWork({
    ...remote,
    title: '오래된 제목',
    chapters: [{ id: 'chapter', title: '1화', content: '새 원고', revision: 1 }],
  })
  expect(useWorks.getState().works[0].title).toBe('서버 작품')
  expect(useWorks.getState().works[0].settings).toEqual([setting])
  useWorks.getState().initialize('writer@example.com')
  await useWorks.getState().loadWork('1')
  expect(useWorks.getState().works[0].chapters[0].content).toBe('새 원고')
})

test('작품 조회 실패는 빈 목록으로 바꾸지 않고 오류로 표시한다', async () => {
  vi.mocked(workApi.list).mockRejectedValue(new Error('서버 연결 실패'))
  await useWorks.getState().loadWorks()
  expect(useWorks.getState().listError).toBe('서버 연결 실패')
})

test('계정 변경 전에 시작한 생성 응답이 다른 계정에 나타나지 않는다', async () => {
  let finish: (work: Work) => void = () => {}
  vi.mocked(workApi.create).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const pending = useWorks.getState().createWork(remote)
  const rejected = expect(pending).rejects.toThrow('로그인 정보가 변경')
  useWorks.getState().initialize('other@example.com')
  finish(remote)
  await rejected
  expect(useWorks.getState().works).toEqual([])
})

test('버전 충돌 시 화면의 기존 설정을 변경하지 않는다', async () => {
  await useWorks.getState().loadWork('1')
  vi.mocked(settingApi.save).mockRejectedValue(new Error('버전 충돌'))
  await expect(
    useWorks
      .getState()
      .saveSetting(remote, { title: '설정', kind: '인물', status: '확정', content: '내용' }),
  ).rejects.toThrow('버전 충돌')
  expect(useWorks.getState().works[0].settings).toEqual([])
})

test('저장 실패 시 원고를 메모리에 보존한다', async () => {
  await useWorks.getState().loadWork('1')
  const original = Storage.prototype.setItem
  Storage.prototype.setItem = () => {
    throw new Error('quota')
  }
  try {
    useWorks.getState().updateWork({
      ...remote,
      chapters: [{ id: '1', title: '1화', content: '보존할 내용', revision: 1 }],
    })
    expect(useWorks.getState().storageError).toContain('저장에 실패')
    expect(useWorks.getState().works[0].chapters[0].content).toBe('보존할 내용')
  } finally {
    Storage.prototype.setItem = original
  }
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
  useWorks.getState().initialize('writer@example.com')
  vi.mocked(workApi.get).mockRejectedValueOnce(new Error('작품 없음'))
  await useWorks.getState().loadWork('999')
  expect(useWorks.getState().detailError).toBe('작품 없음')
  const legacy = useWorks.getState().works[0]
  await useWorks.getState().loadWork(legacy.id)
  expect(useWorks.getState().detailError).toBe('')
})

test('뒤늦은 설정 조회가 이미 저장한 최신 버전을 덮어쓰지 않는다', async () => {
  await useWorks.getState().loadWork('1')
  const latest = {
    id: '2',
    title: '최신 설정',
    kind: '인물' as const,
    status: '확정' as const,
    content: '최신 내용',
    version: 1,
  }
  vi.mocked(settingApi.save).mockResolvedValue(latest)
  await useWorks.getState().saveSetting(remote, latest)
  vi.mocked(settingApi.list).mockResolvedValue({
    ...page,
    items: [{ ...latest, title: '이전 설정', version: 0 }],
    totalElements: 1,
    totalPages: 1,
  })
  await useWorks.getState().loadSettings('1')
  expect(useWorks.getState().works[0].settings[0].title).toBe('최신 설정')
})
