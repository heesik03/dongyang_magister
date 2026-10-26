import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { Chapter, Decision, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import { ReviewPanel } from '../review/ReviewPanel'
import { analysisClient } from '../analysis/analysisClient'
import { settingsChanged } from '../analysis/analysisState'
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
  const analysis = work.analyses.find((item) => item.chapterId === chapter?.id)
  const [creating, setCreating] = useState(false)
  const [optionsOpen, setOptionsOpen] = useState(false)
  const [settingIds, setSettingIds] = useState(
    work.settings.filter((setting) => setting.status === '확정').map((setting) => setting.id),
  )
  const [previousIds, setPreviousIds] = useState<string[]>([])
  const [consistency, setConsistency] = useState(true)
  const [motivation, setMotivation] = useState(true)
  const [consent, setConsent] = useState(false)
  const [analyzing, setAnalyzing] = useState(false)
  const [error, setError] = useState('')
  const [view, setView] = useState('원고')
  const [fontSize, setFontSize] = useState(17)
  const [highlight, setHighlight] = useState(params.get('quote') || '')
  const [focusMode, setFocusMode] = useState(false)
  const editor = useRef<HTMLTextAreaElement>(null)
  const analysisRequest = useRef<AbortController | null>(null)
  useEffect(
    () => () => {
      analysisRequest.current?.abort()
    },
    [],
  )
  useEffect(() => {
    const quote = params.get('quote')
    const element = editor.current
    const start = quote ? (chapter?.content.indexOf(quote) ?? -1) : -1
    if (quote && element && start >= 0) {
      element.focus()
      element.setSelectionRange(start, start + quote.length)
      element.scrollIntoView({ block: 'center' })
    }
  }, [params, chapter?.id, chapter?.content])
  // 원고나 참고 설정이 달라지면 과거 분석에 작가 판단을 덧붙이지 않고 재검토를 유도한다.
  const stale =
    !!analysis && (analysis.source !== chapter?.content || settingsChanged(analysis, work.settings))
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
    if (!consent) {
      setError('선택한 원고·설정의 Gemini 전송에 동의해 주세요.')
      setOptionsOpen(true)
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
    setOptionsOpen(false)
    setAnalyzing(true)
    setError('')
    setView('검토')
    setFocusMode(false)
    try {
      await useWorksStore.getState().flushWorkspace(work.id)
      const state = useWorksStore.getState(),
        latest = state.works.find((item) => item.id === work.id)
      if (!latest || state.dirtyIds.includes(work.id))
        throw new Error('원고를 먼저 서버에 저장해 주세요.')
      const result = await analysisClient.run(
        work.id,
        {
          chapterId: chapter.id,
          chapterRevision: latest.chapters.find((item) => item.id === chapter.id)?.revision,
          workspaceVersion: latest.workspaceVersion ?? 0,
          settingIds,
          previousIds,
          consistency,
          motivation,
        },
        controller.signal,
      )
      if (controller.signal.aborted) return
      const current = useWorksStore.getState().works.find((item) => item.id === work.id)
      if (!current) return
      updateWork({
        ...current,
        analyses: [...current.analyses.filter((item) => item.chapterId !== chapter.id), result],
      })
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(failure instanceof Error ? failure.message : 'AI 검토에 실패했습니다.')
    } finally {
      if (!controller.signal.aborted) setAnalyzing(false)
    }
  }
  async function cancelAnalysis() {
    if (!chapter) return
    try {
      await analysisClient.cancel(work.id, chapter.id)
      analysisRequest.current?.abort()
      setAnalyzing(false)
      setError('AI 검토를 취소했습니다.')
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '취소 요청 실패')
    }
  }
  // 작가 판단은 해당 원고의 검토 항목에만 적용하며 작품 전체의 예외로 확대하지 않는다.
  function decide(id: string, decision: Decision, note: string) {
    if (!analysis || stale) return
    updateWork({
      ...work,
      analyses: work.analyses.map((item) =>
        item.chapterId === analysis.chapterId
          ? {
              ...item,
              reviews: item.reviews.map((review) =>
                review.id === id ? { ...review, decision, note } : review,
              ),
            }
          : item,
      ),
    })
  }
  function jump(quote: string) {
    setHighlight(quote)
    setView('원고')
    const element = editor.current
    const start = chapter?.content.indexOf(quote) ?? -1
    if (element && start >= 0) {
      // 다음 프레임에 모바일 원고 탭이 표시된 뒤 포커스와 선택 영역을 적용한다.
      requestAnimationFrame(() => {
        element.focus()
        element.setSelectionRange(start, start + quote.length)
        element.scrollIntoView({ block: 'center', behavior: 'auto' })
      })
    }
  }
  function toggle(ids: string[], id: string) {
    return ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id]
  }
  return (
    <div className="manuscript-workspace">
      {analyzing && <button onClick={() => void cancelAnalysis()}>AI 검토 취소</button>}
      <div className="manuscript-toolbar">
        <div className="chapter-select">
          <label>
            현재 회차
            <select
              value={chapter?.id || ''}
              disabled={analyzing || !chapter}
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
            disabled={analyzing}
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
          <button disabled={analyzing} onClick={() => setOptionsOpen(true)}>
            <Icon name="settings" />
            분석 설정
          </button>
          <button className="primary" disabled={analyzing || !chapter} onClick={analyze}>
            <Icon name="spark" />
            {analyzing ? '분석 중…' : 'AI 검토'}
          </button>
        </div>
      </div>
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
                  disabled={analyzing}
                  onChange={(event) =>
                    updateWork({
                      ...work,
                      chapters: work.chapters.map((item) =>
                        item.id === chapter.id ? { ...item, title: event.target.value } : item,
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
                  disabled={analyzing}
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
          stale={stale}
          analyzing={analyzing}
          error={error}
          retry={analyze}
          onJump={jump}
          onDecision={decide}
        />
      </div>
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
            <label className="check">
              <input
                type="checkbox"
                checked={consent}
                onChange={(event) => setConsent(event.target.checked)}
              />
              선택한 원고·확정 설정·검토 판단을 Gemini로 전송하는 데 동의합니다.
            </label>
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
              <button className="primary" disabled={!chapter || analyzing} onClick={analyze}>
                선택 범위로 분석
              </button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  )
}
