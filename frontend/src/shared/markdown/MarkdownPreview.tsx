import { lazy, Suspense } from 'react'
const MarkdownContent = lazy(() => import('./MarkdownContent'))
export function MarkdownPreview({ content }: { content: string }) {
  return (
    <Suspense fallback={<p role="status">미리보기를 준비하고 있습니다.</p>}>
      <MarkdownContent content={content} />
    </Suspense>
  )
}
