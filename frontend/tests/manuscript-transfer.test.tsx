// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ManuscriptTransfer } from '../src/features/manuscript/ManuscriptTransfer'
import { manuscriptTransferClient } from '../src/features/manuscript/manuscriptTransferClient'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { ApiError } from '../src/shared/httpClient'
import type { Work } from '../src/features/works/workTypes'
const base = useWorksStore.getState()
const chapter = {
  id: 'c',
  title: '1화',
  content: '기존 원고',
  revision: 1,
  version: 0,
  chapterNo: 1,
}
const work: Work = {
  id: '1',
  source: 'server',
  title: '작품',
  genre: '판타지',
  era: '',
  description: '',
  updatedAt: '',
  chapters: [chapter],
  settings: [],
  analyses: [],
  events: [],
}
const saved = {
  ...chapter,
  title: '가져온 회차',
  content: '새 원고',
  version: 1,
  revision: 2,
  workVersion: 2,
  updatedAt: '2026-11-02T00:00:00Z',
}
beforeEach(() => {
  useWorksStore.setState({
    ...base,
    works: [work],
    account: 'test@example.com',
    flushWorkspace: vi.fn().mockResolvedValue(undefined),
    acceptImportedChapter: vi.fn(),
  })
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  vi.spyOn(manuscriptTransferClient, 'preview').mockResolvedValue({
    workTitle: '',
    genre: '',
    chapterTitle: '가져온 회차',
    content: '새 원고',
    format: 'md',
  })
})
afterEach(() => {
  cleanup()
  base.reset()
  vi.restoreAllMocks()
})
async function openPreview() {
  fireEvent.click(screen.getByText('파일 메뉴'))
  fireEvent.click(screen.getByRole('button', { name: '원고 가져오기' }))
  fireEvent.change(screen.getByLabelText('원고 파일'), {
    target: { files: [new File(['새 원고'], '원고.md')] },
  })
  await screen.findByLabelText('회차 제목')
}
test('미리보기만으로 저장하지 않고 확인한 파일을 새 회차로 가져온다', async () => {
  const imported = vi.fn(),
    save = vi.spyOn(manuscriptTransferClient, 'importFile').mockResolvedValue(saved)
  render(<ManuscriptTransfer work={work} chapter={chapter} onImported={imported} />)
  await openPreview()
  expect(save).not.toHaveBeenCalled()
  expect((screen.getByLabelText('가져온 본문 · 4자') as HTMLTextAreaElement).value).toBe('새 원고')
  fireEvent.click(screen.getByRole('button', { name: '새 회차로 가져오기' }))
  await waitFor(() => expect(imported).toHaveBeenCalledWith('c'))
  expect(save.mock.calls[0][2]).toMatchObject({ title: '가져온 회차', version: undefined })
  expect(useWorksStore.getState().acceptImportedChapter).toHaveBeenCalledWith('1', saved)
})
test('현재 회차 교체는 별도 확인이 필요하고 충돌 시 파일과 제목을 보존한다', async () => {
  const save = vi
    .spyOn(manuscriptTransferClient, 'importFile')
    .mockRejectedValue(new ApiError('충돌', 409))
  render(<ManuscriptTransfer work={work} chapter={chapter} onImported={vi.fn()} />)
  await openPreview()
  fireEvent.change(screen.getByLabelText('저장 방식'), { target: { value: 'replace' } })
  expect(
    (screen.getByRole('button', { name: '현재 회차 교체' }) as HTMLButtonElement).disabled,
  ).toBe(true)
  fireEvent.click(screen.getByRole('checkbox'))
  fireEvent.click(screen.getByRole('button', { name: '현재 회차 교체' }))
  await screen.findByRole('alert')
  expect(save.mock.calls[0][2]).toMatchObject({ id: 'c', version: 0 })
  expect((screen.getByLabelText('회차 제목') as HTMLInputElement).value).toBe('가져온 회차')
  expect(useWorksStore.getState().conflicts).toContain('1')
})
test('응답 유실 재시도는 같은 회차 ID와 작업 ID를 보낸다', async () => {
  const save = vi
    .spyOn(manuscriptTransferClient, 'importFile')
    .mockRejectedValueOnce(new Error('연결 끊김'))
    .mockResolvedValueOnce(saved)
  render(<ManuscriptTransfer work={work} chapter={chapter} onImported={vi.fn()} />)
  await openPreview()
  fireEvent.click(screen.getByRole('button', { name: '새 회차로 가져오기' }))
  await screen.findByText('연결 끊김')
  fireEvent.click(screen.getByRole('button', { name: '새 회차로 가져오기' }))
  await waitFor(() => expect(save).toHaveBeenCalledTimes(2))
  expect(save.mock.calls[0][2]).toEqual(save.mock.calls[1][2])
})
test('저장 실패로 남은 입력이 있으면 다운로드를 시작하지 않는다', async () => {
  const download = vi.spyOn(manuscriptTransferClient, 'exportFile')
  useWorksStore.setState({ dirtyIds: ['1'] })
  render(<ManuscriptTransfer work={work} chapter={chapter} onImported={vi.fn()} />)
  fireEvent.click(screen.getByText('파일 메뉴'))
  fireEvent.click(screen.getByRole('button', { name: '원고 내려받기' }))
  fireEvent.click(screen.getByRole('button', { name: '파일 내려받기' }))
  await screen.findByRole('alert')
  expect(download).not.toHaveBeenCalled()
})
test('가져온 회차 반영 시 다른 회차와 사건·판단 이력을 보존한다', () => {
  useWorksStore.setState({ works: [{ ...work, chapters: [] }] })
  base.acceptImportedChapter('1', saved)
  const current = useWorksStore.getState().works[0]
  expect(current.chapters).toEqual([saved])
  expect(current.events).toEqual(work.events)
  expect(current.analyses).toEqual(work.analyses)
  expect(current.version).toBe(2)
  expect(useWorksStore.getState().dirtyIds).toEqual([])
})
test('가져오기 응답보다 최신인 회차는 이전 버전으로 되돌리지 않는다', () => {
  const newer = { ...chapter, version: 3, content: '다른 탭의 최신 원고' }
  useWorksStore.setState({ works: [{ ...work, chapters: [newer] }] })
  base.acceptImportedChapter('1', saved)
  expect(useWorksStore.getState().works[0].chapters).toEqual([newer])
})
