import { httpClient } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'

function readTarget(raw: unknown) {
  const data = readRecord(raw)
  const quote = readString(data, 'quote')
  const startOffset = readInteger(data, 'startOffset')
  const endOffset = readInteger(data, 'endOffset', 1)
  if (!quote.trim() || endOffset - startOffset !== quote.length) {
    throw new Error('원문 위치를 확인할 수 없습니다.')
  }
  return {
    chapterId: readString(data, 'chapterId'),
    revision: readInteger(data, 'revision'),
    quote,
    startOffset,
    endOffset,
  }
}
export type RevisionTarget = ReturnType<typeof readTarget>
const path = (workId: string, reviewId: string) =>
  '/work/' +
  encodeURIComponent(workId) +
  '/review/' +
  encodeURIComponent(reviewId) +
  '/revision-suggestion'

export const revisionSuggestionClient = {
  async preview(workId: string, reviewId: string, signal: AbortSignal): Promise<RevisionPreview> {
    const data = readRecord(await httpClient.get(path(workId, reviewId) + '/context', { signal }))
    const material = readRecord(data.material)
    const target = readTarget(data.target)
    const contextHash = readString(data, 'contextHash')
    const previousRaw = material.previous ?? []
    if (
      !/^[0-9a-f]{64}$/.test(contextHash) ||
      readString(material, 'quote') !== target.quote ||
      !Array.isArray(material.settings) ||
      !Array.isArray(previousRaw)
    ) {
      throw new Error('전송 범위를 확인할 수 없습니다.')
    }
    return {
      target,
      contextHash,
      before: readString(material, 'before'),
      after: readString(material, 'after'),
      reason: readString(material, 'reason'),
      direction: readString(material, 'direction'),
      settings: material.settings.map((raw) => {
        const setting = readRecord(raw)
        return { title: readString(setting, 'title'), quote: readString(setting, 'quote') }
      }),
      previous: previousRaw.map((raw) => {
        const item = readRecord(raw)
        return {
          title: readString(item, 'title'),
          quote: readString(item, 'quote'),
          explanation: readString(item, 'explanation'),
        }
      }),
    }
  },
  async generate(workId: string, reviewId: string, preview: RevisionPreview, signal: AbortSignal) {
    const data = readRecord(
      await httpClient.post(
        path(workId, reviewId),
        { contextHash: preview.contextHash },
        { signal, timeout: 45000 },
      ),
    )
    const target = readTarget(data.target)
    const confirmed = preview.target
    const replacement = readString(data, 'replacement')
    if (
      !replacement.trim() ||
      replacement.length > 10000 ||
      replacement === confirmed.quote ||
      target.chapterId !== confirmed.chapterId ||
      target.revision !== confirmed.revision ||
      target.startOffset !== confirmed.startOffset ||
      target.endOffset !== confirmed.endOffset ||
      target.quote !== confirmed.quote
    ) {
      throw new Error('제안과 확인한 원문이 일치하지 않습니다.')
    }
    return replacement
  },
}
export interface RevisionPreview {
  target: RevisionTarget
  contextHash: string
  before: string
  after: string
  reason: string
  direction: string
  settings: { title: string; quote: string }[]
  previous?: { title: string; quote: string; explanation: string }[]
}
