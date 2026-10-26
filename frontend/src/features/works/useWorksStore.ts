import { create } from 'zustand'
import { workClient, type WorkInput } from './workClient'
import { settingClient, type SettingInput } from '../settings/settingClient'
import { workspaceClient } from '../workspace/workspaceClient'
import { ApiError } from '../../shared/httpClient'
import { readStoredWorks, isWorks } from './workStorage'
import { mergeWorkByVersion, mergeSettingsByVersion } from './workCache'
import type { Work, Setting } from './workTypes'

interface SettingView {
  workId: string
  kind: string
  ids: string[]
  nextCursor: string | null
  hasNext: boolean
  loading: boolean
  error: string
}
interface WorksState {
  account: string | null
  works: Work[]
  listedIds: string[]
  shortcutIds: string[]
  shortcutError: string
  listPage: number
  totalPages: number
  totalElements: number
  loading: boolean
  listError: string
  detailLoading: boolean
  detailError: string
  storageError: string
  settingView: SettingView
  loadedIds: string[]
  dirtyIds: string[]
  savingIds: string[]
  conflicts: string[]
  workspaceErrors: Record<string, string>
  initialize: (email: string) => void
  reset: () => void
  loadWorks: (keyword?: string, genre?: string, page?: number) => Promise<void>
  loadShortcuts: () => Promise<void>
  loadWork: (id: string) => Promise<void>
  loadSettings: (id: string, kind?: string, cursor?: string) => Promise<void>
  createWork: (input: WorkInput) => Promise<Work>
  saveWorkInfo: (work: Work, input: WorkInput) => Promise<void>
  saveSetting: (work: Work, input: SettingInput, existing?: Setting) => Promise<Setting>
  updateWork: (work: Work) => void
  retrySave: () => void
  flushWorkspace: (id: string) => Promise<void>
  reloadWorkspace: (id: string) => Promise<void>
  handleRemoteSave: (id: string, version: number) => void
  recoverDraft: (id: string) => void
}
const emptyView: SettingView = {
  workId: '',
  kind: '전체',
  ids: [],
  nextCursor: null,
  hasNext: false,
  loading: false,
  error: '',
}
const initial = {
  account: null,
  works: [],
  listedIds: [],
  shortcutIds: [],
  shortcutError: '',
  listPage: 0,
  totalPages: 0,
  totalElements: 0,
  loading: false,
  listError: '',
  detailLoading: false,
  detailError: '',
  storageError: '',
  settingView: emptyView,
  loadedIds: [],
  dirtyIds: [],
  savingIds: [],
  conflicts: [],
  workspaceErrors: {},
}
let accountRevision = 0
let listRequest: AbortController | undefined,
  detailRequest: AbortController | undefined,
  settingRequest: AbortController | undefined
let legacyRaw: string | null = null
let legacyBlocked = false
let legacyDirty = false
let legacyDrafts: Work[] = []
const timers = new Map<string, ReturnType<typeof setTimeout>>()
const flights = new Map<string, Promise<void>>()
const mutations = new Map<string, { body: string; id: string; version: number }>()
const message = (error: unknown) =>
  error instanceof Error ? error.message : '요청에 실패했습니다. 다시 시도해 주세요.'
