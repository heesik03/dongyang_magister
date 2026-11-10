import { httpClient } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'

export type SceneSelection = {
  chapterId: string
  revision: number
  start: number
  end: number
  settingIds: number[]
}

export type SceneImageInput = {
  kind: 'LOCATION' | 'PROP'
  confirmed: string
  direction: string
}

export type SceneImageJob = {
  id: string
  status: string
  revision: number
  kind: 'LOCATION' | 'PROP'
  confirmed: string
  direction: string
}

export type SceneImagePreview = {
  input: SceneImageInput
  token: string
  revision: number
}

function readArray<T>(raw: unknown, parse: (item: unknown) => T): T[] {
  if (!Array.isArray(raw)) throw new Error('장면 보드 목록을 읽을 수 없습니다.')
  return raw.map(parse)
}

function readText(raw: unknown) {
  if (typeof raw !== 'string') throw new Error('장면 보드 내용을 읽을 수 없습니다.')
  return raw
}

function readImageKind(raw: string): 'LOCATION' | 'PROP' {
  if (raw === 'LOCATION' || raw === 'PROP') return raw
  throw new Error('콘셉트 이미지 유형을 읽을 수 없습니다.')
}

function readImageJob(raw: unknown): SceneImageJob {
  const job = readRecord(raw)
  const input = readRecord(job.input)
  return {
    id: readString(job, 'id'),
    status: readString(job, 'status'),
    revision: readInteger(job, 'revision', 1),
    kind: readImageKind(readString(input, 'kind')),
    confirmed: readString(input, 'confirmed'),
    direction: readString(input, 'direction'),
  }
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
const imagePath = (workId: string, boardId: string) =>
  path(workId) + '/' + encodeURIComponent(boardId) + '/images'

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
  async previewImage(
    workId: string,
    boardId: string,
    input: SceneImageInput,
  ): Promise<SceneImagePreview> {
    const raw = readRecord(await httpClient.post(imagePath(workId, boardId) + '/preview', input))
    const confirmed = readRecord(raw.input)
    if (
      confirmed.kind !== input.kind ||
      confirmed.confirmed !== input.confirmed ||
      confirmed.direction !== input.direction
    )
      throw new Error('전송할 이미지 설명이 변경되었습니다.')
    return {
      input,
      token: readString(raw, 'token'),
      revision: readInteger(raw, 'revision', 1),
    }
  },
  async createImage(
    workId: string,
    boardId: string,
    input: SceneImageInput,
    token: string,
    requestId: string,
  ): Promise<SceneImageJob> {
    return readImageJob(
      await httpClient.post(imagePath(workId, boardId), {
        input,
        token,
        requestId,
        consent: true,
      }),
    )
  },
  async listImages(
    workId: string,
    boardId: string,
    signal?: AbortSignal,
  ): Promise<SceneImageJob[]> {
    const result = await httpClient.get(imagePath(workId, boardId), { signal })
    if (!Array.isArray(result)) throw new Error('시안 이력 응답이 올바르지 않습니다.')
    return result.map(readImageJob)
  },
  async imageContent(workId: string, boardId: string, imageId: string, signal?: AbortSignal) {
    const file = await httpClient.get(
      imagePath(workId, boardId) + '/' + encodeURIComponent(imageId) + '/content',
      {
        responseType: 'blob',
        signal,
      },
    )
    if (!(file instanceof Blob) || file.type !== 'image/png')
      throw new Error('이미지 응답 형식이 올바르지 않습니다.')
    return file
  },
  async usage(feature: 'SCENE_ANALYSIS' | 'SCENE_IMAGE' = 'SCENE_ANALYSIS') {
    const rows = readArray(await httpClient.get('/ai-usage'), readRecord)
    const row = rows.find((value) => value.feature === feature)
    if (!row) throw new Error('AI 사용량을 확인할 수 없습니다.')
    return {
      remaining: Math.max(0, readInteger(row, 'limit', 1) - readInteger(row, 'used')),
      resetAt: readString(row, 'resetAt'),
    }
  },
}
