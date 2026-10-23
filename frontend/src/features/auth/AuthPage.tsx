import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getReturnPath, useAuth } from './auth'
import { authApi } from './api'
import { Icon } from '../../shared/Icon'

export function AuthPage({ signup = false }: { signup?: boolean }) {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    const data = new FormData(event.currentTarget)
    const password = String(data.get('password'))
    if (signup && password !== data.get('confirm')) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }
    if (signup && !/^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^\p{L}\p{N}\s])\S{8,128}$/u.test(password)) {
      setError('비밀번호는 8~128자로 영문·숫자·특수문자를 포함하고 공백 없이 입력해 주세요.')
      return
    }
    const email = String(data.get('email')).trim()
    setSaving(true)
    setError('')
    try {
      if (signup) {
        await authApi.signup(String(data.get('name')).trim(), email, password)
        navigate('/login', {
          state: { ...getLoginState(location.state), registered: true },
          replace: true,
        })
      } else {
        await login(email, password)
        navigate(getReturnPath(location.state), { replace: true })
      }
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : '요청에 실패했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setSaving(false)
    }
  }
  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Link className="brand" to="/login">
          <Icon name="book" /> Magister
        </Link>
        <div className="story-copy">
          <span className="eyebrow">A QUIET SPACE FOR YOUR STORY</span>
          <h1>
            당신의 세계가,
            <br />
            흔들림 없이 이어지도록.
          </h1>
          <p>
            설정과 원고 사이의 작은 틈을 발견하고,
            <br />
            이야기의 다음 페이지를 자신 있게 펼치세요.
          </p>
          <div className="paper-preview">
            <span className="muted">이야기를 읽는 또 하나의 시선</span>
            <p>“그는 금지된 마법을 능숙하게 시전했다.”</p>
            <div className="preview-note">
              <Icon name="spark" />
              <div>
                <strong>설정 충돌 후보</strong>
                <p>
                  인물의 능력이 변화한 계기가 있나요?
                  <br />
                  최종 판단은 언제나 작가의 몫입니다.
                </p>
              </div>
            </div>
          </div>
        </div>
        <span className="muted small">당신의 설정을 존중하는 창작 검토 공간.</span>
      </section>
      <section className="auth-form-wrap">
        <form className="auth-form" onSubmit={submit} key={signup ? 'signup' : 'login'}>
          <span className="eyebrow">YOUR WRITING COMPANION</span>
          <h2>{signup ? '새 이야기를 시작해요' : '다시 만나 반가워요'}</h2>
          <p className="muted">
            {signup
              ? '작가님의 세계를 함께 살펴볼 준비가 되었어요.'
              : '이어 쓰던 이야기가 기다리고 있어요.'}
          </p>
          {signup && (
            <label>
              필명
              <input
                name="name"
                required
                maxLength={30}
                autoComplete="nickname"
                placeholder="이야기에 남길 이름"
              />
            </label>
          )}
          <label>
            이메일
            <input
              name="email"
              type="email"
              required
              autoComplete="email"
              placeholder="writer@example.com"
            />
          </label>
          <label>
            비밀번호
            <input
              name="password"
              type="password"
              required
              minLength={8}
              maxLength={128}
              autoComplete={signup ? 'new-password' : 'current-password'}
              placeholder="8자 이상, 영문·숫자·특수문자 조합"
            />
          </label>
          {signup && (
            <>
              <label>
                비밀번호 확인
                <input
                  name="confirm"
                  type="password"
                  required
                  minLength={8}
                  maxLength={128}
                  autoComplete="new-password"
                  placeholder="비밀번호를 한 번 더 입력"
                />
              </label>
              <label className="check">
                <input type="checkbox" required />
                작품과 원고는 아직 이 브라우저에 저장됨을 확인했습니다.
              </label>
            </>
          )}
          {!signup && location.state?.registered && (
            <p role="status">가입되었습니다. 이메일로 로그인해 주세요.</p>
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
            {signup ? '이미 계정이 있나요?' : '처음 오셨나요?'}{' '}
            <Link to={signup ? '/login' : '/signup'}>{signup ? '로그인' : '회원가입'}</Link>
          </p>
          <p className="notice small">
            로그인은 1시간 동안 활동이 없으면 만료됩니다. 작품과 원고는 이 브라우저에 저장됩니다.
          </p>
        </form>
      </section>
    </main>
  )
}

function getLoginState(state: unknown): { from: string } {
  return { from: getReturnPath(state) }
}
