import type { Analysis, Setting } from '../works/workTypes'
import type { Work } from '../works/workTypes'
import type { InputSnapshot } from './analysisClient'
import { isRecord } from '../../shared/responseReader'

export function inputChanged(input: InputSnapshot, work: Work): boolean {
  return (
    [input.chapter, ...input.previous].some((source) => {
      const current = work.chapters.find((chapter) => chapter.id === source.id)
      return (
        !current ||
        current.content !== source.content ||
        current.title !== source.title ||
        current.revision !== source.revision
      )
    }) ||
    input.settings.some((source) => {
      const current = work.settings.find((setting) => setting.id === source.id)
      return (
        !current ||
        current.version !== source.version ||
        current.status !== '확정' ||
        current.content !== source.content ||
        current.title !== source.title ||
        current.kind !== source.kind
      )
    })
  )
}
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
      .sort((first, second) => first.id.localeCompare(second.id))
      .map((setting) => [
        setting.id,
        setting.title,
        setting.content,
        setting.kind,
        setting.status,
        setting.version ?? 0,
      ]),
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
