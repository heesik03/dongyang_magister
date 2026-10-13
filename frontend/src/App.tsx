import { BrowserRouter, useRoutes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { routes } from './routes'
import './App.css'

export default function App() {
  return <BrowserRouter><AuthProvider><AppRoutes /></AuthProvider></BrowserRouter>
}

function AppRoutes() {
  return useRoutes(routes)
}
