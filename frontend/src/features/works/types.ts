export type SettingKind = '인물' | '세계관' | '주요 사건' | '의도한 각색'
export type SettingStatus = '확정' | '아이디어' | 'AI 후보'
export interface Setting {
  version?: number
  id: string
  title: string
  kind: SettingKind
  status: SettingStatus
  content: string
}
export interface Chapter {
  id: string
  title: string
  content: string
  revision: number
}
export type Decision = '미검토' | '수용' | '보류' | '의도된 설정'
export interface Review {
  id: string
  type: '설정 충돌 후보' | '설명 보완'
  quote: string
  settingId: string
  evidence: string
  reason: string
  suggestion: string
  decision: Decision
  note: string
}
export interface Analysis {
  chapterId: string
  revision: number
  source: string
  settingIds: string[]
  previousIds: string[]
  reviews: Review[]
  createdAt: string
  settingsSnapshot?: string
}
export interface StoryEvent {
  id: string
  title: string
  time: string
  characters: string
  chapterId: string
  quote: string
}
export interface Work {
  source?: 'server' | 'local'
  version?: number
  id: string
  title: string
  genre: string
  era: string
  description: string
  updatedAt: string
  settings: Setting[]
  chapters: Chapter[]
  analyses: Analysis[]
  events: StoryEvent[]
}
