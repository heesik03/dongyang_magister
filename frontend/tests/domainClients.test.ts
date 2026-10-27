import { clearCsrfToken } from '../src/shared/httpClient'
// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => ({ request }) } }))
import { workClient } from '../src/features/works/workClient'
import { settingClient } from '../src/features/settings/settingClient'
import { authClient } from '../src/features/auth/authClient'
import { analysisClient, parseJob, parseInput } from '../src/features/analysis/analysisClient'
import { chapterClient, parseChapter } from '../src/features/manuscript/chapterClient'
import { workspaceClient } from '../src/features/workspace/workspaceClient'
import { SESSION_EXPIRED_EVENT } from '../src/shared/httpClient'
import {
  decisionClient,
  parseDecision,
  parseDecisionReference,
} from '../src/features/review/decisionClient'
import { reviewClient, parseLinkedReview } from '../src/features/review/reviewClient'

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

test('판단의 버전·멱등 키·범위를 보내고 최신 상태와 이력을 읽는다', async () => {
  const decision = {
    id: 1,
    reviewId: 'candidate',
    quote: '마법 사용',
    version: 1,
    status: '의도된 설정',
    reason: '능력 변화',
    basisHash: 'a'.repeat(64),
    stale: false,
    canReconfirm: true,
    createdAt: '2026-10-27T00:00:00Z',
    scopes: [
      {
        id: 1,
        scopeType: 'CHAPTER_RANGE',
        fromChapterId: 'c2',
        toChapterId: null,
        sceneSourceId: null,
        startOffset: null,
        endOffset: null,
        conditionText: '봉인 해제 후',
        fromChapterTitle: '2화',
        toChapterTitle: null,
      },
    ],
  }
  request.mockResolvedValueOnce(success(decision))
  expect((await decisionClient.get('1', 'candidate')).version).toBe(1)
  const payload = {
    status: '보류' as const,
    reason: '확인 필요',
    expectedVersion: 1,
    mutationId: 'same-mutation',
    basisHash: decision.basisHash,
    reconfirm: false,
    scopes: [],
  }
  request
    .mockResolvedValueOnce(csrf())
    .mockResolvedValueOnce(success({ ...decision, status: '보류', version: 2 }))
  await decisionClient.save('1', 'candidate', payload)
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      url: '/work/1/review/candidate/decision',
      method: 'PUT',
      data: payload,
    }),
  )
  request.mockResolvedValueOnce(
    success({ items: [decision], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
  )
  expect((await decisionClient.history('1', 'candidate')).items[0].scopes[0].fromChapterTitle).toBe(
    '2화',
  )
  request.mockResolvedValueOnce(
    success({ items: [decision], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
  )
  expect((await decisionClient.list('1', 0, '의도된 설정')).items).toHaveLength(1)
  expect(() => parseDecision({ ...decision, basisHash: 'bad' })).toThrow()
  expect(() => parseDecision({ ...decision, version: -1 })).toThrow()
  expect(() =>
    parseDecision({ ...decision, scopes: [{ ...decision.scopes[0], scopeType: 'SCENE' }] }),
  ).toThrow()
})

test('분석 입력의 실제 판단 버전·범위·조건을 검증한다', () => {
  const reference = {
    scopeId: 1,
    decisionId: 1,
    reviewId: 'candidate',
    version: 2,
    status: '의도된 설정',
    reason: '반전',
    basisHash: 'a'.repeat(64),
    quote: '마법 사용',
    scopeType: 'SCENE',
    fromChapterId: null,
    toChapterId: null,
    sceneSourceId: 1,
    startOffset: 11,
    endOffset: 16,
    conditionText: '이 장면만',
    evidenceQuotes: ['능력 · 마법 불가'],
    fromChapterTitle: null,
    toChapterTitle: null,
  }
  expect(parseDecisionReference(reference).version).toBe(2)
  expect(() => parseDecisionReference({ ...reference, endOffset: 10 })).toThrow()
  expect(() => parseDecisionReference({ ...reference, scopeId: '1' })).toThrow()
  expect(() => parseDecisionReference({ ...reference, evidenceQuotes: [1] })).toThrow()
})

const linkedReview = {
  id: 'candidate',
  type: '설정 충돌 후보',
  quote: '마법 사용',
  targetSourceId: 1,
  startOffset: 11,
  endOffset: 16,
  reason: '변화 확인',
  suggestion: '계기 설명',
  evidences: [
    {
      sourceId: 2,
      sourceType: 'PREVIOUS',
      sourceKey: 'previous',
      title: '1화',
      revision: 1,
      quote: '마법 불가',
      startOffset: 5,
      endOffset: 10,
      explanation: '이전 행동',
    },
  ],
}

test('검토 후보와 스냅샷 원문을 실제 경로로 조회하고 위치·출처를 검증한다', async () => {
  request.mockResolvedValueOnce(
    success({
      candidates: { items: [linkedReview], page: 0, size: 100, totalElements: 1, totalPages: 1 },
      legacy: false,
      stale: true,
    }),
  )
  expect((await reviewClient.list('1', 'job-1')).reviews[0].startOffset).toBe(11)
  expect(request.mock.calls[0][0].url).toBe('/work/1/analysis/job-1/review')
  request.mockResolvedValueOnce(
    success({
      id: 2,
      type: 'PREVIOUS',
      sourceId: 'previous',
      title: '1화',
      content: '이전에는 마법 불가',
      revision: 1,
    }),
  )
  expect((await reviewClient.source('1', 'job-1', 2)).content).toBe('이전에는 마법 불가')
  expect(request.mock.calls[1][0].url).toBe('/work/1/analysis/job-1/source/2')
  expect(() => parseLinkedReview({ ...linkedReview, endOffset: 15 })).toThrow()
  expect(() => parseLinkedReview({ ...linkedReview, evidences: [] })).toThrow()
  expect(() =>
    parseLinkedReview({
      ...linkedReview,
      evidences: [{ ...linkedReview.evidences[0], sourceType: 'TARGET' }],
    }),
  ).toThrow()
  request.mockResolvedValueOnce(
    success({
      id: 3,
      type: 'PREVIOUS',
      sourceId: 'previous',
      title: '1화',
      content: '',
      revision: 1,
    }),
  )
  await expect(reviewClient.source('1', 'job-1', 2)).rejects.toThrow()
})
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

test('분석 미리보기와 확인된 접수를 구분하고 같은 요청 ID·토큰을 보낸다', async () => {
  const selection = {
    chapterId: 'c1',
    chapterRevision: 1,
    workspaceVersion: 0,
    settingIds: ['1'],
    previousIds: [],
    consistency: true,
    motivation: true,
  }
  const input = {
    chapter: { id: 'c1', title: '1화', content: '원고', revision: 1 },
    previous: [],
    settings: [
      { id: '1', title: '능력', content: '마법 불가', kind: '인물', status: '확정', version: 0 },
    ],
    decisions: [],
    consistency: true,
    motivation: true,
  }
  request.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(
    success({
      previewToken: 'confirmed-token',
      policyVersion: 'policy-v1',
      expiresAt: chapter.updatedAt,
      input,
      provider: 'Google Gemini API',
      model: 'test-model',
      retentionNotice: '보관 안내',
      deletionNotice: '삭제 안내',
      providerPolicyUrl: 'https://ai.google.dev/gemini-api/terms',
    }),
  )
  const preview = await analysisClient.preview('1', selection)
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      url: '/work/1/analysis/preview',
      method: 'POST',
      data: { ...selection, requestId: preview.request.requestId },
    }),
  )
  expect(request).toHaveBeenCalledTimes(2)
  request.mockResolvedValueOnce(
    success({ id: 'job-1', status: 'QUEUED', error: null, result: null }),
  )
  await analysisClient.create('1', preview)
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      url: '/work/1/analysis',
      method: 'POST',
      data: {
        ...preview.request,
        previewToken: 'confirmed-token',
        consentPolicyVersion: 'policy-v1',
      },
    }),
  )
})

