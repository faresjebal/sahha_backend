import type { ReferralResource } from '../../models/referral'
import type { SharedClinicalResource } from '../../models/sharedClinical'
import type { MedicalFileResource } from '../../models/medicalFile'

export const sharedReferral:ReferralResource = {
  referralType:'SECOND_OPINION',
  id:'referral-1', organisationId:'org-1', patientRegistrationId:'registration-1',
  senderUserId:'sender', senderDisplayName:'Synthetic Sender', recipientUserId:'recipient', recipientDisplayName:'Synthetic Recipient',
  reason:'Synthetic second opinion', priority:'ROUTINE', clinicalSummary:null,
  purpose:'Second opinion', consentType:'RECORDED_WRITTEN', consentEvidenceReference:'synthetic-consent',
  consentRecordedAt:'2026-09-01T09:00:00Z', accessExpiresAt:'2099-09-09T09:00:00Z', status:'ACTIVE',
  sharingGrantId:'grant-1', createdAt:'2026-09-01T09:00:00Z', sentAt:'2026-09-01T09:00:00Z', acceptedAt:'2026-09-01T09:05:00Z', activeAt:'2026-09-01T09:05:00Z',
  rejectedAt:null, completedAt:null, revokedAt:null, expiredAt:null, decisionReason:null, version:3,
  selectedItems:[{ id:'selection-1', resourceType:'DIAGNOSIS', resourceId:'diagnosis-1' }, { id:'selection-2', resourceType:'MEDICAL_DOCUMENT', resourceId:'file-1' }],
}
export const sharedDiagnosis:Extract<SharedClinicalResource, { resourceType:'DIAGNOSIS' }> = {
  resourceType:'DIAGNOSIS', resourceId:'diagnosis-1', patientRegistrationId:'registration-1', validUntil:'2099-09-09T09:00:00Z',
  diagnosis:{ id:'diagnosis-1', label:'Synthetic selected diagnosis', code:'SYNTHETIC', codeSystem:'DEMO', type:'PRIMARY', status:'SUSPECTED', notes:'Selected item only.' },
}
export const sharedFile:MedicalFileResource = {
  fileId:'file-1', consultationId:'consultation-1', originalFilename:'synthetic-selected.pdf', contentType:'application/pdf', size:9,
  uploadStatus:'STORED', scanStatus:'CLEAN', downloadAvailable:true,
  createdAt:'2026-09-01T09:00:00Z', uploadedAt:'2026-09-01T09:00:00Z', availableAt:'2026-09-01T09:00:00Z', rejectedAt:null,
}
