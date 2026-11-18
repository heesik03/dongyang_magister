import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../shared/httpClient'
import {
  revisionSuggestionClient,
  type RevisionPreview,
  type RevisionTarget,
} from './revisionSuggestionClient'

export function RevisionSuggestionPanel({
  workId,
  reviewId,
  stale,
  onApply,
  retry,
  saveStatus,
  onViewManuscript,
}: {
  workId: string
  reviewId: string
  stale: boolean
  onApply: (target: RevisionTarget, replacement: string) => void
  retry: () => void
  saveStatus: string
  onViewManuscript: () => void
}) {
  const [preview, setPreview] = useState<RevisionPreview>()
  const [replacement, setReplacement] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [applied, setApplied] = useState(false)
  const [basisChanged, setBasisChanged] = useState(false)
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])
  function close() {
    request.current?.abort()
    request.current = null
    setBusy(false)
    setError('')
    setPreview(undefined)
    setReplacement('')
    setApplied(false)
  }
  async function load() {
    if (request.current || stale) return
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    setError('')
    try {
      if (preview) {
        const result = await revisionSuggestionClient.generate(
          workId,
          reviewId,
          preview,
          controller.signal,
        )
        if (!controller.signal.aborted) setReplacement(result)
      } else {
        const result = await revisionSuggestionClient.preview(workId, reviewId, controller.signal)
        if (!controller.signal.aborted) setPreview(result)
      }
    } catch (failure) {
      if (!controller.signal.aborted) {
        if (failure instanceof ApiError && failure.status === 409) setBasisChanged(true)
        setError(failure instanceof Error ? failure.message : '보완 문장을 요청하지 못했습니다.')
      }
    } finally {
      if (!controller.signal.aborted) {
        request.current = null
        setBusy(false)
      }
    }
  }
  function apply() {
    if (!preview || !replacement || busy) return
    try {
      onApply(preview.target, replacement)
      setApplied(true)
      setError('')
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '원고에 적용하지 못했습니다.')
    }
  }
  return (
    <section className="revision-suggestion" aria-label="검토 후보 보완 문장">
      {applied ? (
        <>
          <h4>원고에 반영했어요</h4>
          <blockquote>{replacement}</blockquote>
          <p role="status">
            {saveStatus} · 기존 검토와 판단 근거는 원고 버전 {preview?.target.revision} 기준입니다.
          </p>
          <button onClick={retry}>다시 검토</button>
          <button onClick={onViewManuscript}>원고 보기</button>
        </>
      ) : stale || basisChanged ? (
        <>
          <p role="status">원고 또는 근거가 변경되어 이전 제안을 적용할 수 없습니다.</p>
          <button onClick={retry}>현재 원고로 다시 검토</button>
          <button onClick={onViewManuscript}>원고로 돌아가기</button>
        </>
      ) : (
        <>
          {preview && (
            <>
              <h4>{replacement ? '보완 문장 비교' : 'Gemini에 보낼 자료'}</h4>
              {replacement ? (
                <div className="revision-comparison">
                  <div>
                    <strong>원래 문장</strong>
                    <blockquote>{preview.target.quote}</blockquote>
                  </div>
                  <div>
                    <strong>AI 보완 문장</strong>
                    <blockquote>{replacement}</blockquote>
                  </div>
                </div>
              ) : (
                <>
                  <strong>원고 구간</strong>
                  <blockquote>
                    {preview.before}
                    <mark>{preview.target.quote}</mark>
                    {preview.after}
                  </blockquote>
                  <p>검토 이유 · {preview.reason}</p>
                  <p>보완 방향 · {preview.direction}</p>
                </>
              )}
              <strong>참고할 확정 설정</strong>
              {preview.settings.length ? (
                preview.settings.map((s, i) => (
                  <p key={i}>
                    {s.title} · {s.quote}
                  </p>
                ))
              ) : (
                <p>이 후보에 연결된 확정 설정 인용이 없습니다.</p>
              )}
            </>
          )}
          <div className="decision-actions">
            {replacement && (
              <button onClick={apply} disabled={busy}>
                원고에 적용
              </button>
            )}
            <button onClick={load} disabled={busy}>
              {preview ? (replacement ? '다시 제안' : 'Gemini에 제안 요청') : '보완 문장 제안받기'}
            </button>
            {(preview || busy || error) && <button onClick={close}>닫기</button>}
          </div>
        </>
      )}
      {busy && (
        <p role="status">
          {preview ? '보완 문장을 생성하고 있습니다.' : '전송 범위를 확인하고 있습니다.'}
        </p>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  )
}
