import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../services/api/httpClient'
import { AuthProvider, useAuth } from './AuthProvider'
import { ACTIVE_ORGANISATION_CHANGED_KEY } from './browserAuthContext'
import { ProtectedRoute } from './ProtectedRoute'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

// Exercise the real REST adapter, provider, route guards and selector together.
vi.mock('../../services', async () => ({ services:{ auth:(await import('../../services/api/authRestService')).authRestService } }))
const initial = () => ({ userId:'synthetic-doctor', sessionId:'synthetic-session', accessTokenExpiresAt:new Date(Date.now()+600000).toISOString(), activeOrganisationId:'org-a', organisationRoles:['ORGANIZATION_ADMIN','DOCTOR'], platformRoles:[] as string[] })
let server = initial()
let observed: QueryClient
let fetchMock: ReturnType<typeof vi.fn>
function Controls() {
  const auth = useAuth()
  return <><p>{auth.status}</p><WorkspaceSwitcher/><output aria-label="Clinical capability">{String(auth.hasPermission('patient:read:clinical'))}</output><button onClick={() => void auth.selectActiveOrganisation('org-b')}>Change organisation</button></>
}
function Clinical() {
  observed = useQueryClient()
  const [draft, setDraft] = useState('')
  return <><h1>Clinical workspace</h1><input aria-label="Draft" value={draft} onChange={event => setDraft(event.target.value)}/></>
}
function show(path = '/hospital/admin/overview') {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><Controls/><Routes>
    <Route path="/hospital/admin/*" element={<ProtectedRoute roles={['hospital-super-admin']}><h1>Administrative workspace</h1></ProtectedRoute>}/>
    <Route path="/doctor/*" element={<ProtectedRoute roles={['doctor']} permission="patient:read:clinical"><Clinical/></ProtectedRoute>}/>
    <Route path="/reception/*" element={<ProtectedRoute roles={['receptionist']}><h1>Reception workspace</h1></ProtectedRoute>}/>
    <Route path="/platform/*" element={<ProtectedRoute roles={['platform-admin']}><h1>Platform workspace</h1></ProtectedRoute>}/>
    <Route path="/forbidden" element={<h1>Forbidden</h1>}/>
  </Routes></AuthProvider></MemoryRouter>)
}

