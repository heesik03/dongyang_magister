import { Link, NavLink, Outlet, useMatch, useSearchParams } from 'react-router-dom'
import { useState } from 'react'
import { useAuthStore } from '../features/auth/useAuthStore'
import { WorksProvider } from '../features/works/WorksProvider'
import { useWorksStore } from '../features/works/useWorksStore'
import { Icon } from './Icon'
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
    query.delete('manage')
    if (review) query.set('view', 'review')
    else query.delete('view')
    const search = query.toString()
    return '/works/' + current?.id + '/' + tab + (search ? '?' + search : '')
  }
  function navigation() {
    return (
      <>
        <NavLink to="/" end className="nav-item">
          <Icon name="book" />내 작품
        </NavLink>
        {current ? (
          <>
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
            >
              <Icon name="search" />
              검토 결과
            </Link>
            <NavLink className="nav-item" to={workTab('settings')}>
              <Icon name="grid" />
              인물·세계관
            </NavLink>
            <NavLink className="nav-item" to={workTab('timeline')}>
              <Icon name="clock" />
              사건 순서
            </NavLink>
          </>
        ) : works.length > 0 ? (
          <details className="workspace-shortcuts">
            <summary>
              <Icon name="history" />
              최근 작업
            </summary>
            <div className="workspace-shortcut-list">
              {works.map((work) => (
                <NavLink
                  key={work.id}
                  className="nav-item"
                  to={'/works/' + work.id + '/manuscript'}
                >
                  <span>{work.title}</span>
                </NavLink>
              ))}
            </div>
          </details>
        ) : null}
      </>
    )
  }
  return (
    <div className="app-layout">
      <header className="editorial-header">
        <NavLink to="/" className="brand" aria-label="Magister 작품 보관함">
          Magister
        </NavLink>
        <nav className="editorial-navigation" aria-label="작품 탐색">
          {navigation()}
        </nav>
        <div className="editorial-account">
          <NavLink to="/mypage" className={'nav-item' + (isMyPage ? ' active' : '')}>
            <Icon name="settings" />내 계정
          </NavLink>
          <button className="icon-button" aria-label="로그아웃" onClick={() => void leave()}>
            <Icon name="logout" />
          </button>
        </div>
      </header>
      <div className="main-area">
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
    </div>
  )
}
