import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Icon } from './Icon'

export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  const [dirty, setDirty] = useState(false)
  const [confirmClose, setConfirmClose] = useState(false)
  function requestClose() { if (dirty) setConfirmClose(true); else onClose() }
  useEffect(() => {
    const previous = document.activeElement
    const dialog = ref.current
    dialog?.showModal()
    const firstInput = dialog?.querySelector('input, textarea, select')
    if (firstInput instanceof HTMLElement) firstInput.focus()
    return () => { dialog?.close(); if (previous instanceof HTMLElement) previous.focus() }
  }, [])
  return <dialog ref={ref} aria-labelledby="dialog-title" onInputCapture={event => { if (event.target instanceof HTMLElement && event.target.closest('form')) setDirty(true) }} onCancel={event => { event.preventDefault(); requestClose() }}>
    <div className="dialog-heading"><h2 id="dialog-title">{title}</h2><button className="icon-button" aria-label="닫기" onClick={requestClose}><Icon name="close" /></button></div>{children}
    {confirmClose && <div className="notice discard-confirm" role="alert"><p>입력한 내용을 저장하지 않고 닫을까요?</p><div className="dialog-actions"><button onClick={() => setConfirmClose(false)}>계속 작성</button><button onClick={onClose}>입력 버리고 닫기</button></div></div>}
  </dialog>
}
