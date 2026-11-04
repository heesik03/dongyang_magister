// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { MyPage } from '../src/features/account/MyPage'
import { accountClient } from '../src/features/account/accountClient'
import { useAuthStore } from '../src/features/auth/useAuthStore'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { ApiError } from '../src/shared/httpClient'

vi.mock('../src/features/account/accountClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/features/account/accountClient')>()
  return {
    ...original,
    accountClient: {
      getActivity: vi.fn(),
      updateProfile: vi.fn(),
      changePassword: vi.fn(),
      withdraw: vi.fn(),
    },
  }
})

const initialUser = {
  id: 1,
  name: '김연서',
  email: 'writer@example.com',
}

const sampleActivity = {
  workCount: 2,
  chapterCount: 5,
  unreviewedCount: 3,
  recentManuscript: {
    workId: '10',
    workTitle: '잿빛 항구의 연대기',
    chapterId: 'ch-3',
    chapterNo: 3,
    chapterTitle: '3화 안개 낀 부두',
    updatedAt: '2026-10-26T12:00:00Z',
  },
}

beforeEach(() => {
  vi.resetAllMocks()
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  localStorage.clear()
  sessionStorage.clear()
  useAuthStore.setState({ user: initialUser, loading: false, error: '' })
  useWorksStore.getState().reset()
  vi.mocked(accountClient.getActivity).mockResolvedValue(sampleActivity)
})

afterEach(cleanup)

