import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { sharedDiagnosis, sharedFile, sharedReferral } from '../../test/fixtures/sharedReferral'
import type { ReferralResource } from '../../models/referral'
import type { SharedClinicalResource } from '../../models/sharedClinical'
import { sharedReferralRestService } from '../../services/api/sharedReferralRestService'
import { SharedReferralPreview } from './SharedReferralPreview'

const props = { referral:sharedReferral, userId:'recipient', organisationId:'org-1', now:Date.now() }
beforeEach(() => {
  vi.spyOn(sharedReferralRestService, 'clinical').mockResolvedValue(sharedDiagnosis)
  vi.spyOn(sharedReferralRestService, 'file').mockResolvedValue(sharedFile)
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers() })
const openDiagnosis = () => fireEvent.click(screen.getByRole('button', { name:'Open selected diagnosis' }))

it('waits for an explicit selection and displays only the selected child without edit or parent queries', async () => {
  render(<SharedReferralPreview {...props}/>)
  expect(sharedReferralRestService.clinical).not.toHaveBeenCalled()
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
  openDiagnosis()
  expect(await screen.findByText('Synthetic selected diagnosis')).toBeInTheDocument()
  expect(sharedReferralRestService.clinical).toHaveBeenCalledWith(sharedReferral, sharedReferral.selectedItems[0])
  expect(sharedReferralRestService.file).not.toHaveBeenCalled()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name:/save|finalise|append correction/i })).not.toBeInTheDocument()
  expect(localStorage.length).toBe(0)
  expect(sessionStorage.length).toBe(0)
})

it.each([
  { userId:'sender' }, { userId:'unrelated' }, { organisationId:'other-org' },
  { referral:{ ...sharedReferral, status:'SENT' as const, sharingGrantId:null } },
  { referral:{ ...sharedReferral, status:'REVOKED' as const } },
  { referral:{ ...sharedReferral, status:'COMPLETED' as const } },
  { referral:{ ...sharedReferral, sharingGrantId:null } },
  { now:Date.parse(sharedReferral.accessExpiresAt) },
])('never offers protected reads outside accepted recipient context: %j', change => {
  render(<SharedReferralPreview {...props} {...change}/>)
  expect(screen.queryByRole('button', { name:/open selected/i })).not.toBeInTheDocument()
  expect(sharedReferralRestService.clinical).not.toHaveBeenCalled()
  expect(sharedReferralRestService.file).not.toHaveBeenCalled()
})

it('drops content and late responses when the recipient identity changes or the preview closes', async () => {
  let resolve!:(value:SharedClinicalResource) => void
  vi.mocked(sharedReferralRestService.clinical).mockImplementationOnce(() => new Promise(done => { resolve = done }))
  const view = render(<SharedReferralPreview {...props}/>)
  openDiagnosis()
  fireEvent.click(screen.getByRole('button', { name:'Close preview' }))
  await act(async () => resolve(sharedDiagnosis))
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
  openDiagnosis()
  expect(await screen.findByText('Synthetic selected diagnosis')).toBeInTheDocument()
  view.rerender(<SharedReferralPreview {...props} userId="other-recipient"/>)
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
})

it('clears an open preview when the referral is revoked', async () => {
  const view = render(<SharedReferralPreview {...props}/>)
  openDiagnosis()
  expect(await screen.findByText('Synthetic selected diagnosis')).toBeInTheDocument()
  view.rerender(<SharedReferralPreview {...props} referral={{ ...sharedReferral, status:'REVOKED' }}/>)
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
})

it('keeps the earlier server access deadline after clearing expired payload', async () => {
  vi.mocked(sharedReferralRestService.clinical).mockResolvedValue({ ...sharedDiagnosis, validUntil:'2098-01-01T00:00:00Z' })
  const view = render(<SharedReferralPreview {...props}/>)
  openDiagnosis()
  expect(await screen.findByText('Synthetic selected diagnosis')).toBeInTheDocument()
  view.rerender(<SharedReferralPreview {...props} now={Date.parse('2098-01-01T00:00:01Z')}/>)
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('access window has ended'))
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
  expect(screen.queryByText('Verifying selected access…')).not.toBeInTheDocument()
})

it('clears content on a periodic access denial and stops background reads until explicit retry', async () => {
  vi.useFakeTimers()
  vi.mocked(sharedReferralRestService.clinical).mockResolvedValueOnce(sharedDiagnosis).mockRejectedValue(new Error('Denied'))
  render(<SharedReferralPreview {...props}/>)
  await act(async () => openDiagnosis())
  expect(screen.getByText('Synthetic selected diagnosis')).toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
  expect(screen.queryByText('Synthetic selected diagnosis')).not.toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent('preview has been cleared')
  await act(async () => { await vi.advanceTimersByTimeAsync(20_000) })
  expect(sharedReferralRestService.clinical).toHaveBeenCalledTimes(2)
})

