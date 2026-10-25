import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../auth/useAuthStore'
import { useWorksStore } from './useWorksStore'
import { GENRES } from './workGenres'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'

export function LibraryPage() {
  const user = useAuthStore((state) => state.user)
  const works = useWorksStore((state) => state.works)
  const listedIds = useWorksStore((state) => state.listedIds)
  const loading = useWorksStore((state) => state.loading)
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
  return (
    <main className="page library-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">MY STORIES</span>
          <h1>작품 보관함</h1>
          <p className="muted">{user?.name} 작가님, 오늘은 어떤 이야기를 이어 볼까요?</p>
        </div>
        <button className="primary" onClick={() => setCreating(true)}>
          <Icon name="plus" />새 작품 만들기
        </button>
      </div>
      <section className="welcome-note">
        <div className="welcome-icon">
          <Icon name="book" />
        </div>
        <div>
          <h2>이야기의 세계를 차근차근 쌓아 보세요.</h2>
          <p>설정을 기록하고, 원고를 살펴보고, 다음 장면을 준비하는 공간입니다.</p>
        </div>
        <span className="badge green">작가의 판단이 먼저</span>
      </section>
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
      <div className="section-line">
        <h2>
          나의 작품{' '}
          <span className="muted small">
            {totalElements}개 중 {listedIds.length}개 불러옴
          </span>
        </h2>
        <span className="small muted">최근 수정 순</span>
      </div>
      {loading && <p role="status">작품을 불러오고 있습니다.</p>}
      {listError && (
        <div role="alert" className="error">
          {listError}
          <button onClick={() => void loadWorks(query, genre)}>다시 시도</button>
        </div>
      )}
      {!loading && !listError && filtered.length === 0 ? (
        <div className="empty">
          <Icon name="book" />
          <h3>{works.length ? '검색 결과가 없어요' : '첫 이야기를 시작해 보세요'}</h3>
          <p>
            {works.length
              ? '다른 이름이나 장르로 찾아보세요.'
              : '새 작품을 만들고 세계관과 인물을 기록해 보세요.'}
          </p>
          <button
            onClick={() => (works.length ? (setQuery(''), setGenre('전체')) : setCreating(true))}
          >
            {works.length ? '검색 초기화' : '새 작품 만들기'}
          </button>
        </div>
      ) : (
        <div className="work-grid">
          {[...filtered]
            .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
            .map((work, index) => (
              <Link className="work-card" key={work.id} to={`/works/${work.id}/manuscript`}>
                <div className={`book-cover cover-${index % 3}`}>
                  <span className="cover-label">MAGISTER / MANUSCRIPT</span>
                  <span className="cover-title">{work.title}</span>
                  <div className="cover-line" />
                  <Icon name="book" />
                </div>
                <div className="work-card-body">
                  <div className="row">
                    <span className="badge">{work.genre}</span>
                    {work.source !== 'server' && (
                      <span className="badge warning">브라우저 작품</span>
                    )}
                    <span className="small muted">{work.era || '시대 미정'}</span>
                  </div>
                  <h3>{work.title}</h3>
                  <p className="small muted">
                    {work.description || '새로운 이야기가 시작되는 곳.'}
                  </p>
                  <div className="work-meta">
                    <span>
                      <Icon name="file" />
                      {work.chapters.length}개 회차
                    </span>
                    <span>
                      <Icon name="book" />
                      {work.source === 'server' ? '설정집' : `${work.settings.length}개 설정`}
                    </span>
                  </div>
                  <div className="work-card-footer">
                    <span>{new Date(work.updatedAt).toLocaleDateString('ko-KR')} 수정</span>
                    <Icon name="arrow" />
                  </div>
                </div>
              </Link>
            ))}
        </div>
      )}
      {listPage + 1 < totalPages && (
        <button disabled={loading} onClick={() => void loadWorks(query, genre, listPage + 1)}>
          작품 더 불러오기
        </button>
      )}
      <section className="getting-started">
        <span className="eyebrow">A LITTLE GUIDE</span>
        <h2>한 페이지씩, 더 단단한 이야기로.</h2>
        <div className="guide-grid">
          {[
            ['01', '세계를 기록하세요', '인물과 세계관을 설정집에 모아 두세요.'],
            ['02', '원고를 살펴보세요', '회차를 입력하고 참고 범위를 선택하세요.'],
            ['03', '작가의 답을 남기세요', '근거를 확인하고 의도한 전개를 기록하세요.'],
          ].map(([number, title, description]) => (
            <div key={number}>
              <span>{number}</span>
              <h3>{title}</h3>
              <p>{description}</p>
            </div>
          ))}
        </div>
      </section>
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
