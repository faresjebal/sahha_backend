import { describe, expect, it } from 'vitest'
import type { AuthSession } from '../../models/auth'
import { resolveSessionAccess, sessionPermissions, sessionRoles } from './sessionAccess'
import { sessionDestination } from './roleRoutes'

const dual: AuthSession = {
  user:{ id:'synthetic-user', organizationId:'org-a', role:'hospital-super-admin', permissions:[], displayName:'Synthetic', initials:'SY', email:'synthetic@example.test' },
  organisationRoles:['ORGANIZATION_ADMIN', 'DOCTOR'], platformRoles:[], expiresAt:'2099-01-01T00:00:00Z',
}

describe('explicit workspace access', () => {
  it('unions assigned roles once, retaining the administrative default landing page', () => {
    const access = resolveSessionAccess('org-a', ['DOCTOR','ORGANIZATION_ADMIN','DOCTOR','RECEPTIONIST'], ['PLATFORM_ADMIN'])
    expect(access.roles).toEqual(['hospital-super-admin','doctor','receptionist','platform-admin'])
    expect(access.permissions).toEqual(expect.arrayContaining(['patient:read:clinical','patient:write:clinical','patient:write:administrative','hospital:access','platform:admin']))
    expect(new Set(access.permissions).size).toBe(access.permissions.length)
    expect(sessionDestination(dual)).toBe('/hospital/admin/overview')
  })

  it.each([['ORGANIZATION_ADMIN'], ['RECEPTIONIST'], ['UNKNOWN'], ['PLATFORM_ADMIN'], []])('never infers Doctor from organisation roles %j', (...roles) => {
    const access = resolveSessionAccess('org-a', roles, [])
    expect(access.roles).not.toContain('doctor')
    expect(access.permissions).not.toContain('patient:read:clinical')
    expect(access.permissions).not.toContain('patient:write:clinical')
  })

  it('ignores out-of-context, wrong-scope and malformed role values', () => {
    expect(resolveSessionAccess(null, ['DOCTOR'], []).roles).toEqual(['patient'])
    expect(resolveSessionAccess('org-a', 'DOCTOR', ['DOCTOR']).roles).toEqual([])
    expect(resolveSessionAccess('org-a', [null, {}, 'UNKNOWN'], {}).permissions).toEqual([])
    expect(resolveSessionAccess('org-a', [], ['PLATFORM_ADMIN']).roles).toEqual(['platform-admin'])
  })

  it('uses canonical metadata rather than an inflated presentation role or permissions', () => {
    const admin: AuthSession = { ...dual, user:{ ...dual.user, role:'doctor', permissions:['patient:read:clinical'] }, organisationRoles:['ORGANIZATION_ADMIN'] }
    expect(sessionRoles(admin)).toEqual(['hospital-super-admin'])
    expect(sessionPermissions(admin)).not.toContain('patient:read:clinical')
    expect(sessionRoles({ ...admin, organisationRoles:[] })).toEqual([])
    expect(sessionDestination({ ...admin, organisationRoles:[] })).toBe('/forbidden')
  })

  it('keeps the migrated single-role mock contract only when canonical metadata is absent', () => {
    const demo: AuthSession = { user:{ ...dual.user, role:'doctor', permissions:['patient:read:clinical'] }, expiresAt:dual.expiresAt }
    expect(sessionRoles(demo)).toEqual(['doctor'])
    expect(sessionPermissions(demo)).toEqual(['patient:read:clinical'])
    expect(sessionRoles({ ...demo, platformRoles:[] })).toEqual([])
    expect(sessionRoles(null)).toEqual([])
  })

  it('restores deep links for any explicitly assigned workspace', () => {
    expect(sessionDestination(dual, '/doctor/clinical/synthetic-record?tab=history#summary')).toBe('/doctor/clinical/synthetic-record?tab=history#summary')
    expect(sessionDestination(dual, '/hospital/admin/settings')).toBe('/hospital/admin/settings')
    expect(sessionDestination(dual, '/doctor')).toBe('/doctor')
  })

  it.each(['/platform/overview', '/doctor-evil/overview', '//example.test/doctor/overview', '/\\example.test/doctor/overview', 'https://example.test/doctor/overview', '/doctor/../platform/overview'])('rejects unassigned or unsafe return destination %s', from => {
    expect(sessionDestination(dual, from)).toBe('/hospital/admin/overview')
  })
})
