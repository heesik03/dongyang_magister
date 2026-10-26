// 외부 응답은 타입 선언만으로 신뢰하지 않고 통신 경계에서 검증한다.
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
export function readRecord(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) throw new Error('서버 응답 형식이 올바르지 않습니다.')
  return value
}
export function readString(value: Record<string, unknown>, field: string): string {
  const result = value[field]
  if (typeof result !== 'string')
    throw new Error('서버 응답의 ' + field + ' 필드가 올바르지 않습니다.')
  return result
}
export function readInteger(value: Record<string, unknown>, field: string, minimum = 0): number {
  const result = value[field]
  if (typeof result !== 'number' || !Number.isSafeInteger(result) || result < minimum) {
    throw new Error('서버 응답의 ' + field + ' 숫자가 올바르지 않습니다.')
  }
  return result
}
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export function parsePage<T>(raw: unknown, parseItem: (entry: unknown) => T): Page<T> {
  const value = readRecord(raw)
  if (!Array.isArray(value.items)) throw new Error('목록 응답이 올바르지 않습니다.')
  return {
    items: value.items.map(parseItem),
    page: readInteger(value, 'page'),
    size: readInteger(value, 'size', 1),
    totalElements: readInteger(value, 'totalElements'),
    totalPages: readInteger(value, 'totalPages'),
  }
}

export interface CursorPage<T> {
  items: T[]
  hasNext: boolean
  nextCursor: string | null
}

export function parseCursorPage<T>(raw: unknown, parseItem: (entry: unknown) => T): CursorPage<T> {
  const value = readRecord(raw)
  if (
    !Array.isArray(value.items) ||
    typeof value.hasNext !== 'boolean' ||
    (value.nextCursor !== null &&
      (typeof value.nextCursor !== 'string' || value.nextCursor.length === 0)) ||
    value.hasNext !== (value.nextCursor !== null) ||
    (value.hasNext && value.items.length === 0)
  )
    throw new Error('커서 목록 응답이 올바르지 않습니다.')

  return {
    items: value.items.map(parseItem),
    hasNext: value.hasNext,
    nextCursor: value.nextCursor,
  }
}
