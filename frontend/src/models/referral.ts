export type ReferralDirection = 'ALL' | 'SENT' | 'RECEIVED'
export type ReferralType = 'SECOND_OPINION' | 'SHARED_TREATMENT'
export type ReferralStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'ACTIVE' | 'REJECTED' | 'REVOKED' | 'COMPLETED' | 'EXPIRED'
export type ShareResourceType = 'CONSULTATION' | 'DIAGNOSIS' | 'MEDICATION' | 'ALLERGY' | 'MEDICAL_DOCUMENT'
export type ReferralAction = 'send' | 'accept' | 'reject' | 'revoke' | 'complete'
export interface CreateReferralCommand {
  referralType:ReferralType
  referralRequestId:string
  recipientUserId:string
  patientRegistrationId:string
  sourceConsultationId?:string
  reason:string
  priority:ReferralResource['priority']
  clinicalSummary:string | null
  purpose:string
  consentType:ReferralResource['consentType']
  consentEvidenceReference:string
  consentRecordedAt:string
  accessExpiresAt:string
  selectedItems:Array<{ resourceType:ShareResourceType; resourceId:string }>
  sendImmediately:boolean
}
export interface ReferralResource {
  referralType:ReferralType
  id:string
  organisationId:string
  patientRegistrationId:string
  senderUserId:string
  senderDisplayName:string
  recipientUserId:string
  recipientDisplayName:string
  reason:string
  priority:'ROUTINE' | 'URGENT'
  clinicalSummary:string | null
  purpose:string
  consentType:'EXPLICIT_DIGITAL' | 'RECORDED_WRITTEN' | 'RECORDED_VERBAL' | 'LEGAL_BASIS' | 'EMERGENCY'
  consentEvidenceReference:string
  consentRecordedAt:string
  accessExpiresAt:string
  status:ReferralStatus
  sharingGrantId:string | null
  createdAt:string
  sentAt:string | null
  acceptedAt:string | null
  activeAt:string | null
  rejectedAt:string | null
  completedAt:string | null
  revokedAt:string | null
  expiredAt:string | null
  decisionReason:string | null
  version:number
  selectedItems:Array<{ id:string; resourceType:ShareResourceType; resourceId:string }>
}
export interface ReferralPageResource {
  content:ReferralResource[]
  page:number
  size:number
  totalElements:number
  totalPages:number
}
export type ReferralCommand =
  | { action:'send' | 'accept' | 'complete'; expectedVersion:number }
  | { action:'reject' | 'revoke'; expectedVersion:number; reason:string }
