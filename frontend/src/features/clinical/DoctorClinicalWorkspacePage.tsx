import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  CalendarDays,
  Check,
  FileHeart,
  History,
  LoaderCircle,
  LockKeyhole,
  Plus,
  RefreshCw,
  Save,
  ShieldCheck,
  Stethoscope,
  Trash2,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import type {
  ClinicalRecordResource,
  PatientClinicalSummaryResource,
} from '../../models/clinical'
import type { AppointmentResource } from '../../models/scheduling'
import { apiErrorMessage } from '../../services/api/ApiError'
import { appointmentRestService } from '../../services/api/appointmentRestService'
import { consultationRestService } from '../../services/api/consultationRestService'
import {
  clinicalDraftSchema,
  clinicalFinalSchema,
  clinicalFormToCommand,
  clinicalRecordToForm,
  emptyClinicalFormValues,
  type ClinicalFormValues,
} from './clinicalForm'
import { MedicalFilePanel } from './MedicalFilePanel'
import { doctorAppointments } from '../scheduling/doctorAppointments'

type SavePhase = 'saved' | 'pending' | 'saving' | 'invalid' | 'error'

const consultationFields = [
  ['reasonForConsultation', 'Reason for consultation'],
  ['clinicalAssessment', 'Clinical assessment'],
  ['treatmentPlan', 'Treatment plan'],
  ['followUpInstructions', 'Follow-up instructions'],
  ['additionalNotes', 'Additional notes'],
] as const

type ConsultationCorrectionField = typeof consultationFields[number][0]

const correctionSchema = z.object({
  fieldName:z.enum([
    'reasonForConsultation',
    'clinicalAssessment',
    'treatmentPlan',
    'followUpInstructions',
    'additionalNotes',
  ]),
  newValue:z.string().trim().max(20000),
  reason:z.string().trim().min(4, 'Explain why this correction is required.').max(1000),
})

type CorrectionValues = z.infer<typeof correctionSchema>

const windowRange = () => {
  const from = new Date()
  from.setDate(from.getDate() - 31)
  const to = new Date()
  to.setDate(to.getDate() + 31)
  return { from:from.toISOString(), to:to.toISOString() }
}

const statusLabel = (status:string) =>
  status.replaceAll('_', ' ').toLowerCase().replace(/^./, value => value.toUpperCase())

const errorText = (error:{ message?:string } | undefined) =>
  error?.message ? <small className="login-field-error">{error.message}</small> : null

export function DoctorClinicalWorkspacePage({
  consultationId,
}:{ consultationId?:string }) {
  return consultationId
    ? <ActiveConsultationWorkspace consultationId={consultationId}/>
    : <ClinicalWorkQueue/>
}

