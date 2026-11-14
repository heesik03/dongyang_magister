// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { AiWritingAssistant } from '../src/features/manuscript/AiWritingAssistant'
import { aiWritingClient } from '../src/features/manuscript/aiWritingClient'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const props = {
  workId: '1',
  content: '문 앞에 섰다. 열어 줘. 잠시 후 대답이 왔다.',
  confirmedSettings: [],
  getSelection: () => ({ start: 9, end: 14 }),
}

test('대사 다듬기는 선택한 구간만 바꾸고 주변 문장을 보존한다', async () => {
  const assist = vi.spyOn(aiWritingClient, 'assist').mockResolvedValue('문을 열어.')
  const apply = vi.fn(() => true)
  render(<AiWritingAssistant {...props} onApply={apply} />)
  fireEvent.click(screen.getByRole('button', { name: '대사 다듬기' }))
  expect(assist).toHaveBeenCalledWith(
    '1',
    {
      action: 'DIALOGUE',
      excerpt: props.content.slice(9, 14),
      before: props.content.slice(0, 9),
      after: props.content.slice(14),
      settingIds: [],
    },
    expect.any(AbortSignal),
  )
  fireEvent.click(await screen.findByRole('button', { name: '선택 구간에 적용' }))
  expect(apply).toHaveBeenCalledWith(
    props.content.slice(0, 9) + '문을 열어.' + props.content.slice(14),
  )
})

test('회차가 전환되면 이전 요청의 신호를 취소하고 결과를 숨긴다', async () => {
  let complete: (text: string) => void = () => {}
  const assist = vi.spyOn(aiWritingClient, 'assist').mockImplementation(
    () =>
      new Promise<string>((resolve) => {
        complete = resolve
      }),
  )
  const apply = vi.fn(() => true)
  const view = render(<AiWritingAssistant key="first" {...props} onApply={apply} />)
  fireEvent.click(screen.getByRole('button', { name: '다음 문장 이어쓰기' }))
  view.rerender(<AiWritingAssistant key="second" {...props} content="다른 회차" onApply={apply} />)
  expect(assist.mock.calls[0][2].aborted).toBe(true)
  complete('이전 회차의 제안')
  await Promise.resolve()
  expect(screen.queryByText('이전 회차의 제안')).toBeNull()
  expect(apply).not.toHaveBeenCalled()
})
