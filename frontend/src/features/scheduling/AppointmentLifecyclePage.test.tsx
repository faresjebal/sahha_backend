import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { AppointmentResource } from '../../models/scheduling'
import { AppointmentLifecyclePage } from './AppointmentLifecyclePage'

const appointmentService = vi.hoisted(() => ({
  list:vi.fn(),
  book:vi.fn(),
  confirm:vi.fn(),
  reject:vi.fn(),
  reschedule:vi.fn(),
  cancel:vi.fn(),
  checkIn:vi.fn(),
  start:vi.fn(),
  complete:vi.fn(),
  noShow:vi.fn(),
}))
const availabilityService = vi.hoisted(() => ({
  listDoctors:vi.fn(),
  slots:vi.fn(),
}))
const consultationService = vi.hoisted(() => ({ create:vi.fn() }))

vi.mock('../../services/api/appointmentRestService', () => ({
  appointmentRestService:appointmentService,
}))
vi.mock('../../services/api/availabilityRestService', () => ({
  availabilityRestService:availabilityService,
}))
vi.mock('../../services/api/consultationRestService', () => ({
  consultationRestService:consultationService,
}))
vi.mock('../../services/api/patientRegistryRestService', () => ({
  patientRegistryRestService:{ list:vi.fn() },
}))
vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({
    session:{ user:{ organizationId:'organisation-1' } },
  }),
}))

