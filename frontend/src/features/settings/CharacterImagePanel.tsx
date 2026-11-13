import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Dialog } from '../../shared/Dialog'
import { Icon } from '../../shared/Icon'
import { ApiError } from '../../shared/httpClient'
import type { Setting } from '../works/workTypes'
import {
  characterImageClient,
  type PortraitInput,
  type PortraitStatus,
} from './characterImageClient'

type Gallery = Awaited<ReturnType<typeof characterImageClient.list>>
type Preview = Awaited<ReturnType<typeof characterImageClient.preview>> & {
  input: PortraitInput
  requestId: string
}

const statusLabels: Record<PortraitStatus, string> = {
  QUEUED: '대기 중',
  RUNNING: '생성 중',
  SUCCEEDED: '생성 완료',
  FAILED: '생성 실패',
}

function Portrait({
  workId,
  settingId,
  id,
  label,
}: {
  workId: string
  settingId: string
  id: string
  label: string
}) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl = ''
    void characterImageClient
      .content(workId, settingId, id, controller.signal)
      .then((blob) => {
        if (controller.signal.aborted) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true)
      })
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [workId, settingId, id, retry])
  if (error)
    return (
      <button
        onClick={() => {
          setError(false)
          setRetry((value) => value + 1)
        }}
      >
        이미지 다시 불러오기
      </button>
    )
  if (!url) return <p role="status">이미지를 불러오는 중입니다.</p>
  return <img src={url} alt={label} className="character-portrait" />
}

