import { httpClient } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'

export type SceneSelection = {
  chapterId: string
  revision: number
  start: number
  end: number
  settingIds: number[]
}

function readArray<T>(raw: unknown, parse: (item: unknown) => T): T[] {
  if (!Array.isArray(raw)) throw new Error('장면 보드 목록을 읽을 수 없습니다.')
  return raw.map(parse)
}

function readText(raw: unknown) {
  if (typeof raw !== 'string') throw new Error('장면 보드 내용을 읽을 수 없습니다.')
  return raw
}

function readSnapshot(raw: unknown) {
  const snapshot = readRecord(raw)
  return {
    text: readString(snapshot, 'text'),
    chapterTitle: readString(snapshot, 'chapterTitle'),
    revision: readInteger(readRecord(snapshot.selection), 'revision', 1),
    settings: readArray(snapshot.settings, (item) => {
      const setting = readRecord(item)
      return {
        id: readInteger(setting, 'id', 1),
        title: readString(setting, 'title'),
        content: readString(setting, 'content'),
      }
    }),
  }
}

function readBoard(raw: unknown) {
  const board = readRecord(raw)
  const result = board.result == null ? null : readRecord(board.result)
  return {
    id: readString(board, 'id'),
    status: readString(board, 'status'),
    snapshot: readSnapshot(board.snapshot),
    result: result && {
      facts: readArray(result.facts, (item) => {
        const fact = readRecord(item)
        return {
          description: readString(fact, 'description'),
          quote: readString(fact, 'quote'),
          start: readInteger(fact, 'start'),
          end: readInteger(fact, 'end', 1),
        }
      }),
      inferences: readArray(result.inferences, readText),
      gaps: readArray(result.gaps, readText),
      options: readArray(result.options, readText),
      references: readArray(result.references, (item) => {
        const recommendation = readRecord(item)
        const reference = readRecord(recommendation.reference)
        const url = new URL(readString(reference, 'url'))
        if (url.protocol !== 'https:' || url.hostname !== 'www.gutenberg.org')
          throw new Error('레퍼런스 출처를 확인할 수 없습니다.')
        return {
          id: readString(reference, 'id'),
          title: readString(reference, 'title'),
          author: readString(reference, 'author'),
          location: readString(reference, 'location'),
          technique: readString(reference, 'technique'),
          question: readString(recommendation, 'question'),
          url: url.href,
        }
      }),
    },
  }
}

const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/scene-board'

export const sceneBoardClient = {
  async preview(workId: string, selection: SceneSelection) {
    const raw = readRecord(await httpClient.post(path(workId) + '/preview', selection))
    return { token: readString(raw, 'token'), snapshot: readSnapshot(raw.snapshot), selection }
  },
  async create(workId: string, selection: SceneSelection, token: string, requestId: string) {
    return readBoard(
      await httpClient.post(path(workId), { selection, token, requestId, consent: true }),
    )
  },
  async get(workId: string, id: string, signal?: AbortSignal) {
    return readBoard(await httpClient.get(path(workId) + '/' + encodeURIComponent(id), { signal }))
  },
  async list(workId: string, chapterId: string, signal: AbortSignal) {
    return readArray(
      await httpClient.get(path(workId), { params: { chapterId }, signal }),
      readText,
    )
  },
  async usage() {
    const rows = readArray(await httpClient.get('/ai-usage'), readRecord)
    const row = rows.find((value) => value.feature === 'SCENE_ANALYSIS')
    if (!row) throw new Error('AI 사용량을 확인할 수 없습니다.')
    return {
      remaining: Math.max(0, readInteger(row, 'limit', 1) - readInteger(row, 'used')),
      resetAt: readString(row, 'resetAt'),
    }
  },
}
