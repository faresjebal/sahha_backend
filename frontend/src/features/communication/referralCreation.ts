import { z } from 'zod'
import type { ClinicalRecordResource } from '../../models/clinical'
import type { MedicalFileResource } from '../../models/medicalFile'
import type { CreateReferralCommand, ShareResourceType } from '../../models/referral'

export const consentTypes = ['EXPLICIT_DIGITAL', 'RECORDED_WRITTEN', 'RECORDED_VERBAL', 'LEGAL_BASIS', 'EMERGENCY'] as const
const localDate = z.string().min(1, 'Enter a date and time.').refine(value => Number.isFinite(Date.parse(value)), 'Enter a valid date and time.')
export const referralCreationSchema = z.object({
  referralType:z.enum(['SECOND_OPINION', 'SHARED_TREATMENT']),
  recipientUserId:z.string().min(1, 'Choose an eligible colleague.'),
  reason:z.string().trim().min(4).max(1000),
  priority:z.enum(['ROUTINE', 'URGENT']),
  clinicalSummary:z.string().trim().max(4000),
  purpose:z.string().trim().min(4).max(500),
  consentType:z.enum(consentTypes, { error:'Choose the recorded consent or legal basis.' }),
  consentEvidenceReference:z.string().trim().min(3).max(255),
  consentRecordedAt:localDate.refine(value => Date.parse(value) <= Date.now(), 'Consent must already be recorded.'),
  accessExpiresAt:localDate.refine(value => Date.parse(value) > Date.now(), 'Expiry must be in the future.')
    .refine(value => Date.parse(value) <= Date.now() + 90 * 86400_000, 'Expiry cannot be more than 90 days away.'),
  selections:z.array(z.string()).max(50, 'Select no more than 50 resources.'),
  sharedCareConfirmed:z.boolean(),
  reviewed:z.boolean().refine(Boolean, 'Confirm the selections, purpose and recorded evidence.'),
}).superRefine((value, context) => {
  if (value.referralType === 'SECOND_OPINION' && !value.selections.length) {
    context.addIssue({ code:'custom', path:['selections'], message:'Select at least one resource.' })
  }
  if (value.referralType === 'SHARED_TREATMENT') {
    if (value.selections.length) context.addIssue({ code:'custom', path:['selections'], message:'Shared treatment uses the stated care scope, not a selected-resource list.' })
    if (!value.sharedCareConfirmed) context.addIssue({ code:'custom', path:['sharedCareConfirmed'], message:'Acknowledge the shared-treatment responsibility and access scope.' })
  }
})
export type ReferralCreationValues = z.infer<typeof referralCreationSchema>
export interface ReferralChoice { key:string; resourceType:ShareResourceType; resourceId:string; label:string }
export function referralChoices(record:ClinicalRecordResource, files:MedicalFileResource[]):ReferralChoice[] {
  const choice = (resourceType:ShareResourceType, resourceId:string, label:string):ReferralChoice => ({ key:`${resourceType}:${resourceId}`, resourceType, resourceId, label })
  return [
    choice('CONSULTATION', record.id, 'Whole consultation — all clinical sections and corrections; files excluded'),
    ...record.diagnoses.map(item => choice('DIAGNOSIS', item.id, `Diagnosis: ${item.label}`)),
    ...record.medications.map(item => choice('MEDICATION', item.id, `Medication: ${item.name}`)),
    ...record.history.filter(item => item.category === 'ALLERGY').map(item => choice('ALLERGY', item.id, `Allergy: ${item.description}`)),
    ...files.filter(item => item.consultationId === record.id && item.uploadStatus === 'STORED' && item.scanStatus === 'CLEAN' && item.downloadAvailable)
      .map(item => choice('MEDICAL_DOCUMENT', item.fileId, `File: ${item.originalFilename}`)),
  ]
}
export function selectedReferralItems(keys:string[], choices:ReferralChoice[]):CreateReferralCommand['selectedItems'] {
  const selected = keys.map(key => choices.find(choice => choice.key === key))
  if (!selected.length || selected.length > 50 || new Set(keys).size !== keys.length || selected.some(item => !item)) {
    throw new Error('The selected resources changed. Return to the form and review them.')
  }
  return selected.map(item => ({ resourceType:item!.resourceType, resourceId:item!.resourceId }))
}
