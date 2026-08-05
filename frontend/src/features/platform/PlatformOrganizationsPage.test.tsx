import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { OrganisationPageResource, OrganisationResource } from '../../models/organisation'
import { ApiError } from '../../services/api/ApiError'
import { PlatformOrganizationsPage } from './PlatformOrganizationsPage'

const organisationService = vi.hoisted(() => ({
  list:vi.fn(),
  get:vi.fn(),
  create:vi.fn(),
  listAdministrators:vi.fn(),
  getAdministrator:vi.fn(),
  assignAdministrator:vi.fn(),
}))

vi.mock('../../services/api/organisationRestService', () => ({
  organisationRestService:organisationService,
}))

const clinic: OrganisationResource = {
  id:'0adfbe08-5c0f-494a-b4c1-5902e68eab7d',
  name:'Sahha Clinic Tunis',
  legalName:'Sahha Clinic SARL',
  type:'CLINIC',
  status:'ACTIVE',
  contactEmail:'contact@sahha.test',
  phoneNumber:'+21670000000',
  address:'1 Health Avenue',
  city:'Tunis',
  region:'Tunis',
  postalCode:'1000',
  countryCode:'TN',
  timeZone:'Africa/Tunis',
  createdBy:'10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98',
  createdAt:'2026-07-31T10:00:00Z',
  updatedAt:'2026-07-31T10:00:00Z',
  version:0,
}

const page = (items: OrganisationResource[]): OrganisationPageResource => ({
  items,
  page:0,
  size:100,
  totalElements:items.length,
  totalPages:items.length ? 1 : 0,
})

const administrator = {
  id:'1605d039-158d-4db4-8dac-a9e581413a92',
  organisationId:clinic.id,
  userId:'0ce89700-962e-41a1-b298-8ff7b17f0b5c',
  email:'administrator@sahha.test',
  displayName:'Leila Mansour',
  status:'ACTIVE' as const,
  roles:['ORGANIZATION_ADMIN'] as const,
  joinedAt:'2026-07-31T11:00:00Z',
  createdBy:clinic.createdBy,
  createdAt:'2026-07-31T11:00:00Z',
  updatedAt:'2026-07-31T11:00:00Z',
  version:0,
}

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<PlatformOrganizationsPage/>, { wrapper:Wrapper })
}

describe('Platform Administrator organisations page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    organisationService.list.mockResolvedValue(page([clinic]))
    organisationService.get.mockResolvedValue(clinic)
    organisationService.create.mockResolvedValue(clinic)
    organisationService.listAdministrators.mockResolvedValue({
      items:[], page:0, size:20, totalElements:0, totalPages:0,
    })
    organisationService.getAdministrator.mockResolvedValue(administrator)
    organisationService.assignAdministrator.mockResolvedValue(administrator)
  })

  it('renders Organisation Service data instead of demo organisations', async () => {
    renderPage()

    expect(await screen.findAllByText('Sahha Clinic Tunis')).not.toHaveLength(0)
    expect(screen.getByText('Contact').parentElement).toHaveTextContent('contact@sahha.test')
    expect(screen.queryByText(/St\. Helena/i)).not.toBeInTheDocument()
    expect(organisationService.list).toHaveBeenCalledOnce()
    expect(organisationService.get).toHaveBeenCalledWith(clinic.id)
  })

  it('validates and creates a new active organisation', async () => {
    const created = {
      ...clinic,
      id:'564d9c3e-3595-49b5-9a76-923d17f63c29',
      name:'Carthage Medical Centre',
      legalName:null,
      type:'HOSPITAL' as const,
      contactEmail:'admin@carthage.test',
      phoneNumber:'+21671000000',
      address:'20 Carthage Road',
      city:'Carthage',
      region:'Tunis',
      postalCode:null,
    }
    organisationService.list
      .mockResolvedValueOnce(page([clinic]))
      .mockResolvedValueOnce(page([created, clinic]))
    organisationService.create.mockResolvedValueOnce(created)
    organisationService.get.mockImplementation((id: string) =>
      Promise.resolve(id === created.id ? created : clinic),
    )
    renderPage()
    await screen.findAllByText('Sahha Clinic Tunis')

    fireEvent.click(screen.getByRole('button', { name:'Add organization' }))
    fireEvent.change(screen.getByLabelText('Organization name'), { target:{ value:'Carthage Medical Centre' } })
    fireEvent.change(screen.getByLabelText(/Organization type/), { target:{ value:'HOSPITAL' } })
    fireEvent.change(screen.getByLabelText('Contact email'), { target:{ value:'admin@carthage.test' } })
    fireEvent.change(screen.getByLabelText('Phone number'), { target:{ value:'+21671000000' } })
    fireEvent.change(screen.getByLabelText('Street address'), { target:{ value:'20 Carthage Road' } })
    fireEvent.change(screen.getByLabelText('City'), { target:{ value:'Carthage' } })
    fireEvent.change(screen.getByLabelText('Region'), { target:{ value:'Tunis' } })
    fireEvent.click(screen.getByRole('button', { name:'Create organization' }))

    await waitFor(() => expect(organisationService.create).toHaveBeenCalledWith({
      name:'Carthage Medical Centre',
      legalName:null,
      type:'HOSPITAL',
      contactEmail:'admin@carthage.test',
      phoneNumber:'+21671000000',
      address:'20 Carthage Road',
      city:'Carthage',
      region:'Tunis',
      postalCode:null,
      countryCode:'TN',
      timeZone:'Africa/Tunis',
    }))
    expect(await screen.findByText(/Carthage Medical Centre is active/)).toBeInTheDocument()
  })

  it('explains when the signed user lacks Platform Administrator authority', async () => {
    organisationService.list.mockRejectedValue(new ApiError({
      type:'urn:sahha:problem:request-forbidden',
      title:'Request forbidden',
      status:403,
    }))
    renderPage()

    expect(await screen.findByText('This action requires the Platform Administrator role.'))
      .toBeInTheDocument()
  })

  it('assigns an existing account as the selected organization administrator', async () => {
    organisationService.listAdministrators
      .mockResolvedValueOnce({ items:[], page:0, size:20, totalElements:0, totalPages:0 })
      .mockResolvedValueOnce({ items:[administrator], page:0, size:20, totalElements:1, totalPages:1 })
    renderPage()
    await screen.findAllByText('Sahha Clinic Tunis')

    fireEvent.click(await screen.findByRole('button', { name:'Assign' }))
    fireEvent.change(screen.getByLabelText('Account email'), {
      target:{ value:'  ADMINISTRATOR@SAHHA.TEST  ' },
    })
    fireEvent.click(screen.getByRole('button', { name:'Assign administrator' }))

    await waitFor(() => expect(organisationService.assignAdministrator)
      .toHaveBeenCalledWith(clinic.id, { email:'administrator@sahha.test' }))
    expect(await screen.findByText(/Leila Mansour is now an Organisation Administrator/))
      .toBeInTheDocument()
    expect(screen.getAllByText('Leila Mansour').length).toBeGreaterThan(0)
  })
})
