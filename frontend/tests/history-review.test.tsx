// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { HistoryReviewPanel } from '../src/features/review/HistoryReviewPanel'
import { parseHistoryReport } from '../src/features/review/historyReport'
import { reviewClient } from '../src/features/review/reviewClient'

test('공식 근거와 원문 위치를 표시하고 선택한 판단·사유를 해당 항목에 저장한다', async () => {
  const quote = '경복궁에 도착했다.'
  const chapter = { id: 'target', title: '1화', content: quote, revision: 2 }
  const report = parseHistoryReport(
    {
      revision: 2,
      checkedAt: '2026-11-23T00:00:00Z',
      standardStatus: 'FAILED',
      historicalStatus: 'COMPLETED',
      findings: [
        {
          quote,
          startOffset: 0,
          endOffset: quote.length,
          target: '경복궁',
          assertion: '궁이 존재했다',
          sceneTime: '1392년',
          status: 'POSSIBLE_CONFLICT',
          reason: '창건 시점 확인',
          evidence: [
            {
              title: '경복궁',
              institution: '한국민족문화대백과사전',
              sourceUrl: 'https://encykorea.aks.ac.kr/',
              passage: '1395년에 창건되었다.',
            },
          ],
        },
      ],
    },
    {},
  )
  const save = vi.spyOn(reviewClient, 'saveHistoryDecision').mockResolvedValue(undefined)
  const props = {
    workId: '1',
    jobId: 'job',
    chapter,
    report,
    stale: false,
    onJump: vi.fn(),
    onSaved: vi.fn(),
  }
  const view = render(<HistoryReviewPanel {...props} />)
  expect(screen.getByRole('link', { name: /경복궁/ }).getAttribute('href')).toBe(
    'https://encykorea.aks.ac.kr/',
  )
  expect(screen.getByText('1395년에 창건되었다.')).toBeTruthy()
  expect(screen.getByText(/설정·개연성 검토에 실패/)).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '원고에서 확인' }))
  expect(props.onJump).toHaveBeenCalledWith(
    expect.objectContaining({
      quote,
      sourceKey: 'target',
      startOffset: 0,
      endOffset: quote.length,
    }),
  )
  fireEvent.click(screen.getByRole('button', { name: '의도된 설정' }))
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '대체 역사 설정' } })
  fireEvent.click(screen.getByRole('button', { name: '판단 저장' }))
  await waitFor(() => expect(props.onSaved).toHaveBeenCalledOnce())
  expect(save).toHaveBeenCalledWith(
    '1',
    'job',
    0,
    { choice: 'INTENDED', reason: '대체 역사 설정' },
    expect.any(AbortSignal),
  )
  view.rerender(<HistoryReviewPanel {...props} chapter={{ ...chapter, revision: 3 }} />)
  expect(
    (screen.getByRole('button', { name: '원고에서 확인' }) as HTMLButtonElement).disabled,
  ).toBe(true)
  save.mockRestore()
})

test('기관 전문을 펼치면 판정에 사용한 인용문과 기관별 조회 상태를 확인할 수 있다', () => {
  const quote = '1959년 경복궁의 건설되었다.'
  const citation = '1395년에 창건되었다.'
  const report = parseHistoryReport(
    {
      revision: 1,
      checkedAt: '2026-11-23T00:00:00Z',
      historicalStatus: 'PARTIAL',
      standardStatus: 'NOT_REQUESTED',
      findings: [
        {
          quote,
          startOffset: 0,
          endOffset: quote.length,
          target: '경복궁',
          sceneTime: '1959년',
          assertion: '최초 건립',
          kind: 'FOUNDATION',
          status: 'POSSIBLE_CONFLICT',
          reason: '건립 연도가 다릅니다.',
          officialYear: 1395,
          citation,
          lookups: [{ institution: '한국학중앙연구원', status: 'FAILED' }],
          evidence: [
            {
              title: '경복궁',
              institution: '국가유산청',
              sourceUrl: 'https://www.heritage.go.kr/',
              passage: '시대\n조선\n' + citation,
            },
          ],
        },
      ],
    },
    {},
  )
  const view = render(
    <HistoryReviewPanel workId="1" jobId="job" report={report} stale={false} onJump={() => {}} />,
  )
  const disclosure = view.getByText('기관 자료 전체 보기')
  expect(disclosure.closest('details')?.open).toBe(false)
  fireEvent.click(disclosure)
  expect(disclosure.closest('details')?.open).toBe(true)
  expect(view.container.querySelector('mark')?.textContent).toBe(citation)
  expect(view.getByText('한국학중앙연구원 · 조회 실패')).toBeTruthy()
  expect(view.getByText(/자료의 창건 연도 · 1395년/)).toBeTruthy()
})
