// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => ({ request }) } }))
import { clearCsrfToken, ApiError } from '../src/shared/httpClient'
import {
  manuscriptTransferClient,
  validateManuscriptFile,
} from '../src/features/manuscript/manuscriptTransferClient'
const file = new File(['본문'], '1화.md', { type: 'text/markdown' })
const csrf = {
  status: 200,
  data: { success: true, data: { headerName: 'X-CSRF-TOKEN', token: 'test' } },
}
const chapter = {
  id: 'chapter',
  title: '1화',
  content: '본문',
  revision: 2,
  version: 1,
  chapterNo: 1,
  workVersion: 3,
  updatedAt: '2026-11-02T00:00:00Z',
}
beforeEach(() => {
  request.mockReset()
  clearCsrfToken()
})
test('파일 미리보기는 multipart와 세션 CSRF로 요청하고 응답을 검증한다', async () => {
  request.mockResolvedValueOnce(csrf).mockResolvedValueOnce({
    status: 200,
    data: {
      success: true,
      data: { workTitle: '', genre: '', chapterTitle: '1화', content: '본문', format: 'md' },
    },
  })
  expect((await manuscriptTransferClient.preview('1', file)).content).toBe('본문')
  const config = request.mock.calls[1][0]
  expect(config.url).toBe('/work/1/chapter/import/parse')
  expect(config.data.get('file')).toBe(file)
  expect(config.headers).toEqual({ 'X-CSRF-TOKEN': 'test' })
})
test('기존 회차 교체에 저장 버전과 동일한 재시도 식별자를 전달한다', async () => {
  request
    .mockResolvedValueOnce(csrf)
    .mockResolvedValue({ status: 200, data: { success: true, data: chapter } })
  const input = { id: 'chapter', mutationId: 'same-operation', title: '수정 제목', version: 0 }
  await manuscriptTransferClient.importFile('1', file, input)
  await manuscriptTransferClient.importFile('1', file, input)
  for (const [config] of request.mock.calls.slice(1)) {
    expect(config.method).toBe('PUT')
    expect(config.url).toBe('/work/1/chapter/chapter/import')
    expect(config.data.get('version')).toBe('0')
    expect(config.data.get('mutationId')).toBe('same-operation')
  }
})
test('새 회차 생성은 명시적인 회차 ID와 작업 ID를 보낸다', async () => {
  request
    .mockResolvedValueOnce(csrf)
    .mockResolvedValueOnce({ status: 201, data: { success: true, data: chapter } })
  await manuscriptTransferClient.importFile('1', file, {
    id: 'chapter',
    mutationId: 'operation',
    title: '1화',
  })
  expect(request.mock.calls[1][0].method).toBe('POST')
  expect(request.mock.calls[1][0].data.get('id')).toBe('chapter')
})
test('회차와 작품 전체를 바이너리 파일로 내려받는다', async () => {
  const blob = new Blob(['본문'], { type: 'application/pdf' })
  request.mockResolvedValue({ status: 200, data: blob })
  expect(await manuscriptTransferClient.exportFile('1', '회차', 'pdf')).toBe(blob)
  expect(request.mock.calls[0][0]).toMatchObject({
    responseType: 'blob',
    url: '/work/1/chapter/' + encodeURIComponent('회차') + '/export',
    params: { format: 'pdf' },
  })
  await manuscriptTransferClient.exportFile('1', null, 'docx')
  expect(request.mock.calls[1][0].url).toBe('/work/1/export')
})
test('바이너리 다운로드 오류에서도 서버 메시지와 세션 만료를 전달한다', async () => {
  const blob = new Blob()
  Object.defineProperty(blob, 'text', {
    value: async () => JSON.stringify({ message: '로그인이 필요합니다.' }),
  })
  request.mockResolvedValue({ status: 401, data: blob })
  const expired = vi.fn()
  window.addEventListener('auth-expired', expired)
  await expect(manuscriptTransferClient.exportFile('1', 'chapter', 'txt')).rejects.toEqual(
    new ApiError('로그인이 필요합니다.', 401),
  )
  expect(expired).toHaveBeenCalledOnce()
  window.removeEventListener('auth-expired', expired)
})
test('빈 파일과 미지원 형식 및 큰 파일은 전송하지 않는다', () => {
  expect(() => validateManuscriptFile(new File([], 'empty.txt'))).toThrow('내용이 있는')
  expect(() => validateManuscriptFile(new File(['a'], 'file.exe'))).toThrow('TXT')
  const large = new File(['a'], 'large.txt')
  Object.defineProperty(large, 'size', { value: 8 * 1024 * 1024 })
  expect(() => validateManuscriptFile(large)).toThrow('8MB')
  expect(request).not.toHaveBeenCalled()
})
