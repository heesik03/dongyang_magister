import { httpClient } from '../../shared/httpClient'
import { parsePage, type Page } from '../../shared/responseReader'
import { parseWork } from './workResponse'
import type { Work } from './workTypes'

export type WorkInput = Pick<Work, 'title' | 'genre' | 'era' | 'description'>
interface WorkListOptions {
  keyword?: string
  genre?: string
  page?: number
  signal?: AbortSignal
}
const workPath = (id: string) => '/work/' + encodeURIComponent(id)

export const workClient = {
  async list({ keyword = '', genre = '', page = 0, signal }: WorkListOptions = {}): Promise<
    Page<Work>
  > {
    const response = await httpClient.get('/work', {
      params: { keyword, genre, page, size: 20 },
      signal,
    })
    return parsePage(response, parseWork)
  },
  async get(id: string, signal?: AbortSignal): Promise<Work> {
    return parseWork(await httpClient.get(workPath(id), { signal }))
  },
  async create(input: WorkInput): Promise<Work> {
    return parseWork(await httpClient.post('/work', input))
  },
  async update(id: string, input: WorkInput, version: number): Promise<Work> {
    // 읽었던 버전을 보내 다른 편집 결과를 덮어쓰는 요청을 서버가 거절할 수 있게 한다.
    return parseWork(await httpClient.put(workPath(id), { ...input, version }))
  },
}
