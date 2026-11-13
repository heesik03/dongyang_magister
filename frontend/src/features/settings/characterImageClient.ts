import { httpClient } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'

export type PortraitInput = { appearance: string; direction: string }
export type PortraitStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
type PortraitJob = {
  id: string
  settingVersion: number
  createdAt: string
  status: PortraitStatus
  primary: boolean
}

function imagePath(workId: string, settingId: string) {
  return (
    '/work/' +
    encodeURIComponent(workId) +
    '/setting/' +
    encodeURIComponent(settingId) +
    '/character-image'
  )
}

function readJob(value: unknown): PortraitJob {
  const row = readRecord(value)
  const status = readString(row, 'status')
  if (
    (status !== 'QUEUED' &&
      status !== 'RUNNING' &&
      status !== 'SUCCEEDED' &&
      status !== 'FAILED') ||
    typeof row.primary !== 'boolean'
  )
    throw new Error('인물 이미지 작업 상태를 확인할 수 없습니다.')
  return {
    id: readString(row, 'id'),
    settingVersion: readInteger(row, 'settingVersion'),
    createdAt: readString(row, 'createdAt'),
    status,
    primary: row.primary,
  }
}

export const characterImageClient = {
  async list(workId: string, settingId: string, signal: AbortSignal) {
    const data = readRecord(await httpClient.get(imagePath(workId, settingId), { signal }))
    if (!Array.isArray(data.jobs) || typeof data.enabled !== 'boolean')
      throw new Error('인물 이미지 목록을 확인할 수 없습니다.')
    return {
      jobs: data.jobs.map(readJob),
      remaining: readInteger(data, 'remaining'),
      capacity: readInteger(data, 'capacity', 1),
      resetAt: readString(data, 'resetAt'),
      enabled: data.enabled,
    }
  },
  async create(workId: string, settingId: string, input: PortraitInput, requestId: string) {
    return readJob(
      await httpClient.post(imagePath(workId, settingId), {
        input,
        requestId,
      }),
    )
  },
  async content(workId: string, settingId: string, id: string, signal: AbortSignal) {
    const data = await httpClient.get(
      imagePath(workId, settingId) + '/' + encodeURIComponent(id) + '/content',
      { responseType: 'blob', signal },
    )
    if (!(data instanceof Blob) || !['image/png', 'image/jpeg'].includes(data.type))
      throw new Error('이미지를 표시할 수 없습니다.')
    return data
  },
  async primary(workId: string, settingId: string, id: string | null) {
    const path = imagePath(workId, settingId)
    if (id === null) await httpClient.delete(path + '/primary')
    else await httpClient.put(path + '/' + encodeURIComponent(id) + '/primary', {})
  },
  async delete(workId: string, settingId: string, id: string) {
    await httpClient.delete(imagePath(workId, settingId) + '/' + encodeURIComponent(id))
  },
}
