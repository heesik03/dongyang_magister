// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '../src/shared/markdown/MarkdownContent'
import { MarkdownEditor } from '../src/shared/markdown/MarkdownEditor'
import { MarkdownPreview } from '../src/shared/markdown/MarkdownPreview'
import { formatMarkdown } from '../src/shared/markdown/markdownFormatting'
import { Dialog } from '../src/shared/Dialog'
afterEach(cleanup)
test('선택한 글자에 서식을 넣고 줄별 제목·목록을 적용한다', () => {
  expect(formatMarkdown('앞 문장 뒤', 2, 4, 'bold').value).toBe('앞 **문장** 뒤')
  expect(formatMarkdown('하나\n둘', 0, 4, 'number').value).toBe('1. 하나\n2. 둘')
  expect(formatMarkdown('제목', 0, 2, 'h4').value).toBe('#### 제목')
  expect(formatMarkdown('취소', 0, 2, 'strike').value).toBe('~~취소~~')
})
test('미리보기는 안전한 서식만 표시하고 HTML·이미지·링크·표를 만들지 않는다', async () => {
  const { container } = render(
    <MarkdownPreview
      content={
        '# 제목\n\n**강조** ~~취소~~\n\n<script>alert(1)</script>\n\n[링크](javascript:alert(1)) ![추적](https://example.com/pixel)\n\n| 표 | 값 |\n| --- | --- |\n| A | B |'
      }
    />,
  )
  await screen.findByRole('heading', { name: '제목' })
  expect(container.querySelector('strong')?.textContent).toBe('강조')
  expect(container.querySelector('del')?.textContent).toBe('취소')
  expect(container.querySelector('script, img, a, table')).toBeNull()
})
test('서식 수정과 미리보기 전환 뒤에도 폼에는 마크다운 원문을 제출한다', async () => {
  render(
    <form data-testid="form">
      <MarkdownEditor name="content" label="내용" defaultValue="원문" />
    </form>,
  )
  const input = screen.getByRole('textbox', { name: '내용' }) as HTMLTextAreaElement
  input.setSelectionRange(0, 2)
  fireEvent.click(screen.getByRole('button', { name: '굵게' }))
  await waitFor(() => expect(input.value).toBe('**원문**'))
  fireEvent.click(screen.getByRole('button', { name: '미리보기' }))
  await screen.findByText('원문')
  expect(new FormData(screen.getByTestId('form') as HTMLFormElement).get('content')).toBe(
    '**원문**',
  )
})
test('도구로 수정한 모달도 저장하지 않고 닫을 때 확인한다', async () => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  const close = vi.fn()
  render(
    <Dialog title="설정 등록" onClose={close}>
      <form>
        <MarkdownEditor name="content" label="내용" defaultValue="원문" />
      </form>
    </Dialog>,
  )
  fireEvent.click(screen.getByRole('button', { name: 'h1 제목' }))
  fireEvent.click(screen.getByRole('button', { name: '닫기', exact: true }))
  expect(close).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: '입력 버리고 닫기' })).toBeTruthy()
})
