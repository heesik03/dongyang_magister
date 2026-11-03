import { useEffect, useRef, useState } from 'react'
import { Dialog } from '../../shared/Dialog'
import { ApiError } from '../../shared/httpClient'
import type { Chapter, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import {
  manuscriptTransferClient,
  type ImportPreview,
  type ManuscriptFormat,
} from './manuscriptTransferClient'

export function ManuscriptTransfer({
  work,
  chapter,
  onImported,
}: {
  work: Work
  chapter?: Chapter
  onImported: (id: string) => void
}) {
  const [mode, setMode] = useState<'import' | 'export' | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [title, setTitle] = useState('')
  const [target, setTarget] = useState('new')
  const [confirmed, setConfirmed] = useState(false)
  const [format, setFormat] = useState<ManuscriptFormat>('txt')
  const [scope, setScope] = useState('chapter')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const request = useRef<AbortController | null>(null)
  const active = useRef(true)
  // 응답을 잃은 뒤 재시도해도 같은 작업 ID를 보내 회차의 중복 생성을 막는다.
  const mutation = useRef<{ key: string; id: string; mutationId: string; version?: number } | null>(
    null,
  )
  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
      request.current?.abort()
    }
  }, [])
  function open(next: 'import' | 'export') {
    setMode(next)
    setError('')
    setMessage('')
    setPreview(null)
    setFile(null)
    setTarget('new')
    setScope('chapter')
    setConfirmed(false)
    mutation.current = null
  }
  async function selectFile(next?: File) {
    request.current?.abort()
    setFile(next ?? null)
    setPreview(null)
    setError('')
    setConfirmed(false)
    mutation.current = null
    if (!next) {
      setBusy(false)
      return
    }
    const controller = new AbortController()
    request.current = controller
    setBusy(true)
    try {
      const result = await manuscriptTransferClient.preview(work.id, next, controller.signal)
      if (!controller.signal.aborted) {
        setPreview(result)
        setTitle(result.chapterTitle)
      }
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : '파일 확인 실패')
    } finally {
      if (active.current && !controller.signal.aborted) setBusy(false)
    }
  }
  async function execute() {
    if (
      busy ||
      (mode === 'import' &&
        (!file || !preview || !title.trim() || (target === 'replace' && !confirmed)))
    )
      return
    setBusy(true)
    setError('')
    const account = useWorksStore.getState().account
    try {
      await useWorksStore.getState().flushWorkspace(work.id)
      const state = useWorksStore.getState()
      if (!active.current || state.account !== account) return
      if (state.dirtyIds.includes(work.id) || state.conflicts.includes(work.id))
        throw new Error('현재 입력을 먼저 서버에 저장하고 충돌을 해결해 주세요.')
      const current = state.works
        .find((item) => item.id === work.id)
        ?.chapters.find((item) => item.id === chapter?.id)
      if (mode === 'export') {
        if (scope === 'chapter' && (!current || current.version === undefined))
          throw new Error('저장된 회차를 선택해 주세요.')
        const blob = await manuscriptTransferClient.exportFile(
          work.id,
          scope === 'work' ? null : (current?.id ?? null),
          format,
        )
        if (!active.current || useWorksStore.getState().account !== account) return
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        const safeName = Array.from(
          `${work.title}_${scope === 'work' ? '전체 원고' : current?.title}`,
          (character) =>
            character.charCodeAt(0) < 32 || /[<>:"/\\|?*]/.test(character) ? '_' : character,
        )
          .join('')
          .trim()
          .replace(/[. ]+$/, '')
          .slice(0, 80)
        link.download = (safeName || '원고') + '.' + format
        document.body.appendChild(link)
        link.click()
        link.remove()
        setTimeout(() => URL.revokeObjectURL(url), 1000)
        setMode(null)
        setMessage('원고 파일 다운로드를 시작했습니다.')
      } else if (file && preview) {
        if (target === 'replace' && (!current || current.version === undefined))
          throw new Error('교체할 회차를 먼저 서버에 저장해 주세요.')
        const key = JSON.stringify([
          file.name,
          file.size,
          file.lastModified,
          title.trim(),
          target,
          current?.id,
        ])
        if (mutation.current?.key !== key)
          mutation.current = {
            key,
            id: target === 'replace' && current ? current.id : crypto.randomUUID(),
            mutationId: crypto.randomUUID(),
            version: target === 'replace' ? current?.version : undefined,
          }
        const result = await manuscriptTransferClient.importFile(work.id, file, {
          ...mutation.current,
          title: title.trim(),
        })
        if (!active.current || useWorksStore.getState().account !== account) return
        useWorksStore.getState().acceptImportedChapter(work.id, result)
        setMode(null)
        setMessage('원고를 가져와 서버에 저장했습니다.')
        onImported(result.id)
      }
    } catch (failure) {
      if (
        active.current &&
        useWorksStore.getState().account === account &&
        target === 'replace' &&
        failure instanceof ApiError &&
        failure.status === 409
      ) {
        const state = useWorksStore.getState()
        useWorksStore.setState({
          conflicts: [...new Set([...state.conflicts, work.id])],
          workspaceErrors: {
            ...state.workspaceErrors,
            [work.id]: '가져오기 중 서버 원고가 변경되었습니다. 충돌 원고를 비교해 주세요.',
          },
        })
      }
      if (active.current)
        setError(
          failure instanceof ApiError && failure.status === 409
            ? '서버 원고가 변경되었습니다. 입력과 파일은 유지됩니다. 닫은 뒤 충돌 원고를 확인하고 다시 가져와 주세요.'
            : failure instanceof Error
              ? failure.message
              : '파일 처리에 실패했습니다.',
        )
    } finally {
      if (active.current) setBusy(false)
    }
  }
  return (
    <>
      <button onClick={() => open('import')}>원고 가져오기</button>
      <button disabled={!chapter} onClick={() => open('export')}>
        원고 내보내기
      </button>
      {message && (
        <span className="small" role="status">
          {message}
        </span>
      )}
      {mode && (
        <Dialog
          title={mode === 'import' ? '원고 가져오기' : '원고 내보내기'}
          onClose={() => {
            if (!busy) setMode(null)
          }}
        >
          <div className="stack manuscript-transfer" aria-busy={busy}>
            {mode === 'import' ? (
              <>
                <p className="small muted">
                  TXT · Markdown · PDF · DOCX / 8MB 미만 · 본문 최대 20만 자
                </p>
                <p className="small">
                  파일은 이 작품의 원고로 가져옵니다. 작품 정보와 설정집은 변경하지 않습니다.
                </p>
                <label>
                  원고 파일
                  <input
                    type="file"
                    accept=".txt,.md,.markdown,.pdf,.docx"
                    disabled={busy}
                    onChange={(event) => void selectFile(event.target.files?.[0])}
                  />
                </label>
                {file && !preview && !busy && (
                  <button onClick={() => void selectFile(file)}>파일 다시 확인</button>
                )}
                {preview && (
                  <>
                    {preview.workTitle && (
                      <p className="small muted">
                        파일 정보 · {preview.workTitle}
                        {preview.genre ? ' · ' + preview.genre : ''}
                      </p>
                    )}
                    <label>
                      회차 제목
                      <input
                        value={title}
                        maxLength={100}
                        required
                        disabled={busy}
                        onChange={(event) => {
                          setTitle(event.target.value)
                          setConfirmed(false)
                        }}
                      />
                    </label>
                    <label>
                      가져온 본문 · {preview.content.length.toLocaleString()}자
                      <textarea readOnly rows={7} value={preview.content} />
                    </label>
                    <label>
                      저장 방식
                      <select
                        value={target}
                        disabled={busy}
                        onChange={(event) => {
                          setTarget(event.target.value)
                          setConfirmed(false)
                        }}
                      >
                        <option value="new">새 회차로 추가</option>
                        {chapter && (
                          <option value="replace">현재 회차 교체 · {chapter.title}</option>
                        )}
                      </select>
                    </label>
                    {target === 'replace' && (
                      <label className="transfer-confirm">
                        <input
                          type="checkbox"
                          checked={confirmed}
                          disabled={busy}
                          onChange={(event) => setConfirmed(event.target.checked)}
                        />
                        현재 회차의 제목과 본문을 교체합니다. 기존 검토 결과는 재검토가 필요할 수
                        있습니다.
                      </label>
                    )}
                  </>
                )}
              </>
            ) : (
              <>
                <p>
                  <strong>{chapter?.title}</strong>
                </p>
                <p className="small muted">
                  현재 입력을 서버에 저장한 뒤 선택한 범위를 내려받습니다. 작품명·장르·회차 정보가
                  함께 포함됩니다.
                </p>
                <label>
                  내보낼 범위
                  <select
                    value={scope}
                    disabled={busy}
                    onChange={(event) => setScope(event.target.value)}
                  >
                    <option value="chapter">현재 회차 · {chapter?.title}</option>
                    <option value="work">전체 회차 · {work.chapters.length}개</option>
                  </select>
                </label>
                <label>
                  파일 형식
                  <select
                    value={format}
                    disabled={busy}
                    onChange={(event) => {
                      const value = event.target.value
                      if (value === 'txt' || value === 'md' || value === 'pdf' || value === 'docx')
                        setFormat(value)
                    }}
                  >
                    <option value="txt">TXT · 텍스트</option>
                    <option value="md">Markdown</option>
                    <option value="pdf">PDF</option>
                    <option value="docx">DOCX · Word</option>
                  </select>
                </label>
              </>
            )}
            {busy && (
              <p role="status">
                {preview || mode === 'export'
                  ? '원고를 처리하고 있습니다…'
                  : '파일 내용을 확인하고 있습니다…'}
              </p>
            )}
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <div className="dialog-actions">
              <button disabled={busy} onClick={() => setMode(null)}>
                취소
              </button>
              <button
                className="primary"
                disabled={
                  busy ||
                  (mode === 'import' &&
                    (!preview || !title.trim() || (target === 'replace' && !confirmed)))
                }
                onClick={() => void execute()}
              >
                {mode === 'export'
                  ? '파일 내려받기'
                  : target === 'replace'
                    ? '현재 회차 교체'
                    : '새 회차로 가져오기'}
              </button>
            </div>
          </div>
        </Dialog>
      )}
    </>
  )
}
