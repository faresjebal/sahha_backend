import { describe, expect, it } from 'vitest'
import type { ClinicalRecordResource } from '../../models/clinical'
import {
  clinicalDraftSchema,
  clinicalFinalSchema,
  clinicalFormToCommand,
  clinicalRecordToForm,
  emptyClinicalFormValues,
} from './clinicalForm'

const record:ClinicalRecordResource = {
  id:'consultation-1',
  organisationId:'organisation-1',
  appointmentId:'appointment-1',
  patientRegistrationId:'registration-1',
  patientId:'patient-1',
  doctorUserId:'doctor-1',
  doctorMembershipId:'membership-1',
  status:'DRAFT',
  reasonForConsultation:'Persistent cough',
  clinicalAssessment:'Likely acute bronchitis.',
  treatmentPlan:'Supportive care.',
  followUpInstructions:'Return in seven days.',
  additionalNotes:null,
  symptoms:[{
    id:'symptom-1', name:'Cough', onsetDescription:'Five days',
    severity:'MODERATE', notes:null,
  }],
  history:[],
  vitalSigns:null,
  examinationFindings:[{
    id:'exam-1', bodySystem:'Respiratory', finding:'Scattered rhonchi',
    notes:null,
  }],
  diagnoses:[{
    id:'diagnosis-1', code:'J20.9', codeSystem:'ICD-10',
    label:'Acute bronchitis', type:'PRIMARY', status:'CONFIRMED', notes:null,
  }],
  medications:[],
  corrections:[],
  finalizedAt:null,
  finalizedByUserId:null,
  createdAt:'2026-08-30T10:00:00Z',
  updatedAt:'2026-08-30T10:00:00Z',
  version:4,
}

describe('Clinical workspace form contract', () => {
  it('allows an incomplete draft while enforcing finalisation completeness', () => {
    const empty = emptyClinicalFormValues()

    expect(clinicalDraftSchema.safeParse(empty).success).toBe(true)
    const final = clinicalFinalSchema.safeParse(empty)
    expect(final.success).toBe(false)
    if (!final.success) {
      expect(final.error.issues.map(issue => issue.path[0])).toEqual(
        expect.arrayContaining([
          'reasonForConsultation',
          'clinicalAssessment',
          'followUpInstructions',
          'symptoms',
          'examinationFindings',
          'diagnoses',
        ]),
      )
    }
  })

  it('round-trips the typed record and sends its explicit optimistic version', () => {
    const values = clinicalRecordToForm(record)
    const command = clinicalFormToCommand(values, record.version)

    expect(command).toMatchObject({
      version:4,
      reasonForConsultation:'Persistent cough',
      clinicalAssessment:'Likely acute bronchitis.',
      vitalSigns:null,
      symptoms:[{ name:'Cough', severity:'MODERATE' }],
      diagnoses:[{ label:'Acute bronchitis', codeSystem:'ICD-10' }],
    })
  })
})