const contentOf = (work: Work) => ({
  chapters: work.chapters,
  analyses: work.analyses,
  events: work.events,
})
// 여러 화면이 공유하는 작품 데이터와 저장 버전만 관리한다. 폼·선택은 각 화면의 상태다.
export const useWorksStore = create<WorksState>((set, get) => ({
  ...initial,
  initialize(email) {
    get().reset()
    let works: Work[] = [],
      storageError = ''
    try {
      legacyRaw = localStorage.getItem('magister.works.v1:' + email)
      works = readStoredWorks('magister.works.v1:' + email).map((work) => ({
        ...work,
        source: 'local',
      }))
      legacyDrafts = readStoredWorks('magister.drafts.v1:' + email)
    } catch {
      legacyBlocked = true
      storageError = '기존 브라우저 데이터를 읽지 못했습니다. 원본을 보존합니다.'
    }
    set({
      ...initial,
      account: email,
      works,
      storageError,
      loadedIds: works.map((work) => work.id),
    })
  },
  reset() {
    accountRevision++
    listRequest?.abort()
    detailRequest?.abort()
    settingRequest?.abort()
    for (const timer of timers.values()) clearTimeout(timer)
    timers.clear()
    flights.clear()
    mutations.clear()
    legacyDrafts = []
    legacyBlocked = false
    legacyDirty = false
    legacyRaw = null
    set(initial)
  },
  async loadWorks(keyword = '', genre = '', page = 0) {
    const revision = accountRevision
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
      if (revision !== accountRevision || controller.signal.aborted) return
      const works = result.items.reduce((all, work) => {
        const cached = all.find((item) => item.id === work.id)
        return mergeWorkByVersion(
          all,
          cached
            ? {
                ...cached,
                ...work,
                ...contentOf(cached),
                settings: cached.settings,
                workspaceVersion: cached.workspaceVersion,
              }
            : work,
        )
      }, get().works)
      const ids = result.items.map((work) => work.id)
      set({
        works,
        listedIds: page === 0 ? ids : [...new Set([...get().listedIds, ...ids])],
        listPage: page,
        totalPages: result.totalPages,
        totalElements: result.totalElements,
        loading: false,
      })
    } catch (error) {
      if (revision === accountRevision && !controller.signal.aborted)
        set({ loading: false, listError: message(error) })
    }
  },
  async loadShortcuts() {
    const revision = accountRevision
    try {
      const result = await workClient.list()
      if (revision !== accountRevision) return
      const works = result.items.reduce((all, work) => {
        const cached = all.find((item) => item.id === work.id)
        return mergeWorkByVersion(
          all,
          cached
            ? {
                ...cached,
                ...work,
                ...contentOf(cached),
                settings: cached.settings,
                workspaceVersion: cached.workspaceVersion,
              }
            : work,
        )
      }, get().works)
      set({ works, shortcutIds: result.items.map((work) => work.id), shortcutError: '' })
    } catch (error) {
      if (revision === accountRevision) set({ shortcutError: message(error) })
    }
  },
  async loadWork(id) {
    detailRequest?.abort()
    set({ detailLoading: false, detailError: '' })
    if (
      get().works.some((work) => work.id === id && work.source !== 'server') ||
      get().dirtyIds.includes(id)
    )
      return
    const revision = accountRevision,
      controller = new AbortController()
    detailRequest = controller
    set({ detailLoading: true })
    try {
      const [work, settings, workspace] = await Promise.all([
        workClient.get(id, controller.signal),
        settingClient.listAll(id, controller.signal),
        workspaceClient.get(id, controller.signal),
      ])
      if (revision !== accountRevision || controller.signal.aborted) return
      const cached = get().works.find((item) => item.id === id)
      const full = {
        ...work,
        ...workspace.content,
        workspaceVersion: workspace.version,
        version: workspace.workVersion,
        settings: mergeSettingsByVersion(cached?.settings || [], settings),
      }
      set({
        works: mergeWorkByVersion(get().works, full),
        loadedIds: [...new Set([...get().loadedIds, id])],
        detailLoading: false,
      })
    } catch (error) {
      if (revision === accountRevision && !controller.signal.aborted)
        set({ detailLoading: false, detailError: message(error) })
    }
  },
  async loadSettings(id, kind = '전체', cursor) {
    settingRequest?.abort()
    if (get().works.some((work) => work.id === id && work.source !== 'server')) {
      set({ settingView: { ...emptyView, workId: id, kind } })
      return
    }
    const revision = accountRevision,
      controller = new AbortController()
    settingRequest = controller
    const previousView = get().settingView
    const append = cursor !== undefined && previousView.workId === id && previousView.kind === kind
    // 작품이나 필터가 바뀌면 이전 목록의 커서를 재사용하지 않는다.
    const requestedCursor = append ? cursor : undefined
    set({
      settingView: {
        ...(append ? previousView : emptyView),
        workId: id,
        kind,
        loading: true,
        error: '',
      },
    })
    try {
      const result = await settingClient.list(id, {
        kind,
        cursor: requestedCursor,
        signal: controller.signal,
      })
      if (revision !== accountRevision || controller.signal.aborted) return
      const ids = result.items.map((setting) => setting.id)
      set({
        works: get().works.map((work) =>
          work.id === id
            ? { ...work, settings: mergeSettingsByVersion(work.settings, result.items) }
            : work,
        ),
        settingView: {
          workId: id,
          kind,
          ids: append ? [...new Set([...previousView.ids, ...ids])] : ids,
          nextCursor: result.nextCursor,
          hasNext: result.hasNext,
          loading: false,
          error: '',
        },
      })
    } catch (error) {
      if (revision === accountRevision && !controller.signal.aborted)
        set({ settingView: { ...get().settingView, loading: false, error: message(error) } })
    }
  },
  async createWork(input) {
    const revision = accountRevision,
      work = await workClient.create(input)
    if (revision !== accountRevision)
      throw new Error('로그인 정보가 변경되었습니다. 다시 시도해 주세요.')
    set({
      works: mergeWorkByVersion(get().works, work),
      listedIds: [work.id, ...get().listedIds],
      shortcutIds: [work.id, ...get().shortcutIds],
      totalElements: get().totalElements + 1,
    })
    return work
  },
  async saveWorkInfo(work, input) {
    if (work.source !== 'server') {
      get().updateWork({ ...work, ...input })
      return
    }
    const revision = accountRevision,
      saved = await workClient.update(work.id, input, work.version ?? 0)
    if (revision !== accountRevision) return
    const latest = get().works.find((item) => item.id === work.id)
    if (latest)
      set({
        works: mergeWorkByVersion(get().works, {
          ...latest,
          ...saved,
          ...contentOf(latest),
          settings: latest.settings,
          workspaceVersion: latest.workspaceVersion,
        }),
      })
  },
  async saveSetting(work, input, existing) {
    const revision = accountRevision
    let saved: Setting
    if (work.source !== 'server') saved = { ...input, id: existing?.id || crypto.randomUUID() }
    else if (existing) saved = await settingClient.update(work.id, existing, input)
    else saved = await settingClient.create(work.id, input)
    if (revision !== accountRevision) throw new Error('로그인 정보가 변경되었습니다.')
    const latest = get().works.find((item) => item.id === work.id)
    if (!latest) throw new Error('작품을 다시 불러와 주세요.')
    const next = { ...latest, settings: mergeSettingsByVersion(latest.settings, [saved]) }
    if (work.source === 'server') {
      set({ works: mergeWorkByVersion(get().works, next) })
      try {
        const metadata = await workClient.get(work.id)
        if (revision === accountRevision) {
          const current = get().works.find((item) => item.id === work.id)
          if (current)
            set({
              works: mergeWorkByVersion(get().works, {
                ...current,
                ...metadata,
                ...contentOf(current),
                settings: current.settings,
                workspaceVersion: current.workspaceVersion,
              }),
            })
        }
      } catch (error) {
        if (revision === accountRevision)
          set({
            listError: '설정은 저장되었지만 작품 정보를 갱신하지 못했습니다. ' + message(error),
          })
      }
    } else get().updateWork(next)
    return saved
  },
  updateWork(work) {
    const current = get().works.find((item) => item.id === work.id)
    if (!current) return
    const next =
      current.source === 'server'
        ? { ...current, ...contentOf(work) }
        : { ...work, updatedAt: new Date().toISOString() }
    set({ works: mergeWorkByVersion(get().works, next) })
    if (next.source === 'server') {
      set({ dirtyIds: [...new Set([...get().dirtyIds, next.id])] })
      clearTimeout(timers.get(next.id))
      timers.set(
        next.id,
        setTimeout(() => void get().flushWorkspace(next.id), 600),
      )
    } else {
      legacyDirty = true
      clearTimeout(timers.get('legacy'))
      timers.set(
        'legacy',
        setTimeout(() => get().retrySave(), 600),
      )
    }
  },
  async flushWorkspace(id) {
    clearTimeout(timers.get(id))
    if (flights.has(id)) return flights.get(id)
    if (!get().dirtyIds.includes(id) || get().conflicts.includes(id)) return
    const revision = accountRevision,
      account = get().account
    const task = (async () => {
      const work = get().works.find((item) => item.id === id)
      if (!work) return
      const content = contentOf(work),
        body = JSON.stringify(content),
        previous = mutations.get(id)
      const mutation =
        previous?.body === body
          ? previous
          : { body, id: crypto.randomUUID(), version: work.workspaceVersion ?? 0 }
      mutations.set(id, mutation)
      set({
        savingIds: [...get().savingIds, id],
        workspaceErrors: { ...get().workspaceErrors, [id]: '' },
      })
      try {
        const response = await workspaceClient.save(id, mutation.version, mutation.id, content)
        if (revision !== accountRevision) return
        const latest = get().works.find((item) => item.id === id)
        if (!latest) return
        const unchanged =
          latest.chapters === work.chapters &&
          latest.analyses === work.analyses &&
          latest.events === work.events
        set({
          works: get().works.map((item) =>
            item.id === id
              ? {
                  ...item,
                  workspaceVersion: response.version,
                  version: response.workVersion,
                  updatedAt: response.updatedAt,
                }
              : item,
          ),
          dirtyIds: unchanged ? get().dirtyIds.filter((key) => key !== id) : get().dirtyIds,
        })
        mutations.delete(id)
        try {
          sessionStorage.removeItem('magister.recovery:' + account + ':' + id)
          localStorage.setItem(
            'magister.workspace-saved:' + account,
            JSON.stringify({ id, version: response.version }),
          )
        } catch {
          set({
            storageError: '브라우저 복구 정보 갱신에 실패했습니다. 서버 저장은 완료되었습니다.',
          })
        }
      } catch (error) {
        if (revision !== accountRevision) return
        try {
          sessionStorage.setItem('magister.recovery:' + account + ':' + id, JSON.stringify(work))
        } catch {
          set({ storageError: '복구 파일을 내려받아 입력을 보존해 주세요.' })
        }
        set({
          workspaceErrors: { ...get().workspaceErrors, [id]: message(error) },
          conflicts:
            error instanceof ApiError && error.status === 409
              ? [...new Set([...get().conflicts, id])]
              : get().conflicts,
        })
      } finally {
        if (revision === accountRevision)
          set({ savingIds: get().savingIds.filter((key) => key !== id) })
      }
    })()
    flights.set(id, task)
    await task
    if (revision !== accountRevision) return
    flights.delete(id)
    if (get().dirtyIds.includes(id) && !get().workspaceErrors[id] && !get().conflicts.includes(id))
      await get().flushWorkspace(id)
  },
  async reloadWorkspace(id) {
    const revision = accountRevision,
      response = await workspaceClient.get(id)
    if (revision !== accountRevision) return
    set({
      works: get().works.map((work) =>
        work.id === id
          ? {
              ...work,
              ...response.content,
              workspaceVersion: response.version,
              version: response.workVersion,
              updatedAt: response.updatedAt,
            }
          : work,
      ),
      dirtyIds: get().dirtyIds.filter((key) => key !== id),
      conflicts: get().conflicts.filter((key) => key !== id),
      workspaceErrors: { ...get().workspaceErrors, [id]: '' },
    })
    mutations.delete(id)
  },
  handleRemoteSave(id, version) {
    const work = get().works.find((item) => item.id === id)
    if (!work || version <= (work.workspaceVersion ?? 0)) return
    if (get().dirtyIds.includes(id)) {
      clearTimeout(timers.get(id))
      set({
        conflicts: [...new Set([...get().conflicts, id])],
        workspaceErrors: {
          ...get().workspaceErrors,
          [id]: '다른 탭에서 새 버전을 저장했습니다. 현재 입력은 유지됩니다.',
        },
      })
    } else
      void get()
        .reloadWorkspace(id)
        .catch((error) =>
          set({ workspaceErrors: { ...get().workspaceErrors, [id]: message(error) } }),
        )
  },
  recoverDraft(id) {
    const state = get(),
      work = state.works.find((item) => item.id === id)
    if (!work) return
    try {
      const raw = sessionStorage.getItem('magister.recovery:' + state.account + ':' + id)
      if (raw) {
        const candidate: unknown = [JSON.parse(raw)]
        if (!isWorks(candidate)) throw new Error('복구본 형식이 올바르지 않습니다.')
        const draft = candidate[0]
        if (draft.id !== id) throw new Error('다른 작품의 복구본입니다.')
        const base = draft.workspaceVersion ?? 0
        set({
          works: get().works.map((item) =>
            item.id === id ? { ...item, workspaceVersion: base } : item,
          ),
        })
        get().updateWork({ ...work, ...contentOf(draft) })
        if (base !== (work.workspaceVersion ?? 0))
          get().handleRemoteSave(id, work.workspaceVersion ?? 0)
        return
      }
      const draft = legacyDrafts.find((item) => item.id === id)
      if (draft && !work.chapters.length && !work.analyses.length && !work.events.length)
        get().updateWork({ ...work, ...contentOf(draft) })
      else
        set({
          workspaceErrors: {
            ...get().workspaceErrors,
            [id]: '가져올 초안이 없거나 서버 문서가 이미 작성되어 있습니다.',
          },
        })
    } catch (error) {
      set({ workspaceErrors: { ...get().workspaceErrors, [id]: message(error) } })
    }
  },
  retrySave() {
    for (const id of get().dirtyIds) void get().flushWorkspace(id)
    const account = get().account,
      revision = accountRevision
    if (!account || legacyBlocked || !legacyDirty) return
    const key = 'magister.works.v1:' + account
    const persist = () => {
      if (revision !== accountRevision) return
      if (localStorage.getItem(key) !== legacyRaw) {
        set({
          storageError: '다른 탭이 브라우저 작품을 변경했습니다. 현재 입력은 화면에 유지됩니다.',
        })
        return
      }
      const next = JSON.stringify(get().works.filter((work) => work.source !== 'server'))
      localStorage.setItem(key, next)
      legacyRaw = next
      legacyDirty = false
      set({ storageError: '' })
    }
    try {
      // 서버 연결 전의 브라우저 작품도 탭 간 쓰기를 직렬화한다.
      if (!navigator.locks) {
        set({
          storageError:
            '이 브라우저는 안전한 다중 탭 저장을 지원하지 않습니다. 원고를 파일로 보관해 주세요.',
        })
        return
      }
      void navigator.locks.request(key, persist).catch(() => {
        if (revision === accountRevision)
          set({ storageError: '브라우저 저장에 실패했습니다. 입력은 화면에 유지됩니다.' })
      })
    } catch {
      set({ storageError: '브라우저 저장에 실패했습니다. 입력은 화면에 유지됩니다.' })
    }
  },
}))
