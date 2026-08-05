import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { StaffInvitationPageResource, StaffInvitationResource } from '../../models/organisation'
import { OrganisationStaffInvitationsPage } from './OrganisationStaffInvitationsPage'

const invitationService = vi.hoisted(() => ({
  list:vi.fn(),
  get:vi.fn(),
  create:vi.fn(),
  renew:vi.fn(),
  revoke:vi.fn(),
  listMine:vi.fn(),
  accept:vi.fn(),
  reject:vi.fn(),
}))

vi.mock('../../services/api/staffInvitationRestService', () => ({
  staffInvitationRestService:invitationService,
}))

vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({
    session:{ user:{ organizationId:'organisation-1' } },
  }),
}))

const invitation: StaffInvitationResource = {
  id:'invitation-1',
  organisationId:'organisation-1',
  organisationName:'Sahha Clinic',
  email:'doctor@example.test',
  role:'DOCTOR',
  status:'PENDING',
  expiresAt:'2030-01-08T00:00:00Z',
  resolvedAt:null,
  resolvedByUserId:null,
  acceptedMembershipId:null,
  createdBy:'admin-1',
  createdAt:'2030-01-01T00:00:00Z',
  updatedAt:'2030-01-01T00:00:00Z',
  version:0,
}

const page = (items: StaffInvitationResource[]): StaffInvitationPageResource => ({
  items,
  page:0,
  size:100,
  totalElements:items.length,
  totalPages:items.length ? 1 : 0,
})

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<OrganisationStaffInvitationsPage/>, { wrapper:Wrapper })
}

describe('Organisation staff invitations page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    invitationService.list.mockResolvedValue(page([invitation]))
    invitationService.create.mockResolvedValue(invitation)
    invitationService.renew.mockResolvedValue({
      ...invitation,
      expiresAt:'2030-01-15T00:00:00Z',
      version:1,
    })
    invitationService.revoke.mockResolvedValue({
      ...invitation,
      status:'REVOKED',
      resolvedAt:'2030-01-02T00:00:00Z',
      version:1,
    })
  })

  it('renders authoritative invitation data and identity boundary', async () => {
    renderPage()

    expect(await screen.findByText('doctor@example.test')).toBeInTheDocument()
    expect(screen.getByText('Identity remains private')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
  })

  it('creates only an allowed doctor or receptionist invitation', async () => {
    const receptionist = {
      ...invitation,
      id:'invitation-2',
      email:'reception@example.test',
      role:'RECEPTIONIST' as const,
    }
    invitationService.create.mockResolvedValueOnce(receptionist)
    renderPage()
    await screen.findByText('doctor@example.test')

    fireEvent.click(screen.getByRole('button', { name:/invite staff member/i }))
    fireEvent.change(screen.getByLabelText('Staff email'), {
      target:{ value:'Reception@Example.Test' },
    })
    fireEvent.change(screen.getByLabelText('Organisation role'), {
      target:{ value:'RECEPTIONIST' },
    })
    fireEvent.click(screen.getByRole('button', { name:'Create invitation' }))

    await waitFor(() => expect(invitationService.create).toHaveBeenCalledWith({
      email:'reception@example.test',
      role:'RECEPTIONIST',
    }))
    expect(await screen.findByText(/Invitation created for reception@example.test/i))
      .toBeInTheDocument()
  })

  it('renews with the server resource version', async () => {
    renderPage()
    await screen.findByText('doctor@example.test')

    fireEvent.click(screen.getByRole('button', { name:'Renew' }))

    await waitFor(() => expect(invitationService.renew).toHaveBeenCalledWith(
      invitation.id,
      { version:0 },
    ))
  })
})
