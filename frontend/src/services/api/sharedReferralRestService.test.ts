import { afterEach, expect, it, vi } from 'vitest'
import { sharedDiagnosis, sharedFile, sharedReferral } from '../../test/fixtures/sharedReferral'
import type { SharedClinicalResource } from '../../models/sharedClinical'
import type { MedicalFileDownloadGrantResource } from '../../models/medicalFile'
import { httpClient } from './httpClient'
import { sharedReferralRestService, validateSelectedClinical } from './sharedReferralRestService'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); httpClient.invalidateCsrfToken() })
const selection = sharedReferral.selectedItems[0]
const grant:MedicalFileDownloadGrantResource = {
  grantId:'short-grant', fileId:'file-1', downloadPath:'/api/v1/files/shared/registration-1/file-1/content',
  downloadToken:'synthetic-one-time-token', expiresAt:'2099-09-09T09:00:00Z',
}

it('loads exactly the selected Clinical resource and File metadata through Gateway-relative paths', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValueOnce(sharedDiagnosis).mockResolvedValueOnce(sharedFile)
  await expect(sharedReferralRestService.clinical(sharedReferral, selection)).resolves.toEqual(sharedDiagnosis)
  await expect(sharedReferralRestService.file(sharedReferral, 'file-1')).resolves.toEqual(sharedFile)
  expect(request.mock.calls.map(call => call[0])).toEqual(['/clinical/shared/registration-1/DIAGNOSIS/diagnosis-1', '/files/shared/registration-1/file-1'])
  expect(request.mock.calls.every(call => call[1]?.cache === 'no-store')).toBe(true)
})

it('refuses unselected items before sending any request', async () => {
  const request = vi.spyOn(httpClient, 'request')
  await expect(sharedReferralRestService.clinical(sharedReferral, { ...selection, resourceId:'unselected' })).rejects.toThrow('could not be verified')
  await expect(sharedReferralRestService.file(sharedReferral, 'unselected')).rejects.toThrow('could not be verified')
  expect(request).not.toHaveBeenCalled()
})

it.each([
  { resourceId:'wrong-resource' }, { patientRegistrationId:'wrong-patient' }, { resourceType:'MEDICATION' },
  { validUntil:'invalid' }, { validUntil:'2000-01-01T00:00:00Z' },
  { consultation:{ id:'unselected-parent', clinicalAssessment:'Must never appear' } },
  { diagnosis:{ ...sharedDiagnosis.diagnosis, id:'wrong-item' } },
])('rejects mismatched, expired or overbroad selected responses: %j', change => {
  expect(() => validateSelectedClinical({ ...sharedDiagnosis, ...change } as SharedClinicalResource, sharedReferral, selection)).toThrow('could not be verified')
})

it('keeps file credentials in headers and consumes only the pinned selected path', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValue(grant)
  const blob = new Blob(['synthetic'], { type:'application/pdf' })
  const bytes = vi.spyOn(httpClient, 'requestBlob').mockResolvedValue(blob)
  await expect(sharedReferralRestService.download(sharedReferral, sharedFile, () => true)).resolves.toBe(blob)
  expect(request).toHaveBeenCalledWith('/files/shared/registration-1/file-1/download-grants', { method:'POST', cache:'no-store' })
  expect(bytes).toHaveBeenCalledWith('/files/shared/registration-1/file-1/content', { cache:'no-store', headers:{ 'X-Download-Token':'synthetic-one-time-token' } })
})

it.each([
  { downloadPath:'https://untrusted.example/secret' }, { downloadPath:'/api/v1/files/other/content' },
  { fileId:'wrong-file' }, { expiresAt:'2000-01-01T00:00:00Z' }, { expiresAt:'invalid' }, { downloadToken:'' },
])('does not follow malformed or unbound download grants: %j', async change => {
  vi.spyOn(httpClient, 'request').mockResolvedValue({ ...grant, ...change })
  const bytes = vi.spyOn(httpClient, 'requestBlob')
  await expect(sharedReferralRestService.download(sharedReferral, sharedFile, () => true)).rejects.toThrow('could not be verified')
  expect(bytes).not.toHaveBeenCalled()
})

it('drops a grant and bytes that finish after the view identity/lifecycle changes', async () => {
  let current = true
  vi.spyOn(httpClient, 'request').mockImplementationOnce(async () => { current = false; return grant })
  const bytes = vi.spyOn(httpClient, 'requestBlob')
  await expect(sharedReferralRestService.download(sharedReferral, sharedFile, () => current)).rejects.toThrow('could not be verified')
  expect(bytes).not.toHaveBeenCalled()
  current = true
  vi.mocked(httpClient.request).mockResolvedValue(grant)
  bytes.mockImplementationOnce(async () => { current = false; return new Blob(['synthetic']) })
  await expect(sharedReferralRestService.download(sharedReferral, sharedFile, () => current)).rejects.toThrow('could not be verified')
})
