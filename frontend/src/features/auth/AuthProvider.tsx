import { useState, type ReactNode } from 'react'
import { AuthContext, readUser, type User } from './auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readUser)
  function login(nextUser: User) {
    sessionStorage.setItem('magister.session', JSON.stringify(nextUser))
    setUser(nextUser)
  }
  function logout() {
    sessionStorage.removeItem('magister.session')
    setUser(null)
  }
  return <AuthContext value={{ user, login, logout }}>{children}</AuthContext>
}
