import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type { DoctorProfileResource, StaffMemberPageResource } from '../../models/organisation'
import { httpClient } from './httpClient'
import { staffDirectoryRestService } from './staffDirectoryRestService'

const jsonResponse = (body: unknown, status = 200) => new Response(
  JSON.stringify(body),
  { status, headers:{ 'Content-Type':'application/json' } },
)

describe('Gateway staff directory REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('lists the active organisation staff only through the Gateway', async () => {
    const page: StaffMemberPageResource = {
      items:[], page:0, size:100, totalElements:0, totalPages:0,
    }
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(page))
    vi.stubGlobal('fetch', fetchMock)

    await expect(staffDirectoryRestService.list()).resolves.toEqual(page)

    expect(fetchMock.mock.calls[0][0]).toBe(`${env.apiBaseUrl}/staff?page=0&size=100`)
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include' })
  })

  it('sends CSRF and resource versions for staff lifecycle writes', async () => {
    const csrf = {
      headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'staff-csrf-token',
    }
    const changed = { membershipId:'membership-1', status:'SUSPENDED', version:1 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(csrf))
      .mockResolvedValueOnce(jsonResponse(changed))
    vi.stubGlobal('fetch', fetchMock)

    await staffDirectoryRestService.changeStatus('membership-1', {
      status:'SUSPENDED',
      version:0,
    })

    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/staff/membership-1/status`,
    )
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method:'PUT',
      credentials:'include',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'staff-csrf-token' }),
      body:JSON.stringify({ status:'SUSPENDED', version:0 }),
    })
  })

  it('uses the caller-owned doctor profile endpoint without an organisation id', async () => {
    const profile: DoctorProfileResource = {
      id:'profile-1',
      organisationId:'organisation-1',
      membershipId:'membership-1',
      specialty:'Cardiology',
      professionalTitle:'Consultant cardiologist',
      licenceNumber:'MED-123',
      registrationAuthority:'Synthetic Medical Council',
      biography:null,
      createdAt:'2030-01-01T00:00:00Z',
      updatedAt:'2030-01-01T00:00:00Z',
      version:0,
    }
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(profile))
    vi.stubGlobal('fetch', fetchMock)

    await expect(staffDirectoryRestService.getMyDoctorProfile())
      .resolves.toEqual(profile)
    expect(fetchMock.mock.calls[0][0]).toBe(`${env.apiBaseUrl}/my/doctor-profile`)
  })
})
