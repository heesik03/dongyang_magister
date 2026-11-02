import type { InputSnapshot } from './analysisClient'

export function AnalysisMaterials({ input }: { input: InputSnapshot }) {
  return (
    <div className="analysis-materials">
      <section className="material-section" aria-label="검토 원고">
        <h3>검토 원고</h3>
        <details>
          <summary>
            <span className="material-title">{input.chapter.title}</span>
            <span className="material-meta">
              버전 {input.chapter.revision} · {input.chapter.content.length.toLocaleString()}자
            </span>
          </summary>
          <p className="material-source">{input.chapter.content}</p>
        </details>
      </section>
      <section className="material-section" aria-label="확정 설정">
        <h3>
          확정 설정 <span className="material-count">{input.settings.length}개</span>
        </h3>
        {input.settings.map((setting) => (
          <details key={setting.id}>
            <summary>
              <span className="material-title">{setting.title}</span>
              <span className="material-meta">버전 {setting.version}</span>
            </summary>
            <p className="material-source">{setting.content}</p>
          </details>
        ))}
      </section>
      <section className="material-section" aria-label="이전 회차">
        <h3>
          이전 회차 <span className="material-count">{input.previous.length}개</span>
        </h3>
        {input.previous.map((chapter) => (
          <details key={chapter.id}>
            <summary>
              <span className="material-title">{chapter.title}</span>
              <span className="material-meta">버전 {chapter.revision}</span>
            </summary>
            <p className="material-source">{chapter.content}</p>
          </details>
        ))}
      </section>
      <section className="material-section" aria-label="참고할 저장 판단">
        <h3>
          참고할 저장 판단{' '}
          <span className="material-count">{(input.decisionReferences ?? []).length}개</span>
        </h3>
        {(input.decisionReferences ?? []).map((reference) => (
          <details key={reference.scopeId}>
            <summary>
              <span className="material-title">{reference.quote}</span>
              <span className="material-meta">
                {reference.status} · 버전 {reference.version}
              </span>
            </summary>
            <dl className="material-facts">
              <dt>작가 사유</dt>
              <dd>{reference.reason || '없음'}</dd>
              <dt>적용 범위</dt>
              <dd>
                {reference.scopeType === 'SCENE'
                  ? '해당 회차의 특정 장면'
                  : `${reference.fromChapterTitle ?? '시작 회차'}부터 ${reference.toChapterTitle ?? '이후 회차'}까지`}
              </dd>
              <dt>적용 조건</dt>
              <dd>{reference.conditionText}</dd>
              <dt>관련 근거</dt>
              <dd>{reference.evidenceQuotes.join(' / ')}</dd>
            </dl>
          </details>
        ))}
      </section>
      {input.decisions.length > 0 && (
        <section className="material-section" aria-label="과거 분석의 기존 판단">
          <h3>
            과거 분석에 포함했던 기존 판단{' '}
            <span className="material-count">{input.decisions.length}개</span>
          </h3>
          {input.decisions.map((decision) => (
            <details key={decision.id}>
              <summary>
                <span className="material-title">{decision.quote}</span>
                <span className="material-meta">{decision.decision}</span>
              </summary>
              <dl className="material-facts">
                <dt>관련 근거</dt>
                <dd>{decision.evidence}</dd>
                <dt>검토 이유</dt>
                <dd>{decision.reason}</dd>
                <dt>작가 사유</dt>
                <dd>{decision.note || '없음'}</dd>
              </dl>
            </details>
          ))}
        </section>
      )}
      <p className="material-help">
        현재 선택한 근거가 모두 포함되고 유효한 판단만 전송합니다. 저장 판단은 표시한
        회차·장면·조건에 해당하는 관련 근거에만 참고합니다. 범위 밖의 충돌을 무시하지 않습니다.
      </p>
    </div>
  )
}
