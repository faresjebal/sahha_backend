import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type {
  ClinicalRecordResource,
  ConsultationResource,
  ReplaceConsultationDraftCommand,
} from '../../models/clinical'
import { consultationRestService } from './consultationRestService'
import { httpClient } from './httpClient'

const response = (body: unknown, status = 200) => new Response(
  JSON.stringify(body),
  { status, headers:{ 'Content-Type':'application/json' } },
)

const consultation: ConsultationResource = {
  id:'f8ecfbca-c341-4976-af04-995781c224ab',
  organisationId:'7d7567ed-d259-4bd1-9756-8a93ad4d199c',
  appointmentId:'980f0425-b576-4a91-a45d-b8eab0f4475a',
  patientRegistrationId:'a6319c09-82f7-44cc-8443-1ec91c433991',
  patientId:'1d860db0-faa0-4ad6-9ac6-6d278306f3df',
  doctorUserId:'3f43e66f-870e-462b-850f-093614633469',
  doctorMembershipId:'b78860f7-784b-4279-ab15-65337e4a0986',
  status:'DRAFT',
  reasonForConsultation:null,
  draftNotes:null,
  createdAt:'2026-08-24T20:00:00Z',
  updatedAt:'2026-08-24T20:00:00Z',
  version:0,
}

const draft:ReplaceConsultationDraftCommand = {
  version:0,
  reasonForConsultation:'Persistent cough',
  clinicalAssessment:'Likely uncomplicated acute bronchitis.',
  treatmentPlan:'Supportive care.',
  followUpInstructions:'Return in seven days.',
  additionalNotes:'Synthetic internship record.',
  symptoms:[{
    name:'Cough', onsetDescription:'Five days', severity:'MODERATE', notes:null,
  }],
  history:[],
  vitalSigns:null,
  examinationFindings:[{
    bodySystem:'Respiratory', finding:'Scattered rhonchi', notes:null,
  }],
  diagnoses:[{
    code:'J20.9', codeSystem:'ICD-10', label:'Acute bronchitis',
    type:'PRIMARY', status:'CONFIRMED', notes:null,
  }],
  medications:[],
}

const record:ClinicalRecordResource = {
  ...consultation,
  clinicalAssessment:draft.clinicalAssessment,
  treatmentPlan:draft.treatmentPlan,
  followUpInstructions:draft.followUpInstructions,
  additionalNotes:draft.additionalNotes,
  symptoms:[{ id:'029be79f-305d-4d8a-af69-d9f18aa447e8', ...draft.symptoms[0] }],
  history:[],
  vitalSigns:null,
  examinationFindings:[{
    id:'33deff19-a1d6-4fbb-a728-623fbed74aaf',
    ...draft.examinationFindings[0],
  }],
  diagnoses:[{
    id:'1896db78-271a-4356-9f87-06383baa7fc9', ...draft.diagnoses[0],
  }],
  medications:[],
  corrections:[],
  finalizedAt:null,
  finalizedByUserId:null,
}

