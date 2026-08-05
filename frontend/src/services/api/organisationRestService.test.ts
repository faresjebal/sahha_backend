import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type { CreateOrganisationCommand, OrganisationMembershipResource, OrganisationResource } from '../../models/organisation'
import { httpClient } from './httpClient'
import { organisationRestService } from './organisationRestService'

const jsonResponse = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers:{ 'Content-Type':'application/json' },
})

const organisation: OrganisationResource = {
  id:'0adfbe08-5c0f-494a-b4c1-5902e68eab7d',
  name:'Sahha Clinic Tunis',
  legalName:null,
  type:'CLINIC',
  status:'ACTIVE',
  contactEmail:'contact@sahha.test',
  phoneNumber:'+21670000000',
  address:'1 Health Avenue',
  city:'Tunis',
  region:'Tunis',
  postalCode:'1000',
  countryCode:'TN',
  timeZone:'Africa/Tunis',
  createdBy:'10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98',
  createdAt:'2026-07-31T10:00:00Z',
  updatedAt:'2026-07-31T10:00:00Z',
  version:0,
}

const administrator: OrganisationMembershipResource = {
  id:'1605d039-158d-4db4-8dac-a9e581413a92',
  organisationId:organisation.id,
  userId:'0ce89700-962e-41a1-b298-8ff7b17f0b5c',
  email:'administrator@sahha.test',
  displayName:'Leila Mansour',
  status:'ACTIVE',
  roles:['ORGANIZATION_ADMIN'],
  joinedAt:'2026-07-31T11:00:00Z',
  createdBy:organisation.createdBy,
  createdAt:'2026-07-31T11:00:00Z',
  updatedAt:'2026-07-31T11:00:00Z',
  version:0,
}

describe('Gateway Organisation REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('lists platform organisations through the configured Gateway URL', async () => {
    const page = { items:[organisation], page:0, size:25, totalElements:1, totalPages:1 }
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(page))
    vi.stubGlobal('fetch', fetchMock)

    await expect(organisationRestService.list(0, 25)).resolves.toEqual(page)

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/platform/organisations?page=0&size=25`,
    )
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include' })
  })

  it('reads a selected organisation through its encoded Gateway resource URL', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(organisation))
    vi.stubGlobal('fetch', fetchMock)

    await expect(organisationRestService.get(organisation.id)).resolves.toEqual(organisation)

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/platform/organisations/${organisation.id}`,
    )
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include' })
  })

  it('creates an organisation with cookie authentication and CSRF protection', async () => {
    const command: CreateOrganisationCommand = {
      name:'Sahha Clinic Tunis',
      legalName:null,
      type:'CLINIC',
      contactEmail:'contact@sahha.test',
      phoneNumber:'+21670000000',
      address:'1 Health Avenue',
      city:'Tunis',
      region:'Tunis',
      postalCode:'1000',
      countryCode:'TN',
      timeZone:'Africa/Tunis',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName:'X-XSRF-TOKEN',
        parameterName:'_csrf',
        token:'organisation-csrf-token',
      }))
      .mockResolvedValueOnce(jsonResponse(organisation, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(organisationRestService.create(command)).resolves.toEqual(organisation)

    expect(fetchMock.mock.calls[0][0]).toBe(`${env.apiBaseUrl}/auth/csrf`)
    expect(fetchMock.mock.calls[1][0]).toBe(`${env.apiBaseUrl}/platform/organisations`)
    const options = fetchMock.mock.calls[1][1] as RequestInit
    expect(options).toMatchObject({
      method:'POST',
      credentials:'include',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'organisation-csrf-token' }),
    })
    expect(JSON.parse(String(options.body))).toEqual(command)
  })

  it('lists and reads organisation administrators through nested resources', async () => {
    const administratorPage = {
      items:[administrator],
      page:0,
      size:20,
      totalElements:1,
      totalPages:1,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(administratorPage))
      .mockResolvedValueOnce(jsonResponse(administrator))
    vi.stubGlobal('fetch', fetchMock)

    await expect(organisationRestService.listAdministrators(organisation.id))
      .resolves.toEqual(administratorPage)
    await expect(organisationRestService.getAdministrator(
      organisation.id,
      administrator.id,
    )).resolves.toEqual(administrator)

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/platform/organisations/${organisation.id}/administrators?page=0&size=20`,
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/platform/organisations/${organisation.id}/administrators/${administrator.id}`,
    )
  })

  it('assigns an administrator with CSRF and an exact normalized email', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName:'X-XSRF-TOKEN',
        parameterName:'_csrf',
        token:'membership-csrf-token',
      }))
      .mockResolvedValueOnce(jsonResponse(administrator, 201))
    vi.stubGlobal('fetch', fetchMock)

    await organisationRestService.assignAdministrator(
      organisation.id,
      { email:administrator.email },
    )

    const options = fetchMock.mock.calls[1][1] as RequestInit
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/platform/organisations/${organisation.id}/administrators`,
    )
    expect(options).toMatchObject({
      method:'POST',
      credentials:'include',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'membership-csrf-token' }),
    })
    expect(JSON.parse(String(options.body))).toEqual({ email:administrator.email })
  })
})
