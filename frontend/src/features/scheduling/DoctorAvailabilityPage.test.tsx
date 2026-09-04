import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DoctorAvailabilityResource } from '../../models/scheduling'
import { DoctorAvailabilityPage } from './DoctorAvailabilityPage'

const availabilityService = vi.hoisted(() => ({
  getMine:vi.fn(),
  updateMine:vi.fn(),
  listDoctors:vi.fn(),
  slots:vi.fn(),
}))

vi.mock('../../services/api/availabilityRestService', () => ({
  availabilityRestService:availabilityService,
}))
vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({ session:{ user:{ organizationId:'organisation-1' } } }),
}))

const availability: DoctorAvailabilityResource = {
  id:'availability-1', organisationId:'organisation-1', doctorUserId:'doctor-1',
  doctorMembershipId:'membership-1', timeZone:'Africa/Tunis',
  appointmentDurationMinutes:30, minimumLeadTimeMinutes:720,
  bookingHorizonDays:60, locationLabel:'Synthetic Tunis clinic',
  weeklyWindows:[
    { id:'window-1', dayOfWeek:'MONDAY', startTime:'09:00:00', endTime:'17:00:00' },
  ],
  breaks:[
    { id:'break-1', dayOfWeek:'MONDAY', startTime:'12:30:00', endTime:'13:30:00', label:'Lunch' },
  ],
  timeOff:[], createdAt:'2030-01-01T00:00:00Z',
  updatedAt:'2030-01-01T00:00:00Z', version:2,
}

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<DoctorAvailabilityPage/>, { wrapper:Wrapper })
}

describe('Doctor availability page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    availabilityService.getMine.mockResolvedValue(availability)
    availabilityService.updateMine.mockResolvedValue({ ...availability, version:3 })
  })

  it('loads organisation availability and publishes an optimistic versioned update', async () => {
    renderPage()

    expect(await screen.findByDisplayValue('Synthetic Tunis clinic'))
      .toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Consultation location'), {
      target:{ value:'Synthetic clinic room 2' },
    })
    fireEvent.click(screen.getByRole('button', { name:/publish schedule/i }))

    await waitFor(() => expect(availabilityService.updateMine).toHaveBeenCalled())
    expect(availabilityService.updateMine.mock.calls[0][0])
      .toEqual(expect.objectContaining({
        locationLabel:'Synthetic clinic room 2',
        timeZone:'Africa/Tunis',
        appointmentDurationMinutes:30,
        version:2,
        weeklyWindows:[{
          dayOfWeek:'MONDAY', startTime:'09:00', endTime:'17:00',
        }],
        breaks:[{
          dayOfWeek:'MONDAY', startTime:'12:30', endTime:'13:30',
          label:'Protected break',
        }],
      }))
    expect(await screen.findByText(/availability is published/i))
      .toBeInTheDocument()
  })
})
