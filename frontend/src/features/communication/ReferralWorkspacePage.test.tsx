import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { ReferralResource } from '../../models/referral'
import { referralRestService } from '../../services/api/referralRestService'
import { sharedReferralRestService } from '../../services/api/sharedReferralRestService'
import { sharedDiagnosis } from '../../test/fixtures/sharedReferral'
import { ReferralWorkspacePage, referralActions } from './ReferralWorkspacePage'

const auth = vi.hoisted(() => ({ session:{ user:{ id:'recipient', organizationId:'org' } } }))
vi.mock('../../app/auth/AuthProvider', () => ({ useAuth:() => auth }))
const referral:ReferralResource = {
  referralType:'SECOND_OPINION',
  id:'referral-1', organisationId:'org', patientRegistrationId:'registration-1',
  senderUserId:'sender', senderDisplayName:'Synthetic Sender', recipientUserId:'recipient', recipientDisplayName:'Synthetic Recipient',
  reason:'Synthetic second opinion', priority:'ROUTINE', clinicalSummary:'Selected diagnosis review only.',
  purpose:'Second opinion', consentType:'RECORDED_WRITTEN', consentEvidenceReference:'synthetic-consent-1',
  consentRecordedAt:'2026-09-01T09:00:00Z', accessExpiresAt:'2099-09-09T09:00:00Z', status:'SENT',
  sharingGrantId:null, createdAt:'2026-09-01T09:00:00Z', sentAt:'2026-09-01T09:00:00Z', acceptedAt:null, activeAt:null,
  rejectedAt:null, completedAt:null, revokedAt:null, expiredAt:null, decisionReason:null, version:2,
  selectedItems:[{ id:'item-1', resourceType:'DIAGNOSIS', resourceId:'diagnosis-1' }],
}
function show(route = '/doctor/referrals') {
  const client = new QueryClient({ defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } } })
  return { client, ...render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[route]}><ReferralWorkspacePage/></MemoryRouter></QueryClientProvider>) }
}
beforeEach(() => {
  auth.session.user.id = 'recipient'
  vi.spyOn(referralRestService, 'list').mockResolvedValue({ content:[referral], page:0, size:20, totalElements:1, totalPages:1 })
  vi.spyOn(referralRestService, 'get').mockResolvedValue(referral)
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

it('uses participant-scoped direction filters and opens an authoritative detail', async () => {
  show()
  fireEvent.click(await screen.findByRole('button', { name:'Review referral' }, { timeout:3000 }))
  expect(await screen.findByText('diagnosis-1')).toBeInTheDocument()
  await waitFor(() => expect(referralRestService.get).toHaveBeenCalledWith('referral-1'))
  expect(await screen.findByRole('button', { name:'Accept referral' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name:'Revoke referral' })).not.toBeInTheDocument()
})

it('accepts only after confirmation with the reviewed version and displays server state', async () => {
  const command = vi.spyOn(referralRestService, 'command').mockResolvedValue({ ...referral, status:'ACTIVE', version:3 })
  show('/doctor/referrals?referral=referral-1')
  fireEvent.click(await screen.findByRole('button', { name:'Accept referral' }))
  expect(command).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name:'Confirm accept' }))
  await waitFor(() => expect(command).toHaveBeenCalledWith(referral.id, { action:'accept', expectedVersion:2 }))
  expect(await screen.findByRole('button', { name:'Complete referral' })).toBeInTheDocument()
  expect(screen.getByText('Referral updated by Communication Service.')).toBeInTheDocument()
})

