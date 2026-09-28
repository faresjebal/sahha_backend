import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '../../models/auth'
import { AuthProvider, useAuth } from './AuthProvider'
import { ACTIVE_ORGANISATION_CHANGED_KEY } from './browserAuthContext'

const auth = vi.hoisted(() => ({ restoreSession:vi.fn(), login:vi.fn(), logout:vi.fn(), refreshSession:vi.fn(), selectActiveOrganisation:vi.fn() }))
vi.mock('../../services', () => ({ services:{ auth } }))
const session = (id:string, org='org-a'):AuthSession => ({ user:{ id, organizationId:org, role:'doctor', displayName:id, email:`${id}@example.test`, initials:'SD', permissions:['patient:read:clinical'] }, expiresAt:new Date(Date.now()+3600000).toISOString() })
let observed: QueryClient
function Probe() {
  const state = useAuth()
  const client = useQueryClient()
  observed = client
  const [draft, setDraft] = useState('')
  return <><p>{state.status}</p><p>{state.session?.user.id || 'no identity'}</p><p>{state.session?.user.organizationId}</p><output>{String(client.getQueryData(['protected']) || 'empty cache')}</output><input aria-label="Clinical draft" value={draft} onChange={e => setDraft(e.target.value)}/><button onClick={() => { client.setQueryData(['protected'],'doctor A data'); setDraft('doctor A note') }}>Cache data</button><button onClick={() => void state.logout().catch(()=>{})}>Logout</button><button onClick={() => void state.login({ email:'b@example.test', password:'synthetic' }).catch(()=>{})}>Login B</button><button onClick={() => void state.selectActiveOrganisation('org-b').catch(()=>{})}>Switch organisation</button></>
}
describe('Auth-owned data isolation', () => {
  beforeEach(() => { vi.resetAllMocks(); auth.restoreSession.mockResolvedValue(session('doctor-a')); auth.logout.mockResolvedValue(undefined); auth.login.mockResolvedValue(session('doctor-b')); auth.selectActiveOrganisation.mockResolvedValue(session('doctor-a','org-b')) })
  afterEach(() => { cleanup(); vi.useRealTimers() })

  it('discards cached data and component drafts before a same-organisation account switch', async () => {
    render(<AuthProvider><Probe/></AuthProvider>)
    await screen.findByText('doctor-a')
    fireEvent.click(screen.getByText('Cache data'))
    const previous = observed
    expect(screen.getByLabelText('Clinical draft')).toHaveValue('doctor A note')
    fireEvent.click(screen.getByText('Logout'))
    await screen.findByText('anonymous')
    fireEvent.click(screen.getByText('Login B'))
    await screen.findByText('doctor-b')
    expect(observed).not.toBe(previous)
    expect(previous.getQueryData(['protected'])).toBeUndefined()
    expect(screen.getByText('empty cache')).toBeInTheDocument()
    expect(screen.getByLabelText('Clinical draft')).toHaveValue('')
    act(() => previous.setQueryData(['protected'],'late doctor A response'))
    expect(observed.getQueryData(['protected'])).toBeUndefined()
  })

  it('discards data on organisation switch and fails closed if another tab cannot restore context', async () => {
    render(<AuthProvider><Probe/></AuthProvider>)
    await screen.findByText('doctor-a')
    fireEvent.click(screen.getByText('Cache data'))
    const previous = observed
    fireEvent.click(screen.getByText('Switch organisation'))
    await screen.findByText('org-b')
    expect(observed).not.toBe(previous)
    expect(screen.getByLabelText('Clinical draft')).toHaveValue('')
    auth.restoreSession.mockRejectedValueOnce(new Error('context unavailable'))
    act(() => window.dispatchEvent(new StorageEvent('storage', { key:ACTIVE_ORGANISATION_CHANGED_KEY, newValue:'changed' })))
    await screen.findByText('anonymous')
    expect(screen.getByText('no identity')).toBeInTheDocument()
    expect(screen.queryByText('org-b')).not.toBeInTheDocument()
  })

  it('does not let a delayed initial restoration replace a newer login', async () => {
    let finish!: (value:AuthSession) => void
    auth.restoreSession.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    render(<AuthProvider><Probe/></AuthProvider>)
    fireEvent.click(screen.getByText('Login B'))
    await screen.findByText('doctor-b')
    await act(async () => finish(session('doctor-a')))
    expect(screen.queryByText('doctor-a')).not.toBeInTheDocument()
  })

  it('ignores an in-flight refresh after logout', async () => {
    const expiring = session('doctor-a'); expiring.expiresAt = new Date(Date.now()+31000).toISOString()
    auth.restoreSession.mockResolvedValueOnce(expiring)
    let finish!: (value:AuthSession) => void
    auth.refreshSession.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    render(<AuthProvider><Probe/></AuthProvider>)
    await screen.findByText('doctor-a')
    await waitFor(() => expect(auth.refreshSession).toHaveBeenCalled(), { timeout:2500 })
    fireEvent.click(screen.getByText('Logout'))
    await screen.findByText('anonymous')
    await act(async () => finish(session('doctor-a')))
    expect(screen.getByText('no identity')).toBeInTheDocument()
  })
})
