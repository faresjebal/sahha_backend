import type { AuthSession, LoginRequest, RegisterAccountRequest, SessionUser } from '../../models/auth'
import { canonicalRoles, resolveSessionAccess } from '../../app/auth/sessionAccess'
import { broadcastActiveOrganisationChange } from '../../app/auth/browserAuthContext'
import { ApiError } from './ApiError'
import { withBrowserRefreshLock } from './browserRefreshLock'
import { httpClient } from './httpClient'
import type { AuthService } from '../contracts'

interface CurrentSessionResource {
  userId: string
  sessionId: string
  accessTokenExpiresAt: string
  platformRoles: string[]
  activeOrganisationId: string | null
  organisationRoles: string[]
}

interface AuthenticatedSessionResource extends CurrentSessionResource {
  refreshTokenExpiresAt: string
  sessionIdleExpiresAt: string
  sessionAbsoluteExpiresAt: string
}

interface UiIdentity {
  userId: string
  email: string
  displayName: string
}

const UI_IDENTITY_KEY = 'sahha-auth-ui-identity-v1'

const displayNameFromEmail = (email: string) => email
  .split('@')[0]
  .split(/[._-]+/)
  .filter(Boolean)
  .map(part => `${part[0]?.toUpperCase() || ''}${part.slice(1)}`)
  .join(' ') || 'Sahha user'

const initialsFromName = (displayName: string) => displayName
  .split(/\s+/)
  .filter(Boolean)
  .map(part => part[0])
  .join('')
  .slice(0, 2)
  .toUpperCase() || 'SU'

const readUiIdentity = (userId: string): UiIdentity | null => {
  try {
    const value = JSON.parse(sessionStorage.getItem(UI_IDENTITY_KEY) || 'null') as UiIdentity | null
    return value?.userId === userId ? value : null
  } catch {
    return null
  }
}

const writeUiIdentity = (identity: UiIdentity) =>
  sessionStorage.setItem(UI_IDENTITY_KEY, JSON.stringify(identity))

const clearUiIdentity = () => sessionStorage.removeItem(UI_IDENTITY_KEY)

const toAuthSession = (
  resource: CurrentSessionResource,
  suppliedIdentity?: Omit<UiIdentity, 'userId'>,
): AuthSession => {
  const persisted = readUiIdentity(resource.userId)
  const identity = suppliedIdentity || persisted || {
    email:'',
    displayName:`Sahha user ${resource.userId.slice(0, 8)}`,
  }
  const access = resolveSessionAccess(resource.activeOrganisationId, resource.organisationRoles, resource.platformRoles)
  const user: SessionUser = {
    id:resource.userId,
    email:identity.email,
    displayName:identity.displayName,
    initials:initialsFromName(identity.displayName),
    role:access.roles[0] || 'patient', // Default landing page, not the complete authority.
    permissions:access.permissions,
    ...(resource.activeOrganisationId
      ? { organizationId:resource.activeOrganisationId }
      : {}),
  }
  return {
    user,
    expiresAt:resource.accessTokenExpiresAt,
    platformRoles:canonicalRoles(resource.platformRoles),
    organisationRoles:canonicalRoles(resource.organisationRoles),
  }
}

const browserDeviceName = () => {
  const platform = navigator.platform?.trim()
  return platform ? `Sahha web on ${platform}` : 'Sahha web browser'
}

let refreshRequest: Promise<AuthSession | null> | null = null
const ACCESS_REFRESH_LEEWAY_MS = 30_000

const authMutation = async <T,>(
  path: string,
  body?: unknown,
): Promise<T> => httpClient.request<T>(path, { method:'POST', body })

const isAuthenticationRequired = (error: unknown) =>
  error instanceof ApiError && error.problem.status === 401

const currentBrowserSession = () =>
  httpClient.request<CurrentSessionResource>(
    '/auth/session',
    { method:'GET', csrf:false },
  )

const expiresSoon = (resource: CurrentSessionResource) =>
  new Date(resource.accessTokenExpiresAt).getTime()
    <= Date.now() + ACCESS_REFRESH_LEEWAY_MS

const refreshBrowserSession = () => {
  if (refreshRequest) return refreshRequest
  refreshRequest = withBrowserRefreshLock(async () => {
    try {
      const current = await currentBrowserSession()
      if (!expiresSoon(current)) return toAuthSession(current)
    } catch (error) {
      if (!isAuthenticationRequired(error)) throw error
    }

    try {
      return toAuthSession(
        await authMutation<AuthenticatedSessionResource>('/auth/refresh'),
      )
    } catch (error) {
      if (isAuthenticationRequired(error)) {
        clearUiIdentity()
        return null
      }
      throw error
    }
  })
    .finally(() => {
      httpClient.invalidateCsrfToken()
      refreshRequest = null
    })
  return refreshRequest
}

export const authRestService: AuthService = {
  async restoreSession() {
    try {
      const current = await currentBrowserSession()
      if (!expiresSoon(current)) return toAuthSession(current)
    } catch (error) {
      if (!isAuthenticationRequired(error)) throw error
    }
    return refreshBrowserSession()
  },
  refreshSession:refreshBrowserSession,

  async selectActiveOrganisation(organisationId: string) {
    const resource = await authMutation<CurrentSessionResource>(
      '/auth/active-organisation',
      { organisationId },
    )
    httpClient.invalidateCsrfToken()
    const session = toAuthSession(resource)
    broadcastActiveOrganisationChange()
    return session
  },

  async login(request: LoginRequest) {
    const resource = await authMutation<AuthenticatedSessionResource>('/auth/login', {
      email:request.email,
      password:request.password,
      deviceName:browserDeviceName(),
    })
    httpClient.invalidateCsrfToken()
    const identity = {
      email:request.email.trim().toLowerCase(),
      displayName:displayNameFromEmail(request.email),
    }
    writeUiIdentity({ userId:resource.userId, ...identity })
    broadcastActiveOrganisationChange()
    return toAuthSession(resource, identity)
  },

  async register(request: RegisterAccountRequest) {
    await authMutation('/auth/registrations', {
      ...request,
      email:request.email.trim().toLowerCase(),
      phoneNumber:request.phoneNumber || null,
    })
  },

  async confirmEmail(token: string) {
    await authMutation('/auth/email-verifications/confirm', { token })
  },

  async logout() {
    try {
      await authMutation('/auth/logout')
    } finally {
      httpClient.invalidateCsrfToken()
      clearUiIdentity()
      broadcastActiveOrganisationChange()
    }
  },
}
