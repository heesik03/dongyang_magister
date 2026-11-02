import { Link, useNavigate } from 'react-router-dom'
import { SiteFooter } from '../../shared/SiteFooter'
import { Icon } from '../../shared/Icon'
export type ErrorKind = 'not-found' | 'server' | 'connection'
const content = {
  'not-found': {
    code: '404',
    title: '이 페이지는 찾을 수 없어요',
    message:
      '주소가 변경되었거나 더 이상 제공되지 않는 페이지예요.\n주소를 확인하거나 작품 보관함으로 이동해 주세요.',
  },
  server: {
    code: '500',
    title: '잠시 문제가 생겼어요',
    message:
      '일시적인 문제로 페이지를 불러오지 못했어요.\n잠시 후 다시 시도하거나 작품 보관함으로 이동해 주세요.',
  },
  connection: {
    code: '!',
    title: '연결 상태를 확인해 주세요',
    message:
      '서버에 연결하지 못해 페이지를 불러올 수 없어요.\n네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
  },
}
export function ErrorPage({
  kind = 'not-found',
  onRetry,
  message,
  embedded = false,
}: {
  kind?: ErrorKind
  onRetry?: () => void
  message?: string
  embedded?: boolean
}) {
  const navigate = useNavigate()
  const error = content[kind]
  const panel = (
    <section className="error-card" aria-labelledby="error-page-title">
      <div className="error-code" aria-hidden="true">
        {error.code}
      </div>
      <h1 id="error-page-title">{error.title}</h1>
      <p role={kind === 'not-found' ? undefined : 'alert'}>{message || error.message}</p>
      <div className="error-page-actions">
        {kind === 'not-found' ? (
          <>
            <Link className="button primary" to="/">
              작품 보관함으로
            </Link>
            <button
              onClick={() => {
                if (window.history.state?.idx > 0) navigate(-1)
                else navigate('/', { replace: true })
              }}
            >
              이전 페이지로
            </button>
          </>
        ) : (
          <>
            <button className="primary" onClick={onRetry || (() => window.location.reload())}>
              다시 시도
            </button>
            <Link className="button" to="/">
              작품 보관함으로
            </Link>
          </>
        )}
      </div>
    </section>
  )
  if (embedded) return <main className="error-content">{panel}</main>
  return (
    <div className="error-page">
      <header className="error-header">
        <Link className="brand" to="/">
          <Icon name="book" brand />
          Magister
        </Link>
      </header>
      <main className="error-content">{panel}</main>
      <SiteFooter />
    </div>
  )
}
