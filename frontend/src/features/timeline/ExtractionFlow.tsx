import { useCallback, useEffect, useRef, useState } from 'react'
import { Dialog } from '../../shared/Dialog'
import { ApiError } from '../../shared/httpClient'
import { readRecord, readInteger } from '../../shared/responseReader'
import { useWorksStore } from '../works/useWorksStore'
import type { Work } from '../works/workTypes'
import {
  extractionClient,
  parseCandidate,
  type EventCandidate,
  type ExtractionJob,
  type ExtractionPreview,
} from './extractionClient'
import { ExtractionSourceButton } from './ExtractionSourceButton'

const statuses = {
  QUEUED: '추출 대기',
  RUNNING: '사건 추출 중',
  SUCCEEDED: '추출 완료',
  FAILED: '추출 실패',
  CANCELLED: '추출 취소',
}
export function ExtractionFlow({ work }: { work: Work }) {
  const account = useWorksStore((state) => state.account)
  const [job, setJob] = useState<ExtractionJob | null>(null)
  const [history, setHistory] = useState<ExtractionJob[]>([])
  const [page, setPage] = useState(0),
    [totalPages, setTotalPages] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [mode, setMode] = useState<'select' | 'preview' | 'candidates' | null>(null)
  const [selected, setSelected] = useState<string[]>(
    work.chapters.slice(0, 1).map((chapter) => chapter.id),
  )
  const [preview, setPreview] = useState<ExtractionPreview | null>(null)
  const [consent, setConsent] = useState(false)
  const [candidates, setCandidates] = useState<EventCandidate[]>([])
  const [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(work.source === 'server')
  const [error, setError] = useState(''),
    [message, setMessage] = useState('')
  const active = useRef(true),
    requestId = useRef(''),
    currentId = useRef(''),
    editingModal = useRef(false)
  const activeJob = job?.status === 'QUEUED' || job?.status === 'RUNNING'
  const jobId = job?.id
  const key = useCallback(
    (id: string) => `magister.event-draft:${account}:${work.id}:${id}`,
    [account, work.id],
  )
  useEffect(() => {
    editingModal.current = mode === 'candidates'
  }, [mode])
  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
    }
  }, [])
  const accept = useCallback(
    (next: ExtractionJob) => {
      currentId.current = next.id
      let draft = next.candidates,
        version = next.version
      try {
        const stored = sessionStorage.getItem(key(next.id))
        if (stored && next.status === 'SUCCEEDED') {
          const value = readRecord(JSON.parse(stored))
          if (!Array.isArray(value.candidates) || value.candidates.length > 100)
            throw new Error('후보 초안 형식 오류')
          draft = value.candidates.map(parseCandidate)
          version = readInteger(value, 'version')
          if (version !== next.version)
            setError(
              '서버 후보가 변경되었습니다. 초안은 유지됩니다. 서버 후보 다시 불러오기 전에 현재 입력을 확인해 주세요.',
            )
        }
      } catch {
        setError('후보 초안을 복구하지 못했습니다. 서버에 저장된 후보를 표시합니다.')
      }
      setJob({ ...next, version })
      setCandidates(draft)
      setHistory((previous) => previous.map((item) => (item.id === next.id ? next : item)))
    },
    [key],
  )
  useEffect(() => {
    if (work.source !== 'server') return
    const controller = new AbortController()
    void extractionClient
      .list(work.id, page, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setHistory(result.items)
        setTotalPages(result.totalPages)
        const next = result.items.find((item) => item.id === currentId.current) ?? result.items[0]
        if (next && (!editingModal.current || next.id !== currentId.current)) accept(next)
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setError(failure instanceof Error ? failure.message : '추출 이력 조회 실패')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [work.id, work.source, account, page, refresh, accept])
  useEffect(() => {
    if (!jobId || !activeJob) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const next = await extractionClient.get(work.id, jobId, controller.signal)
        if (controller.signal.aborted) return
        if (next.status === 'QUEUED' || next.status === 'RUNNING') {
          setJob(next)
          setCandidates(next.candidates)
          timer = setTimeout(() => void poll(), 2000)
        } else {
          accept(next)
        }
      } catch (failure) {
        if (!controller.signal.aborted) {
          setError(failure instanceof Error ? failure.message : '추출 상태 조회 실패')
          timer = setTimeout(() => void poll(), 5000)
        }
      }
    }
    timer = setTimeout(() => void poll(), 1000)
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [work.id, jobId, activeJob, accept])
  function edit(id: string, patch: Partial<EventCandidate>) {
    if (!job) return
    const next = candidates.map((candidate) =>
      candidate.id === id ? { ...candidate, ...patch } : candidate,
    )
    setCandidates(next)
    try {
      sessionStorage.setItem(
        key(job.id),
        JSON.stringify({ version: job.version, candidates: next }),
      )
    } catch {
      setError('후보 임시 저장에 실패했습니다. 화면을 이동하기 전에 후보 저장을 눌러 주세요.')
    }
  }
  async function run(action: () => Promise<void>) {
    if (busy) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await action()
    } catch (failure) {
      if (active.current)
        setError(
          failure instanceof ApiError && failure.status === 409
            ? '저장 버전이 변경되었습니다. 후보와 기존 사건은 유지됩니다. 서버 자료를 확인한 뒤 다시 시도해 주세요.'
            : failure instanceof Error
              ? failure.message
              : '사건 추출 처리 실패',
        )
    } finally {
      if (active.current) setBusy(false)
    }
  }
  async function prepare() {
    await useWorksStore.getState().flushWorkspace(work.id)
    const state = useWorksStore.getState(),
      latest = state.works.find((item) => item.id === work.id)
    if (!active.current) return null
    if (state.account !== account) throw new Error('작품 화면을 다시 확인해 주세요.')
    if (!latest || state.dirtyIds.includes(work.id) || state.conflicts.includes(work.id))
      throw new Error('현재 입력을 먼저 저장하고 충돌을 해결해 주세요.')
    return latest
  }
  async function preparePreview() {
    const latest = await prepare()
    if (!latest) return
    const next = await extractionClient.preview(latest, selected)
    if (!active.current) return
    setPreview(next)
    setConsent(false)
    requestId.current = crypto.randomUUID()
    setMode('preview')
  }
  async function start() {
    if (!preview || !consent) return
    const next = await extractionClient.create(work.id, preview, requestId.current)
    if (!active.current) return
    accept(next)
    setHistory((previous) => [next, ...previous.filter((item) => item.id !== next.id)])
    setMode(null)
  }
  async function saveDraft() {
    if (!job) return null
    const next = await extractionClient.save(work.id, job, candidates)
    if (!active.current) return null
    setJob(next)
    setCandidates(next.candidates)
    setHistory((previous) => previous.map((item) => (item.id === next.id ? next : item)))
    try {
      sessionStorage.removeItem(key(job.id))
    } catch {
      setError(
        '후보는 서버에 저장했지만 브라우저 임시본을 정리하지 못했습니다. 재방문 시 서버 후보를 확인해 주세요.',
      )
    }
    setMessage('후보를 서버에 저장했습니다.')
    return next
  }
  async function confirm() {
    const latest = await prepare()
    if (!latest) return
    const next = await saveDraft()
    if (!next) return
    await extractionClient.confirm(work.id, next, latest.workspaceVersion ?? 0, crypto.randomUUID())
    if (!active.current) return
    const state = useWorksStore.getState()
    if (state.dirtyIds.includes(work.id))
      throw new Error(
        '서버에 확정했지만 새로운 입력이 있습니다. 입력을 저장한 뒤 새로 불러와 주세요.',
      )
    await state.reloadWorkspace(work.id)
    setMode(null)
    setMessage('선택한 사건을 타임라인에 추가했습니다. 기존 사건은 유지됩니다.')
  }
  async function reloadTimeline() {
    const state = useWorksStore.getState()
    if (
      state.dirtyIds.includes(work.id) ||
      state.savingIds.includes(work.id) ||
      state.conflicts.includes(work.id)
    )
      throw new Error(
        '현재 입력을 유지하고 있습니다. 작품 관리에서 저장·충돌을 확인한 뒤 최신 상태를 불러와 주세요.',
      )
    await state.reloadWorkspace(work.id)
    if (active.current) setMessage('최신 타임라인을 불러왔습니다. 후보 입력은 유지됩니다.')
  }
  if (work.source !== 'server') return null
  return (
    <section className="extraction-flow stack" aria-label="AI 사건 추출">
      <div className="row">
        <button
          disabled={busy || activeJob || !work.chapters.length}
          onClick={() => {
            setMode('select')
            setError('')
          }}
        >
          원고에서 사건 추출
        </button>
        {loading && <span role="status">추출 이력 조회 중…</span>}
        <button
          disabled={busy || loading}
          onClick={() => {
            setLoading(true)
            setRefresh((value) => value + 1)
          }}
        >
          작업 상태 다시 확인
        </button>
      </div>
      {history.length > 0 && (
        <label>
          사건 추출 이력
          <select
            value={job?.id ?? ''}
            disabled={busy}
            onChange={(event) => {
              const next = history.find((item) => item.id === event.target.value)
              if (next) accept(next)
            }}
          >
            {history.map((item) => (
              <option key={item.id} value={item.id}>
                {new Date(item.createdAt).toLocaleString('ko-KR')} · {statuses[item.status]}
              </option>
            ))}
          </select>
        </label>
      )}
      {totalPages > 1 && (
        <div className="dialog-actions">
          <button
            disabled={page === 0 || busy}
            onClick={() => {
              setLoading(true)
              setPage((value) => value - 1)
            }}
          >
            이전 추출 이력
          </button>
          <span>
            {page + 1} / {totalPages}
          </span>
          <button
            disabled={page + 1 >= totalPages || busy}
            onClick={() => {
              setLoading(true)
              setPage((value) => value + 1)
            }}
          >
            다음 추출 이력
          </button>
        </div>
      )}
      {job && (
        <div className="row">
          <span role="status">
            {statuses[job.status]}
            {job.status === 'SUCCEEDED' ? ` · 후보 ${job.candidates.length}개` : ''}
          </span>
          {activeJob && (
            <button
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  const next = await extractionClient.cancel(work.id, job.id)
                  if (active.current) accept(next)
                })
              }
            >
              사건 추출 취소
            </button>
          )}
          {job.status === 'SUCCEEDED' && (
            <button onClick={() => setMode('candidates')}>사건 후보 확인</button>
          )}
          {(job.status === 'FAILED' || job.status === 'CANCELLED') && (
            <button onClick={() => setMode('select')}>다시 추출</button>
          )}
        </div>
      )}
      {job?.error && (
        <p role="alert" className="error">
          {job.error}
        </p>
      )}
      {error && !mode && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {message && !mode && <p role="status">{message}</p>}
      {mode && (
        <Dialog
          initialFocus="heading"
          title={
            mode === 'select'
              ? '원고에서 사건 추출'
              : mode === 'preview'
                ? '사건 추출 전송 확인'
                : '사건 후보 확인'
          }
          size="wide"
          onClose={() => {
            if (!busy) setMode(null)
          }}
        >
          <div className="stack" aria-busy={busy}>
            {mode === 'select' && (
              <>
                <p>추출할 회차를 선택하세요. 최대 10회차·본문 합계 20만 자입니다.</p>
                <fieldset disabled={busy}>
                  <legend>추출할 회차</legend>
                  {work.chapters.map((chapter) => (
                    <label key={chapter.id} className="extraction-check">
                      <input
                        type="checkbox"
                        checked={selected.includes(chapter.id)}
                        onChange={() =>
                          setSelected((previous) =>
                            previous.includes(chapter.id)
                              ? previous.filter((id) => id !== chapter.id)
                              : [...previous, chapter.id],
                          )
                        }
                      />
                      {chapter.title} · 버전 {chapter.revision}
                    </label>
                  ))}
                </fieldset>
                <button
                  className="primary"
                  disabled={busy || !selected.length || selected.length > 10}
                  onClick={() => void run(preparePreview)}
                >
                  전송할 원고 확인
                </button>
              </>
            )}
            {mode === 'preview' && preview && (
              <>
                <p>
                  Google Gemini · {preview.model}로 아래 원고가 전송됩니다. 원고 수정 후에는 다시
                  확인해야 합니다.
                </p>
                {preview.sources.map((source) => (
                  <details key={source.id}>
                    <summary>
                      {source.title} · 버전 {source.revision} ·{' '}
                      {source.content.length.toLocaleString()}자
                    </summary>
                    <pre className="extraction-source">{source.content}</pre>
                  </details>
                ))}
                <label className="extraction-check">
                  <input
                    type="checkbox"
                    disabled={busy}
                    checked={consent}
                    onChange={(event) => setConsent(event.target.checked)}
                  />
                  선택한 원고의 외부 AI 전송에 동의합니다.
                </label>
                <p className="small muted">
                  AI 후보는 확정 전까지 기존 사건을 변경하지 않습니다. 취소 후에도 이미 전송한
                  요청의 과금 중단은 보장되지 않습니다.
                </p>
                <div className="dialog-actions">
                  <button disabled={busy} onClick={() => setMode('select')}>
                    범위 다시 선택
                  </button>
                  <button
                    className="primary"
                    disabled={busy || !consent}
                    onClick={() => void run(start)}
                  >
                    사건 후보 추출
                  </button>
                </div>
              </>
            )}
            {mode === 'candidates' && job && (
              <>
                <p>
                  후보를 수정하고 확정·보류·제외를 선택하세요. 기준이 없는 상대 시점과 회상은 임의로
                  정렬하지 않습니다.
                </p>
                {!candidates.length && (
                  <p>
                    선택한 원고에서 추출된 사건이 없습니다. 다른 회차를 선택해 다시 추출할 수
                    있습니다.
                  </p>
                )}
                {candidates.map((candidate) => {
                  const confirmed = work.events.some((event) => event.id === candidate.id)
                  const duplicate = work.events.filter(
                    (event) =>
                      event.id !== candidate.id &&
                      (event.title === candidate.title ||
                        (event.chapterId === candidate.chapterId &&
                          event.quote === candidate.quote)),
                  )
                  const stale =
                    work.chapters.find((chapter) => chapter.id === candidate.chapterId)
                      ?.revision !== candidate.sourceRevision
                  return (
                    <article key={candidate.id} className="extraction-candidate stack">
                      <h3>{candidate.title || '사건명 입력 필요'}</h3>
                      {confirmed && (
                        <p className="notice">
                          이미 확정한 사건입니다. 변경은 타임라인의 사건 수정에서 적용해 주세요.
                        </p>
                      )}
                      {!!duplicate.length && (
                        <details>
                          <summary>중복 가능 · 기존 사건 {duplicate.length}개와 비교</summary>
                          {duplicate.map((event) => (
                            <p key={event.id}>
                              {event.title} · {event.time || '시점 미상'}
                              <br />
                              {event.description}
                              <br />
                              근거: {event.quote}
                            </p>
                          ))}
                        </details>
                      )}
                      {stale && (
                        <p className="notice">
                          근거 변경 · 추출 당시 버전 {candidate.sourceRevision} · 재확인 필요
                        </p>
                      )}
                      <fieldset className="form-fields stack" disabled={busy || confirmed}>
                        <label>
                          사건명
                          <input
                            value={candidate.title}
                            maxLength={100}
                            onChange={(event) => edit(candidate.id, { title: event.target.value })}
                          />
                        </label>
                        <label>
                          사건 설명
                          <textarea
                            value={candidate.description}
                            maxLength={2000}
                            rows={2}
                            onChange={(event) =>
                              edit(candidate.id, { description: event.target.value })
                            }
                          />
                        </label>
                        <div className="form-grid">
                          <label>
                            시간 표현
                            <input
                              value={candidate.time}
                              maxLength={100}
                              onChange={(event) => edit(candidate.id, { time: event.target.value })}
                            />
                          </label>
                          <label>
                            등장인물
                            <input
                              value={candidate.characters}
                              maxLength={500}
                              onChange={(event) =>
                                edit(candidate.id, { characters: event.target.value })
                              }
                            />
                          </label>
                        </div>
                        <label>
                          이야기 순서
                          <input
                            type="number"
                            min={0}
                            max={10000}
                            value={candidate.sequence}
                            onChange={(event) => {
                              const value = event.target.valueAsNumber
                              if (Number.isSafeInteger(value) && value >= 0)
                                edit(candidate.id, { sequence: value })
                            }}
                          />
                        </label>
                        <label className="extraction-check">
                          <input
                            type="checkbox"
                            checked={candidate.timeStatus === 'CONFIRMED'}
                            onChange={(event) =>
                              edit(candidate.id, {
                                timeStatus: event.target.checked ? 'CONFIRMED' : 'UNRESOLVED',
                              })
                            }
                          />
                          시점과 이야기 속 순서를 확인했습니다
                        </label>
                        <label>
                          작가 선택
                          <select
                            value={candidate.decision}
                            onChange={(event) => {
                              const decision = event.target.value
                              if (
                                decision === 'HOLD' ||
                                decision === 'INCLUDE' ||
                                decision === 'EXCLUDE'
                              )
                                edit(candidate.id, { decision })
                            }}
                          >
                            <option value="HOLD">보류</option>
                            <option value="INCLUDE">타임라인에 확정</option>
                            <option value="EXCLUDE">제외</option>
                          </select>
                        </label>
                      </fieldset>
                      <p className="small muted">
                        근거 ·{' '}
                        {work.chapters.find((chapter) => chapter.id === candidate.chapterId)
                          ?.title ?? candidate.chapterId}{' '}
                        · 버전 {candidate.sourceRevision} · 위치 {candidate.startOffset}–
                        {candidate.endOffset}
                      </p>
                      <blockquote>{candidate.quote}</blockquote>
                      <ExtractionSourceButton
                        workId={work.id}
                        event={{ ...candidate, extractionId: job.id }}
                      />
                    </article>
                  )
                })}
                <div className="dialog-actions">
                  <button
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        sessionStorage.removeItem(key(job.id))
                        accept(await extractionClient.get(work.id, job.id))
                        setMessage('서버 후보를 다시 불러왔습니다.')
                      })
                    }
                  >
                    서버 후보 다시 불러오기
                  </button>
                  <button disabled={busy} onClick={() => void run(reloadTimeline)}>
                    타임라인 최신 상태 확인
                  </button>
                  <button
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await saveDraft()
                      })
                    }
                  >
                    후보 저장
                  </button>
                  <button
                    className="primary"
                    disabled={
                      busy ||
                      !candidates.some((candidate) => candidate.decision === 'INCLUDE') ||
                      candidates.some((candidate) => !candidate.title.trim())
                    }
                    onClick={() => void run(confirm)}
                  >
                    선택한 사건 확정
                  </button>
                </div>
              </>
            )}
            {busy && <p role="status">처리 중입니다. 입력과 기존 사건을 유지합니다.</p>}
            {message && <p role="status">{message}</p>}
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
          </div>
        </Dialog>
      )}
    </section>
  )
}
