import { useState } from 'react'
import { Dialog } from '../../shared/Dialog'
import { AnalysisMaterials } from './AnalysisMaterials'
import type { Preview } from './analysisClient'

export function AnalysisPreviewDialog({
  preview,
  submitting,
  error,
  onConfirm,
  onClose,
}: {
  preview: Preview
  submitting: boolean
  error: string
  onConfirm: () => void
  onClose: () => void
}) {
  const [consent, setConsent] = useState(false)
  return (
    <Dialog title="AI 전송 자료 확인" onClose={onClose}>
      <div className="stack">
        <p>아래 자료를 {preview.provider}로 전송합니다. 이 미리보기는 AI를 호출하지 않습니다.</p>
        <p className="small muted">
          원문을 펼쳐 전송 내용을 확인할 수 있습니다. 자료가 바뀌거나 10분이 지나면 다시 확인해야
          합니다.
        </p>
        <AnalysisMaterials input={preview.input} />
        <p className="notice small">{preview.retentionNotice}</p>
        <p className="small">{preview.deletionNotice}</p>
        <a href={preview.providerPolicyUrl} target="_blank" rel="noreferrer">
          Google Gemini 자료 이용·보관 정책
        </a>
        <label className="check">
          <input
            type="checkbox"
            checked={consent}
            disabled={submitting}
            onChange={(event) => setConsent(event.target.checked)}
          />
          위 원고·설정·판단과 보관 안내를 확인하고 Gemini 전송에 동의합니다.
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          <button disabled={submitting} onClick={onClose}>
            돌아가기
          </button>
          <button className="primary" disabled={!consent || submitting} onClick={onConfirm}>
            {submitting ? '접수 중…' : '동의하고 분석 시작'}
          </button>
        </div>
      </div>
    </Dialog>
  )
}