function ClinicalWorkQueue() {
  const auth = useAuth()
  const navigate = useNavigate()
  const organisationId = auth.session?.user.organizationId || 'none'
  const rangeRef = useRef(windowRange())
  const appointmentsQuery = useQuery({
    queryKey:['clinical-appointment-queue', organisationId],
    select:appointments => doctorAppointments(appointments, auth.session?.user.id),
    queryFn:() => appointmentRestService.list(
      rangeRef.current.from,
      rangeRef.current.to,
    ),
    enabled:organisationId !== 'none',
  })
  const openMutation = useMutation({
    mutationFn:(appointmentId:string) =>
      consultationRestService.create({ appointmentId }),
    onSuccess:consultation => navigate(`/doctor/clinical/${consultation.id}`),
  })
  const active = appointmentsQuery.data?.filter(value =>
    value.status === 'CHECKED_IN' || value.status === 'IN_PROGRESS') || []

  return <div className="page workflow-page clinical-workspace-page">
    <header className="clinical-workspace-heading">
      <div><p className="eyebrow">Clinical Service · Active organisation</p><h1>Consultations with a traceable record.</h1><p>Start care from a checked-in appointment, then record, review, and finalise one versioned clinical record.</p></div>
      <Stethoscope/>
    </header>
    <section className="clinical-queue" aria-labelledby="clinical-queue-title">
      <header><div><p className="eyebrow">Care queue</p><h2 id="clinical-queue-title">Ready for clinical work</h2></div><button type="button" className="secondary" onClick={()=>navigate('/doctor/appointments')}><CalendarDays/>Appointment schedule</button></header>
      {appointmentsQuery.isPending&&<div className="clinical-inline-state"><LoaderCircle className="spin"/>Loading the active care queue…</div>}
      {appointmentsQuery.isError&&<div className="clinical-inline-state clinical-inline-state--error"><span>{apiErrorMessage(appointmentsQuery.error, 'The clinical queue could not be loaded.')}</span><button className="secondary" onClick={()=>void appointmentsQuery.refetch()}><RefreshCw/>Retry</button></div>}
      {active.map(appointment => <article key={appointment.id}>
        <CalendarDays/>
        <span><strong>Registration {appointment.patientRegistrationId.slice(0, 8)}</strong><small>{new Intl.DateTimeFormat(undefined,{ dateStyle:'medium', timeStyle:'short' }).format(new Date(appointment.startsAt))} · {appointment.locationLabel}</small></span>
        <i className={`appointment-status appointment-status--${appointment.status.toLowerCase()}`}>{statusLabel(appointment.status)}</i>
        {appointment.status === 'IN_PROGRESS'
          ? <button type="button" className="primary" disabled={openMutation.isPending} onClick={()=>openMutation.mutate(appointment.id)}>{openMutation.isPending?<LoaderCircle className="spin"/>:<FileHeart/>}Open consultation</button>
          : <button type="button" className="secondary" onClick={()=>navigate('/doctor/appointments')}>Start from schedule</button>}
      </article>)}
      {!appointmentsQuery.isPending&&!appointmentsQuery.isError&&!active.length&&<div className="clinical-inline-state"><FileHeart/><span>No checked-in or in-progress appointments need clinical work.</span></div>}
      {openMutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(openMutation.error, 'The consultation could not be opened.')}</p>}
    </section>
  </div>
}

