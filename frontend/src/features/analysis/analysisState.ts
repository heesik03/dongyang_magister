import type { Analysis, Setting } from '../works/workTypes'
import { isRecord } from '../../shared/responseReader'
function signature(
  settings: {
    id: string
    title: string
    content: string
    kind: string
    status: string
    version?: number
  }[],
) {
  return JSON.stringify(
    [...settings]
      .sort((a, b) => a.id.localeCompare(b.id))
      .map((s) => [s.id, s.title, s.content, s.kind, s.status, s.version ?? 0]),
  )
}
export function settingsChanged(analysis: Analysis, settings: Setting[]): boolean {
  if (!analysis.settingsSnapshot)
    return analysis.settingIds.some(
      (id) => !settings.some((setting) => setting.id === id && setting.status === '확정'),
    )
  try {
    const snapshot: unknown = JSON.parse(analysis.settingsSnapshot)
    if (!Array.isArray(snapshot)) return true
    const parsed: Setting[] = []
    for (const value of snapshot) {
      if (
        !isRecord(value) ||
        typeof value.id !== 'string' ||
        typeof value.title !== 'string' ||
        typeof value.content !== 'string' ||
        (value.kind !== '인물' &&
          value.kind !== '세계관' &&
          value.kind !== '주요 사건' &&
          value.kind !== '의도한 각색') ||
        (value.status !== '확정' && value.status !== '아이디어' && value.status !== 'AI 후보') ||
        (value.version !== undefined && typeof value.version !== 'number')
      )
        return true
      parsed.push({
        id: value.id,
        title: value.title,
        content: value.content,
        kind: value.kind,
        status: value.status,
        version: value.version,
      })
    }
    return (
      signature(parsed) !==
      signature(
        settings.filter(
          (setting) => analysis.settingIds.includes(setting.id) && setting.status === '확정',
        ),
      )
    )
  } catch {
    return true
  }
}
