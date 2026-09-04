import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type {
  AppointmentResource,
  BookAppointmentCommand,
} from '../../models/scheduling'
import { appointmentRestService } from './appointmentRestService'
import { httpClient } from './httpClient'

const response = (body: unknown, status = 200) => new Response(
  JSON.stringify(body),
  { status, headers:{ 'Content-Type':'application/json' } },
)

const command: BookAppointmentCommand = {
  bookingRequestId:'c319ad7a-902e-45bb-a7eb-d7d702a1199f',
  patientRegistrationId:'a6319c09-82f7-44cc-8443-1ec91c433991',
  doctorUserId:'3f43e66f-870e-462b-850f-093614633469',
  startsAt:'2030-01-14T09:00:00Z',
}

const appointment: AppointmentResource = {
  id:'980f0425-b576-4a91-a45d-b8eab0f4475a',
  organisationId:'7d7567ed-d259-4bd1-9756-8a93ad4d199c',
  ...command,
  patientId:'1d860db0-faa0-4ad6-9ac6-6d278306f3df',
  doctorMembershipId:'b78860f7-784b-4279-ab15-65337e4a0986',
  status:'REQUESTED',
  statusReason:null,
  endsAt:'2030-01-14T09:30:00Z',
  timeZone:'UTC',
  locationLabel:'Synthetic booking room',
  bookedByUserId:'570759d8-c873-45bd-b288-0d5ea797e6fd',
  bookedByActorType:'STAFF',
  bookedByMembershipId:'43a9ec66-608c-4b52-8424-1758c2aac69f',
  bookedAt:'2026-08-05T03:45:00Z',
  createdAt:'2026-08-05T03:45:00Z',
  updatedAt:'2026-08-05T03:45:00Z',
  version:0,
}

describe('Gateway appointment REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('books an availability-backed appointment with CSRF', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN',
        parameterName:'_csrf',
        token:'appointment-csrf-token',
      }))
      .mockResolvedValueOnce(response(appointment, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(appointmentRestService.book(command)).resolves.toEqual(
      appointment,
    )
    expect(fetchMock.mock.calls[1][0]).toBe(`${env.apiBaseUrl}/appointments`)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method:'POST',
      credentials:'include',
      headers:expect.objectContaining({
        'X-XSRF-TOKEN':'appointment-csrf-token',
      }),
    })
    expect(JSON.parse(String(
      (fetchMock.mock.calls[1][1] as RequestInit).body,
    ))).toEqual(command)
  })

	it('lists a bounded range and sends versioned lifecycle commands', async () => {
	  const fetchMock = vi.fn()
	    .mockResolvedValueOnce(response([appointment]))
	    .mockResolvedValueOnce(response({
	      headerName:'X-XSRF-TOKEN',
	      parameterName:'_csrf',
	      token:'appointment-csrf-token',
	    }))
	    .mockResolvedValueOnce(response({
	      ...appointment,
	      status:'CONFIRMED',
	      version:1,
	    }))
	    .mockResolvedValueOnce(response({
	      ...appointment,
	      status:'RESCHEDULED',
	      statusReason:'Patient requested another time',
	      startsAt:'2030-01-14T10:00:00Z',
	      version:2,
	    }))
	    .mockResolvedValueOnce(response({
	      ...appointment,
	      status:'CHECKED_IN',
	      version:3,
	    }))
	    .mockResolvedValueOnce(response({
	      ...appointment,
	      status:'IN_PROGRESS',
	      version:4,
	    }))
	    .mockResolvedValueOnce(response({
	      ...appointment,
	      status:'COMPLETED',
	      version:5,
	    }))
	    .mockResolvedValueOnce(response({
	      ...appointment,
	      status:'NO_SHOW',
	      version:3,
	    }))
	  vi.stubGlobal('fetch', fetchMock)

	  await expect(appointmentRestService.list(
	    '2030-01-01T00:00:00Z',
	    '2030-01-31T00:00:00Z',
	  )).resolves.toEqual([appointment])
	  await appointmentRestService.confirm(appointment.id, {
	    commandRequestId:'a2b41b4e-26e9-4aaf-b407-23944186208f',
	    version:0,
	  })
	  await appointmentRestService.reschedule(appointment.id, {
	    commandRequestId:'624b89fe-b97d-4b50-8e7f-8f2d0bd0aacc',
	    version:1,
	    startsAt:'2030-01-14T10:00:00Z',
	    reason:'Patient requested another time',
	  })
	  const transitionCommand = {
	    commandRequestId:'579c98f9-41b6-42cb-bc8f-301184598272',
	    version:2,
	  }
	  await appointmentRestService.checkIn(appointment.id, transitionCommand)
	  await appointmentRestService.start(appointment.id, {
	    ...transitionCommand,
	    version:3,
	  })
	  await appointmentRestService.complete(appointment.id, {
	    ...transitionCommand,
	    version:4,
	  })
	  await appointmentRestService.noShow(appointment.id, transitionCommand)

	  expect(fetchMock.mock.calls[0][0]).toBe(
	    `${env.apiBaseUrl}/appointments?from=2030-01-01T00%3A00%3A00Z&to=2030-01-31T00%3A00%3A00Z`,
	  )
	  expect(fetchMock.mock.calls[2][0]).toBe(
	    `${env.apiBaseUrl}/appointments/${appointment.id}/confirm`,
	  )
	  expect(fetchMock.mock.calls[3][0]).toBe(
	    `${env.apiBaseUrl}/appointments/${appointment.id}/reschedule`,
	  )
	  expect(JSON.parse(String(
	    (fetchMock.mock.calls[3][1] as RequestInit).body,
	  ))).toMatchObject({
	    version:1,
	    startsAt:'2030-01-14T10:00:00Z',
	    reason:'Patient requested another time',
	  })
	  expect(fetchMock.mock.calls.slice(4, 8).map(call => call[0])).toEqual([
	    `${env.apiBaseUrl}/appointments/${appointment.id}/check-in`,
	    `${env.apiBaseUrl}/appointments/${appointment.id}/start`,
	    `${env.apiBaseUrl}/appointments/${appointment.id}/complete`,
	    `${env.apiBaseUrl}/appointments/${appointment.id}/no-show`,
	  ])
	})

  it('uses the patient-owned list and booking endpoints through Gateway', async () => {
    const patientAppointment = {
      id:appointment.id,
      organisationId:appointment.organisationId,
      doctorUserId:appointment.doctorUserId,
      status:appointment.status,
      statusReason:null,
      startsAt:appointment.startsAt,
      endsAt:appointment.endsAt,
      timeZone:appointment.timeZone,
      locationLabel:appointment.locationLabel,
      bookedAt:appointment.bookedAt,
      updatedAt:appointment.updatedAt,
      version:0,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([patientAppointment]))
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'patient-csrf',
      }))
      .mockResolvedValueOnce(response(patientAppointment, 201))
    vi.stubGlobal('fetch', fetchMock)

    await appointmentRestService.listMine(
      command.patientRegistrationId,
      '2030-01-01T00:00:00Z',
      '2030-01-31T00:00:00Z',
    )
    await appointmentRestService.bookMine(command)

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/appointments/mine?registrationId=${command.patientRegistrationId}&from=2030-01-01T00%3A00%3A00Z&to=2030-01-31T00%3A00%3A00Z`,
    )
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/appointments/mine`,
    )
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method:'POST',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'patient-csrf' }),
    })
  })
})
