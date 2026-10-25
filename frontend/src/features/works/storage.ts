import type { Work } from './types'

function record(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}
function strings(value: Record<string, unknown>, keys: string[]) {
  return keys.every((key) => typeof value[key] === 'string')
}
function arrayOf(value: unknown, check: (entry: unknown) => boolean) {
  return Array.isArray(value) && value.every(check)
}
export function isWorks(value: unknown): value is Work[] {
  return arrayOf(
    value,
    (work) =>
      record(work) &&
      strings(work, ['id', 'title', 'genre', 'era', 'description', 'updatedAt']) &&
      arrayOf(
        work.settings,
        (s) =>
          record(s) &&
          strings(s, ['id', 'title', 'content']) &&
          ['인물', '세계관', '주요 사건', '의도한 각색'].includes(String(s.kind)) &&
          ['확정', '아이디어', 'AI 후보'].includes(String(s.status)),
      ) &&
      arrayOf(
        work.chapters,
        (c) =>
          record(c) &&
          strings(c, ['id', 'title', 'content']) &&
          typeof c.revision === 'number' &&
          Number.isInteger(c.revision),
      ) &&
      arrayOf(
        work.events,
        (e) => record(e) && strings(e, ['id', 'title', 'time', 'characters', 'chapterId', 'quote']),
      ) &&
      arrayOf(
        work.analyses,
        (a) =>
          record(a) &&
          strings(a, ['chapterId', 'source', 'createdAt']) &&
          typeof a.revision === 'number' &&
          arrayOf(a.settingIds, (id) => typeof id === 'string') &&
          arrayOf(a.previousIds, (id) => typeof id === 'string') &&
          arrayOf(
            a.reviews,
            (r) =>
              record(r) &&
              strings(r, [
                'id',
                'quote',
                'settingId',
                'evidence',
                'reason',
                'suggestion',
                'note',
              ]) &&
              ['설정 충돌 후보', '설명 보완'].includes(String(r.type)) &&
              ['미검토', '수용', '보류', '의도된 설정'].includes(String(r.decision)),
          ),
      ),
  )
}
