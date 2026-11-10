import { useEffect, useRef, useState, type RefObject } from 'react'
import { Dialog } from '../../shared/Dialog'
import { ApiError } from '../../shared/httpClient'
import { useWorksStore } from '../works/useWorksStore'
import type { Chapter, Work } from '../works/workTypes'
import { sceneBoardClient } from './sceneBoardClient'
import { SceneImagePanel } from './SceneImagePanel'
import './sceneBoard.css'

type Board = Awaited<ReturnType<typeof sceneBoardClient.get>>
type Preview = Awaited<ReturnType<typeof sceneBoardClient.preview>>
type Usage = Awaited<ReturnType<typeof sceneBoardClient.usage>>

export function SceneBoardFlow({
  work,
  chapter,
  editor,
}: {
  work: Work
  chapter: Chapter
  editor: RefObject<HTMLTextAreaElement | null>
}) {
  const [open, setOpen] = useState(false)
  const [range, setRange] = useState<{ start: number; end: number; text: string } | null>(null)
  const [settings, setSettings] = useState<number[]>([])
  const [history, setHistory] = useState<string[]>([])
  const [preview, setPreview] = useState<Preview | null>(null)
  const [board, setBoard] = useState<Board | null>(null)
  const [usage, setUsage] = useState<Usage | null>(null)
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<'analysis' | 'references' | 'images'>('analysis')
  const requestId = useRef('')
  const pending = board?.status === 'QUEUED' || board?.status === 'RUNNING'
  const boardId = board?.id

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    void sceneBoardClient
      .list(work.id, chapter.id, controller.signal)
      .then((ids) => {
        if (!controller.signal.aborted) setHistory(ids)
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('장면 보드 이력을 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [open, work.id, chapter.id])

  useEffect(() => {
    if (!open || !pending || !boardId || error) return
    const controller = new AbortController()
    let timer: number
    function poll(id: string) {
      timer = window.setTimeout(() => {
        void sceneBoardClient
          .get(work.id, id, controller.signal)
          .then((next) => {
            if (controller.signal.aborted) return
            setBoard(next)
            if (next.status === 'QUEUED' || next.status === 'RUNNING') poll(id)
          })
          .catch(() => {
            if (!controller.signal.aborted) setError('진행 상태 조회에 실패했습니다.')
          })
      }, 3000)
    }
    poll(boardId)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [open, pending, boardId, work.id, error])

  function show() {
    const area = editor.current
    const start = area?.selectionStart ?? 0
    const end = area?.selectionEnd ?? 0
    const text = area?.value.slice(start, end) ?? ''
    setRange(text.trim() ? { start, end, text } : null)
    setPreview(null)
    setConsent(false)
    setError('')
    setOpen(true)
  }

  async function prepare() {
    if (!range || busy) return
    setBusy(true)
    setError('')
    try {
      await useWorksStore.getState().flushWorkspace(work.id)
      const saved = useWorksStore.getState()
      const current = saved.works
        .find((item) => item.id === work.id)
        ?.chapters.find((item) => item.id === chapter.id)
      if (
        !current ||
        saved.dirtyIds.includes(work.id) ||
        saved.conflicts.includes(work.id) ||
        current.content.slice(range.start, range.end) !== range.text
      ) {
        throw new Error('서버에 저장된 원고를 확인한 뒤 구간을 다시 선택해 주세요.')
      }
      const [next, quota] = await Promise.all([
        sceneBoardClient.preview(work.id, {
          chapterId: current.id,
          revision: current.revision,
          start: range.start,
          end: range.end,
          settingIds: settings,
        }),
        sceneBoardClient.usage(),
      ])
      requestId.current = crypto.randomUUID()
      setPreview(next)
      setUsage(quota)
      setConsent(false)
      setBoard(null)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '전송 자료를 확인하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  async function submit() {
    if (!preview || !consent || busy) return
    setBusy(true)
    setError('')
    try {
      const next = await sceneBoardClient.create(
        work.id,
        preview.selection,
        preview.token,
        requestId.current,
      )
      setBoard(next)
      setHistory((previous) => [next.id, ...previous.filter((id) => id !== next.id)])
      setPreview(null)
      setTab('analysis')
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '장면 분석을 접수하지 못했습니다.')
      if (failure instanceof ApiError && failure.status === 429) {
        await sceneBoardClient
          .usage()
          .then(setUsage)
          .catch(() => undefined)
      }
    } finally {
      setBusy(false)
    }
  }

  async function loadBoard(id: string) {
    setBusy(true)
    setError('')
    try {
      setBoard(await sceneBoardClient.get(work.id, id))
      setPreview(null)
      setTab('analysis')
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '장면 보드를 열지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <button type="button" onClick={show}>
        AI 장면 보드
      </button>
      {open && (
        <Dialog
          title="AI 장면 보드"
          size="wide"
          initialFocus="heading"
          onClose={() => {
            if (!busy) setOpen(false)
          }}
        >
          <div className="scene-board-flow">
            {error && <p role="alert">{error}</p>}
            {history.length > 0 && (
              <label>
                이 회차의 장면 보드
                <select
                  disabled={busy}
                  value={board?.id ?? ''}
                  onChange={(event) => {
                    if (event.target.value) void loadBoard(event.target.value)
                  }}
                >
                  <option value="">이력 선택</option>
                  {history.map((id, index) => (
                    <option key={id} value={id}>
                      최근 기록 {index + 1} · {id.slice(0, 8)}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {!preview && (
              <section aria-label="분석할 원고 선택">
                <h3>선택한 원고</h3>
                {range ? (
                  <blockquote>{range.text}</blockquote>
                ) : (
                  <p>편집기에서 분석할 문장을 선택한 뒤 장면 보드를 여세요.</p>
                )}
                {range && (
                  <fieldset disabled={busy}>
                    <legend>함께 전송할 확정 설정 (최대 10개)</legend>
                    {work.settings
                      .filter((setting) => setting.status === '확정')
                      .map((setting) => {
                        const id = Number(setting.id)
                        const checked = settings.includes(id)
                        return (
                          <label key={setting.id}>
                            <input
                              type="checkbox"
                              checked={checked}
                              disabled={!checked && settings.length >= 10}
                              onChange={() =>
                                setSettings(
                                  checked
                                    ? settings.filter((value) => value !== id)
                                    : [...settings, id],
                                )
                              }
                            />
                            {setting.title}
                          </label>
                        )
                      })}
                  </fieldset>
                )}
                <button type="button" disabled={!range || busy} onClick={() => void prepare()}>
                  전송 자료 확인
                </button>
              </section>
            )}
            {preview && (
              <section aria-label="외부 AI 전송 자료">
                <h3>외부 AI 전송 자료</h3>
                <p>
                  {preview.snapshot.chapterTitle} · 원고 v{preview.snapshot.revision}
                </p>
                <blockquote>{preview.snapshot.text}</blockquote>
                {preview.snapshot.settings.map((setting) => (
                  <details key={setting.id}>
                    <summary>{setting.title}</summary>
                    <p>{setting.content}</p>
                  </details>
                ))}
                {usage && (
                  <p>
                    오늘 장면 분석 {usage.remaining}회 남음 · 초기화{' '}
                    {new Date(usage.resetAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}{' '}
                    (KST)
                  </p>
                )}
                <label>
                  <input
                    type="checkbox"
                    checked={consent}
                    onChange={(event) => setConsent(event.target.checked)}
                  />{' '}
                  표시된 자료의 외부 AI 전송에 동의합니다.
                </label>
                <button type="button" disabled={busy} onClick={() => setPreview(null)}>
                  돌아가기
                </button>
                <button
                  type="button"
                  disabled={!consent || busy || usage?.remaining === 0}
                  onClick={() => void submit()}
                >
                  동의하고 분석 시작
                </button>
              </section>
            )}
            {board && (
              <section aria-label="장면 보드 결과">
                <h3>
                  {board.snapshot.chapterTitle} · 원고 v{board.snapshot.revision}
                </h3>
                {board.snapshot.revision !== chapter.revision && <p>이전 원고 기준 결과입니다.</p>}
                {pending && <p role="status">장면을 분석하고 있습니다.</p>}
                {pending && error && (
                  <button type="button" onClick={() => void loadBoard(board.id)}>
                    상태 다시 확인
                  </button>
                )}
                {board.status === 'FAILED' && (
                  <p role="alert">
                    장면 분석에 실패했습니다. 전송 자료를 다시 확인해 요청할 수 있습니다.
                  </p>
                )}
                {board.result && (
                  <>
                    <details>
                      <summary>분석 당시 선택 원고</summary>
                      <blockquote>{board.snapshot.text}</blockquote>
                    </details>
                    <div role="group" aria-label="장면 보드 보기">
                      <button
                        type="button"
                        aria-pressed={tab === 'analysis'}
                        onClick={() => setTab('analysis')}
                      >
                        장면 분석
                      </button>
                      <button
                        type="button"
                        aria-pressed={tab === 'references'}
                        onClick={() => setTab('references')}
                      >
                        소설 레퍼런스
                      </button>
                      <button
                        type="button"
                        aria-pressed={tab === 'images'}
                        onClick={() => setTab('images')}
                      >
                        콘셉트 이미지
                      </button>
                    </div>
                    {tab === 'images' ? (
                      <SceneImagePanel key={board.id} workId={work.id} boardId={board.id} />
                    ) : tab === 'analysis' ? (
                      <>
                        <h4>원고에서 확인된 사실</h4>
                        {board.result.facts.length === 0 && (
                          <p>인용을 확인할 수 있는 사실이 없습니다.</p>
                        )}
                        {board.result.facts.map((fact, index) => (
                          <details key={fact.start + ':' + index}>
                            <summary>{fact.description}</summary>
                            <blockquote>{fact.quote}</blockquote>
                            <p>
                              원고 위치 {fact.start + 1}~{fact.end}
                            </p>
                          </details>
                        ))}
                        <ResultList
                          title="독자가 추측할 수 있는 내용"
                          values={board.result.inferences}
                        />
                        <ResultList title="설명이 필요한 부분" values={board.result.gaps} />
                        <ResultList
                          title="정보 공개 순서와 단서 배치"
                          values={board.result.options}
                        />
                      </>
                    ) : (
                      <>
                        {board.result.references.length === 0 && <p>추천된 레퍼런스가 없습니다.</p>}
                        {board.result.references.map((reference) => (
                          <article key={reference.id}>
                            <h4>
                              {reference.title} · {reference.author}
                            </h4>
                            <p>
                              {reference.location} · {reference.technique}
                            </p>
                            <p>{reference.question}</p>
                            <a href={reference.url} target="_blank" rel="noreferrer">
                              원문 출처 열기
                            </a>
                          </article>
                        ))}
                      </>
                    )}
                  </>
                )}
              </section>
            )}
          </div>
        </Dialog>
      )}
    </>
  )
}

function ResultList({ title, values }: { title: string; values: string[] }) {
  return (
    <>
      <h4>{title}</h4>
      {values.length ? (
        <ul>
          {values.map((value, index) => (
            <li key={index}>{value}</li>
          ))}
        </ul>
      ) : (
        <p>표시할 항목이 없습니다.</p>
      )}
    </>
  )
}
