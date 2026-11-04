import { httpClient } from '../../shared/httpClient'
import { parsePage, readInteger, readRecord, readString } from '../../shared/responseReader'
import { parseDecision, type SavedDecision } from './decisionClient'

export interface SourceLocation {
  sourceId: number
  quote: string
  startOffset: number
  endOffset: number
}
export interface ReviewEvidence extends SourceLocation {
  sourceType: 'SETTING' | 'PREVIOUS'
  sourceKey: string
  title: string
  revision: number
  explanation: string
}
export interface RevisionOption {
  title: string
  direction: string
  premise: string
  rationale: string
}
export interface LinkedReview {
  id: string
  type: '설정 충돌 후보' | '설명 보완'
  quote: string
  targetSourceId: number
  startOffset: number
  endOffset: number
  reason: string
  suggestion: string
  evidences: ReviewEvidence[]
  issueGroupKey?: string | null
  issueGroupTitle?: string | null
  confirmedFact?: string | null
  interpretation?: string | null
  missingInfo?: string | null
  options?: RevisionOption[]
}
export interface PriorityIssue {
  groupKey: string
  title: string
  type: string
  summary: string
  candidateIds: string[]
  sourceTitles: string[]
}
export interface ScopeSummary {
  scopeNotice: string
  settingCount: number
  previousChapterCount: number
  totalCandidates: number
  conflictCount: number
  supplementCount: number
  unreviewedCount: number
  issueGroupCount: number
  priorityItems: PriorityIssue[]
}
export interface ReviewReport {
  reviews: LinkedReview[]
  legacy: boolean
  stale: boolean
  decisions?: Record<string, SavedDecision>
  summary?: ScopeSummary | null
}
export interface ReviewSource {
  id: number
  type: string
  sourceKey: string
  title: string
  content: string
  revision: number
}
export interface ChapterReviewSummary {
  chapterId: string
  chapterNo: number
  chapterTitle: string
  currentRevision: number
  analysisStatus: 'CURRENT' | 'OUTDATED' | 'UNANALYZED'
  latestJobId: string | null
  analyzedRevision: number | null
  analyzedAt: string | null
  settingCount: number
  previousChapterCount: number
  analysisRunCount: number
  totalCandidateCount: number
  conflictCount: number
  supplementCount: number
  unreviewedCount: number
  acceptedCount: number
  holdCount: number
  intendedCount: number
}
export interface WorkReviewOverview {
  workId: number
  totalChapters: number
  analyzedChapters: number
  currentRevisionChapters: number
  outdatedRevisionChapters: number
  unanalyzedChapters: number
  totalConflictCount: number
  totalSupplementCount: number
  totalUnreviewedCount: number
  aggregationBasisNote: string
  chapters: ChapterReviewSummary[]
}
export interface ComparedCandidate {
  category: 'RETAINED' | 'ADDED' | 'NOT_REAPPEARED' | 'INCOMPARABLE'
  candidateId: string
  matchedPreviousCandidateId: string | null
  type: string
  quote: string
  startOffset: number
  endOffset: number
  previousStartOffset: number | null
  previousEndOffset: number | null
  reason: string
  suggestion: string
  sourceTitles: string[]
  comparisonNote: string
}
export interface ReviewComparison {
  currentJobId: string
  baseJobId: string | null
  hasBaseJob: boolean
  chapterId: string
  chapterTitle: string
  currentChapterRevision: number
  baseChapterRevision: number | null
  currentAnalyzedAt: string | null
  baseAnalyzedAt: string | null
  scopeChanged: boolean
  manuscriptModified: boolean
  scopeComparisonNote: string
  resolutionCautionNote: string
  retained: ComparedCandidate[]
  added: ComparedCandidate[]
  notReappeared: ComparedCandidate[]
  incomparable: ComparedCandidate[]
}

function readNullableString(value: Record<string, unknown>, field: string): string | null {
  const result = value[field]
  if (result === null || result === undefined) return null
  if (typeof result !== 'string')
    throw new Error('서버 응답의 ' + field + ' 필드가 올바르지 않습니다.')
  return result
}

