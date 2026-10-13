import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getReturnPath, useAuth } from './auth'

export function ProtectedRoute() {
  const { user } = useAuth()
  const location = useLocation()
  return user ? <Outlet /> : <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
}
export function GuestRoute() {
  const location = useLocation()
  return useAuth().user ? <Navigate to={getReturnPath(location.state)} replace /> : <Outlet />
}
