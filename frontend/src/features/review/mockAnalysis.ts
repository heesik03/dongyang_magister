import type { Analysis, Chapter, Review, Work } from '../works/workTypes'

export interface AnalysisOptions {
  settingIds: string[]
  previousIds: string[]
  consistency: boolean
  motivation: boolean
}

// 시연 규칙은 샘플의 정확한 문장과 작가가 선택한 확정 설정만 비교한다.
// 일치하는 규칙이 없다는 결과를 실제 AI 검토의 통과 판정으로 사용하지 않는다.
export function runMockAnalysis(work: Work, chapter: Chapter, options: AnalysisOptions): Analysis {
  const settings = work.settings.filter(
    (setting) => setting.status === '확정' && options.settingIds.includes(setting.id),
  )
  const reviews: Review[] = []
  const previous = work.analyses.find((analysis) => analysis.chapterId === chapter.id)
  function add(
    type: Review['type'],
    quote: string,
    phrase: string,
    reason: string,
    suggestion: string,
  ) {
    const setting = settings.find((item) => item.content.includes(phrase))
    if (!setting || !chapter.content.includes(quote)) return
    const id = `${chapter.id}:${type}`
    const saved = previous?.reviews.find(
      (item) =>
        item.id === id &&
        item.quote === quote &&
        item.evidence === setting.content &&
        item.settingId === setting.id,
    )
    reviews.push({
      id,
      type,
      quote,
      settingId: setting.id,
      evidence: setting.content,
      reason,
      suggestion,
      decision: saved?.decision || '미검토',
      note: saved?.note || '',
    })
  }
  if (options.consistency)
    add(
      '설정 충돌 후보',
      '그는 금지된 마법을 능숙하게 시전했다.',
      '마법을 사용할 수 없다',
      '확정 설정은 마법을 사용할 수 없다고 설명하지만, 원문에는 능숙하게 시전하는 장면이 있습니다. 이 시연 규칙에서는 능력 변화의 계기를 판단할 수 없습니다.',
      '능력을 얻은 계기를 보완하거나, 의도한 반전이라면 적용 회차와 이유를 기록해 주세요.',
    )
  if (options.motivation)
    add(
      '설명 보완',
      '그는 망설임 없이 경비병을 공격했다.',
      '누구도 해치지 않겠다는 신념',
      '누구도 해치지 않겠다는 신념과 다른 행동입니다. 인물의 변화 자체가 오류는 아니며, 행동의 동기가 설명되어 있는지 작가의 확인이 필요합니다.',
      '인물의 목표가 바뀐 이유나 공격할 수밖에 없는 상황을 보완해 주세요.',
    )
  return {
    chapterId: chapter.id,
    revision: chapter.revision,
    source: chapter.content,
    settingIds: settings.map((item) => item.id),
    previousIds: options.previousIds.filter((id) =>
      work.chapters.some((item) => item.id === id && item.id !== chapter.id),
    ),
    reviews,
    createdAt: new Date().toISOString(),
    settingsSnapshot: JSON.stringify(settings),
  }
}
