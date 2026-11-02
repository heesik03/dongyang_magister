export type MarkdownFormat =
  'h1' | 'h2' | 'h3' | 'h4' | 'bold' | 'italic' | 'strike' | 'quote' | 'bullet' | 'number'

export function formatMarkdown(text: string, start: number, end: number, format: MarkdownFormat) {
  const selected = text.slice(start, end)
  if (format === 'bold' || format === 'italic' || format === 'strike') {
    const marker = format === 'bold' ? '**' : format === 'italic' ? '*' : '~~'
    const content = selected || '내용'
    return {
      value: text.slice(0, start) + marker + content + marker + text.slice(end),
      start: start + marker.length,
      end: start + marker.length + content.length,
    }
  }
  const lineStart = start === 0 ? 0 : text.lastIndexOf('\n', start - 1) + 1
  const nextLine = text.indexOf('\n', end)
  const lineEnd = nextLine === -1 ? text.length : nextLine
  const lines = text.slice(lineStart, lineEnd).split('\n')
  const replacement = lines
    .map((line, index) => {
      const prefix = format.startsWith('h')
        ? '#'.repeat(Number(format.slice(1))) + ' '
        : format === 'quote'
          ? '> '
          : format === 'bullet'
            ? '- '
            : index + 1 + '. '
      return prefix + line.replace(/^(#{1,6} |[>] |[-*] |[0-9]+[.] )/, '')
    })
    .join('\n')
  return {
    value: text.slice(0, lineStart) + replacement + text.slice(lineEnd),
    start: lineStart,
    end: lineStart + replacement.length,
  }
}
