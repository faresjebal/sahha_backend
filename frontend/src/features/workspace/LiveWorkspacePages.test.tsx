import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { accountRestService } from '../../services/api/accountRestService'
import { appointmentRestService } from '../../services/api/appointmentRestService'
import { communicationRestService } from '../../services/api/communicationRestService'
import { organisationRestService } from '../../services/api/organisationRestService'
import { departmentRestService } from '../../services/api/departmentRestService'
import { staffDirectoryRestService } from '../../services/api/staffDirectoryRestService'
import { DoctorOverviewPage, DoctorPatientsPage, OrganisationOverviewPage, PlatformAccountsPage, UnavailablePage } from './LiveWorkspacePages'
import type { AppointmentResource } from '../../models/scheduling'
import { OwnProfilePage, OrganisationSettingsPage } from './ProfilePages'

vi.mock('../../app/auth/AuthProvider', () => ({ useAuth:() => ({ session:{ user:{ id:'own-account', organizationId:'own-org' } } }) }))
const account = { id:'own-account', email:'synthetic@example.test', firstName:'Synthetic', lastName:'Member', phoneNumber:null, status:'ACTIVE', emailVerified:true, version:4 }
const organisation = { id:'own-org', name:'Synthetic Clinic', legalName:null, type:'CLINIC' as const, status:'ACTIVE' as const, contactEmail:'clinic@example.test', phoneNumber:'+21670000000', address:'12 Synthetic Street', city:'Tunis', region:'Tunis', postalCode:'1000', countryCode:'TN', timeZone:'Africa/Tunis', createdBy:'platform-user', createdAt:'2026-08-01T00:00:00Z', updatedAt:'2026-08-01T00:00:00Z', version:2 }
function show(node:ReactNode) {
  const client = new QueryClient({ defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } } })
  return { client, ...render(<QueryClientProvider client={client}><MemoryRouter>{node}</MemoryRouter></QueryClientProvider>) }
}
beforeEach(() => { vi.spyOn(accountRestService, 'me').mockResolvedValue(account) })
afterEach(() => { cleanup(); vi.restoreAllMocks() })

it('loads real overview counts and refetches the selected day without demo fallback', async () => {
  const list = vi.spyOn(appointmentRestService, 'list').mockResolvedValue([])
  vi.spyOn(communicationRestService, 'conversations').mockResolvedValue({ content:[], page:0, size:20, totalElements:7, totalPages:1 })
  show(<DoctorOverviewPage/>)
  expect(await screen.findByText('7 conversations')).toBeInTheDocument()
  expect(await screen.findByText('No appointments on this date')).toBeInTheDocument()
  expect(screen.queryByText(/Nora|128 patients|all services stable/i)).not.toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Schedule date'), { target:{ value:'2030-02-14' } })
  await waitFor(() => expect(list).toHaveBeenLastCalledWith(new Date('2030-02-14T00:00:00').toISOString(), new Date('2030-02-15T00:00:00').toISOString()))
})

it('reports API failure instead of substituting a zero-count dashboard', async () => {
  vi.spyOn(appointmentRestService, 'list').mockRejectedValue(new Error('offline'))
  vi.spyOn(communicationRestService, 'conversations').mockResolvedValue({ content:[], page:0, size:20, totalElements:0, totalPages:0 })
  show(<DoctorOverviewPage/>)
  expect(await screen.findByRole('alert')).toHaveTextContent('offline')
  expect(screen.queryByText('Appointments on selected date')).not.toBeInTheDocument()
})

it.each(['overview','patients'])('selects only the current doctor’s %s rows without narrowing the administrative query cache', async page => {
  const visits = [
    { id:'own-appointment', doctorUserId:'own-account', patientRegistrationId:'own-registration', startsAt:'2030-02-14T09:00:00Z', locationLabel:'Own room', status:'CONFIRMED' },
    { id:'other-appointment', doctorUserId:'other-doctor', patientRegistrationId:'other-registration', startsAt:'2030-02-14T10:00:00Z', locationLabel:'Other room', status:'CONFIRMED' },
  ] as AppointmentResource[]
  vi.spyOn(appointmentRestService, 'list').mockResolvedValue(visits)
  vi.spyOn(communicationRestService, 'conversations').mockResolvedValue({ content:[], page:0, size:20, totalElements:0, totalPages:0 })
  const { client } = show(page === 'overview' ? <DoctorOverviewPage/> : <DoctorPatientsPage/>)
  await screen.findByText(page === 'overview' ? 'Own room' : 'Registration own-registration')
  expect(screen.queryByText(/Other room|other-registration/)).not.toBeInTheDocument()
  expect(client.getQueriesData({ queryKey:['appointments'] })[0][1]).toEqual(visits)
})