function readNullableInteger(
  value: Record<string, unknown>,
  field: string,
  minimum = 0,
): number | null {
  const result = value[field]
  if (result === null || result === undefined) return null
  if (typeof result !== 'number' || !Number.isSafeInteger(result) || result < minimum)
    throw new Error('서버 응답의 ' + field + ' 숫자가 올바르지 않습니다.')
  return result
}

function range(value: Record<string, unknown>) {
  const quote = readString(value, 'quote')
  const startOffset = readInteger(value, 'startOffset')
  const endOffset = readInteger(value, 'endOffset', 1)
  if (!quote.trim() || endOffset - startOffset !== quote.length)
    throw new Error('근거 인용 위치가 올바르지 않습니다.')
  return { quote, startOffset, endOffset }
}

function parseRevisionOption(raw: unknown): RevisionOption {
  const value = readRecord(raw)
  return {
    title: readString(value, 'title'),
    direction: readString(value, 'direction'),
    premise: readString(value, 'premise'),
    rationale: readString(value, 'rationale'),
  }
}

export function parseLinkedReview(raw: unknown): LinkedReview {
  const value = readRecord(raw)
  const type = readString(value, 'type')
  if (
    (type !== '설정 충돌 후보' && type !== '설명 보완') ||
    !Array.isArray(value.evidences) ||
    value.evidences.length < 1 ||
    value.evidences.length > 10
  )
    throw new Error('검토 후보 형식이 올바르지 않습니다.')
  const options = Array.isArray(value.options) ? value.options.map(parseRevisionOption) : []
  return {
    id: readString(value, 'id'),
    type,
    ...range(value),
    targetSourceId: readInteger(value, 'targetSourceId', 1),
    reason: readString(value, 'reason'),
    suggestion: readString(value, 'suggestion'),
    evidences: value.evidences.map((raw) => {
      const evidence = readRecord(raw)
      const sourceType = readString(evidence, 'sourceType')
      if (sourceType !== 'SETTING' && sourceType !== 'PREVIOUS')
        throw new Error('근거 출처 종류가 올바르지 않습니다.')
      return {
        ...range(evidence),
        sourceId: readInteger(evidence, 'sourceId', 1),
        sourceType,
        sourceKey: readString(evidence, 'sourceKey'),
        title: readString(evidence, 'title'),
        revision: readInteger(evidence, 'revision'),
        explanation: readString(evidence, 'explanation'),
      }
    }),
    issueGroupKey: readNullableString(value, 'issueGroupKey'),
    issueGroupTitle: readNullableString(value, 'issueGroupTitle'),
    confirmedFact: readNullableString(value, 'confirmedFact'),
    interpretation: readNullableString(value, 'interpretation'),
    missingInfo: readNullableString(value, 'missingInfo'),
    options,
  }
}

function parseScopeSummary(raw: unknown): ScopeSummary | null {
  if (raw === null || raw === undefined) return null
  const value = readRecord(raw)
  const priorityItems = Array.isArray(value.priorityItems)
    ? value.priorityItems.map((entry) => {
        const item = readRecord(entry)
        return {
          groupKey: readString(item, 'groupKey'),
          title: readString(item, 'title'),
          type: readString(item, 'type'),
          summary: readString(item, 'summary'),
          candidateIds: Array.isArray(item.candidateIds)
            ? item.candidateIds.map((id) => String(id))
            : [],
          sourceTitles: Array.isArray(item.sourceTitles)
            ? item.sourceTitles.map((title) => String(title))
            : [],
        }
      })
    : []
  return {
    scopeNotice: readString(value, 'scopeNotice'),
    settingCount: readInteger(value, 'settingCount'),
    previousChapterCount: readInteger(value, 'previousChapterCount'),
    totalCandidates: readInteger(value, 'totalCandidates'),
    conflictCount: readInteger(value, 'conflictCount'),
    supplementCount: readInteger(value, 'supplementCount'),
    unreviewedCount: readInteger(value, 'unreviewedCount'),
    issueGroupCount: readInteger(value, 'issueGroupCount'),
    priorityItems,
  }
}

