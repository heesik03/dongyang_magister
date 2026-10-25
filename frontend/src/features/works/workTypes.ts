export type SettingKind = '인물' | '세계관' | '주요 사건' | '의도한 각색'
export type SettingStatus = '확정' | '아이디어' | 'AI 후보'
// 확정 설정은 검토 기준이며, 아이디어와 AI 후보는 작가의 확정 전까지 구분한다.
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
// 분석 당시의 원고와 설정을 보관해 이후 수정으로 근거가 달라졌는지 판단한다.
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
// 작품 정보·설정은 서버에 저장한다. 원고·분석·사건은 별도 workspaceVersion으로 충돌을 검사한다.
export interface Work {
  workspaceVersion?: number
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
