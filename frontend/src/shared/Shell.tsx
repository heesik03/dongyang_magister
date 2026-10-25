import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '../features/auth/useAuthStore'
import { Icon } from './Icon'
import { WorksProvider } from '../features/works/WorksProvider'
import { useWorksStore } from '../features/works/useWorksStore'
import { useState } from 'react'
import { Dialog } from './Dialog'

export function Shell() {
  const user = useAuthStore((state) => state.user)
  return (
    <WorksProvider key={user?.email} email={user?.email || ''}>
      <ShellContent />
    </WorksProvider>
  )
}

function ShellContent() {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const error = useAuthStore((state) => state.error)
  const cachedWorks = useWorksStore((state) => state.works)
  const shortcutIds = useWorksStore((state) => state.shortcutIds)
  const shortcutError = useWorksStore((state) => state.shortcutError)
  const works = cachedWorks.filter(
    (work) => work.source !== 'server' || shortcutIds.includes(work.id),
  )
  const [logoutError, setLogoutError] = useState('')
  async function leave() {
    const state = useWorksStore.getState()
    await Promise.all(state.dirtyIds.map((id) => state.flushWorkspace(id)))
    if (useWorksStore.getState().dirtyIds.length) {
      setLogoutError(
        '저장되지 않은 입력이 있습니다. 저장 또는 복구본 다운로드 후 로그아웃해 주세요.',
      )
      return
    }
    await logout()
  }
  const storageError = useWorksStore((state) => state.storageError)
  const retrySave = useWorksStore((state) => state.retrySave)
  const [collapsed, setCollapsed] = useState(false)
  const [drawer, setDrawer] = useState(false)
  return (
    <div className="app-layout">
      <aside className={`sidebar ${collapsed ? 'sidebar-collapsed' : ''}`}>
        <NavLink to="/" className="brand" aria-label="Magister 작품 보관함">
          <Icon name="book" />
          Magister
        </NavLink>
        <button
          className="icon-button mobile-menu"
          aria-label="작품 탐색 메뉴 열기"
          onClick={() => setDrawer(true)}
        >
          <Icon name="menu" />
        </button>
        <div className="workspace-label">나의 작업 공간</div>
        <NavLink to="/" end className="nav-item" aria-label="작품 보관함">
          <Icon name="grid" />
          작품 보관함
        </NavLink>
        <div className="workspace-label">작품 바로가기</div>
        <div className="sidebar-works">
          {works.map((work) => (
            <NavLink key={work.id} className="nav-item" to={`/works/${work.id}/manuscript`}>
              <Icon name="book" />
              <span>{work.title}</span>
            </NavLink>
          ))}
        </div>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <Icon name="spark" />
            <p>
              좋은 이야기는
              <br />
              작은 일관성에서 시작됩니다.
            </p>
          </div>
          <div className="profile">
            <span className="avatar">{user?.name.slice(0, 1)}</span>
            <div>
              <strong>{user?.name} 작가님</strong>
              <span className="small muted">개인 작업 공간</span>
            </div>
            <button className="icon-button" aria-label="로그아웃" onClick={() => void leave()}>
              <Icon name="logout" />
            </button>
          </div>
        </div>
      </aside>
      <div className="main-area">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="icon-button desktop-menu"
              aria-label={collapsed ? '탐색 패널 펼치기' : '탐색 패널 접기'}
              onClick={() => setCollapsed(!collapsed)}
            >
              <Icon name="menu" />
            </button>
            <span>
              나의 작업 공간 <span className="breadcrumb">/</span> Magister
            </span>
          </div>
          <span className="demo-tag">개인 창작 작업 공간</span>
        </header>
        {(error || logoutError) && (
          <div role="alert" className="storage-error">
            {error || logoutError}
          </div>
        )}
        {shortcutError && (
          <div role="alert">
            작품 바로가기 조회 실패{' '}
            <button onClick={() => void useWorksStore.getState().loadShortcuts()}>다시 시도</button>
          </div>
        )}
        {storageError && (
          <div role="alert" className="storage-error">
            {storageError}
            <button onClick={retrySave}>저장 재시도</button>
          </div>
        )}
        <Outlet />
        <footer className="app-footer">
          작품·설정·원고·판단·타임라인은 서버에 저장됩니다. AI 검토 실행 시 선택한 원고와 설정을
          Gemini로 전송합니다.
        </footer>
      </div>
      {drawer && (
        <Dialog title="작품 탐색" onClose={() => setDrawer(false)}>
          <nav className="drawer-nav" aria-label="작품 탐색">
            <NavLink className="nav-item" to="/" onClick={() => setDrawer(false)}>
              <Icon name="grid" />
              작품 보관함
            </NavLink>
            {works.map((work) => (
              <NavLink
                key={work.id}
                className="nav-item"
                to={`/works/${work.id}/manuscript`}
                onClick={() => setDrawer(false)}
              >
                <Icon name="book" />
                {work.title}
              </NavLink>
            ))}
          </nav>
        </Dialog>
      )}
    </div>
  )
}