function parseChapterSummary(raw: unknown): ChapterReviewSummary {
  const value = readRecord(raw)
  const analysisStatus = readString(value, 'analysisStatus')
  if (
    analysisStatus !== 'CURRENT' &&
    analysisStatus !== 'OUTDATED' &&
    analysisStatus !== 'UNANALYZED'
  ) {
    throw new Error('회차 분석 상태가 올바르지 않습니다.')
  }
  return {
    chapterId: readString(value, 'chapterId'),
    chapterNo: readInteger(value, 'chapterNo', 1),
    chapterTitle: readString(value, 'chapterTitle'),
    currentRevision: readInteger(value, 'currentRevision'),
    analysisStatus,
    latestJobId: readNullableString(value, 'latestJobId'),
    analyzedRevision: readNullableInteger(value, 'analyzedRevision'),
    analyzedAt: readNullableString(value, 'analyzedAt'),
    settingCount: readInteger(value, 'settingCount'),
    previousChapterCount: readInteger(value, 'previousChapterCount'),
    analysisRunCount: readInteger(value, 'analysisRunCount'),
    totalCandidateCount: readInteger(value, 'totalCandidateCount'),
    conflictCount: readInteger(value, 'conflictCount'),
    supplementCount: readInteger(value, 'supplementCount'),
    unreviewedCount: readInteger(value, 'unreviewedCount'),
    acceptedCount: readInteger(value, 'acceptedCount'),
    holdCount: readInteger(value, 'holdCount'),
    intendedCount: readInteger(value, 'intendedCount'),
  }
}

export function parseWorkReviewOverview(raw: unknown): WorkReviewOverview {
  const value = readRecord(raw)
  if (!Array.isArray(value.chapters)) throw new Error('회차별 검토 현황 목록이 올바르지 않습니다.')
  return {
    workId: readInteger(value, 'workId', 1),
    totalChapters: readInteger(value, 'totalChapters'),
    analyzedChapters: readInteger(value, 'analyzedChapters'),
    currentRevisionChapters: readInteger(value, 'currentRevisionChapters'),
    outdatedRevisionChapters: readInteger(value, 'outdatedRevisionChapters'),
    unanalyzedChapters: readInteger(value, 'unanalyzedChapters'),
    totalConflictCount: readInteger(value, 'totalConflictCount'),
    totalSupplementCount: readInteger(value, 'totalSupplementCount'),
    totalUnreviewedCount: readInteger(value, 'totalUnreviewedCount'),
    aggregationBasisNote: readString(value, 'aggregationBasisNote'),
    chapters: value.chapters.map(parseChapterSummary),
  }
}

function parseComparedCandidate(raw: unknown): ComparedCandidate {
  const value = readRecord(raw)
  const category = readString(value, 'category')
  if (
    category !== 'RETAINED' &&
    category !== 'ADDED' &&
    category !== 'NOT_REAPPEARED' &&
    category !== 'INCOMPARABLE'
  ) {
    throw new Error('비교 후보 분류가 올바르지 않습니다.')
  }
  return {
    category,
    candidateId: readString(value, 'candidateId'),
    matchedPreviousCandidateId: readNullableString(value, 'matchedPreviousCandidateId'),
    type: readString(value, 'type'),
    quote: readString(value, 'quote'),
    startOffset: readInteger(value, 'startOffset'),
    endOffset: readInteger(value, 'endOffset', 1),
    previousStartOffset: readNullableInteger(value, 'previousStartOffset'),
    previousEndOffset: readNullableInteger(value, 'previousEndOffset'),
    reason: readString(value, 'reason'),
    suggestion: readString(value, 'suggestion'),
    sourceTitles: Array.isArray(value.sourceTitles)
      ? value.sourceTitles.map((item) => String(item))
      : [],
    comparisonNote: readString(value, 'comparisonNote'),
  }
}

