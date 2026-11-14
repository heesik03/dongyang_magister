import { httpClient } from '../../shared/httpClient'
import { readRecord, readString } from '../../shared/responseReader'

export type WritingAction = 'CONTINUE' | 'DESCRIBE' | 'DIALOGUE'

export interface WritingInput {
  action: WritingAction
  excerpt: string
  before: string
  after: string
  settingIds: string[]
}

export const aiWritingClient = {
  async assist(workId: string, input: WritingInput, signal: AbortSignal): Promise<string> {
    const response = readRecord(
      await httpClient.post('/work/' + encodeURIComponent(workId) + '/writing-assist', input, {
        signal,
      }),
    )
    const suggestion = readString(response, 'text').trim()
    if (!suggestion) throw new Error('AI가 제안 문장을 반환하지 않았습니다.')
    return suggestion
  },
}
