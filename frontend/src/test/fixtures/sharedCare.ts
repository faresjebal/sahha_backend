import type { ReferralResource } from '../../models/referral'
import type { SharedCareHistoryPage, SharedCareRecord } from '../../models/sharedCare'
import { sharedReferral, sharedDiagnosis } from './sharedReferral'

export const careReferral:ReferralResource = { ...sharedReferral, referralType:'SHARED_TREATMENT', selectedItems:[] }
export const careHistory:SharedCareHistoryPage = {
  organisationId:careReferral.organisationId, patientRegistrationId:careReferral.patientRegistrationId,
  validUntil:careReferral.accessExpiresAt, page:0, size:20, totalElements:1, totalPages:1,
  content:[{ consultationId:'consultation-1', appointmentId:'appointment-1', doctorUserId:'original-author', finalizedAt:'2026-09-01T09:00:00Z', version:2 }],
}
export const careRecord:SharedCareRecord = {
  organisationId:careReferral.organisationId, patientRegistrationId:careReferral.patientRegistrationId, validUntil:careReferral.accessExpiresAt,
  consultation:{
    id:'consultation-1', organisationId:'org-1', appointmentId:'appointment-1', patientRegistrationId:'registration-1', patientId:'patient-1', doctorUserId:'original-author', doctorMembershipId:'original-membership',
    status:'FINALIZED', reasonForConsultation:'Synthetic shared-care encounter', clinicalAssessment:'Corrected assessment', treatmentPlan:'Synthetic plan', followUpInstructions:null, additionalNotes:null,
    symptoms:[], history:[], vitalSigns:null, examinationFindings:[], diagnoses:[sharedDiagnosis.diagnosis], medications:[],
    corrections:[{ id:'correction-1', targetType:'CONSULTATION', targetId:null, fieldName:'clinicalAssessment', oldValue:'Original assessment', newValue:'Corrected assessment', reason:'Synthetic correction reason', actorUserId:'correction-author', consultationVersion:2, correctedAt:'2026-09-01T09:10:00Z' }],
    finalizedAt:'2026-09-01T09:00:00Z', finalizedByUserId:'original-author', createdAt:'2026-09-01T08:00:00Z', updatedAt:'2026-09-01T09:10:00Z', version:2,
  },
}
