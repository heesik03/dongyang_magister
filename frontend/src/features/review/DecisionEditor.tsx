import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useWorksStore } from '../works/useWorksStore'
import {
  decisionDraftKey,
  readDecisionDraft,
  writeDecisionDraft,
  type DecisionDraft,
} from './decisionDraftStorage'
import { createPortal } from 'react-dom'
import { Dialog } from '../../shared/Dialog'
import {
  decisionClient,
  type DecisionRequest,
  type DecisionScope,
  type SavedDecision,
} from './decisionClient'
import type { LinkedReview } from './reviewClient'
import type { Decision, Work } from '../works/workTypes'

export function DecisionEditor({
  work,
  review,
  initialStatus,
  onSaved,
  onClose,
}: {
  work: Work
  review: LinkedReview
  initialStatus: Decision
  onSaved: () => void
  onClose: () => void
}) {
  const account = useWorksStore((state) => state.account)
  const [loaded, setLoaded] = useState<SavedDecision | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    void decisionClient
      .get(work.id, review.id, controller.signal)
      .then((decision) => {
        if (!controller.signal.aborted) {
          setLoaded(decision)
          setError('')
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setError(error instanceof Error ? error.message : '판단 조회 실패')
      })
    return () => controller.abort()
  }, [work.id, review.id, reload])
  // 검토 패널이 모바일에서 접혀도 열린 판단 폼과 작성 중인 내용은 유지한다.
  return createPortal(
    <Dialog
      title="작가 판단 기록"
      onClose={onClose}
      closeMessage="서버에 저장하지 않고 닫을까요? 이 탭의 판단 초안은 다시 복구할 수 있습니다."
      confirmCloseLabel="초안 보관하고 닫기"
    >
      {error && (
        <div role="alert">
          <p>{error}</p>
          <button onClick={() => setReload((value) => value + 1)}>판단 다시 조회</button>
        </div>
      )}
      {!loaded && !error && <p role="status">최신 판단을 확인하고 있습니다.</p>}
      {loaded && (
        <DecisionForm
          key={account}
          work={work}
          review={review}
          initial={loaded}
          initialStatus={initialStatus}
          onSaved={onSaved}
          onClose={onClose}
        />
      )}
    </Dialog>,
    document.body,
  )
}

