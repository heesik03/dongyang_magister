import { httpClient } from '../../shared/httpClient'
import { parsePage, type Page } from '../../shared/responseReader'
import { parseSetting, toSettingRequest, toSettingKindCode } from './settingResponse'
import type { Setting } from '../works/workTypes'

export type SettingInput = Pick<Setting, 'title' | 'kind' | 'status' | 'content'>
interface SettingListOptions {
  kind?: string
  page?: number
  size?: number
  signal?: AbortSignal
}
const settingsPath = (workId: string) => '/work/' + encodeURIComponent(workId) + '/setting'

// 설정은 항상 작품에 속한다. 설정 ID만으로 조회·변경하는 경로는 제공하지 않는다.
export const settingClient = {
  async list(
    workId: string,
    { kind = '전체', page = 0, size = 20, signal }: SettingListOptions = {},
  ): Promise<Page<Setting>> {
    const response = await httpClient.get(settingsPath(workId), {
      params: { page, size, kind: toSettingKindCode(kind) },
      signal,
    })
    return parsePage(response, parseSetting)
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
