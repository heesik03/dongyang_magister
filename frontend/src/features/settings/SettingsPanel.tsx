import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Setting, Work } from '../works/workTypes'
import { useWorksStore } from '../works/useWorksStore'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function SettingsPanel({ work }: { work: Work }) {
  const saveSetting = useWorksStore((state) => state.saveSetting)
  const loadSettings = useWorksStore((state) => state.loadSettings)
  const settingView = useWorksStore((state) => state.settingView)
  const [kind, setKind] = useState('전체')
  const latestKind = useRef(kind)
  useEffect(() => {
    latestKind.current = kind
  }, [kind])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    void loadSettings(work.id, kind)
  }, [work.id, kind, loadSettings])
  const [selectedId, setSelectedId] = useState(work.settings[0]?.id || '')
  const [editing, setEditing] = useState<Setting | 'new' | null>(null)
  // 서버 페이지의 표시 순서는 유지하면서, 본문은 캐시에 병합된 최신 설정을 사용한다.
  const filtered =
    work.source === 'server'
      ? settingView.workId === work.id && settingView.kind === kind
        ? settingView.ids.flatMap((id) => {
            const item = work.settings.find((setting) => setting.id === id)
            return item ? [item] : []
          })
        : []
      : work.settings.filter((setting) => kind === '전체' || setting.kind === kind)
  const selected = filtered.find((setting) => setting.id === selectedId) || filtered[0]
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    const content = String(data.get('content'))
    const inputKind = data.get('kind')
    const status = data.get('status')
    if (!title || !content.trim()) return
    if (
      inputKind !== '인물' &&
      inputKind !== '세계관' &&
      inputKind !== '주요 사건' &&
      inputKind !== '의도한 각색'
    )
      return
    if (status !== '확정' && status !== '아이디어' && status !== 'AI 후보') return
    setSaving(true)
    setError('')
    try {
      const saved = await saveSetting(
        work,
        { title, content, kind: inputKind, status },
        editing && editing !== 'new' ? editing : undefined,
      )
      setSelectedId(saved.id)
      setEditing(null)
      await loadSettings(work.id, latestKind.current)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '설정을 저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }
  const draft = editing && editing !== 'new' ? editing : null
  return (
    <section className="settings-panel">
      <div className="section-line">
        <div>
          <h2>이야기의 기준, 설정집</h2>
          <p className="small muted">
            확정된 설정을 검토의 기준으로 사용합니다. 아이디어와 후보는 구분해 보관하세요.
          </p>
        </div>
        <button className="primary" onClick={() => setEditing('new')}>
          <Icon name="plus" />
          설정 등록
        </button>
      </div>
      <div className="filter-tabs setting-filters">
        {['전체', '인물', '세계관', '주요 사건', '의도한 각색'].map((item) => (
          <button
            key={item}
            aria-pressed={kind === item}
            className={kind === item ? 'selected' : ''}
            onClick={() => setKind(item)}
          >
            {item}
          </button>
        ))}
      </div>
      {work.source === 'server' && settingView.loading && (
        <p role="status">설정을 불러오고 있습니다.</p>
      )}
      {work.source === 'server' && settingView.error && (
        <div role="alert" className="error">
          {settingView.error}
          <button onClick={() => void loadSettings(work.id, kind)}>다시 시도</button>
        </div>
      )}
      {work.source === 'server' && settingView.hasNext && (
        <button
          disabled={settingView.loading}
          onClick={() => void loadSettings(work.id, kind, settingView.nextCursor ?? undefined)}
        >
          설정 더 불러오기
        </button>
      )}
      <div className="settings-layout">
        <div className="setting-list">
          {filtered.map((setting) => (
            <button
              key={setting.id}
              className={`setting-item ${selected?.id === setting.id ? 'selected' : ''}`}
              onClick={() => setSelectedId(setting.id)}
            >
              <Icon name={setting.kind === '인물' ? 'file' : 'book'} />
              <div>
                <strong>{setting.title}</strong>
                <span className="small muted">{setting.kind}</span>
              </div>
              <span className={`badge ${setting.status === '확정' ? 'green' : 'warning'}`}>
                {setting.status}
              </span>
            </button>
          ))}
          {!filtered.length && !settingView.loading && !settingView.error && (
            <div className="empty">
              <p>등록된 설정이 없습니다.</p>
              <button onClick={() => setEditing('new')}>첫 설정 등록</button>
            </div>
          )}
        </div>
        {selected ? (
          <article className="setting-detail">
            <div className="row">
              <span className={`badge ${selected.status === '확정' ? 'green' : 'warning'}`}>
                {selected.status}
              </span>
              <button className="text-button" onClick={() => setEditing(selected)}>
                설정 수정
              </button>
            </div>
            <span className="small muted">{selected.kind}</span>
            <h2>{selected.title}</h2>
            <p className="pre-wrap">{selected.content}</p>
            <div className="notice small">
              {selected.status === '확정'
                ? '작가님이 확정한 설정입니다. 검토 후보가 이 내용을 자동으로 변경하지 않습니다.'
                : '아직 확정하지 않은 설정입니다. 검토의 확정 근거로 사용되지 않습니다.'}
            </div>
          </article>
        ) : (
          <div className="setting-detail muted">인물과 세계관을 기록하고 원고와 연결해 보세요.</div>
        )}
      </div>
      {editing && (
        <Dialog
          title={editing === 'new' ? '설정 등록' : '설정 수정'}
          onClose={() => {
            if (!saving) setEditing(null)
          }}
        >
          <form className="stack" onSubmit={save}>
            <fieldset disabled={saving} className="form-fields">
              <label>
                설정 이름
                <input
                  name="title"
                  required
                  maxLength={100}
                  defaultValue={draft?.title}
                  autoFocus
                />
              </label>
              <div className="form-grid">
                <label>
                  유형
                  <select name="kind" defaultValue={draft?.kind || '인물'}>
                    {['인물', '세계관', '주요 사건', '의도한 각색'].map((item) => (
                      <option key={item}>{item}</option>
                    ))}
                  </select>
                </label>
                <label>
                  상태
                  <select name="status" defaultValue={draft?.status || '확정'}>
                    {['확정', '아이디어', 'AI 후보'].map((item) => (
                      <option key={item}>{item}</option>
                    ))}
                  </select>
                </label>
              </div>
              <label>
                설정 내용
                <textarea
                  name="content"
                  required
                  rows={6}
                  maxLength={10000}
                  defaultValue={draft?.content}
                  placeholder="인물의 능력, 성격, 세계의 규칙을 기록하세요."
                />
              </label>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <div className="dialog-actions">
                <button type="button" onClick={() => setEditing(null)}>
                  취소
                </button>
                <button className="primary">설정 저장</button>
              </div>
            </fieldset>
          </form>
        </Dialog>
      )}
    </section>
  )
}
