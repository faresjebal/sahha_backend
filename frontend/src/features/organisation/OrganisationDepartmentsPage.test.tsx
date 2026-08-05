import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DepartmentPageResource, DepartmentResource } from '../../models/organisation'
import { OrganisationDepartmentsPage } from './OrganisationDepartmentsPage'

const departmentService = vi.hoisted(() => ({
  list:vi.fn(),
  get:vi.fn(),
  create:vi.fn(),
  update:vi.fn(),
  changeStatus:vi.fn(),
}))

vi.mock('../../services/api/departmentRestService', () => ({
  departmentRestService:departmentService,
}))

vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({
    session:{ user:{ organizationId:'organisation-1' } },
  }),
}))

const cardiology: DepartmentResource = {
  id:'department-1',
  organisationId:'organisation-1',
  name:'Cardiology',
  code:'CARD',
  description:'Heart and vascular medicine.',
  status:'ACTIVE',
  createdBy:'user-1',
  updatedBy:'user-1',
  createdAt:'2026-08-01T10:00:00Z',
  updatedAt:'2026-08-01T10:00:00Z',
  version:0,
}

const page = (items: DepartmentResource[]): DepartmentPageResource => ({
  items,
  page:0,
  size:100,
  totalElements:items.length,
  totalPages:items.length ? 1 : 0,
})

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<OrganisationDepartmentsPage/>, { wrapper:Wrapper })
}

describe('Organisation departments page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    departmentService.list.mockResolvedValue(page([cardiology]))
    departmentService.create.mockResolvedValue(cardiology)
    departmentService.update.mockResolvedValue(cardiology)
    departmentService.changeStatus.mockResolvedValue({
      ...cardiology,
      status:'INACTIVE',
      version:1,
    })
  })

  it('renders real department resources and filters by code', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name:'Cardiology' })).toBeInTheDocument()
    expect(screen.getAllByText('CARD').length).toBeGreaterThan(0)
    fireEvent.change(screen.getByPlaceholderText('Search name, code, or description'), {
      target:{ value:'unknown' },
    })
    expect(screen.getByRole('heading', { name:'No department found' })).toBeInTheDocument()
    expect(departmentService.list).toHaveBeenCalledOnce()
  })

  it('validates and creates a department without accepting an organisation id', async () => {
    const created = {
      ...cardiology,
      id:'department-2',
      name:'Neurology',
      code:'NEURO',
      description:null,
    }
    departmentService.create.mockResolvedValueOnce(created)
    departmentService.list
      .mockResolvedValueOnce(page([cardiology]))
      .mockResolvedValueOnce(page([created, cardiology]))
    renderPage()
    await screen.findByRole('heading', { name:'Cardiology' })

    fireEvent.click(screen.getByRole('button', { name:/add department/i }))
    fireEvent.change(screen.getByLabelText('Department name'), {
      target:{ value:'Neurology' },
    })
    fireEvent.change(screen.getByLabelText('Department code'), {
      target:{ value:'neuro' },
    })
    fireEvent.click(screen.getByRole('button', { name:'Create department' }))

    await waitFor(() => expect(departmentService.create).toHaveBeenCalledWith({
      name:'Neurology',
      code:'NEURO',
      description:null,
    }))
    expect(await screen.findByRole('heading', { name:'Neurology' })).toBeInTheDocument()
  })

  it('changes status using the resource version', async () => {
    renderPage()
    await screen.findByRole('heading', { name:'Cardiology' })

    fireEvent.click(screen.getByRole('button', { name:'Deactivate' }))

    await waitFor(() => expect(departmentService.changeStatus).toHaveBeenCalledWith(
      cardiology.id,
      { status:'INACTIVE', version:0 },
    ))
    expect(await screen.findByText('Cardiology is now inactive.')).toBeInTheDocument()
  })
})
