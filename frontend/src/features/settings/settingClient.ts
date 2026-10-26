import { httpClient } from '../../shared/httpClient'
import { parseCursorPage, type CursorPage } from '../../shared/responseReader'
import { parseSetting, toSettingRequest, toSettingKindCode } from './settingResponse'
import type { Setting } from '../works/workTypes'

export type SettingInput = Pick<Setting, 'title' | 'kind' | 'status' | 'content'>
interface SettingListOptions {
  kind?: string
  cursor?: string
  size?: number
  signal?: AbortSignal
}
const settingsPath = (workId: string) => '/work/' + encodeURIComponent(workId) + '/setting'

// 설정은 항상 작품에 속한다. 설정 ID만으로 조회·변경하는 경로는 제공하지 않는다.
export const settingClient = {
  async listAll(workId: string, signal?: AbortSignal): Promise<Setting[]> {
    const items = new Map<string, Setting>()
    const seenCursors = new Set<string>()
    let cursor: string | undefined
    while (true) {
      const result = await settingClient.list(workId, { cursor, size: 100, signal })
      for (const item of result.items) items.set(item.id, item)
      if (!result.hasNext || result.nextCursor === null) break
      if (seenCursors.has(result.nextCursor)) throw new Error('목록 응답의 커서가 반복됩니다.')
      seenCursors.add(result.nextCursor)
      cursor = result.nextCursor
    }
    return [...items.values()]
  },
  async list(
    workId: string,
    { kind = '전체', cursor, size = 20, signal }: SettingListOptions = {},
  ): Promise<CursorPage<Setting>> {
    const response = await httpClient.get(settingsPath(workId) + '/scroll', {
      params: { cursor, size, kind: toSettingKindCode(kind) },
      signal,
    })
    return parseCursorPage(response, parseSetting)
  },
  async create(workId: string, input: SettingInput): Promise<Setting> {
    return parseSetting(await httpClient.post(settingsPath(workId), toSettingRequest(input)))
  },
  async update(workId: string, existing: Setting, input: SettingInput): Promise<Setting> {
    const path = settingsPath(workId) + '/' + encodeURIComponent(existing.id)
    return parseSetting(
      await httpClient.put(path, { ...toSettingRequest(input), version: existing.version }),
    )
  },
}
