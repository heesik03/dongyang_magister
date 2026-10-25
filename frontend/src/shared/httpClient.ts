import axios from 'axios'
import { isRecord, readRecord, readString } from './responseReader'

// 세션 만료를 인증 상태에 전달한다. HTTP 계층은 store를 직접 참조하지 않는다.
export const SESSION_EXPIRED_EVENT = 'auth-expired'
const apiOrigin = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')
const transport = axios.create({
  baseURL: apiOrigin + '/api',
  withCredentials: true,
  timeout: 15000,
  validateStatus: () => true,
})
export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}
export function parseErrorMessage(body: unknown): string {
  if (!isRecord(body)) return '요청을 처리하지 못했습니다.'
  if (Array.isArray(body.errors)) {
    for (const error of body.errors) {
      if (isRecord(error) && typeof error.message === 'string' && error.message.trim()) {
        return error.message
      }
    }
  }
  return typeof body.message === 'string' && body.message.trim()
    ? body.message
    : '요청을 처리하지 못했습니다.'
}
interface RequestOptions {
  signal?: AbortSignal
  params?: Record<string, string | number | undefined>
  // 로그인 거절의 401은 기존 세션 만료와 구분한다.
  expireSessionOnUnauthorized?: boolean
}
async function send(
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  options: RequestOptions,
  data?: object,
  headers?: Record<string, string>,
): Promise<unknown> {
  const { expireSessionOnUnauthorized = true, ...config } = options
  const response = await transport.request<unknown>({ ...config, method, url: path, data, headers })
  if (response.status < 200 || response.status >= 300) {
    if (response.status === 401 && expireSessionOnUnauthorized) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }
    throw new ApiError(parseErrorMessage(response.data), response.status)
  }
  const body = readRecord(response.data)
  if (body.success !== true || !('data' in body)) {
    throw new Error('서버 응답 형식이 올바르지 않습니다.')
  }
  return body.data
}
async function sendWithCsrf(
  method: 'POST' | 'PUT',
  path: string,
  data?: object,
  options: RequestOptions = {},
): Promise<unknown> {
  // 로그인·로그아웃으로 세션이 바뀔 수 있으므로 변경 요청마다 현재 CSRF 토큰을 받는다.
  const csrf = readRecord(await send('GET', '/user/csrf', options))
  const headerName = readString(csrf, 'headerName')
  const token = readString(csrf, 'token')
  if (!headerName.trim() || !token.trim()) throw new Error('보안 토큰을 확인하지 못했습니다.')
  return send(method, path, options, data, {
    'Content-Type': 'application/json',
    [headerName]: token,
  })
}
// 쿠키·응답 봉투·CSRF는 여기서 처리하고, 도메인 client는 업무 요청과 응답 해석을 담당한다.
export const httpClient = {
  get(path: string, options: RequestOptions = {}): Promise<unknown> {
    return send('GET', path, options)
  },
  post(path: string, data?: object, options: RequestOptions = {}): Promise<unknown> {
    return sendWithCsrf('POST', path, data, options)
  },
  put(path: string, data: object, options: RequestOptions = {}): Promise<unknown> {
    return sendWithCsrf('PUT', path, data, options)
  },
}
