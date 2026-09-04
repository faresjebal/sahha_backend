import { beforeEach, describe, expect, it } from 'vitest'
import { roleHome } from '../../app/auth/roleRoutes'
import { mockServices } from './mockServices'

describe('role-aware demo authentication', () => {
  beforeEach(() => localStorage.clear())

  it('creates the selected hospital staff identity and route', async () => {
    const session = await mockServices.auth.login({
      email:'mateo.s@sthelena.health',
      password:'demo-password',
      demoRole:'staff',
      demoStaffRole:'LABORATORY',
    })
    expect(session.user.role).toBe('staff')
    expect(session.user.staffRole).toBe('LABORATORY')
    expect(session.user.permissions).toContain('hospital:laboratory')
    expect(roleHome[session.user.role]).toBe('/staff/overview')
  })

  it('preserves the private-versus-hospital doctor distinction', async () => {
    const session = await mockServices.auth.login({
      email:'mara.v@sahha.health',
      password:'demo-password',
      demoRole:'doctor',
      demoDoctorType:'HOSPITAL',
    })
    expect(session.user.doctorType).toBe('HOSPITAL')
    expect(roleHome[session.user.role]).toBe('/doctor/overview')
  })
})
