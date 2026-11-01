import { expect, test } from 'vitest'
import { parseWork } from '../src/features/works/workResponse'
import { isWorks } from '../src/features/works/workStorage'
import { createSampleWorks } from './fixtures/sampleWorks'
test('보관함 요약은 원고 본문 없이 조회하고 잘못된 개수 타입은 거절한다', () => {
  const raw = {
    id: 1,
    title: '작품',
    genre: '판타지',
    era: '',
    description: '',
    updatedAt: '2026-11-01T00:00:00Z',
    version: 0,
    chapterCount: 2,
    settingCount: 3,
    latestChapterId: 'c2',
    latestChapterTitle: '2화',
  }
  const work = parseWork(raw)
  expect(work.summary).toEqual({
    chapterCount: 2,
    settingCount: 3,
    latestChapterId: 'c2',
    latestChapterTitle: '2화',
  })
  expect(work.chapters).toEqual([])
  expect(() => parseWork({ ...raw, chapterCount: -1 })).toThrow()
  expect(() => parseWork({ ...raw, latestChapterTitle: 2 })).toThrow()
})
test('기존 사건은 그대로 읽고 새 사건 설명의 형식을 검증한다', () => {
  const works = createSampleWorks()
  expect(isWorks(works)).toBe(true)
  const work = works[0]
  const event = {
    id: 'e',
    title: '사건',
    time: '',
    characters: '',
    chapterId: '',
    quote: '',
    description: '문이 열렸다.',
  }
  expect(isWorks([{ ...work, events: [event] }])).toBe(true)
  expect(isWorks([{ ...work, events: [{ ...event, description: 123 }] }])).toBe(false)
  expect(isWorks([{ ...work, events: [{ ...event, description: '가'.repeat(2001) }] }])).toBe(false)
})
