import { SiteFooter } from '../../shared/SiteFooter'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getReturnPath, useAuthStore } from './useAuthStore'
import { authClient } from './authClient'
import { ApiError } from '../../shared/httpClient'
import { useEmailAvailability } from './useEmailAvailability'
import { Icon } from '../../shared/Icon'
import { PasswordInput } from './PasswordInput'

export function AuthPage({ signup = false }: { signup?: boolean }) {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const emailCheck = useEmailAvailability()
  const emailInput = useRef<HTMLInputElement>(null)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const passwordRules = [
    { label: '8~128자', valid: password.length >= 8 && password.length <= 128 },
    { label: '영문 포함', valid: /[A-Za-z]/.test(password) },
    { label: '숫자 포함', valid: /[0-9]/.test(password) },
    { label: '특수문자 포함', valid: /[^\p{L}\p{N}\s]/u.test(password) },
    { label: '공백 없음', valid: password.length > 0 && !/\s/.test(password) },
  ]
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    const data = new FormData(event.currentTarget)
    const name = String(data.get('name') ?? '').trim()
    const password = String(data.get('password'))
    if (signup && !name) {
      setError('필명을 입력해 주세요.')
      return
    }
    if (signup && password !== data.get('confirm')) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }
    if (signup && !/^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^\p{L}\p{N}\s])\S{8,128}$/u.test(password)) {
      setError('비밀번호는 8~128자로 영문·숫자·특수문자를 포함하고 공백 없이 입력해 주세요.')
      return
    }
    if (signup && emailCheck.status !== 'available') {
      setError('이메일 중복 확인을 완료해 주세요.')
      return
    }
    const email = String(data.get('email')).trim()
    setSaving(true)
    setError('')
    try {
      if (signup) {
        await authClient.signup(name, email, password)
        setPassword('')
        setConfirmation('')
        emailCheck.setEmail('')
        navigate('/login', {
          state: { ...getLoginState(location.state), registered: true },
          replace: true,
        })
      } else {
        await login(email, password)
        navigate(getReturnPath(location.state), { replace: true })
      }
    } catch (failure) {
      if (signup && failure instanceof ApiError && failure.status === 409) {
        emailCheck.markUnavailable(failure.message)
      }
      setError(
        failure instanceof Error ? failure.message : '요청에 실패했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setSaving(false)
    }
  }
  function resetAuthForm() {
    setError('')
    setPassword('')
    setConfirmation('')
    emailCheck.setEmail('')
  }

  return (
    <main className="auth-layout">
      <header className="editorial-header">
        <Link className="brand" to="/login" onClick={resetAuthForm}>
          Magister
        </Link>
        <nav aria-label="로그인 및 가입" className="editorial-navigation">
          <Link
            className="nav-item"
            to={signup ? '/login' : '/signup'}
            state={getLoginState(location.state)}
            onClick={resetAuthForm}
          >
            <Icon name={signup ? 'arrow' : 'plus'} />
            {signup ? '로그인' : '회원가입'}
          </Link>
        </nav>
      </header>
      <section className="auth-form-wrap">
        <h1 className="auth-page-title">{signup ? '회원가입' : '로그인'}</h1>
        <form className="auth-form" onSubmit={submit} key={signup ? 'signup' : 'login'}>
          <div className="auth-form-heading">
            <h2>{signup ? '계정 정보' : '이메일로 로그인'}</h2>
            {signup && <p className="muted">이메일과 필명을 입력해 계정을 만듭니다.</p>}
          </div>
          {!signup && location.state?.registered && (
            <div role="status" className="signup-success">
              <span className="signup-success-icon" aria-hidden="true">
                ✓
              </span>
              <div>
                <strong>회원가입이 완료되었습니다.</strong>
                <p>가입한 이메일과 비밀번호로 로그인해 주세요.</p>
              </div>
            </div>
          )}
          {signup && (
            <label>
              <span>
                필명{' '}
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </span>
              <input
                aria-label="필명"
                name="name"
                required
                maxLength={30}
                autoComplete="nickname"
                placeholder="이야기에 남길 이름"
                disabled={saving}
              />
            </label>
          )}
          <div className="auth-email-field">
            <label htmlFor="auth-email">
              이메일{' '}
              {signup && (
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              )}
            </label>
            <div className="email-check-field">
              <input
                aria-label="이메일"
                id="auth-email"
                ref={emailInput}
                name="email"
                type="email"
                required
                maxLength={254}
                autoComplete="email"
                placeholder="writer@example.com"
                value={emailCheck.email}
                onChange={(event) => emailCheck.setEmail(event.target.value)}
                disabled={saving}
                aria-describedby={signup ? 'email-check-result' : undefined}
              />
              {signup && (
                <button
                  type="button"
                  disabled={saving || emailCheck.status === 'checking'}
                  onClick={() => {
                    if (emailInput.current?.reportValidity()) void emailCheck.check()
                  }}
                >
                  {emailCheck.status === 'checking' ? '확인 중' : '중복 확인'}
                </button>
              )}
            </div>
            {signup && (
              <p
                id="email-check-result"
                role="status"
                className={`email-check-result email-check-result--${emailCheck.status}`}
              >
                {emailCheck.status === 'available' && <span aria-hidden="true">✓ </span>}
                {(emailCheck.status === 'unavailable' || emailCheck.status === 'error') && (
                  <span aria-hidden="true">! </span>
                )}
                {emailCheck.message || '가입 전 이메일 중복을 확인해 주세요.'}
              </p>
            )}
          </div>
          <div className="auth-field-group">
            <PasswordInput
              label="비밀번호"
              name="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-describedby={signup ? 'password-conditions' : undefined}
              required
              minLength={8}
              maxLength={128}
              autoComplete={signup ? 'new-password' : 'current-password'}
              placeholder="8자 이상, 영문·숫자·특수문자 조합"
              disabled={saving}
            />
            {signup && (
              <div className="password-rule-card">
                <div className="row small">
                  <strong>비밀번호 조건</strong>
                  <span aria-live="polite">
                    {passwordRules.filter((rule) => rule.valid).length} / 5
                  </span>
                </div>
                <ul
                  id="password-conditions"
                  className="password-conditions"
                  aria-label="비밀번호 조건"
                >
                  {passwordRules.map((rule) => (
                    <li key={rule.label} className={rule.valid ? 'is-valid' : ''}>
                      <span aria-hidden="true">{rule.valid ? '✓' : '○'}</span> {rule.label}
                      <span className="sr-only">{rule.valid ? ' 충족' : ' 미충족'}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
          {signup && (
            <div className="auth-field-group">
              <PasswordInput
                label="비밀번호 확인"
                name="confirm"
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
                aria-describedby="password-confirmation"
                required
                minLength={8}
                maxLength={128}
                autoComplete="new-password"
                placeholder="비밀번호를 한 번 더 입력"
                disabled={saving}
              />
              {confirmation && (
                <p id="password-confirmation" role="status" className="small">
                  {password === confirmation
                    ? '비밀번호가 일치합니다.'
                    : '비밀번호가 일치하지 않습니다.'}
                </p>
              )}
            </div>
          )}
          {saving && <p role="status">처리 중입니다.</p>}
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          <button className="primary full" disabled={saving}>
            {signup ? '회원가입하고 시작하기' : '로그인'} <Icon name="arrow" />
          </button>
          <p className="auth-switch">
            {signup ? '이미 계정이 있으신가요?' : '처음 오셨나요?'}{' '}
            <Link
              to={signup ? '/login' : '/signup'}
              state={getLoginState(location.state)}
              onClick={resetAuthForm}
            >
              {signup ? '로그인' : '회원가입'}
            </Link>
          </p>
        </form>
      </section>
      <SiteFooter />
    </main>
  )
}

function getLoginState(state: unknown): { from: string } {
  return { from: getReturnPath(state) }
}
