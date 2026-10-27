import { useCallback, useRef } from 'react'
import { Dialog } from '../../shared/Dialog'
import type { ReviewSource, SourceLocation } from './reviewClient'

export function ReviewSourceDialog({
  source,
  location,
  onClose,
}: {
  source: ReviewSource
  location: SourceLocation
  onClose: () => void
}) {
  const editor = useRef<HTMLTextAreaElement>(null)
  // 네이티브 dialog가 열린 직후 선택해 첫 포커스와 정확한 인용 범위가 함께 보이게 한다.
  const selectSource = useCallback(() => {
    editor.current?.focus()
    editor.current?.setSelectionRange(location.startOffset, location.endOffset)
  }, [location.startOffset, location.endOffset])
  return (
    <Dialog title="분석 당시 근거 원문" onClose={onClose} onOpen={selectSource}>
      <p>
        <strong>{source.title}</strong> · 버전 {source.revision}
      </p>
      <p className="notice small">
        분석 당시 고정한 자료입니다. 현재 편집 중인 원고와 다를 수 있습니다.
      </p>
      <blockquote style={{ overflowWrap: 'anywhere' }}>{location.quote}</blockquote>
      <p className="small muted">
        UTF-16 위치 {location.startOffset}–{location.endOffset}
      </p>
      <label>
        분석 당시 원문
        <textarea
          ref={editor}
          readOnly
          value={source.content}
          rows={12}
          style={{ width: '100%', minWidth: 0 }}
        />
      </label>
      <div className="dialog-actions">
        <button onClick={onClose}>원문 닫기</button>
      </div>
    </Dialog>
  )
}
