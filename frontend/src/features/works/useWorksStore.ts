import { create } from 'zustand'
import { workClient, type WorkInput } from './workClient'
import { settingClient, type SettingInput } from '../settings/settingClient'
import { readStoredWorks } from './workStorage'
import { mergeWorkByVersion, attachLocalDrafts, mergeSettingsByVersion } from './workCache'
import type { Work, Setting } from './workTypes'

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
const emptySettingView: SettingView = {
  workId: '',
  kind: '전체',
  ids: [],
  page: 0,
  totalPages: 0,
  loading: false,
  error: '',
}
const initialState = {
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
  settingView: emptySettingView,
}
// 계정이 바뀌기 전에 시작한 요청은 새 작업 공간에 결과를 반영하지 않는다.
let accountRevision = 0
let listRequest: AbortController | undefined
let detailRequest: AbortController | undefined
let settingRequest: AbortController | undefined
let drafts: Work[] = []
let legacyBlocked = false
let draftsBlocked = false
function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '요청에 실패했습니다. 다시 시도해 주세요.'
}
// 보관함·설정집·원고·타임라인이 공유하는 작품 데이터만 이 store에서 관리한다.
// 폼 입력, 선택 항목, 모달 열림 상태는 각 화면의 로컬 상태로 둔다.
export const useWorksStore = create<WorksState>((set, get) => ({
  ...initialState,
  initialize(email) {
    get().reset()
    let legacy: Work[] = []
    let error = ''
    try {
      legacy = readStoredWorks(`magister.works.v1:${email}`).map((work) => ({
        ...work,
        source: 'local',
      }))
    } catch {
      legacyBlocked = true
      error = '기존 브라우저 데이터를 읽지 못했습니다. 원본은 보존합니다.'
    }
    try {
      drafts = readStoredWorks(`magister.drafts.v1:${email}`)
    } catch {
      draftsBlocked = true
      error =
        '브라우저 원고 데이터를 읽지 못했습니다. 원본은 보존하며 변경 내용은 메모리에 유지합니다.'
    }
    set({ ...initialState, account: email, works: legacy, storageError: error })
  },
  reset() {
    accountRevision++
    listRequest?.abort()
    detailRequest?.abort()
    settingRequest?.abort()
    drafts = []
    legacyBlocked = false
    draftsBlocked = false
    set(initialState)
  },
  async loadWorks(keyword = '', genre = '', page = 0) {
    const current = accountRevision
    listRequest?.abort()
    const controller = new AbortController()
    listRequest = controller
    set({ loading: true, listError: '' })
    try {
      const result = await workClient.list({
        keyword,
        genre: genre === '전체' ? '' : genre,
        page,
        signal: controller.signal,
      })
      if (current !== accountRevision || controller.signal.aborted) return
      const works = result.items.reduce(
        (all, work) => mergeWorkByVersion(all, attachLocalDrafts(work, all, drafts)),
        get().works,
      )
      const ids = result.items.map((work) => work.id)
      set({
        works,
        listedIds: page === 0 ? ids : [...new Set([...get().listedIds, ...ids])],
        listPage: page,
        totalPages: result.totalPages,
        loading: false,
      })
    } catch (error) {
      if (current === accountRevision && !controller.signal.aborted)
        set({ loading: false, listError: getErrorMessage(error) })
    }
  },
  async loadWork(id) {
    detailRequest?.abort()
    set({ detailLoading: false, detailError: '' })
    if (get().works.some((work) => work.id === id && work.source !== 'server')) return
    const current = accountRevision
    const controller = new AbortController()
    detailRequest = controller
    set({ detailLoading: true, detailError: '' })
    try {
      const [work, settings] = await Promise.all([
        workClient.get(id, controller.signal),
        settingClient.list(id, { size: 100, signal: controller.signal }),
      ])
      if (current !== accountRevision || controller.signal.aborted) return
      const full = {
        ...attachLocalDrafts(work, get().works, drafts),
        settings: mergeSettingsByVersion(
          get().works.find((item) => item.id === id)?.settings || [],
          settings.items,
        ),
      }
      set({ works: mergeWorkByVersion(get().works, full), detailLoading: false })
    } catch (error) {
      if (current === accountRevision && !controller.signal.aborted)
        set({ detailLoading: false, detailError: getErrorMessage(error) })
    }
  },
  async loadSettings(id, kind = '전체', page = 0) {
    if (get().works.some((work) => work.id === id && work.source !== 'server')) {
      settingRequest?.abort()
      set({ settingView: { ...emptySettingView, workId: id, kind } })
      return
    }
    const current = accountRevision
    settingRequest?.abort()
    const controller = new AbortController()
    settingRequest = controller
    set({ settingView: { ...get().settingView, workId: id, kind, loading: true, error: '' } })
    try {
      const result = await settingClient.list(id, { kind, page, signal: controller.signal })
      if (current !== accountRevision || controller.signal.aborted) return
      const works = get().works.map((work) =>
        work.id !== id
          ? work
          : {
              ...work,
              settings: mergeSettingsByVersion(work.settings, result.items),
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
      if (current === accountRevision && !controller.signal.aborted)
        set({
          settingView: { ...get().settingView, loading: false, error: getErrorMessage(error) },
        })
    }
  },
  async createWork(input) {
    const current = accountRevision
    const work = await workClient.create(input)
    if (current !== accountRevision)
      throw new Error('로그인 정보가 변경되었습니다. 다시 시도해 주세요.')
    set({ works: mergeWorkByVersion(get().works, work), listedIds: [work.id, ...get().listedIds] })
    return work
  },
  async saveWorkInfo(work, input) {
    if (work.source !== 'server') {
      get().updateWork({ ...work, ...input })
      return
    }
    const current = accountRevision
    const saved = await workClient.update(work.id, input, work.version ?? 0)
    if (current !== accountRevision) return
    set({ works: mergeWorkByVersion(get().works, attachLocalDrafts(saved, get().works, drafts)) })
  },
  async saveSetting(work, input, existing) {
    const current = accountRevision
    let saved: Setting
    if (work.source !== 'server') {
      saved = { ...input, id: existing?.id || crypto.randomUUID() }
    } else if (existing) {
      saved = await settingClient.update(work.id, existing, input)
    } else {
      saved = await settingClient.create(work.id, input)
    }
    if (current !== accountRevision) throw new Error('로그인 정보가 변경되었습니다.')
    const latest = get().works.find((item) => item.id === work.id)
    if (!latest) throw new Error('작품을 다시 불러와 주세요.')
    const next = {
      ...latest,
      settings: existing
        ? latest.settings.map((item) => (item.id === saved.id ? saved : item))
        : [...latest.settings, saved],
    }
    if (work.source === 'server') set({ works: mergeWorkByVersion(get().works, next) })
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
    set({ works: mergeWorkByVersion(get().works, next) })
    if (next.source === 'server') drafts = mergeWorkByVersion(drafts, next)
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
