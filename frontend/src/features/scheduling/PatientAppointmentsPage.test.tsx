import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/api/ApiError'
import { PatientAppointmentsPage } from './PatientAppointmentsPage'

const patientService = vi.hoisted(() => ({
  listMyRegistrations:vi.fn(),
  linkMyAccount:vi.fn(),
}))
const availabilityService = vi.hoisted(() => ({
  listMyPatientDoctors:vi.fn(),
  myPatientSlots:vi.fn(),
}))
const appointmentService = vi.hoisted(() => ({
  listMine:vi.fn(),
  bookMine:vi.fn(),
}))

vi.mock('../../services/api/patientRegistryRestService', () => ({
  patientRegistryRestService:patientService,
}))
vi.mock('../../services/api/availabilityRestService', () => ({
  availabilityRestService:availabilityService,
}))
vi.mock('../../services/api/appointmentRestService', () => ({
  appointmentRestService:appointmentService,
}))

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<PatientAppointmentsPage/>, { wrapper:Wrapper })
}

describe('Patient-owned appointment page', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('crypto', { randomUUID:() => 'booking-request-1' })
    patientService.listMyRegistrations.mockResolvedValue([{
      registrationId:'registration-1',
      organisationId:'organisation-1',
      medicalRecordNumber:'PT-12AB34CD56EF',
      status:'ACTIVE',
      firstName:'Synthetic',
      lastName:'Patient',
    }])
    availabilityService.listMyPatientDoctors.mockResolvedValue([{
      doctorUserId:'doctor-1',
      displayName:'Dr Synthetic Patient',
      timeZone:'UTC',
      locationLabel:'Synthetic room',
      appointmentDurationMinutes:30,
    }])
    availabilityService.myPatientSlots.mockResolvedValue({
      organisationId:'organisation-1',
      doctorUserId:'doctor-1',
      from:'2030-01-14',
      to:'2030-01-20',
      timeZone:'UTC',
      locationLabel:'Synthetic room',
      appointmentDurationMinutes:30,
      slots:[{
        startsAt:'2030-01-14T09:00:00Z',
        endsAt:'2030-01-14T09:30:00Z',
        localDate:'2030-01-14',
        localStartTime:'09:00:00',
        localEndTime:'09:30:00',
      }],
    })
    appointmentService.listMine.mockResolvedValue([])
    appointmentService.bookMine.mockResolvedValue({
      id:'appointment-12345678',
      organisationId:'organisation-1',
      doctorUserId:'doctor-1',
      status:'REQUESTED',
      statusReason:null,
      startsAt:'2030-01-14T09:00:00Z',
      endsAt:'2030-01-14T09:30:00Z',
      timeZone:'UTC',
      locationLabel:'Synthetic room',
      bookedAt:'2026-08-14T02:00:00Z',
      updatedAt:'2026-08-14T02:00:00Z',
      version:0,
    })
  })

  it('does not offer record linking when the patient service is unavailable', async () => {
    patientService.listMyRegistrations.mockRejectedValue(new ApiError({
      type:'urn:sahha:problem:service-unavailable',
      title:'Service unavailable',
      status:503,
      detail:'Patient Service is unavailable.',
    }))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Patient Service is unavailable.')
    expect(screen.queryByRole('button', { name:/verify and connect/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name:/retry/i })).toBeInTheDocument()
  })

  it('requests a published slot with the linked registration only', async () => {
    renderPage()

    expect(await screen.findByText('Dr Synthetic Patient')).toBeInTheDocument()
    fireEvent.click(await screen.findByText('09:00'))
    fireEvent.click(screen.getByRole('button', { name:/request appointment/i }))

    await waitFor(() => expect(appointmentService.bookMine).toHaveBeenCalled())
    expect(vi.mocked(appointmentService.bookMine).mock.calls[0]?.[0]).toEqual({
      bookingRequestId:'booking-request-1',
      patientRegistrationId:'registration-1',
      doctorUserId:'doctor-1',
      startsAt:'2030-01-14T09:00:00Z',
    })
    expect(await screen.findByText(/your appointment request was sent/i))
      .toBeInTheDocument()
  })
})
