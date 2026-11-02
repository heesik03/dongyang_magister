import { httpClient } from '../../shared/httpClient'
import { readRecord, readString } from '../../shared/responseReader'
import { parseChapter } from './chapterClient'

export type ManuscriptFormat = 'txt' | 'md' | 'pdf' | 'docx'
export interface ImportPreview {
  workTitle: string
  genre: string
  chapterTitle: string
  content: string
  format: string
}
const path = (workId: string) => '/work/' + encodeURIComponent(workId) + '/chapter'
export function validateManuscriptFile(file: File) {
  if (!/\.(txt|md|markdown|pdf|docx)$/i.test(file.name))
    throw new Error('TXT, Markdown, PDF, DOCX 파일을 선택해 주세요.')
  if (!file.size) throw new Error('내용이 있는 파일을 선택해 주세요.')
  // 서버의 8MB 요청 제한에는 multipart 헤더도 포함된다.
  if (file.size > 8 * 1024 * 1024 - 64 * 1024) throw new Error('파일은 8MB 미만으로 준비해 주세요.')
}
function fileBody(file: File) {
  validateManuscriptFile(file)
  const body = new FormData()
  body.set('file', file)
  return body
}
export const manuscriptTransferClient = {
  async preview(workId: string, file: File, signal?: AbortSignal): Promise<ImportPreview> {
    const value = readRecord(
      await httpClient.post(path(workId) + '/import/parse', fileBody(file), { signal }),
    )
    const result = {
      workTitle: readString(value, 'workTitle'),
      genre: readString(value, 'genre'),
      chapterTitle: readString(value, 'chapterTitle'),
      content: readString(value, 'content'),
      format: readString(value, 'format'),
    }
    if (result.content.length > 200000 || result.chapterTitle.length > 100)
      throw new Error('가져온 원고의 길이를 확인해 주세요.')
    return result
  },
  async importFile(
    workId: string,
    file: File,
    input: {
      id: string
      mutationId: string
      title: string
      version?: number
    },
  ) {
    const body = fileBody(file)
    body.set('title', input.title)
    body.set('mutationId', input.mutationId)
    if (input.version === undefined) {
      body.set('id', input.id)
      return parseChapter(await httpClient.post(path(workId) + '/import', body))
    }
    body.set('version', String(input.version))
    return parseChapter(
      await httpClient.put(path(workId) + '/' + encodeURIComponent(input.id) + '/import', body),
    )
  },
  async exportFile(
    workId: string,
    chapterId: string | null,
    format: ManuscriptFormat,
  ): Promise<Blob> {
    const result = await httpClient.get(
      chapterId === null
        ? '/work/' + encodeURIComponent(workId) + '/export'
        : path(workId) + '/' + encodeURIComponent(chapterId) + '/export',
      {
        params: { format },
        responseType: 'blob',
      },
    )
    if (!(result instanceof Blob) || !result.size)
      throw new Error('내보낼 파일을 확인하지 못했습니다.')
    return result
  },
}
