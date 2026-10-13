import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { AuthPage } from './features/auth/AuthPage'
import { ProtectedRoute, GuestRoute } from './features/auth/ProtectedRoute'
import { Shell } from './shared/Shell'
import './App.css'

export default function App() {
  return <BrowserRouter><AuthProvider><Routes>
    <Route element={<GuestRoute />}>
      <Route path="/login" element={<AuthPage />} />
      <Route path="/signup" element={<AuthPage signup />} />
    </Route>
    <Route element={<ProtectedRoute />}>
      <Route element={<Shell />}>
        <Route index element={<div className="page"><h1>작품 보관함</h1><p>당신의 이야기를 위한 작업 공간입니다.</p></div>} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></AuthProvider></BrowserRouter>
}
