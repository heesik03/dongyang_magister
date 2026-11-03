import { useEffect, useRef, useState } from 'react'
import { Dialog } from '../../shared/Dialog'
import { extractionClient, type ExtractionSource } from './extractionClient'
import type { StoryEvent } from '../works/workTypes'

export function ExtractionSourceButton({ workId, event }: { workId: string; event: StoryEvent }) {
  const [source, setSource] = useState<ExtractionSource | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const request = useRef<AbortController | null>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  useEffect(() => () => request.current?.abort(), [])
  useEffect(() => {
    if (source && event.startOffset != null && event.endOffset != null) {
      textarea.current?.focus()
      textarea.current?.setSelectionRange(event.startOffset, event.endOffset)
    }
  }, [source, event.startOffset, event.endOffset])
  async function open() {
    if (!event.extractionId || busy) return
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    setError('')
    try {
      const sources = await extractionClient.sources(workId, event.extractionId, controller.signal)
      const found = sources.find((source) => source.id === event.chapterId)
      if (
        !found ||
        found.revision !== event.sourceRevision ||
        event.startOffset == null ||
        event.endOffset == null ||
        found.content.slice(event.startOffset, event.endOffset) !== event.quote
      )
        throw new Error('근거 원문과 위치가 일치하지 않습니다.')
      if (!controller.signal.aborted) setSource(found)
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : '근거 조회 실패')
    } finally {
      if (!controller.signal.aborted) setBusy(false)
    }
  }
  return (
    <>
      <button disabled={busy} onClick={() => void open()}>
        {busy ? '근거 조회 중…' : '추출 당시 원문'}
      </button>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {source && (
        <Dialog title="추출 당시 근거 원문" size="wide" onClose={() => setSource(null)}>
          <div className="stack">
            <p>
              {source.title} · 원고 버전 {source.revision} · {event.startOffset}–{event.endOffset}
            </p>
            <blockquote>{event.quote}</blockquote>
            <label>
              추출 당시 원고
              <textarea ref={textarea} rows={12} readOnly value={source.content} />
            </label>
          </div>
        </Dialog>
      )}
    </>
  )
}
