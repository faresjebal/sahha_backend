import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { AppRole, AuthSession, LoginRequest, Permission, RegisterAccountRequest } from '../../models/auth'
import { services } from '../../services'
import { ACTIVE_ORGANISATION_CHANGED_KEY } from './browserAuthContext'

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

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [status, setStatus] = useState<AuthContextValue['status']>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    services.auth.restoreSession().then(restored => {
      if (!active) return
      setSession(restored)
      setStatus(restored ? 'authenticated' : 'anonymous')
    }).catch(() => {
      if (!active) return
      setError('We could not restore your session.')
      setStatus('error')
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    const restoreChangedContext = (event: StorageEvent) => {
      if (event.key !== ACTIVE_ORGANISATION_CHANGED_KEY) return
      services.auth.restoreSession()
        .then(restored => {
          if (!active) return
          setSession(restored)
          setStatus(restored ? 'authenticated' : 'anonymous')
          setError(null)
        })
        .catch(() => {
          if (!active) return
          setError('Your organisation context changed, but this tab could not refresh it.')
        })
    }
    window.addEventListener('storage', restoreChangedContext)
    return () => {
      active = false
      window.removeEventListener('storage', restoreChangedContext)
    }
  }, [])

  const login = useCallback(async (request: LoginRequest) => {
    setStatus('loading')
    setError(null)
    try {
      const next = await services.auth.login(request)
      setSession(next)
      setStatus('authenticated')
      return next
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'Sign in failed.')
      setStatus('error')
      throw loginError
    }
  }, [])

  const register = useCallback(async (request: RegisterAccountRequest) => {
    setError(null)
    try {
      await services.auth.register(request)
    } catch (registrationError) {
      setError(registrationError instanceof Error ? registrationError.message : 'Registration failed.')
      throw registrationError
    }
  }, [])

  const confirmEmail = useCallback(async (token: string) => {
    setError(null)
    try {
      await services.auth.confirmEmail(token)
    } catch (confirmationError) {
      setError(confirmationError instanceof Error ? confirmationError.message : 'Email verification failed.')
      throw confirmationError
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await services.auth.logout()
    } finally {
      setSession(null)
      setStatus('anonymous')
    }
  }, [])

  const selectActiveOrganisation = useCallback(async (organisationId: string) => {
    setError(null)
    const next = await services.auth.selectActiveOrganisation(organisationId)
    setSession(next)
    setStatus('authenticated')
    return next
  }, [])

  useEffect(() => {
    if (!session) return
    const refreshAt = new Date(session.expiresAt).getTime() - 30_000
    const delay = Math.max(1_000, refreshAt - Date.now())
    const timeout = window.setTimeout(() => {
      services.auth.refreshSession()
        .then(refreshed => {
          setSession(refreshed)
          setStatus(refreshed ? 'authenticated' : 'anonymous')
        })
        .catch(() => {
          setError('Your session could not be renewed. Please sign in again.')
          setSession(null)
          setStatus('anonymous')
        })
    }, delay)
    return () => window.clearTimeout(timeout)
  }, [session])

  const value = useMemo<AuthContextValue>(() => ({
    session,
    status,
    error,
    login,
    register,
    confirmEmail,
    selectActiveOrganisation,
    logout,
    hasPermission: permission => session?.user.permissions.includes(permission) ?? false,
    hasRole: roles => session ? roles.includes(session.user.role) : false,
  }), [session, status, error, login, register, confirmEmail, selectActiveOrganisation, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
