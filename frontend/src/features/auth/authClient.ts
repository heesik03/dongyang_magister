import { httpClient } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'
import type { User } from './authTypes'

function parseUser(raw: unknown): User {
  const user = readRecord(raw)
  return {
    id: readInteger(user, 'id', 1),
    name: readString(user, 'name'),
    email: readString(user, 'email'),
  }
}
// 계정 생성과 로그인 세션 시작은 별도 요청이다. 회원가입 후의 이동은 화면에서 결정한다.
export const authClient = {
  async checkEmail(email: string): Promise<boolean> {
    const result = readRecord(await httpClient.post('/user/email/check', { email }))
    if (typeof result.available !== 'boolean')
      throw new Error('이메일 확인 응답 형식이 올바르지 않습니다.')
    return result.available
  },
  async me(signal?: AbortSignal): Promise<User> {
    return parseUser(await httpClient.get('/user/me', { signal }))
  },
  async signup(name: string, email: string, password: string): Promise<User> {
    return parseUser(await httpClient.post('/user', { name, email, password }))
  },
  async login(email: string, password: string): Promise<User> {
    const response = await httpClient.post(
      '/user/login',
      { email, password },
      {
        expireSessionOnUnauthorized: false,
      },
    )
    return parseUser(response)
  },
  async logout(): Promise<void> {
    await httpClient.post('/user/logout')
  },
}
