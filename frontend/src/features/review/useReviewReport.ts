import { useEffect, useState } from 'react'
import { reviewClient, type ReviewReport } from './reviewClient'

export function useReviewReport(workId: string, jobId: string | undefined, completed: boolean) {
  const [state, setState] = useState<{ id: string; report?: ReviewReport; error: string } | null>(
    null,
  )
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    if (!jobId || !completed) return
    const id = jobId
    const controller = new AbortController()
    void reviewClient
      .list(workId, id, controller.signal)
      .then((report) => {
        if (!controller.signal.aborted) setState({ id, report, error: '' })
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setState({ id, error: error instanceof Error ? error.message : '검토 근거 조회 실패' })
      })
    return () => controller.abort()
  }, [workId, jobId, completed, refresh])
  const current = state?.id === jobId && completed ? state : null
  return {
    report: current?.report,
    error: current?.error ?? '',
    loading: !!jobId && completed && !current,
    retry: () => setRefresh((value) => value + 1),
  }
}