describe('REST-backed multi-role workspace boundary', () => {
  beforeEach(() => {
    server = initial()
    sessionStorage.clear(); localStorage.clear(); httpClient.invalidateCsrfToken()
    fetchMock = vi.fn(async (input: string, options: RequestInit) => {
      if (input.endsWith('/auth/csrf')) return Response.json({ token:'synthetic-csrf', headerName:'X-XSRF-TOKEN', parameterName:'_csrf' })
      if (input.endsWith('/auth/active-organisation')) {
        expect(options.method).toBe('POST')
        expect(JSON.parse(String(options.body))).toEqual({ organisationId:'org-b' })
        server = { ...server, activeOrganisationId:'org-b', organisationRoles:['ORGANIZATION_ADMIN'] }
        return Response.json(server)
      }
      if (input.endsWith('/auth/session')) return Response.json(server)
      throw new Error(`Unexpected request: ${input}`)
    })
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('opens both assigned workspaces without mutating session authority', async () => {
    show()
    await screen.findByRole('heading', { name:'Administrative workspace' })
    expect(screen.getAllByRole('option').map(option => option.textContent)).toEqual(['Organisation administrator','Doctor'])
    expect(screen.getByLabelText('Clinical capability')).toHaveTextContent('true')
    fireEvent.change(screen.getByLabelText('Switch workspace'), { target:{ value:'doctor' } })
    await screen.findByRole('heading', { name:'Clinical workspace' })
    expect(screen.getByLabelText('Switch workspace')).toHaveValue('doctor')
    fireEvent.change(screen.getByLabelText('Switch workspace'), { target:{ value:'hospital-super-admin' } })
    await screen.findByRole('heading', { name:'Administrative workspace' })
    expect(fetchMock.mock.calls.every(([, options]) => options.method === 'GET')).toBe(true)
  })

  it('restores a clinical deep link despite the administrative default role', async () => {
    show('/doctor/clinical/synthetic-record')
    await screen.findByRole('heading', { name:'Clinical workspace' })
    expect(screen.getByLabelText('Switch workspace')).toHaveValue('doctor')
  })

  it.each(['ORGANIZATION_ADMIN','RECEPTIONIST','UNKNOWN'])('denies clinical routes and permissions to %s alone', async role => {
    server.organisationRoles = [role]
    sessionStorage.setItem('sahha-auth-ui-identity-v1', JSON.stringify({ userId:server.userId, email:'synthetic@example.test', displayName:'Synthetic', role:'doctor', permissions:['patient:read:clinical'] }))
    show('/doctor/clinical/synthetic-record')
    await screen.findByRole('heading', { name:'Forbidden' })
    expect(screen.queryByLabelText('Draft')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Switch workspace')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Clinical capability')).toHaveTextContent('false')
  })

  it('exposes reception and platform only when also explicitly assigned', async () => {
    server.organisationRoles.push('RECEPTIONIST'); server.platformRoles = ['PLATFORM_ADMIN']
    show()
    await screen.findByRole('heading', { name:'Administrative workspace' })
    fireEvent.change(screen.getByLabelText('Switch workspace'), { target:{ value:'receptionist' } })
    await screen.findByRole('heading', { name:'Reception workspace' })
    fireEvent.change(screen.getByLabelText('Switch workspace'), { target:{ value:'platform-admin' } })
    await screen.findByRole('heading', { name:'Platform workspace' })
  })

  it('removes clinical routes, cached data and drafts when changing to an admin-only organisation', async () => {
    show('/doctor/clinical/synthetic-record')
    await screen.findByRole('heading', { name:'Clinical workspace' })
    const previous = observed
    act(() => previous.setQueryData(['protected-record'], 'synthetic record'))
    fireEvent.change(screen.getByLabelText('Draft'), { target:{ value:'synthetic draft' } })
    fireEvent.click(screen.getByText('Change organisation'))
    await screen.findByRole('heading', { name:'Forbidden' })
    expect(previous.getQueryData(['protected-record'])).toBeUndefined()
    expect(screen.queryByLabelText('Draft')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Clinical capability')).toHaveTextContent('false')
    expect(screen.queryByLabelText('Switch workspace')).not.toBeInTheDocument()
  })

  it('applies same-organisation role revocation on cross-tab restoration without stale clinical state', async () => {
    show('/doctor/clinical/synthetic-record')
    await screen.findByRole('heading', { name:'Clinical workspace' })
    const previous = observed
    act(() => previous.setQueryData(['protected-record'], 'synthetic record'))
    fireEvent.change(screen.getByLabelText('Draft'), { target:{ value:'synthetic draft' } })
    server.organisationRoles = ['ORGANIZATION_ADMIN']
    act(() => window.dispatchEvent(new StorageEvent('storage', { key:ACTIVE_ORGANISATION_CHANGED_KEY, newValue:'changed' })))
    await screen.findByRole('heading', { name:'Forbidden' })
    expect(previous.getQueryData(['protected-record'])).toBeUndefined()
    expect(screen.getByLabelText('Clinical capability')).toHaveTextContent('false')
    server.organisationRoles.push('DOCTOR')
    act(() => window.dispatchEvent(new StorageEvent('storage', { key:ACTIVE_ORGANISATION_CHANGED_KEY, newValue:'changed-again' })))
    await waitFor(() => expect(screen.getByLabelText('Switch workspace')).toBeInTheDocument())
    fireEvent.change(screen.getByLabelText('Switch workspace'), { target:{ value:'doctor' } })
    await screen.findByRole('heading', { name:'Clinical workspace' })
    expect(screen.getByLabelText('Draft')).toHaveValue('')
    expect(observed).not.toBe(previous)
  })

  it('re-evaluates all assigned roles and clears clinical cache on scheduled session renewal', async () => {
    server.accessTokenExpiresAt = new Date(Date.now()+32000).toISOString()
    show('/doctor/clinical/synthetic-record')
    await screen.findByRole('heading', { name:'Clinical workspace' })
    const previous = observed
    act(() => previous.setQueryData(['protected-record'], 'synthetic record'))
    server = { ...server, organisationRoles:['ORGANIZATION_ADMIN'], accessTokenExpiresAt:new Date(Date.now()+600000).toISOString() }
    await screen.findByRole('heading', { name:'Forbidden' }, { timeout:4500 })
    expect(previous.getQueryData(['protected-record'])).toBeUndefined()
    expect(screen.getByLabelText('Clinical capability')).toHaveTextContent('false')
    expect(screen.queryByLabelText('Switch workspace')).not.toBeInTheDocument()
  })
})
