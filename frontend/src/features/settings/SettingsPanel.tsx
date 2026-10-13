import { useState, type FormEvent } from 'react'
import type { Setting, SettingKind, SettingStatus, Work } from '../works/types'
import { useWorks } from '../works/store'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function SettingsPanel({ work }: { work: Work }) {
  const { updateWork } = useWorks()
  const [kind, setKind] = useState('전체')
  const [selectedId, setSelectedId] = useState(work.settings[0]?.id || '')
  const [editing, setEditing] = useState<Setting | 'new' | null>(null)
  const filtered = work.settings.filter(setting => kind === '전체' || setting.kind === kind)
  const selected = filtered.find(setting => setting.id === selectedId) || filtered[0]
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    const content = String(data.get('content')).trim()
    if (!title || !content) return
    const setting: Setting = { id: editing && editing !== 'new' ? editing.id : crypto.randomUUID(), title, content, kind: String(data.get('kind')) as SettingKind, status: String(data.get('status')) as SettingStatus }
    updateWork({ ...work, settings: editing === 'new' ? [...work.settings, setting] : work.settings.map(item => item.id === setting.id ? setting : item) })
    setSelectedId(setting.id); setKind('전체'); setEditing(null)
  }
  const draft = editing && editing !== 'new' ? editing : null
  return <section className="settings-panel"><div className="section-line"><div><h2>이야기의 기준, 설정집</h2><p className="small muted">확정된 설정을 검토의 기준으로 사용합니다. 아이디어와 후보는 구분해 보관하세요.</p></div><button className="primary" onClick={() => setEditing('new')}><Icon name="plus" />설정 등록</button></div><div className="filter-tabs setting-filters">{['전체', '인물', '세계관', '주요 사건', '의도한 각색'].map(item => <button key={item} aria-pressed={kind === item} className={kind === item ? 'selected' : ''} onClick={() => setKind(item)}>{item}</button>)}</div>
    <div className="settings-layout"><div className="setting-list">{filtered.map(setting => <button key={setting.id} className={`setting-item ${selected?.id === setting.id ? 'selected' : ''}`} onClick={() => setSelectedId(setting.id)}><Icon name={setting.kind === '인물' ? 'file' : 'book'} /><div><strong>{setting.title}</strong><span className="small muted">{setting.kind}</span></div><span className={`badge ${setting.status === '확정' ? 'green' : 'warning'}`}>{setting.status}</span></button>)}{!filtered.length && <div className="empty"><p>등록된 설정이 없습니다.</p><button onClick={() => setEditing('new')}>첫 설정 등록</button></div>}</div>
    {selected ? <article className="setting-detail"><div className="row"><span className={`badge ${selected.status === '확정' ? 'green' : 'warning'}`}>{selected.status}</span><button className="text-button" onClick={() => setEditing(selected)}>설정 수정</button></div><span className="small muted">{selected.kind}</span><h2>{selected.title}</h2><p className="pre-wrap">{selected.content}</p><div className="notice small">{selected.status === '확정' ? '작가님이 확정한 설정입니다. 검토 후보가 이 내용을 자동으로 변경하지 않습니다.' : '아직 확정하지 않은 설정입니다. 검토의 확정 근거로 사용되지 않습니다.'}</div></article> : <div className="setting-detail muted">인물과 세계관을 기록하고 원고와 연결해 보세요.</div>}</div>
    {editing && <Dialog title={editing === 'new' ? '설정 등록' : '설정 수정'} onClose={() => setEditing(null)}><form className="stack" onSubmit={save}><label>설정 이름<input name="title" required maxLength={100} defaultValue={draft?.title} autoFocus /></label><div className="form-grid"><label>유형<select name="kind" defaultValue={draft?.kind || '인물'}>{['인물', '세계관', '주요 사건', '의도한 각색'].map(item => <option key={item}>{item}</option>)}</select></label><label>상태<select name="status" defaultValue={draft?.status || '확정'}>{['확정', '아이디어', 'AI 후보'].map(item => <option key={item}>{item}</option>)}</select></label></div><label>설정 내용<textarea name="content" required rows={6} defaultValue={draft?.content} placeholder="인물의 능력, 성격, 세계의 규칙을 기록하세요." /></label><div className="dialog-actions"><button type="button" onClick={() => setEditing(null)}>취소</button><button className="primary">설정 저장</button></div></form></Dialog>}
  </section>
}
