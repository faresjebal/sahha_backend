import type { ReferralResource } from '../../models/referral'
import type { MedicalFileDownloadGrantResource, MedicalFileResource } from '../../models/medicalFile'
import type { SharedCareFile, SharedCareFilePage } from '../../models/sharedCareFile'
import { httpClient } from './httpClient'

const invalid = () => new Error('Shared-care documents could not be verified. Refresh the referral before retrying.')
const future = (value:string) => Number.isFinite(Date.parse(value)) && Date.parse(value) > Date.now()
function base(referral:ReferralResource) {
  if (referral.referralType !== 'SHARED_TREATMENT' || referral.status !== 'ACTIVE' || !referral.sharingGrantId
    || !future(referral.accessExpiresAt)) throw invalid()
  return '/files/shared-care/' + encodeURIComponent(referral.patientRegistrationId)
}
function bound(result:SharedCareFile | SharedCareFilePage, referral:ReferralResource) {
  if (!result || result.organisationId !== referral.organisationId || result.patientRegistrationId !== referral.patientRegistrationId
    || !future(result.validUntil) || !future(referral.accessExpiresAt)) throw invalid()
}
function validateFile(file:MedicalFileResource, consultationId:string) {
  if (!file || typeof file.fileId !== 'string' || !file.fileId || file.consultationId !== consultationId
    || typeof file.originalFilename !== 'string' || !file.originalFilename || typeof file.contentType !== 'string' || !file.contentType
    || !Number.isSafeInteger(file.size) || file.size < 1 || file.uploadStatus !== 'STORED'
    || file.scanStatus !== 'CLEAN' || file.downloadAvailable !== true) throw invalid()
}
export const sharedCareFileRestService = {
  async list(referral:ReferralResource, consultationId:string, page = 0) {
    if (!consultationId || !Number.isSafeInteger(page) || page < 0) throw invalid()
    const result = await httpClient.request<SharedCareFilePage>(
      base(referral) + '/consultations/' + encodeURIComponent(consultationId) + '?page=' + page + '&size=20', { cache:'no-store' })
    bound(result, referral)
    if (result.consultationId !== consultationId || result.page !== page || result.size !== 20
      || !Number.isSafeInteger(result.totalElements) || result.totalElements < 0
      || result.totalPages !== Math.ceil(result.totalElements / result.size) || !Array.isArray(result.content)
      || result.content.length > result.size || result.content.length > result.totalElements
      || new Set(result.content.map(file => file?.fileId)).size !== result.content.length) throw invalid()
    result.content.forEach(file => validateFile(file, consultationId))
    return result
  },
  async metadata(referral:ReferralResource, consultationId:string, fileId:string) {
    if (!consultationId || !fileId) throw invalid()
    const result = await httpClient.request<SharedCareFile>(base(referral) + '/' + encodeURIComponent(fileId), { cache:'no-store' })
    bound(result, referral); validateFile(result.file, consultationId)
    if (result.file.fileId !== fileId) throw invalid()
    return result
  },
  async download(referral:ReferralResource, file:MedicalFileResource, stillCurrent:() => boolean) {
    const active = () => stillCurrent() && future(referral.accessExpiresAt)
    if (!active()) throw invalid()
    const metadata = await sharedCareFileRestService.metadata(referral, file.consultationId, file.fileId)
    if (!active()) throw invalid()
    const path = base(referral) + '/' + encodeURIComponent(file.fileId)
    const grant = await httpClient.request<MedicalFileDownloadGrantResource>(path + '/download-grants', { method:'POST', cache:'no-store' })
    if (!active() || !grant || grant.fileId !== file.fileId || grant.downloadPath !== '/api/v1' + path + '/content'
      || typeof grant.downloadToken !== 'string' || !grant.downloadToken || !future(grant.expiresAt)
      || !future(metadata.validUntil)) throw invalid()
    // Never navigate to a server-supplied URL or put the one-time token in a URL/storage.
    const blob = await httpClient.requestBlob(path + '/content', { cache:'no-store', headers:{ 'X-Download-Token':grant.downloadToken } })
    if (!active() || !future(grant.expiresAt) || !future(metadata.validUntil)) throw invalid()
    return blob
  },
}
