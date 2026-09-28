import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { ClinicalRecordResource } from '../../models/clinical'
import type { MedicalFileResource } from '../../models/medicalFile'
import type { ReferralResource } from '../../models/referral'
import { communicationRestService } from '../../services/api/communicationRestService'
import { consultationRestService } from '../../services/api/consultationRestService'
import { medicalFileRestService } from '../../services/api/medicalFileRestService'
import { referralRestService } from '../../services/api/referralRestService'
import { ReferralComposer } from './ReferralComposer'
import { referralChoices, referralCreationSchema, selectedReferralItems } from './referralCreation'

const now = '2026-09-01T10:00:00Z'
const record:ClinicalRecordResource = {
  id:'consultation-1', organisationId:'org', appointmentId:'appointment-1', patientRegistrationId:'registration-1',
  patientId:'private-patient', doctorUserId:'sender', doctorMembershipId:'member-sender', status:'FINALIZED',
  reasonForConsultation:'Synthetic visit', clinicalAssessment:'Private assessment', treatmentPlan:'Private plan',
  followUpInstructions:null, additionalNotes:'Private notes', symptoms:[], vitalSigns:null, examinationFindings:[],
  diagnoses:[{ id:'diagnosis-1', code:null, codeSystem:null, label:'Synthetic diagnosis', type:'PRIMARY', status:'CONFIRMED', notes:null }],
  medications:[{ id:'medication-1', kind:'CURRENT', name:'Synthetic medicine', strength:null, form:null, dosage:null, frequency:null, route:null, duration:null, quantity:null, specialInstructions:null }],
  history:[{ id:'allergy-1', category:'ALLERGY', description:'Synthetic allergy', notes:null }, { id:'history-1', category:'MEDICAL', description:'Private medical history', notes:null }],
  corrections:[], finalizedAt:now, finalizedByUserId:'sender', createdAt:now, updatedAt:now, version:4,
}
const file:MedicalFileResource = { fileId:'file-1', consultationId:record.id, originalFilename:'synthetic.pdf',
  contentType:'application/pdf', size:123, uploadStatus:'STORED', scanStatus:'CLEAN', downloadAvailable:true,
  createdAt:now, uploadedAt:now, availableAt:now, rejectedAt:null }
const sourcePage = { content:[{ consultationId:record.id, patientRegistrationId:record.patientRegistrationId,
  appointmentId:record.appointmentId, finalizedAt:now, version:4 }], page:0, size:20, totalElements:1, totalPages:1 }
