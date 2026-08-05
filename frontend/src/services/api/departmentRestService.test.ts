import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type { DepartmentResource } from '../../models/organisation'
import { departmentRestService } from './departmentRestService'
import { httpClient } from './httpClient'

const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers:{ 'Content-Type':'application/json' },
})

const department: DepartmentResource = {
  id:'b4095934-bcc8-42ae-b7aa-d62f36555e5d',
  organisationId:'0adfbe08-5c0f-494a-b4c1-5902e68eab7d',
  name:'Cardiology',
  code:'CARD',
  description:'Synthetic department data.',
  status:'ACTIVE',
  createdBy:'10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98',
  updatedBy:'10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98',
  createdAt:'2026-08-01T10:00:00Z',
  updatedAt:'2026-08-01T10:00:00Z',
  version:0,
}

describe('Gateway department REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('lists active-organisation departments through Gateway', async () => {
    const page = { items:[department], page:0, size:100, totalElements:1, totalPages:1 }
    const fetchMock = vi.fn().mockResolvedValueOnce(response(page))
    vi.stubGlobal('fetch', fetchMock)

    await expect(departmentRestService.list()).resolves.toEqual(page)

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/departments?page=0&size=100`,
    )
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include' })
  })

  it('creates and updates status with CSRF and optimistic versions', async () => {
    const csrfResource = {
      headerName:'X-XSRF-TOKEN',
      parameterName:'_csrf',
      token:'department-csrf-token',
    }
    const inactive = { ...department, status:'INACTIVE' as const, version:1 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(csrfResource))
      .mockResolvedValueOnce(response(department, 201))
      .mockResolvedValueOnce(response(inactive))
    vi.stubGlobal('fetch', fetchMock)

    await departmentRestService.create({
      name:'Cardiology',
      code:'CARD',
      description:null,
    })
    await departmentRestService.changeStatus(department.id, {
      status:'INACTIVE',
      version:0,
    })

    expect(fetchMock.mock.calls[1][0]).toBe(`${env.apiBaseUrl}/departments`)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method:'POST',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'department-csrf-token' }),
    })
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/departments/${department.id}/status`,
    )
    expect(JSON.parse(String((fetchMock.mock.calls[2][1] as RequestInit).body)))
      .toEqual({ status:'INACTIVE', version:0 })
  })
})
