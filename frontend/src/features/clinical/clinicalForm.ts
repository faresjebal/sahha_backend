import { z } from 'zod'
import type {
  ClinicalRecordResource,
  ReplaceConsultationDraftCommand,
} from '../../models/clinical'

const optionalText = (maximum:number) => z.string().trim().max(
  maximum,
  `Use ${maximum.toLocaleString()} characters or fewer.`,
)

const symptomSchema = z.object({
  name:z.string().trim().min(1, 'Enter the symptom.').max(200),
  onsetDescription:optionalText(300),
  severity:z.enum(['', 'MILD', 'MODERATE', 'SEVERE']),
  notes:optionalText(1000),
})

const historySchema = z.object({
  category:z.enum(['MEDICAL', 'SURGICAL', 'FAMILY', 'ALLERGY']),
  description:z.string().trim().min(1, 'Enter the history item.').max(1000),
  notes:optionalText(1000),
})

const examinationSchema = z.object({
  bodySystem:z.string().trim().min(1, 'Enter the body system.').max(160),
  finding:z.string().trim().min(1, 'Enter the examination finding.').max(1000),
  notes:optionalText(1000),
})

const diagnosisSchema = z.object({
  code:optionalText(64),
  codeSystem:optionalText(64),
  label:z.string().trim().min(1, 'Enter the diagnosis.').max(300),
  type:z.enum(['PRIMARY', 'SECONDARY', 'DIFFERENTIAL']),
  status:z.enum(['CONFIRMED', 'SUSPECTED', 'RULED_OUT']),
  notes:optionalText(1000),
})

const medicationSchema = z.object({
  kind:z.enum(['CURRENT', 'PRESCRIBED']),
  name:z.string().trim().min(1, 'Enter the medication name.').max(300),
  strength:optionalText(100),
  form:optionalText(100),
  dosage:optionalText(160),
  frequency:optionalText(160),
  route:optionalText(100),
  duration:optionalText(160),
  quantity:optionalText(100),
  specialInstructions:optionalText(1000),
})

const optionalDecimal = z.string().trim().refine(
  value => !value || Number.isFinite(Number(value)),
  'Enter a valid number.',
)

const vitalSignsSchema = z.object({
  measuredAt:z.string(),
  temperatureCelsius:optionalDecimal,
  systolicBloodPressure:optionalDecimal,
  diastolicBloodPressure:optionalDecimal,
  heartRateBpm:optionalDecimal,
  respiratoryRateBpm:optionalDecimal,
  oxygenSaturationPercent:optionalDecimal,
  weightKg:optionalDecimal,
  heightCm:optionalDecimal,
}).superRefine((values, context) => {
  const measurements = Object.entries(values)
    .filter(([field]) => field !== 'measuredAt')
    .some(([, value]) => Boolean(value))
  if (measurements && !values.measuredAt) {
    context.addIssue({
      code:'custom',
      path:['measuredAt'],
      message:'Record when these measurements were taken.',
    })
  }
})

export const clinicalDraftSchema = z.object({
  reasonForConsultation:optionalText(1000),
  clinicalAssessment:optionalText(20000),
  treatmentPlan:optionalText(20000),
  followUpInstructions:optionalText(20000),
  additionalNotes:optionalText(20000),
  symptoms:z.array(symptomSchema).max(50),
  history:z.array(historySchema).max(100),
  vitalSigns:vitalSignsSchema,
  examinationFindings:z.array(examinationSchema).max(100),
  diagnoses:z.array(diagnosisSchema).max(50),
  medications:z.array(medicationSchema).max(100),
})

export const clinicalFinalSchema = clinicalDraftSchema.superRefine(
  (values, context) => {
    const requiredNarratives = [
      ['reasonForConsultation', values.reasonForConsultation, 'Record the reason for consultation.'],
      ['clinicalAssessment', values.clinicalAssessment, 'Record the clinical assessment.'],
      ['followUpInstructions', values.followUpInstructions, 'Record follow-up instructions.'],
    ] as const
    requiredNarratives.forEach(([field, value, message]) => {
      if (!value) context.addIssue({ code:'custom', path:[field], message })
    })
    if (!values.symptoms.length) {
      context.addIssue({ code:'custom', path:['symptoms'], message:'Add at least one symptom.' })
    }
    if (!values.examinationFindings.length) {
      context.addIssue({
        code:'custom',
        path:['examinationFindings'],
        message:'Add at least one examination finding.',
      })
    }
    if (!values.diagnoses.some(value => value.status !== 'RULED_OUT')) {
      context.addIssue({
        code:'custom',
        path:['diagnoses'],
        message:'Add at least one suspected or confirmed diagnosis.',
      })
    }
    const prescriptions = values.medications.filter(
      value => value.kind === 'PRESCRIBED',
    )
    if (!values.treatmentPlan && !prescriptions.length) {
      context.addIssue({
        code:'custom',
        path:['treatmentPlan'],
        message:'Record a treatment plan or prescribed medication.',
      })
    }
    prescriptions.forEach((value, index) => {
      const required = [
        ['dosage', value.dosage],
        ['frequency', value.frequency],
        ['route', value.route],
        ['duration', value.duration],
      ] as const
      required.forEach(([field, fieldValue]) => {
        if (!fieldValue) context.addIssue({
          code:'custom',
          path:['medications', index, field],
          message:`Enter the prescription ${field}.`,
        })
      })
    })
  },
)

export type ClinicalFormValues = z.infer<typeof clinicalDraftSchema>

const currentLocalDateTime = () => {
  const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000)
  return now.toISOString().slice(0, 16)
}