const doctor = { membershipId:'member-recipient', organisationId:'org', userId:'recipient', displayName:'Synthetic Colleague', membershipVersion:0 }
const doctorPage = { content:[doctor, { ...doctor, userId:'sender', membershipId:'member-sender', displayName:'Self' }], page:0, size:100, totalElements:2, totalPages:1 }
const result:ReferralResource = { id:'referral-created', organisationId:'org', patientRegistrationId:record.patientRegistrationId,
  referralType:'SECOND_OPINION',
  senderUserId:'sender', senderDisplayName:'Synthetic Sender', recipientUserId:'recipient', recipientDisplayName:doctor.displayName,
  reason:'Synthetic second opinion', priority:'ROUTINE', clinicalSummary:null, purpose:'Second opinion', consentType:'RECORDED_WRITTEN',
  consentEvidenceReference:'synthetic-consent', consentRecordedAt:now, accessExpiresAt:'2099-01-01T00:00:00Z', status:'SENT',
  sharingGrantId:null, createdAt:now, sentAt:now, acceptedAt:null, activeAt:null, rejectedAt:null, completedAt:null, revokedAt:null,
  expiredAt:null, decisionReason:null, version:0, selectedItems:[{ id:'item-1', resourceType:'DIAGNOSIS', resourceId:'diagnosis-1' }],
}
function localInput(offset:number) {
  const date = new Date(Date.now() + offset)
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0,16)
}
function show() {
  const client = new QueryClient({ defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } } })
  const created = vi.fn(), close = vi.fn()
  render(<QueryClientProvider client={client}><MemoryRouter><ReferralComposer userId="sender" organisationId="org" close={close} created={created}/></MemoryRouter></QueryClientProvider>)
  return { client, created, close }
}
async function openForm() {
  fireEvent.click(await screen.findByRole('button', { name:'Use consultation' }))
  await screen.findByLabelText('Referral reason')
  await waitFor(() => expect(screen.getByRole('button', { name:'Review and send' })).toBeEnabled())
}
function fill(type:'SECOND_OPINION' | 'SHARED_TREATMENT' = 'SECOND_OPINION') {
  if (type === 'SHARED_TREATMENT') fireEvent.change(screen.getByLabelText('Referral type'), { target:{ value:type } })
  fireEvent.change(screen.getByLabelText('Recipient colleague'), { target:{ value:'recipient' } })
  fireEvent.change(screen.getByLabelText('Referral reason'), { target:{ value:'Synthetic second opinion' } })
  fireEvent.change(screen.getByLabelText('Sharing purpose'), { target:{ value:'Second opinion' } })
  fireEvent.change(screen.getByLabelText('Recorded consent or legal basis'), { target:{ value:'RECORDED_WRITTEN' } })
  fireEvent.change(screen.getByLabelText('Consent evidence reference'), { target:{ value:'synthetic-consent' } })
  fireEvent.change(screen.getByLabelText('Consent recorded at'), { target:{ value:localInput(-3600_000) } })
  fireEvent.change(screen.getByLabelText('Access expires at'), { target:{ value:localInput(86400_000) } })
  if (type === 'SECOND_OPINION') fireEvent.click(screen.getByLabelText('Diagnosis: Synthetic diagnosis'))
  else fireEvent.click(screen.getByLabelText(/I acknowledge that both doctors/))
  fireEvent.click(screen.getByLabelText(/I confirm the/))
}
async function review(send = true) {
  fill()
  fireEvent.click(screen.getByRole('button', { name:send ? 'Review and send' : 'Review draft' }))
  await screen.findByRole('button', { name:send ? 'Confirm and send' : 'Confirm draft' })
}
beforeEach(() => {
  vi.spyOn(consultationRestService, 'listReferralSources').mockResolvedValue(sourcePage)
  vi.spyOn(consultationRestService, 'getRecord').mockResolvedValue(record)
  vi.spyOn(medicalFileRestService, 'list').mockResolvedValue([file, { ...file, fileId:'pending', originalFilename:'pending.pdf', scanStatus:'PENDING', downloadAvailable:false }, { ...file, fileId:'foreign', consultationId:'foreign', originalFilename:'foreign.pdf' }])
  vi.spyOn(communicationRestService, 'doctors').mockResolvedValue(doctorPage)
  vi.spyOn(referralRestService, 'create').mockResolvedValue(result)
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

it('discovers minimal author sources before loading a record, with no preselected resource or consent', async () => {
  show()
  await screen.findByRole('button', { name:'Use consultation' })
  expect(consultationRestService.getRecord).not.toHaveBeenCalled()
  await openForm()
  expect(screen.getAllByRole('checkbox').every(item => !(item as HTMLInputElement).checked)).toBe(true)
  expect(screen.getByLabelText('Recorded consent or legal basis')).toHaveValue('')
  expect(screen.getByLabelText('Referral type')).toHaveValue('SECOND_OPINION')
  expect(screen.getByLabelText('Optional clinical summary')).toHaveValue('')
  expect(screen.queryByText('Private medical history')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('File: pending.pdf')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('File: foreign.pdf')).not.toBeInTheDocument()
  expect(screen.queryByRole('option', { name:'Self' })).not.toBeInTheDocument()
})

it.each([true, false])('creates a reviewed referral with sendImmediately=%s and only explicit IDs', async sendImmediately => {
  const { created } = show(); await openForm(); await review(sendImmediately)
  expect(referralRestService.create).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name:sendImmediately ? 'Confirm and send' : 'Confirm draft' }))
  await waitFor(() => expect(created).toHaveBeenCalledWith(result))
  const command = vi.mocked(referralRestService.create).mock.calls[0][0]
  expect(command).toMatchObject({ recipientUserId:'recipient', patientRegistrationId:'registration-1', sourceConsultationId:'consultation-1', sendImmediately,
    referralType:'SECOND_OPINION', selectedItems:[{ resourceType:'DIAGNOSIS', resourceId:'diagnosis-1' }], clinicalSummary:null,
    consentType:'RECORDED_WRITTEN', consentEvidenceReference:'synthetic-consent' })
  expect(command.referralRequestId).toMatch(/^[0-9a-f-]{36}$/)
  expect(JSON.stringify(command)).not.toContain('Private')
  expect(consultationRestService.listReferralSources).toHaveBeenCalledTimes(2)
  expect(consultationRestService.getRecord).toHaveBeenCalledTimes(2)
  expect(medicalFileRestService.list).toHaveBeenCalledTimes(2)
})

