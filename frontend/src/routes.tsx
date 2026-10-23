import { Navigate, type RouteObject } from 'react-router-dom'
import { AuthPage } from './features/auth/AuthPage'
import { GuestRoute, ProtectedRoute } from './features/auth/ProtectedRoute'
import { LibraryPage } from './features/works/LibraryPage'
import { WorkPage } from './features/works/WorkPage'
import { Shell } from './shared/Shell'

export const guestRoutes: RouteObject[] = [
  { path: '/login', element: <AuthPage /> },
  { path: '/signup', element: <AuthPage signup /> },
]

export const protectedRoutes: RouteObject[] = [
  { index: true, element: <LibraryPage /> },
  { path: 'works/:workId/:tab', element: <WorkPage /> },
]

export const routes: RouteObject[] = [
  { element: <GuestRoute />, children: guestRoutes },
  { element: <ProtectedRoute />, children: [{ element: <Shell />, children: protectedRoutes }] },
  { path: '*', element: <Navigate to="/" replace /> },
]
