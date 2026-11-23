import { readInteger, readRecord, readString } from '../../shared/responseReader'

export const historyStatuses = {
  POSSIBLE_CONFLICT: '시대 충돌 후보',
  NO_CONFLICT_FOUND: '확인한 근거에서 충돌 없음',
  INSUFFICIENT_EVIDENCE: '근거 부족',
  LOOKUP_FAILED: '자료 조회 실패',
}
export const choices = { ACCEPT: '수용', HOLD: '보류', INTENDED: '의도된 설정' }
export type HistoryDecision = { choice: keyof typeof choices; reason: string }
const progress = {
  COMPLETED: '완료',
  FAILED: '실패',
  PARTIAL: '일부 조회 실패',
  NOT_REQUESTED: '미요청',
}

function member<T extends string>(value: string, options: Record<T, string>): T {
  if (!Object.hasOwn(options, value)) throw new Error('역사 고증 상태를 확인할 수 없습니다.')
  return value as T
}

export function parseHistoryReport(raw: unknown, decisions: unknown) {
  const item = readRecord(raw)
  const saved = decisions == null ? {} : readRecord(decisions)
  if (!Array.isArray(item.findings) || item.findings.length > 3)
    throw new Error('역사 고증 항목이 올바르지 않습니다.')
  return {
    revision: readInteger(item, 'revision', 1),
    checkedAt: readString(item, 'checkedAt'),
    historicalStatus: member(readString(item, 'historicalStatus'), progress),
    standardStatus: member(readString(item, 'standardStatus'), progress),
    findings: item.findings.map((raw, index) => {
      const finding = readRecord(raw)
      const startOffset = readInteger(finding, 'startOffset')
      const endOffset = readInteger(finding, 'endOffset', 1)
      const quote = readString(finding, 'quote')
      if (!quote || endOffset - startOffset !== quote.length || !Array.isArray(finding.evidence))
        throw new Error('역사 고증 근거 형식이 올바르지 않습니다.')
      const row = saved[index] == null ? undefined : readRecord(saved[index])
      return {
        startOffset,
        endOffset,
        quote,
        target: readString(finding, 'target'),
        sceneTime: readString(finding, 'sceneTime'),
        assertion: readString(finding, 'assertion'),
        reason: readString(finding, 'reason'),
        status: member(readString(finding, 'status'), historyStatuses),
        decision: row && {
          choice: member(readString(row, 'choice'), choices),
          reason: readString(row, 'reason'),
        },
        evidence: finding.evidence.map((raw) => {
          const evidence = readRecord(raw)
          const url = new URL(readString(evidence, 'sourceUrl'))
          if (!['http:', 'https:'].includes(url.protocol))
            throw new Error('출처 주소가 올바르지 않습니다.')
          return {
            title: readString(evidence, 'title'),
            institution: readString(evidence, 'institution'),
            url: url.href,
            passage: evidence.passage == null ? null : readString(evidence, 'passage'),
          }
        }),
      }
    }),
  }
}
export type HistoryReport = ReturnType<typeof parseHistoryReport>
export const historyProgress = progress