it('requires valid consent, a future bounded expiry, selected items and an explicit review', async () => {
  show(); await openForm()
  fireEvent.click(screen.getByRole('button', { name:'Review and send' }))
  expect((await screen.findAllByRole('alert')).length).toBeGreaterThan(5)
  expect(referralRestService.create).not.toHaveBeenCalled()
  expect(screen.queryByRole('button', { name:'Confirm and send' })).not.toBeInTheDocument()
})

it('whole-consultation selection removes redundant child selections but not explicitly chosen files', async () => {
  show(); await openForm()
  fireEvent.click(screen.getByLabelText('Diagnosis: Synthetic diagnosis'))
  fireEvent.click(screen.getByLabelText('File: synthetic.pdf'))
  fireEvent.click(screen.getByLabelText(/Whole consultation/))
  expect(screen.getByLabelText('Diagnosis: Synthetic diagnosis')).not.toBeChecked()
  expect(screen.getByLabelText('Diagnosis: Synthetic diagnosis')).toBeDisabled()
  expect(screen.getByLabelText('File: synthetic.pdf')).toBeChecked()
})

it.each(['version', 'file', 'source', 'recipient', 'outage'])('blocks stale or unavailable %s during final verification', async condition => {
  const { created } = show(); await openForm(); fill()
  if (condition === 'file') { fireEvent.click(screen.getByLabelText('File: synthetic.pdf')); fireEvent.click(screen.getByLabelText(/I confirm the selected resources/)) }
  fireEvent.click(screen.getByRole('button', { name:'Review and send' }))
  await screen.findByRole('button', { name:'Confirm and send' })
  if (condition === 'version') vi.mocked(consultationRestService.getRecord).mockResolvedValue({ ...record, version:5 })
  if (condition === 'file') vi.mocked(medicalFileRestService.list).mockResolvedValue([{ ...file, scanStatus:'REJECTED', downloadAvailable:false }])
  if (condition === 'source') vi.mocked(consultationRestService.listReferralSources).mockResolvedValue({ ...sourcePage, content:[] })
  if (condition === 'recipient') vi.mocked(communicationRestService.doctors).mockResolvedValue({ ...doctorPage, content:[] })
  if (condition === 'outage') vi.mocked(consultationRestService.getRecord).mockRejectedValue(new Error('Authorisation unavailable'))
  fireEvent.click(screen.getByRole('button', { name:'Confirm and send' }))
  await screen.findByRole('alert')
  expect(referralRestService.create).not.toHaveBeenCalled()
  expect(created).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name:'Back to edit' })).toBeEnabled()
})

