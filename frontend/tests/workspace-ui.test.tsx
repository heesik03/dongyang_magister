// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { Dialog } from '../src/shared/Dialog'
import { WorkPage } from '../src/features/works/WorkPage'
import { SettingsPanel } from '../src/features/settings/SettingsPanel'
import { useWorksStore } from '../src/features/works/useWorksStore'
import { settingsChanged } from '../src/features/analysis/analysisState'
import type { Work, Analysis } from '../src/features/works/workTypes'
const base = useWorksStore.getState()
const setting = {
  id: '1',
  title: '확정 능력',
  kind: '인물' as const,
  status: '확정' as const,
  content: '마법 불가',
  version: 0,
}
const work: Work = {
  id: '1',
  source: 'server',
  title: '검증 작품',
  genre: '판타지',
  era: '',
  description: '',
  updatedAt: '2026-10-25T00:00:00Z',
  version: 0,
  workspaceVersion: 0,
  settings: [setting],
  chapters: [],
  analyses: [],
  events: [],
}
beforeEach(() => {
  base.reset()
  useWorksStore.setState({
    ...base,
    works: [work],
    account: 'qa@example.com',
    loadedIds: ['1'],
    loadWork: vi.fn().mockResolvedValue(undefined),
  })
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})
afterEach(cleanup)
test('입력창이 없는 모달도 포커스를 받고 제목 ID가 중복되지 않는다', () => {
  render(
    <>
      <Dialog title="첫 모달" onClose={() => {}}>
        <a href="/">작품 보관함</a>
      </Dialog>
      <Dialog title="둘째 모달" onClose={() => {}}>
        설명
      </Dialog>
    </>,
  )
  const dialogs = screen.getAllByRole('dialog')
  expect(dialogs[0].getAttribute('aria-labelledby')).not.toBe(
    dialogs[1].getAttribute('aria-labelledby'),
  )
  expect(dialogs[1].contains(document.activeElement)).toBe(true)
})
test('작품 상세 로딩 완료 전에는 원고 패널을 마운트하지 않는다', () => {
  useWorksStore.setState({ loadedIds: [], works: [{ ...work, settings: [] }] })
  render(
    <MemoryRouter initialEntries={['/works/1/manuscript']}>
      <Routes>
        <Route path="/works/:workId/:tab" element={<WorkPage />} />
      </Routes>
    </MemoryRouter>,
  )
  expect(screen.getByRole('status').textContent).toContain('작품을 불러오고')
  expect(screen.queryByRole('button', { name: '분석 설정' })).toBeNull()
  act(() => useWorksStore.setState({ works: [work], loadedIds: ['1'] }))
  expect(screen.getByRole('heading', { name: '검증 작품' })).toBeTruthy()
})
test('설정 저장 후 편집 유형이 바뀌어도 현재 필터로 한 번만 재조회한다', async () => {
  const load = vi.fn(async (id: string, kind = '전체') => {
    useWorksStore.setState({
      settingView: {
        workId: id,
        kind,
        ids: ['1'],
        page: 0,
        totalPages: 1,
        loading: false,
        error: '',
      },
    })
  })
  const save = vi.fn().mockResolvedValue({ ...setting, kind: '세계관' })
  useWorksStore.setState({ loadSettings: load, saveSetting: save })
  render(<SettingsPanel work={work} />)
  await screen.findByRole('button', { name: '설정 수정' })
  fireEvent.click(screen.getByRole('button', { name: '인물', exact: true }))
  fireEvent.click(screen.getByRole('button', { name: '설정 수정' }))
  fireEvent.change(screen.getByLabelText('유형'), { target: { value: '세계관' } })
  load.mockClear()
  fireEvent.submit(screen.getByRole('button', { name: '설정 저장' }).closest('form')!)
  await vi.waitFor(() => expect(save).toHaveBeenCalledTimes(1))
  await vi.waitFor(() => expect(load).toHaveBeenCalledTimes(1))
  expect(load).toHaveBeenLastCalledWith('1', '인물')
})
test('근거 비교는 설정 정렬·JSON 속성 순서에 영향을 받지 않는다', () => {
  const analysis: Analysis = {
    chapterId: 'c',
    revision: 1,
    source: '내용',
    settingIds: ['1'],
    previousIds: [],
    reviews: [],
    createdAt: '2026-10-25T00:00:00Z',
    settingsSnapshot: JSON.stringify([
      {
        version: 0,
        status: '확정',
        kind: '인물',
        content: '마법 불가',
        title: '확정 능력',
        id: '1',
      },
    ]),
  }
  expect(settingsChanged(analysis, [setting])).toBe(false)
  expect(settingsChanged(analysis, [{ ...setting, content: '마법 가능' }])).toBe(true)
})
