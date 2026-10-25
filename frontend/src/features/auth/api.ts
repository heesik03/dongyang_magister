import type { User } from './auth'
import { request, mutate } from '../../shared/http'
export { ApiError, parseErrorMessage } from '../../shared/http'

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseUser(value: unknown): User {
  if (
    !record(value) ||
    typeof value.id !== 'number' ||
    !Number.isSafeInteger(value.id) ||
    value.id < 1 ||
    typeof value.name !== 'string' ||
    typeof value.email !== 'string'
  )
    throw new Error('사용자 응답 형식이 올바르지 않습니다.')
  return { id: value.id, name: value.name, email: value.email }
}

export const authApi = {
  async checkEmail(email: string) {
    const data = await mutate('/user/email/check', { email })
    if (!record(data) || typeof data.available !== 'boolean') {
      throw new Error('이메일 확인 응답 형식이 올바르지 않습니다.')
    }
    return data.available
  },
  async me(signal?: AbortSignal) {
    return parseUser(await request('/user/me', { signal }))
  },
  async signup(name: string, email: string, password: string) {
    return parseUser(await mutate('/user', { name, email, password }))
  },
  async login(email: string, password: string) {
    return parseUser(await mutate('/user/login', { email, password }))
  },
  async logout() {
    await mutate('/user/logout')
  },
}
