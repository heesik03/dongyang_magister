import type { InputSnapshot } from './analysisClient'

export function AnalysisMaterials({ input }: { input: InputSnapshot }) {
  return (
    <div className="stack" style={{ overflowWrap: 'anywhere' }}>
      <details>
        <summary>
          검토 원고 · {input.chapter.title} · 버전 {input.chapter.revision} ·{' '}
          {input.chapter.content.length.toLocaleString()}자
        </summary>
        <p style={{ whiteSpace: 'pre-wrap' }}>{input.chapter.content}</p>
      </details>
      <strong>확정 설정 {input.settings.length}개</strong>
      {input.settings.map((setting) => (
        <details key={setting.id}>
          <summary>
            {setting.title} · 버전 {setting.version}
          </summary>
          <p style={{ whiteSpace: 'pre-wrap' }}>{setting.content}</p>
        </details>
      ))}
      <strong>이전 회차 {input.previous.length}개</strong>
      {input.previous.map((chapter) => (
        <details key={chapter.id}>
          <summary>
            {chapter.title} · 버전 {chapter.revision}
          </summary>
          <p style={{ whiteSpace: 'pre-wrap' }}>{chapter.content}</p>
        </details>
      ))}
      <strong>참고할 저장 판단 {input.decisions.length}개</strong>
      {input.decisions.map((decision) => (
        <details key={decision.id}>
          <summary>
            {decision.decision} · {decision.quote}
          </summary>
          <p>관련 근거: {decision.evidence}</p>
          <p>검토 이유: {decision.reason}</p>
          <p>작가 사유: {decision.note || '없음'}</p>
        </details>
      ))}
      <p className="small muted">저장 판단은 현재 회차의 해당 원문·근거에 한해 참고합니다.</p>
    </div>
  )
}
