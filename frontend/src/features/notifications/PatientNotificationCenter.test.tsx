import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/api/ApiError'
import { patientRegistryRestService } from '../../services/api/patientRegistryRestService'
import { PatientNotificationCenter } from './PatientNotificationCenter'

vi.mock('../../app/auth/AuthProvider', ()=>({ useAuth:()=>({ session:{ user:{ id:'patient-user' } } }) }))
vi.mock('../../config/env', ()=>({ env:{ useAuthMocks:false } }))
vi.mock('./DoctorNotificationCenter', ()=>({ DoctorNotificationCenter:({ patientRegistrationId }: { patientRegistrationId: string })=>
  <output aria-label="Verified patient inbox">{patientRegistrationId}</output> }))
beforeEach(()=>vi.restoreAllMocks())
afterEach(()=>cleanup())
const registration = (id: string, status: 'ACTIVE'|'INACTIVE' = 'ACTIVE')=>({
  registrationId:id, organisationId:'organisation-' + id, medicalRecordNumber:'PT-' + id,
  firstName:'Synthetic', lastName:'Patient', status,
})
const setup = ()=>render(<QueryClientProvider client={new QueryClient({ defaultOptions:{ queries:{ retry:false } } })}>
  <MemoryRouter><PatientNotificationCenter/></MemoryRouter>
</QueryClientProvider>)

it('selects only active registrations returned by the own-account API', async ()=>{
  vi.spyOn(patientRegistryRestService,'listMyRegistrations').mockResolvedValue([
    registration('inactive', 'INACTIVE'), registration('first'), registration('second'),
  ])
  setup()
  expect(await screen.findByLabelText('Verified patient inbox')).toHaveTextContent('first')
  expect(screen.queryByRole('option', { name:'PT-inactive' })).not.toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Notification registration'), { target:{ value:'second' } })
  expect(screen.getByLabelText('Verified patient inbox')).toHaveTextContent('second')
})
it('does not invent a registration for an unlinked patient', async ()=>{
  vi.spyOn(patientRegistryRestService,'listMyRegistrations').mockRejectedValue(
    new ApiError({ status:404, title:'Not linked', type:'about:blank' }))
  setup()
  fireEvent.click(screen.getByRole('button', { name:'Notifications' }))
  expect(await screen.findByRole('link', { name:'Open appointments' })).toHaveAttribute('href', '/patient/appointments')
  expect(screen.queryByLabelText('Verified patient inbox')).not.toBeInTheDocument()
})
it('fails closed with retry when registration verification is unavailable', async ()=>{
  const list = vi.spyOn(patientRegistryRestService,'listMyRegistrations').mockRejectedValue(new Error('Unavailable'))
  setup()
  fireEvent.click(screen.getByRole('button', { name:'Notifications' }))
  fireEvent.click(await screen.findByRole('button', { name:'Retry' }))
  await waitFor(()=>expect(list).toHaveBeenCalledTimes(2))
  expect(screen.queryByLabelText('Verified patient inbox')).not.toBeInTheDocument()
})
