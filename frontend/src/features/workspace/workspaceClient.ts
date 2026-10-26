import { httpClient } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'
import { isWorks } from '../works/workStorage'
import type { Work } from '../works/workTypes'
export type WorkspaceContent = Pick<Work, 'chapters' | 'analyses' | 'events'>
export interface WorkspaceResponse {
  workVersion: number
  version: number
  content: WorkspaceContent
  updatedAt: string
}
function parseWorkspace(raw: unknown): WorkspaceResponse {
  const value = readRecord(raw)
  const content = readRecord(value.content)
  // 브라우저 초안과 서버 문서는 같은 도메인 구조로 검증한다.
  const candidate: unknown = [
    {
      ...content,
      id: 'validation',
      title: '',
      genre: '',
      era: '',
      description: '',
      updatedAt: '',
      settings: [],
    },
  ]
  if (!isWorks(candidate)) throw new Error('원고·검토·타임라인 응답 형식이 올바르지 않습니다.')
  return {
    version: readInteger(value, 'version'),
    workVersion: readInteger(value, 'workVersion'),
    content: {
      chapters: candidate[0].chapters,
      analyses: candidate[0].analyses,
      events: candidate[0].events,
    },
    updatedAt: readString(value, 'updatedAt'),
  }
}
export const workspaceClient = {
  async get(workId: string, signal?: AbortSignal): Promise<WorkspaceResponse> {
    return parseWorkspace(
      await httpClient.get('/work/' + encodeURIComponent(workId) + '/workspace', { signal }),
    )
  },
  async save(
    workId: string,
    version: number,
    mutationId: string,
    content: WorkspaceContent,
  ): Promise<WorkspaceResponse> {
    return parseWorkspace(
      await httpClient.put('/work/' + encodeURIComponent(workId) + '/workspace', {
        version,
        mutationId,
        separateChapters: true,
        content: { ...content, chapters: [] },
      }),
    )
  },
}
