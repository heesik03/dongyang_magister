import { create } from 'zustand'
import { workApi, settingApi, type WorkInput, type SettingInput } from './api'
import { isWorks } from './storage'
import type { Work, Setting } from './types'

interface SettingView {
  workId: string
  kind: string
  ids: string[]
  page: number
  totalPages: number
  loading: boolean
  error: string
}
interface WorksState {
  account: string | null
  works: Work[]
  listedIds: string[]
  listPage: number
  totalPages: number
  loading: boolean
  listError: string
  detailLoading: boolean
  detailError: string
  storageError: string
  settingView: SettingView
  initialize: (email: string) => void
  reset: () => void
  loadWorks: (keyword?: string, genre?: string, page?: number) => Promise<void>
  loadWork: (id: string) => Promise<void>
  loadSettings: (id: string, kind?: string, page?: number) => Promise<void>
  createWork: (input: WorkInput) => Promise<Work>
  saveWorkInfo: (work: Work, input: WorkInput) => Promise<void>
  saveSetting: (work: Work, input: SettingInput, existing?: Setting) => Promise<Setting>
  updateWork: (work: Work) => void
  retrySave: () => void
}
const emptyView: SettingView = {
  workId: '',
  kind: '전체',
  ids: [],
  page: 0,
  totalPages: 0,
  loading: false,
  error: '',
}
const initial = {
  account: null,
  works: [],
  listedIds: [],
  listPage: 0,
  totalPages: 0,
  loading: false,
  listError: '',
  detailLoading: false,
  detailError: '',
  storageError: '',
  settingView: emptyView,
}
let generation = 0
let listRequest: AbortController | undefined
let detailRequest: AbortController | undefined
let settingRequest: AbortController | undefined
let drafts: Work[] = []
let legacyBlocked = false
let draftsBlocked = false
function message(error: unknown) {
  return error instanceof Error ? error.message : '요청에 실패했습니다. 다시 시도해 주세요.'
}
function merge(items: Work[], work: Work) {
  const existing = items.find((item) => item.id === work.id)
  if (
    existing?.source === 'server' &&
    work.source === 'server' &&
    (existing.version ?? 0) > (work.version ?? 0)
  )
    return items
  return existing ? items.map((item) => (item.id === work.id ? work : item)) : [...items, work]
}
function hydrate(work: Work, items: Work[]) {
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
function mergeSettings(previous: Setting[], incoming: Setting[]) {
  return incoming.reduce((all, setting) => {
    const existing = all.find((item) => item.id === setting.id)
    if (existing && (existing.version ?? 0) > (setting.version ?? 0)) return all
    return existing
      ? all.map((item) => (item.id === setting.id ? setting : item))
      : [...all, setting]
  }, previous)
}
function read(key: string): Work[] {
  const raw = localStorage.getItem(key)
  if (!raw) return []
  const value: unknown = JSON.parse(raw)
  if (!isWorks(value)) throw new Error('저장된 데이터 형식이 올바르지 않습니다.')
  return value
}
export const useWorks = create<WorksState>((set, get) => ({
  ...initial,
  initialize(email) {
    get().reset()
    let legacy: Work[] = []
    let error = ''
    try {
      legacy = read(`magister.works.v1:${email}`).map((work) => ({ ...work, source: 'local' }))
    } catch {
      legacyBlocked = true
      error = '기존 브라우저 데이터를 읽지 못했습니다. 원본은 보존합니다.'
    }
    try {
      drafts = read(`magister.drafts.v1:${email}`)
    } catch {
      draftsBlocked = true
      error =
        '브라우저 원고 데이터를 읽지 못했습니다. 원본은 보존하며 변경 내용은 메모리에 유지합니다.'
    }
    set({ ...initial, account: email, works: legacy, storageError: error })
  },
  reset() {
    generation++
    listRequest?.abort()
    detailRequest?.abort()
    settingRequest?.abort()
    drafts = []
    legacyBlocked = false
    draftsBlocked = false
    set(initial)
  },
  async loadWorks(keyword = '', genre = '', page = 0) {
    const current = generation
    listRequest?.abort()
    const controller = new AbortController()
    listRequest = controller
    set({ loading: true, listError: '' })
    try {
      const result = await workApi.list(
        keyword,
        genre === '전체' ? '' : genre,
        page,
        controller.signal,
      )
      if (current !== generation || controller.signal.aborted) return
      const works = result.items.reduce((all, work) => merge(all, hydrate(work, all)), get().works)
      const ids = result.items.map((work) => work.id)
      set({
        works,
        listedIds: page === 0 ? ids : [...new Set([...get().listedIds, ...ids])],
        listPage: page,
        totalPages: result.totalPages,
        loading: false,
      })
    } catch (error) {
      if (current === generation && !controller.signal.aborted)
        set({ loading: false, listError: message(error) })
    }
  },
  async loadWork(id) {
    detailRequest?.abort()
    set({ detailLoading: false, detailError: '' })
    if (get().works.some((work) => work.id === id && work.source !== 'server')) return
    const current = generation
    detailRequest?.abort()
    const controller = new AbortController()
    detailRequest = controller
    set({ detailLoading: true, detailError: '' })
    try {
      const [work, settings] = await Promise.all([
        workApi.get(id, controller.signal),
        settingApi.list(id, '전체', 0, 100, controller.signal),
      ])
      if (current !== generation || controller.signal.aborted) return
      const full = {
        ...hydrate(work, get().works),
        settings: mergeSettings(
          get().works.find((item) => item.id === id)?.settings || [],
          settings.items,
        ),
      }
      set({ works: merge(get().works, full), detailLoading: false })
    } catch (error) {
      if (current === generation && !controller.signal.aborted)
        set({ detailLoading: false, detailError: message(error) })
    }
  },
  async loadSettings(id, kind = '전체', page = 0) {
    if (get().works.some((work) => work.id === id && work.source !== 'server')) {
      settingRequest?.abort()
      set({ settingView: { ...emptyView, workId: id, kind } })
      return
    }
    const current = generation
    settingRequest?.abort()
    const controller = new AbortController()
    settingRequest = controller
    set({ settingView: { ...get().settingView, workId: id, kind, loading: true, error: '' } })
    try {
      const result = await settingApi.list(id, kind, page, 20, controller.signal)
      if (current !== generation || controller.signal.aborted) return
      const works = get().works.map((work) =>
        work.id !== id
          ? work
          : {
              ...work,
              settings: mergeSettings(work.settings, result.items),
            },
      )
      const ids = result.items.map((setting) => setting.id)
      set({
        works,
        settingView: {
          workId: id,
          kind,
          ids: page === 0 ? ids : [...new Set([...get().settingView.ids, ...ids])],
          page,
          totalPages: result.totalPages,
          loading: false,
          error: '',
        },
      })
    } catch (error) {
      if (current === generation && !controller.signal.aborted)
        set({ settingView: { ...get().settingView, loading: false, error: message(error) } })
    }
  },
  async createWork(input) {
    const current = generation
    const work = await workApi.create(input)
    if (current !== generation) throw new Error('로그인 정보가 변경되었습니다. 다시 시도해 주세요.')
    set({ works: merge(get().works, work), listedIds: [work.id, ...get().listedIds] })
    return work
  },
  async saveWorkInfo(work, input) {
    if (work.source !== 'server') {
      get().updateWork({ ...work, ...input })
      return
    }
    const current = generation
    const saved = await workApi.update(work.id, input, work.version ?? 0)
    if (current !== generation) return
    set({ works: merge(get().works, hydrate(saved, get().works)) })
  },
  async saveSetting(work, input, existing) {
    const current = generation
    const saved =
      work.source === 'server'
        ? await settingApi.save(work.id, input, existing)
        : { ...input, id: existing?.id || crypto.randomUUID() }
    if (current !== generation) throw new Error('로그인 정보가 변경되었습니다.')
    const latest = get().works.find((item) => item.id === work.id)
    if (!latest) throw new Error('작품을 다시 불러와 주세요.')
    const next = {
      ...latest,
      settings: existing
        ? latest.settings.map((item) => (item.id === saved.id ? saved : item))
        : [...latest.settings, saved],
    }
    if (work.source === 'server') set({ works: merge(get().works, next) })
    else get().updateWork(next)
    return saved
  },
  updateWork(work) {
    const existing = get().works.find((item) => item.id === work.id)
    if (!existing) return
    // 원고·분석·타임라인의 로컬 저장은 서버의 작품 정보와 설정을 덮어쓰지 않는다.
    const next =
      existing.source === 'server'
        ? { ...existing, chapters: work.chapters, analyses: work.analyses, events: work.events }
        : { ...work, updatedAt: new Date().toISOString() }
    set({ works: merge(get().works, next) })
    if (next.source === 'server') drafts = merge(drafts, next)
    get().retrySave()
  },
  retrySave() {
    const email = get().account
    if (!email) return
    try {
      if (!legacyBlocked)
        localStorage.setItem(
          `magister.works.v1:${email}`,
          JSON.stringify(get().works.filter((work) => work.source !== 'server')),
        )
      if (!draftsBlocked)
        localStorage.setItem(`magister.drafts.v1:${email}`, JSON.stringify(drafts))
      if (!legacyBlocked && !draftsBlocked) set({ storageError: '' })
    } catch {
      set({
        storageError:
          '브라우저 저장에 실패했습니다. 입력은 화면에 유지됩니다. 저장 공간을 확인하고 다시 시도해 주세요.',
      })
    }
  },
}))