it('retains the frozen request and idempotency UUID after an ambiguous failure', async () => {
  vi.mocked(referralRestService.create).mockRejectedValueOnce(new Error('Response lost')).mockResolvedValueOnce(result)
  const { created } = show(); await openForm(); await review()
  fireEvent.click(screen.getByRole('button', { name:'Confirm and send' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Response lost')
  expect(created).not.toHaveBeenCalled()
  expect(screen.queryByRole('button', { name:'Back to edit' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name:'Retry same request' }))
  await waitFor(() => expect(created).toHaveBeenCalledWith(result))
  expect(vi.mocked(referralRestService.create).mock.calls[1][0]).toEqual(vi.mocked(referralRestService.create).mock.calls[0][0])
})

it.each(['draft', 'foreign', 'denied'])('does not load files or display source selections for a %s record', async condition => {
  if (condition === 'draft') vi.mocked(consultationRestService.getRecord).mockResolvedValue({ ...record, status:'DRAFT' })
  if (condition === 'foreign') vi.mocked(consultationRestService.getRecord).mockResolvedValue({ ...record, doctorUserId:'unrelated' })
  if (condition === 'denied') vi.mocked(consultationRestService.getRecord).mockRejectedValue(new Error('Not found'))
  show(); fireEvent.click(await screen.findByRole('button', { name:'Use consultation' }))
  await screen.findByRole('alert')
  expect(medicalFileRestService.list).not.toHaveBeenCalled()
  expect(screen.queryByLabelText('Diagnosis: Synthetic diagnosis')).not.toBeInTheDocument()
  expect(referralRestService.create).not.toHaveBeenCalled()
})

it('shows real empty and denied discovery states without mock sources', async () => {
  vi.mocked(consultationRestService.listReferralSources).mockResolvedValue({ ...sourcePage, content:[] })
  const { client } = show()
  await screen.findByText('No eligible finalised consultations')
  vi.mocked(consultationRestService.listReferralSources).mockRejectedValue(new Error('Membership denied'))
  await client.invalidateQueries({ queryKey:['referral-sources'] })
  expect(await screen.findByRole('alert')).toHaveTextContent('Membership denied')
  expect(screen.queryByRole('button', { name:'Use consultation' })).not.toBeInTheDocument()
})

it('paginates recipients without submitting the form and clears the previous selection', async () => {
  vi.mocked(communicationRestService.doctors).mockResolvedValue({ ...doctorPage, totalPages:2 })
  show(); await openForm(); fill()
  fireEvent.click(screen.getByRole('button', { name:'Next page' }))
  await waitFor(() => expect(communicationRestService.doctors).toHaveBeenLastCalledWith('org',1))
  expect(await screen.findByLabelText('Recipient colleague')).toHaveValue('')
  expect(screen.queryByRole('button', { name:'Confirm draft' })).not.toBeInTheDocument()
})

it('validates date boundaries, the selection cap and stale or duplicated selection keys', () => {
  const valid = { referralType:'SECOND_OPINION', sharedCareConfirmed:false, recipientUserId:'recipient', reason:'Synthetic opinion', priority:'ROUTINE', clinicalSummary:'', purpose:'Second opinion',
    consentType:'RECORDED_WRITTEN', consentEvidenceReference:'evidence', consentRecordedAt:localInput(-3600_000),
    accessExpiresAt:localInput(86400_000), selections:['DIAGNOSIS:diagnosis-1'], reviewed:true }
  expect(referralCreationSchema.safeParse(valid).success).toBe(true)
  for (const invalid of [{ consentRecordedAt:localInput(86400_000) }, { accessExpiresAt:localInput(-86400_000) },
    { accessExpiresAt:localInput(91 * 86400_000) }, { accessExpiresAt:'bad' }, { selections:[] },
    { selections:Array.from({ length:51 }, (_, index) => String(index)) }, { reviewed:false }]) {
    expect(referralCreationSchema.safeParse({ ...valid, ...invalid }).success).toBe(false)
  }
  const choices = referralChoices(record, [file])
  expect(() => selectedReferralItems(['MEDICAL_DOCUMENT:unknown'], choices)).toThrow('changed')
  expect(() => selectedReferralItems(['DIAGNOSIS:diagnosis-1', 'DIAGNOSIS:diagnosis-1'], choices)).toThrow('changed')
  const shared = { ...valid, referralType:'SHARED_TREATMENT', sharedCareConfirmed:true, selections:[] }
  expect(referralCreationSchema.safeParse(shared).success).toBe(true)
  for (const change of [{ sharedCareConfirmed:false }, { selections:['DIAGNOSIS:diagnosis-1'] },
    { reviewed:false }, { consentType:undefined }, { referralType:'ALL_RECORDS' }]) {
    expect(referralCreationSchema.safeParse({ ...shared, ...change }).success).toBe(false)
  }
})

it.each([true, false])('creates explicit shared treatment with no selected IDs and sendImmediately=%s', async sendImmediately => {
  const response = { ...result, referralType:'SHARED_TREATMENT' as const, selectedItems:[], status:sendImmediately ? 'SENT' as const : 'DRAFT' as const }
  vi.mocked(referralRestService.create).mockResolvedValue(response)
  const { created } = show(); await openForm(); fill('SHARED_TREATMENT')
  expect(screen.queryByLabelText('Diagnosis: Synthetic diagnosis')).not.toBeInTheDocument()
  expect(screen.getByText(/After acceptance, both doctors are responsible/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name:sendImmediately ? 'Review and send' : 'Review draft' }))
  expect(await screen.findByRole('heading', { name:'Referral type: Shared treatment' })).toBeInTheDocument()
  expect(screen.getByText(/Shared-treatment scope: both doctors treat/)).toHaveTextContent('later finalised encounters')
  expect(screen.queryByText(/Only these resources will be selected/)).not.toBeInTheDocument()
  expect(referralRestService.create).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name:sendImmediately ? 'Confirm and send' : 'Confirm draft' }))
  await waitFor(() => expect(created).toHaveBeenCalledWith(response))
  expect(referralRestService.create).toHaveBeenCalledWith(expect.objectContaining({
    referralType:'SHARED_TREATMENT', selectedItems:[], sourceConsultationId:'consultation-1',
    patientRegistrationId:'registration-1', consentType:'RECORDED_WRITTEN', sendImmediately,
  }))
  expect(medicalFileRestService.list).toHaveBeenCalledTimes(1) // No selected-file preflight for the care scope.
  expect(consultationRestService.getRecord).toHaveBeenCalledTimes(2)
})