test('이력 복원은 GET만 사용하고 회차·페이지·취소 신호를 전달한다', async () => {
  const signal = new AbortController().signal
  request.mockResolvedValueOnce(
    success({
      items: [
        {
          id: 'job-1',
          chapterId: 'c1',
          chapterTitle: '1화',
          status: 'RUNNING',
          createdAt: chapter.updatedAt,
          updatedAt: chapter.updatedAt,
          previewConfirmed: true,
        },
      ],
      page: 1,
      size: 10,
      totalElements: 11,
      totalPages: 2,
    }),
  )
  const history = await analysisClient.list('1', 'c1', 1, signal)
  expect(history.items[0].status).toBe('RUNNING')
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      method: 'GET',
      url: '/work/1/analysis',
      params: { chapterId: 'c1', page: 1, size: 10 },
      signal,
    }),
  )
  request.mockResolvedValueOnce(
    success({ id: 'job-1', status: 'RUNNING', error: null, result: null }),
  )
  await analysisClient.get('1', 'job-1', signal)
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({ method: 'GET', url: '/work/1/analysis/job-1', signal }),
  )
})

test('분석 상태·완료 결과·확정 여부가 잘못된 응답을 빈 결과로 바꾸지 않는다', () => {
  expect(() => parseJob({ id: 'job', status: 'UNKNOWN', error: null, result: null })).toThrow()
  expect(() => parseJob({ id: 'job', status: 'SUCCEEDED', error: null, result: null })).toThrow()
  expect(() =>
    parseInput({
      chapter: { id: 'c', title: '1화', content: '원고', revision: 1 },
      previous: [],
      settings: [
        {
          id: '1',
          title: '능력',
          content: '아이디어',
          kind: '인물',
          status: '아이디어',
          version: 0,
        },
      ],
      decisions: [],
      consistency: true,
      motivation: true,
    }),
  ).toThrow()
})
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
        separateReviews: true,
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

test('작품 삭제는 CSRF와 읽었던 버전을 전달하고 204를 처리한다', async () => {
  request.mockResolvedValueOnce(csrf()).mockResolvedValueOnce({ status: 204, data: '' })
  await expect(workClient.delete('work/id', 7)).resolves.toBeUndefined()
  expect(request).toHaveBeenLastCalledWith(
    expect.objectContaining({
      method: 'DELETE',
      url: '/work/work%2Fid',
      params: { version: 7 },
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': expect.any(String) }),
    }),
  )
})
