import { test } from 'vitest'
import assert from 'node:assert/strict'
import { runMockAnalysis } from '../src/features/review/mockAnalysis.ts'
import { createSampleWorks } from './fixtures/sampleWorks.ts'
import { isWorks } from '../src/features/works/workStorage.ts'

const options = {
  settingIds: ['s-ian', 's-library'],
  previousIds: ['ch-1'],
  consistency: true,
  motivation: true,
}

test('샘플 원고의 실제 문장과 선택한 확정 설정을 근거로 두 후보를 생성한다', () => {
  const work = createSampleWorks()[0]
  const chapter = work.chapters[1]
  const result = runMockAnalysis(work, chapter, options)
  assert.equal(result.reviews.length, 2)
  assert.equal(result.source, chapter.content)
  for (const review of result.reviews) {
    assert.ok(chapter.content.includes(review.quote))
    assert.equal(
      review.evidence,
      work.settings.find((setting) => setting.id === review.settingId).content,
    )
  }
  assert.deepEqual(result.previousIds, ['ch-1'])
})

test('아이디어와 AI 후보는 확정 근거로 사용하지 않는다', () => {
  for (const status of ['아이디어', 'AI 후보']) {
    const work = createSampleWorks()[0]
    work.settings[0].status = status
    assert.equal(runMockAnalysis(work, work.chapters[1], options).reviews.length, 0)
  }
})

test('선택하지 않은 설정과 분석 항목은 검토에서 제외한다', () => {
  const work = createSampleWorks()[0]
  assert.equal(
    runMockAnalysis(work, work.chapters[1], { ...options, settingIds: ['s-library'] }).reviews
      .length,
    0,
  )
  assert.deepEqual(
    runMockAnalysis(work, work.chapters[1], { ...options, consistency: false }).reviews.map(
      (review) => review.type,
    ),
    ['설명 보완'],
  )
})

test('일반 원고에 샘플 검토 후보를 잘못 붙이지 않는다', () => {
  const work = createSampleWorks()[0]
  const chapter = { ...work.chapters[1], content: '두 사람은 조용히 책을 읽었다.' }
  assert.equal(runMockAnalysis(work, chapter, options).reviews.length, 0)
})

test('동일 원문·근거의 작가 판단을 다음 분석에 유지하고 원고와 확정 설정은 변경하지 않는다', () => {
  const work = createSampleWorks()[0]
  const result = runMockAnalysis(work, work.chapters[1], options)
  result.reviews[0].decision = '의도된 설정'
  result.reviews[0].note = '2화 봉인이 풀리는 장면에만 적용'
  work.analyses = [result]
  const before = JSON.stringify(work)
  const next = runMockAnalysis(work, work.chapters[1], options)
  assert.equal(next.reviews[0].decision, '의도된 설정')
  assert.equal(next.reviews[0].note, '2화 봉인이 풀리는 장면에만 적용')
  assert.equal(JSON.stringify(work), before)
})

test('근거가 바뀌거나 다른 회차인 경우 이전 의도를 자동 적용하지 않는다', () => {
  const work = createSampleWorks()[0]
  const result = runMockAnalysis(work, work.chapters[1], options)
  result.reviews[0].decision = '의도된 설정'
  work.analyses = [result]
  work.settings[0].content += ' 예외는 현재 없음.'
  assert.equal(runMockAnalysis(work, work.chapters[1], options).reviews[0].decision, '미검토')
  assert.equal(
    runMockAnalysis(work, { ...work.chapters[1], id: 'ch-3' }, options).reviews[0].decision,
    '미검토',
  )
})

test('참고 원고에서 존재하지 않는 ID와 현재 회차를 제외한다', () => {
  const work = createSampleWorks()[0]
  assert.deepEqual(
    runMockAnalysis(work, work.chapters[1], {
      ...options,
      previousIds: ['ch-1', 'ch-2', 'missing'],
    }).previousIds,
    ['ch-1'],
  )
})

test('저장 데이터의 중첩 구조와 판단 상태를 검증한다', () => {
  const works = createSampleWorks()
  assert.equal(isWorks(works), true)
  works[0].analyses = [runMockAnalysis(works[0], works[0].chapters[1], options)]
  assert.equal(isWorks(works), true)
  works[0].analyses[0].reviews[0].decision = 'unknown'
  assert.equal(isWorks(works), false)
  assert.equal(isWorks([{ title: '불완전한 데이터' }]), false)
})
