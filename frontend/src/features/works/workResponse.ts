import type { Work } from './workTypes'
import { readRecord, readString, readInteger } from '../../shared/responseReader'

// 서버는 작품 정보만 관리한다. 원고·분석·타임라인 초안은 캐시 병합 단계에서 연결한다.
export function parseWork(raw: unknown): Work {
  const value = readRecord(raw)
  const updatedAt = readString(value, 'updatedAt')
  if (!Number.isFinite(Date.parse(updatedAt)))
    throw new Error('작품 수정 시각이 올바르지 않습니다.')
  return {
    id: String(readInteger(value, 'id', 1)),
    title: readString(value, 'title'),
    genre: readString(value, 'genre'),
    era: readString(value, 'era'),
    description: readString(value, 'description'),
    updatedAt,
    version: readInteger(value, 'version'),
    source: 'server',
    settings: [],
    chapters: [],
    analyses: [],
    events: [],
  }
}
