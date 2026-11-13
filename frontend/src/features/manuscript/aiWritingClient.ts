import { httpClient } from '../../shared/httpClient'
import { readRecord, readString } from '../../shared/responseReader'

export type WritingAction = 'CONTINUE' | 'DESCRIBE' | 'DIALOGUE'

export const aiWritingClient = {
  async assist(
    prompt: string,
    action: WritingAction,
    signal?: AbortSignal,
  ): Promise<string> {
    const snippet = prompt.trim().slice(-1800)
    if (!snippet) throw new Error('보조할 원고 내용을 먼저 입력해 주세요.')
    const response = readRecord(
      await httpClient.post(
        '/gemini',
        { prompt: snippet, action },
        { signal },
      ),
    )
    return readString(response, 'text')
  },
}
