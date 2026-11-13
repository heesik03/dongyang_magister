// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { CharacterImagePanel } from '../src/features/settings/CharacterImagePanel'
import { characterImageClient } from '../src/features/settings/characterImageClient'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

test('저장된 외형을 확인하고 전송 동의 후에만 생성하며 시안 상태를 표시한다', async () => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  const job = {
    id: 'job',
    status: 'QUEUED' as const,
    primary: false,
    settingVersion: 2,
    createdAt: '2026-11-13T00:00:00Z',
  }
  const gallery = { jobs: [], remaining: 3, capacity: 10, resetAt: '', enabled: true }
  vi.spyOn(characterImageClient, 'list')
    .mockResolvedValue({ ...gallery, jobs: [job] })
    .mockResolvedValueOnce(gallery)
  const preview = vi.spyOn(characterImageClient, 'preview').mockResolvedValue({
    prompt: '검은 머리와 수채화',
    settingVersion: 2,
    token: 'signed-preview',
  })
  const create = vi.spyOn(characterImageClient, 'create').mockResolvedValue(job)
  render(
    <CharacterImagePanel
      workId="1"
      setting={{
        id: '2',
        title: '린',
        kind: '인물',
        status: '확정',
        content: '외형: 검은 머리',
        version: 2,
      }}
    />,
  )
  await screen.findByText('아직 생성한 시안이 없습니다.')
  fireEvent.click(screen.getByRole('button', { name: '인물 이미지 만들기' }))
  const dialog = within(screen.getByRole('dialog', { name: '인물 이미지 만들기' }))
  expect(dialog.getByLabelText('외형 설명')).toHaveProperty('value', '검은 머리')
  fireEvent.change(dialog.getByLabelText('연출 (선택)'), { target: { value: '수채화' } })
  fireEvent.click(dialog.getByRole('button', { name: '전송 내용 확인' }))
  await dialog.findByText('검은 머리와 수채화')
  expect(preview).toHaveBeenCalledWith('1', '2', { appearance: '검은 머리', direction: '수채화' })
  const submit = dialog.getByRole('button', { name: '동의하고 생성' })
  expect(submit).toHaveProperty('disabled', true)
  expect(create).not.toHaveBeenCalled()
  fireEvent.click(dialog.getByRole('checkbox'))
  fireEvent.click(submit)
  await waitFor(() => expect(create).toHaveBeenCalledOnce())
  await screen.findByText('대기 중')
  expect(screen.getByText('최근 시안 1개').parentElement).toHaveProperty('open', true)
})
