// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => ({ request }) } }))
import { authApi } from '../src/features/auth/api'

beforeEach(() => request.mockReset())

function csrfResponse() {
  return {
    status: 200,
    data: { success: true, data: { headerName: 'X-CSRF-TOKEN', token: 'test' } },
  }
}

test('서버의 필드 검증 메시지를 일반 오류보다 우선 전달한다', async () => {
  request.mockResolvedValueOnce(csrfResponse()).mockResolvedValueOnce({
    status: 400,
    data: {
      success: false,
      message: '입력값을 확인해 주세요.',
      errors: [{ field: 'password', message: '영문·숫자·특수문자를 포함해 주세요.' }],
    },
  })
  await expect(authApi.signup('작가', 'writer@example.com', 'password1')).rejects.toThrow(
    '영문·숫자·특수문자를 포함해 주세요.',
  )
})

test('사용할 수 있는 필드 메시지가 없으면 일반 오류로 안내한다', async () => {
  request.mockResolvedValueOnce(csrfResponse()).mockResolvedValueOnce({
    status: 400,
    data: {
      success: false,
      message: '입력값을 확인해 주세요.',
      errors: [null, { message: '   ' }],
    },
  })
  await expect(authApi.signup('작가', 'writer@example.com', 'password1')).rejects.toThrow(
    '입력값을 확인해 주세요.',
  )
})

test('오류 응답 형식이 올바르지 않아도 기본 안내를 제공한다', async () => {
  request
    .mockResolvedValueOnce(csrfResponse())
    .mockResolvedValueOnce({ status: 502, data: '<html>Bad Gateway</html>' })
  await expect(authApi.login('writer@example.com', 'Password1!')).rejects.toThrow(
    '요청을 처리하지 못했습니다.',
  )
})
