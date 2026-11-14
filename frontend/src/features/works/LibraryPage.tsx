import { ErrorPage } from '../errors/ErrorPage'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useWorksStore } from './useWorksStore'
import { GENRES } from './workGenres'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function LibraryPage() {
  const works = useWorksStore((state) => state.works)
  const listedIds = useWorksStore((state) => state.listedIds)
  const loading = useWorksStore((state) => state.loading)
  const listErrorStatus = useWorksStore((state) => state.listErrorStatus)
  const listError = useWorksStore((state) => state.listError)
  const listPage = useWorksStore((state) => state.listPage)
  const totalElements = useWorksStore((state) => state.totalElements)
  const totalPages = useWorksStore((state) => state.totalPages)
  const loadWorks = useWorksStore((state) => state.loadWorks)
  const createWork = useWorksStore((state) => state.createWork)
  const navigate = useNavigate()
  const [selectedId, setSelectedId] = useState('')
  const [query, setQuery] = useState('')
  const [genre, setGenre] = useState('전체')
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => {
      void loadWorks(query, genre)
    }, 200)
    return () => clearTimeout(timer)
  }, [query, genre, loadWorks])
  const filtered = works.filter(
    (work) =>
      (work.source !== 'server' || listedIds.includes(work.id)) &&
      work.title.toLowerCase().includes(query.toLowerCase()) &&
      (genre === '전체' || work.genre === genre),
  )
  const ordered = [...filtered].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  const selected = ordered.find((work) => work.id === selectedId) ?? ordered[0]
  const recentChapter = selected?.chapters.at(-1)
  const chapterId = selected?.summary?.latestChapterId ?? recentChapter?.id
  const chapterTitle = selected?.summary?.latestChapterTitle ?? recentChapter?.title
  const workPath = selected ? '/works/' + selected.id + '/manuscript' : ''
  const chapterPath = chapterId ? workPath + '?chapter=' + encodeURIComponent(chapterId) : workPath
  const date = (value: string) => new Date(value).toLocaleDateString('ko-KR')

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    if (!title) return
    setSaving(true)
    setError('')
    try {
      const work = await createWork({
        title,
        genre: String(data.get('genre')),
        era: String(data.get('era')).trim(),
        description: String(data.get('description')).trim(),
      })
      navigate(`/works/${work.id}/settings`)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '작품을 만들지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }
  if (listError && !filtered.length && !loading)
    return (
      <ErrorPage
        embedded
        kind={listErrorStatus === 0 ? 'connection' : 'server'}
        onRetry={() => void loadWorks(query, genre)}
      />
    )
  return (
    <main className="page library-page">
      <div className="page-heading">
        <div>
          <h1>작품 보관함</h1>
          <p className="muted">작품을 선택해 원고를 이어 쓰세요.</p>
        </div>
        <button className="primary" onClick={() => setCreating(true)}>
          <Icon name="plus" />새 작품 만들기
        </button>
      </div>
      <div className="library-workbench editorial-surface">
        <section className="library-list-pane" aria-label={'작품 ' + totalElements + '개'}>
          <div className="editorial-section-title">
            <h2>
              <Icon name="book" />
              작품 목록
            </h2>
            <p className="muted">최근 수정한 작품부터 표시합니다.</p>
          </div>
          <div className="library-list-filters">
            <label className="search-field">
              <Icon name="search" />
              <input
                aria-label="작품 검색"
                placeholder="작품 이름으로 검색"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </label>
            <label className="library-genre-field">
              <Icon name="settings" />
              <select
                aria-label="작품 장르"
                value={genre}
                onChange={(event) => setGenre(event.target.value)}
              >
                <option value="전체">전체 장르</option>
                {[...new Set([...GENRES, ...works.map((work) => work.genre)])].map((item) => (
                  <option key={item}>{item}</option>
                ))}
              </select>
            </label>
          </div>
          {loading && (
            <p role="status" className={ordered.length ? 'sr-only' : 'muted'}>
              작품을 불러오고 있습니다.
            </p>
          )}
          {listError && (
            <div role="alert" className="error">
              {listError}
              <button onClick={() => void loadWorks(query, genre)}>다시 시도</button>
            </div>
          )}
          {!loading && !listError && !ordered.length && (
            <div className="empty">
              <Icon name="book" />
              <h3>
                {query || genre !== '전체' ? '검색 결과가 없습니다' : '등록한 작품이 없습니다'}
              </h3>
              <p>
                {query || genre !== '전체'
                  ? '다른 제목이나 장르로 찾아보세요.'
                  : '새 작품을 만들고 첫 회차를 작성하세요.'}
              </p>
              <button
                className="primary"
                onClick={() => {
                  if (query || genre !== '전체') {
                    setQuery('')
                    setGenre('전체')
                  } else setCreating(true)
                }}
              >
                {query || genre !== '전체' ? '검색 초기화' : '새 작품 만들기'}
              </button>
            </div>
          )}
          {ordered.length > 0 && (
            <ul className="editorial-list">
              {ordered.map((work) => (
                <li key={work.id}>
                  <button
                    className="editorial-list-item"
                    type="button"
                    aria-pressed={selected?.id === work.id}
                    onClick={() => setSelectedId(work.id)}
                  >
                    <Icon name="book" />
                    <span className="editorial-list-copy">
                      <strong>{work.title}</strong>
                      <span className="small muted">
                        {work.genre} · <time dateTime={work.updatedAt}>{date(work.updatedAt)}</time>
                      </span>
                    </span>
                    <Icon name="arrow" />
                  </button>
                </li>
              ))}
            </ul>
          )}
          {listPage + 1 < totalPages && (
            <button disabled={loading} onClick={() => void loadWorks(query, genre, listPage + 1)}>
              작품 더 불러오기
            </button>
          )}
        </section>
        {selected && (
          <article className="library-detail-pane" aria-label="선택한 작품">
            <header>
              <div className="library-detail-genre">{selected.genre}</div>
              <h2>{selected.title}</h2>
              {selected.era && <p className="muted">{selected.era}</p>}
              {selected.description && <p>{selected.description}</p>}
            </header>
            <section className="library-last-chapter">
              <h3>
                <Icon name="file" />
                최근 원고
              </h3>
              <p>{chapterTitle || '작성한 원고가 없습니다.'}</p>
              <p className="notice small">
                <Icon name="clock" />
                최근 수정 · {date(selected.updatedAt)}
              </p>
            </section>
            <div className="editorial-actions">
              <Link className="button primary" to={chapterPath}>
                <Icon name="edit" />
                {chapterId ? '원고 이어 쓰기' : '원고 쓰기'}
              </Link>
              <Link className="button" to={workPath + '?manage=1'}>
                <Icon name="settings" />
                작품 관리
              </Link>
            </div>
          </article>
        )}
      </div>
      {creating && (
        <Dialog
          title="새 작품 만들기"
          onClose={() => {
            if (!saving) setCreating(false)
          }}
        >
          <form className="stack" onSubmit={create}>
            <fieldset disabled={saving} className="form-fields">
              <label>
                작품 제목
                <input
                  name="title"
                  required
                  maxLength={100}
                  placeholder="이야기의 이름을 지어 주세요"
                  autoFocus
                />
              </label>
              <div className="form-grid">
                <label>
                  장르
                  <select name="genre">
                    {GENRES.map((item) => (
                      <option key={item}>{item}</option>
                    ))}
                  </select>
                </label>
                <label>
                  시대·배경
                  <input name="era" maxLength={100} placeholder="예: 가상의 중세" />
                </label>
              </div>
              <label>
                짧은 소개
                <textarea
                  name="description"
                  rows={3}
                  maxLength={500}
                  placeholder="어떤 이야기를 들려주고 싶나요?"
                />
              </label>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <div className="dialog-actions">
                <button type="button" onClick={() => setCreating(false)}>
                  취소
                </button>
                <button className="primary">작품 만들기</button>
              </div>
            </fieldset>
          </form>
        </Dialog>
      )}
    </main>
  )
}
