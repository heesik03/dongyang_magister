import { useState } from 'react'
import { Icon } from '../../shared/Icon'
import { aiWritingClient, type WritingAction } from './aiWritingClient'

const ACTION_LABELS: Record<WritingAction, string> = {
  CONTINUE: '다음 문장 이어쓰기',
  DESCRIBE: '장면 묘사 보강',
  DIALOGUE: '대사 다듬기',
}

export function AiWritingAssistant({
  content,
  disabled,
  onApply,
}: {
  content: string
  disabled?: boolean
  onApply: (text: string) => void
}) {
  const [busy, setBusy] = useState(false)
  const [suggestion, setSuggestion] = useState('')
  const [error, setError] = useState('')

  async function runAction(action: WritingAction) {
    if (busy || disabled) return
    if (!content.trim()) {
      setError('원고를 먼저 입력해 주세요.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const result = await aiWritingClient.assist(content, action)
      setSuggestion(result)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'AI 집필 보조 요청에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="AI 집필 도우미">
      <h3>
        <Icon name="spark" />
        AI 집필 도우미
      </h3>
      <p className="small muted">현재 원고의 마지막 흐름을 바탕으로 문장 제안을 받아보세요.</p>
      <div className="stack">
        {(['CONTINUE', 'DESCRIBE', 'DIALOGUE'] as const).map((action) => (
          <button
            key={action}
            type="button"
            disabled={busy || disabled || !content.trim()}
            onClick={() => void runAction(action)}
          >
            {ACTION_LABELS[action]}
          </button>
        ))}
      </div>
      {busy && <p role="status">문장을 다듬고 있습니다…</p>}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {suggestion && (
        <div className="notice small stack">
          <strong>AI 제안 문장</strong>
          <p style={{ whiteSpace: 'pre-wrap' }}>{suggestion}</p>
          <div className="dialog-actions">
            <button type="button" onClick={() => setSuggestion('')}>
              닫기
            </button>
            <button
              type="button"
              className="primary"
              onClick={() => {
                onApply(suggestion)
                setSuggestion('')
              }}
            >
              원고 끝에 추가
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