it('requires a reason for rejection and does not claim success on backend failure', async () => {
  const command = vi.spyOn(referralRestService, 'command').mockRejectedValue(new Error('Version conflict: reload referral'))
  show('/doctor/referrals?referral=referral-1')
  fireEvent.click(await screen.findByRole('button', { name:'Reject referral' }))
  expect(screen.getByRole('button', { name:'Confirm reject' })).toBeDisabled()
  fireEvent.change(screen.getByLabelText('Decision reason'), { target:{ value:'Outside requested specialty' } })
  fireEvent.click(screen.getByRole('button', { name:'Confirm reject' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Version conflict')
  expect(command).toHaveBeenCalledWith(referral.id, { action:'reject', expectedVersion:2, reason:'Outside requested specialty' })
  expect(screen.queryByText('Referral updated by Communication Service.')).not.toBeInTheDocument()
})

it('blocks a confirmation when background recovery changes the reviewed version', async () => {
  const command = vi.spyOn(referralRestService, 'command')
  const { client } = show('/doctor/referrals?referral=referral-1')
  fireEvent.click(await screen.findByRole('button', { name:'Accept referral' }))
  act(() => client.setQueryData(['referral','recipient','org','referral-1'], { ...referral, version:3 }))
  await waitFor(() => expect(screen.getByRole('button', { name:'Confirm accept' })).toBeDisabled())
  expect(screen.getByRole('alert')).toHaveTextContent('The referral changed')
  expect(command).not.toHaveBeenCalled()
})

it('uses server direction pagination and displays empty/error states without seeded referrals', async () => {
  vi.mocked(referralRestService.list).mockResolvedValue({ content:[], page:0, size:20, totalElements:0, totalPages:0 })
  show()
  expect(await screen.findByText('No referrals in this view')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name:'Received' }))
  await waitFor(() => expect(referralRestService.list).toHaveBeenLastCalledWith('RECEIVED',0))
  vi.mocked(referralRestService.list).mockRejectedValue(new Error('Referral service unavailable'))
  fireEvent.click(screen.getByRole('button', { name:'Sent' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Referral service unavailable')
})

it('fails closed on denied detail even if the previous list contained the referral', async () => {
  vi.mocked(referralRestService.get).mockRejectedValue(new Error('Referral not found'))
  show('/doctor/referrals?referral=referral-1')
  expect(await screen.findByRole('alert')).toHaveTextContent('Referral not found')
  expect(screen.queryByRole('button', { name:'Accept referral' })).not.toBeInTheDocument()
  expect(screen.queryByText('diagnosis-1')).not.toBeInTheDocument()
})

it('clears an already opened selection when authoritative referral recovery is denied', async () => {
  vi.mocked(referralRestService.get).mockResolvedValue({ ...referral, status:'ACTIVE', sharingGrantId:'grant-1' })
  vi.spyOn(sharedReferralRestService, 'clinical').mockResolvedValue(sharedDiagnosis)
  const { client } = show('/doctor/referrals?referral=referral-1')
  fireEvent.click(await screen.findByRole('button', { name:'Open selected diagnosis' }))
  expect(await screen.findByText('Synthetic selected diagnosis')).toBeInTheDocument()
  vi.mocked(referralRestService.get).mockRejectedValue(new Error('Referral not found'))
  await act(async () => { await client.invalidateQueries({ queryKey:['referral','recipient','org','referral-1'] }) })
  expect(await screen.findByRole('alert')).toHaveTextContent('Referral not found')
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name:'Open selected diagnosis' })).not.toBeInTheDocument()
  expect(client.getQueryData(['referral','recipient','org','referral-1'])).not.toHaveProperty('diagnosis')
})

it.each([
  { action:'send' as const, status:'DRAFT' as const, user:'sender', result:'SENT' as const, label:'Send referral' },
  { action:'revoke' as const, status:'ACTIVE' as const, user:'sender', result:'REVOKED' as const, label:'Revoke referral' },
  { action:'complete' as const, status:'ACTIVE' as const, user:'recipient', result:'COMPLETED' as const, label:'Complete referral' },
])('persists $action through its versioned command', async ({ action,status,user,result,label }) => {
  auth.session.user.id = user
  vi.mocked(referralRestService.get).mockResolvedValue({ ...referral,status })
  const command = vi.spyOn(referralRestService,'command').mockResolvedValue({ ...referral,status:result,version:3 })
  show('/doctor/referrals?referral=referral-1')
  fireEvent.click(await screen.findByRole('button', { name:label }))
  if (action === 'revoke') fireEvent.change(screen.getByLabelText('Decision reason'), { target:{ value:'Consent withdrawn' } })
  fireEvent.click(screen.getByRole('button', { name:'Confirm ' + action }))
  await waitFor(() => expect(command).toHaveBeenCalledWith(referral.id, action === 'revoke' ? { action,expectedVersion:2,reason:'Consent withdrawn' } : { action,expectedVersion:2 }))
  expect(await screen.findByText('Referral updated by Communication Service.')).toBeInTheDocument()
})

it('restricts actions by participant, backend state and expiry', () => {
  expect(referralActions(referral,'sender')).toEqual(['revoke'])
  expect(referralActions(referral,'recipient')).toEqual(['accept','reject'])
  expect(referralActions(referral,'unrelated')).toEqual([])
  expect(referralActions({ ...referral,status:'REVOKED' },'recipient')).toEqual([])
  expect(referralActions({ ...referral,status:'ACTIVE' },'sender')).toEqual(['complete','revoke'])
  expect(referralActions({ ...referral,status:'DRAFT' },'sender')).toEqual(['send','revoke'])
  expect(referralActions(referral,'recipient',Date.parse(referral.accessExpiresAt))).toEqual([])
})

it('explains shared-treatment responsibility before acceptance without opening protected history', async () => {
  vi.mocked(referralRestService.get).mockResolvedValue({ ...referral, referralType:'SHARED_TREATMENT' })
  show('/doctor/referrals?referral=referral-1')
  fireEvent.click(await screen.findByRole('button', { name:'Accept referral' }))
  expect(screen.getByText(/Both doctors participate after acceptance/)).toBeInTheDocument()
  expect(screen.getByText(/Accept shared-treatment responsibility/)).toHaveTextContent('same-organisation finalised history')
  expect(screen.getByText(/After acceptance, open shared-care history/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name:'Open shared-care history' })).not.toBeInTheDocument()
  expect(screen.queryByText('Accept the referral and activate access only to its selected resources until expiry.')).not.toBeInTheDocument()
})