function ActiveConsultationWorkspace({ consultationId }:{ consultationId:string }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'none'
  const recordKey = ['clinical-record', organisationId, consultationId] as const
  const [savePhase, setSavePhase] = useState<SavePhase>('saved')
  const [notice, setNotice] = useState('')
  const [finalizeError, setFinalizeError] = useState('')
  const versionRef = useRef(0)
  const loadedRecordRef = useRef('')
  const lastAttemptedRef = useRef('')
  const autosaveTimerRef = useRef<number | null>(null)

  const recordQuery = useQuery({
    queryKey:recordKey,
    queryFn:() => consultationRestService.getRecord(consultationId),
    enabled:organisationId !== 'none',
  })

  const form = useForm<ClinicalFormValues>({
    resolver:zodResolver(clinicalDraftSchema),
    defaultValues:emptyClinicalFormValues(),
    mode:'onBlur',
  })
  const {
    control,
    register,
    reset,
    getValues,
    handleSubmit,
    formState:{ errors, isDirty },
  } = form
  const symptoms = useFieldArray({ control, name:'symptoms' })
  const history = useFieldArray({ control, name:'history' })
  const examinations = useFieldArray({ control, name:'examinationFindings' })
  const diagnoses = useFieldArray({ control, name:'diagnoses' })
  const medications = useFieldArray({ control, name:'medications' })
  const watchedValues = useWatch({ control })
  const serializedValues = JSON.stringify(watchedValues)

  useEffect(() => {
    const record = recordQuery.data
    if (!record || loadedRecordRef.current === record.id) return
    loadedRecordRef.current = record.id
    versionRef.current = record.version
    reset(clinicalRecordToForm(record))
    setSavePhase(record.status === 'FINALIZED' ? 'saved' : 'saved')
  }, [recordQuery.data, reset])

  type SaveVariables = {
    values:ClinicalFormValues
    rawSnapshot:string
    version:number
  }

  const saveMutation = useMutation({
    mutationFn:(variables:SaveVariables) => consultationRestService.replaceDraft(
      consultationId,
      clinicalFormToCommand(variables.values, variables.version),
    ),
    onMutate:() => {
      setNotice('')
      setSavePhase('saving')
    },
    onSuccess:(record, variables) => {
      versionRef.current = record.version
      queryClient.setQueryData<ClinicalRecordResource>(recordKey, record)
      if (JSON.stringify(getValues()) === variables.rawSnapshot) {
        reset(variables.values)
        setSavePhase('saved')
      } else {
        setSavePhase('pending')
      }
    },
    onError:() => setSavePhase('error'),
  })

  const runSave = (
    values:ClinicalFormValues,
    rawSnapshot:string,
  ) => {
    lastAttemptedRef.current = rawSnapshot
    saveMutation.mutate({
      values,
      rawSnapshot,
      version:versionRef.current,
    })
  }

  useEffect(() => {
    const record = recordQuery.data
    if (!record || record.status !== 'DRAFT' || !isDirty
        || saveMutation.isPending
        || serializedValues === lastAttemptedRef.current) return
    setSavePhase('pending')
    if (autosaveTimerRef.current !== null) {
      window.clearTimeout(autosaveTimerRef.current)
    }
    const rawSnapshot = serializedValues
    autosaveTimerRef.current = window.setTimeout(() => {
      void handleSubmit(
        values => runSave(values, rawSnapshot),
        () => {
          lastAttemptedRef.current = rawSnapshot
          setSavePhase('invalid')
        },
      )()
    }, 1500)
    return () => {
      if (autosaveTimerRef.current !== null) {
        window.clearTimeout(autosaveTimerRef.current)
        autosaveTimerRef.current = null
      }
    }
  }, [
    handleSubmit,
    isDirty,
    recordQuery.data,
    saveMutation.isPending,
    serializedValues,
  ])

  const manualSave = handleSubmit(values => {
    if (autosaveTimerRef.current !== null) {
      window.clearTimeout(autosaveTimerRef.current)
      autosaveTimerRef.current = null
    }
    runSave(values, JSON.stringify(getValues()))
  }, () => setSavePhase('invalid'))

  const finalizeMutation = useMutation({
    mutationFn:async ({ values, saveFirst }:{
      values:ClinicalFormValues
      saveFirst:boolean
    }) => {
      let version = versionRef.current
      if (saveFirst) {
        const saved = await consultationRestService.replaceDraft(
          consultationId,
          clinicalFormToCommand(values, version),
        )
        version = saved.version
        versionRef.current = version
      }
      return consultationRestService.finalize(consultationId, { version })
    },
    onSuccess:record => {
      versionRef.current = record.version
      queryClient.setQueryData<ClinicalRecordResource>(recordKey, record)
      reset(clinicalRecordToForm(record))
      setSavePhase('saved')
      setFinalizeError('')
      setNotice('The consultation is finalised and immutable. Scheduling will complete the appointment from the clinical event; recovery remains available if delivery is interrupted.')
      void queryClient.invalidateQueries({ queryKey:['appointments', organisationId] })
      void queryClient.invalidateQueries({ queryKey:['clinical-appointment-queue', organisationId] })
    },
  })

  const finalize = () => {
    if (autosaveTimerRef.current !== null) {
      window.clearTimeout(autosaveTimerRef.current)
      autosaveTimerRef.current = null
    }
    const result = clinicalFinalSchema.safeParse(getValues())
    if (!result.success) {
      setFinalizeError(result.error.issues[0]?.message || 'Complete the required clinical fields before finalising.')
      return
    }
    setFinalizeError('')
    finalizeMutation.mutate({ values:result.data, saveFirst:isDirty })
  }

  if (recordQuery.isPending) return <ClinicalPageState label="Loading the clinical record"/>
  if (recordQuery.isError) return <ClinicalPageError error={recordQuery.error} retry={()=>void recordQuery.refetch()}/>

  const record = recordQuery.data
  const immutable = record.status === 'FINALIZED'
  const commandError = saveMutation.error || finalizeMutation.error

  return <div className="page workflow-page clinical-workspace-page">
    <button type="button" className="back-button" onClick={()=>navigate('/doctor/appointments')}><CalendarDays/>Back to appointments</button>
    <header className="clinical-workspace-heading clinical-workspace-heading--record">
      <div><p className="eyebrow">Registration {record.patientRegistrationId.slice(0, 8)} · Appointment {record.appointmentId.slice(0, 8)}</p><h1>{immutable ? 'Final clinical record.' : 'Consultation in progress.'}</h1><p>{immutable ? 'The signed source is locked. Any correction is appended with its reason and author.' : 'Changes autosave after a short pause and every write carries the latest explicit record version.'}</p></div>
      <div className="clinical-record-state"><i className={`appointment-status appointment-status--${immutable?'completed':'in_progress'}`}>{immutable?'Finalised':'Draft'}</i><strong>Version {record.version}</strong><small>{savePhase === 'saving' ? 'Saving…' : savePhase === 'pending' ? 'Changes pending' : savePhase === 'invalid' ? 'Fix highlighted fields' : savePhase === 'error' ? 'Autosave failed' : immutable ? 'Signed source locked' : 'All changes saved'}</small></div>
    </header>
    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}
    {commandError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(commandError, 'The clinical record could not be updated. Reload it before retrying if another request changed the version.')}</p>}

    <div className="clinical-workspace-layout">
      <form className="clinical-record-form" onSubmit={event=>event.preventDefault()} noValidate>
        <fieldset disabled={immutable}>
          <ClinicalNarrativeFields register={register} errors={errors}/>

          <ClinicalCollectionSection title="Symptoms" copy="Record the presenting symptoms and their severity." error={errors.symptoms?.root?.message || (errors.symptoms as { message?:string } | undefined)?.message} addLabel="Add symptom" onAdd={()=>symptoms.append({ name:'', onsetDescription:'', severity:'', notes:'' })}>
            {symptoms.fields.map((field, index) => <article className="clinical-entry-card" key={field.id}>
              <header><strong>Symptom {index + 1}</strong><button type="button" className="icon-button" aria-label={`Remove symptom ${index + 1}`} onClick={()=>symptoms.remove(index)}><Trash2/></button></header>
              <div className="clinical-field-grid">
                <label><span>Symptom</span><input {...register(`symptoms.${index}.name`)} />{errorText(errors.symptoms?.[index]?.name)}</label>
                <label><span>Onset</span><input {...register(`symptoms.${index}.onsetDescription`)} placeholder="e.g. Five days"/>{errorText(errors.symptoms?.[index]?.onsetDescription)}</label>
                <label><span>Severity</span><select {...register(`symptoms.${index}.severity`)}><option value="">Not classified</option><option value="MILD">Mild</option><option value="MODERATE">Moderate</option><option value="SEVERE">Severe</option></select></label>
                <label className="wide"><span>Symptom notes</span><textarea rows={3} {...register(`symptoms.${index}.notes`)}/>{errorText(errors.symptoms?.[index]?.notes)}</label>
              </div>
            </article>)}
          </ClinicalCollectionSection>

          <ClinicalCollectionSection title="History and allergies" copy="Keep prior history structured; allergy entries feed the bounded summary." addLabel="Add history item" onAdd={()=>history.append({ category:'MEDICAL', description:'', notes:'' })}>
            {history.fields.map((field, index) => <article className="clinical-entry-card" key={field.id}>
              <header><strong>History item {index + 1}</strong><button type="button" className="icon-button" aria-label={`Remove history item ${index + 1}`} onClick={()=>history.remove(index)}><Trash2/></button></header>
              <div className="clinical-field-grid">
                <label><span>Category</span><select {...register(`history.${index}.category`)}><option value="MEDICAL">Medical</option><option value="SURGICAL">Surgical</option><option value="FAMILY">Family</option><option value="ALLERGY">Allergy</option></select></label>
                <label><span>Description</span><input {...register(`history.${index}.description`)}/>{errorText(errors.history?.[index]?.description)}</label>
                <label className="wide"><span>Notes</span><textarea rows={3} {...register(`history.${index}.notes`)}/>{errorText(errors.history?.[index]?.notes)}</label>
              </div>
            </article>)}
          </ClinicalCollectionSection>

          <section className="clinical-form-section">
            <header><div><p className="eyebrow">Measurements</p><h2>Vital signs</h2><p>Leave measurements empty when they were not taken.</p></div></header>
            <div className="clinical-vitals-grid">
              <label><span>Measured at</span><input type="datetime-local" {...register('vitalSigns.measuredAt')}/>{errorText(errors.vitalSigns?.measuredAt)}</label>
              <label><span>Temperature °C</span><input inputMode="decimal" {...register('vitalSigns.temperatureCelsius')}/>{errorText(errors.vitalSigns?.temperatureCelsius)}</label>
              <label><span>Systolic</span><input inputMode="numeric" {...register('vitalSigns.systolicBloodPressure')}/>{errorText(errors.vitalSigns?.systolicBloodPressure)}</label>
              <label><span>Diastolic</span><input inputMode="numeric" {...register('vitalSigns.diastolicBloodPressure')}/>{errorText(errors.vitalSigns?.diastolicBloodPressure)}</label>
              <label><span>Heart rate</span><input inputMode="numeric" {...register('vitalSigns.heartRateBpm')}/>{errorText(errors.vitalSigns?.heartRateBpm)}</label>
              <label><span>Respiratory rate</span><input inputMode="numeric" {...register('vitalSigns.respiratoryRateBpm')}/>{errorText(errors.vitalSigns?.respiratoryRateBpm)}</label>
              <label><span>Oxygen saturation %</span><input inputMode="decimal" {...register('vitalSigns.oxygenSaturationPercent')}/>{errorText(errors.vitalSigns?.oxygenSaturationPercent)}</label>
              <label><span>Weight kg</span><input inputMode="decimal" {...register('vitalSigns.weightKg')}/>{errorText(errors.vitalSigns?.weightKg)}</label>
              <label><span>Height cm</span><input inputMode="decimal" {...register('vitalSigns.heightCm')}/>{errorText(errors.vitalSigns?.heightCm)}</label>
            </div>
          </section>

          <ClinicalCollectionSection title="Physical examination" copy="Record the body system and the observed finding." error={errors.examinationFindings?.root?.message || (errors.examinationFindings as { message?:string } | undefined)?.message} addLabel="Add finding" onAdd={()=>examinations.append({ bodySystem:'', finding:'', notes:'' })}>
            {examinations.fields.map((field, index) => <article className="clinical-entry-card" key={field.id}>
              <header><strong>Finding {index + 1}</strong><button type="button" className="icon-button" aria-label={`Remove examination finding ${index + 1}`} onClick={()=>examinations.remove(index)}><Trash2/></button></header>
              <div className="clinical-field-grid">
                <label><span>Body system</span><input {...register(`examinationFindings.${index}.bodySystem`)}/>{errorText(errors.examinationFindings?.[index]?.bodySystem)}</label>
                <label><span>Finding</span><input {...register(`examinationFindings.${index}.finding`)}/>{errorText(errors.examinationFindings?.[index]?.finding)}</label>
                <label className="wide"><span>Notes</span><textarea rows={3} {...register(`examinationFindings.${index}.notes`)}/>{errorText(errors.examinationFindings?.[index]?.notes)}</label>
              </div>
            </article>)}
          </ClinicalCollectionSection>

          <ClinicalCollectionSection title="Diagnoses" copy="Use a bounded code when available and keep the clinical status explicit." error={errors.diagnoses?.root?.message || (errors.diagnoses as { message?:string } | undefined)?.message} addLabel="Add diagnosis" onAdd={()=>diagnoses.append({ code:'', codeSystem:'', label:'', type:'PRIMARY', status:'SUSPECTED', notes:'' })}>
            {diagnoses.fields.map((field, index) => <article className="clinical-entry-card" key={field.id}>
              <header><strong>Diagnosis {index + 1}</strong><button type="button" className="icon-button" aria-label={`Remove diagnosis ${index + 1}`} onClick={()=>diagnoses.remove(index)}><Trash2/></button></header>
              <div className="clinical-field-grid clinical-field-grid--three">
                <label><span>Diagnosis</span><input {...register(`diagnoses.${index}.label`)}/>{errorText(errors.diagnoses?.[index]?.label)}</label>
                <label><span>Code</span><input {...register(`diagnoses.${index}.code`)}/></label>
                <label><span>Code system</span><input {...register(`diagnoses.${index}.codeSystem`)} placeholder="ICD-10"/></label>
                <label><span>Type</span><select {...register(`diagnoses.${index}.type`)}><option value="PRIMARY">Primary</option><option value="SECONDARY">Secondary</option><option value="DIFFERENTIAL">Differential</option></select></label>
                <label><span>Status</span><select {...register(`diagnoses.${index}.status`)}><option value="CONFIRMED">Confirmed</option><option value="SUSPECTED">Suspected</option><option value="RULED_OUT">Ruled out</option></select></label>
                <label className="wide"><span>Notes</span><textarea rows={3} {...register(`diagnoses.${index}.notes`)}/></label>
              </div>
            </article>)}
          </ClinicalCollectionSection>

          <ClinicalCollectionSection title="Medication" copy="Current medication and new prescriptions remain inside Clinical Service for V1." addLabel="Add medication" onAdd={()=>medications.append({ kind:'PRESCRIBED', name:'', strength:'', form:'', dosage:'', frequency:'', route:'', duration:'', quantity:'', specialInstructions:'' })}>
            {medications.fields.map((field, index) => <article className="clinical-entry-card" key={field.id}>
              <header><strong>Medication {index + 1}</strong><button type="button" className="icon-button" aria-label={`Remove medication ${index + 1}`} onClick={()=>medications.remove(index)}><Trash2/></button></header>
              <div className="clinical-field-grid clinical-field-grid--three">
                <label><span>Kind</span><select {...register(`medications.${index}.kind`)}><option value="PRESCRIBED">Prescribed</option><option value="CURRENT">Current</option></select></label>
                <label><span>Name</span><input {...register(`medications.${index}.name`)}/>{errorText(errors.medications?.[index]?.name)}</label>
                <label><span>Strength</span><input {...register(`medications.${index}.strength`)}/></label>
                <label><span>Form</span><input {...register(`medications.${index}.form`)} placeholder="Tablet"/></label>
                <label><span>Dosage</span><input {...register(`medications.${index}.dosage`)}/>{errorText(errors.medications?.[index]?.dosage)}</label>
                <label><span>Frequency</span><input {...register(`medications.${index}.frequency`)}/>{errorText(errors.medications?.[index]?.frequency)}</label>
                <label><span>Route</span><input {...register(`medications.${index}.route`)}/>{errorText(errors.medications?.[index]?.route)}</label>
                <label><span>Duration</span><input {...register(`medications.${index}.duration`)}/>{errorText(errors.medications?.[index]?.duration)}</label>
                <label><span>Quantity</span><input {...register(`medications.${index}.quantity`)}/></label>
                <label className="wide"><span>Special instructions</span><textarea rows={3} {...register(`medications.${index}.specialInstructions`)}/></label>
              </div>
            </article>)}
          </ClinicalCollectionSection>
        </fieldset>

        {!immutable&&<footer className="clinical-record-actions">
          <span><ShieldCheck/>Draft writes are organisation- and doctor-scoped.</span>
          <button type="button" className="secondary" disabled={saveMutation.isPending||finalizeMutation.isPending||!isDirty} onClick={()=>void manualSave()}><Save/>{saveMutation.isPending?'Saving…':'Save now'}</button>
          <button type="button" className="primary" disabled={saveMutation.isPending||finalizeMutation.isPending} onClick={finalize}>{finalizeMutation.isPending?<LoaderCircle className="spin"/>:<LockKeyhole/>}Finalise consultation</button>
        </footer>}
        {finalizeError&&<p className="form-message form-message--error" role="alert">{finalizeError}</p>}
      </form>

      <aside className="clinical-context-column">
        <PatientSummaryPanel patientRegistrationId={record.patientRegistrationId}/>
        <MedicalFilePanel consultationId={record.id}/>
        {immutable&&<CorrectionPanel
          record={record}
          recordKey={recordKey}
          versionRef={versionRef}
          onCorrected={updated => reset(clinicalRecordToForm(updated))}
        />}
      </aside>
    </div>
  </div>
}

