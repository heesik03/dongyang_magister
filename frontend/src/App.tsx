import { AppErrorBoundary } from './features/errors/AppErrorBoundary'
import { BrowserRouter, useRoutes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { routes } from './routes'
import './App.css'
import './workspaceDesign.css'

export default function App() {
  return (
    <BrowserRouter>
      <AppErrorBoundary>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </AppErrorBoundary>
    </BrowserRouter>
  )
}

function AppRoutes() {
  return useRoutes(routes)
}
