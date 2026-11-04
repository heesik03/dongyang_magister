import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Dialog } from '../../shared/Dialog'
import { PasswordInput } from '../auth/PasswordInput'
import { useAuthStore } from '../auth/useAuthStore'
import { useWorksStore } from '../works/useWorksStore'
import { accountClient, type UserActivity } from './accountClient'
import { WithdrawAccountDialog } from './WithdrawAccountDialog'

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^\p{L}\p{N}\s])\S+$/u

export function MyPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const updateUser = useAuthStore((state) => state.updateUser)
  const dirtyIds = useWorksStore((state) => state.dirtyIds)

  const [activeTab, setActiveTab] = useState<'profile' | 'password'>('profile')
  const [name, setName] = useState(user?.name ?? '')
  const [profileBusy, setProfileBusy] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [profileNotice, setProfileNotice] = useState('')

  const [activity, setActivity] = useState<UserActivity | null>(null)
  const [activityLoading, setActivityLoading] = useState(true)
  const [activityError, setActivityError] = useState('')
  const [activityReload, setActivityReload] = useState(0)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [passwordError, setPasswordError] = useState('')

  const [showAiPolicyDialog, setShowAiPolicyDialog] = useState(false)
  const [showWithdrawDialog, setShowWithdrawDialog] = useState(false)

  useEffect(() => {
    if (user) {
      setName(user.name)
    }
  }, [user])

  useEffect(() => {
    const controller = new AbortController()
    setActivityLoading(true)
    setActivityError('')
    void accountClient
      .getActivity(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setActivity(result)
          setActivityLoading(false)
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setActivityLoading(false)
          setActivityError(
            failure instanceof Error
              ? failure.message
              : '창작 공간 현황을 불러오지 못했습니다.',
          )
        }
      })
    return () => controller.abort()
  }, [activityReload])

  if (!user) return null

  const trimmedName = name.trim()
  const isProfileUnchanged = trimmedName === user.name

  async function handleProfileSubmit(event: FormEvent) {
    event.preventDefault()
    if (profileBusy) return
    if (!trimmedName) {
      setProfileError('필명을 1자 이상 입력해 주세요.')
      setProfileNotice('')
      return
    }
    if (trimmedName.length > 30) {
      setProfileError('필명은 30자 이하로 입력해 주세요.')
      setProfileNotice('')
      return
    }
    setProfileBusy(true)
    setProfileError('')
    setProfileNotice('')
    try {
      const updated = await accountClient.updateProfile(trimmedName)
      updateUser(updated)
      setName(updated.name)
      setProfileNotice('필명이 저장되었습니다.')
    } catch (failure) {
      setProfileError(
        failure instanceof Error
          ? failure.message
          : '필명을 저장하지 못했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setProfileBusy(false)
    }
  }

  async function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault()
    if (passwordBusy) return
    setPasswordError('')

    if (!currentPassword) {
      setPasswordError('현재 비밀번호를 입력해 주세요.')
      return
    }
    if (
      newPassword.length < 8 ||
      newPassword.length > 128 ||
      !PASSWORD_PATTERN.test(newPassword)
    ) {
      setPasswordError(
        '새 비밀번호는 공백 없이 영문·숫자·특수문자를 포함하여 8~128자로 입력해 주세요.',
      )
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('새 비밀번호와 비밀번호 확인이 일치하지 않습니다.')
      return
    }

    setPasswordBusy(true)
    try {
      const store = useWorksStore.getState()
      if (store.dirtyIds.length > 0) {
        await Promise.all(store.dirtyIds.map((id) => store.flushWorkspace(id)))
        if (useWorksStore.getState().dirtyIds.length > 0) {
          setPasswordError(
            '저장되지 않은 원고 입력이 있습니다. 원고를 먼저 저장하거나 복구본을 내려받은 뒤 비밀번호를 변경해 주세요.',
          )
          return
        }
      }

      await accountClient.changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
      })
      useWorksStore.getState().reset()
      useAuthStore.getState().expire()
      navigate('/login', { replace: true })
    } catch (failure) {
      setPasswordError(
        failure instanceof Error
          ? failure.message
          : '비밀번호를 변경하지 못했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setPasswordBusy(false)
    }
  }

  return (
    <main className="mypage-container">
      <section className="mypage-header">
        <div>
          <span className="mypage-eyebrow">PERSONAL ACCOUNT</span>
          <h1>마이페이지</h1>
          <p className="muted">작가 프로필과 창작 공간 현황, 계정 보안을 관리합니다.</p>
        </div>
        <div className="mypage-tabs" role="tablist" aria-label="마이페이지 메뉴">
          <button
            type="button"
            role="tab"
            id="mypage-tab-profile"
            aria-selected={activeTab === 'profile'}
            aria-controls="mypage-panel-profile"
            className={activeTab === 'profile' ? 'active' : 'ghost'}
            onClick={() => setActiveTab('profile')}
          >
            프로필 · 활동
          </button>
          <button
            type="button"
            role="tab"
            id="mypage-tab-password"
            aria-selected={activeTab === 'password'}
            aria-controls="mypage-panel-password"
            className={activeTab === 'password' ? 'active' : 'ghost'}
            onClick={() => setActiveTab('password')}
          >
            비밀번호 변경
          </button>
        </div>
      </section>

      {activeTab === 'profile' ? (
        <div
          id="mypage-panel-profile"
          role="tabpanel"
          aria-labelledby="mypage-tab-profile"
          className="mypage-stack"
        >
          <div className="mypage-grid">
            <section className="mypage-card" aria-labelledby="mypage-profile-heading">
              <div className="mypage-card-header">
                <h2 id="mypage-profile-heading">작가 프로필</h2>
              </div>
              <div className="mypage-identity-banner">
                <span className="avatar mypage-avatar" aria-hidden="true">
                  {user.name.slice(0, 1)}
                </span>
                <div className="mypage-identity-text">
                  <strong>{user.name} 작가님</strong>
                  <span className="small muted">{user.email}</span>
                </div>
                <span className="mypage-session-badge">세션 인증됨</span>
              </div>

              <form className="stack" onSubmit={(event) => void handleProfileSubmit(event)}>
                <label>
                  <span>
                    필명 <span className="required-mark">*</span>
                  </span>
                  <input
                    value={name}
                    maxLength={30}
                    disabled={profileBusy}
                    onChange={(event) => {
                      setName(event.target.value)
                      if (profileError) setProfileError('')
                      if (profileNotice) setProfileNotice('')
                    }}
                  />
                  <span className="small muted">
                    1~30자, 앞뒤 공백은 저장 시 자동 정리됩니다. 다른 작가와 같은 필명을 사용할 수
                    있습니다.
                  </span>
                </label>

                <label>
                  <span>이메일</span>
                  <input value={user.email} readOnly disabled aria-readonly="true" />
                  <span className="small muted">로그인 아이디로 사용되며 변경할 수 없습니다.</span>
                </label>

                {profileError && (
                  <p role="alert" className="mypage-error-message">
                    {profileError}
                  </p>
                )}
                {profileNotice && (
                  <p role="status" className="mypage-success-message">
                    {profileNotice}
                  </p>
                )}

                <div className="mypage-form-actions">
                  <button
                    type="button"
                    className="ghost"
                    disabled={profileBusy || isProfileUnchanged}
                    onClick={() => {
                      setName(user.name)
                      setProfileError('')
                      setProfileNotice('')
                    }}
                  >
                    변경 취소
                  </button>
                  <button
                    type="submit"
                    className="primary"
                    disabled={profileBusy || isProfileUnchanged}
                  >
                    {profileBusy ? '저장 중…' : '프로필 저장'}
                  </button>
                </div>
              </form>
            </section>

            <section className="mypage-card" aria-labelledby="mypage-activity-heading">
              <div className="mypage-card-header">
                <h2 id="mypage-activity-heading">창작 공간 현황</h2>
                <Link to="/" className="mypage-inline-link">
                  작품 보관함 →
                </Link>
              </div>

              {activityError ? (
                <div role="alert" className="storage-error">
                  <span>{activityError}</span>
                  <button
                    type="button"
                    onClick={() => setActivityReload((value) => value + 1)}
                  >
                    다시 시도
                  </button>
                </div>
              ) : (
                <>
                  <div className="mypage-metrics" aria-busy={activityLoading}>
                    <div className="mypage-metric-box">
                      <span className="small muted">보유 작품</span>
                      <strong className="mypage-metric-value">
                        {activityLoading ? '…' : (activity?.workCount ?? 0)}
                      </strong>
                      <span className="small muted">개인 창작 공간</span>
                    </div>
                    <div className="mypage-metric-box">
                      <span className="small muted">저장된 회차</span>
                      <strong className="mypage-metric-value">
                        {activityLoading ? '…' : (activity?.chapterCount ?? 0)}
                      </strong>
                      <span className="small muted">전체 작품 합산</span>
                    </div>
                    <div className="mypage-metric-box mypage-metric-highlight">
                      <span className="small">미검토 후보</span>
                      <strong className="mypage-metric-value">
                        {activityLoading ? '…' : (activity?.unreviewedCount ?? 0)}
                      </strong>
                      <span className="small">최신 원고의 완료된 분석 기준</span>
                    </div>
                  </div>

                  <div className="mypage-recent-box">
                    <div>
                      <span className="small muted">최근 작업 원고</span>
                      {activity?.recentManuscript ? (
                        <strong>
                          {activity.recentManuscript.workTitle} ·{' '}
                          {activity.recentManuscript.chapterTitle}
                        </strong>
                      ) : (
                        <strong>아직 저장된 회차 원고가 없습니다.</strong>
                      )}
                    </div>
                    {activity?.recentManuscript ? (
                      <Link
                        className="button ghost"
                        to={
                          '/works/' +
                          encodeURIComponent(activity.recentManuscript.workId) +
                          '/manuscript?chapter=' +
                          encodeURIComponent(activity.recentManuscript.chapterId)
                        }
                      >
                        원고 이어서 쓰기 →
                      </Link>
                    ) : (
                      <Link className="button ghost" to="/">
                        작품 만들러 가기 →
                      </Link>
                    )}
                  </div>
                </>
              )}

              <div className="mypage-privacy-box">
                <p className="small muted">
                  작품·설정집·원고는 로그인한 본인 계정에서만 조회할 수 있습니다. AI 검토는 작가가 선택한
                  자료만 전송하며 원고를 자동 수정하지 않습니다.
                </p>
                <button
                  type="button"
                  className="ghost small"
                  onClick={() => setShowAiPolicyDialog(true)}
                >
                  AI 자료 처리 안내
                </button>
              </div>
            </section>
          </div>

          <section className="mypage-danger-card" aria-labelledby="mypage-withdraw-heading">
            <div>
              <h2 id="mypage-withdraw-heading">회원 탈퇴</h2>
              <p className="small muted">
                계정과 보유 작품, 설정집, 원고, AI 검토 이력을 영구 삭제합니다. 삭제된 자료는 복구할 수
                없습니다.
              </p>
            </div>
            <button
              type="button"
              className="danger"
              onClick={() => setShowWithdrawDialog(true)}
            >
              회원 탈퇴...
            </button>
          </section>
        </div>
      ) : (
        <div
          id="mypage-panel-password"
          role="tabpanel"
          aria-labelledby="mypage-tab-password"
          className="mypage-password-wrapper"
        >
          <section className="mypage-card mypage-password-card" aria-labelledby="mypage-pwd-title">
            <div className="mypage-card-header">
              <div>
                <h2 id="mypage-pwd-title">비밀번호 변경</h2>
                <p className="small muted">
                  현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.
                </p>
              </div>
            </div>

            <form className="stack" onSubmit={(event) => void handlePasswordSubmit(event)}>
              <PasswordInput
                label="현재 비밀번호"
                required
                disabled={passwordBusy}
                autoComplete="current-password"
                placeholder="현재 비밀번호를 입력해 주세요"
                value={currentPassword}
                onChange={(event) => {
                  setCurrentPassword(event.target.value)
                  if (passwordError) setPasswordError('')
                }}
              />

              <div className="stack-xs">
                <PasswordInput
                  label="새 비밀번호"
                  required
                  disabled={passwordBusy}
                  autoComplete="new-password"
                  placeholder="영문·숫자·특수문자 포함 8~128자"
                  value={newPassword}
                  onChange={(event) => {
                    setNewPassword(event.target.value)
                    if (passwordError) setPasswordError('')
                  }}
                />
                <span className="small muted">
                  공백 없이 영문, 숫자, 특수문자를 모두 포함하여 8~128자로 입력해 주세요.
                </span>
              </div>

              <div className="stack-xs">
                <PasswordInput
                  label="새 비밀번호 확인"
                  required
                  disabled={passwordBusy}
                  autoComplete="new-password"
                  placeholder="새 비밀번호를 다시 입력해 주세요"
                  value={confirmPassword}
                  onChange={(event) => {
                    setConfirmPassword(event.target.value)
                    if (passwordError) setPasswordError('')
                  }}
                />
                <span className="small muted">새 비밀번호를 한 번 더 입력해 주세요.</span>
              </div>

              <div className="mypage-session-warning">
                <p className="small">
                  비밀번호를 변경하면 보안을 위해 현재 로그인 세션을 포함한 모든 기기의 세션이 종료되며,
                  새 비밀번호로 다시 로그인해야 합니다.
                </p>
              </div>

              {dirtyIds.length > 0 && (
                <p role="status" className="mypage-unsaved-warning">
                  아직 저장되지 않은 원고가 있습니다. 비밀번호 변경 시 자동 저장을 먼저 시도하며, 저장에
                  실패하면 세션 종료를 중단합니다.
                </p>
              )}

              {passwordError && (
                <p role="alert" className="mypage-error-message">
                  {passwordError}
                </p>
              )}

              <div className="mypage-form-actions">
                <button
                  type="button"
                  className="ghost"
                  disabled={passwordBusy}
                  onClick={() => {
                    setCurrentPassword('')
                    setNewPassword('')
                    setConfirmPassword('')
                    setPasswordError('')
                    setActiveTab('profile')
                  }}
                >
                  취소
                </button>
                <button type="submit" className="primary" disabled={passwordBusy}>
                  {passwordBusy ? '변경 중…' : '비밀번호 변경'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {showAiPolicyDialog && (
        <Dialog title="AI 자료 처리 및 개인정보 안내" onClose={() => setShowAiPolicyDialog(false)}>
          <div className="stack">
            <p>
              <strong>1. 비공개 창작 공간</strong>
              <br />
              작품·설정집·원고·타임라인·판단 기록은 로그인한 작가 본인만 조회할 수 있습니다.
            </p>
            <p>
              <strong>2. 선택 기반 외부 AI 전송</strong>
              <br />
              AI 검토와 사건 추출은 작가가 직접 선택한 대상 회차·확정 설정·참고 회차만 접수 시점에 외부
              공급자(Google Gemini API)로 전송합니다. 선택하지 않은 자료는 전송하거나 평가하지 않으며,
              AI가 원고나 설정집을 자동으로 수정하지 않습니다.
            </p>
            <p>
              <strong>3. 삭제 및 취소 정책</strong>
              <br />
              작품 삭제 또는 회원 탈퇴 시 서버에 저장된 모든 창작 자료와 분석 이력은 즉시 영구 삭제되고
              대기·실행 중인 작업은 취소됩니다. 다만 이미 외부 AI 공급자로 전송된 요청 자료는 공급자
              약관에 따라 처리됩니다.
            </p>
            <div className="dialog-actions">
              <button
                type="button"
                className="primary"
                onClick={() => setShowAiPolicyDialog(false)}
              >
                확인
              </button>
            </div>
          </div>
        </Dialog>
      )}

      {showWithdrawDialog && (
        <WithdrawAccountDialog
          user={user}
          activity={activity}
          onClose={() => setShowWithdrawDialog(false)}
        />
      )}
    </main>
  )
}
