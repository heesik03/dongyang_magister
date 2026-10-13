import { useEffect, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'

export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const previous = document.activeElement
    const dialog = ref.current
    dialog?.showModal()
    return () => { dialog?.close(); if (previous instanceof HTMLElement) previous.focus() }
  }, [])
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={event => { event.preventDefault(); onClose() }}>
    <div className="dialog-heading"><h2 id="dialog-title">{title}</h2><button className="icon-button" aria-label="닫기" onClick={onClose}><Icon name="close" /></button></div>{children}
  </dialog>
}
