import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Dialog } from '../../shared/Dialog'
import { PasswordInput } from '../auth/PasswordInput'
import { useAuthStore } from '../auth/useAuthStore'
import type { User } from '../auth/authTypes'
import { useWorksStore } from '../works/useWorksStore'
import { accountClient, purgeAccountBrowserStorage, type UserActivity } from './accountClient'

interface WithdrawAccountDialogProps {
  user: User
  activity: UserActivity | null
  onClose: () => void
}

export function WithdrawAccountDialog({
  user,
  activity,
  onClose,
}: WithdrawAccountDialogProps) {
  const navigate = useNavigate()
  const dirtyIds = useWorksStore((state) => state.dirtyIds)
  const [currentPassword, setCurrentPassword] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const workCount = activity?.workCount ?? 0
  const chapterCount = activity?.chapterCount ?? 0
  const canSubmit = !busy && currentPassword.trim().length > 0 && agreed

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    setBusy(true)
    setError('')
    try {
      await accountClient.withdraw({
        currentPassword,
        agreed,
      })
      purgeAccountBrowserStorage(user.email)
      useAuthStore.getState().expire()
      onClose()
      navigate('/login', { replace: true })
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : '회원 탈퇴를 처리하지 못했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      title="정말로 탈퇴하시겠습니까?"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <form className="stack mypage-withdraw-form" onSubmit={(event) => void handleSubmit(event)}>
        <div className="mypage-danger-badge-row">
          <span className="mypage-danger-badge">영구 삭제 후 복구 불가</span>
        </div>
        <p className="muted">
          탈퇴가 완료되면 아래의 창작 자료가 즉시 영구 삭제되며 복구할 수 없습니다.
        </p>

        <div className="mypage-deletion-scope" role="region" aria-label="영구 삭제 대상">
          <strong>영구 삭제 대상</strong>
          <ul>
            <li>보유 작품 {workCount}편 및 설정집 전체</li>
            <li>저장된 회차 원고 {chapterCount}건 및 사건 타임라인</li>
            <li>AI 검토 결과, 근거 스냅샷 및 작가 판단 기록</li>
            <li>작가 계정 ({user.email}) 및 로그인 정보</li>
          </ul>
          <p className="small muted">
            원고를 보관하려면 탈퇴 전에 작품 화면에서 Markdown 또는 텍스트 파일로 내보내 주세요.
          </p>
        </div>

        <div className="mypage-ai-notice-box">
          <p className="small">
            진행 중이거나 대기 중인 AI 분석·사건 추출 작업은 즉시 취소되며 늦게 도착한 결과도 반영되지
            않습니다. 단, 이미 외부 AI 공급자(Google Gemini API)로 전송된 요청 자료는 공급자 정책에
            따라 처리되며 탈퇴로 회수할 수 없습니다.
          </p>
        </div>

        {dirtyIds.length > 0 && (
          <p role="status" className="mypage-unsaved-warning">
            현재 브라우저에 아직 서버로 저장되지 않은 편집 중 원고가 있습니다. 탈퇴 시 브라우저 초안과
            복구 자료도 함께 삭제됩니다.
          </p>
        )}

        <PasswordInput
          label="현재 비밀번호 확인"
          required
          disabled={busy}
          autoComplete="current-password"
          placeholder="본인 확인을 위해 현재 비밀번호를 입력해 주세요"
          value={currentPassword}
          onChange={(event) => {
            setCurrentPassword(event.target.value)
            if (error) setError('')
          }}
        />

        <label className="mypage-agree-row">
          <input
            type="checkbox"
            checked={agreed}
            disabled={busy}
            onChange={(event) => {
              setAgreed(event.target.checked)
              if (error) setError('')
            }}
          />
          <span>
            위 작품·원고·설정·사건 타임라인·AI 검토 결과·판단 기록이 모두 영구 삭제되며 복구할 수 없음을
            확인했고 이에 동의합니다.
          </span>
        </label>

        {error && (
          <p role="alert" className="mypage-error-message">
            {error}
          </p>
        )}

        <div className="dialog-actions mypage-withdraw-actions">
          <button type="button" className="ghost" disabled={busy} onClick={onClose}>
            취소하고 돌아가기
          </button>
          <button
            type="submit"
            className="danger"
            disabled={!canSubmit}
          >
            {busy ? '탈퇴 처리 중…' : agreed ? '회원 탈퇴' : '동의 후 탈퇴 가능'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
