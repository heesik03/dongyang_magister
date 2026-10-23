// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../src/features/auth/AuthProvider'
import { AuthPage } from '../src/features/auth/AuthPage'
import { ProtectedRoute, GuestRoute } from '../src/features/auth/ProtectedRoute'
import { useAuth } from '../src/features/auth/auth'
import { ApiError, authApi } from '../src/features/auth/api'

vi.mock('../src/features/auth/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/features/auth/api')>()
  return {
    ...original,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn(), logout: vi.fn(), checkEmail: vi.fn() },
  }
})
const user = { id: 1, name: '작가', email: 'writer@example.com' }
beforeEach(() => {
  useAuth.setState({ user: null, loading: true, error: '' })
  vi.resetAllMocks()
  vi.mocked(authApi.me).mockRejectedValue(new ApiError('로그인이 필요합니다.', 401))
})
afterEach(cleanup)
function renderAuth(path = '/works/demo/manuscript') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route element={<GuestRoute />}>
            <Route path="/login" element={<AuthPage />} />
            <Route path="/signup" element={<AuthPage signup />} />
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route path="/works/demo/manuscript" element={<h1>보호된 원고</h1>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}
async function submitLogin() {
  await screen.findByRole('heading', { name: '다시 만나 반가워요' })
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: user.email } })
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'Password1!' } })
  fireEvent.submit(screen.getByRole('button', { name: /로그인/ }).closest('form')!)
}
test('서버 로그인 후 원래 보호 주소로 돌아간다', async () => {
  vi.mocked(authApi.login).mockResolvedValue(user)
  renderAuth()
  await submitLogin()
  expect(await screen.findByRole('heading', { name: '보호된 원고' })).toBeTruthy()
  expect(authApi.login).toHaveBeenCalledWith(user.email, 'Password1!')
})
test('새로고침 시 서버 세션으로 로그인 상태를 복원한다', async () => {
  vi.mocked(authApi.me).mockResolvedValue(user)
  renderAuth()
  expect(await screen.findByRole('heading', { name: '보호된 원고' })).toBeTruthy()
})
test('서버 연결 실패는 미로그인으로 오인하지 않고 재시도를 제공한다', async () => {
  vi.mocked(authApi.me).mockRejectedValueOnce(new Error('offline'))
  renderAuth()
  expect((await screen.findByRole('alert')).textContent).toContain('서버 연결')
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  expect(await screen.findByRole('heading', { name: '다시 만나 반가워요' })).toBeTruthy()
})
test('로그인 실패 후 이메일 입력을 보존한다', async () => {
  vi.mocked(authApi.login).mockRejectedValue(
    new ApiError('이메일 또는 비밀번호를 확인해 주세요.', 401),
  )
  renderAuth()
  await submitLogin()
  expect((await screen.findByRole('alert')).textContent).toContain('비밀번호')
  expect((screen.getByLabelText('이메일') as HTMLInputElement).value).toBe(user.email)
})
test('회원가입 비밀번호 불일치 시 API를 호출하지 않는다', async () => {
  renderAuth('/signup')
  await screen.findByLabelText('필명')
  fireEvent.change(screen.getByLabelText('비밀번호', { exact: true }), {
    target: { value: 'Password1!' },
  })
  fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'different' } })
  fireEvent.submit(screen.getByRole('button', { name: '회원가입하고 시작하기' }).closest('form')!)
  expect((await screen.findByRole('alert')).textContent).toContain('일치하지 않습니다')
  expect(authApi.signup).not.toHaveBeenCalled()
})
test('세션 만료 응답을 받으면 로그인 화면으로 이동한다', async () => {
  vi.mocked(authApi.me).mockResolvedValue(user)
  renderAuth()
  await screen.findByRole('heading', { name: '보호된 원고' })
  window.dispatchEvent(new Event('auth-expired'))
  expect(await screen.findByRole('heading', { name: '다시 만나 반가워요' })).toBeTruthy()
})

async function fillSignup() {
  renderAuth('/signup')
  await screen.findByLabelText('필명')
  fireEvent.change(screen.getByLabelText('필명'), { target: { value: '작가' } })
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: user.email } })
  fireEvent.change(screen.getByLabelText('비밀번호', { exact: true }), {
    target: { value: 'Password1!' },
  })
  fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'Password1!' } })
}

test('이메일 중복 확인을 완료하기 전에는 가입하지 않는다', async () => {
  await fillSignup()
  fireEvent.submit(screen.getByRole('button', { name: '회원가입하고 시작하기' }).closest('form')!)
  expect((await screen.findByRole('alert')).textContent).toContain('이메일 중복 확인')
  expect(authApi.signup).not.toHaveBeenCalled()
})

test('사용 가능한 이메일 확인 후 가입하며 비밀번호 충족 여부를 표시한다', async () => {
  vi.mocked(authApi.checkEmail).mockResolvedValue(true)
  vi.mocked(authApi.signup).mockResolvedValue(user)
  await fillSignup()
  expect(screen.getByRole('list', { name: '비밀번호 조건' }).textContent).not.toContain('미충족')
  expect(screen.getByText('비밀번호가 일치합니다.')).toBeTruthy()
  expect(screen.queryByRole('checkbox')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: '중복 확인' }))
  await screen.findByText('사용할 수 있는 이메일입니다.')
  fireEvent.submit(screen.getByRole('button', { name: '회원가입하고 시작하기' }).closest('form')!)
  await screen.findByText('가입되었습니다. 이메일로 로그인해 주세요.')
  expect((screen.getByLabelText('비밀번호', { exact: true }) as HTMLInputElement).value).toBe('')
  expect(authApi.signup).toHaveBeenCalledWith('작가', user.email, 'Password1!')
})

test('입력 변경 이후 도착한 이메일 확인 응답을 무시한다', async () => {
  let resolveCheck: (available: boolean) => void = () => {}
  vi.mocked(authApi.checkEmail).mockReturnValue(
    new Promise((resolve) => {
      resolveCheck = resolve
    }),
  )
  await fillSignup()
  fireEvent.click(screen.getByRole('button', { name: '중복 확인' }))
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'other@example.com' } })
  await act(async () => {
    resolveCheck(true)
  })
  expect(screen.queryByText('사용할 수 있는 이메일입니다.')).toBeNull()
  fireEvent.submit(screen.getByRole('button', { name: '회원가입하고 시작하기' }).closest('form')!)
  expect((await screen.findByRole('alert')).textContent).toContain('이메일 중복 확인')
})

test('중복 이메일과 조회 실패를 안내하고 재시도할 수 있다', async () => {
  vi.mocked(authApi.checkEmail)
    .mockResolvedValueOnce(false)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(true)
  await fillSignup()
  fireEvent.click(screen.getByRole('button', { name: '중복 확인' }))
  await screen.findByText('이미 가입된 이메일입니다.')
  fireEvent.click(screen.getByRole('button', { name: '중복 확인' }))
  await screen.findByText('이메일을 확인하지 못했습니다. 다시 시도해 주세요.')
  fireEvent.click(screen.getByRole('button', { name: '중복 확인' }))
  await screen.findByText('사용할 수 있는 이메일입니다.')
  fireEvent.change(screen.getByLabelText('비밀번호', { exact: true }), {
    target: { value: 'password' },
  })
  expect(screen.getByRole('list', { name: '비밀번호 조건' }).textContent).toContain('미충족')
})
