import { useId, useState, type ComponentProps } from 'react'
import { Icon } from '../../shared/Icon'

type PasswordInputProps = Omit<ComponentProps<'input'>, 'type' | 'ref'> & { label: string }

export function PasswordInput({
  label,
  id: providedId,
  disabled,
  required,
  'aria-describedby': describedBy,
  onKeyDown,
  onKeyUp,
  onClick,
  onBlur,
  ...props
}: PasswordInputProps) {
  const generatedId = useId()
  const id = providedId ?? generatedId
  const noticeId = id + '-caps-lock'
  const [visible, setVisible] = useState(false)
  const [capsLockOn, setCapsLockOn] = useState(false)
  return (
    <div className="password-input-group">
      <label htmlFor={id}>
        <span>
          {label}{' '}
          {required && (
            <span className="required-mark" aria-hidden="true">
              *
            </span>
          )}
        </span>
      </label>
      <div className="password-input-control">
        <input
          {...props}
          id={id}
          aria-label={label}
          disabled={disabled}
          required={required}
          type={visible ? 'text' : 'password'}
          aria-describedby={
            [describedBy, capsLockOn ? noticeId : undefined].filter(Boolean).join(' ') || undefined
          }
          onKeyDown={(event) => {
            setCapsLockOn(event.getModifierState('CapsLock'))
            onKeyDown?.(event)
          }}
          onKeyUp={(event) => {
            setCapsLockOn(event.getModifierState('CapsLock'))
            onKeyUp?.(event)
          }}
          onClick={(event) => {
            setCapsLockOn(event.getModifierState('CapsLock'))
            onClick?.(event)
          }}
          onBlur={(event) => {
            setCapsLockOn(false)
            onBlur?.(event)
          }}
        />
        <button
          type="button"
          className="password-visibility-toggle"
          aria-label={`${label} ${visible ? '숨기기' : '보기'}`}
          aria-pressed={visible}
          aria-controls={id}
          disabled={disabled}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => setVisible(!visible)}
        >
          <Icon name={visible ? 'eye-off' : 'eye'} />
        </button>
      </div>
      {capsLockOn && (
        <p id={noticeId} className="caps-lock-notice" role="status">
          Caps Lock이 켜져 있습니다.
        </p>
      )}
    </div>
  )
}
