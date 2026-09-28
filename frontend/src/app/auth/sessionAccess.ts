import type { AppRole, AuthSession, Permission } from '../../models/auth'

type LiveRole = 'patient' | 'doctor' | 'hospital-super-admin' | 'receptionist' | 'platform-admin'

const rolePermissions: Record<LiveRole, Permission[]> = {
  patient:['patient:read:self', 'appointment:manage:self'],
  doctor:['patient:read:clinical', 'patient:write:clinical', 'appointment:manage:practice'],
  'hospital-super-admin':['patient:read:administrative', 'appointment:manage:hospital', 'hospital:operations', 'hospital:access', 'hospital:audit'],
  receptionist:['patient:read:administrative', 'patient:write:administrative'],
  'platform-admin':['platform:admin'],
}

export const canonicalRoles = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((role): role is string => typeof role === 'string') : []

// UI capabilities only. Each backend still authorises the current session and resource.
// Memberships outside the server-selected organisation must never enter this mapping.
export function resolveSessionAccess(organisationId: string | null | undefined, organisationRoles: unknown, platformRoles: unknown) {
  const roles: LiveRole[] = []
  const assigned = organisationId ? canonicalRoles(organisationRoles) : []
  if (assigned.includes('ORGANIZATION_ADMIN')) roles.push('hospital-super-admin')
  if (assigned.includes('DOCTOR')) roles.push('doctor')
  if (assigned.includes('RECEPTIONIST')) roles.push('receptionist')
  if (canonicalRoles(platformRoles).includes('PLATFORM_ADMIN')) roles.push('platform-admin')
  if (!organisationId && !roles.length) roles.push('patient')
  return { roles, permissions:[...new Set(roles.flatMap(role => rolePermissions[role]))] }
}

const hasCanonicalMetadata = (session: AuthSession) =>
  session.organisationRoles !== undefined || session.platformRoles !== undefined

export function sessionRoles(session: AuthSession | null): AppRole[] {
  if (!session) return []
  // Retain the migrated single-role demo contracts; REST always supplies metadata.
  if (!hasCanonicalMetadata(session)) return [session.user.role]
  return resolveSessionAccess(session.user.organizationId, session.organisationRoles, session.platformRoles).roles
}

export function sessionPermissions(session: AuthSession | null): Permission[] {
  if (!session) return []
  if (!hasCanonicalMetadata(session)) return session.user.permissions
  return resolveSessionAccess(session.user.organizationId, session.organisationRoles, session.platformRoles).permissions
}
