import { NavLink, Outlet, useMatch, useSearchParams } from 'react-router-dom'
import { useState } from 'react'
import { useAuthStore } from '../features/auth/useAuthStore'
import { WorksProvider } from '../features/works/WorksProvider'
import { useWorksStore } from '../features/works/useWorksStore'
import { Icon } from './Icon'
import { Dialog } from './Dialog'
import { SiteFooter } from './SiteFooter'

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
  const storageError = useWorksStore((state) => state.storageError)
  const retrySave = useWorksStore((state) => state.retrySave)
  const match = useMatch('/works/:workId/:tab')
  const [params] = useSearchParams()
  const current = cachedWorks.find((work) => work.id === match?.params.workId)
  const selectedChapter = params.get('chapter') || current?.chapters.at(-1)?.id
  const works = cachedWorks.filter(
    (work) => work.source !== 'server' || shortcutIds.includes(work.id),
  )
  const [logoutError, setLogoutError] = useState('')
  const [collapsed, setCollapsed] = useState(false)
  const [drawer, setDrawer] = useState(false)
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
  function navigation() {
    return (
      <>
        <NavLink to="/" end className="nav-item" onClick={() => setDrawer(false)}>
          <Icon name="grid" />
          작품 보관함
        </NavLink>
        {current ? (
          <>
            <div className="sidebar-current">
              <span className="small muted">작업 중인 작품</span>
              <strong>{current.title}</strong>
              <span className="small muted">
                {current.genre} · {current.era || '시대 미정'}
              </span>
            </div>
            {(['manuscript', 'settings', 'timeline'] as const).map((tab) => (
              <NavLink
                className="nav-item"
                key={tab}
                to={'/works/' + current.id + '/' + tab}
                onClick={() => setDrawer(false)}
              >
                <Icon
                  name={tab === 'manuscript' ? 'file' : tab === 'settings' ? 'book' : 'clock'}
                />
                {tab === 'manuscript'
                  ? '원고 · 검토'
                  : tab === 'settings'
                    ? '설정집'
                    : '사건 타임라인'}
              </NavLink>
            ))}
            <div className="sidebar-chapters">
              <span className="small muted">회차 / {current.chapters.length}</span>
              <nav aria-label="회차 탐색">
                {current.chapters.map((chapter) => (
                  <NavLink
                    key={chapter.id}
                    className={
                      'nav-item ' + (selectedChapter === chapter.id ? 'chapter-active' : '')
                    }
                    to={
                      '/works/' +
                      current.id +
                      '/manuscript?chapter=' +
                      encodeURIComponent(chapter.id)
                    }
                    onClick={() => setDrawer(false)}
                  >
                    {chapter.title}
                  </NavLink>
                ))}
              </nav>
              <NavLink
                className="nav-item add-chapter"
                onClick={() => setDrawer(false)}
                to={'/works/' + current.id + '/manuscript?newChapter=' + crypto.randomUUID()}
              >
                <Icon name="plus" />
                회차 추가
              </NavLink>
            </div>
          </>
        ) : (
          <div className="sidebar-works">
            <span className="small muted">최근 작업</span>
            {works.map((work) => (
              <NavLink
                key={work.id}
                className="nav-item"
                to={'/works/' + work.id + '/manuscript'}
                onClick={() => setDrawer(false)}
              >
                <span>{work.title}</span>
              </NavLink>
            ))}
          </div>
        )}
      </>
    )
  }
  return (
    <div className="app-layout">
      <aside className={'sidebar ' + (collapsed ? 'sidebar-collapsed' : '')}>
        <NavLink to="/" className="brand" aria-label="Magister 작품 보관함">
          <Icon name="book" brand />
          Magister
        </NavLink>
        <button
          className="icon-button mobile-menu"
          aria-label="작품 탐색 메뉴 열기"
          onClick={() => setDrawer(true)}
        >
          <Icon name="menu" />
        </button>
        <div className="sidebar-navigation">{navigation()}</div>
        <div className="sidebar-bottom">
          {!current && (
            <p className="sidebar-note">
              좋은 이야기는
              <br />
              작은 일관성에서 시작됩니다.
            </p>
          )}
          <div className="profile">
            <span className="avatar">{user?.name.slice(0, 1)}</span>
            <div>
              <strong>{user?.name} 작가님</strong>
              <span className="small muted">개인 창작 공간</span>
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
            <NavLink to="/">작품 보관함</NavLink>
            <Icon name="chevron" />
            <strong>{current?.title || '나의 작업 공간'}</strong>
          </div>
          <span className="demo-tag">개인 작업 공간</span>
        </header>
        {(error || logoutError) && (
          <div role="alert" className="storage-error">
            {error || logoutError}
          </div>
        )}
        {shortcutError && (
          <div role="alert" className="storage-error">
            최근 작업 조회 실패{' '}
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
        <SiteFooter />
      </div>
      {drawer && (
        <Dialog title="작품 탐색" onClose={() => setDrawer(false)}>
          <nav className="drawer-nav">{navigation()}</nav>
        </Dialog>
      )}
    </div>
  )
}
