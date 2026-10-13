import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../features/auth/auth'
import { Icon } from './Icon'

export function Shell() {
  const { user, logout } = useAuth()
  return <div className="app-layout"><aside className="sidebar"><NavLink to="/" className="brand"><Icon name="book" />Magister</NavLink><div className="workspace-label">나의 작업 공간</div>
    <NavLink to="/" end className="nav-item" aria-label="작품 보관함"><Icon name="grid" />작품 보관함</NavLink>
    <div className="sidebar-bottom"><div className="sidebar-note"><Icon name="spark" /><p>좋은 이야기는<br />작은 일관성에서 시작됩니다.</p></div><div className="profile"><span className="avatar">{user?.name.slice(0, 1)}</span><div><strong>{user?.name} 작가님</strong><span className="small muted">개인 작업 공간</span></div><button className="icon-button" aria-label="로그아웃" onClick={logout}><Icon name="logout" /></button></div></div>
  </aside><div className="main-area"><header className="topbar"><span>나의 작업 공간 <span className="breadcrumb">/</span> Magister</span><span className="demo-tag">시연 모드</span></header><Outlet /><footer className="app-footer">원고는 이 브라우저에만 저장되며 외부로 전송되지 않습니다.</footer></div></div>
}
