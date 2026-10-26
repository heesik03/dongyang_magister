import { clearCsrfToken } from '../src/shared/httpClient'
// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => ({ request }) } }))
import { workClient } from '../src/features/works/workClient'
import { settingClient } from '../src/features/settings/settingClient'
import { authClient } from '../src/features/auth/authClient'
import { chapterClient, parseChapter } from '../src/features/manuscript/chapterClient'
import { workspaceClient } from '../src/features/workspace/workspaceClient'
import { SESSION_EXPIRED_EVENT } from '../src/shared/httpClient'

const work = {
  id: 1,
  title: '검증 작품',
  genre: '판타지',
  era: '중세',
  description: '',
  version: 2,
  updatedAt: '2026-10-25T00:00:00Z',
}
const setting = {
  id: 3,
  title: '능력',
  content: '마법을 사용할 수 없다.',
  kind: 'CHARACTER',
  status: 'CONFIRMED',
  version: 4,
}
const settingInput = {
  title: '능력',
  content: '마법을 사용할 수 없다.',
  kind: '인물' as const,
  status: '확정' as const,
}
function success(data: unknown) {
  return { status: 200, data: { success: true, data } }
}
function csrf() {
  return success({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
}
beforeEach(() => {
  request.mockReset()
  clearCsrfToken()
  chapterClient.reset()
})

const chapter = {
  id: 'c1',
  title: '1화',
  content: '원고',
  chapterNo: 1,
  revision: 1,
  version: 0,
  updatedAt: '2026-10-26T00:00:00Z',
  workVersion: 1,
}
test('회차 생성과 수정은 각각의 경로와 회차 버전을 사용한다', async () => {
  request.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(success(chapter))
  await chapterClient.save('1', { id: 'c1', title: '1화', content: '원고', revision: 1 })
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      method: 'POST',
      url: '/work/1/chapter',
      data: expect.objectContaining({ id: 'c1', title: '1화', content: '원고' }),
    }),
  )
  request.mockResolvedValueOnce(success({ ...chapter, version: 1, revision: 2 }))
  await chapterClient.save('1', chapter)
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      method: 'PUT',
      url: '/work/1/chapter/c1',
      data: expect.objectContaining({ version: 0 }),
    }),
  )
})

test('응답이 유실된 원고 저장 재시도는 동일 mutationId와 기준 버전을 사용한다', async () => {
  request.mockResolvedValueOnce(csrf()).mockRejectedValueOnce(new Error('응답 유실'))
  await expect(chapterClient.save('1', chapter)).rejects.toThrow('응답 유실')
  const firstRequest: unknown = request.mock.calls[1][0]
  request.mockResolvedValueOnce(success({ ...chapter, version: 1 }))
  await chapterClient.save('1', chapter)
  expect(request.mock.calls[2][0]).toEqual(firstRequest)
})

test('회차 목록은 모든 페이지를 가져오고 잘못된 응답은 거절한다', async () => {
  request
    .mockResolvedValueOnce(
      success({ items: [chapter], page: 0, size: 20, totalElements: 2, totalPages: 2 }),
    )
    .mockResolvedValueOnce(
      success({
        items: [{ ...chapter, id: 'c2', chapterNo: 2 }],
        page: 1,
        size: 20,
        totalElements: 2,
        totalPages: 2,
      }),
    )
  expect((await chapterClient.listAll('1')).map((item) => item.id)).toEqual(['c1', 'c2'])
  expect(() => parseChapter({ ...chapter, version: -1 })).toThrow()
  expect(() => parseChapter({ ...chapter, content: null })).toThrow()
})

test('판단·사건 저장 요청은 원고 본문을 workspace로 다시 전송하지 않는다', async () => {
  request.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(
    success({
      version: 1,
      workVersion: 1,
      content: { chapters: [], analyses: [], events: [] },
      updatedAt: chapter.updatedAt,
    }),
  )
  await workspaceClient.save('1', 0, 'decision', { chapters: [chapter], analyses: [], events: [] })
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      data: {
        version: 0,
        mutationId: 'decision',
        separateChapters: true,
        content: { chapters: [], analyses: [], events: [] },
      },
    }),
  )
})

test('작품 목록의 검색·페이지·취소 신호를 전달하고 정상 빈 결과를 유지한다', async () => {
  const signal = new AbortController().signal
  request.mockResolvedValueOnce(
    success({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 }),
  )
  const result = await workClient.list({ keyword: '소설', genre: '판타지', page: 1, signal })
  expect(request).toHaveBeenCalledWith(
    expect.objectContaining({
      method: 'GET',
      url: '/work',
      signal,
      params: { keyword: '소설', genre: '판타지', page: 1, size: 20 },
    }),
  )
  expect(result.items).toEqual([])
})

