// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ErrorPage } from '../src/features/errors/ErrorPage'
afterEach(cleanup)
test('404는 보관함 복귀와 이전 페이지 이동을 제공한다', () => {
  render(
    <MemoryRouter>
      <ErrorPage />
    </MemoryRouter>,
  )
  expect(screen.getByRole('heading', { name: '이 페이지는 찾을 수 없어요' })).toBeTruthy()
  expect(screen.getByRole('link', { name: '작품 보관함으로' }).getAttribute('href')).toBe('/')
  expect(screen.getByRole('button', { name: '이전 페이지로' })).toBeTruthy()
})
test('연결 실패에서 실제 재요청 콜백을 실행한다', () => {
  const retry = vi.fn()
  render(
    <MemoryRouter>
      <ErrorPage kind="connection" onRetry={retry} />
    </MemoryRouter>,
  )
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  expect(retry).toHaveBeenCalledOnce()
})
