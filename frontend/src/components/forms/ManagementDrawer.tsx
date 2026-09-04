import { useEffect, useId, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

interface ManagementDrawerProps {
  open: boolean
  onClose(): void
  title: string
  eyebrow: string
  copy: string
  icon: ReactNode
  children: ReactNode
  wide?: boolean
}

export function ManagementDrawer({
  open,
  onClose,
  title,
  eyebrow,
  copy,
  icon,
  children,
  wide = false,
}: ManagementDrawerProps) {
  const panelRef = useRef<HTMLElement>(null)
  const titleId = useId()
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useEffect(() => {
    if (!open) return

    const previousFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const focusableControls = () => Array.from(
      panelRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])',
      ) || [],
    )
    window.requestAnimationFrame(() => focusableControls()[0]?.focus())

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        closeRef.current()
        return
      }
      if (event.key !== 'Tab') return

      const controls = focusableControls()
      if (!controls.length) return
      const first = controls[0]
      const last = controls.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      previousFocus?.focus()
    }
  }, [open])

  if (!open) return null

  return <div className="management-drawer">
    <button
      type="button"
      className="management-drawer__scrim"
      aria-label={`Close ${title}`}
      onClick={onClose}
    />
    <aside
      ref={panelRef}
      className={wide ? 'wide' : undefined}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
    >
      <header>
        <span>{icon}</span>
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2 id={titleId}>{title}</h2>
          <p>{copy}</p>
        </div>
        <button
          type="button"
          className="icon-button"
          aria-label={`Close ${title}`}
          onClick={onClose}
        ><X/></button>
      </header>
      {children}
    </aside>
  </div>
}
