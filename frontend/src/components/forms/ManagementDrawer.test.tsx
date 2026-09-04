import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { Building2 } from 'lucide-react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ManagementDrawer } from './ManagementDrawer'

describe('Management drawer', () => {
  afterEach(() => cleanup())

  it('provides a labelled modal and closes with Escape', () => {
    const close = vi.fn()
    render(<ManagementDrawer
      open
      onClose={close}
      title="Create a department"
      eyebrow="Organisation structure"
      copy="Create one bounded service line."
      icon={<Building2/>}
    >
      <form><label>Department name<input/></label></form>
    </ManagementDrawer>)

    expect(screen.getByRole('dialog', { name:'Create a department' }))
      .toBeInTheDocument()
    expect(document.body.style.overflow).toBe('hidden')

    fireEvent.keyDown(document, { key:'Escape' })
    expect(close).toHaveBeenCalledOnce()
  })

  it('does not render or lock scrolling while closed', () => {
    render(<ManagementDrawer
      open={false}
      onClose={vi.fn()}
      title="Create a department"
      eyebrow="Organisation structure"
      copy="Create one bounded service line."
      icon={<Building2/>}
    ><span>Hidden editor</span></ManagementDrawer>)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
  })
})
