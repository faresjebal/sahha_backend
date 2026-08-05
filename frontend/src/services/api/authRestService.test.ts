import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import { ACTIVE_ORGANISATION_CHANGED_KEY } from '../../app/auth/browserAuthContext'
import { authRestService } from './authRestService'
import { REFRESH_LOCK_NAME } from './browserRefreshLock'
import { httpClient } from './httpClient'

const jsonResponse = (body: unknown, status = 200) => new Response(
  body === undefined ? undefined : JSON.stringify(body),
  {
    status,
    headers:body === undefined ? undefined : { 'Content-Type':'application/json' },
  },
)

const csrfResponse = () => jsonResponse({
  headerName:'X-XSRF-TOKEN',
  parameterName:'_csrf',
  token:'csrf-token-123',
})

const sessionResponse = (platformRoles: string[] = []) => ({
  userId:'10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98',
  sessionId:'a8c42991-ce66-4f66-842f-262390359515',
  accessTokenExpiresAt:'2099-07-29T22:10:00Z',
  refreshTokenExpiresAt:'2099-07-30T22:00:00Z',
  sessionIdleExpiresAt:'2099-08-05T22:00:00Z',
  sessionAbsoluteExpiresAt:'2099-08-28T22:00:00Z',
  platformRoles,
})