export function parseReviewComparison(raw: unknown): ReviewComparison {
  const value = readRecord(raw)
  if (typeof value.hasBaseJob !== 'boolean' || typeof value.scopeChanged !== 'boolean') {
    throw new Error('비교 결과 형식이 올바르지 않습니다.')
  }
  return {
    currentJobId: readString(value, 'currentJobId'),
    baseJobId: readNullableString(value, 'baseJobId'),
    hasBaseJob: value.hasBaseJob,
    chapterId: readString(value, 'chapterId'),
    chapterTitle: readString(value, 'chapterTitle'),
    currentChapterRevision: readInteger(value, 'currentChapterRevision'),
    baseChapterRevision: readNullableInteger(value, 'baseChapterRevision'),
    currentAnalyzedAt: readNullableString(value, 'currentAnalyzedAt'),
    baseAnalyzedAt: readNullableString(value, 'baseAnalyzedAt'),
    scopeChanged: value.scopeChanged,
    manuscriptModified: Boolean(value.manuscriptModified),
    scopeComparisonNote: readString(value, 'scopeComparisonNote'),
    resolutionCautionNote: readString(value, 'resolutionCautionNote'),
    retained: Array.isArray(value.retained) ? value.retained.map(parseComparedCandidate) : [],
    added: Array.isArray(value.added) ? value.added.map(parseComparedCandidate) : [],
    notReappeared: Array.isArray(value.notReappeared)
      ? value.notReappeared.map(parseComparedCandidate)
      : [],
    incomparable: Array.isArray(value.incomparable)
      ? value.incomparable.map(parseComparedCandidate)
      : [],
  }
}

const path = (workId: string, jobId: string) =>
  '/work/' + encodeURIComponent(workId) + '/analysis/' + encodeURIComponent(jobId)

export const reviewClient = {
  async list(workId: string, jobId: string, signal?: AbortSignal): Promise<ReviewReport> {
    const value = readRecord(
      await httpClient.get(path(workId, jobId) + '/review', {
        signal,
        params: { page: 0, size: 100 },
      }),
    )
    if (typeof value.legacy !== 'boolean' || typeof value.stale !== 'boolean')
      throw new Error('검토 상태가 올바르지 않습니다.')
    const page = parsePage(value.candidates, parseLinkedReview)
    if (page.totalElements > 100 || page.items.length !== page.totalElements)
      throw new Error('검토 결과 범위를 초과했습니다.')
    const decisions: Record<string, SavedDecision> = {}
    if (value.decisions !== undefined) {
      for (const [id, raw] of Object.entries(readRecord(value.decisions))) {
        const decision = parseDecision(raw)
        if (decision.reviewId !== id || !page.items.some((review) => review.id === id))
          throw new Error('판단과 후보가 일치하지 않습니다.')
        decisions[id] = decision
      }
    }
    return {
      reviews: page.items,
      legacy: value.legacy,
      stale: value.stale,
      decisions,
      summary: parseScopeSummary(value.summary),
    }
  },
  async source(
    workId: string,
    jobId: string,
    sourceId: number,
    signal?: AbortSignal,
  ): Promise<ReviewSource> {
    const value = readRecord(
      await httpClient.get(path(workId, jobId) + '/source/' + sourceId, { signal }),
    )
    const id = readInteger(value, 'id', 1)
    const type = readString(value, 'type')
    if (id !== sourceId || !['TARGET', 'SETTING', 'PREVIOUS'].includes(type))
      throw new Error('근거 원문이 올바르지 않습니다.')
    return {
      id,
      type,
      sourceKey: readString(value, 'sourceId'),
      title: readString(value, 'title'),
      content: readString(value, 'content'),
      revision: readInteger(value, 'revision'),
    }
  },
  async getOverview(workId: string, signal?: AbortSignal): Promise<WorkReviewOverview> {
    return parseWorkReviewOverview(
      await httpClient.get('/work/' + encodeURIComponent(workId) + '/analysis/overview', {
        signal,
      }),
    )
  },
  async compare(
    workId: string,
    jobId: string,
    baseJobId?: string,
    signal?: AbortSignal,
  ): Promise<ReviewComparison> {
    return parseReviewComparison(
      await httpClient.get(path(workId, jobId) + '/compare', {
        signal,
        params: baseJobId ? { baseJobId } : undefined,
      }),
    )
  },
}