test('작품 수정에 CSRF 헤더와 읽었던 버전을 전송한다', async () => {
  request.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(success(work))
  const input = { title: '검증 작품', genre: '판타지', era: '중세', description: '' }
  await workClient.update('1', input, 2)
  expect(request).toHaveBeenNthCalledWith(
    1,
    expect.objectContaining({ method: 'GET', url: '/user/csrf' }),
  )
  expect(request).toHaveBeenNthCalledWith(
    2,
    expect.objectContaining({
      method: 'PUT',
      url: '/work/1',
      data: { ...input, version: 2 },
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'test-csrf' }),
    }),
  )
})

test('작품별 설정 생성·수정에서 서버 코드와 설정 버전을 사용한다', async () => {
  request.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(success(setting))
  const created = await settingClient.create('1', settingInput)
  expect(created).toMatchObject({ id: '3', kind: '인물', status: '확정', version: 4 })
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      method: 'POST',
      url: '/work/1/setting',
      data: { ...settingInput, kind: 'CHARACTER', status: 'CONFIRMED' },
    }),
  )
  request.mockResolvedValueOnce(success({ ...setting, version: 5 }))
  await settingClient.update('1', created, settingInput)
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      method: 'PUT',
      url: '/work/1/setting/3',
      data: { ...settingInput, kind: 'CHARACTER', status: 'CONFIRMED', version: 4 },
    }),
  )
})

test('설정 조회 필터를 서버 열거형으로 바꾸고 알 수 없는 종류는 요청 전에 거절한다', async () => {
  request.mockResolvedValueOnce(success({ items: [setting], hasNext: false, nextCursor: null }))
  await settingClient.list('1', { kind: '인물' })
  expect(request).toHaveBeenCalledWith(
    expect.objectContaining({
      url: '/work/1/setting/scroll',
      params: { kind: 'CHARACTER', cursor: undefined, size: 20 },
    }),
  )
  await expect(settingClient.list('1', { kind: '없는 종류' })).rejects.toThrow('설정 종류')
  expect(request).toHaveBeenCalledTimes(1)
})

test('세션 만료를 전달하되 로그인 거절은 세션 만료 이벤트로 전파하지 않는다', async () => {
  const expired = vi.fn()
  window.addEventListener(SESSION_EXPIRED_EVENT, expired)
  try {
    request.mockResolvedValueOnce({ status: 401, data: { message: '로그인 필요' } })
    await expect(workClient.list()).rejects.toThrow('로그인 필요')
    expect(expired).toHaveBeenCalledTimes(1)
    request
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce({ status: 401, data: { message: '인증 실패' } })
    await expect(authClient.login('qa@example.com', 'test')).rejects.toThrow('인증 실패')
    expect(expired).toHaveBeenCalledTimes(1)
  } finally {
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
  }
})

test('CSRF 응답이 잘못되면 변경 요청을 전송하지 않는다', async () => {
  request.mockResolvedValueOnce(success({ headerName: '', token: 'token' }))
  await expect(settingClient.create('1', settingInput)).rejects.toThrow('보안 토큰')
  expect(request).toHaveBeenCalledTimes(1)
})

test('잘못된 설정 코드와 성공 응답 형식을 mock 결과로 대신하지 않는다', async () => {
  request.mockResolvedValueOnce(
    success({
      items: [{ ...setting, kind: 'constructor' }],
      hasNext: false,
      nextCursor: null,
    }),
  )
  await expect(settingClient.list('1')).rejects.toThrow('설정 종류')
  request.mockResolvedValueOnce({ status: 200, data: { items: [work] } })
  await expect(workClient.list()).rejects.toThrow('서버 응답 형식')
})

test('설정이 100개를 넘어도 마지막 페이지까지 조회한다', async () => {
  request
    .mockResolvedValueOnce(
      success({
        items: Array.from({ length: 100 }, (_, index) => ({ ...setting, id: index + 1 })),
        hasNext: true,
        nextCursor: 'after-100',
      }),
    )
    .mockResolvedValueOnce(
      success({
        items: [{ ...setting, id: 101 }],
        hasNext: false,
        nextCursor: null,
      }),
    )
  const result = await settingClient.listAll('1')
  expect(result).toHaveLength(101)
  expect(result[100].id).toBe('101')
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      params: expect.objectContaining({ cursor: 'after-100', size: 100 }),
    }),
  )
})
test('같은 세션에서는 CSRF 토큰 조회를 재사용한다', async () => {
  request
    .mockResolvedValueOnce(csrf())
    .mockResolvedValueOnce(success(work))
    .mockResolvedValueOnce(success(work))
  await workClient.create(work)
  await workClient.update('1', work, 2)
  expect(request.mock.calls.filter(([config]) => config.url === '/user/csrf')).toHaveLength(1)
})

test('커서 메타데이터가 모순되거나 반복되면 무한 조회하지 않고 실패한다', async () => {
  request.mockResolvedValueOnce(success({ items: [setting], hasNext: true, nextCursor: null }))
  await expect(settingClient.list('1')).rejects.toThrow('커서 목록')

  request.mockResolvedValue(success({ items: [setting], hasNext: true, nextCursor: 'repeated' }))
  await expect(settingClient.listAll('1')).rejects.toThrow('커서가 반복')
  expect(request).toHaveBeenCalledTimes(3)
})