function ClinicalNarrativeFields({ register, errors }:{
  register:ReturnType<typeof useForm<ClinicalFormValues>>['register']
  errors:ReturnType<typeof useForm<ClinicalFormValues>>['formState']['errors']
}) {
  return <section className="clinical-form-section clinical-form-section--narrative">
    <header><div><p className="eyebrow">Clinical narrative</p><h2>Assessment and plan</h2><p>The record remains a draft until the treating doctor finalises it.</p></div><FileHeart/></header>
    <div className="clinical-narrative-grid">
      <label className="wide"><span>Reason for consultation</span><textarea rows={3} {...register('reasonForConsultation')}/>{errorText(errors.reasonForConsultation)}</label>
      <label className="wide"><span>Clinical assessment</span><textarea rows={6} {...register('clinicalAssessment')}/>{errorText(errors.clinicalAssessment)}</label>
      <label><span>Treatment plan</span><textarea rows={5} {...register('treatmentPlan')}/>{errorText(errors.treatmentPlan)}</label>
      <label><span>Follow-up instructions</span><textarea rows={5} {...register('followUpInstructions')}/>{errorText(errors.followUpInstructions)}</label>
      <label className="wide"><span>Additional notes</span><textarea rows={4} {...register('additionalNotes')}/>{errorText(errors.additionalNotes)}</label>
    </div>
  </section>
}

