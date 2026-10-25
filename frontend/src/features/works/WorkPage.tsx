import { useEffect, useState, type FormEvent } from 'react'
import { WorkspaceStatus } from '../workspace/WorkspaceStatus'
import { GENRES } from './workGenres'
import { Dialog } from '../../shared/Dialog'
import { Link, Navigate, NavLink, useParams } from 'react-router-dom'
import { useWorksStore } from './useWorksStore'
import { SettingsPanel } from '../settings/SettingsPanel'
import { Icon } from '../../shared/Icon'
import { ManuscriptPanel } from '../manuscript/ManuscriptPanel'
import { TimelinePanel } from '../timeline/TimelinePanel'

export function WorkPage() {
  const { workId, tab } = useParams()
  const loaded = useWorksStore((state) => !!workId && state.loadedIds.includes(workId))
  const works = useWorksStore((state) => state.works)
  const loadWork = useWorksStore((state) => state.loadWork)
  const detailLoading = useWorksStore((state) => state.detailLoading)
  const detailError = useWorksStore((state) => state.detailError)
  const saveWorkInfo = useWorksStore((state) => state.saveWorkInfo)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    if (workId) void loadWork(workId)
  }, [workId, loadWork])
  const work = works.find((item) => item.id === workId)
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!work || saving) return
    const data = new FormData(event.currentTarget)
    setSaving(true)
    setError('')
    try {
      await saveWorkInfo(work, {
        title: String(data.get('title')).trim(),
        genre: String(data.get('genre')).trim(),
        era: String(data.get('era')).trim(),
        description: String(data.get('description')).trim(),
      })
      setEditing(false)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }
  if ((!loaded && !detailError) || (detailLoading && !work))
    return <p role="status">작품을 불러오고 있습니다.</p>
  if (detailError)
    return (
      <main className="page empty">
        <p role="alert">{detailError}</p>
        <button
          onClick={() => {
            if (workId) void loadWork(workId)
          }}
        >
          다시 불러오기
        </button>
        <Link to="/">작품 보관함으로</Link>
      </main>
    )
  if (!work)
    return (
      <main className="page empty">
        <h1>작품을 찾을 수 없어요</h1>
        <Link className="button" to="/">
          작품 보관함으로
        </Link>
      </main>
    )
  if (!['manuscript', 'settings', 'timeline'].includes(tab || ''))
    return <Navigate to={`/works/${work.id}/manuscript`} replace />
  return (
    <main className="work-page">
      <div className="work-heading">
        <Link to="/" className="small muted">
          작품 보관함 /
        </Link>
        <div className="row">
          <div>
            <h1>{work.title}</h1>
            <p className="small muted">
              {work.genre} · {work.era || '시대 미정'}
            </p>
          </div>
          <div className="row">
            <span className="badge green">
              {work.source === 'server' ? '개인 작품' : '브라우저 작품'}
            </span>
            <button onClick={() => setEditing(true)}>작품 정보 수정</button>
            <button onClick={() => void loadWork(work.id)} disabled={detailLoading}>
              새로 불러오기
            </button>
          </div>
        </div>
      </div>
      {editing && (
        <Dialog
          title="작품 정보 수정"
          onClose={() => {
            if (!saving) setEditing(false)
          }}
        >
          <form className="stack" onSubmit={save}>
            <fieldset disabled={saving} className="form-fields">
              <label>
                작품 제목
                <input name="title" required maxLength={100} defaultValue={work.title} autoFocus />
              </label>
              <label>
                장르
                <select name="genre" defaultValue={work.genre}>
                  {[...new Set([...GENRES, work.genre])].map((genre) => (
                    <option key={genre}>{genre}</option>
                  ))}
                </select>
              </label>
              <label>
                시대·배경
                <input name="era" maxLength={100} defaultValue={work.era} />
              </label>
              <label>
                짧은 소개
                <textarea name="description" maxLength={500} defaultValue={work.description} />
              </label>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <div className="dialog-actions">
                <button type="button" onClick={() => setEditing(false)}>
                  취소
                </button>
                <button className="primary">작품 저장</button>
              </div>
            </fieldset>
          </form>
        </Dialog>
      )}
      <nav className="work-tabs" aria-label="작품 메뉴">
        {[
          ['manuscript', '원고 · 검토', 'file'],
          ['settings', '설정집', 'book'],
          ['timeline', '사건 타임라인', 'clock'],
        ].map(([path, label, icon]) => (
          <NavLink to={`/works/${work.id}/${path}`} key={path}>
            <Icon name={icon === 'file' ? 'file' : icon === 'book' ? 'book' : 'clock'} />
            {label}
          </NavLink>
        ))}
      </nav>
      {work.source === 'server' && <WorkspaceStatus work={work} />}
      <div className="work-content">
        {tab === 'settings' ? (
          <SettingsPanel key={work.id} work={work} />
        ) : tab === 'timeline' ? (
          <TimelinePanel key={work.id} work={work} />
        ) : (
          <ManuscriptPanel key={work.id} work={work} />
        )}
      </div>
    </main>
  )
}
