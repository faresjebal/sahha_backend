import type { ClinicalRecordResource } from './clinical'

export interface SharedCareHistoryPage {
  organisationId:string
  patientRegistrationId:string
  validUntil:string
  content:Array<{ consultationId:string; appointmentId:string; doctorUserId:string; finalizedAt:string; version:number }>
  page:number
  size:number
  totalElements:number
  totalPages:number
}
export interface SharedCareRecord {
  organisationId:string
  patientRegistrationId:string
  validUntil:string
  consultation:ClinicalRecordResource
}
