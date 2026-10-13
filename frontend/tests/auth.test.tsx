// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../src/features/auth/AuthProvider'
import { AuthPage } from '../src/features/auth/AuthPage'
import { ProtectedRoute, GuestRoute } from '../src/features/auth/ProtectedRoute'

beforeEach(() => sessionStorage.clear())
afterEach(() => { cleanup(); vi.restoreAllMocks() })

function renderAuth(path = '/works/demo/manuscript', signup = false) {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><Routes>
    <Route element={<GuestRoute />}><Route path="/login" element={<AuthPage />} /><Route path="/signup" element={<AuthPage signup={signup} />} /></Route>
    <Route element={<ProtectedRoute />}><Route path="/works/demo/manuscript" element={<h1>보호된 원고</h1>} /></Route>
  </Routes></AuthProvider></MemoryRouter>)
}

test('비인증 접근은 로그인으로 보내고 입장 후 원래 보호 주소로 돌아간다', async () => {
  renderAuth()
  expect(await screen.findByRole('heading', { name: '다시 만나 반가워요' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '샘플 작품으로 둘러보기' }))
  expect(await screen.findByRole('heading', { name: '보호된 원고' })).toBeTruthy()
})

test('회원가입 비밀번호 불일치 시 입력을 유지하고 세션을 만들지 않는다', async () => {
  renderAuth('/signup', true)
  fireEvent.change(screen.getByLabelText('필명'), { target: { value: '시연 작가' } })
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'test@example.com' } })
  fireEvent.change(screen.getByLabelText('비밀번호', { exact: true }), { target: { value: 'sample123' } })
  fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'different123' } })
  fireEvent.submit(screen.getByRole('button', { name: '회원가입하고 시작하기' }).closest('form')!)
  expect((await screen.findByRole('alert')).textContent).toContain('일치하지 않습니다')
  expect(sessionStorage.getItem('magister.session')).toBeNull()
  expect((screen.getByLabelText('필명') as HTMLInputElement).value).toBe('시연 작가')
})

test('세션 저장 실패를 로그인 화면에 안내하고 보호 화면을 열지 않는다', async () => {
  renderAuth('/login')
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage denied') })
  fireEvent.click(screen.getByRole('button', { name: '샘플 작품으로 둘러보기' }))
  expect((await screen.findByRole('alert')).textContent).toContain('세션 저장이 차단')
  expect(screen.queryByRole('heading', { name: '보호된 원고' })).toBeNull()
})
