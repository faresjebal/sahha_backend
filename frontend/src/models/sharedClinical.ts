import type { ClinicalRecordResource } from './clinical'

interface SharedResourceContext {
  resourceId:string
  patientRegistrationId:string
  validUntil:string
}
export type SharedClinicalResource = SharedResourceContext & (
  | { resourceType:'CONSULTATION'; consultation:ClinicalRecordResource }
  | { resourceType:'DIAGNOSIS'; diagnosis:ClinicalRecordResource['diagnoses'][number] }
  | { resourceType:'MEDICATION'; medication:ClinicalRecordResource['medications'][number] }
  | { resourceType:'ALLERGY'; allergy:ClinicalRecordResource['history'][number] }
)
