// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { SceneBoardFlow } from '../src/features/scene/SceneBoardFlow'
import { sceneBoardClient } from '../src/features/scene/sceneBoardClient'
import { ApiError } from '../src/shared/httpClient'
import { useWorksStore } from '../src/features/works/useWorksStore'
import type { Work } from '../src/features/works/workTypes'

const chapter = { id: 'chapter-1', title: '등대', content: '등불이 깨졌다.', revision: 2 }
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

afterEach(() => {
  cleanup()
  useWorksStore.getState().reset()
  vi.restoreAllMocks()
})

test('저장 버전의 선택 구간을 확인하고 동의한 뒤에만 장면 분석을 접수한다', async () => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  useWorksStore.setState({ works: [work], flushWorkspace: vi.fn().mockResolvedValue(undefined) })
  const selection = { chapterId: chapter.id, revision: 2, start: 0, end: 3, settingIds: [] }
  const snapshot = { text: '등불이', chapterTitle: '등대', revision: 2, settings: [] }
  vi.spyOn(sceneBoardClient, 'list').mockResolvedValue([])
  const preview = vi
    .spyOn(sceneBoardClient, 'preview')
    .mockResolvedValue({ token: 'preview', snapshot, selection })
  vi.spyOn(sceneBoardClient, 'usage').mockResolvedValue({
    remaining: 1,
    resetAt: '2026-11-10T15:00:00Z',
  })
  const create = vi.spyOn(sceneBoardClient, 'create').mockResolvedValue({
    id: 'board',
    status: 'SUCCEEDED',
    snapshot,
    result: {
      facts: [{ description: '등불이 등장합니다.', quote: '등불이', start: 0, end: 3 }],
      inferences: [],
      gaps: [],
      options: [],
      references: [],
    },
  })
  const area = document.createElement('textarea')
  area.value = chapter.content
  area.setSelectionRange(0, 3)
  render(<SceneBoardFlow work={work} chapter={chapter} editor={{ current: area }} />)
  fireEvent.click(screen.getByRole('button', { name: 'AI 장면 보드' }))
  fireEvent.click(screen.getByRole('button', { name: '전송 자료 확인' }))
  const submit = await screen.findByRole('button', { name: '동의하고 분석 시작' })
  expect(preview).toHaveBeenCalledWith(work.id, selection)
  expect(submit.hasAttribute('disabled')).toBe(true)
  expect(create).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('checkbox'))
  fireEvent.click(submit)
  expect(await screen.findByText('등불이 등장합니다.')).toBeTruthy()
  expect(create).toHaveBeenCalledWith(work.id, selection, 'preview', expect.any(String))
})

test('409 충돌 시 미리보기를 닫고 전송 자료를 다시 확인할 수 있다', async () => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  useWorksStore.setState({ works: [work], flushWorkspace: vi.fn().mockResolvedValue(undefined) })
  const selection = { chapterId: chapter.id, revision: 2, start: 0, end: 3, settingIds: [] }
  const snapshot = { text: '등불이', chapterTitle: '등대', revision: 2, settings: [] }
  vi.spyOn(sceneBoardClient, 'list').mockResolvedValue([])
  vi.spyOn(sceneBoardClient, 'preview').mockResolvedValue({ token: 'preview', snapshot, selection })
  vi.spyOn(sceneBoardClient, 'usage').mockResolvedValue({
    remaining: 1,
    resetAt: '2026-11-10T15:00:00Z',
  })
  const create = vi
    .spyOn(sceneBoardClient, 'create')
    .mockRejectedValueOnce(new ApiError('입력 변경', 409, 'PREVIEW_CHANGED', []))
    .mockResolvedValueOnce({
      id: 'board-1',
      status: 'SUCCEEDED',
      snapshot,
      result: {
        facts: [],
        inferences: [],
        gaps: [],
        options: [],
        references: [],
      },
    })
  const area = document.createElement('textarea')
  area.value = chapter.content
  area.setSelectionRange(0, 3)
  render(<SceneBoardFlow work={work} chapter={chapter} editor={{ current: area }} />)
  fireEvent.click(screen.getByRole('button', { name: 'AI 장면 보드' }))
  fireEvent.click(screen.getByRole('button', { name: '전송 자료 확인' }))
  fireEvent.click(await screen.findByRole('checkbox'))
  fireEvent.click(screen.getByRole('button', { name: '동의하고 분석 시작' }))

  expect(await screen.findByRole('alert')).toBeTruthy()
  expect(screen.getByRole('button', { name: '전송 자료 확인' })).toBeTruthy()

  fireEvent.click(screen.getByRole('button', { name: '전송 자료 확인' }))
  fireEvent.click(await screen.findByRole('checkbox'))
  fireEvent.click(screen.getByRole('button', { name: '동의하고 분석 시작' }))
  expect(create).toHaveBeenCalledTimes(2)

  expect(await screen.findByRole('button', { name: '소설 레퍼런스' })).toBeTruthy()
  expect(screen.queryByRole('button', { name: '콘셉트 이미지' })).toBeNull()
})