function ClinicalCollectionSection({
  title,
  copy,
  addLabel,
  onAdd,
  error,
  children,
}:{
  title:string
  copy:string
  addLabel:string
  onAdd():void
  error?:string
  children:React.ReactNode
}) {
  return <section className="clinical-form-section">
    <header><div><p className="eyebrow">Structured record</p><h2>{title}</h2><p>{copy}</p></div><button type="button" className="secondary" onClick={onAdd}><Plus/>{addLabel}</button></header>
    {error&&<p className="form-message form-message--error">{error}</p>}
    <div className="clinical-entry-list">{children}</div>
  </section>
}

function PatientSummaryPanel({ patientRegistrationId }:{
  patientRegistrationId:string
}) {
  const auth = useAuth()
  const organisationId = auth.session?.user.organizationId || 'none'
  const summaryQuery = useQuery({
    queryKey:[
      'clinical-patient-summary',
      organisationId,
      patientRegistrationId,
    ],
    queryFn:() => consultationRestService.getPatientSummary(patientRegistrationId),
    enabled:organisationId !== 'none',
  })
  return <section className="clinical-summary" aria-labelledby="patient-summary-title">
    <header><div><p className="eyebrow">Minimum necessary</p><h2 id="patient-summary-title">Patient clinical summary</h2></div><ShieldCheck/></header>
    {summaryQuery.isPending&&<div className="clinical-inline-state"><LoaderCircle className="spin"/>Loading authorised history…</div>}
    {summaryQuery.isError&&<div className="clinical-inline-state clinical-inline-state--error"><span>{apiErrorMessage(summaryQuery.error, 'The patient summary could not be loaded.')}</span><button type="button" className="secondary" onClick={()=>void summaryQuery.refetch()}><RefreshCw/>Retry</button></div>}
    {summaryQuery.data&&<PatientSummaryContent summary={summaryQuery.data}/>}
  </section>
}

