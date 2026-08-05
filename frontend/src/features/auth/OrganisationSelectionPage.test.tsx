import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OrganisationSelectionPage } from './OrganisationSelectionPage'

const { selectActiveOrganisation, listMyContexts, listMine, accept, reject } = vi.hoisted(() => ({
  selectActiveOrganisation:vi.fn(),
  listMyContexts:vi.fn(),
  listMine:vi.fn(),
  accept:vi.fn(),
  reject:vi.fn(),
}))

vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({
    session:{
      user:{ id:'user-1', role:'patient' },
      expiresAt:'2030-01-01T00:00:00Z',
    },
    selectActiveOrganisation,
  }),
}))

vi.mock('../../services/api/organisationRestService', () => ({
  organisationRestService:{ listMyContexts },
}))

vi.mock('../../services/api/staffInvitationRestService', () => ({
  staffInvitationRestService:{ listMine, accept, reject },
}))

describe('OrganisationSelectionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listMyContexts.mockResolvedValue([{
      membershipId:'membership-1',
      organisationId:'organisation-1',
      organisationName:'Sahha Clinic',
      organisationType:'CLINIC',
      roles:['ORGANIZATION_ADMIN'],
      membershipVersion:0,
    }])
    selectActiveOrganisation.mockResolvedValue({
      user:{ id:'user-1', role:'hospital-super-admin' },
      expiresAt:'2030-01-01T00:00:00Z',
    })
    listMine.mockResolvedValue({
      items:[], page:0, size:100, totalElements:0, totalPages:0,
    })
  })

  it('selects an authoritative membership and opens its scoped workspace', async () => {
    const queryClient = new QueryClient({
      defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/organisations/select']}>
          <Routes>
            <Route path="/organisations/select" element={<OrganisationSelectionPage/>}/>
            <Route path="/hospital/admin/overview" element={<p>Organisation administration</p>}/>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name:/Sahha Clinic/i }))
    await waitFor(() => expect(selectActiveOrganisation)
      .toHaveBeenCalledWith('organisation-1'))
    expect(await screen.findByText('Organisation administration'))
      .toBeInTheDocument()
  })

  it('accepts an email-matched invitation before an organisation is active', async () => {
    const invitation = {
      id:'invitation-1',
      organisationId:'organisation-2',
      organisationName:'Sahha Specialist Clinic',
      email:'doctor@example.test',
      role:'DOCTOR' as const,
      status:'PENDING' as const,
      expiresAt:'2030-01-08T00:00:00Z',
      resolvedAt:null,
      resolvedByUserId:null,
      acceptedMembershipId:null,
      createdBy:'admin-1',
      createdAt:'2030-01-01T00:00:00Z',
      updatedAt:'2030-01-01T00:00:00Z',
      version:0,
    }
    listMyContexts.mockResolvedValue([])
    listMine.mockResolvedValue({
      items:[invitation], page:0, size:100, totalElements:1, totalPages:1,
    })
    accept.mockResolvedValue({
      ...invitation,
      status:'ACCEPTED',
      acceptedMembershipId:'membership-2',
      version:1,
    })
    const queryClient = new QueryClient({
      defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/organisations/select']}>
          <OrganisationSelectionPage/>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Sahha Specialist Clinic'))
      .toBeInTheDocument()

    fireEvent.click(await screen.findByRole('button', { name:/accept/i }))

    await waitFor(() => expect(accept).toHaveBeenCalledWith(
      'invitation-1',
      { version:0 },
    ))
    expect(await screen.findByText(/Membership activated/i)).toBeInTheDocument()
  })
})
