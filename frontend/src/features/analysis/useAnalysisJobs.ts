import { useCallback, useEffect, useState } from 'react'
import { analysisClient, isActiveJob, type Job, type JobSummary } from './analysisClient'

// 호출 화면은 작품·회차를 key로 상태를 분리한다. 화면 이동 시 조회만 멈추고 서버 작업은 유지한다.
export function useAnalysisJobs(workId: string, chapterId: string | undefined, enabled: boolean) {
  const [listState, setListState] = useState<{
    key: string
    items: JobSummary[]
    totalPages: number
    error: string
  } | null>(null)
  const [storedJob, setJob] = useState<Job | null>(null)
  const [page, setPage] = useState(0)
  const [pollError, setPollError] = useState('')
  const [selection, setSelection] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const context = workId + ':' + chapterId
  const listKey = context + ':' + page + ':' + refreshKey
  const currentList = listState?.key === listKey ? listState : null
  const job = storedJob?.id === selection ? storedJob : null
  useEffect(() => {
    if (!enabled || !chapterId) return
    const controller = new AbortController()
    void analysisClient
      .list(workId, chapterId, page, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setListState({
          key: listKey,
          items: result.items,
          totalPages: result.totalPages,
          error: '',
        })
        setSelection((previous) =>
          previous && result.items.some((item) => item.id === previous)
            ? previous
            : (result.items[0]?.id ?? null),
        )
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setListState({
            key: listKey,
            items: [],
            totalPages: 0,
            error: error instanceof Error ? error.message : '분석 이력 조회 실패',
          })
      })
    return () => controller.abort()
  }, [workId, chapterId, enabled, page, listKey])
  useEffect(() => {
    if (!enabled || !selection) return
    const selectedId = selection
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    async function poll() {
      try {
        const result = await analysisClient.get(workId, selectedId, controller.signal)
        if (controller.signal.aborted) return
        setJob(result)
        setPollError('')
        setListState((previous) =>
          previous
            ? {
                ...previous,
                items: previous.items.map((item) =>
                  item.id === result.id ? { ...item, status: result.status } : item,
                ),
              }
            : previous,
        )
        if (isActiveJob(result)) timer = setTimeout(() => void poll(), 1000)
      } catch (error) {
        if (!controller.signal.aborted)
          setPollError(error instanceof Error ? error.message : '분석 상태 조회 실패')
      }
    }
    void poll()
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [workId, enabled, selection, refreshKey])
  const refresh = useCallback(() => {
    setPollError('')
    setRefreshKey((value) => value + 1)
  }, [])
  function select(id: string) {
    setPollError('')
    setSelection(id)
  }
  function accept(created: Job, requestContext: string) {
    if (context !== requestContext) return
    setPage(0)
    setJob(created)
    setSelection(created.id)
    refresh()
  }
  return {
    history: currentList?.items ?? [],
    job,
    page,
    totalPages: currentList?.totalPages ?? 0,
    loading: enabled && !!chapterId && !currentList,
    error: currentList?.error || pollError,
    refresh,
    select,
    changePage: (page: number) => {
      setPollError('')
      setSelection(null)
      setPage(page)
    },
    accept,
    active: !!job && isActiveJob(job),
    context,
  }
}
