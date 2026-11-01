import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

// 원문은 그대로 보관하고, 미리보기에서만 안전한 텍스트 서식을 렌더링한다.
export default function MarkdownContent({ content }: { content: string }) {
  return (
    <div className="markdown-preview">
      <Markdown
        remarkPlugins={[[remarkGfm, { singleTilde: false }]]}
        skipHtml
        allowedElements={[
          'p',
          'br',
          'h1',
          'h2',
          'h3',
          'h4',
          'strong',
          'em',
          'del',
          'blockquote',
          'ul',
          'ol',
          'li',
          'code',
          'pre',
          'hr',
        ]}
        unwrapDisallowed
      >
        {content}
      </Markdown>
    </div>
  )
}