it('saves own profile with the loaded version and displays the authoritative response', async () => {
  const save = vi.spyOn(accountRestService, 'updateProfile').mockResolvedValue({ ...account, firstName:'Updated', version:5 })
  show(<OwnProfilePage/>)
  fireEvent.click(await screen.findByRole('button', { name:'Edit profile' }))
  fireEvent.change(screen.getByLabelText('First name'), { target:{ value:'Updated' } })
  fireEvent.click(screen.getByRole('button', { name:'Save profile' }))
  await waitFor(() => expect(save).toHaveBeenCalledWith({ firstName:'Updated', lastName:'Member', phoneNumber:null, version:4 }, expect.anything()))
  expect(await screen.findByRole('heading', { name:'Updated Member' })).toBeInTheDocument()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('retains the profile form and original identity when saving fails', async () => {
  vi.spyOn(accountRestService, 'updateProfile').mockRejectedValue(new Error('conflict'))
  show(<OwnProfilePage/>)
  fireEvent.click(await screen.findByRole('button', { name:'Edit profile' }))
  fireEvent.change(screen.getByLabelText('First name'), { target:{ value:'Unsaved' } })
  fireEvent.click(screen.getByRole('button', { name:'Save profile' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('conflict')
  expect(screen.getByRole('dialog')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name:'Synthetic Member' })).toBeInTheDocument()
})

it('uses admin-only counters and current organisation context', async () => {
  vi.spyOn(organisationRestService, 'listMyContexts').mockResolvedValue([{ organisationId:'own-org', organisationName:'Actual Clinic', organisationType:'CLINIC', membershipId:'member', membershipVersion:0, roles:['ORGANIZATION_ADMIN'] }])
  vi.spyOn(departmentRestService, 'list').mockResolvedValue({ items:[], page:0, size:1, totalElements:3, totalPages:3 })
  vi.spyOn(staffDirectoryRestService, 'list').mockResolvedValue({ items:[], page:0, size:1, totalElements:9, totalPages:9 })
  show(<OrganisationOverviewPage/>)
  expect(await screen.findByText('Actual Clinic')).toBeInTheDocument()
  expect(await screen.findByText('3')).toBeInTheDocument()
  expect(await screen.findByText('9')).toBeInTheDocument()
  expect(screen.queryByText(/revenue|occupancy|clinical notes/i)).not.toBeInTheDocument()
})

it('saves organisation profile with its current version and invalidates shell identity', async () => {
  vi.spyOn(organisationRestService, 'currentProfile').mockResolvedValue(organisation)
  const save = vi.spyOn(organisationRestService, 'updateCurrentProfile').mockResolvedValue({ ...organisation, name:'Updated Clinic', version:3 })
  const { client } = show(<OrganisationSettingsPage/>)
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  fireEvent.click(await screen.findByRole('button', { name:'Edit organisation profile' }))
  fireEvent.change(screen.getByLabelText('Organisation name'), { target:{ value:'Updated Clinic' } })
  fireEvent.click(screen.getByRole('button', { name:'Save organisation profile' }))
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ name:'Updated Clinic', version:2 }), expect.anything()))
  expect(await screen.findByRole('heading', { name:'Updated Clinic' })).toBeInTheDocument()
  expect(invalidate).toHaveBeenCalledWith({ queryKey:['organisation-contexts'] })
})

it('looks up exact account email and uses the returned account ID for status changes', async () => {
  const find = vi.spyOn(accountRestService, 'find').mockResolvedValue(account)
  const change = vi.spyOn(accountRestService, 'changeStatus').mockResolvedValue(undefined)
  show(<PlatformAccountsPage/>)
  fireEvent.change(screen.getByLabelText('Account email'), { target:{ value:account.email } })
  fireEvent.click(screen.getByRole('button', { name:'Find account' }))
  fireEvent.click(await screen.findByRole('button', { name:'Suspend account' }))
  await waitFor(() => expect(change).toHaveBeenCalledWith(account.id, 'SUSPEND'))
  expect(find).toHaveBeenCalledWith(account.email)
})

it('keeps deferred workspaces explicit without fake actions', () => {
  show(<UnavailablePage/>)
  expect(screen.getByText('No live workflow available')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name:/save|approve|submit/i })).not.toBeInTheDocument()
})