describe('Gateway consultation REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('creates, reads, and version-updates an authorised draft', async () => {
    const update = {
      version:0,
      reasonForConsultation:'Persistent cough',
      draftNotes:'Synthetic draft note',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN',
        parameterName:'_csrf',
        token:'clinical-csrf-token',
      }))
      .mockResolvedValueOnce(response(consultation, 201))
      .mockResolvedValueOnce(response(consultation))
      .mockResolvedValueOnce(response({ ...consultation, ...update, version:1 }))
    vi.stubGlobal('fetch', fetchMock)

    await consultationRestService.create({
      appointmentId:consultation.appointmentId,
    })
    await consultationRestService.get(consultation.id)
    await consultationRestService.updateDraft(consultation.id, update)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${env.apiBaseUrl}/auth/csrf`,
      `${env.apiBaseUrl}/consultations`,
      `${env.apiBaseUrl}/consultations/${consultation.id}`,
      `${env.apiBaseUrl}/consultations/${consultation.id}/draft`,
    ])
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method:'POST',
      credentials:'include',
      headers:expect.objectContaining({
        'X-XSRF-TOKEN':'clinical-csrf-token',
      }),
    })
    expect(JSON.parse(String(
      (fetchMock.mock.calls[1][1] as RequestInit).body,
    ))).toEqual({ appointmentId:consultation.appointmentId })
    expect(fetchMock.mock.calls[3][1]).toMatchObject({ method:'PATCH' })
    expect(JSON.parse(String(
      (fetchMock.mock.calls[3][1] as RequestInit).body,
    ))).toEqual(update)
  })

  it('replaces, finalizes, and corrects a structured record through Gateway', async () => {
    const correction = {
      version:2,
      targetType:'CONSULTATION' as const,
      targetId:null,
      fieldName:'clinicalAssessment',
      newValue:'Corrected assessment',
      reason:'Dictation correction',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(record))
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'clinical-csrf-token',
      }))
      .mockResolvedValueOnce(response({ ...record, version:1 }))
      .mockResolvedValueOnce(response({
        ...record, status:'FINALIZED', version:2,
      }))
      .mockResolvedValueOnce(response({
        ...record, clinicalAssessment:correction.newValue, version:3,
      }))
    vi.stubGlobal('fetch', fetchMock)

    await consultationRestService.getRecord(consultation.id)
    await consultationRestService.replaceDraft(consultation.id, draft)
    await consultationRestService.finalize(consultation.id, { version:1 })
    await consultationRestService.correct(consultation.id, correction)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${env.apiBaseUrl}/consultations/${consultation.id}/record`,
      `${env.apiBaseUrl}/auth/csrf`,
      `${env.apiBaseUrl}/consultations/${consultation.id}/draft-content`,
      `${env.apiBaseUrl}/consultations/${consultation.id}/finalize`,
      `${env.apiBaseUrl}/consultations/${consultation.id}/corrections`,
    ])
    expect(fetchMock.mock.calls.slice(2).map(call => call[1]?.method))
      .toEqual(['PUT', 'POST', 'POST'])
    expect(JSON.parse(String(
      (fetchMock.mock.calls[2][1] as RequestInit).body,
    ))).toEqual(draft)
    expect(JSON.parse(String(
      (fetchMock.mock.calls[4][1] as RequestInit).body,
    ))).toEqual(correction)
  })

  it('reads a bounded patient summary and requests appointment recovery', async () => {
    const summary = {
      organisationId:consultation.organisationId,
      patientRegistrationId:consultation.patientRegistrationId,
      patientId:consultation.patientId,
      careRelationship:{
        appointmentId:consultation.appointmentId,
        appointmentStatus:'CONFIRMED',
      },
      encounters:[],
      generatedAt:'2026-08-24T20:10:00Z',
    }
    const recovery = {
      consultationId:consultation.id,
      appointmentId:consultation.appointmentId,
      outcome:'APPLIED',
      duplicate:false,
      appointmentStatus:'COMPLETED',
      appointmentVersion:4,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(summary))
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'clinical-csrf-token',
      }))
      .mockResolvedValueOnce(response(recovery))
    vi.stubGlobal('fetch', fetchMock)

    await consultationRestService.getPatientSummary(
      consultation.patientRegistrationId,
    )
    await consultationRestService.recoverAppointmentCompletion(
      consultation.id,
      { version:2 },
    )

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${env.apiBaseUrl}/clinical/patients/${consultation.patientRegistrationId}/summary`,
      `${env.apiBaseUrl}/auth/csrf`,
      `${env.apiBaseUrl}/consultations/${consultation.id}/appointment-completion-recovery`,
    ])
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method:'POST',
      credentials:'include',
      headers:expect.objectContaining({
        'X-XSRF-TOKEN':'clinical-csrf-token',
      }),
    })
    expect(JSON.parse(String(
      (fetchMock.mock.calls[2][1] as RequestInit).body,
    ))).toEqual({ version:2 })
  })
})
