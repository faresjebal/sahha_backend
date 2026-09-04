import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type { DoctorAvailabilityResource } from '../../models/scheduling'
import { availabilityRestService } from './availabilityRestService'
import { httpClient } from './httpClient'

const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers:{ 'Content-Type':'application/json' },
})

const availability: DoctorAvailabilityResource = {
  id:'availability-1', organisationId:'organisation-1', doctorUserId:'doctor-1',
  doctorMembershipId:'membership-1', timeZone:'Africa/Tunis',
  appointmentDurationMinutes:30, minimumLeadTimeMinutes:720,
  bookingHorizonDays:60, locationLabel:'Synthetic clinic',
  weeklyWindows:[{ id:'window-1', dayOfWeek:'MONDAY', startTime:'09:00:00', endTime:'17:00:00' }],
  breaks:[], timeOff:[], createdAt:'2030-01-01T00:00:00Z',
  updatedAt:'2030-01-01T00:00:00Z', version:0,
}

describe('Gateway availability REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('returns null only when the doctor has not published availability', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response({
      type:'urn:sahha:problem:availability-not-found',
      title:'Doctor availability not found',
      status:404,
    }, 404)))

    await expect(availabilityRestService.getMine()).resolves.toBeNull()
  })

  it('updates through Gateway with CSRF and requests calculated slots', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'schedule-csrf',
      }))
      .mockResolvedValueOnce(response(availability))
      .mockResolvedValueOnce(response({
        organisationId:'organisation-1', doctorUserId:'doctor-1',
        from:'2030-01-07', to:'2030-01-14', timeZone:'Africa/Tunis',
        locationLabel:'Synthetic clinic', appointmentDurationMinutes:30,
        slots:[],
      }))
    vi.stubGlobal('fetch', fetchMock)

    await availabilityRestService.updateMine({
      timeZone:'Africa/Tunis', appointmentDurationMinutes:30,
      minimumLeadTimeMinutes:720, bookingHorizonDays:60,
      locationLabel:'Synthetic clinic',
      weeklyWindows:[{ dayOfWeek:'MONDAY', startTime:'09:00', endTime:'17:00' }],
      breaks:[], timeOff:[], version:null,
    })
    await availabilityRestService.slots('doctor-1', '2030-01-07', '2030-01-14')

    expect(fetchMock.mock.calls[1][0]).toBe(`${env.apiBaseUrl}/availability/me`)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method:'PUT',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'schedule-csrf' }),
    })
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/availability/doctors/doctor-1/slots?from=2030-01-07&to=2030-01-14`,
    )
  })

  it('derives patient availability from the owned registration route', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([{
        doctorUserId:'doctor-1', displayName:'Dr Synthetic', timeZone:'UTC',
        locationLabel:'Synthetic room', appointmentDurationMinutes:30,
      }]))
      .mockResolvedValueOnce(response({
        organisationId:'organisation-1', doctorUserId:'doctor-1',
        from:'2030-01-07', to:'2030-01-14', timeZone:'UTC',
        locationLabel:'Synthetic room', appointmentDurationMinutes:30,
        slots:[],
      }))
    vi.stubGlobal('fetch', fetchMock)

    await availabilityRestService.listMyPatientDoctors('registration-1')
    await availabilityRestService.myPatientSlots(
      'registration-1', 'doctor-1', '2030-01-07', '2030-01-14',
    )

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/availability/mine/registrations/registration-1/doctors`,
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/availability/mine/registrations/registration-1/doctors/doctor-1/slots?from=2030-01-07&to=2030-01-14`,
    )
  })
})
