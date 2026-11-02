// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { PasswordInput } from '../src/features/auth/PasswordInput'

afterEach(cleanup)

test('보기·숨기기는 입력 값과 폼 데이터를 유지하고 폼을 제출하지 않는다', () => {
  const submit = vi.fn((event: React.FormEvent) => event.preventDefault())
  render(
    <form onSubmit={submit} data-testid="form">
      <PasswordInput label="비밀번호" name="password" defaultValue="Sample123!" />
    </form>,
  )
  const input = screen.getByLabelText('비밀번호') as HTMLInputElement
  expect(input.type).toBe('password')
  fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))
  expect(input.type).toBe('text')
  expect(screen.getByRole('button', { name: '비밀번호 숨기기' }).getAttribute('aria-pressed')).toBe(
    'true',
  )
  expect(new FormData(screen.getByTestId('form') as HTMLFormElement).get('password')).toBe(
    'Sample123!',
  )
  fireEvent.click(screen.getByRole('button', { name: '비밀번호 숨기기' }))
  expect(input.type).toBe('password')
  expect(submit).not.toHaveBeenCalled()
})

test('비밀번호와 확인 입력의 표시 상태는 독립적이다', () => {
  render(
    <>
      <PasswordInput label="비밀번호" />
      <PasswordInput label="비밀번호 확인" />
    </>,
  )
  fireEvent.click(screen.getByRole('button', { name: '비밀번호 확인 보기' }))
  expect((screen.getByLabelText('비밀번호') as HTMLInputElement).type).toBe('password')
  expect((screen.getByLabelText('비밀번호 확인') as HTMLInputElement).type).toBe('text')
})

test('Caps Lock 안내를 입력 아래에 연결하고 해제·포커스 이탈 시 지운다', () => {
  render(<PasswordInput label="비밀번호" aria-describedby="password-help" />)
  const input = screen.getByLabelText('비밀번호')
  const capsOn = () => {
    const event = new KeyboardEvent('keydown', { bubbles: true, key: 'A' })
    Object.defineProperty(event, 'getModifierState', { value: (key: string) => key === 'CapsLock' })
    fireEvent(input, event)
  }
  capsOn()
  const notice = screen.getByRole('status')
  expect(notice.textContent).toBe('Caps Lock이 켜져 있습니다.')
  expect(input.getAttribute('aria-describedby')).toBe('password-help ' + notice.id)
  fireEvent.keyUp(input, { key: 'CapsLock' })
  expect(screen.queryByRole('status')).toBeNull()
  capsOn()
  fireEvent.blur(input)
  expect(screen.queryByRole('status')).toBeNull()
  expect(input.getAttribute('aria-describedby')).toBe('password-help')
})
