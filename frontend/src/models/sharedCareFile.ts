import type { MedicalFileResource } from './medicalFile'

export interface SharedCareFilePage {
  organisationId:string
  patientRegistrationId:string
  consultationId:string
  validUntil:string
  content:MedicalFileResource[]
  page:number
  size:number
  totalElements:number
  totalPages:number
}
export interface SharedCareFile {
  organisationId:string
  patientRegistrationId:string
  validUntil:string
  file:MedicalFileResource
}
