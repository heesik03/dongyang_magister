import type { Setting, SettingKind, SettingStatus } from '../works/workTypes'
import { readRecord, readString, readInteger } from '../../shared/responseReader'

// 서버 열거형과 작가에게 표시하는 용어 사이의 변환은 설정 도메인에서만 수행한다.
const settingKinds: Record<string, SettingKind> = {
  CHARACTER: '인물',
  WORLD: '세계관',
  MAJOR_EVENT: '주요 사건',
  INTENDED_ADAPTATION: '의도한 각색',
}
const settingStatuses: Record<string, SettingStatus> = {
  CONFIRMED: '확정',
  IDEA: '아이디어',
  AI_CANDIDATE: 'AI 후보',
}
const kindCodes: Record<SettingKind, string> = {
  인물: 'CHARACTER',
  세계관: 'WORLD',
  '주요 사건': 'MAJOR_EVENT',
  '의도한 각색': 'INTENDED_ADAPTATION',
}
const statusCodes: Record<SettingStatus, string> = {
  확정: 'CONFIRMED',
  아이디어: 'IDEA',
  'AI 후보': 'AI_CANDIDATE',
}
export function parseSetting(raw: unknown): Setting {
  const value = readRecord(raw)
  const kindCode = readString(value, 'kind')
  const statusCode = readString(value, 'status')
  if (!Object.hasOwn(settingKinds, kindCode) || !Object.hasOwn(settingStatuses, statusCode)) {
    throw new Error('설정 종류 또는 상태가 올바르지 않습니다.')
  }
  const kind = settingKinds[kindCode]
  const status = settingStatuses[statusCode]
  return {
    id: String(readInteger(value, 'id', 1)),
    title: readString(value, 'title'),
    content: readString(value, 'content'),
    kind,
    status,
    version: readInteger(value, 'version'),
  }
}

export function toSettingRequest(input: Pick<Setting, 'title' | 'kind' | 'status' | 'content'>) {
  return { ...input, kind: kindCodes[input.kind], status: statusCodes[input.status] }
}
export function toSettingKindCode(kind: string): string | undefined {
  if (kind === '전체') return undefined
  const code = Object.entries(kindCodes).find(([label]) => label === kind)?.[1]
  if (!code) throw new Error('설정 종류가 올바르지 않습니다.')
  return code
}
