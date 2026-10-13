import { Link, NavLink, useParams } from 'react-router-dom'
import { useWorks } from './store'
import { SettingsPanel } from '../settings/SettingsPanel'
import { Icon } from '../../shared/Icon'

export function WorkPage() {
  const { workId, tab } = useParams()
  const { works } = useWorks()
  const work = works.find(item => item.id === workId)
  if (!work) return <main className="page empty"><h1>작품을 찾을 수 없어요</h1><Link className="button" to="/">작품 보관함으로</Link></main>
  return <main className="work-page"><div className="work-heading"><Link to="/" className="small muted">작품 보관함 /</Link><div className="row"><div><h1>{work.title}</h1><p className="small muted">{work.genre} · {work.era || '시대 미정'}</p></div><span className="badge green">개인 작품</span></div></div><nav className="work-tabs" aria-label="작품 메뉴">{[['manuscript', '원고 · 검토', 'file'], ['settings', '설정집', 'book'], ['timeline', '사건 타임라인', 'clock']].map(([path, label, icon]) => <NavLink to={`/works/${work.id}/${path}`} key={path}><Icon name={icon === 'file' ? 'file' : icon === 'book' ? 'book' : 'clock'} />{label}</NavLink>)}</nav><div className="work-content">{tab === 'settings' ? <SettingsPanel key={work.id} work={work} /> : <div className="empty"><h2>{tab === 'timeline' ? '사건 타임라인' : '원고 · 검토 작업실'}</h2><p>이야기를 이어갈 공간입니다.</p></div>}</div></main>
}