it('renders protected file availability without inventing download access', async () => {
  vi.mocked(sharedReferralRestService.file).mockResolvedValue({ ...sharedFile, downloadAvailable:false, scanStatus:'PENDING' })
  render(<SharedReferralPreview {...props}/>)
  fireEvent.click(screen.getByRole('button', { name:'Open selected medical document' }))
  expect(await screen.findByText('synthetic-selected.pdf')).toBeInTheDocument()
  expect(screen.getByRole('button', { name:'Download selected file' })).toBeDisabled()
  expect(sharedReferralRestService.clinical).not.toHaveBeenCalled()
})

it('downloads authorised bytes on click and promptly revokes its temporary browser URL', async () => {
  const download = vi.spyOn(sharedReferralRestService, 'download').mockResolvedValue(new Blob(['synthetic']))
  const create = vi.fn().mockReturnValue('blob:synthetic-selected')
  const revoke = vi.fn()
  const OriginalURL = URL
  vi.stubGlobal('URL', Object.assign(class extends OriginalURL {}, { createObjectURL:create, revokeObjectURL:revoke }))
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  const view = render(<SharedReferralPreview {...props}/>)
  fireEvent.click(screen.getByRole('button', { name:'Open selected medical document' }))
  fireEvent.click(await screen.findByRole('button', { name:'Download selected file' }))
  expect(await screen.findByText('The authorised download has started.')).toBeInTheDocument()
  expect(download).toHaveBeenCalledWith(sharedReferral, sharedFile, expect.any(Function))
  expect(click).toHaveBeenCalledOnce()
  view.unmount()
  await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:synthetic-selected'))
})

it('does not start a browser download from bytes that arrive after closing the preview', async () => {
  let resolve!:(value:Blob) => void
  vi.spyOn(sharedReferralRestService, 'download').mockImplementation(() => new Promise(done => { resolve = done }))
  const create = vi.fn()
  const OriginalURL = URL
  vi.stubGlobal('URL', Object.assign(class extends OriginalURL {}, { createObjectURL:create, revokeObjectURL:vi.fn() }))
  render(<SharedReferralPreview {...props}/>)
  fireEvent.click(screen.getByRole('button', { name:'Open selected medical document' }))
  fireEvent.click(await screen.findByRole('button', { name:'Download selected file' }))
  fireEvent.click(screen.getByRole('button', { name:'Close preview' }))
  await act(async () => resolve(new Blob(['synthetic'])))
  expect(create).not.toHaveBeenCalled()
})

it('renders an explicitly shared final consultation and its correction provenance without loading its files', async () => {
  const referral:ReferralResource = { ...sharedReferral, selectedItems:[{ id:'whole-selection', resourceType:'CONSULTATION', resourceId:'consultation-1' }] }
  const record:SharedClinicalResource = {
    resourceType:'CONSULTATION', resourceId:'consultation-1', patientRegistrationId:'registration-1', validUntil:sharedReferral.accessExpiresAt,
    consultation:{
      id:'consultation-1', organisationId:'org-1', appointmentId:'appointment-1', patientRegistrationId:'registration-1', patientId:'patient-1', doctorUserId:'original-author', doctorMembershipId:'original-membership',
      status:'FINALIZED', reasonForConsultation:'Synthetic whole consultation', clinicalAssessment:'Selected assessment', treatmentPlan:'Selected plan', followUpInstructions:'Selected follow-up', additionalNotes:null,
      symptoms:[], history:[], vitalSigns:null, examinationFindings:[], diagnoses:[sharedDiagnosis.diagnosis], medications:[],
      corrections:[{ id:'correction-1', targetType:'CONSULTATION', targetId:null, fieldName:'clinicalAssessment', oldValue:'Original assessment', newValue:'Selected assessment', reason:'Synthetic correction reason', actorUserId:'correction-author', consultationVersion:2, correctedAt:'2026-09-01T09:10:00Z' }],
      finalizedAt:'2026-09-01T09:00:00Z', finalizedByUserId:'original-author', createdAt:'2026-09-01T08:00:00Z', updatedAt:'2026-09-01T09:10:00Z', version:2,
    },
  }
  vi.mocked(sharedReferralRestService.clinical).mockResolvedValue(record)
  render(<SharedReferralPreview {...props} referral={referral}/>)
  fireEvent.click(screen.getByRole('button', { name:'Open selected consultation' }))
  expect(await screen.findByText('Synthetic whole consultation')).toBeInTheDocument()
  expect(screen.getByText('Synthetic correction reason')).toBeInTheDocument()
  expect(screen.getByText('correction-author')).toBeInTheDocument()
  expect(sharedReferralRestService.file).not.toHaveBeenCalled()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})