export const emptyClinicalFormValues = ():ClinicalFormValues => ({
  reasonForConsultation:'',
  clinicalAssessment:'',
  treatmentPlan:'',
  followUpInstructions:'',
  additionalNotes:'',
  symptoms:[],
  history:[],
  vitalSigns:{
    measuredAt:currentLocalDateTime(),
    temperatureCelsius:'',
    systolicBloodPressure:'',
    diastolicBloodPressure:'',
    heartRateBpm:'',
    respiratoryRateBpm:'',
    oxygenSaturationPercent:'',
    weightKg:'',
    heightCm:'',
  },
  examinationFindings:[],
  diagnoses:[],
  medications:[],
})

const text = (value:string | null | undefined) => value || ''
const numericText = (value:number | null | undefined) =>
  value === null || value === undefined ? '' : String(value)

const localDateTime = (value:string) => {
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString().slice(0, 16)
}

export const clinicalRecordToForm = (
  record:ClinicalRecordResource,
):ClinicalFormValues => ({
  reasonForConsultation:text(record.reasonForConsultation),
  clinicalAssessment:text(record.clinicalAssessment),
  treatmentPlan:text(record.treatmentPlan),
  followUpInstructions:text(record.followUpInstructions),
  additionalNotes:text(record.additionalNotes),
  symptoms:record.symptoms.map(value => ({
    name:value.name,
    onsetDescription:text(value.onsetDescription),
    severity:value.severity || '',
    notes:text(value.notes),
  })),
  history:record.history.map(value => ({
    category:value.category,
    description:value.description,
    notes:text(value.notes),
  })),
  vitalSigns:record.vitalSigns ? {
    measuredAt:localDateTime(record.vitalSigns.measuredAt),
    temperatureCelsius:numericText(record.vitalSigns.temperatureCelsius),
    systolicBloodPressure:numericText(record.vitalSigns.systolicBloodPressure),
    diastolicBloodPressure:numericText(record.vitalSigns.diastolicBloodPressure),
    heartRateBpm:numericText(record.vitalSigns.heartRateBpm),
    respiratoryRateBpm:numericText(record.vitalSigns.respiratoryRateBpm),
    oxygenSaturationPercent:numericText(
      record.vitalSigns.oxygenSaturationPercent,
    ),
    weightKg:numericText(record.vitalSigns.weightKg),
    heightCm:numericText(record.vitalSigns.heightCm),
  } : emptyClinicalFormValues().vitalSigns,
  examinationFindings:record.examinationFindings.map(value => ({
    bodySystem:value.bodySystem,
    finding:value.finding,
    notes:text(value.notes),
  })),
  diagnoses:record.diagnoses.map(value => ({
    code:text(value.code),
    codeSystem:text(value.codeSystem),
    label:value.label,
    type:value.type,
    status:value.status,
    notes:text(value.notes),
  })),
  medications:record.medications.map(value => ({
    kind:value.kind,
    name:value.name,
    strength:text(value.strength),
    form:text(value.form),
    dosage:text(value.dosage),
    frequency:text(value.frequency),
    route:text(value.route),
    duration:text(value.duration),
    quantity:text(value.quantity),
    specialInstructions:text(value.specialInstructions),
  })),
})

const nullable = (value:string) => value.trim() || null
const optionalNumber = (value:string) => value ? Number(value) : null

export const clinicalFormToCommand = (
  values:ClinicalFormValues,
  version:number,
):ReplaceConsultationDraftCommand => {
  const vitals = values.vitalSigns
  const hasMeasurements = Object.entries(vitals)
    .filter(([field]) => field !== 'measuredAt')
    .some(([, value]) => Boolean(value))
  return {
    version,
    reasonForConsultation:nullable(values.reasonForConsultation),
    clinicalAssessment:nullable(values.clinicalAssessment),
    treatmentPlan:nullable(values.treatmentPlan),
    followUpInstructions:nullable(values.followUpInstructions),
    additionalNotes:nullable(values.additionalNotes),
    symptoms:values.symptoms.map(value => ({
      name:value.name.trim(),
      onsetDescription:nullable(value.onsetDescription),
      severity:value.severity || null,
      notes:nullable(value.notes),
    })),
    history:values.history.map(value => ({
      category:value.category,
      description:value.description.trim(),
      notes:nullable(value.notes),
    })),
    vitalSigns:hasMeasurements ? {
      measuredAt:new Date(vitals.measuredAt).toISOString(),
      temperatureCelsius:optionalNumber(vitals.temperatureCelsius),
      systolicBloodPressure:optionalNumber(vitals.systolicBloodPressure),
      diastolicBloodPressure:optionalNumber(vitals.diastolicBloodPressure),
      heartRateBpm:optionalNumber(vitals.heartRateBpm),
      respiratoryRateBpm:optionalNumber(vitals.respiratoryRateBpm),
      oxygenSaturationPercent:optionalNumber(vitals.oxygenSaturationPercent),
      weightKg:optionalNumber(vitals.weightKg),
      heightCm:optionalNumber(vitals.heightCm),
      specialtyMeasurements:{},
    } : null,
    examinationFindings:values.examinationFindings.map(value => ({
      bodySystem:value.bodySystem.trim(),
      finding:value.finding.trim(),
      notes:nullable(value.notes),
    })),
    diagnoses:values.diagnoses.map(value => ({
      code:nullable(value.code),
      codeSystem:nullable(value.codeSystem),
      label:value.label.trim(),
      type:value.type,
      status:value.status,
      notes:nullable(value.notes),
    })),
    medications:values.medications.map(value => ({
      kind:value.kind,
      name:value.name.trim(),
      strength:nullable(value.strength),
      form:nullable(value.form),
      dosage:nullable(value.dosage),
      frequency:nullable(value.frequency),
      route:nullable(value.route),
      duration:nullable(value.duration),
      quantity:nullable(value.quantity),
      specialInstructions:nullable(value.specialInstructions),
    })),
  }
}
