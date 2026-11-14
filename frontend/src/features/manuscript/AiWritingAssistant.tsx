import { useEffect, useRef, useState } from 'react'
import { Icon } from '../../shared/Icon'
import type { Setting } from '../works/workTypes'
import { aiWritingClient, type WritingAction } from './aiWritingClient'

const ACTION_LABELS: Record<WritingAction, string> = {
  CONTINUE: '다음 문장 이어쓰기',
  DESCRIBE: '장면 묘사 보강',
  DIALOGUE: '대사 다듬기',
}

export function AiWritingAssistant({
  workId,
  content,
  confirmedSettings,
  getSelection,
  disabled,
  onApply,
}: {
  workId: string
  content: string
  confirmedSettings: Setting[]
  getSelection: () => { start: number; end: number } | null
  disabled?: boolean
  onApply: (text: string) => boolean
}) {
  const [busy, setBusy] = useState(false)
  const [suggestion, setSuggestion] = useState<{
    text: string
    start: number
    end: number
    action: WritingAction
  } | null>(null)
  const [error, setError] = useState('')
  const [settingIds, setSettingIds] = useState<string[]>([])
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])

  async function runAction(action: WritingAction) {
    if (busy || disabled) return
    if (!content.trim()) {
      setError('원고를 먼저 입력해 주세요.')
      return
    }
    const selection =
      action === 'CONTINUE' ? { start: content.length, end: content.length } : getSelection()
    if (
      !selection ||
      (action !== 'CONTINUE' && !content.slice(selection.start, selection.end).trim())
    ) {
      setError('원고 편집기에서 다듬을 문장을 먼저 선택해 주세요.')
      return
    }
    const { start, end } = selection
    if (end - start > 1800) {
      setError('선택한 문장을 1,800자 이하로 줄여 주세요.')
      return
    }
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    setError('')
    setSuggestion(null)
    try {
      const text = await aiWritingClient.assist(
        workId,
        {
          action,
          excerpt:
            action === 'CONTINUE' ? content.trimEnd().slice(-1800) : content.slice(start, end),
          before: action === 'CONTINUE' ? '' : content.slice(Math.max(0, start - 300), start),
          after: action === 'CONTINUE' ? '' : content.slice(end, end + 300),
          settingIds: confirmedSettings
            .filter((setting) => settingIds.includes(setting.id))
            .map(({ id }) => id),
        },
        controller.signal,
      )
      if (!controller.signal.aborted) setSuggestion({ text, start, end, action })
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : 'AI 집필 보조 요청에 실패했습니다.')
    } finally {
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  function apply() {
    if (!suggestion || disabled) return
    const { start, end, text, action } = suggestion
    const separator = action === 'CONTINUE' && !content.endsWith('\n') ? '\n\n' : ''
    const result = content.slice(0, start) + separator + text + content.slice(end)
    if (result.length > 200000) {
      setError('제안을 적용하면 원고의 20만 자 제한을 초과합니다.')
      return
    }
    if (!onApply(result)) {
      setError('원고가 변경되었습니다. 최신 원고에서 새 제안을 받아 주세요.')
      return
    }
    setSuggestion(null)
  }

  return (
    <section aria-label="AI 집필 도우미">
      <h3>
        <Icon name="spark" />
        AI 집필 도우미
      </h3>
      <p className="small muted">
        이어쓰기는 원고 끝 최대 1,800자를, 묘사·대사 편집은 선택한 문장과 앞뒤 최대 300자씩을
        Gemini에 전송합니다. 선택한 확정 설정의 제목과 본문도 함께 전송합니다.
      </p>
      {confirmedSettings.length > 0 && (
        <details className="workspace-disclosure">
          <summary>참고할 확정 설정 선택 (최대 5개)</summary>
          <fieldset disabled={busy || disabled}>
            <legend>Gemini에 보낼 설정</legend>
            {confirmedSettings.map(({ id, title }) => (
              <label className="check" key={id}>
                <input
                  type="checkbox"
                  checked={settingIds.includes(id)}
                  disabled={settingIds.length >= 5 && !settingIds.includes(id)}
                  onChange={(event) => {
                    const checked = event.target.checked
                    setSettingIds((ids) =>
                      checked ? [...ids, id] : ids.filter((value) => value !== id),
                    )
                  }}
                />
                {title}
              </label>
            ))}
          </fieldset>
        </details>
      )}
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
          <p style={{ whiteSpace: 'pre-wrap' }}>{suggestion.text}</p>
          <div className="dialog-actions">
            <button type="button" onClick={() => setSuggestion(null)}>
              닫기
            </button>
            <button type="button" className="primary" disabled={disabled} onClick={apply}>
              {suggestion.action === 'CONTINUE' ? '원고 끝에 추가' : '선택 구간에 적용'}
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
