import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './auth'

export function ProtectedRoute() {
  const { user } = useAuth()
  const location = useLocation()
  return user ? <Outlet /> : <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
}
export function GuestRoute() {
  return useAuth().user ? <Navigate to="/" replace /> : <Outlet />
}