export function CharacterImagePanel({ workId, setting }: { workId: string; setting: Setting }) {
  const { id: settingId } = setting
  const [gallery, setGallery] = useState<Gallery | null>(null)
  const [reload, setReload] = useState(0)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState<PortraitInput>({ appearance: '', direction: '' })
  const [preview, setPreview] = useState<Preview | null>(null)
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [galleryOpen, setGalleryOpen] = useState(false)
  const activeAction = useRef(false)
  const primary = gallery?.jobs.find((job) => job.primary)

  useEffect(() => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    async function load() {
      try {
        const result = await characterImageClient.list(workId, settingId, controller.signal)
        if (controller.signal.aborted) return
        setGallery(result)
        setLoadError('')
        if (result.jobs.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING')) {
          timer = setTimeout(() => void load(), 2500)
        } else if (result.remaining === 0) {
          timer = setTimeout(
            () => void load(),
            Math.max(1000, Date.parse(result.resetAt) - Date.now()),
          )
        }
      } catch {
        if (!controller.signal.aborted) setLoadError('인물 이미지 목록을 불러오지 못했습니다.')
      }
    }
    void load()
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [workId, settingId, reload])

  function refresh() {
    setReload((value) => value + 1)
  }

  async function run(action: () => Promise<void>) {
    if (activeAction.current) return
    activeAction.current = true
    setBusy(true)
    setActionError('')
    try {
      await action()
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : '요청을 처리하지 못했습니다.')
      if (failure instanceof ApiError && (failure.status === 409 || failure.status === 429)) {
        setPreview(null)
        setConsent(false)
        refresh()
      }
    } finally {
      activeAction.current = false
      setBusy(false)
    }
  }

  function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const payload = { appearance: input.appearance.trim(), direction: input.direction.trim() }
    if (!payload.appearance) return
    void run(async () => {
      const result = await characterImageClient.preview(workId, settingId, payload)
      setPreview({ ...result, input: payload, requestId: crypto.randomUUID() })
      setConsent(false)
    })
  }

  function close() {
    if (busy) return
    setOpen(false)
    setPreview(null)
    setConsent(false)
    setActionError('')
  }

  return (
    <section className="character-images" aria-label="인물 이미지">
      <div className="character-image-actions">
        <h3>
          <Icon name="grid" />
          인물 이미지
        </h3>
        <button
          className="primary"
          disabled={
            !gallery?.enabled || gallery.remaining === 0 || gallery.jobs.length >= gallery.capacity
          }
          onClick={() => {
            setActionError('')
            const appearance = setting.content.match(
              /^\s*(?:[-*]\s+)?(?:\*\*)?외형(?:\*\*)?\s*[:：]\s*(?:\*\*)?([^\r\n]+)/m,
            )?.[1]
            setInput({ appearance: appearance?.trim().slice(0, 1500) ?? '', direction: '' })
            setOpen(true)
          }}
        >
          <Icon name="grid" />
          인물 이미지 만들기
        </button>
      </div>
      {loadError ? (
        <p role="alert">
          {loadError} <button onClick={refresh}>다시 불러오기</button>
        </p>
      ) : (
        !gallery && <p role="status">시안을 불러오는 중입니다.</p>
      )}
      {gallery && (
        <div className="character-image-actions small muted">
          <span>
            오늘 {gallery.remaining}회 남음 · 시안 {gallery.jobs.length}/{gallery.capacity}개
          </span>
          {!gallery.enabled && <span>이미지 모델을 사용할 수 없습니다.</span>}
          {gallery.remaining === 0 && <span>한국 시간 자정에 한도가 초기화됩니다.</span>}
        </div>
      )}
      {gallery && !primary && (
        <div className="character-image-empty">
          <Icon name="grid" />
          <strong>대표 이미지가 없습니다</strong>
          <span className="small muted">생성한 시안에서 대표 이미지를 지정할 수 있습니다.</span>
        </div>
      )}
      {primary && (
        <figure className="character-image-card">
          <Portrait
            key={primary.id}
            workId={workId}
            settingId={settingId}
            id={primary.id}
            label={setting.title + ' 대표 이미지'}
          />
          <figcaption>
            대표 이미지 {primary.settingVersion !== setting.version && '· 이전 설정 버전'}
          </figcaption>
          <button
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await characterImageClient.primary(workId, settingId, null)
                refresh()
              })
            }
          >
            대표 지정 해제
          </button>
        </figure>
      )}
      {gallery?.jobs.length === 0 && <p className="muted">아직 생성한 시안이 없습니다.</p>}
      {gallery && gallery.jobs.length > 0 && (
        <details
          className="workspace-disclosure"
          open={galleryOpen}
          onToggle={(event) => setGalleryOpen(event.currentTarget.open)}
        >
          <summary>최근 시안 {gallery.jobs.length}개</summary>
          {galleryOpen && (
            <ul className="character-image-gallery">
              {gallery.jobs.map((job) => (
                <li className="character-image-card" key={job.id}>
                  <span className="badge" role="status">
                    {statusLabels[job.status]}
                    {job.primary && ' · 대표'}
                  </span>
                  {job.settingVersion !== setting.version && (
                    <span className="small muted">이전 설정 버전</span>
                  )}
                  {job.status === 'SUCCEEDED' && (
                    <>
                      {!job.primary && (
                        <Portrait
                          workId={workId}
                          settingId={settingId}
                          id={job.id}
                          label={setting.title + ' 시안'}
                        />
                      )}
                      {!job.primary && (
                        <button
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await characterImageClient.primary(workId, settingId, job.id)
                              refresh()
                            })
                          }
                        >
                          대표 이미지로 지정
                        </button>
                      )}
                    </>
                  )}
                  {(job.status === 'FAILED' || job.status === 'SUCCEEDED') && (
                    <button
                      disabled={busy}
                      onClick={() => {
                        setActionError('')
                        setDeleteId(job.id)
                      }}
                    >
                      시안 삭제
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </details>
      )}
      {actionError && !open && !deleteId && (
        <p role="alert" className="error">
          {actionError}
        </p>
      )}
      {open && (
        <Dialog title="인물 이미지 만들기" onClose={close}>
          {actionError && (
            <p role="alert" className="error">
              {actionError}
            </p>
          )}
          {preview ? (
            <div className="stack">
              <section aria-label="전송할 문구">
                <h3>외부 AI에 전송할 내용</h3>
                <pre className="character-image-prompt">{preview.prompt}</pre>
              </section>
              <label className="check">
                <input
                  type="checkbox"
                  checked={consent}
                  onChange={(event) => setConsent(event.target.checked)}
                />
                표시된 내용의 외부 AI 전송에 동의합니다.
              </label>
              <div className="dialog-actions">
                <button
                  disabled={busy}
                  onClick={() => {
                    setPreview(null)
                    setConsent(false)
                  }}
                >
                  내용 수정
                </button>
                <button
                  className="primary"
                  disabled={!consent || busy}
                  onClick={() =>
                    void run(async () => {
                      await characterImageClient.create(
                        workId,
                        settingId,
                        preview.input,
                        preview.token,
                        preview.requestId,
                      )
                      setOpen(false)
                      setGalleryOpen(true)
                      setPreview(null)
                      setConsent(false)
                      refresh()
                    })
                  }
                >
                  동의하고 생성
                </button>
              </div>
            </div>
          ) : (
            <form className="stack" onSubmit={prepare}>
              <details className="workspace-disclosure">
                <summary>저장된 인물 설정 보기</summary>
                <pre className="character-image-prompt">{setting.content}</pre>
              </details>
              <label>
                외형 설명
                <textarea
                  required
                  maxLength={1500}
                  rows={5}
                  value={input.appearance}
                  onChange={(event) => setInput({ ...input, appearance: event.target.value })}
                  placeholder="저장된 설정에서 외형을 확인하고 필요한 내용만 입력하세요."
                />
              </label>
              <label>
                연출 (선택)
                <textarea
                  maxLength={500}
                  rows={3}
                  value={input.direction}
                  onChange={(event) => setInput({ ...input, direction: event.target.value })}
                  placeholder="의상, 표정, 화풍 등"
                />
              </label>
              <div className="dialog-actions">
                <button type="button" disabled={busy} onClick={close}>
                  취소
                </button>
                <button className="primary" disabled={busy || !input.appearance.trim()}>
                  전송 내용 확인
                </button>
              </div>
            </form>
          )}
        </Dialog>
      )}
      {deleteId && (
        <Dialog
          title="시안 삭제"
          onClose={() => {
            if (!busy) setDeleteId(null)
          }}
        >
          <p>선택한 시안을 삭제합니다. 대표 시안이라면 대표 이미지도 해제됩니다.</p>
          {actionError && (
            <p role="alert" className="error">
              {actionError}
            </p>
          )}
          <div className="dialog-actions">
            <button disabled={busy} onClick={() => setDeleteId(null)}>
              취소
            </button>
            <button
              disabled={busy}
              className="danger"
              onClick={() =>
                void run(async () => {
                  await characterImageClient.delete(workId, settingId, deleteId)
                  setDeleteId(null)
                  refresh()
                })
              }
            >
              삭제
            </button>
          </div>
        </Dialog>
      )}
    </section>
  )
}