describe('Gateway Auth REST integration', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
    Object.defineProperty(navigator, 'locks', {
      configurable:true,
      value:undefined,
    })
  })

  it('bootstraps CSRF and registers through Gateway with credentials', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({ status:'accepted' }, 202))
    vi.stubGlobal('fetch', fetchMock)

    await authRestService.register({
      firstName:'Amal',
      lastName:'Mansour',
      email:'  AMAL@example.com ',
      password:'Synthetic passphrase 2026!',
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][0]).toBe(`${env.apiBaseUrl}/auth/csrf`)
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include', method:'GET' })
    expect(fetchMock.mock.calls[1][0]).toBe(`${env.apiBaseUrl}/auth/registrations`)
    const registrationOptions = fetchMock.mock.calls[1][1] as RequestInit
    expect(registrationOptions.credentials).toBe('include')
    expect(registrationOptions.headers).toMatchObject({ 'X-XSRF-TOKEN':'csrf-token-123' })
    expect(JSON.parse(String(registrationOptions.body))).toMatchObject({
      email:'amal@example.com',
      phoneNumber:null,
    })
  })

  it('creates a patient UI session from server metadata without trusting a demo role', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(sessionResponse()))
    vi.stubGlobal('fetch', fetchMock)

    const session = await authRestService.login({
      email:'patient.user@example.com',
      password:'Synthetic passphrase 2026!',
      demoRole:'hospital-super-admin',
    })

    expect(session.user.role).toBe('patient')
    expect(session.user.permissions).toEqual(['patient:read:self', 'appointment:manage:self'])
    expect(session.user.displayName).toBe('Patient User')
    expect(session.user.email).toBe('patient.user@example.com')
    const loginBody = JSON.parse(String((fetchMock.mock.calls[1][1] as RequestInit).body))
    expect(loginBody).not.toHaveProperty('demoRole')
    expect(loginBody).toMatchObject({
      email:'patient.user@example.com',
      password:'Synthetic passphrase 2026!',
    })
    expect(loginBody.deviceName).toContain('Sahha web')
  })

  it('maps only the signed global platform role to the platform workspace', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(sessionResponse(['PLATFORM_ADMIN'])))
    vi.stubGlobal('fetch', fetchMock)

    const session = await authRestService.login({
      email:'admin@example.com',
      password:'Synthetic passphrase 2026!',
    })

    expect(session.user.role).toBe('platform-admin')
    expect(session.user.permissions).toEqual(['platform:admin'])
  })

  it('selects a scoped organisation and notifies the other browser tabs', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({
        ...sessionResponse(),
        activeOrganisationId:'3ec4fb05-da05-4edb-b77c-bc27c747b484',
        organisationRoles:['DOCTOR'],
      }))
    vi.stubGlobal('fetch', fetchMock)

    const session = await authRestService.selectActiveOrganisation(
      '3ec4fb05-da05-4edb-b77c-bc27c747b484',
    )

    expect(session.user.role).toBe('doctor')
    expect(session.user.organizationId).toBe(
      '3ec4fb05-da05-4edb-b77c-bc27c747b484',
    )
    expect(localStorage.getItem(ACTIVE_ORGANISATION_CHANGED_KEY))
      .not.toBeNull()
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/auth/active-organisation`,
    )
  })

  it('restores from the access cookie without refreshing credentials', async () => {
    const firstFetch = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(sessionResponse()))
    vi.stubGlobal('fetch', firstFetch)
    await authRestService.login({
      email:'restored.patient@example.com',
      password:'Synthetic passphrase 2026!',
    })

    httpClient.invalidateCsrfToken()
    const restoreFetch = vi.fn()
      .mockImplementation(() => Promise.resolve(
        jsonResponse(sessionResponse()),
      ))
    vi.stubGlobal('fetch', restoreFetch)

    const [first, second] = await Promise.all([
      authRestService.restoreSession(),
      authRestService.restoreSession(),
    ])

    expect(restoreFetch).toHaveBeenCalledTimes(2)
    expect(restoreFetch.mock.calls.every(call =>
      call[0] === `${env.apiBaseUrl}/auth/session`
      && (call[1] as RequestInit).method === 'GET',
    )).toBe(true)
    expect(first).toEqual(second)
    expect(first?.user.email).toBe('restored.patient@example.com')
    expect(first?.user.displayName).toBe('Restored Patient')
  })

  it('treats a missing refresh session as anonymous', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        type:'urn:sahha:problem:authentication-required',
        title:'Authentication required',
        status:401,
      }, 401))
      .mockResolvedValueOnce(jsonResponse({
        type:'urn:sahha:problem:authentication-required',
        title:'Authentication required',
        status:401,
      }, 401))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({
        type:'urn:sahha:problem:authentication-required',
        title:'Authentication required',
        status:401,
        detail:'Authentication credentials are invalid or expired.',
      }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(authRestService.restoreSession()).resolves.toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('refreshes once when the access session is missing but the refresh session is valid', async () => {
    const authenticationRequired = () => jsonResponse({
      type:'urn:sahha:problem:authentication-required',
      title:'Authentication required',
      status:401,
    }, 401)
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(authenticationRequired())
      .mockResolvedValueOnce(authenticationRequired())
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(sessionResponse()))
    vi.stubGlobal('fetch', fetchMock)

    const restored = await authRestService.restoreSession()

    expect(restored?.user.role).toBe('patient')
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[3][0]).toBe(`${env.apiBaseUrl}/auth/refresh`)
    expect(fetchMock.mock.calls[3][1]).toMatchObject({
      credentials:'include',
      method:'POST',
    })
  })

  it('rechecks the access session after acquiring the cross-tab lock', async () => {
    const request = vi.fn(async (
      _name: string,
      _options: LockOptions,
      operation: () => Promise<unknown>,
    ) => operation())
    Object.defineProperty(navigator, 'locks', {
      configurable:true,
      value:{ request },
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        type:'urn:sahha:problem:authentication-required',
        title:'Authentication required',
        status:401,
      }, 401))
      .mockResolvedValueOnce(jsonResponse(sessionResponse()))
    vi.stubGlobal('fetch', fetchMock)

    const restored = await authRestService.restoreSession()

    expect(restored?.user.role).toBe('patient')
    expect(request).toHaveBeenCalledWith(
      REFRESH_LOCK_NAME,
      { mode:'exclusive' },
      expect.any(Function),
    )
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls.every(call =>
      call[0] === `${env.apiBaseUrl}/auth/session`,
    )).toBe(true)
  })

  it('renews stale CSRF once before repeating an Auth mutation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({
        type:'urn:sahha:problem:request-forbidden',
        title:'Request forbidden',
        status:403,
      }, 403))
      .mockResolvedValueOnce(jsonResponse({
        headerName:'X-XSRF-TOKEN',
        parameterName:'_csrf',
        token:'fresh-csrf-token',
      }))
      .mockResolvedValueOnce(jsonResponse({ status:'accepted' }, 202))
    vi.stubGlobal('fetch', fetchMock)

    await authRestService.register({
      firstName:'Amal',
      lastName:'Mansour',
      email:'amal@example.com',
      password:'Synthetic passphrase 2026!',
    })

    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[3][1]).toMatchObject({
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'fresh-csrf-token' }),
    })
  })
})
