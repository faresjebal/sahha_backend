import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import { httpClient } from './httpClient'

const jsonResponse = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers:{ 'Content-Type':'application/json' },
})

const csrfResponse = (token: string) => jsonResponse({
  headerName:'X-XSRF-TOKEN',
  parameterName:'_csrf',
  token,
})

const forbiddenResponse = () => jsonResponse({
  type:'urn:sahha:problem:request-forbidden',
  title:'Request forbidden',
  status:403,
}, 403)

describe('shared Gateway HTTP client CSRF recovery', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('reloads a stale CSRF token once before retrying an unsafe request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse('stale-csrf-token'))
      .mockResolvedValueOnce(forbiddenResponse())
      .mockResolvedValueOnce(csrfResponse('fresh-csrf-token'))
      .mockResolvedValueOnce(jsonResponse({ id:'department-id' }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(httpClient.request('/departments', {
      method:'POST',
      body:{ name:'Neurology', code:'NEUR' },
    })).resolves.toEqual({ id:'department-id' })

    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[0][0]).toBe(`${env.apiBaseUrl}/auth/csrf`)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'stale-csrf-token' }),
    })
    expect(fetchMock.mock.calls[2][0]).toBe(`${env.apiBaseUrl}/auth/csrf`)
    expect(fetchMock.mock.calls[3][1]).toMatchObject({
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'fresh-csrf-token' }),
    })
  })

  it('stops after one CSRF retry when the service still denies access', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse('first-csrf-token'))
      .mockResolvedValueOnce(forbiddenResponse())
      .mockResolvedValueOnce(csrfResponse('second-csrf-token'))
      .mockResolvedValueOnce(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)

    const request = httpClient.request('/departments', {
      method:'POST',
      body:{ name:'Neurology', code:'NEUR' },
    })

    await expect(request).rejects.toMatchObject({
      problem:expect.objectContaining({ status:403 }),
    })
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('does not retry a forbidden safe request as a CSRF failure', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)

    await expect(httpClient.request('/departments', { method:'GET' }))
      .rejects.toMatchObject({
        problem:expect.objectContaining({ status:403 }),
      })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
