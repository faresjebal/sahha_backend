import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DoctorProfileResource } from '../../models/organisation'
import { DoctorProfessionalProfilePage } from './DoctorProfessionalProfilePage'

const staffService = vi.hoisted(() => ({
  getMyDoctorProfile:vi.fn(),
  upsertMyDoctorProfile:vi.fn(),
}))

vi.mock('../../services/api/staffDirectoryRestService', () => ({
  staffDirectoryRestService:staffService,
}))
vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({ session:{ user:{ organizationId:'organisation-1' } } }),
}))

const profile: DoctorProfileResource = {
  id:'profile-1', organisationId:'organisation-1', membershipId:'membership-1',
  specialty:'Cardiology', professionalTitle:'Consultant cardiologist',
  licenceNumber:'MED-123', registrationAuthority:'Synthetic Medical Council',
  biography:'Synthetic internship profile.', createdAt:'2030-01-01T00:00:00Z',
  updatedAt:'2030-01-01T00:00:00Z', version:2,
}

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<DoctorProfessionalProfilePage/>, { wrapper:Wrapper })
}

describe('Doctor professional profile page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    staffService.getMyDoctorProfile.mockResolvedValue(profile)
    staffService.upsertMyDoctorProfile.mockResolvedValue({ ...profile, version:3 })
  })

  it('loads the active-organisation profile and saves with its resource version', async () => {
    renderPage()

    expect(await screen.findByDisplayValue('Cardiology')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Professional title'), {
      target:{ value:'Senior consultant cardiologist' },
    })
    fireEvent.click(screen.getByRole('button', { name:/save professional profile/i }))

    await waitFor(() => expect(staffService.upsertMyDoctorProfile)
      .toHaveBeenCalledWith({
        specialty:'Cardiology',
        professionalTitle:'Senior consultant cardiologist',
        licenceNumber:'MED-123',
        registrationAuthority:'Synthetic Medical Council',
        biography:'Synthetic internship profile.',
        version:2,
      }))
    expect(await screen.findByText(/profile was saved for this organisation/i))
      .toBeInTheDocument()
  })
})