const requested: AppointmentResource = {
  id:'appointment-12345678',
  organisationId:'organisation-1',
  bookingRequestId:'booking-1',
  patientRegistrationId:'registration-12345678',
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

const renderPage = (scope: 'doctor' | 'reception') => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/doctor/appointments']}>
        <Routes>
          <Route path="/doctor/appointments" element={<AppointmentLifecyclePage scope={scope}/>}/>
          <Route path="/doctor/clinical/:consultationId" element={<p>Clinical consultation opened</p>}/>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Real appointment lifecycle page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    appointmentService.list.mockResolvedValue([requested])
    appointmentService.confirm.mockResolvedValue({
      ...requested,
      status:'CONFIRMED',
      version:1,
    })
    appointmentService.reschedule.mockResolvedValue({
      ...requested,
      status:'RESCHEDULED',
      statusReason:'Patient requested another time',
      startsAt:'2030-01-14T10:00:00Z',
      endsAt:'2030-01-14T10:30:00Z',
      version:1,
    })
    appointmentService.checkIn.mockResolvedValue({
      ...requested,
      status:'CHECKED_IN',
      version:1,
    })
    appointmentService.start.mockResolvedValue({
      ...requested,
      status:'IN_PROGRESS',
      version:2,
    })
    appointmentService.complete.mockResolvedValue({
      ...requested,
      status:'COMPLETED',
      version:3,
    })
    appointmentService.noShow.mockResolvedValue({
      ...requested,
      status:'NO_SHOW',
      version:1,
    })
    consultationService.create.mockResolvedValue({ id:'consultation-12345678' })
    availabilityService.listDoctors.mockResolvedValue([{
      doctorUserId:requested.doctorUserId,
      doctorMembershipId:requested.doctorMembershipId,
      timeZone:'UTC',
      locationLabel:'Synthetic room',
      appointmentDurationMinutes:30,
    }])
    availabilityService.slots.mockResolvedValue({
      organisationId:'organisation-1',
      doctorUserId:requested.doctorUserId,
      from:'2030-01-14',
      to:'2030-02-13',
      timeZone:'UTC',
      locationLabel:'Synthetic room',
      appointmentDurationMinutes:30,
      slots:[{
        startsAt:'2030-01-14T10:00:00Z',
        endsAt:'2030-01-14T10:30:00Z',
        localDate:'2030-01-14',
        localStartTime:'10:00:00',
        localEndTime:'10:30:00',
      }],
    })
  })

  it('lets the doctor confirm an owned requested appointment', async () => {
    renderPage('doctor')

    fireEvent.click(await screen.findByRole('button', { name:'Manage' }))
    fireEvent.click(screen.getByRole('button', { name:'Confirm appointment' }))

    await waitFor(() => expect(appointmentService.confirm).toHaveBeenCalled())
    expect(appointmentService.confirm.mock.calls[0][0]).toBe(requested.id)
    expect(appointmentService.confirm.mock.calls[0][1]).toEqual({
      commandRequestId:expect.any(String),
      version:0,
    })
    expect(await screen.findByText(/now confirmed/i)).toBeInTheDocument()
  })

  it('uses explicit bounded upcoming and history appointment windows', async () => {
    renderPage('doctor')

    await waitFor(() => expect(appointmentService.list).toHaveBeenCalledTimes(1))
    const [upcomingFrom, upcomingTo] = appointmentService.list.mock.calls[0]
    expect(new Date(upcomingTo).getTime() - new Date(upcomingFrom).getTime())
      .toBe(31 * 24 * 60 * 60 * 1_000)
    expect(screen.getByRole('button', { name:'Upcoming' }))
      .toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name:'History' }))

    await waitFor(() => expect(appointmentService.list).toHaveBeenCalledTimes(2))
    const [historyFrom, historyTo] = appointmentService.list.mock.calls[1]
    expect(historyTo).toBe(upcomingFrom)
    expect(new Date(historyTo).getTime() - new Date(historyFrom).getTime())
      .toBe(31 * 24 * 60 * 60 * 1_000)
    expect(screen.getByRole('button', { name:'History' }))
      .toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Appointment history')).toBeInTheDocument()
  })

  it('limits reception to reasoned reschedule or cancellation', async () => {
    renderPage('reception')

    fireEvent.click(await screen.findByRole('button', { name:'Manage' }))
    expect(screen.queryByRole('option', { name:'Confirm appointment' }))
      .not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Exact published slot'))
      .toHaveValue('2030-01-14T10:00:00Z'))
    fireEvent.change(screen.getByLabelText('Reschedule reason'), {
      target:{ value:'Patient requested another time' },
    })
    fireEvent.click(screen.getByRole('button', {
      name:'Reschedule appointment',
    }))

    await waitFor(() => expect(appointmentService.reschedule).toHaveBeenCalled())
    expect(appointmentService.reschedule.mock.calls[0][1]).toMatchObject({
      version:0,
      startsAt:'2030-01-14T10:00:00Z',
      reason:'Patient requested another time',
    })
  })

  it('lets reception check in a confirmed patient and adds them to the waiting list', async () => {
    appointmentService.list.mockResolvedValue([{
      ...requested,
      status:'CONFIRMED',
      version:1,
    }])
    renderPage('reception')

    expect(await screen.findByText('No patients are waiting')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name:'Manage' }))
    expect(screen.queryByRole('option', { name:'Mark as no-show' }))
      .not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name:'Check in patient' }))

    await waitFor(() => expect(appointmentService.checkIn).toHaveBeenCalledWith(
      requested.id,
      { commandRequestId:expect.any(String), version:1 },
    ))
    expect(await screen.findByText('1 waiting')).toBeInTheDocument()
    expect(screen.getByText(/^Checked in /)).toBeInTheDocument()
  })

  it('starts a checked-in appointment and opens its retry-safe consultation', async () => {
    appointmentService.list.mockResolvedValue([{
      ...requested,
      status:'CHECKED_IN',
      version:1,
    }])
    renderPage('doctor')

    fireEvent.click(await screen.findByRole('button', { name:'Manage' }))
    fireEvent.click(screen.getByRole('button', { name:'Start appointment' }))
    await waitFor(() => expect(appointmentService.start).toHaveBeenCalledWith(
      requested.id,
      { commandRequestId:expect.any(String), version:1 },
    ))
    await waitFor(() => expect(consultationService.create).toHaveBeenCalledWith({
      appointmentId:requested.id,
    }))
    expect(await screen.findByText('Clinical consultation opened'))
      .toBeInTheDocument()
    expect(appointmentService.complete).not.toHaveBeenCalled()
  })

  it('offers no-show only after the scheduled start', async () => {
    appointmentService.list.mockResolvedValue([{
      ...requested,
      status:'CONFIRMED',
      startsAt:'2020-01-14T09:00:00Z',
      endsAt:'2020-01-14T09:30:00Z',
      version:1,
    }])
    renderPage('reception')

    fireEvent.click(await screen.findByRole('button', { name:'Manage' }))
    fireEvent.change(screen.getByLabelText('Allowed action'), {
      target:{ value:'no-show' },
    })
    fireEvent.click(screen.getByRole('button', { name:'Mark as no-show' }))

    await waitFor(() => expect(appointmentService.noShow).toHaveBeenCalledWith(
      requested.id,
      { commandRequestId:expect.any(String), version:1 },
    ))
  })
})
