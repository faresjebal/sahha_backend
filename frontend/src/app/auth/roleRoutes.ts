import type { AppRole, AuthSession } from '../../models/auth'
import { sessionRoles } from './sessionAccess'

export const roleHome: Record<AppRole, string> = {
  patient: '/patient/overview',
  doctor: '/doctor/overview',
  staff: '/staff/overview',
  'hospital-super-admin': '/hospital/admin/overview',
  'hospital-operations': '/hospital/operations/overview',
  receptionist: '/reception/patients',
  'platform-admin': '/platform/overview',
}

const roleRoutePrefix: Record<AppRole, string> = {
  patient:'/patient/', doctor:'/doctor/', 'hospital-operations':'/hospital/operations/',
  staff:'/staff/', 'hospital-super-admin':'/hospital/admin/', receptionist:'/reception/', 'platform-admin':'/platform/',
}

export const workspaceRoleForPath = (path: string): AppRole | undefined =>
  (Object.keys(roleRoutePrefix) as AppRole[]).find(role =>
    path === roleRoutePrefix[role].slice(0, -1) || path.startsWith(roleRoutePrefix[role]))

export function sessionDestination(session: AuthSession, from?: string): string {
  const roles = sessionRoles(session)
  if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && !from.includes('\\')) {
    const destination = new URL(from, 'https://sahha.invalid')
    const role = workspaceRoleForPath(destination.pathname)
    if (role && roles.includes(role)) return `${destination.pathname}${destination.search}${destination.hash}`
  }
  return roles.length ? roleHome[roles[0]] : '/forbidden'
}
