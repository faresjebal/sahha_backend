import type {
  AppointmentCompletionRecoveryResource,
  ClinicalRecordResource,
  CreateClinicalCorrectionCommand,
  ConsultationResource,
  CreateConsultationCommand,
  FinalizeConsultationCommand,
  PatientClinicalSummaryResource,
  RecoverAppointmentCompletionCommand,
  ReplaceConsultationDraftCommand,
  UpdateConsultationDraftCommand,
} from '../../models/clinical'
import { httpClient } from './httpClient'

export const consultationRestService = {
  create(command: CreateConsultationCommand) {
    return httpClient.request<ConsultationResource>('/consultations', {
      method:'POST',
      body:command,
    })
  },

  get(consultationId: string) {
    return httpClient.request<ConsultationResource>(
      `/consultations/${consultationId}`,
    )
  },

  updateDraft(
    consultationId: string,
    command: UpdateConsultationDraftCommand,
  ) {
    return httpClient.request<ConsultationResource>(
      `/consultations/${consultationId}/draft`,
      { method:'PATCH', body:command },
    )
  },

  getRecord(consultationId:string) {
    return httpClient.request<ClinicalRecordResource>(
      `/consultations/${consultationId}/record`,
    )
  },

  replaceDraft(
    consultationId:string,
    command:ReplaceConsultationDraftCommand,
  ) {
    return httpClient.request<ClinicalRecordResource>(
      `/consultations/${consultationId}/draft-content`,
      { method:'PUT', body:command },
    )
  },

  finalize(
    consultationId:string,
    command:FinalizeConsultationCommand,
  ) {
    return httpClient.request<ClinicalRecordResource>(
      `/consultations/${consultationId}/finalize`,
      { method:'POST', body:command },
    )
  },

  correct(
    consultationId:string,
    command:CreateClinicalCorrectionCommand,
  ) {
    return httpClient.request<ClinicalRecordResource>(
      `/consultations/${consultationId}/corrections`,
      { method:'POST', body:command },
    )
  },

  getPatientSummary(patientRegistrationId:string) {
    return httpClient.request<PatientClinicalSummaryResource>(
      `/clinical/patients/${patientRegistrationId}/summary`,
    )
  },

  recoverAppointmentCompletion(
    consultationId:string,
    command:RecoverAppointmentCompletionCommand,
  ) {
    return httpClient.request<AppointmentCompletionRecoveryResource>(
      `/consultations/${consultationId}/appointment-completion-recovery`,
      { method:'POST', body:command },
    )
  },
}
