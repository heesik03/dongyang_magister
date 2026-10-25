import type { Work, Setting } from './workTypes'

// 네트워크 응답이 늦게 도착해도 버전이 더 높은 작품·설정을 이전 값으로 되돌리지 않는다.
export function mergeWorkByVersion(items: Work[], work: Work) {
  const existing = items.find((item) => item.id === work.id)
  if (
    existing?.source === 'server' &&
    work.source === 'server' &&
    (existing.version ?? 0) > (work.version ?? 0)
  )
    return items
  return existing ? items.map((item) => (item.id === work.id ? work : item)) : [...items, work]
}
// 작품 정보를 재조회해도 아직 서버 API가 없는 원고·판단·사건 초안은 유지한다.
export function attachLocalDrafts(work: Work, items: Work[], drafts: Work[]) {
  const existing =
    items.find((item) => item.id === work.id) || drafts.find((item) => item.id === work.id)
  return {
    ...work,
    settings: items.find((item) => item.id === work.id)?.settings || [],
    chapters: existing?.chapters || [],
    analyses: existing?.analyses || [],
    events: existing?.events || [],
  }
}
export function mergeSettingsByVersion(previous: Setting[], incoming: Setting[]) {
  return incoming.reduce((all, setting) => {
    const existing = all.find((item) => item.id === setting.id)
    if (existing && (existing.version ?? 0) > (setting.version ?? 0)) return all
    return existing
      ? all.map((item) => (item.id === setting.id ? setting : item))
      : [...all, setting]
  }, previous)
}
