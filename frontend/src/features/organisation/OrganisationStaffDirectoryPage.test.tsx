import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DepartmentPageResource, StaffMemberPageResource, StaffMemberResource } from '../../models/organisation'
import { OrganisationStaffDirectoryPage } from './OrganisationStaffDirectoryPage'

const staffService = vi.hoisted(() => ({
  list:vi.fn(),
  get:vi.fn(),
  changeStatus:vi.fn(),
  assignDepartment:vi.fn(),
  endDepartmentAssignment:vi.fn(),
  getDoctorProfile:vi.fn(),
  getMyDoctorProfile:vi.fn(),
  upsertMyDoctorProfile:vi.fn(),
}))

const departmentService = vi.hoisted(() => ({ list:vi.fn() }))

vi.mock('../../services/api/staffDirectoryRestService', () => ({
  staffDirectoryRestService:staffService,
}))
vi.mock('../../services/api/departmentRestService', () => ({
  departmentRestService:departmentService,
}))
vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({ session:{ user:{ organizationId:'organisation-1' } } }),
}))

const doctor: StaffMemberResource = {
  membershipId:'membership-1',
  organisationId:'organisation-1',
  userId:'user-1',
  email:'doctor@example.test',
  displayName:'Synthetic Doctor',
  status:'ACTIVE',
  roles:['DOCTOR'],
  departmentAssignments:[],
  doctorProfile:null,
  joinedAt:'2030-01-01T00:00:00Z',
  updatedAt:'2030-01-01T00:00:00Z',
  version:0,
}

const staffPage = (items: StaffMemberResource[]): StaffMemberPageResource => ({
  items, page:0, size:100, totalElements:items.length, totalPages:items.length ? 1 : 0,
})

const departments: DepartmentPageResource = {
  items:[{
    id:'department-1', organisationId:'organisation-1', name:'Cardiology', code:'CARD',
    description:null, status:'ACTIVE', createdBy:'admin-1', updatedBy:'admin-1',
    createdAt:'2030-01-01T00:00:00Z', updatedAt:'2030-01-01T00:00:00Z', version:0,
  }],
  page:0, size:100, totalElements:1, totalPages:1,
}

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(<OrganisationStaffDirectoryPage/>, { wrapper:Wrapper })
}

describe('Organisation staff directory page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    staffService.list.mockResolvedValue(staffPage([doctor]))
    departmentService.list.mockResolvedValue(departments)
  })

  it('renders accepted tenant staff and opens authoritative membership details', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole(
      'button', { name:/Synthetic Doctor/i }, { timeout:5_000 },
    ))

    expect(screen.getByRole('dialog', { name:'Synthetic Doctor' })).toBeInTheDocument()
    expect(screen.getByText('Professional profile incomplete')).toBeInTheDocument()
    expect(screen.getByText('No department assignment has been recorded.'))
      .toBeInTheDocument()
  })

  it('suspends access with the current membership version and refreshes server state', async () => {
    const suspended = { ...doctor, status:'SUSPENDED' as const, version:1 }
    staffService.changeStatus.mockResolvedValueOnce(suspended)
    staffService.list
      .mockResolvedValueOnce(staffPage([doctor]))
      .mockResolvedValueOnce(staffPage([suspended]))
    renderPage()
    fireEvent.click(await screen.findByRole(
      'button', { name:/Synthetic Doctor/i }, { timeout:5_000 },
    ))

    fireEvent.click(screen.getByRole('button', { name:/suspend access/i }))

    await waitFor(() => expect(staffService.changeStatus).toHaveBeenCalledWith(
      doctor.membershipId,
      { status:'SUSPENDED', version:0 },
    ))
    expect(await screen.findByText('Synthetic Doctor is now suspended.'))
      .toBeInTheDocument()
  })

  it('creates a tenant-scoped department assignment without sending organisationId', async () => {
    const assignment = {
      id:'assignment-1', departmentId:'department-1', departmentName:'Cardiology',
      departmentCode:'CARD', positionTitle:'Attending physician', primaryAssignment:true,
      startDate:'2030-01-02', plannedEndDate:null, status:'ACTIVE' as const, endedAt:null,
      createdAt:'2030-01-02T00:00:00Z', updatedAt:'2030-01-02T00:00:00Z', version:0,
    }
    staffService.assignDepartment.mockResolvedValueOnce(assignment)
    renderPage()
    fireEvent.click(await screen.findByRole(
      'button', { name:/Synthetic Doctor/i }, { timeout:5_000 },
    ))

    fireEvent.change(screen.getByLabelText('Department'), {
      target:{ value:'department-1' },
    })
    fireEvent.change(screen.getByLabelText('Position title'), {
      target:{ value:'Attending physician' },
    })
    fireEvent.change(screen.getByLabelText('Start date'), {
      target:{ value:'2030-01-02' },
    })
    fireEvent.click(screen.getByLabelText('Primary department assignment'))
    fireEvent.click(screen.getByRole('button', { name:/create assignment/i }))

    await waitFor(() => expect(staffService.assignDepartment).toHaveBeenCalledWith(
      doctor.membershipId,
      {
        departmentId:'department-1',
        positionTitle:'Attending physician',
        primaryAssignment:true,
        startDate:'2030-01-02',
        plannedEndDate:null,
      },
    ))
  })
})
