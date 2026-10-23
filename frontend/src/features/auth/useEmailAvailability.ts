import { useRef, useState } from 'react'
import { authApi } from './api'

type Status = 'idle' | 'checking' | 'available' | 'unavailable' | 'error'

export function useEmailAvailability() {
  const [email, setValue] = useState('')
  const [status, setStatus] = useState<Status>('idle')
  const [message, setMessage] = useState('')
  const requestVersion = useRef(0)

  function setEmail(value: string) {
    requestVersion.current++
    setValue(value)
    setStatus('idle')
    setMessage('')
  }

  function markUnavailable(reason = '이미 가입된 이메일입니다.') {
    requestVersion.current++
    setStatus('unavailable')
    setMessage(reason)
  }

  async function check() {
    const trimmed = email.trim()
    if (!trimmed) {
      setStatus('error')
      setMessage('이메일을 입력해 주세요.')
      return
    }
    const version = ++requestVersion.current
    setStatus('checking')
    setMessage('이메일을 확인하고 있습니다.')
    try {
      const available = await authApi.checkEmail(trimmed)
      // 입력을 바꾼 뒤 도착한 이전 이메일의 응답은 사용하지 않는다.
      if (version !== requestVersion.current) return
      setStatus(available ? 'available' : 'unavailable')
      setMessage(available ? '사용할 수 있는 이메일입니다.' : '이미 가입된 이메일입니다.')
    } catch {
      if (version !== requestVersion.current) return
      setStatus('error')
      setMessage('이메일을 확인하지 못했습니다. 다시 시도해 주세요.')
    }
  }

  return { email, setEmail, status, message, check, markUnavailable }
}
