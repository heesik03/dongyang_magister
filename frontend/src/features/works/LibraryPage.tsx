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
          <h1>내 작품</h1>
          <p className="muted">작품을 선택해 원고를 이어 쓰세요.</p>
        </div>
        <button className="primary" onClick={() => setCreating(true)}>
          <Icon name="plus" />새 작품 만들기
        </button>
      </div>
      <details className="workspace-disclosure library-filters">
        <summary>작품 검색 및 장르 필터{query || genre !== '전체' ? ' · 적용 중' : ''}</summary>
        <div className="library-toolbar">
          <div className="filter-tabs">
            {[
              '전체',
              ...new Set([
                '판타지',
                '로맨스',
                '현대 드라마',
                '역사',
                '미스터리',
                'SF',
                '기타',
                ...works.map((work) => work.genre),
              ]),
            ].map((item) => (
              <button
                key={item}
                className={genre === item ? 'selected' : ''}
                aria-pressed={genre === item}
                onClick={() => setGenre(item)}
              >
                {item}
              </button>
            ))}
          </div>
          <label className="search-field">
            <Icon name="search" />
            <input
              aria-label="작품 검색"
              placeholder="작품 이름으로 검색"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
        </div>
      </details>
      {loading && (
        <p role="status" className="sr-only">
          작품을 불러오고 있습니다.
        </p>
      )}
      {listError && (
        <div role="alert" className="error">
          {listError}
          <button onClick={() => void loadWorks(query, genre)}>다시 시도</button>
        </div>
      )}
      {loading && filtered.length === 0 ? (
        <div className="workspace-list work-list list-skeleton" aria-hidden="true">
          {Array.from({ length: 4 }, (_, index) => (
            <div className="workspace-list-row" key={index}>
              <span />
              <span />
              <span />
              <span />
            </div>
          ))}
        </div>
      ) : !listError && filtered.length === 0 ? (
        <div className="empty">
          <Icon name="book" />
          <h3>{query || genre !== '전체' ? '검색 결과가 없습니다' : '등록한 작품이 없습니다'}</h3>
          <p>
            {query || genre !== '전체'
              ? '다른 제목이나 장르로 찾아보세요.'
              : '새 작품을 만들고 첫 회차를 작성하세요.'}
          </p>
          <button
            className="primary"
            onClick={() =>
              query || genre !== '전체' ? (setQuery(''), setGenre('전체')) : setCreating(true)
            }
          >
            {query || genre !== '전체' ? '검색 초기화' : '새 작품 만들기'}
          </button>
        </div>
      ) : (
        <section className="workspace-list work-list" aria-label={'작품 ' + totalElements + '개'}>
          <div className="workspace-list-row workspace-list-header" aria-hidden="true">
            <span>작품</span>
            <span>장르</span>
            <span>회차</span>
            <span>최근 수정</span>
          </div>
          {[...filtered]
            .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
            .map((work) => (
              <Link
                className="workspace-list-row work-list-link"
                key={work.id}
                to={'/works/' + work.id + '/manuscript'}
              >
                <strong>{work.title}</strong>
                <span>
                  <span className="sr-only">장르 </span>
                  {work.genre}
                </span>
                <span>{work.summary?.chapterCount ?? work.chapters.length}화</span>
                <time dateTime={work.updatedAt}>
                  <span className="sr-only">최근 수정 </span>
                  {new Date(work.updatedAt).toLocaleDateString('ko-KR')}
                </time>
              </Link>
            ))}
        </section>
      )}
      {listPage + 1 < totalPages && (
        <button disabled={loading} onClick={() => void loadWorks(query, genre, listPage + 1)}>
          작품 더 불러오기
        </button>
      )}
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
