import axios, { type AxiosRequestConfig } from 'axios'
import type { User } from './auth'

const client = axios.create({ baseURL: `${import.meta.env.VITE_API_URL || ''}/api/user`, withCredentials: true, timeout: 15000, validateStatus: () => true })
export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}
function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function parseUser(value: unknown): User {
  if (!record(value) || typeof value.id !== 'number' || !Number.isSafeInteger(value.id) || value.id < 1 ||
    typeof value.name !== 'string' || typeof value.email !== 'string') throw new Error('사용자 응답 형식이 올바르지 않습니다.')
  return { id: value.id, name: value.name, email: value.email }
}
async function request(path: string, options: AxiosRequestConfig = {}): Promise<unknown> {
  const response = await client.request<unknown>({ ...options, url: path })
  const body: unknown = response.data
  if (response.status < 200 || response.status >= 300) {
    if (response.status === 401 && path !== '/login') window.dispatchEvent(new Event('auth-expired'))
    throw new ApiError(record(body) && typeof body.message === 'string' ? body.message : '요청을 처리하지 못했습니다.', response.status)
  }
  if (!record(body) || body.success !== true || !('data' in body)) throw new Error('서버 응답 형식이 올바르지 않습니다.')
  return body.data
}
async function mutate(path: string, body?: object) {
  const csrf = await request('/csrf')
  if (!record(csrf) || typeof csrf.headerName !== 'string' || typeof csrf.token !== 'string') throw new Error('보안 토큰을 확인하지 못했습니다.')
  return request(path, { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, data: body })
}
export const authApi = {
  async me(signal?: AbortSignal) { return parseUser(await request('/me', { signal })) },
  async signup(name: string, email: string, password: string) { return parseUser(await mutate('', { name, email, password })) },
  async login(email: string, password: string) { return parseUser(await mutate('/login', { email, password })) },
  async logout() { await mutate('/logout') },
}
