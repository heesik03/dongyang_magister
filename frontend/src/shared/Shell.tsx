import { Link, NavLink, Outlet, useMatch, useSearchParams } from 'react-router-dom'
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
  const logout = useAuthStore((state) => state.logout)
  const error = useAuthStore((state) => state.error)
  const cachedWorks = useWorksStore((state) => state.works)
  const shortcutIds = useWorksStore((state) => state.shortcutIds)
  const shortcutError = useWorksStore((state) => state.shortcutError)
  const storageError = useWorksStore((state) => state.storageError)
  const retrySave = useWorksStore((state) => state.retrySave)
  const match = useMatch('/works/:workId/:tab')
  const isMyPage = Boolean(useMatch('/mypage'))
  const [params] = useSearchParams()
  const current = cachedWorks.find((work) => work.id === match?.params.workId)
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
  function workTab(tab: 'manuscript' | 'settings' | 'timeline', review = false) {
    const query =
      tab === 'manuscript' && match?.params.tab === 'manuscript'
        ? new URLSearchParams(params)
        : new URLSearchParams()
    if (review) query.set('view', 'review')
    else query.delete('view')
    const search = query.toString()
    return '/works/' + current?.id + '/' + tab + (search ? '?' + search : '')
  }
  function navigation() {
    return (
      <>
        <NavLink to="/" end className="nav-item" onClick={() => setDrawer(false)}>
          <Icon name="book" />내 작품
        </NavLink>
        {current ? (
          <>
            <div className="sidebar-current">
              <strong>{current.title}</strong>
            </div>
            <Link
              className={
                'nav-item' +
                (match?.params.tab === 'manuscript' && params.get('view') !== 'review'
                  ? ' active'
                  : '')
              }
              aria-current={
                match?.params.tab === 'manuscript' && params.get('view') !== 'review'
                  ? 'page'
                  : undefined
              }
              to={workTab('manuscript')}
              onClick={() => setDrawer(false)}
            >
              <Icon name="edit" />
              원고 쓰기
            </Link>
            <Link
              className={
                'nav-item' +
                (match?.params.tab === 'manuscript' && params.get('view') === 'review'
                  ? ' active'
                  : '')
              }
              aria-current={
                match?.params.tab === 'manuscript' && params.get('view') === 'review'
                  ? 'page'
                  : undefined
              }
              to={workTab('manuscript', true)}
              onClick={() => setDrawer(false)}
            >
              <Icon name="search" />
              검토 결과
            </Link>
            <NavLink className="nav-item" to={workTab('settings')} onClick={() => setDrawer(false)}>
              <Icon name="book" />
              인물·세계관
            </NavLink>
            <NavLink className="nav-item" to={workTab('timeline')} onClick={() => setDrawer(false)}>
              <Icon name="clock" />
              사건 순서
            </NavLink>
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
        <div className="sidebar-header">
          <NavLink to="/" className="brand" aria-label="Magister 작품 보관함">
            Magister
          </NavLink>
          <button
            className="icon-button sidebar-toggle"
            aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
            title={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
            aria-expanded={!collapsed}
            aria-controls="sidebar-navigation"
            onClick={() => setCollapsed(!collapsed)}
          >
            <Icon name={collapsed ? 'panel-left-open' : 'panel-left-close'} />
          </button>
        </div>
        <button
          className="icon-button mobile-menu"
          aria-label="작품 탐색 메뉴 열기"
          onClick={() => setDrawer(true)}
        >
          <Icon name="menu" />
        </button>
        <p className="sidebar-caption">소설 원고와 설정 관리</p>
        <div className="sidebar-navigation" id="sidebar-navigation">
          {navigation()}
        </div>
        <div className="sidebar-bottom">
          <div className={'profile ' + (isMyPage ? 'profile-active' : '')}>
            <NavLink
              to="/mypage"
              className="profile-link"
              aria-label="마이페이지 이동"
              onClick={() => setDrawer(false)}
            >
              <Icon name="settings" />내 계정
            </NavLink>
            <button className="icon-button" aria-label="로그아웃" onClick={() => void leave()}>
              <Icon name="logout" />
            </button>
          </div>
        </div>
      </aside>
      <div className="main-area">
        <header className="topbar">
          <div className="topbar-left">
            {isMyPage ? (
              <>
                <span className="muted">내 계정</span>
                <Icon name="chevron" />
                <strong>마이페이지</strong>
              </>
            ) : (
              <>
                <NavLink to="/">내 작품</NavLink>
                <Icon name="chevron" />
                <strong>{current?.title || '나의 작업 공간'}</strong>
              </>
            )}
          </div>
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
          <nav className="drawer-nav">
            {navigation()}
            <NavLink to="/mypage" className="nav-item" onClick={() => setDrawer(false)}>
              마이페이지
            </NavLink>
          </nav>
        </Dialog>
      )}
    </div>
  )
}