it('clears narrower selections, purpose, consent evidence and confirmations whenever referral type changes', async () => {
  show(); await openForm(); fill()
  fireEvent.change(screen.getByLabelText('Referral type'), { target:{ value:'SHARED_TREATMENT' } })
  expect(screen.getByLabelText('Sharing purpose')).toHaveValue('')
  expect(screen.getByLabelText('Recorded consent or legal basis')).toHaveValue('')
  expect(screen.getByLabelText('Consent evidence reference')).toHaveValue('')
  expect(screen.getByLabelText('Consent recorded at')).toHaveValue('')
  expect(screen.getAllByRole('checkbox').every(item => !(item as HTMLInputElement).checked)).toBe(true)
  fill('SHARED_TREATMENT')
  fireEvent.click(screen.getByLabelText(/I acknowledge that both doctors/))
  fireEvent.click(screen.getByRole('button', { name:'Review and send' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Acknowledge the shared-treatment')
  expect(screen.queryByRole('button', { name:'Confirm and send' })).not.toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Referral type'), { target:{ value:'SECOND_OPINION' } })
  await screen.findByLabelText('Diagnosis: Synthetic diagnosis')
  expect(screen.getAllByRole('checkbox').every(item => !(item as HTMLInputElement).checked)).toBe(true)
  expect(screen.getByLabelText('Recorded consent or legal basis')).toHaveValue('')
  expect(referralRestService.create).not.toHaveBeenCalled()
})

it('can choose shared treatment during a selected-file outage without bypassing source/recipient checks', async () => {
  vi.mocked(medicalFileRestService.list).mockRejectedValue(new Error('File directory unavailable'))
  const response = { ...result, referralType:'SHARED_TREATMENT' as const, selectedItems:[] }
  vi.mocked(referralRestService.create).mockResolvedValue(response)
  const { created } = show()
  fireEvent.click(await screen.findByRole('button', { name:'Use consultation' }))
  await screen.findByLabelText('Referral type')
  await screen.findByRole('alert')
  expect(screen.getByRole('button', { name:'Review and send' })).toBeDisabled()
  fill('SHARED_TREATMENT')
  expect(screen.getByRole('button', { name:'Review and send' })).toBeEnabled()
  fireEvent.click(screen.getByRole('button', { name:'Review and send' }))
  fireEvent.click(await screen.findByRole('button', { name:'Confirm and send' }))
  await waitFor(() => expect(created).toHaveBeenCalledWith(response))
  expect(medicalFileRestService.list).toHaveBeenCalledTimes(1)
})

it.each(['source', 'recipient', 'version', 'outage'])('refuses shared-treatment creation after changed %s preflight', async condition => {
  show(); await openForm(); fill('SHARED_TREATMENT')
  fireEvent.click(screen.getByRole('button', { name:'Review and send' }))
  await screen.findByRole('button', { name:'Confirm and send' })
  if (condition === 'source') vi.mocked(consultationRestService.listReferralSources).mockResolvedValue({ ...sourcePage, content:[] })
  if (condition === 'recipient') vi.mocked(communicationRestService.doctors).mockResolvedValue({ ...doctorPage, content:[] })
  if (condition === 'version') vi.mocked(consultationRestService.getRecord).mockResolvedValue({ ...record, version:5 })
  if (condition === 'outage') vi.mocked(consultationRestService.getRecord).mockRejectedValue(new Error('Source unavailable'))
  fireEvent.click(screen.getByRole('button', { name:'Confirm and send' }))
  await screen.findByRole('alert')
  expect(referralRestService.create).not.toHaveBeenCalled()
})

it('retries the exact shared-treatment type, consent and request ID after an ambiguous response', async () => {
  const response = { ...result, referralType:'SHARED_TREATMENT' as const, selectedItems:[] }
  vi.mocked(referralRestService.create).mockRejectedValueOnce(new Error('Response lost')).mockResolvedValueOnce(response)
  const { created } = show(); await openForm(); fill('SHARED_TREATMENT')
  fireEvent.click(screen.getByRole('button', { name:'Review and send' }))
  fireEvent.click(await screen.findByRole('button', { name:'Confirm and send' }))
  await screen.findByRole('alert')
  expect(screen.queryByRole('button', { name:'Back to edit' })).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Referral type')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name:'Retry same request' }))
  await waitFor(() => expect(created).toHaveBeenCalledWith(response))
  const calls = vi.mocked(referralRestService.create).mock.calls
  expect(calls[1][0]).toEqual(calls[0][0])
  expect(calls[0][0].referralType).toBe('SHARED_TREATMENT')
})
