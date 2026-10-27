// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { WorksProvider } from '../src/features/works/WorksProvider'
import { MemoryRouter } from 'react-router-dom'
import { DeleteWorkDialog } from '../src/features/works/DeleteWorkDialog'
import { workClient } from '../src/features/works/workClient'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { ApiError } from '../src/shared/httpClient'
import type { Work } from '../src/features/works/workTypes'
vi.mock('../src/features/works/workClient', () => ({
  workClient: { get: vi.fn(), delete: vi.fn(), list: vi.fn() },
}))
const work: Work = {
  id: '1',
  title: '검증 작품',
  genre: '',
  era: '',
  description: '',
  source: 'server',
  version: 0,
  updatedAt: '',
  chapters: [],
  settings: [],
  analyses: [],
  events: [],
}
beforeEach(() => {
  vi.resetAllMocks()
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  vi.mocked(workClient.get).mockResolvedValue(work)
  useWorksStore.getState().reset()
  useWorksStore.setState({ works: [work] })
})
afterEach(cleanup)
test('제목 확인과 최신 버전 재조회 없이 충돌한 삭제를 반복하지 않는다', async () => {
  vi.mocked(workClient.delete)
    .mockRejectedValueOnce(new ApiError('충돌', 409))
    .mockResolvedValueOnce(undefined)
  render(
    <MemoryRouter>
      <DeleteWorkDialog work={work} onClose={() => {}} />
    </MemoryRouter>,
  )
  await screen.findByText('검증 작품')
  expect(document.activeElement).toBe(screen.getByLabelText('삭제할 작품 제목을 입력해 주세요'))
  const button = screen.getByRole('button', { name: '작품 영구 삭제', exact: true })
  expect(button).toHaveProperty('disabled', true)
  fireEvent.change(screen.getByLabelText('삭제할 작품 제목을 입력해 주세요'), {
    target: { value: '검증 작품' },
  })
  fireEvent.click(button)
  await screen.findByText(/다른 변경이 저장되어/)
  expect(workClient.delete).toHaveBeenCalledOnce()
  expect(button).toHaveProperty('disabled', true)
  expect(useWorksStore.getState().works).toEqual([work])
  vi.mocked(workClient.get).mockResolvedValue({ ...work, version: 3 })
  fireEvent.click(screen.getByRole('button', { name: '최신 작품 확인' }))
  await screen.findByText(/버전 3/)
  fireEvent.click(button)
  await waitFor(() => expect(workClient.delete).toHaveBeenLastCalledWith('1', 3))
})

test('StrictMode의 이전 정리 작업이 새 작품 세션을 지우지 않는다', async () => {
  vi.mocked(workClient.list).mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  })
  render(
    <StrictMode>
      <WorksProvider email="strict@example.com">
        <p>준비 완료</p>
      </WorksProvider>
    </StrictMode>,
  )
  await waitFor(() => expect(screen.getByText('준비 완료')).toBeTruthy())
  expect(useWorksStore.getState().account).toBe('strict@example.com')
})
