import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { Chapter, Decision, Work } from '../works/types'
import { useWorks } from '../works/store'
import { ReviewPanel } from '../review/ReviewPanel'
import { runMockAnalysis } from '../review/mockAnalysis'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function ManuscriptPanel({ work }: { work: Work }) {
  const { updateWork, storageError } = useWorks()
  const [params, setParams] = useSearchParams()
  const chapter = work.chapters.find(item => item.id === params.get('chapter')) || work.chapters[work.chapters.length - 1]
  const analysis = work.analyses.find(item => item.chapterId === chapter?.id)
  const [creating, setCreating] = useState(false)
  const [optionsOpen, setOptionsOpen] = useState(false)
  const [settingIds, setSettingIds] = useState(work.settings.filter(setting => setting.status === '확정').map(setting => setting.id))
  const [previousIds, setPreviousIds] = useState<string[]>([])
  const [consistency, setConsistency] = useState(true)
  const [motivation, setMotivation] = useState(true)
  const [simulatedFailure, setSimulatedFailure] = useState(false)
  const [analyzing, setAnalyzing] = useState(false)
  const [error, setError] = useState('')
  const [view, setView] = useState('원고')
  const [fontSize, setFontSize] = useState(17)
  const [highlight, setHighlight] = useState(params.get('quote') || '')
  const [focusMode, setFocusMode] = useState(false)
  const editor = useRef<HTMLTextAreaElement>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  useEffect(() => {
    const quote = params.get('quote')
    const element = editor.current
    const start = quote ? chapter?.content.indexOf(quote) ?? -1 : -1
    if (quote && element && start >= 0) { element.focus(); element.setSelectionRange(start, start + quote.length); element.scrollIntoView({ block: 'center' }) }
  }, [params, chapter?.id, chapter?.content])
  const stale = !!analysis && (analysis.source !== chapter?.content || (analysis.settingsSnapshot && analysis.settingsSnapshot !== JSON.stringify(work.settings.filter(setting => setting.status === '확정' && analysis.settingIds.includes(setting.id)))) || analysis.reviews.some(review => work.settings.find(setting => setting.id === review.settingId)?.content !== review.evidence || work.settings.find(setting => setting.id === review.settingId)?.status !== '확정'))
  function createChapter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const title = String(new FormData(event.currentTarget).get('title')).trim()
    if (!title) return
    const next: Chapter = { id: crypto.randomUUID(), title, content: '', revision: 1 }
    updateWork({ ...work, chapters: [...work.chapters, next] })
    setParams({ chapter: next.id }); setCreating(false); setHighlight('')
  }
  function changeChapter(id: string) { setParams({ chapter: id }); setHighlight(''); setError('') }
  function write(content: string) {
    if (!chapter) return
    updateWork({ ...work, chapters: work.chapters.map(item => item.id === chapter.id ? { ...item, content, revision: item.revision + 1 } : item) })
    setHighlight('')
  }
  function analyze() {
    if (!chapter?.content.trim()) { setError('원고를 먼저 입력해 주세요.'); return }
    if (!consistency && !motivation) { setError('분석 항목을 하나 이상 선택해 주세요.'); return }
    if (!work.settings.some(setting => setting.status === '확정' && settingIds.includes(setting.id))) { setError('참고할 확정 설정을 하나 이상 선택해 주세요.'); setOptionsOpen(true); return }
    setOptionsOpen(false); setAnalyzing(true); setError(''); setView('검토'); setFocusMode(false)
    timer.current = setTimeout(() => {
      setAnalyzing(false)
      if (simulatedFailure) { setError('시연용 분석 실패입니다. 원고와 선택한 범위는 유지됩니다. 분석 설정에서 실패 시연을 해제하고 재시도해 주세요.'); return }
      const result = runMockAnalysis(work, chapter, { settingIds, previousIds, consistency, motivation })
      updateWork({ ...work, analyses: [...work.analyses.filter(item => item.chapterId !== chapter.id), result] })
    }, 900)
  }
  function decide(id: string, decision: Decision, note: string) {
    if (!analysis || stale) return
    updateWork({ ...work, analyses: work.analyses.map(item => item.chapterId === analysis.chapterId ? { ...item, reviews: item.reviews.map(review => review.id === id ? { ...review, decision, note } : review) } : item) })
  }
  function jump(quote: string) {
    setHighlight(quote); setView('원고')
    const element = editor.current
    const start = chapter?.content.indexOf(quote) ?? -1
    if (element && start >= 0) {
      // 다음 프레임에 모바일 원고 탭이 표시된 뒤 포커스와 선택 영역을 적용한다.
      requestAnimationFrame(() => { element.focus(); element.setSelectionRange(start, start + quote.length); element.scrollIntoView({ block: 'center', behavior: 'auto' }) })
    }
  }
  function toggle(ids: string[], id: string) { return ids.includes(id) ? ids.filter(item => item !== id) : [...ids, id] }
  return <div className="manuscript-workspace"><div className="manuscript-toolbar"><div className="chapter-select"><label>현재 회차<select value={chapter?.id || ''} disabled={analyzing || !chapter} onChange={event => changeChapter(event.target.value)}>{work.chapters.length ? work.chapters.map(item => <option key={item.id} value={item.id}>{item.title}</option>) : <option value="">아직 회차가 없습니다</option>}</select></label><button className="icon-button" aria-label="새 회차 추가" disabled={analyzing} onClick={() => setCreating(true)}><Icon name="plus" /></button></div><div className="toolbar-actions"><span className="save-status" role="status">{storageError ? '저장 실패 · 입력 유지' : '브라우저에 자동 저장'}</span><button disabled={analyzing} onClick={() => setOptionsOpen(true)}><Icon name="settings" />분석 설정</button><button className="primary" disabled={analyzing || !chapter} onClick={analyze}><Icon name="spark" />{analyzing ? '분석 중…' : 'AI 시연 분석'}</button></div></div>
    <div className="workspace-mobile-tabs">{['원고', '검토'].map(item => <button key={item} className={view === item ? 'selected' : ''} aria-pressed={view === item} onClick={() => setView(item)}>{item}</button>)}</div>
    <div className="focus-toolbar"><button className="text-button" aria-pressed={focusMode} onClick={() => setFocusMode(!focusMode)}>{focusMode ? '검토 패널 펼치기' : '검토 패널 접기'}</button></div>
    <div className={`editor-review-grid view-${view === '원고' ? 'editor' : 'review'} ${focusMode ? 'focus-mode' : ''}`}><section className="editor-pane" aria-label="회차 원고">{chapter ? <><div className="editor-heading"><span className="eyebrow">MANUSCRIPT</span><label className="font-select">글자 크기<select value={fontSize} onChange={event => setFontSize(Number(event.target.value))}>{[15, 17, 19, 21].map(size => <option key={size} value={size}>{size}px</option>)}</select></label></div><h2>{chapter.title}</h2><p className="small muted">원고를 자유롭게 붙여넣고 다듬어 보세요.</p>{highlight && chapter.content.includes(highlight) && <div className="source-highlight" role="status"><strong>선택한 근거 문장</strong><p><mark>{highlight}</mark></p></div>}<label className="manuscript-label">회차 원고<textarea ref={editor} disabled={analyzing} style={{ fontSize }} value={chapter.content} onChange={event => write(event.target.value)} placeholder="이곳에서 이야기를 시작하세요. 한국어 원고를 붙여넣을 수도 있어요." /></label><div className="editor-bottom"><span>{chapter.content.length.toLocaleString()}자 · 원고 버전 {chapter.revision}</span><Link to={`/works/${work.id}/settings`}>설정집 참고하기 →</Link></div></> : <div className="empty"><Icon name="file" /><h2>첫 회차를 펼쳐 보세요.</h2><p>회차를 추가하고 원고를 입력하면 검토를 시작할 수 있어요.</p><button className="primary" onClick={() => setCreating(true)}>회차 추가</button></div>}</section>
      <ReviewPanel key={chapter?.id || 'empty'} work={work} analysis={analysis} stale={stale} analyzing={analyzing} error={error} retry={analyze} onJump={jump} onDecision={decide} />
    </div>
    {creating && <Dialog title="새 회차 추가" onClose={() => setCreating(false)}><form className="stack" onSubmit={createChapter}><label>회차 제목<input name="title" required maxLength={100} placeholder={`${work.chapters.length + 1}화. 새로운 장면`} autoFocus /></label><div className="dialog-actions"><button type="button" onClick={() => setCreating(false)}>취소</button><button className="primary">회차 만들기</button></div></form></Dialog>}
    {optionsOpen && <Dialog title="분석 범위 설정" onClose={() => setOptionsOpen(false)}><div className="stack"><fieldset><legend>분석 항목</legend><label className="check"><input type="checkbox" checked={consistency} onChange={event => setConsistency(event.target.checked)} />설정 충돌 후보</label><label className="check"><input type="checkbox" checked={motivation} onChange={event => setMotivation(event.target.checked)} />인물 행동의 설명 보완</label></fieldset><fieldset><legend>참고할 확정 설정</legend>{work.settings.filter(setting => setting.status === '확정').map(setting => <label className="check" key={setting.id}><input type="checkbox" checked={settingIds.includes(setting.id)} onChange={() => setSettingIds(toggle(settingIds, setting.id))} />{setting.title}</label>)}{!work.settings.some(setting => setting.status === '확정') && <p className="small muted">설정집에서 확정 설정을 먼저 등록해 주세요.</p>}</fieldset><fieldset><legend>이전 원고 · 범위 표시용</legend>{work.chapters.filter(item => item.id !== chapter?.id).map(item => <label className="check" key={item.id}><input type="checkbox" checked={previousIds.includes(item.id)} onChange={() => setPreviousIds(toggle(previousIds, item.id))} />{item.title}</label>)}{work.chapters.length < 2 && <p className="small muted">선택할 다른 회차가 없습니다.</p>}</fieldset><label className="check"><input type="checkbox" checked={simulatedFailure} onChange={event => setSimulatedFailure(event.target.checked)} />분석 실패 상태 시연</label><p className="notice small">실제 AI 호출 없이 샘플 문장과 확정 설정을 비교합니다. 첫 샘플 작품의 2화에서 두 가지 검토 후보를 확인할 수 있습니다.</p>{error && <p className="error" role="alert">{error}</p>}<div className="dialog-actions"><button onClick={() => setOptionsOpen(false)}>닫기</button><button className="primary" disabled={!chapter || analyzing} onClick={analyze}>선택 범위로 분석</button></div></div></Dialog>}
  </div>
}
