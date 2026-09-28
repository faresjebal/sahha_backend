import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { AppRole, AuthSession, LoginRequest, Permission, RegisterAccountRequest } from '../../models/auth'
import { services } from '../../services'
import { httpClient } from '../../services/api/httpClient'
import { ACTIVE_ORGANISATION_CHANGED_KEY } from './browserAuthContext'
import { SessionDataBoundary } from './SessionDataBoundary'
import { sessionPermissions, sessionRoles } from './sessionAccess'

interface AuthContextValue {
  session: AuthSession | null
  status: 'loading' | 'authenticated' | 'anonymous' | 'error'
  error: string | null
  login(request: LoginRequest): Promise<AuthSession>
  register(request: RegisterAccountRequest): Promise<void>
  confirmEmail(token: string): Promise<void>
  selectActiveOrganisation(organisationId: string): Promise<AuthSession>
  logout(): Promise<void>
  hasPermission(permission: Permission): boolean
  hasRole(roles: AppRole[]): boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)
const contextKey = (session: AuthSession | null) => session ? JSON.stringify([
  session.user.id, session.user.organizationId, session.user.role,
  [...session.user.permissions].sort(), [...(session.organisationRoles || [])].sort(),
  [...(session.platformRoles || [])].sort(),
]) : 'anonymous'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [status, setStatus] = useState<AuthContextValue['status']>('loading')
  const [error, setError] = useState<string | null>(null)
  const epoch = useRef(0)
  const current = useRef<AuthSession | null>(null)

  const commit = useCallback((next: AuthSession | null) => {
    if (contextKey(current.current) !== contextKey(next)) httpClient.resetSessionRequests()
    current.current = next
    setSession(next)
    setStatus(next ? 'authenticated' : 'anonymous')
  }, [])

  const begin = useCallback(() => {
    const operation = ++epoch.current
    httpClient.resetSessionRequests()
    current.current = null
    setSession(null)
    setStatus('loading')
    setError(null)
    return operation
  }, [])

  useEffect(() => {
    let active = true
    const operation = epoch.current
    services.auth.restoreSession().then(restored => {
      if (active && operation === epoch.current) commit(restored)
    }).catch(() => {
      if (!active || operation !== epoch.current) return
      commit(null)
      setError('We could not restore your session. Please sign in again.')
      setStatus('error')
    })
    return () => { active = false }
  }, [commit])

  useEffect(() => {
    let active = true
    const restoreChangedContext = (event: StorageEvent) => {
      if (event.key !== ACTIVE_ORGANISATION_CHANGED_KEY) return
      const operation = begin()
      services.auth.restoreSession().then(restored => {
        if (active && operation === epoch.current) commit(restored)
      }).catch(() => {
        if (!active || operation !== epoch.current) return
        commit(null)
        setError('Your session changed in another tab. Please sign in again.')
      })
    }
    window.addEventListener('storage', restoreChangedContext)
    return () => { active = false; window.removeEventListener('storage', restoreChangedContext) }
  }, [begin, commit])

  const authenticate = useCallback(async (action: () => Promise<AuthSession>) => {
    const operation = begin()
    try {
      const next = await action()
      if (operation !== epoch.current) throw new Error('This session operation was superseded.')
      commit(next)
      return next
    } catch (failure) {
      if (operation === epoch.current) {
        commit(null)
        setError(failure instanceof Error ? failure.message : 'Sign in failed.')
        setStatus('error')
      }
      throw failure
    }
  }, [begin, commit])

  const login = useCallback((request: LoginRequest) => authenticate(() => services.auth.login(request)), [authenticate])
  const selectActiveOrganisation = useCallback((id: string) => authenticate(() => services.auth.selectActiveOrganisation(id)), [authenticate])
  const register = useCallback((request: RegisterAccountRequest) => services.auth.register(request), [])
  const confirmEmail = useCallback((token: string) => services.auth.confirmEmail(token), [])
  const logout = useCallback(async () => {
    const operation = begin()
    try { await services.auth.logout() }
    finally { if (operation === epoch.current) commit(null) }
  }, [begin, commit])

  useEffect(() => {
    if (!session) return
    let active = true
    const operation = epoch.current
    const timeout = window.setTimeout(() => {
      services.auth.refreshSession().then(refreshed => {
        if (active && operation === epoch.current) commit(refreshed)
      }).catch(() => {
        if (!active || operation !== epoch.current) return
        commit(null)
        setError('Your session could not be renewed. Please sign in again.')
      })
    }, Math.max(1_000, new Date(session.expiresAt).getTime() - Date.now() - 30_000))
    return () => { active = false; window.clearTimeout(timeout) }
  }, [session, commit])

  const value = useMemo<AuthContextValue>(() => ({
    session, status, error, login, register, confirmEmail, selectActiveOrganisation, logout,
    hasPermission: permission => sessionPermissions(session).includes(permission),
    hasRole: roles => sessionRoles(session).some(role => roles.includes(role)),
  }), [session, status, error, login, register, confirmEmail, selectActiveOrganisation, logout])

  return <AuthContext.Provider value={value}>
    <SessionDataBoundary key={contextKey(session)}>{children}</SessionDataBoundary>
  </AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
