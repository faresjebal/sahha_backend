import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AppointmentResource } from '../../models/scheduling'
import { AvailableSlotBrowser } from './AvailableSlotBrowser'

const availabilityService = vi.hoisted(() => ({
  listDoctors:vi.fn(),
  slots:vi.fn(),
}))
const appointmentService = vi.hoisted(() => ({ book:vi.fn() }))
const patientService = vi.hoisted(() => ({ list:vi.fn() }))

vi.mock('../../services/api/availabilityRestService', () => ({
  availabilityRestService:availabilityService,
}))
vi.mock('../../services/api/appointmentRestService', () => ({
  appointmentRestService:appointmentService,
}))
vi.mock('../../services/api/patientRegistryRestService', () => ({
  patientRegistryRestService:patientService,
}))
vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({ session:{ user:{ organizationId:'organisation-1' } } }),
}))

const appointment: AppointmentResource = {
  id:'appointment-12345678',
  organisationId:'organisation-1',
  bookingRequestId:'booking-1',
  patientRegistrationId:'registration-1',
  patientId:'patient-1',
  doctorUserId:'doctor-12345678',
  doctorMembershipId:'membership-1',
  status:'REQUESTED',
  statusReason:null,
  startsAt:'2030-01-14T09:00:00Z',
  endsAt:'2030-01-14T09:30:00Z',
  timeZone:'UTC',
  locationLabel:'Synthetic room',
  bookedByUserId:'receptionist-1',
  bookedByActorType:'STAFF',
  bookedByMembershipId:'reception-membership-1',
  bookedAt:'2026-08-05T04:00:00Z',
  createdAt:'2026-08-05T04:00:00Z',
  updatedAt:'2026-08-05T04:00:00Z',
  version:0,
}

const renderBrowser = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<AvailableSlotBrowser/>, { wrapper:Wrapper })
}

describe('Available slot booking browser', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    availabilityService.listDoctors.mockResolvedValue([{
      doctorUserId:'doctor-12345678',
      doctorMembershipId:'membership-1',
      timeZone:'UTC',
      locationLabel:'Synthetic room',
      appointmentDurationMinutes:30,
    }])
    availabilityService.slots.mockResolvedValue({
      organisationId:'organisation-1',
      doctorUserId:'doctor-12345678',
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
    patientService.list.mockResolvedValue({
      items:[{
        registrationId:'registration-1',
        patientId:'patient-1',
        medicalRecordNumber:'PT-SYNTHETIC',
        firstName:'Amina',
        lastName:'Synthetic',
        dateOfBirth:'1990-04-12',
        sex:'FEMALE',
        phoneNumber:'+216 20 000 000',
        email:'amina@example.test',
        registrationStatus:'ACTIVE',
        registeredAt:'2026-08-05T00:00:00Z',
        registrationVersion:0,
        identityVersion:0,
      }],
      page:0,
      size:25,
      totalElements:1,
      totalPages:1,
    })
    appointmentService.book.mockResolvedValue(appointment)
  })

  it('selects a calculated slot and books an active patient registration', async () => {
    renderBrowser()

    fireEvent.click(await screen.findByRole('button', { name:/09:00/i }))
    expect(await screen.findByText('Amina Synthetic')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name:/request appointment/i }))

    await waitFor(() => expect(appointmentService.book).toHaveBeenCalled())
    expect(appointmentService.book.mock.calls[0][0]).toEqual({
      bookingRequestId:expect.any(String),
      patientRegistrationId:'registration-1',
      doctorUserId:'doctor-12345678',
      startsAt:'2030-01-14T09:00:00Z',
    })
    expect(await screen.findByText(/appointment appointm was requested/i))
      .toBeInTheDocument()
  })
})
