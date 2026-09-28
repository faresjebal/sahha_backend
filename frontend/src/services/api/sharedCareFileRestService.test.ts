import { afterEach, expect, it, vi } from 'vitest'
import { sharedCareFileRestService } from './sharedCareFileRestService'
import { httpClient } from './httpClient'
import { careReferral } from '../../test/fixtures/sharedCare'
import { careFile, careFileMetadata, careFiles, careFileGrant } from '../../test/fixtures/sharedCareFile'

afterEach(() => vi.restoreAllMocks())
it('lists only the requested encounter through Gateway with no-store, then revalidates metadata, grant and bytes', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValueOnce(careFiles)
    .mockResolvedValueOnce(careFileMetadata).mockResolvedValueOnce(careFileGrant)
  const blob = new Blob(['synthetic'])
  const bytes = vi.spyOn(httpClient, 'requestBlob').mockResolvedValue(blob)
  expect(await sharedCareFileRestService.list(careReferral, 'consultation-1')).toEqual(careFiles)
  expect(request).toHaveBeenNthCalledWith(1, '/files/shared-care/registration-1/consultations/consultation-1?page=0&size=20', { cache:'no-store' })
  expect(await sharedCareFileRestService.download(careReferral, careFile, () => true)).toBe(blob)
  expect(request).toHaveBeenNthCalledWith(2, '/files/shared-care/registration-1/file-1', { cache:'no-store' })
  expect(request).toHaveBeenNthCalledWith(3, '/files/shared-care/registration-1/file-1/download-grants', { method:'POST', cache:'no-store' })
  expect(bytes).toHaveBeenCalledWith('/files/shared-care/registration-1/file-1/content',
    { cache:'no-store', headers:{ 'X-Download-Token':careFileGrant.downloadToken } })
})
it.each([
  { referralType:'SECOND_OPINION' as const }, { status:'SENT' as const }, { status:'REVOKED' as const },
  { sharingGrantId:null }, { accessExpiresAt:'2000-01-01T00:00:00Z' },
])('rejects an ineligible referral before contacting the API', async change => {
  const request = vi.spyOn(httpClient, 'request')
  await expect(sharedCareFileRestService.list({ ...careReferral, ...change }, 'consultation-1')).rejects.toThrow()
  await expect(sharedCareFileRestService.metadata({ ...careReferral, ...change }, 'consultation-1', 'file-1')).rejects.toThrow()
  expect(request).not.toHaveBeenCalled()
})
it.each([
  { organisationId:'other-org' }, { patientRegistrationId:'other-patient' }, { consultationId:'other-record' },
  { validUntil:'2000-01-01T00:00:00Z' }, { page:1 }, { size:100 }, { totalElements:-1 }, { totalPages:99 },
  { content:null }, { content:[careFile, careFile], totalElements:2 },
  { content:[{ ...careFile, consultationId:'other-record' }] }, { content:[{ ...careFile, scanStatus:'PENDING' }] },
  { content:[{ ...careFile, downloadAvailable:false }] }, { content:[{ ...careFile, uploadStatus:'FAILED' }] },
])('rejects mismatched, malformed, expired or unready document lists', async change => {
  vi.spyOn(httpClient, 'request').mockResolvedValue({ ...careFiles, ...change })
  await expect(sharedCareFileRestService.list(careReferral, 'consultation-1')).rejects.toThrow()
})
it.each([
  { fileId:'other-file' }, { consultationId:'other-record' }, { scanStatus:'REJECTED' }, { size:-1 },
])('rejects substituted or unsafe file metadata', async change => {
  vi.spyOn(httpClient, 'request').mockResolvedValue({ ...careFileMetadata, file:{ ...careFile, ...change } })
  await expect(sharedCareFileRestService.metadata(careReferral, 'consultation-1', 'file-1')).rejects.toThrow()
})
it.each([
  { downloadPath:'https://outside.invalid/content' }, { downloadPath:'/api/v1/files/shared/registration-1/file-1/content' },
  { fileId:'other-file' }, { expiresAt:'2000-01-01T00:00:00Z' }, { downloadToken:'' },
])('does not fetch bytes for an invalid grant or substituted download path', async change => {
  vi.spyOn(httpClient, 'request').mockResolvedValueOnce(careFileMetadata).mockResolvedValueOnce({ ...careFileGrant, ...change })
  const bytes = vi.spyOn(httpClient, 'requestBlob')
  await expect(sharedCareFileRestService.download(careReferral, careFile, () => true)).rejects.toThrow()
  expect(bytes).not.toHaveBeenCalled()
})
it.each(['metadata', 'grant', 'bytes'])('discards a download after its context changes during %s', async stage => {
  let active = true
  const request = vi.spyOn(httpClient, 'request').mockImplementation(async path => {
    if (path.endsWith('/download-grants')) { if (stage === 'grant') active = false; return careFileGrant }
    if (stage === 'metadata') active = false
    return careFileMetadata
  })
  const bytes = vi.spyOn(httpClient, 'requestBlob').mockImplementation(async () => { active = false; return new Blob(['synthetic']) })
  await expect(sharedCareFileRestService.download(careReferral, careFile, () => active)).rejects.toThrow()
  expect(request).toHaveBeenCalledTimes(stage === 'metadata' ? 1 : 2)
  expect(bytes).toHaveBeenCalledTimes(stage === 'bytes' ? 1 : 0)
})
it('encodes identifiers and rejects invalid pagination before requesting', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValue({ ...careFiles, consultationId:'consultation/id', content:[] })
  await sharedCareFileRestService.list(careReferral, 'consultation/id')
  expect(request).toHaveBeenCalledWith('/files/shared-care/registration-1/consultations/consultation%2Fid?page=0&size=20', { cache:'no-store' })
  for (const page of [-1, Number.NaN, 0.5]) await expect(sharedCareFileRestService.list(careReferral, 'consultation-1', page)).rejects.toThrow()
  expect(request).toHaveBeenCalledTimes(1)
})
