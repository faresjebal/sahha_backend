import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import { WorkflowNotice } from '../../components/workflow/WorkflowUI'
import type { ClinicalRecordResource } from '../../models/clinical'
import type { MedicalFileResource } from '../../models/medicalFile'
import type { ReferralResource } from '../../models/referral'
import type { SharedClinicalResource } from '../../models/sharedClinical'
import { sharedReferralRestService } from '../../services/api/sharedReferralRestService'
import { dateTime } from '../workspace/LiveWorkspacePages'

type Selection = ReferralResource['selectedItems'][number]
type Preview = { kind:'clinical'; data:SharedClinicalResource } | { kind:'file'; data:MedicalFileResource }

export function SharedReferralPreview({ referral, userId, organisationId, now }:{ referral:ReferralResource; userId:string; organisationId:string; now:number }) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const expiry = Date.parse(referral.accessExpiresAt)
  const eligible = referral.status === 'ACTIVE' && Boolean(referral.sharingGrantId)
    && referral.recipientUserId === userId && referral.organisationId === organisationId
    && Number.isFinite(expiry) && expiry > now
  const item = referral.selectedItems.find(value => value.id === selectedId)
  return <section aria-label="Protected selected content">
    <h3>Explicitly selected resources</h3>
    <p>Only the named recipient can open these selections after acceptance. A selected consultation does not include its files or the rest of the patient history.</p>
    <ul>{referral.selectedItems.map(value => <li key={value.id}>
      <span>{value.resourceType.replaceAll('_',' ')} <code>{value.resourceId}</code></span>{' '}
      {eligible && <button type="button" className="secondary" onClick={() => setSelectedId(value.id)}>Open selected {value.resourceType.toLowerCase().replaceAll('_',' ')}</button>}
    </li>)}</ul>
    {!eligible && <p>Protected previews are unavailable until the recipient has an active, unexpired grant.</p>}
    {eligible && item && <SelectedPreview key={`${referral.id}:${referral.version}:${item.id}:${item.resourceId}:${item.resourceType}`}
      referral={referral} item={item} now={now} close={() => setSelectedId(null)}/>}
  </section>
}

function SelectedPreview({ referral, item, now, close }:{ referral:ReferralResource; item:Selection; now:number; close:() => void }) {
  const [preview, setPreview] = useState<Preview | null>(null)
  const [error, setError] = useState(false)
  const [retry, setRetry] = useState(0)
  const [downloading, setDownloading] = useState(false)
  const [downloaded, setDownloaded] = useState(false)
  const [verifiedUntil, setVerifiedUntil] = useState(() => Date.parse(referral.accessExpiresAt))
  const current = useRef(false)
  const lifecycle = useRef(0)
  const readGeneration = useRef(0)
  const urls = useRef(new Set<string>())
  useEffect(() => {
    let disposed = false
    let timer:number | undefined
    current.current = true
    lifecycle.current++
    setVerifiedUntil(Date.parse(referral.accessExpiresAt))
    setPreview(null); setError(false); setDownloaded(false)
    const load = async () => {
      if (disposed || !current.current) return
      const generation = ++readGeneration.current
      try {
        const result:Preview = item.resourceType === 'MEDICAL_DOCUMENT'
          ? { kind:'file', data:await sharedReferralRestService.file(referral, item.resourceId) }
          : { kind:'clinical', data:await sharedReferralRestService.clinical(referral, item) }
        if (!disposed && current.current && generation === readGeneration.current) {
          setVerifiedUntil(Math.min(Date.parse(referral.accessExpiresAt), result.kind === 'clinical'
            ? Date.parse(result.data.validUntil) : Number.POSITIVE_INFINITY))
          setPreview(result)
          timer = window.setTimeout(() => void load(), 5_000)
        }
      } catch {
        if (!disposed && generation === readGeneration.current) {
          current.current = false
          setPreview(null); setError(true)
        }
      }
    }
    void load()
    return () => {
      disposed = true; current.current = false; readGeneration.current++; lifecycle.current++
      window.clearTimeout(timer)
      for (const url of urls.current) URL.revokeObjectURL(url)
      urls.current.clear()
    }
    // A new component key is used for identity, referral version or selection changes.
    // Data stays only in component memory; never in the shared query cache/storage.
  }, [retry])

  const validUntil = verifiedUntil
  const expired = !Number.isFinite(validUntil) || validUntil <= now
  const visible = !expired && !error ? preview : null
  useEffect(() => {
    if (expired) { current.current = false; readGeneration.current++; setPreview(null) }
  }, [expired])

  const download = async (file:MedicalFileResource) => {
    const generation = lifecycle.current
    const stillCurrent = () => current.current && generation === lifecycle.current
    setDownloading(true); setDownloaded(false)
    try {
      const blob = await sharedReferralRestService.download(referral, file, stillCurrent)
      if (!stillCurrent()) return
      const url = URL.createObjectURL(blob)
      urls.current.add(url)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = file.originalFilename; anchor.rel = 'noopener'
      anchor.click()
      window.setTimeout(() => { URL.revokeObjectURL(url); urls.current.delete(url) }, 0)
      setDownloaded(true)
    } catch {
      if (stillCurrent()) {
        current.current = false; readGeneration.current++
        setPreview(null); setError(true)
      }
    } finally { if (stillCurrent()) setDownloading(false) }
  }
  return <article className="workflow-card" aria-label="Selected content preview">
    <div className="filter-row"><h4>Read-only selected content</h4><button type="button" className="secondary" onClick={close}>Close preview</button></div>
    {expired && <WorkflowNotice error message="The selected access window has ended. The preview has been cleared."/>}
    {error && <><WorkflowNotice error message="Access could not be verified. The preview has been cleared. Refresh the referral before retrying."/>
      {!expired && <button type="button" className="secondary" onClick={() => { setDownloading(false); setRetry(value => value + 1) }}>Retry selected access</button>}</>}
    {!preview && !error && !expired && <p role="status">Verifying selected access…</p>}
    {visible?.kind === 'clinical' && <><p>Access valid until {dateTime(new Date(validUntil).toISOString())}. Rechecked while this preview is open.</p><ClinicalContent resource={visible.data}/></>}
    {visible?.kind === 'file' && <>
      <Fields values={[
        ['File', visible.data.originalFilename], ['Type', visible.data.contentType],
        ['Size', `${visible.data.size} bytes`], ['Scan state', visible.data.scanStatus],
      ]}/>
      <button type="button" className="primary" disabled={downloading || !visible.data.downloadAvailable}
        onClick={() => void download(visible.data)}>{downloading ? 'Authorising download…' : 'Download selected file'}</button>
      {!visible.data.downloadAvailable && <p>This file is not available for download.</p>}
      <p>The service revalidates sharing before issuing a short-lived token and again before returning bytes. Downloaded copies cannot be recalled by revoking sharing.</p>
      {downloaded && <p role="status">The authorised download has started.</p>}
    </>}
  </article>
}

