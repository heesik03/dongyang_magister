import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../shared/httpClient'
import {
  sceneBoardClient,
  type SceneImageInput,
  type SceneImageJob,
  type SceneImagePreview,
} from './sceneBoardClient'

export function SceneImagePanel({ workId, boardId }: { workId: string; boardId: string }) {
  const [input, setInput] = useState<SceneImageInput>({
    kind: 'LOCATION',
    confirmed: '',
    direction: '',
  })
  const [preview, setPreview] = useState<SceneImagePreview | null>(null)
  const [quota, setQuota] = useState<Awaited<ReturnType<typeof sceneBoardClient.usage>> | null>(
    null,
  )
  const [jobs, setJobs] = useState<SceneImageJob[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [image, setImage] = useState<{ id: string; url: string } | null>(null)
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const requestId = useRef('')
  const active = jobs.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING')
  const selected = jobs.find((job) => job.id === selectedId)
  const imageId = selected?.status === 'SUCCEEDED' ? selected.id : undefined

  useEffect(() => {
    const controller = new AbortController()
    void sceneBoardClient
      .listImages(workId, boardId, controller.signal)
      .then((nextJobs) => {
        if (!controller.signal.aborted) setJobs(nextJobs)
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setError(failure instanceof Error ? failure.message : '시안 이력을 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [workId, boardId, refresh])

  useEffect(() => {
    if (!active || error) return
    const controller = new AbortController()
    let timer: number
    function poll() {
      timer = window.setTimeout(() => {
        void sceneBoardClient
          .listImages(workId, boardId, controller.signal)
          .then((nextJobs) => {
            if (controller.signal.aborted) return
            setJobs(nextJobs)
            if (nextJobs.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING')) {
              poll()
            }
          })
          .catch((failure) => {
            if (!controller.signal.aborted)
              setError(
                failure instanceof Error ? failure.message : '시안 이력을 불러오지 못했습니다.',
              )
          })
      }, 3000)
    }
    poll()
    return () => {
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [workId, boardId, active, error])

  useEffect(() => {
    if (!imageId) return
    const controller = new AbortController()
    let url: string | undefined
    void sceneBoardClient
      .imageContent(workId, boardId, imageId, controller.signal)
      .then((file) => {
        if (controller.signal.aborted) return
        url = URL.createObjectURL(file)
        setImage({ id: imageId, url })
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('이미지를 불러오지 못했습니다.')
      })
    return () => {
      controller.abort()
      if (url) URL.revokeObjectURL(url)
    }
  }, [workId, boardId, imageId])

  async function prepare() {
    setBusy(true)
    setError('')
    try {
      const [nextPreview, usage] = await Promise.all([
        sceneBoardClient.previewImage(workId, boardId, input),
        sceneBoardClient.usage('SCENE_IMAGE'),
      ])
      setPreview(nextPreview)
      setQuota(usage)
      setConsent(false)
      requestId.current = crypto.randomUUID()
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : '이미지 전송 자료를 확인하지 못했습니다.',
      )
    } finally {
      setBusy(false)
    }
  }

  async function submit() {
    if (!preview || !consent || busy) return
    setBusy(true)
    setError('')
    try {
      const job = await sceneBoardClient.createImage(
        workId,
        boardId,
        preview.input,
        preview.token,
        requestId.current,
      )
      setJobs((previous) => [job, ...previous.filter((item) => item.id !== job.id)])
      setSelectedId(job.id)
      setPreview(null)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '이미지 생성 요청에 실패했습니다.')
      if (failure instanceof ApiError && failure.status === 409) {
        setPreview(null)
        setConsent(false)
      }
      if (failure instanceof ApiError && failure.status === 429) {
        await sceneBoardClient
          .usage('SCENE_IMAGE')
          .then(setQuota)
          .catch(() => undefined)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="콘셉트 이미지" className="scene-image-panel">
      {error && (
        <p role="alert">
          {error}{' '}
          <button
            type="button"
            onClick={() => {
              setError('')
              setRefresh((value) => value + 1)
            }}
          >
            시안 이력 다시 확인
          </button>
        </p>
      )}
      {preview ? (
        <>
          <h4>이미지 생성기에 보낼 설명 · 원고 v{preview.revision}</h4>
          <p>원고·설정에서 작가가 확인한 요소</p>
          <blockquote>{preview.input.confirmed}</blockquote>
          <p>작가가 추가한 연출</p>
          <blockquote>{preview.input.direction || '없음'}</blockquote>
          {quota && (
            <p>
              오늘 이미지 생성 {quota.remaining}회 남음 · 초기화{' '}
              {new Date(quota.resetAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} (KST)
            </p>
          )}
          <label>
            <input
              type="checkbox"
              checked={consent}
              onChange={(event) => setConsent(event.target.checked)}
            />{' '}
            표시된 설명의 외부 AI 전송에 동의합니다.
          </label>
          <div>
            <button type="button" disabled={busy} onClick={() => setPreview(null)}>
              돌아가기
            </button>
            <button
              type="button"
              disabled={busy || !consent || quota?.remaining === 0}
              onClick={() => void submit()}
            >
              동의하고 이미지 생성
            </button>
          </div>
        </>
      ) : (
        <fieldset disabled={busy}>
          <legend>새 시안 만들기</legend>
          <label>
            이미지 유형
            <select
              value={input.kind}
              onChange={(event) =>
                setInput({ ...input, kind: event.target.value === 'PROP' ? 'PROP' : 'LOCATION' })
              }
            >
              <option value="LOCATION">장소 전경</option>
              <option value="PROP">소품 확대</option>
            </select>
          </label>
          <label>
            원고·설정에서 확인한 장소·시간·소품
            <textarea
              maxLength={500}
              value={input.confirmed}
              onChange={(event) => setInput({ ...input, confirmed: event.target.value })}
            />
          </label>
          <label>
            작가가 추가한 연출
            <textarea
              maxLength={500}
              value={input.direction}
              onChange={(event) => setInput({ ...input, direction: event.target.value })}
            />
          </label>
          <button type="button" disabled={!input.confirmed.trim()} onClick={() => void prepare()}>
            전송 설명 확인
          </button>
        </fieldset>
      )}
      {jobs.length > 0 && (
        <label>
          생성 시안
          <select value={selectedId} onChange={(event) => setSelectedId(event.target.value)}>
            <option value="">시안 선택</option>
            {jobs.map((job, index) => (
              <option key={job.id} value={job.id}>
                {job.kind === 'PROP' ? '소품 확대' : '장소 전경'} · v{job.revision} ·{' '}
                {jobs.length - index}
              </option>
            ))}
          </select>
        </label>
      )}
      {selected && (
        <article>
          <h4>생성 기준 · 원고 v{selected.revision}</h4>
          <p>확인한 요소 · {selected.confirmed}</p>
          <p>추가 연출 · {selected.direction || '없음'}</p>
          {(selected.status === 'QUEUED' || selected.status === 'RUNNING') && (
            <p role="status">이미지를 생성하고 있습니다.</p>
          )}
          {selected.status === 'FAILED' && (
            <p role="alert">
              이미지 생성에 실패했습니다.{' '}
              <button
                type="button"
                disabled={busy}
                onClick={() => {
                  setInput({
                    kind: selected.kind === 'PROP' ? 'PROP' : 'LOCATION',
                    confirmed: selected.confirmed,
                    direction: selected.direction,
                  })
                  setPreview(null)
                }}
              >
                설명 불러와 다시 요청
              </button>
            </p>
          )}
          {imageId && image?.id === imageId && (
            <img
              src={image.url}
              alt={selected.kind === 'PROP' ? '소품 콘셉트 시안' : '장소 콘셉트 시안'}
            />
          )}
        </article>
      )}
    </section>
  )
}
