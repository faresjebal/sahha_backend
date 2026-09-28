import { afterEach, expect, it, vi } from 'vitest'
import { httpClient } from './httpClient'
import { sharedCareRestService } from './sharedCareRestService'
import { careHistory, careRecord, careReferral } from '../../test/fixtures/sharedCare'
import { sharedReferral } from '../../test/fixtures/sharedReferral'

afterEach(() => vi.restoreAllMocks())
it('uses only Gateway paths and no-store for bounded history and a separately authorised record', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValueOnce(careHistory).mockResolvedValueOnce(careRecord)
  expect(await sharedCareRestService.list(careReferral)).toEqual(careHistory)
  expect(request).toHaveBeenNthCalledWith(1, '/clinical/shared-care/registration-1/consultations?page=0&size=20', { cache:'no-store' })
  expect(await sharedCareRestService.record(careReferral, 'consultation-1')).toEqual(careRecord)
  expect(request).toHaveBeenNthCalledWith(2, '/clinical/shared-care/registration-1/consultations/consultation-1', { cache:'no-store' })
})
it.each([
  sharedReferral, { ...careReferral, status:'SENT' as const }, { ...careReferral, status:'REVOKED' as const },
  { ...careReferral, sharingGrantId:null }, { ...careReferral, accessExpiresAt:'2000-01-01T00:00:00Z' },
])('never requests patient-wide history without explicit active shared treatment', async referral => {
  const request = vi.spyOn(httpClient, 'request')
  await expect(sharedCareRestService.list(referral)).rejects.toThrow('could not be verified')
  await expect(sharedCareRestService.record(referral, 'consultation-1')).rejects.toThrow('could not be verified')
  expect(request).not.toHaveBeenCalled()
})
it.each([
  { organisationId:'other-org' }, { patientRegistrationId:'other-patient' }, { validUntil:'2000-01-01T00:00:00Z' },
  { page:1 }, { size:51 }, { totalElements:-1 }, { totalPages:99 }, { content:null },
  { content:[careHistory.content[0], careHistory.content[0]], totalElements:2 },
  { content:[{ ...careHistory.content[0], finalizedAt:'invalid' }] },
])('rejects malformed, mismatched or expired history responses', async change => {
  vi.spyOn(httpClient, 'request').mockResolvedValue({ ...careHistory, ...change })
  await expect(sharedCareRestService.list(careReferral)).rejects.toThrow('could not be verified')
})
it.each([
  { id:'other-record' }, { organisationId:'other-org' }, { patientRegistrationId:'other-patient' },
  { status:'DRAFT' }, { finalizedAt:null }, { diagnoses:null },
])('rejects drafts and record-level scope substitutions', async change => {
  vi.spyOn(httpClient, 'request').mockResolvedValue({ ...careRecord, consultation:{ ...careRecord.consultation, ...change } })
  await expect(sharedCareRestService.record(careReferral, 'consultation-1')).rejects.toThrow('could not be verified')
})
it('encodes identifiers and rejects invalid pages before sending', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValue({ ...careHistory, patientRegistrationId:'registration/id' })
  await sharedCareRestService.list({ ...careReferral, patientRegistrationId:'registration/id' })
  expect(request).toHaveBeenCalledWith('/clinical/shared-care/registration%2Fid/consultations?page=0&size=20', { cache:'no-store' })
  for (const page of [-1, 1.5, Number.NaN]) await expect(sharedCareRestService.list(careReferral, page)).rejects.toThrow()
  expect(request).toHaveBeenCalledTimes(1)
})
