import axios, { type AxiosRequestConfig } from 'axios'

const client = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL || ''}/api`,
  withCredentials: true,
  timeout: 15000,
  validateStatus: () => true,
})

export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function parseErrorMessage(body: unknown): string {
  if (!record(body)) return '요청을 처리하지 못했습니다.'
  if (Array.isArray(body.errors)) {
    for (const item of body.errors) {
      if (record(item) && typeof item.message === 'string' && item.message.trim()) {
        return item.message
      }
    }
  }
  return typeof body.message === 'string' && body.message.trim()
    ? body.message
    : '요청을 처리하지 못했습니다.'
}

export async function request(path: string, options: AxiosRequestConfig = {}): Promise<unknown> {
  const response = await client.request<unknown>({ ...options, url: path })
  const body: unknown = response.data
  if (response.status < 200 || response.status >= 300) {
    if (response.status === 401 && path !== '/user/login')
      window.dispatchEvent(new Event('auth-expired'))
    throw new ApiError(parseErrorMessage(body), response.status)
  }
  if (!record(body) || body.success !== true || !('data' in body))
    throw new Error('서버 응답 형식이 올바르지 않습니다.')
  return body.data
}

export async function mutate(path: string, body?: object, method: 'POST' | 'PUT' = 'POST') {
  const csrf = await request('/user/csrf')
  if (!record(csrf) || typeof csrf.headerName !== 'string' || typeof csrf.token !== 'string')
    throw new Error('보안 토큰을 확인하지 못했습니다.')
  return request(path, {
    method,
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    data: body,
  })
}