function Fields({ values }:{ values:Array<[string, ReactNode]> }) {
  return <dl>{values.map(([label, value]) => <Fragment key={label}><dt>{label}</dt><dd className="referral-text">{value ?? 'Not recorded'}</dd></Fragment>)}</dl>
}
function Diagnosis({ value }:{ value:ClinicalRecordResource['diagnoses'][number] }) {
  return <Fields values={Object.entries({ Diagnosis:value.label, Code:value.code, 'Code system':value.codeSystem, Type:value.type, Status:value.status, Notes:value.notes })}/>
}
function Medication({ value }:{ value:ClinicalRecordResource['medications'][number] }) {
  return <Fields values={Object.entries({ Medication:value.name, Kind:value.kind, Strength:value.strength, Form:value.form, Dosage:value.dosage, Frequency:value.frequency, Route:value.route, Duration:value.duration, Quantity:value.quantity, Instructions:value.specialInstructions })}/>
}
function ClinicalContent({ resource }:{ resource:SharedClinicalResource }) {
  switch (resource.resourceType) {
    case 'DIAGNOSIS': return <Diagnosis value={resource.diagnosis}/>
    case 'MEDICATION': return <Medication value={resource.medication}/>
    case 'ALLERGY': return <Fields values={[["Allergy", resource.allergy.description], ['Notes', resource.allergy.notes]]}/>
    case 'CONSULTATION': return <ReadOnlyClinicalRecord record={resource.consultation}/>
  }
}
export function ReadOnlyClinicalRecord({ record }:{ record:ClinicalRecordResource }) {
  return <div className="selected-consultation">
    <Fields values={[
      ['Finalised', record.finalizedAt ? dateTime(record.finalizedAt) : 'Not recorded'],
      ['Author', record.doctorUserId], ['Version', record.version], ['Reason', record.reasonForConsultation],
      ['Assessment', record.clinicalAssessment], ['Treatment plan', record.treatmentPlan],
      ['Follow-up', record.followUpInstructions], ['Additional notes', record.additionalNotes],
    ]}/>
    <h4>Symptoms</h4>{record.symptoms.map(value => <Fields key={value.id} values={[["Symptom", value.name], ['Onset', value.onsetDescription], ['Severity', value.severity], ['Notes', value.notes]]}/>)}
    <h4>History</h4>{record.history.map(value => <Fields key={value.id} values={[["Category", value.category], ['Description', value.description], ['Notes', value.notes]]}/>)}
    <h4>Vital signs</h4>{record.vitalSigns ? <Fields values={[
      ['Measured', dateTime(record.vitalSigns.measuredAt)], ['Temperature (°C)', record.vitalSigns.temperatureCelsius],
      ['Systolic (mmHg)', record.vitalSigns.systolicBloodPressure], ['Diastolic (mmHg)', record.vitalSigns.diastolicBloodPressure],
      ['Heart rate (bpm)', record.vitalSigns.heartRateBpm], ['Respiratory rate', record.vitalSigns.respiratoryRateBpm],
      ['Oxygen saturation (%)', record.vitalSigns.oxygenSaturationPercent], ['Weight (kg)', record.vitalSigns.weightKg],
      ['Height (cm)', record.vitalSigns.heightCm], ...Object.entries(record.vitalSigns.specialtyMeasurements).map(([key, value]):[string, ReactNode] => [`Specialty: ${key}`, String(value ?? 'Not recorded')]),
    ]}/> : <p>Not recorded</p>}
    <h4>Examination</h4>{record.examinationFindings.map(value => <Fields key={value.id} values={[["Body system", value.bodySystem], ['Finding', value.finding], ['Notes', value.notes]]}/>)}
    <h4>Diagnoses</h4>{record.diagnoses.map(value => <Diagnosis key={value.id} value={value}/>)}
    <h4>Medication</h4>{record.medications.map(value => <Medication key={value.id} value={value}/>)}
    <h4>Attributable corrections</h4>{record.corrections.map(value => <Fields key={value.id} values={[
      ['Target', `${value.targetType} ${value.targetId ?? ''}`], ['Field', value.fieldName], ['Previous value', value.oldValue],
      ['Corrected value', value.newValue], ['Reason', value.reason], ['Author', value.actorUserId],
      ['Recorded', dateTime(value.correctedAt)], ['Version', value.consultationVersion],
    ]}/>)}
    {!record.corrections.length && <p>No corrections recorded.</p>}
  </div>
}
