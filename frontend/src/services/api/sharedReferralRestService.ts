import type { ReferralResource, ShareResourceType } from '../../models/referral'
import type { SharedClinicalResource } from '../../models/sharedClinical'
import type { MedicalFileDownloadGrantResource, MedicalFileResource } from '../../models/medicalFile'
import { httpClient } from './httpClient'

const fields = { CONSULTATION:'consultation', DIAGNOSIS:'diagnosis', MEDICATION:'medication', ALLERGY:'allergy' } as const
const part = encodeURIComponent
const invalid = () => new Error('The selected resource could not be verified. Close the preview and refresh the referral.')
type Selection = ReferralResource['selectedItems'][number]

function requireSelection(referral:ReferralResource, type:ShareResourceType, id:string) {
  if (!referral.selectedItems.some(item => item.resourceType === type && item.resourceId === id)) throw invalid()
}

export function validateSelectedClinical(resource:SharedClinicalResource, referral:ReferralResource, item:Selection):SharedClinicalResource {
  requireSelection(referral, item.resourceType, item.resourceId)
  if (item.resourceType === 'MEDICAL_DOCUMENT') throw invalid()
  if (!resource || resource.resourceId !== item.resourceId || resource.resourceType !== item.resourceType
    || resource.patientRegistrationId !== referral.patientRegistrationId
    || !Number.isFinite(Date.parse(resource.validUntil)) || Date.parse(resource.validUntil) <= Date.now()) throw invalid()
  const expected = fields[item.resourceType]
  const content = resource as unknown as Record<string, unknown>
  for (const field of Object.values(fields)) {
    if (field !== expected && content[field] != null) throw invalid()
  }
  const selected = content[expected] as { id?:string } | undefined
  if (!selected || selected.id !== item.resourceId) throw invalid()
  if (resource.resourceType === 'CONSULTATION' && (resource.consultation.status !== 'FINALIZED'
    || resource.consultation.organisationId !== referral.organisationId
    || resource.consultation.patientRegistrationId !== referral.patientRegistrationId)) throw invalid()
  if (resource.resourceType === 'ALLERGY' && resource.allergy.category !== 'ALLERGY') throw invalid()
  return resource
}

function fileBase(referral:ReferralResource, fileId:string) {
  return `/files/shared/${part(referral.patientRegistrationId)}/${part(fileId)}`
}

export const sharedReferralRestService = {
  async clinical(referral:ReferralResource, item:Selection) {
    requireSelection(referral, item.resourceType, item.resourceId)
    if (!(item.resourceType in fields)) throw invalid()
    const result = await httpClient.request<SharedClinicalResource>(
      `/clinical/shared/${part(referral.patientRegistrationId)}/${part(item.resourceType)}/${part(item.resourceId)}`,
      { cache:'no-store' },
    )
    return validateSelectedClinical(result, referral, item)
  },
  async file(referral:ReferralResource, fileId:string) {
    requireSelection(referral, 'MEDICAL_DOCUMENT', fileId)
    const result = await httpClient.request<MedicalFileResource>(fileBase(referral, fileId), { cache:'no-store' })
    if (!result || result.fileId !== fileId) throw invalid()
    return result
  },
  async download(referral:ReferralResource, file:MedicalFileResource, stillCurrent:() => boolean) {
    requireSelection(referral, 'MEDICAL_DOCUMENT', file.fileId)
    const active = () => stillCurrent() && Date.parse(referral.accessExpiresAt) > Date.now()
    if (!active()) throw invalid()
    const grant = await httpClient.request<MedicalFileDownloadGrantResource>(`${fileBase(referral, file.fileId)}/download-grants`, { method:'POST', cache:'no-store' })
    const expected = `/api/v1${fileBase(referral, file.fileId)}/content`
    if (!active() || !grant || grant.fileId !== file.fileId || grant.downloadPath !== expected
      || !grant.downloadToken || !Number.isFinite(Date.parse(grant.expiresAt))
      || Date.parse(grant.expiresAt) <= Date.now()) throw invalid()
    // The path is constructed from the selected identifiers, never from an arbitrary URL.
    const blob = await httpClient.requestBlob(`${fileBase(referral, file.fileId)}/content`, {
      cache:'no-store', headers:{ 'X-Download-Token':grant.downloadToken },
    })
    if (!active() || Date.parse(grant.expiresAt) <= Date.now()) throw invalid()
    return blob
  },
}
