export type ConsultationStatus = 'DRAFT' | 'FINALIZED'
export interface ReferralSourcePageResource {
  content:Array<{ consultationId:string; patientRegistrationId:string; appointmentId:string; finalizedAt:string; version:number }>
  page:number
  size:number
  totalElements:number
  totalPages:number
}
export type SymptomSeverity = 'MILD' | 'MODERATE' | 'SEVERE'
export type ClinicalHistoryCategory = 'MEDICAL' | 'SURGICAL' | 'FAMILY' | 'ALLERGY'
export type DiagnosisType = 'PRIMARY' | 'SECONDARY' | 'DIFFERENTIAL'
export type DiagnosisStatus = 'CONFIRMED' | 'SUSPECTED' | 'RULED_OUT'
export type MedicationKind = 'CURRENT' | 'PRESCRIBED'
export type CorrectionTargetType =
  | 'CONSULTATION'
  | 'SYMPTOM'
  | 'HISTORY'
  | 'VITAL_SIGNS'
  | 'EXAMINATION'
  | 'DIAGNOSIS'
  | 'MEDICATION'

export type SpecialtyMeasurement = string | number | boolean | null

export interface ConsultationResource {
  id: string
  organisationId: string
  appointmentId: string
  patientRegistrationId: string
  patientId: string
  doctorUserId: string
  doctorMembershipId: string
  status: ConsultationStatus
  reasonForConsultation: string | null
  draftNotes: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateConsultationCommand {
  appointmentId: string
}

export interface UpdateConsultationDraftCommand {
  version: number
  reasonForConsultation: string | null
  draftNotes: string | null
}

export interface ClinicalSymptomInput {
  name:string
  onsetDescription:string | null
  severity:SymptomSeverity | null
  notes:string | null
}

export interface ClinicalHistoryInput {
  category:ClinicalHistoryCategory
  description:string
  notes:string | null
}

export interface ClinicalVitalSignsInput {
  measuredAt:string
  temperatureCelsius:number | null
  systolicBloodPressure:number | null
  diastolicBloodPressure:number | null
  heartRateBpm:number | null
  respiratoryRateBpm:number | null
  oxygenSaturationPercent:number | null
  weightKg:number | null
  heightCm:number | null
  specialtyMeasurements:Record<string, SpecialtyMeasurement>
}

export interface ClinicalExaminationInput {
  bodySystem:string
  finding:string
  notes:string | null
}

export interface ClinicalDiagnosisInput {
  code:string | null
  codeSystem:string | null
  label:string
  type:DiagnosisType
  status:DiagnosisStatus
  notes:string | null
}

export interface ClinicalMedicationInput {
  kind:MedicationKind
  name:string
  strength:string | null
  form:string | null
  dosage:string | null
  frequency:string | null
  route:string | null
  duration:string | null
  quantity:string | null
  specialInstructions:string | null
}

export interface ReplaceConsultationDraftCommand {
  version:number
  reasonForConsultation:string | null
  clinicalAssessment:string | null
  treatmentPlan:string | null
  followUpInstructions:string | null
  additionalNotes:string | null
  symptoms:ClinicalSymptomInput[]
  history:ClinicalHistoryInput[]
  vitalSigns:ClinicalVitalSignsInput | null
  examinationFindings:ClinicalExaminationInput[]
  diagnoses:ClinicalDiagnosisInput[]
  medications:ClinicalMedicationInput[]
}

export interface FinalizeConsultationCommand {
  version:number
}

export interface CreateClinicalCorrectionCommand {
  version:number
  targetType:CorrectionTargetType
  targetId:string | null
  fieldName:string
  newValue:string | null
  reason:string
}

export interface ClinicalRecordResource {
  id:string
  organisationId:string
  appointmentId:string
  patientRegistrationId:string
  patientId:string
  doctorUserId:string
  doctorMembershipId:string
  status:ConsultationStatus
  reasonForConsultation:string | null
  clinicalAssessment:string | null
  treatmentPlan:string | null
  followUpInstructions:string | null
  additionalNotes:string | null
  symptoms:Array<ClinicalSymptomInput & { id:string }>
  history:Array<ClinicalHistoryInput & { id:string }>
  vitalSigns:(ClinicalVitalSignsInput & { id:string }) | null
  examinationFindings:Array<ClinicalExaminationInput & { id:string }>
  diagnoses:Array<ClinicalDiagnosisInput & { id:string }>
  medications:Array<ClinicalMedicationInput & { id:string }>
  corrections:Array<{
    id:string
    targetType:CorrectionTargetType
    targetId:string | null
    fieldName:string
    oldValue:string | null
    newValue:string | null
    reason:string
    actorUserId:string
    consultationVersion:number
    correctedAt:string
  }>
  finalizedAt:string | null
  finalizedByUserId:string | null
  createdAt:string
  updatedAt:string
  version:number
}

export interface PatientClinicalSummaryResource {
  organisationId:string
  patientRegistrationId:string
  patientId:string
  careRelationship:{
    appointmentId:string
    appointmentStatus:string
  }
  encounters:Array<{
    consultationId:string
    appointmentId:string
    doctorUserId:string
    finalizedAt:string
    reasonForConsultation:string | null
    diagnoses:Array<{
      code:string | null
      codeSystem:string | null
      label:string
      type:DiagnosisType
      status:DiagnosisStatus
    }>
    medications:Array<{
      kind:MedicationKind
      name:string
      strength:string | null
      dosage:string | null
      frequency:string | null
      route:string | null
      duration:string | null
    }>
    allergies:string[]
  }>
  generatedAt:string
}

export interface RecoverAppointmentCompletionCommand {
  version:number
}

export interface AppointmentCompletionRecoveryResource {
  consultationId:string
  appointmentId:string
  outcome:'APPLIED' | 'IDEMPOTENT'
  duplicate:boolean
  appointmentStatus:'COMPLETED'
  appointmentVersion:number | null
}
