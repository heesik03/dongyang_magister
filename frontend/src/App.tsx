import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { AuthPage } from './features/auth/AuthPage'
import { ProtectedRoute, GuestRoute } from './features/auth/ProtectedRoute'
import { Shell } from './shared/Shell'
import { LibraryPage } from './features/works/LibraryPage'
import { WorkPage } from './features/works/WorkPage'
import './App.css'

export default function App() {
  return <BrowserRouter><AuthProvider><Routes>
    <Route element={<GuestRoute />}>
      <Route path="/login" element={<AuthPage />} />
      <Route path="/signup" element={<AuthPage signup />} />
    </Route>
    <Route element={<ProtectedRoute />}>
      <Route element={<Shell />}>
        <Route index element={<LibraryPage />} />
        <Route path="works/:workId/:tab" element={<WorkPage />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></AuthProvider></BrowserRouter>
}