function scopeFields(scope: DecisionScope): Omit<DecisionScope, 'id'> {
  return {
    scopeType: scope.scopeType,
    fromChapterId: scope.fromChapterId,
    toChapterId: scope.toChapterId,
    sceneSourceId: scope.sceneSourceId,
    startOffset: scope.startOffset,
    endOffset: scope.endOffset,
    conditionText: scope.conditionText,
  }
}
type DraftScope = Omit<DecisionScope, 'id'> & { key: string }
function DecisionForm({
  work,
  review,
  initial,
  initialStatus,
  onSaved,
  onClose,
}: {
  work: Work
  review: LinkedReview
  initial: SavedDecision
  initialStatus: Decision
  onSaved: () => void
  onClose: () => void
}) {
  const account = useWorksStore((state) => state.account)
  const draftKey = decisionDraftKey(account, work.id, review.id)
  const [recovery] = useState(() => {
    try {
      const draft = readDecisionDraft(draftKey)
      if (draft && draft.baseline.reviewId !== review.id) throw new Error('검토 식별자 불일치')
      return { draft, error: '' }
    } catch {
      return { draft: null, error: '판단 초안을 읽지 못했습니다. 저장소 원본은 유지됩니다.' }
    }
  })
  const [availableDraft, setAvailableDraft] = useState(recovery.draft)
  const [storageError, setStorageError] = useState(recovery.error)
  const [storageBlocked, setStorageBlocked] = useState(!!recovery.error)
  const [dirty, setDirty] = useState(false)
  const completed = useRef(false)
  const [baseline, setBaseline] = useState(initial)
  const [status, setStatus] = useState<Decision>(initialStatus)
  const [reason, setReason] = useState(initial.reason)
  function scene(): DraftScope {
    return {
      key: crypto.randomUUID(),
      scopeType: 'SCENE',
      fromChapterId: null,
      toChapterId: null,
      sceneSourceId: review.targetSourceId,
      startOffset: review.startOffset,
      endOffset: review.endOffset,
      conditionText: '',
    }
  }
  const [scopes, setScopes] = useState<DraftScope[]>(() =>
    initial.scopes.length
      ? initial.scopes.map((scope) => ({ ...scopeFields(scope), key: crypto.randomUUID() }))
      : [scene()],
  )
  const [reconfirm, setReconfirm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [comparison, setComparison] = useState<SavedDecision | null>(null)
  const [history, setHistory] = useState<{
    items: SavedDecision[]
    page: number
    totalPages: number
  } | null>(null)
  const pending = useRef<DecisionRequest | null>(null)
  const compareRequest = useRef<AbortController | null>(null)
  const historyRequest = useRef<AbortController | null>(null)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      compareRequest.current?.abort()
      historyRequest.current?.abort()
    }
  }, [])
  function persistDraft(request: DecisionRequest | null = pending.current) {
    try {
      writeDecisionDraft(draftKey, {
        baseline,
        status,
        reason,
        scopes: scopes.map(scopeFields),
        reconfirm,
        pending: request,
      })
      setStorageError('')
    } catch {
      setStorageError(
        '판단 초안 보관에 실패했습니다. 창을 닫기 전에 작성 내용을 별도로 보관해 주세요.',
      )
    }
  }
  useEffect(() => {
    if (dirty && !completed.current && !availableDraft && !storageBlocked) {
      try {
        writeDecisionDraft(draftKey, {
          baseline,
          status,
          reason,
          scopes: scopes.map(scopeFields),
          reconfirm,
          pending: pending.current,
        })
      } catch {
        // 렌더 중 입력은 유지하며 별도 상태로 저장소 실패를 알린다.
        queueMicrotask(() => {
          if (mounted.current)
            setStorageError('판단 초안 보관에 실패했습니다. 작성 내용을 별도로 보관해 주세요.')
        })
      }
    }
  }, [dirty, baseline, status, reason, scopes, reconfirm, draftKey, availableDraft, storageBlocked])
  function restore(draft: DecisionDraft) {
    setBaseline(draft.baseline)
    setStatus(draft.status)
    setReason(draft.reason)
    setScopes(draft.scopes.map((scope) => ({ ...scope, key: crypto.randomUUID() })))
    setReconfirm(draft.reconfirm)
    pending.current = draft.pending
    setDirty(true)
    setAvailableDraft(null)
    if (
      draft.baseline.version !== initial.version ||
      draft.baseline.basisHash !== initial.basisHash
    )
      setComparison(initial)
  }
  function changed() {
    setDirty(true)
    pending.current = null
    setError('')
  }
  function updateScope(key: string, value: Partial<DraftScope>) {
    changed()
    setScopes((scopes) =>
      scopes.map((scope) => (scope.key === key ? { ...scope, ...value } : scope)),
    )
  }
  async function save(event: FormEvent) {
    event.preventDefault()
    if (saving || storageBlocked || availableDraft) return
    const request: DecisionRequest = pending.current ?? {
      status,
      reason,
      expectedVersion: baseline.version,
      mutationId: crypto.randomUUID(),
      basisHash: baseline.basisHash,
      reconfirm,
      scopes:
        status === '미검토'
          ? []
          : scopes.map((scope) => ({
              ...scopeFields(scope),
              conditionText:
                scope.conditionText.trim() || (status !== '의도된 설정' ? '현재 검토 장면' : ''),
            })),
    }
    pending.current = request
    persistDraft(request)
    setSaving(true)
    setError('')
    try {
      await decisionClient.save(work.id, review.id, request)
      completed.current = true
      try {
        // 닫힌 폼의 응답이 새 폼에서 작성한 초안을 지우지 않도록 요청 소유권을 대조한다.
        const stored = readDecisionDraft(draftKey)
        if (stored?.pending?.mutationId === request.mutationId) sessionStorage.removeItem(draftKey)
      } catch {
        useWorksStore.setState({
          storageError: '판단은 서버에 저장했지만 이 탭의 초안 정리에 실패했습니다.',
        })
      }
      if (mounted.current) {
        onSaved()
        onClose()
      }
    } catch (error) {
      if (mounted.current) setError(error instanceof Error ? error.message : '판단 저장 실패')
    } finally {
      if (mounted.current) setSaving(false)
    }
  }
  async function compare() {
    compareRequest.current?.abort()
    const controller = new AbortController()
    compareRequest.current = controller
    try {
      const latest = await decisionClient.get(work.id, review.id, controller.signal)
      if (mounted.current && !controller.signal.aborted) setComparison(latest)
    } catch (error) {
      if (mounted.current && !controller.signal.aborted)
        setError(error instanceof Error ? error.message : '최신 판단 조회 실패')
    }
  }
  async function loadHistory(page: number) {
    historyRequest.current?.abort()
    const controller = new AbortController()
    historyRequest.current = controller
    try {
      const history = await decisionClient.history(work.id, review.id, page, controller.signal)
      if (mounted.current && !controller.signal.aborted) setHistory(history)
    } catch (error) {
      if (mounted.current && !controller.signal.aborted)
        setError(error instanceof Error ? error.message : '판단 이력 조회 실패')
    }
  }
  return (
    <form className="stack" onSubmit={save}>
      {availableDraft && (
        <div className="notice" role="status">
          <p>
            이 탭에서 보관한 판단 초안이 있습니다. 이전 저장 버전과 요청 식별자도 함께 복구합니다.
          </p>
          <button type="button" onClick={() => restore(availableDraft)}>
            판단 초안 복구
          </button>
          <button
            type="button"
            onClick={() => {
              try {
                sessionStorage.removeItem(draftKey)
                setAvailableDraft(null)
              } catch {
                setStorageError('초안을 삭제하지 못했습니다.')
              }
            }}
          >
            보관한 판단 초안 버리기
          </button>
        </div>
      )}
      {storageError && <p role="alert">{storageError}</p>}
      {storageBlocked && (
        <button
          type="button"
          onClick={() => {
            try {
              sessionStorage.removeItem(draftKey)
              setStorageBlocked(false)
              setStorageError('')
            } catch {
              setStorageError('초안 원본을 삭제하지 못했습니다.')
            }
          }}
        >
          읽을 수 없는 초안 원본 버리기
        </button>
      )}
      <p className="small muted">
        작성 내용은 이 탭의 임시 저장소에 보관됩니다. 탭을 닫거나 브라우저 자료를 지우면 복구할 수
        없습니다.
      </p>
      <blockquote style={{ overflowWrap: 'anywhere' }}>{review.quote}</blockquote>
      <p className="small muted">
        저장 기준 버전 {baseline.version}. 판단은 원고·설정을 자동으로 바꾸지 않습니다.
      </p>
      <label>
        판단 상태
        <select
          value={status}
          disabled={saving}
          onChange={(event) => {
            const value = event.target.value
            if (
              value === '수용' ||
              value === '보류' ||
              value === '의도된 설정' ||
              value === '미검토'
            ) {
              changed()
              setStatus(value)
            }
          }}
        >
          {['수용', '보류', '의도된 설정', '미검토'].map((status) => (
            <option key={status}>{status}</option>
          ))}
        </select>
      </label>
      <label>
        의도와 판단 사유
        <textarea
          rows={4}
          maxLength={4000}
          required={status === '의도된 설정'}
          value={reason}
          disabled={saving}
          onChange={(event) => {
            changed()
            setReason(event.target.value)
          }}
        />
      </label>
      {status !== '미검토' &&
        scopes.map((scope, index) => (
          <fieldset key={scope.key} disabled={saving}>
            <legend>적용 범위 {index + 1}</legend>
            <label>
              범위 유형
              <select
                value={scope.scopeType}
                onChange={(event) =>
                  updateScope(
                    scope.key,
                    event.target.value === 'SCENE'
                      ? { ...scene(), key: scope.key, conditionText: scope.conditionText }
                      : {
                          scopeType: 'CHAPTER_RANGE',
                          fromChapterId: work.chapters[0]?.id ?? null,
                          toChapterId: null,
                          sceneSourceId: null,
                          startOffset: null,
                          endOffset: null,
                        },
                  )
                }
              >
                <option value="SCENE">이 검토 장면만</option>
                <option value="CHAPTER_RANGE">회차 범위</option>
              </select>
            </label>
            {scope.scopeType === 'CHAPTER_RANGE' ? (
              <>
                <label>
                  시작 회차
                  <select
                    required
                    value={scope.fromChapterId ?? ''}
                    onChange={(event) =>
                      updateScope(scope.key, { fromChapterId: event.target.value })
                    }
                  >
                    {work.chapters.map((chapter) => (
                      <option key={chapter.id} value={chapter.id}>
                        {chapter.title}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  종료 회차
                  <select
                    value={scope.toChapterId ?? ''}
                    onChange={(event) =>
                      updateScope(scope.key, { toChapterId: event.target.value || null })
                    }
                  >
                    <option value="">이후 회차도 참고</option>
                    {work.chapters.map((chapter) => (
                      <option key={chapter.id} value={chapter.id}>
                        {chapter.title}
                      </option>
                    ))}
                  </select>
                </label>
              </>
            ) : (
              <p className="small">
                분석 당시 해당 장면의 위치 {review.startOffset}–{review.endOffset}에 한합니다.
              </p>
            )}
            <label>
              적용 조건
              <textarea
                rows={2}
                maxLength={2000}
                required={status === '의도된 설정'}
                value={scope.conditionText}
                placeholder="예: 봉인이 풀린 이후 주인공의 능력 변화에만 적용"
                onChange={(event) => updateScope(scope.key, { conditionText: event.target.value })}
              />
            </label>
            {scopes.length > 1 && (
              <button
                type="button"
                onClick={() => {
                  changed()
                  setScopes(scopes.filter((item) => item.key !== scope.key))
                }}
              >
                이 범위 삭제
              </button>
            )}
          </fieldset>
        ))}
      {status !== '미검토' && (
        <>
          <button
            type="button"
            disabled={saving || scopes.length >= 10}
            onClick={() => {
              changed()
              setScopes([...scopes, scene()])
            }}
          >
            적용 범위 추가
          </button>
          <p className="small muted">
            회차 범위 안에서도 조건·근거가 관련된 장면에만 참고하며 다른 충돌은 계속 검토합니다.
          </p>
        </>
      )}
      {baseline.stale && (
        <div className="notice" role="status">
          <p>이 판단의 근거가 변경되었습니다. 분석 당시 원문과 현재 자료를 비교해 주세요.</p>
          {baseline.canReconfirm ? (
            <label className="check">
              <input
                type="checkbox"
                checked={reconfirm}
                disabled={saving}
                onChange={(event) => {
                  changed()
                  setReconfirm(event.target.checked)
                }}
              />
              현재 근거를 다시 확인했으며 작성한 판단을 적용합니다.
            </label>
          ) : (
            <p>인용 위치 또는 확정 상태가 달라져 재분석이 필요합니다. 판단 철회는 가능합니다.</p>
          )}
        </div>
      )}
      {error && (
        <div role="alert">
          <p className="error">{error}</p>
          <button type="button" disabled={saving} onClick={() => void compare()}>
            최신 판단 비교
          </button>
        </div>
      )}
      {comparison && (
        <div className="notice">
          <strong>
            서버 최신 판단 · 버전 {comparison.version} · {comparison.status}
          </strong>
          <p style={{ whiteSpace: 'pre-wrap' }}>{comparison.reason || '사유 없음'}</p>
          {comparison.scopes.map((scope) => (
            <p key={scope.id}>
              {scope.scopeType === 'SCENE'
                ? '장면 범위'
                : `${scope.fromChapterTitle ?? '시작 회차'} ~ ${scope.toChapterTitle ?? '이후'}`}{' '}
              · {scope.conditionText}
            </p>
          ))}
          <p>현재 작성 내용은 유지됩니다. 서버 버전을 확인한 뒤 저장 기준을 바꿀 수 있습니다.</p>
          <button
            type="button"
            onClick={() => {
              changed()
              setBaseline(comparison)
              setComparison(null)
              setReconfirm(false)
            }}
          >
            작성 내용 유지하고 최신 버전을 기준으로 준비
          </button>
        </div>
      )}
      <button type="button" disabled={saving} onClick={() => void loadHistory(0)}>
        변경 이력 보기
      </button>
      {history && (
        <section aria-label="판단 변경 이력">
          {history.items.map((decision) => (
            <div className="notice small" key={decision.id}>
              <strong>
                버전 {decision.version} · {decision.status}
                {decision.stale ? ' · 근거 변경' : ''}
              </strong>
              <p>{decision.reason || '사유 없음'}</p>
              {decision.scopes.map((scope) => (
                <p key={scope.id}>
                  {scope.scopeType === 'SCENE'
                    ? '해당 장면'
                    : `${scope.fromChapterTitle ?? '시작 회차'} ~ ${scope.toChapterTitle ?? '이후'}`}{' '}
                  · {scope.conditionText}
                </p>
              ))}
            </div>
          ))}
          <button
            type="button"
            disabled={history.page === 0}
            onClick={() => void loadHistory(history.page - 1)}
          >
            이전 이력
          </button>
          <button
            type="button"
            disabled={history.page + 1 >= history.totalPages}
            onClick={() => void loadHistory(history.page + 1)}
          >
            다음 이력
          </button>
        </section>
      )}
      <div className="dialog-actions">
        <button
          className="primary"
          disabled={
            saving ||
            storageBlocked ||
            !!availableDraft ||
            (status !== '미검토' && (!baseline.canReconfirm || (baseline.stale && !reconfirm)))
          }
        >
          {saving ? '판단 저장 중…' : status === '미검토' ? '판단 철회 저장' : '판단 저장'}
        </button>
      </div>
    </form>
  )
}
