import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { Chapter, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import { ReviewPanel } from '../review/ReviewPanel'
import { DecisionLedger } from '../review/DecisionLedger'
import { useReviewReport } from '../review/useReviewReport'
import { reviewClient, type ReviewSource, type SourceLocation } from '../review/reviewClient'
import { ReviewSourceDialog } from '../review/ReviewSourceDialog'
import { analysisClient, type Preview, type Snapshot } from '../analysis/analysisClient'
import { settingsChanged, inputChanged } from '../analysis/analysisState'
import { useAnalysisJobs } from '../analysis/useAnalysisJobs'
import { AnalysisPreviewDialog } from '../analysis/AnalysisPreviewDialog'
import { AnalysisMaterials } from '../analysis/AnalysisMaterials'
import { ApiError } from '../../shared/httpClient'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function ManuscriptPanel({ work }: { work: Work }) {
  const updateWork = useWorksStore((state) => state.updateWork)
  const storageError = useWorksStore((state) => state.storageError)
  const saving = useWorksStore((state) => state.savingIds.includes(work.id))
  const dirty = useWorksStore((state) => state.dirtyIds.includes(work.id))
  const saveError = useWorksStore((state) => state.workspaceErrors[work.id])
  const [params, setParams] = useSearchParams()
  const chapter =
    work.chapters.find((item) => item.id === params.get('chapter')) ||
    work.chapters[work.chapters.length - 1]
  const jobs = useAnalysisJobs(work.id, chapter?.id, work.source === 'server')
  const reportState = useReviewReport(work.id, jobs.job?.id, jobs.job?.status === 'SUCCEEDED')
  const [openedSource, setOpenedSource] = useState<{
    source: ReviewSource
    location: SourceLocation
  } | null>(null)
  const [jumpError, setJumpError] = useState<{ id: string; message: string } | null>(null)
  const [jumpLoading, setJumpLoading] = useState<string | null>(null)
  const jumpRequest = useRef<AbortController | null>(null)
  const savedAnalysis = work.analyses.find((item) => item.chapterId === chapter?.id)
  const analysis = jobs.job?.result ?? savedAnalysis
  const [ledgerOpen, setLedgerOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [optionsOpen, setOptionsOpen] = useState(false)
  const [settingIds, setSettingIds] = useState(
    work.settings.filter((setting) => setting.status === '확정').map((setting) => setting.id),
  )
  const [previousIds, setPreviousIds] = useState<string[]>([])
  const [consistency, setConsistency] = useState(true)
  const [motivation, setMotivation] = useState(true)
  const [preparing, setPreparing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [storedSnapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [sourceFailure, setSourceFailure] = useState<{ id: string; message: string } | null>(null)
  const [showSnapshot, setShowSnapshot] = useState(false)
  const [snapshotRefresh, setSnapshotRefresh] = useState(0)
  const analyzing = jobs.active
  const [error, setError] = useState('')
  const [view, setView] = useState('원고')
  const [fontSize, setFontSize] = useState(17)
  const [highlight, setHighlight] = useState(params.get('quote') || '')
  const [focusMode, setFocusMode] = useState(false)
  const editor = useRef<HTMLTextAreaElement>(null)
  const analysisRequest = useRef<AbortController | null>(null)
  const selectedJobId = jobs.job?.id,
    selectedJobStatus = jobs.job?.status
  const snapshot = storedSnapshot?.jobId === selectedJobId ? storedSnapshot : null
  const snapshotError =
    sourceFailure && sourceFailure.id === selectedJobId ? sourceFailure.message : ''
  const latestWork = useRef(work)
  useEffect(() => {
    latestWork.current = work
  }, [work])
  useEffect(() => () => jumpRequest.current?.abort(), [selectedJobId])
  useEffect(() => {
    if (!selectedJobId) return
    const controller = new AbortController()
    void analysisClient
      .snapshot(work.id, selectedJobId, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) {
          setSnapshot(value)
          setSourceFailure(null)
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setSourceFailure({
            id: selectedJobId,
            message: error instanceof Error ? error.message : '전송 자료 조회 실패',
          })
      })
    return () => controller.abort()
  }, [work.id, selectedJobId, selectedJobStatus, snapshotRefresh])
  useEffect(
    () => () => {
      analysisRequest.current?.abort()
      jumpRequest.current?.abort()
    },
    [],
  )
  useEffect(() => {
    const quote = params.get('quote')
    const element = editor.current
    const start = Number(params.get('start'))
    const end = Number(params.get('end'))
    if (
      quote &&
      element &&
      params.has('start') &&
      params.has('end') &&
      Number.isSafeInteger(start) &&
      Number.isSafeInteger(end) &&
      start >= 0 &&
      end > start &&
      chapter?.content.slice(start, end) === quote
    ) {
      element.focus()
      element.setSelectionRange(start, end)
      element.scrollIntoView({ block: 'center' })
    }
  }, [params, chapter?.id, chapter?.content])
  // 원고나 참고 설정이 달라지면 과거 분석에 작가 판단을 덧붙이지 않고 재검토를 유도한다.
  const stale =
    !!analysis &&
    (analysis.source !== chapter?.content ||
      settingsChanged(analysis, work.settings) ||
      (!!snapshot && (snapshot.stale || inputChanged(snapshot.input, work))) ||
      !!reportState.report?.stale)
  function createChapter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const title = String(new FormData(event.currentTarget).get('title')).trim()
    if (!title) return
    const next: Chapter = { id: crypto.randomUUID(), title, content: '', revision: 1 }
    updateWork({ ...work, chapters: [...work.chapters, next] })
    setParams({ chapter: next.id })
    setCreating(false)
    setHighlight('')
  }
  function changeChapter(id: string) {
    setParams({ chapter: id })
    setHighlight('')
    setError('')
  }
  function write(content: string) {
    if (!chapter) return
    updateWork({
      ...work,
      chapters: work.chapters.map((item) =>
        item.id === chapter.id ? { ...item, content, revision: item.revision + 1 } : item,
      ),
    })
    setHighlight('')
  }
  // 서버에 저장된 원고를 기준으로 작업을 접수한다. 외부 전송은 명시적으로 동의한 범위만 사용한다.
  async function analyze() {
    if (!chapter?.content.trim()) {
      setError('원고를 먼저 입력해 주세요.')
      return
    }
    if (work.source !== 'server') {
      setError('서버에 저장된 작품에서 AI 검토를 실행해 주세요.')
      return
    }
    if (!consistency && !motivation) {
      setError('분석 항목을 하나 이상 선택해 주세요.')
      return
    }
    if (
      !work.settings.some((setting) => setting.status === '확정' && settingIds.includes(setting.id))
    ) {
      setError('참고할 확정 설정을 하나 이상 선택해 주세요.')
      setOptionsOpen(true)
      return
    }
    const controller = new AbortController()
    analysisRequest.current?.abort()
    analysisRequest.current = controller
    setPreparing(true)
    setError('')
    try {
      await useWorksStore.getState().flushWorkspace(work.id)
      const state = useWorksStore.getState(),
        latest = state.works.find((item) => item.id === work.id)
      if (!latest || state.dirtyIds.includes(work.id))
        throw new Error('원고를 먼저 서버에 저장해 주세요.')
      const confirmedSettingIds = settingIds.filter((id) =>
        latest.settings.some((setting) => setting.id === id && setting.status === '확정'),
      )
      const validPreviousIds = previousIds.filter(
        (id) =>
          latest.chapters.findIndex((item) => item.id === id) >= 0 &&
          latest.chapters.findIndex((item) => item.id === id) <
            latest.chapters.findIndex((item) => item.id === chapter.id),
      )
      const result = await analysisClient.preview(
        work.id,
        {
          chapterId: chapter.id,
          chapterRevision: latest.chapters.find((item) => item.id === chapter.id)?.revision,
          workspaceVersion: latest.workspaceVersion ?? 0,
          settingIds: confirmedSettingIds,
          previousIds: validPreviousIds,
          consistency,
          motivation,
        },
        controller.signal,
      )
      if (controller.signal.aborted) return
      setPreview(result)
      setOptionsOpen(false)
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : 'AI 검토에 실패했습니다.')
    } finally {
      if (!controller.signal.aborted) setPreparing(false)
    }
  }
  async function confirmAnalysis() {
    if (!preview || submitting) return
    const controller = new AbortController(),
      context = jobs.context
    analysisRequest.current?.abort()
    analysisRequest.current = controller
    setSubmitting(true)
    setError('')
    try {
      const created = await analysisClient.create(work.id, preview, controller.signal)
      if (controller.signal.aborted) return
      jobs.accept(created, context)
      setPreview(null)
      setView('검토')
      setFocusMode(false)
    } catch (failure) {
      if (controller.signal.aborted) return
      setError(failure instanceof Error ? failure.message : '분석 접수 실패')
      if (failure instanceof ApiError && failure.status === 409) {
        setPreview(null)
        setOptionsOpen(true)
      }
      // 접수 응답만 유실되었다면 기존 서버 작업을 조회한다. 새 POST를 자동 실행하지 않는다.
      jobs.refresh()
    } finally {
      if (!controller.signal.aborted) setSubmitting(false)
    }
  }
  async function retryAnalysis() {
    if (!jobs.job) {
      setOptionsOpen(true)
      return
    }
    const controller = new AbortController()
    analysisRequest.current?.abort()
    analysisRequest.current = controller
    try {
      const previous = await analysisClient.snapshot(work.id, jobs.job.id, controller.signal)
      if (controller.signal.aborted) return
      const settingIds = previous.input.settings
        .map((setting) => setting.id)
        .filter((id) =>
          work.settings.some((setting) => setting.id === id && setting.status === '확정'),
        )
      const previousIds = previous.input.previous
        .map((chapter) => chapter.id)
        .filter(
          (id) =>
            work.chapters.findIndex((item) => item.id === id) >= 0 &&
            work.chapters.findIndex((item) => item.id === id) <
              work.chapters.findIndex((item) => item.id === chapter?.id),
        )
      setSettingIds(settingIds)
      setPreviousIds(previousIds)
      setConsistency(previous.input.consistency)
      setMotivation(previous.input.motivation)
      setPreview(null)
      setError(
        settingIds.length !== previous.input.settings.length ||
          previousIds.length !== previous.input.previous.length
          ? '이전 참고 자료 중 현재 사용할 수 없는 자료가 있습니다. 분석 범위를 다시 선택해 주세요.'
          : '',
      )
      setOptionsOpen(true)
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : '이전 선택 범위 조회 실패')
    }
  }
  async function cancelAnalysis() {
    if (!jobs.job) return
    try {
      await analysisClient.cancel(work.id, jobs.job.id)
      jobs.refresh()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '취소 요청 실패')
    }
  }
  async function jump(location: SourceLocation) {
    if (!selectedJobId) return
    jumpRequest.current?.abort()
    const controller = new AbortController()
    jumpRequest.current = controller
    setJumpLoading(selectedJobId)
    setJumpError(null)
    try {
      const source = await reviewClient.source(
        work.id,
        selectedJobId,
        location.sourceId,
        controller.signal,
      )
      if (controller.signal.aborted) return
      if (source.content.slice(location.startOffset, location.endOffset) !== location.quote)
        throw new Error('원문과 인용 위치가 일치하지 않습니다. 근거를 다시 조회해 주세요.')
      const current = latestWork.current.chapters.find((item) => item.id === source.sourceKey)
      if (
        source.type !== 'SETTING' &&
        current?.content === source.content &&
        current.revision === source.revision
      ) {
        setHighlight(location.quote)
        setView('원고')
        if (current.id !== chapter?.id) {
          setParams({
            chapter: current.id,
            quote: location.quote,
            start: String(location.startOffset),
            end: String(location.endOffset),
          })
        } else {
          requestAnimationFrame(() => {
            editor.current?.focus()
            editor.current?.setSelectionRange(location.startOffset, location.endOffset)
            editor.current?.scrollIntoView({ block: 'center', behavior: 'auto' })
          })
        }
      } else {
        setOpenedSource({ source, location })
      }
    } catch (failure) {
      if (!controller.signal.aborted)
        setJumpError({
          id: selectedJobId,
          message: failure instanceof Error ? failure.message : '근거 원문 조회 실패',
        })
    } finally {
      if (!controller.signal.aborted) setJumpLoading(null)
    }
  }
  function toggle(ids: string[], id: string) {
    return ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id]
  }
  return (
    <div className="manuscript-workspace">
      <button onClick={() => setLedgerOpen(true)}>작품 판단 기록</button>
      {ledgerOpen && <DecisionLedger workId={work.id} onClose={() => setLedgerOpen(false)} />}
      {jumpLoading && jumpLoading === selectedJobId && (
        <p role="status">근거 원문을 불러오고 있습니다.</p>
      )}
      {jumpError && jumpError.id === selectedJobId && (
        <p className="error" role="alert">
          {jumpError.message}
        </p>
      )}
      {analyzing && <button onClick={() => void cancelAnalysis()}>AI 검토 취소</button>}
      <div className="manuscript-toolbar">
        <div className="chapter-select">
          <label>
            현재 회차
            <select
              value={chapter?.id || ''}
              disabled={preparing || submitting || !chapter}
              onChange={(event) => changeChapter(event.target.value)}
            >
              {work.chapters.length ? (
                work.chapters.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.title}
                  </option>
                ))
              ) : (
                <option value="">아직 회차가 없습니다</option>
              )}
            </select>
          </label>
          <button
            className="icon-button"
            aria-label="새 회차 추가"
            disabled={preparing || submitting}
            onClick={() => setCreating(true)}
          >
            <Icon name="plus" />
          </button>
        </div>
        <div className="toolbar-actions">
          <span className="save-status" role="status">
            {storageError || saveError
              ? '저장 실패 · 입력 유지'
              : saving
                ? '서버 저장 중…'
                : dirty
                  ? '저장 대기 · 입력 유지'
                  : work.source === 'server'
                    ? '서버에 자동 저장'
                    : '브라우저에 자동 저장'}
          </span>
          <button disabled={preparing || submitting} onClick={() => setOptionsOpen(true)}>
            <Icon name="settings" />
            분석 설정
          </button>
          <button
            className="primary"
            disabled={analyzing || preparing || submitting || !chapter}
            onClick={() => setOptionsOpen(true)}
          >
            <Icon name="spark" />
            {analyzing ? '분석 중…' : 'AI 검토'}
          </button>
        </div>
      </div>
      {work.source === 'server' && chapter && (
        <section aria-label="분석 작업 이력" className="notice">
          <strong>분석 작업 이력</strong>
          {jobs.loading && <p role="status">분석 기록을 불러오고 있습니다.</p>}
          {jobs.error && <p role="alert">{jobs.error}</p>}
          {!jobs.loading && !jobs.history.length && !jobs.error && (
            <p>아직 이 회차의 분석 기록이 없습니다.</p>
          )}
          {!!jobs.history.length && (
            <label>
              분석 기록 선택
              <select
                value={jobs.job?.id ?? ''}
                onChange={(event) => jobs.select(event.target.value)}
              >
                <option value="" disabled>
                  기록을 선택하세요
                </option>
                {jobs.history.map((item) => (
                  <option key={item.id} value={item.id}>
                    {new Date(item.createdAt).toLocaleString('ko-KR')} ·{' '}
                    {
                      {
                        QUEUED: '대기',
                        RUNNING: '분석 중',
                        SUCCEEDED: '완료',
                        FAILED: '실패',
                        CANCELLED: '취소',
                      }[item.status]
                    }
                  </option>
                ))}
              </select>
            </label>
          )}
          <button onClick={jobs.refresh}>작업 상태 다시 확인</button>
          {jobs.job && (
            <button
              onClick={() => {
                setShowSnapshot(true)
                setSnapshotRefresh((value) => value + 1)
              }}
            >
              분석 당시 전송 자료
            </button>
          )}
          {jobs.totalPages > 1 && (
            <div className="dialog-actions">
              <button disabled={jobs.page === 0} onClick={() => jobs.changePage(jobs.page - 1)}>
                이전 분석 기록
              </button>
              <span>
                {jobs.page + 1} / {jobs.totalPages}
              </span>
              <button
                disabled={jobs.page + 1 >= jobs.totalPages}
                onClick={() => jobs.changePage(jobs.page + 1)}
              >
                다음 분석 기록
              </button>
            </div>
          )}
        </section>
      )}
      <div className="workspace-mobile-tabs">
        {['원고', '검토'].map((item) => (
          <button
            key={item}
            className={view === item ? 'selected' : ''}
            aria-pressed={view === item}
            onClick={() => setView(item)}
          >
            {item}
          </button>
        ))}
      </div>
      <div className="focus-toolbar">
        <button
          className="text-button"
          aria-pressed={focusMode}
          onClick={() => setFocusMode(!focusMode)}
        >
          {focusMode ? '검토 패널 펼치기' : '검토 패널 접기'}
        </button>
      </div>
      <div
        className={`editor-review-grid view-${view === '원고' ? 'editor' : 'review'} ${focusMode ? 'focus-mode' : ''}`}
      >
        <section className="editor-pane" aria-label="회차 원고">
          {chapter ? (
            <>
              <div className="editor-heading">
                <span className="eyebrow">MANUSCRIPT</span>
                <label className="font-select">
                  글자 크기
                  <select
                    value={fontSize}
                    onChange={(event) => setFontSize(Number(event.target.value))}
                  >
                    {[15, 17, 19, 21].map((size) => (
                      <option key={size} value={size}>
                        {size}px
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <label>
                회차 제목
                <input
                  value={chapter.title}
                  maxLength={100}
                  disabled={preparing || submitting}
                  onChange={(event) =>
                    updateWork({
                      ...work,
                      chapters: work.chapters.map((item) =>
                        item.id === chapter.id
                          ? { ...item, title: event.target.value, revision: item.revision + 1 }
                          : item,
                      ),
                    })
                  }
                />
              </label>
              <p className="small muted">원고를 자유롭게 붙여넣고 다듬어 보세요.</p>
              {highlight && chapter.content.includes(highlight) && (
                <div className="source-highlight" role="status">
                  <strong>선택한 근거 문장</strong>
                  <p>
                    <mark>{highlight}</mark>
                  </p>
                </div>
              )}
              <label className="manuscript-label">
                회차 원고
                <textarea
                  ref={editor}
                  disabled={preparing || submitting}
                  style={{ fontSize }}
                  value={chapter.content}
                  maxLength={200000}
                  onChange={(event) => write(event.target.value)}
                  placeholder="이곳에서 이야기를 시작하세요. 한국어 원고를 붙여넣을 수도 있어요."
                />
              </label>
              <div className="editor-bottom">
                <span>
                  {chapter.content.length.toLocaleString()}자 · 원고 버전 {chapter.revision}
                </span>
                <Link to={`/works/${work.id}/settings`}>설정집 참고하기 →</Link>
              </div>
            </>
          ) : (
            <div className="empty">
              <Icon name="file" />
              <h2>첫 회차를 펼쳐 보세요.</h2>
              <p>회차를 추가하고 원고를 입력하면 검토를 시작할 수 있어요.</p>
              <button className="primary" onClick={() => setCreating(true)}>
                회차 추가
              </button>
            </div>
          )}
        </section>
        <ReviewPanel
          key={chapter?.id || 'empty'}
          work={work}
          analysis={analysis}
          report={reportState.report}
          input={snapshot?.input}
          reportLoading={reportState.loading}
          reportError={reportState.error}
          retryReport={reportState.retry}
          stale={stale}
          analyzing={analyzing}
          error={
            error ||
            (jobs.job?.status === 'FAILED'
              ? jobs.job.error || '분석에 실패했습니다.'
              : jobs.job?.status === 'CANCELLED'
                ? 'AI 검토를 취소했습니다.'
                : '')
          }
          retry={() => void retryAnalysis()}
          onJump={(location) => void jump(location)}
          onDecisionSaved={reportState.retry}
        />
      </div>
      {openedSource && (
        <ReviewSourceDialog {...openedSource} onClose={() => setOpenedSource(null)} />
      )}
      {creating && (
        <Dialog title="새 회차 추가" onClose={() => setCreating(false)}>
          <form className="stack" onSubmit={createChapter}>
            <label>
              회차 제목
              <input
                name="title"
                required
                maxLength={100}
                placeholder={`${work.chapters.length + 1}화. 새로운 장면`}
                autoFocus
              />
            </label>
            <div className="dialog-actions">
              <button type="button" onClick={() => setCreating(false)}>
                취소
              </button>
              <button className="primary">회차 만들기</button>
            </div>
          </form>
        </Dialog>
      )}
      {optionsOpen && (
        <Dialog title="분석 범위 설정" onClose={() => setOptionsOpen(false)}>
          <div className="stack">
            <fieldset>
              <legend>분석 항목</legend>
              <label className="check">
                <input
                  type="checkbox"
                  checked={consistency}
                  onChange={(event) => setConsistency(event.target.checked)}
                />
                설정 충돌 후보
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={motivation}
                  onChange={(event) => setMotivation(event.target.checked)}
                />
                인물 행동의 설명 보완
              </label>
            </fieldset>
            <fieldset>
              <legend>참고할 확정 설정</legend>
              {work.settings
                .filter((setting) => setting.status === '확정')
                .map((setting) => (
                  <label className="check" key={setting.id}>
                    <input
                      type="checkbox"
                      checked={settingIds.includes(setting.id)}
                      onChange={() => setSettingIds(toggle(settingIds, setting.id))}
                    />
                    {setting.title}
                  </label>
                ))}
              {!work.settings.some((setting) => setting.status === '확정') && (
                <p className="small muted">설정집에서 확정 설정을 먼저 등록해 주세요.</p>
              )}
            </fieldset>
            <fieldset>
              <legend>참고할 이전 원고</legend>
              {work.chapters
                .filter(
                  (_item, index) =>
                    index < work.chapters.findIndex((item) => item.id === chapter?.id),
                )
                .map((item) => (
                  <label className="check" key={item.id}>
                    <input
                      type="checkbox"
                      checked={previousIds.includes(item.id)}
                      onChange={() => setPreviousIds(toggle(previousIds, item.id))}
                    />
                    {item.title}
                  </label>
                ))}
              {work.chapters.length < 2 && (
                <p className="small muted">선택할 다른 회차가 없습니다.</p>
              )}
            </fieldset>
            <p className="notice small">
              현재 원고와 선택한 이전 회차·확정 설정을 외부 AI에 전송합니다. 결과는 검토 후보이며
              설정을 자동으로 변경하지 않습니다.
            </p>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <div className="dialog-actions">
              <button onClick={() => setOptionsOpen(false)}>닫기</button>
              <button
                className="primary"
                disabled={!chapter || analyzing || preparing || submitting}
                onClick={analyze}
              >
                {preparing ? '전송 자료 준비 중…' : '전송 자료 미리보기'}
              </button>
            </div>
          </div>
        </Dialog>
      )}
      {preview && (
        <AnalysisPreviewDialog
          key={preview.previewToken}
          preview={preview}
          submitting={submitting}
          error={error}
          onConfirm={() => void confirmAnalysis()}
          onClose={() => {
            if (!submitting) setPreview(null)
          }}
        />
      )}
      {showSnapshot && (
        <Dialog title="분석 당시 전송 자료" onClose={() => setShowSnapshot(false)}>
          {snapshotError && <p role="alert">{snapshotError}</p>}
          {!snapshot && !snapshotError && <p role="status">전송 자료를 불러오고 있습니다.</p>}
          {snapshot && (
            <>
              <p>
                {snapshot.legacy
                  ? '기존 분석의 저장 입력입니다.'
                  : '접수 당시 고정한 전송 자료입니다.'}
              </p>
              {(snapshot.stale || inputChanged(snapshot.input, work)) && (
                <p className="notice">현재 원고 또는 참고 자료와 다른 이전 자료입니다.</p>
              )}
              <AnalysisMaterials input={snapshot.input} />
            </>
          )}
        </Dialog>
      )}
    </div>
  )
}