function PatientSummaryContent({ summary }:{ summary:PatientClinicalSummaryResource }) {
  return <div className="clinical-summary-list">
    <div className="clinical-care-boundary"><ShieldCheck/><span><strong>{statusLabel(summary.careRelationship.appointmentStatus)} care relationship</strong><small>Appointment {summary.careRelationship.appointmentId.slice(0, 8)}</small></span></div>
    {summary.encounters.map(encounter => <article key={encounter.consultationId}>
      <header><span><strong>{encounter.reasonForConsultation || 'Finalised consultation'}</strong><small>{new Intl.DateTimeFormat(undefined,{ dateStyle:'medium' }).format(new Date(encounter.finalizedAt))}</small></span><History/></header>
      {encounter.diagnoses.length>0&&<div><small>Diagnoses</small><p>{encounter.diagnoses.map(value=>value.label).join(', ')}</p></div>}
      {encounter.medications.length>0&&<div><small>Medication</small><p>{encounter.medications.map(value=>`${value.name}${value.dosage?` · ${value.dosage}`:''}`).join(', ')}</p></div>}
      {encounter.allergies.length>0&&<div><small>Allergies</small><p>{encounter.allergies.join(', ')}</p></div>}
    </article>)}
    {!summary.encounters.length&&<div className="clinical-inline-state"><History/><span>No prior finalised encounter is available in this authorised summary.</span></div>}
  </div>
}

