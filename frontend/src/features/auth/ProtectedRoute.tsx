import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getReturnPath, useAuthStore } from './useAuthStore'

export function ProtectedRoute() {
  const user = useAuthStore((state) => state.user)
  const loading = useAuthStore((state) => state.loading)
  const error = useAuthStore((state) => state.error)
  const retry = useAuthStore((state) => state.retry)
  const location = useLocation()
  if (loading) return <p role="status">로그인 상태를 확인하고 있습니다.</p>
  if (error && !user)
    return (
      <div role="alert">
        {error}
        <button onClick={retry}>다시 시도</button>
      </div>
    )
  return user ? (
    <Outlet />
  ) : (
    <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  )
}
export function GuestRoute() {
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const loading = useAuthStore((state) => state.loading)
  const error = useAuthStore((state) => state.error)
  const retry = useAuthStore((state) => state.retry)
  if (loading) return <p role="status">로그인 상태를 확인하고 있습니다.</p>
  if (error)
    return (
      <div role="alert">
        {error}
        <button onClick={retry}>다시 시도</button>
      </div>
    )
  return user ? <Navigate to={getReturnPath(location.state)} replace /> : <Outlet />
}
