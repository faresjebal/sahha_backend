import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type { StaffInvitationResource } from '../../models/organisation'
import { httpClient } from './httpClient'
import { staffInvitationRestService } from './staffInvitationRestService'

const jsonResponse = (body: unknown, status = 200) => new Response(
  JSON.stringify(body),
  { status, headers:{ 'Content-Type':'application/json' } },
)

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

describe('Gateway staff invitation REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('lists caller-owned invitations through the Gateway', async () => {
    const page = { items:[invitation], page:0, size:100, totalElements:1, totalPages:1 }
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(page))
    vi.stubGlobal('fetch', fetchMock)

    await expect(staffInvitationRestService.listMine()).resolves.toEqual(page)

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/my/staff-invitations?page=0&size=100`,
    )
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include' })
  })

  it('creates and accepts invitations with CSRF and resource versions', async () => {
    const csrf = {
      headerName:'X-XSRF-TOKEN',
      parameterName:'_csrf',
      token:'invitation-csrf-token',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(csrf))
      .mockResolvedValueOnce(jsonResponse(invitation, 201))
      .mockResolvedValueOnce(jsonResponse({ ...invitation, status:'ACCEPTED' }))
    vi.stubGlobal('fetch', fetchMock)

    await staffInvitationRestService.create({
      email:invitation.email,
      role:invitation.role,
    })
    await staffInvitationRestService.accept(invitation.id, { version:0 })

    expect(fetchMock.mock.calls[1][0]).toBe(`${env.apiBaseUrl}/staff-invitations`)
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/my/staff-invitations/${invitation.id}/accept`,
    )
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method:'POST',
      credentials:'include',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'invitation-csrf-token' }),
    })
  })
})
