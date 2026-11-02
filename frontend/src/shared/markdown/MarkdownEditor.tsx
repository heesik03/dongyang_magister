import { useId, useRef, useState, type RefObject, type ReactNode } from 'react'
import { MarkdownPreview } from './MarkdownPreview'
import { formatMarkdown, type MarkdownFormat } from './markdownFormatting'

const tools: { label: string; title: string; format: MarkdownFormat }[] = [
  ...(['h1', 'h2', 'h3', 'h4'] as const).map((format) => ({
    label: format,
    title: format + ' 제목',
    format,
  })),
  { label: 'B', title: '굵게', format: 'bold' },
  { label: 'I', title: '기울임', format: 'italic' },
  { label: '취소선', title: '취소선', format: 'strike' },
  { label: '인용', title: '인용', format: 'quote' },
  { label: '목록', title: '글머리 목록', format: 'bullet' },
  { label: '번호 목록', title: '번호 목록', format: 'number' },
]
export function MarkdownEditor({
  value,
  header,
  defaultValue = '',
  onChange,
  inputRef,
  name,
  label,
  maxLength = 200000,
  disabled = false,
  placeholder,
  fontSize = 18,
  lineHeight = 1.8,
  onEdit,
  previewMode,
  onPreviewModeChange,
}: {
  header?: ReactNode
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  inputRef?: RefObject<HTMLTextAreaElement | null>
  name?: string
  label: string
  maxLength?: number
  disabled?: boolean
  placeholder?: string
  fontSize?: number
  lineHeight?: number
  onEdit?: () => void
  previewMode?: boolean
  onPreviewModeChange?: (preview: boolean) => void
}) {
  const [draft, setDraft] = useState(defaultValue)
  const [localPreview, setLocalPreview] = useState(false)
  const preview = previewMode ?? localPreview
  function setPreview(next: boolean) {
    if (previewMode === undefined) setLocalPreview(next)
    onPreviewModeChange?.(next)
  }
  const [notice, setNotice] = useState('')
  const localRef = useRef<HTMLTextAreaElement>(null)
  const ref = inputRef ?? localRef
  const id = useId()
  const content = value ?? draft
  function change(next: string) {
    if (next.length > maxLength) {
      setNotice('입력 가능한 글자 수를 초과했습니다.')
      return false
    }
    if (value === undefined) setDraft(next)
    onChange?.(next)
    setNotice('')
    return true
  }
  function format(format: MarkdownFormat) {
    const area = ref.current
    if (!area || disabled) return
    const result = formatMarkdown(content, area.selectionStart, area.selectionEnd, format)
    if (!change(result.value)) return
    // 도구 버튼으로 수정해도 모달의 작성 중 닫기 확인이 동작해야 한다.
    area.dispatchEvent(new Event('input', { bubbles: true }))
    queueMicrotask(() => {
      area.focus()
      area.setSelectionRange(result.start, result.end)
    })
  }
  return (
    <div className="markdown-editor" style={{ fontSize, lineHeight }}>
      {header}
      <div className="markdown-toolbar">
        <div className="markdown-modes" role="group" aria-label="편집 보기">
          <button
            type="button"
            aria-pressed={!preview}
            onClick={() => {
              setPreview(false)
              onEdit?.()
            }}
          >
            편집
          </button>
          <button type="button" aria-pressed={preview} onClick={() => setPreview(true)}>
            미리보기
          </button>
        </div>
        {!preview && (
          <div className="markdown-tools" role="group" aria-label="마크다운 서식">
            {tools.map((tool) => (
              <button
                type="button"
                key={tool.format}
                aria-label={tool.title}
                disabled={disabled}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => format(tool.format)}
              >
                {tool.label}
              </button>
            ))}
          </div>
        )}
      </div>
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <textarea
        id={id}
        ref={ref}
        name={name}
        onInvalid={(event) => {
          event.preventDefault()
          setPreview(false)
          setNotice(label + '을 입력해 주세요.')
          queueMicrotask(() => ref.current?.focus())
        }}
        value={content}
        hidden={preview}
        disabled={disabled}
        required={!!name}
        maxLength={maxLength}
        placeholder={placeholder}
        style={{ fontSize, lineHeight }}
        onChange={(event) => change(event.target.value)}
      />
      {preview && (
        <div className="markdown-preview-scroll" aria-label={label + ' 미리보기'}>
          <MarkdownPreview content={content} />
        </div>
      )}
      {notice && (
        <p role="status" className="error">
          {notice}
        </p>
      )}
    </div>
  )
}
