import type { ReferralResource } from '../../models/referral'
import type { SharedCareHistoryPage, SharedCareRecord } from '../../models/sharedCare'
import { httpClient } from './httpClient'

const invalid = () => new Error('Shared-care access could not be verified. Close the history and refresh the referral.')
const future = (date:string) => Number.isFinite(Date.parse(date)) && Date.parse(date) > Date.now()
function base(referral:ReferralResource) {
  if (referral.referralType !== 'SHARED_TREATMENT' || referral.status !== 'ACTIVE'
    || !referral.sharingGrantId || !future(referral.accessExpiresAt)) throw invalid()
  return `/clinical/shared-care/${encodeURIComponent(referral.patientRegistrationId)}/consultations`
}
function bound(resource:SharedCareHistoryPage | SharedCareRecord, referral:ReferralResource) {
  if (!resource || resource.organisationId !== referral.organisationId
    || resource.patientRegistrationId !== referral.patientRegistrationId || !future(resource.validUntil)
    || !future(referral.accessExpiresAt)) throw invalid()
}
export const sharedCareRestService = {
  async list(referral:ReferralResource, page = 0) {
    if (!Number.isSafeInteger(page) || page < 0) throw invalid()
    const result = await httpClient.request<SharedCareHistoryPage>(`${base(referral)}?page=${page}&size=20`, { cache:'no-store' })
    bound(result, referral)
    if (result.page !== page || result.size !== 20 || !Number.isSafeInteger(result.totalElements) || result.totalElements < 0
      || result.totalPages !== Math.ceil(result.totalElements / result.size) || !Array.isArray(result.content)
      || result.content.length > result.size || result.content.length > result.totalElements
      || new Set(result.content.map(item => item?.consultationId)).size !== result.content.length
      || result.content.some(item => !item || typeof item.consultationId !== 'string' || !item.consultationId
        || typeof item.doctorUserId !== 'string' || !item.doctorUserId || typeof item.appointmentId !== 'string' || !item.appointmentId
        || !Number.isFinite(Date.parse(item.finalizedAt)) || !Number.isSafeInteger(item.version) || item.version < 0)) throw invalid()
    return result
  },
  async record(referral:ReferralResource, consultationId:string) {
    if (!consultationId) throw invalid()
    const result = await httpClient.request<SharedCareRecord>(`${base(referral)}/${encodeURIComponent(consultationId)}`, { cache:'no-store' })
    bound(result, referral)
    const record = result.consultation
    if (!record || record.id !== consultationId || record.organisationId !== referral.organisationId
      || record.patientRegistrationId !== referral.patientRegistrationId || record.status !== 'FINALIZED'
      || !record.finalizedAt || !Number.isFinite(Date.parse(record.finalizedAt))
      || [record.symptoms, record.history, record.examinationFindings, record.diagnoses, record.medications, record.corrections].some(value => !Array.isArray(value))) throw invalid()
    return result
  },
}