function CorrectionPanel({
  record,
  recordKey,
  versionRef,
  onCorrected,
}:{
  record:ClinicalRecordResource
  recordKey:readonly unknown[]
  versionRef:React.MutableRefObject<number>
  onCorrected(record:ClinicalRecordResource):void
}) {
  const queryClient = useQueryClient()
  const [notice, setNotice] = useState('')
  const { register, handleSubmit, reset, watch, formState:{ errors } } =
    useForm<CorrectionValues>({
      resolver:zodResolver(correctionSchema),
      defaultValues:{
        fieldName:'clinicalAssessment',
        newValue:'',
        reason:'',
      },
    })
  const selectedField = watch('fieldName') as ConsultationCorrectionField
  const mutation = useMutation({
    mutationFn:(values:CorrectionValues) => consultationRestService.correct(
      record.id,
      {
        version:versionRef.current,
        targetType:'CONSULTATION',
        targetId:null,
        fieldName:values.fieldName,
        newValue:values.newValue || null,
        reason:values.reason,
      },
    ),
    onSuccess:updated => {
      versionRef.current = updated.version
      queryClient.setQueryData<ClinicalRecordResource>(recordKey, updated)
      onCorrected(updated)
      reset({ fieldName:selectedField, newValue:'', reason:'' })
      setNotice('The correction was appended without changing the signed source.')
    },
  })
  const submit = handleSubmit(values => mutation.mutate(values))
  const current = record[selectedField]

  return <section className="clinical-correction" aria-labelledby="correction-title">
    <header><div><p className="eyebrow">Append-only</p><h2 id="correction-title">Correct the final record</h2></div><LockKeyhole/></header>
    <p className="clinical-section-copy">The original value remains immutable; Sahha records the old value, new value, author, time, and reason.</p>
    <form onSubmit={submit} noValidate>
      <label><span>Field</span><select {...register('fieldName')}>{consultationFields.map(([value,label])=><option value={value} key={value}>{label}</option>)}</select></label>
      <div className="clinical-current-value"><small>Current effective value</small><p>{current || 'No value recorded'}</p></div>
      <label><span>Corrected value</span><textarea rows={4} {...register('newValue')}/>{errorText(errors.newValue)}</label>
      <label><span>Reason for correction</span><textarea rows={3} {...register('reason')}/>{errorText(errors.reason)}</label>
      {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}
      {mutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(mutation.error, 'The correction could not be appended.')}</p>}
      <button className="primary" disabled={mutation.isPending}>{mutation.isPending?<LoaderCircle className="spin"/>:<Plus/>}Append correction</button>
    </form>
    {record.corrections.length>0&&<div className="clinical-correction-history"><p className="eyebrow">Correction history</p>{record.corrections.map(value=><article key={value.id}><strong>{consultationFields.find(([field])=>field===value.fieldName)?.[1] || value.fieldName}</strong><p>{value.oldValue || 'Empty'} → {value.newValue || 'Empty'}</p><small>{value.reason} · actor {value.actorUserId.slice(0,8)} · version {value.consultationVersion} · {new Intl.DateTimeFormat(undefined,{ dateStyle:'medium', timeStyle:'short' }).format(new Date(value.correctedAt))}</small></article>)}</div>}
  </section>
}

function ClinicalPageState({ label }:{ label:string }) {
  return <div className="page workflow-page clinical-workspace-page"><div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>{label}</strong></div></div>
}

function ClinicalPageError({ error, retry }:{ error:unknown; retry():void }) {
  return <div className="page workflow-page clinical-workspace-page"><div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Clinical record unavailable</strong><span>{apiErrorMessage(error, 'The clinical record could not be loaded.')}</span><button className="secondary" onClick={retry}><RefreshCw/>Try again</button></div></div>
}
