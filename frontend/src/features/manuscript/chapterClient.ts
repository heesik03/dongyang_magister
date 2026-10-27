import { httpClient } from '../../shared/httpClient'
import { parsePage, readInteger, readRecord, readString } from '../../shared/responseReader'
import type { Chapter } from '../works/workTypes'

export interface SavedChapter extends Chapter {
  version: number
  chapterNo: number
  updatedAt: string
  workVersion: number
}
export const chapterSignature = (chapter: Chapter) =>
  JSON.stringify([chapter.title, chapter.content])
export function parseChapter(raw: unknown): SavedChapter {
  const value = readRecord(raw)
  const id = readString(value, 'id'),
    title = readString(value, 'title'),
    content = readString(value, 'content')
  if (
    !id.trim() ||
    id.length > 64 ||
    !title.trim() ||
    title.length > 100 ||
    content.length > 200000
  )
    throw new Error('회차 응답 형식이 올바르지 않습니다.')
  return {
    id,
    title,
    content,
    version: readInteger(value, 'version'),
    chapterNo: readInteger(value, 'chapterNo', 1),
    revision: readInteger(value, 'revision', 1),
    updatedAt: readString(value, 'updatedAt'),
    workVersion: readInteger(value, 'workVersion'),
  }
}
const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/chapter'
const mutations = new Map<string, { body: string; id: string; version?: number }>()
export const chapterClient = {
  rebase(workId: string, chapterId: string) {
    mutations.delete(workId + ':' + chapterId)
  },
  forget(workId: string) {
    for (const key of mutations.keys()) if (key.startsWith(workId + ':')) mutations.delete(key)
  },
  reset() {
    mutations.clear()
  },
  async listAll(workId: string, signal?: AbortSignal): Promise<SavedChapter[]> {
    const chapters: SavedChapter[] = []
    for (let page = 0; page < 5; page++) {
      const result = parsePage(
        await httpClient.get(path(workId), { signal, params: { page, size: 20 } }),
        parseChapter,
      )
      if (result.page !== page || result.totalElements > 100)
        throw new Error('회차 목록 범위가 올바르지 않습니다.')
      chapters.push(...result.items)
      if (page + 1 >= result.totalPages) {
        if (
          new Set(chapters.map((chapter) => chapter.id)).size !== chapters.length ||
          chapters.length !== result.totalElements
        )
          throw new Error('회차 목록이 변경되었습니다. 다시 불러와 주세요.')
        return chapters
      }
    }
    throw new Error('회차 목록 조회 한도를 초과했습니다.')
  },
  async get(workId: string, chapterId: string): Promise<SavedChapter> {
    return parseChapter(await httpClient.get(path(workId) + '/' + encodeURIComponent(chapterId)))
  },
  async save(workId: string, chapter: Chapter): Promise<SavedChapter> {
    const key = workId + ':' + chapter.id,
      body = chapterSignature(chapter),
      previous = mutations.get(key)
    const mutation =
      previous?.body === body
        ? previous
        : { body, id: crypto.randomUUID(), version: chapter.version }
    mutations.set(key, mutation)
    const input = { title: chapter.title, content: chapter.content, mutationId: mutation.id }
    const response = parseChapter(
      mutation.version === undefined
        ? await httpClient.post(path(workId), { ...input, id: chapter.id })
        : await httpClient.put(path(workId) + '/' + encodeURIComponent(chapter.id), {
            ...input,
            version: mutation.version,
          }),
    )
    if (mutations.get(key) === mutation) mutations.delete(key)
    return response
  },
}