function renderMyPage() {
  return render(
    <MemoryRouter initialEntries={['/mypage']}>
      <Routes>
        <Route path="/mypage" element={<MyPage />} />
        <Route path="/login" element={<h1>로그인 화면</h1>} />
        <Route path="/works/:workId/manuscript" element={<h1>원고 작업 화면</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

test('창작 공간 현황과 프로필을 표시하고 필명 수정 시 인증 상태를 즉시 갱신한다', async () => {
  vi.mocked(accountClient.updateProfile).mockResolvedValue({
    ...initialUser,
    name: '이도윤',
  })

  renderMyPage()

  expect(await screen.findByText(/잿빛 항구의 연대기 · 3화 안개 낀 부두/)).toBeTruthy()
  expect(screen.getByText('2')).toBeTruthy()
  expect(screen.getByText('5')).toBeTruthy()
  expect(screen.getByText('3')).toBeTruthy()

  const nameInput = screen.getByLabelText(/필명/)
  fireEvent.change(nameInput, { target: { value: '  이도윤  ' } })
  fireEvent.click(screen.getByRole('button', { name: '프로필 저장' }))

  await waitFor(() => expect(accountClient.updateProfile).toHaveBeenCalledWith('이도윤'))
  expect(await screen.findByText('필명이 저장되었습니다.')).toBeTruthy()
  expect(useAuthStore.getState().user?.name).toBe('이도윤')
  expect(screen.getByText('이도윤 작가님')).toBeTruthy()
})

test('비밀번호 변경 성공 시 작업 상태를 비우고 로그인 화면으로 이동한다', async () => {
  vi.mocked(accountClient.changePassword).mockResolvedValue(undefined)

  renderMyPage()
  await screen.findByRole('heading', { name: '마이페이지' })

  fireEvent.click(screen.getByRole('tab', { name: '비밀번호 변경' }))

  fireEvent.change(screen.getByLabelText('현재 비밀번호', { exact: true }), {
    target: { value: 'CurrentPass1!' },
  })
  fireEvent.change(screen.getByLabelText('새 비밀번호', { exact: true }), {
    target: { value: 'NewPassword2@' },
  })
  fireEvent.change(screen.getByLabelText('새 비밀번호 확인', { exact: true }), {
    target: { value: 'NewPassword2@' },
  })

  fireEvent.submit(screen.getByRole('button', { name: '비밀번호 변경' }).closest('form')!)

  await waitFor(() =>
    expect(accountClient.changePassword).toHaveBeenCalledWith({
      currentPassword: 'CurrentPass1!',
      newPassword: 'NewPassword2@',
      confirmPassword: 'NewPassword2@',
    }),
  )
  expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeTruthy()
  expect(useAuthStore.getState().user).toBeNull()
})

test('미저장 원고 자동 저장에 실패하면 비밀번호 변경으로 세션을 종료하지 않는다', async () => {
  const flushSpy = vi.fn().mockResolvedValue(false)
  useWorksStore.setState({
    dirtyIds: ['10'],
    flushWorkspace: flushSpy,
  })

  renderMyPage()
  await screen.findByRole('heading', { name: '마이페이지' })
  fireEvent.click(screen.getByRole('tab', { name: '비밀번호 변경' }))

  fireEvent.change(screen.getByLabelText('현재 비밀번호', { exact: true }), {
    target: { value: 'CurrentPass1!' },
  })
  fireEvent.change(screen.getByLabelText('새 비밀번호', { exact: true }), {
    target: { value: 'NewPassword2@' },
  })
  fireEvent.change(screen.getByLabelText('새 비밀번호 확인', { exact: true }), {
    target: { value: 'NewPassword2@' },
  })

  fireEvent.submit(screen.getByRole('button', { name: '비밀번호 변경' }).closest('form')!)

  expect((await screen.findByRole('alert')).textContent).toContain(
    '저장되지 않은 원고 입력이 있습니다',
  )
  expect(flushSpy).toHaveBeenCalledWith('10')
  expect(accountClient.changePassword).not.toHaveBeenCalled()
})

test('회원 탈퇴 모달에서 비밀번호와 동의 확인 후 브라우저 저장소와 세션을 정리한다', async () => {
  vi.mocked(accountClient.withdraw)
    .mockRejectedValueOnce(new ApiError('현재 비밀번호가 일치하지 않습니다.', 400))
    .mockResolvedValueOnce(undefined)

  localStorage.setItem('magister.drafts.v1:writer@example.com', '{"10":{}}')
  sessionStorage.setItem('magister.recovery:writer@example.com:10', '{"chapters":[]}')
  sessionStorage.setItem(
    'magister.decision-draft.v1:' + encodeURIComponent('writer@example.com') + ':10:cand-1',
    '{}',
  )

  renderMyPage()
  await screen.findByText(/잿빛 항구의 연대기 · 3화 안개 낀 부두/)

  fireEvent.click(screen.getByRole('button', { name: '회원 탈퇴...' }))

  expect(await screen.findByRole('heading', { name: '정말로 탈퇴하시겠습니까?' })).toBeTruthy()
  expect(screen.getByText(/보유 작품 2편 및 설정집 전체/)).toBeTruthy()
  expect(screen.getByText(/저장된 회차 원고 5건 및 사건 타임라인/)).toBeTruthy()

  const submitBtn = screen.getByRole('button', { name: '동의 후 탈퇴 가능' })
  expect(submitBtn).toHaveProperty('disabled', true)

  fireEvent.change(screen.getByLabelText('현재 비밀번호 확인', { exact: true }), {
    target: { value: 'WrongPass1!' },
  })
  fireEvent.click(
    screen.getByRole('checkbox', {
      name: /영구 삭제되며 복구할 수 없음을 확인했고 이에 동의합니다/,
    }),
  )

  const enabledBtn = screen.getByRole('button', { name: '회원 탈퇴' })
  expect(enabledBtn).toHaveProperty('disabled', false)

  fireEvent.click(enabledBtn)
  expect((await screen.findByRole('alert')).textContent).toContain(
    '현재 비밀번호가 일치하지 않습니다',
  )

  fireEvent.change(screen.getByLabelText('현재 비밀번호 확인', { exact: true }), {
    target: { value: 'CorrectPass1!' },
  })
  fireEvent.click(screen.getByRole('button', { name: '회원 탈퇴' }))

  await waitFor(() =>
    expect(accountClient.withdraw).toHaveBeenLastCalledWith({
      currentPassword: 'CorrectPass1!',
      agreed: true,
    }),
  )
  expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeTruthy()
  expect(localStorage.getItem('magister.drafts.v1:writer@example.com')).toBeNull()
  expect(sessionStorage.getItem('magister.recovery:writer@example.com:10')).toBeNull()
  expect(
    sessionStorage.getItem(
      'magister.decision-draft.v1:' + encodeURIComponent('writer@example.com') + ':10:cand-1',
    ),
  ).toBeNull()
})
