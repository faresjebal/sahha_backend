import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { WorkflowDrawer, WorkflowEmpty, WorkflowNotice } from '../../components/workflow/WorkflowUI'
import type { ReferralSourcePageResource } from '../../models/clinical'
import type { CreateReferralCommand, ReferralResource, ReferralType } from '../../models/referral'
import { apiErrorMessage } from '../../services/api/ApiError'
import { communicationRestService } from '../../services/api/communicationRestService'
import { consultationRestService } from '../../services/api/consultationRestService'
import { medicalFileRestService } from '../../services/api/medicalFileRestService'
import { referralRestService } from '../../services/api/referralRestService'
import { DataState, Pagination, dateTime } from '../workspace/LiveWorkspacePages'
import { consentTypes, referralChoices, referralCreationSchema, selectedReferralItems, type ReferralCreationValues } from './referralCreation'

type Source = ReferralSourcePageResource['content'][number]
interface Props { userId:string; organisationId:string; close:() => void; created:(referral:ReferralResource) => void }

export function ReferralComposer({ userId, organisationId, close, created }:Props) {
  const [page, setPage] = useState(0)
  const [source, setSource] = useState<Source | null>(null)
  const [busy, setBusy] = useState(false)
  const query = useQuery({ queryKey:['referral-sources', userId, organisationId, page],
    queryFn:() => consultationRestService.listReferralSources(page), retry:false, staleTime:0 })
  return <WorkflowDrawer open wide close={() => { if (!busy) close() }} eyebrow="Explicit referral and consent" title="New referral"
    copy="Choose your own finalised consultation. Nothing is selected or sent automatically.">
    {!source ? <DataState query={query}>
      <h3>Choose a finalised consultation</h3>
      <p>Only consultations authored under your current doctor membership are available. Patient registrations identify the source without loading a whole patient record.</p>
      <div className="referral-list">{query.data?.content.map(item => <article className="workflow-card" key={item.consultationId}>
        <h4>Finalised {dateTime(item.finalizedAt)}</h4>
        <p>Patient registration <code>{item.patientRegistrationId}</code></p>
        <p>Consultation <code>{item.consultationId}</code></p>
        <button className="secondary" onClick={() => setSource(item)}>Use consultation</button>
      </article>)}</div>
      {!query.data?.content.length && <WorkflowEmpty title="No eligible finalised consultations" copy="Finalise a consultation under your current doctor membership before creating a referral. Drafts and other doctors’ records cannot be selected."/>}
      <Pagination page={page} pages={query.data?.totalPages || 0} change={setPage}/>
    </DataState> : <ReferralForm key={source.consultationId} userId={userId} organisationId={organisationId} source={source}
      sourcePage={page} changeSource={() => setSource(null)} busy={setBusy} created={created}/>}
  </WorkflowDrawer>
}

function ReferralForm({ userId, organisationId, source, sourcePage, changeSource, busy, created }:
  Omit<Props, 'close'> & { source:Source; sourcePage:number; changeSource:() => void; busy:(value:boolean) => void }) {
  const client = useQueryClient()
  const [doctorPage, setDoctorPage] = useState(0)
  const [review, setReview] = useState<{ command:CreateReferralCommand; version:number; recipient:string; labels:string[] } | null>(null)
  const [attempted, setAttempted] = useState(false)
  const [formError, setFormError] = useState('')
  const form = useForm<ReferralCreationValues>({ resolver:zodResolver(referralCreationSchema), defaultValues:{
    referralType:'SECOND_OPINION', recipientUserId:'', reason:'', priority:'ROUTINE', clinicalSummary:'', purpose:'', consentEvidenceReference:'',
    consentRecordedAt:'', accessExpiresAt:'', selections:[], reviewed:false, sharedCareConfirmed:false,
  } })
  const { register, formState:{ errors }, watch, setValue } = form
  const sharedTreatment = watch('referralType') === 'SHARED_TREATMENT'
  const record = useQuery({ queryKey:['referral-source-record', userId, organisationId, source.consultationId],
    queryFn:() => consultationRestService.getRecord(source.consultationId), retry:false, staleTime:0, gcTime:0 })
  const owned = record.data?.id === source.consultationId && record.data?.status === 'FINALIZED'
    && record.data?.organisationId === organisationId && record.data?.doctorUserId === userId
    && record.data?.patientRegistrationId === source.patientRegistrationId
  const files = useQuery({ queryKey:['referral-source-files', userId, organisationId, source.consultationId],
    queryFn:() => medicalFileRestService.list(source.consultationId), enabled:owned && !sharedTreatment, retry:false, staleTime:0, gcTime:0 })
  const doctors = useQuery({ queryKey:['referral-recipients', userId, organisationId, doctorPage],
    queryFn:() => communicationRestService.doctors(organisationId, doctorPage), retry:false, staleTime:0 })
  const choices = owned && record.data && files.isSuccess ? referralChoices(record.data, files.data) : []
  const eligibleDoctors = doctors.data?.content.filter(item => item.organisationId === organisationId && item.userId !== userId) || []
  const selected = watch('selections')
  const wholeSelected = selected.some(key => key.startsWith('CONSULTATION:'))
  const ready = owned && record.isSuccess && doctors.isSuccess && !record.isFetching && !doctors.isFetching
    && (sharedTreatment || (files.isSuccess && !files.isFetching))
  const changeType = (type:ReferralType) => {
    setValue('referralType', type)
    setValue('selections', []); setValue('reviewed', false); setValue('sharedCareConfirmed', false)
    setValue('purpose', ''); form.resetField('consentType')
    setValue('consentEvidenceReference', ''); setValue('consentRecordedAt', '')
    form.clearErrors(); setFormError('')
  }
  const toggle = (key:string, checked:boolean) => {
    let next = selected.filter(value => value !== key)
    if (checked && key.startsWith('CONSULTATION:')) next = next.filter(value => value.startsWith('MEDICAL_DOCUMENT:'))
    if (checked) next.push(key)
    setValue('selections', next, { shouldValidate:true }); setValue('reviewed', false)
  }
  const prepare = (values:ReferralCreationValues, sendImmediately:boolean) => {
    setFormError('')
    if (!ready || !record.data) return
    const recipient = eligibleDoctors.find(item => item.userId === values.recipientUserId)
    if (!recipient) { setFormError('The recipient is no longer in this eligible directory page. Choose a colleague again.'); return }
    try {
      const selectedItems = values.referralType === 'SHARED_TREATMENT' ? [] : selectedReferralItems(values.selections, choices)
      setReview({ version:record.data.version, recipient:recipient.displayName,
        labels:values.selections.map(key => choices.find(item => item.key === key)!.label),
        command:{ referralType:values.referralType, referralRequestId:crypto.randomUUID(), recipientUserId:recipient.userId,
          patientRegistrationId:record.data.patientRegistrationId, sourceConsultationId:record.data.id,
          reason:values.reason, priority:values.priority,
          clinicalSummary:values.clinicalSummary || null, purpose:values.purpose, consentType:values.consentType,
          consentEvidenceReference:values.consentEvidenceReference, consentRecordedAt:new Date(values.consentRecordedAt).toISOString(),
          accessExpiresAt:new Date(values.accessExpiresAt).toISOString(), selectedItems, sendImmediately },
      })
    } catch (error) { setFormError(apiErrorMessage(error, 'Review the selected resources.')) }
  }
  const mutation = useMutation({
    mutationFn:async () => {
      if (!review) throw new Error('Review the referral first.')
      // Once POST has been attempted, retries send the exact frozen body and request ID.
      // No clinical content is persisted in browser storage.
      if (!attempted) {
        const [currentSources, currentRecord, currentFiles, currentDoctors] = await Promise.all([
          consultationRestService.listReferralSources(sourcePage), consultationRestService.getRecord(source.consultationId),
          review.command.referralType === 'SECOND_OPINION' ? medicalFileRestService.list(source.consultationId) : Promise.resolve([]),
          communicationRestService.doctors(organisationId, doctorPage),
        ])
        if (!currentSources.content.some(item => item.consultationId === source.consultationId)
          || currentRecord.status !== 'FINALIZED' || currentRecord.id !== source.consultationId
          || currentRecord.organisationId !== organisationId || currentRecord.doctorUserId !== userId
          || currentRecord.patientRegistrationId !== review.command.patientRegistrationId || currentRecord.version !== review.version
          || !currentDoctors.content.some(item => item.organisationId === organisationId && item.userId === review.command.recipientUserId && item.userId !== userId)) {
          throw new Error('The source, its version or recipient eligibility changed. Return to the form and reload the source.')
        }
        if (review.command.referralType === 'SECOND_OPINION') {
        selectedReferralItems(review.command.selectedItems.map(item => `${item.resourceType}:${item.resourceId}`), referralChoices(currentRecord, currentFiles))
        }
        if (Date.parse(review.command.accessExpiresAt) <= Date.now()) throw new Error('The access window has ended. Choose a future expiry.')
        setAttempted(true)
      }
      return referralRestService.create(review.command)
    },
    onMutate:() => busy(true),
    onSuccess:referral => {
      void client.invalidateQueries({ queryKey:['referrals', userId, organisationId] })
      created(referral)
    },
    onSettled:() => busy(false),
    retry:false,
  })

  return <div className="referral-composer">
    <p>Patient registration <code>{source.patientRegistrationId}</code></p>
    {!review ? <>
      <button className="secondary" onClick={changeSource}>Choose another consultation</button>
      <DataState query={record}>
        {!owned && <WorkflowNotice error message="This consultation is not an owned, finalised source in the active organisation."/>}
        {owned && <DataState query={doctors}>
          <form className="live-profile-form" noValidate onSubmit={form.handleSubmit(values => prepare(values, false))}>
            <label>Referral type<select aria-label="Referral type" {...register('referralType')} onChange={event => changeType(event.target.value as ReferralType)}>
              <option value="SECOND_OPINION">Second opinion — selected information</option>
              <option value="SHARED_TREATMENT">Shared treatment — both doctors treat</option>
            </select></label>
            <WorkflowNotice message={sharedTreatment
              ? 'After acceptance, both doctors are responsible for treatment. The care scope covers this patient’s finalised clinical history and clean documents in this organisation, including encounters finalised later while access remains active. Drafts and other organisations are excluded. Each doctor uses their own appointments and consultations; another doctor’s finalised records cannot be edited.'
              : 'The sending doctor remains responsible for treatment. The recipient receives only explicitly selected information after acceptance.'}/>
            <label>Recipient colleague<select {...register('recipientUserId')}><option value="">Choose a colleague</option>{eligibleDoctors.map(item => <option key={item.membershipId} value={item.userId}>{item.displayName}</option>)}</select></label>
            {!eligibleDoctors.length && <p>No other eligible colleagues on this page.</p>}
            <Pagination page={doctorPage} pages={doctors.data?.totalPages || 0} change={page => { setValue('recipientUserId', ''); setDoctorPage(page) }}/>
            <label>Referral reason<textarea {...register('reason')} maxLength={1000}/></label>
            <label>Priority<select {...register('priority')}><option value="ROUTINE">Routine</option><option value="URGENT">Urgent</option></select></label>
            <label>Optional clinical summary<textarea {...register('clinicalSummary')} maxLength={4000}/></label>
            <p>The reason, summary, purpose and evidence reference form part of the referral message. Include only information needed by this recipient.</p>
            {!sharedTreatment && <DataState query={files}><fieldset className="referral-selections"><legend>Select resources to share</legend>
              <p>Select individual items for minimal sharing. A whole consultation includes all its clinical sections, notes and attributable corrections; it does not include files.</p>
              {choices.map(choice => <label key={choice.key} className="referral-choice"><input type="checkbox" checked={selected.includes(choice.key)}
                disabled={wholeSelected && choice.resourceType !== 'CONSULTATION' && choice.resourceType !== 'MEDICAL_DOCUMENT'}
                onChange={event => toggle(choice.key, event.target.checked)}/><span>{choice.label}</span></label>)}
              <p>Only your stored files marked clean and available can be selected. Pending, rejected and unavailable files are excluded.</p>
            </fieldset></DataState>}
            <label>Sharing purpose<textarea {...register('purpose')} maxLength={500}/></label>
            <label>Recorded consent or legal basis<select {...register('consentType')}><option value="">Choose the recorded basis</option>{consentTypes.map(value => <option key={value} value={value}>{value.replaceAll('_', ' ')}</option>)}</select></label>
            <label>Consent evidence reference<input {...register('consentEvidenceReference')} maxLength={255}/></label>
            <label>Consent recorded at<input type="datetime-local" {...register('consentRecordedAt')}/></label>
            <label>Access expires at<input type="datetime-local" {...register('accessExpiresAt')}/></label>
            <p>Times use your browser’s local time zone. Expiry must be in the future and within 90 days. Choosing a basis does not record or obtain consent on the patient’s behalf.</p>
            {sharedTreatment && <label className="referral-choice"><input type="checkbox" {...register('sharedCareConfirmed')}/>
              <span>I acknowledge that both doctors will treat this patient and that the recorded consent covers finalised history and documents in this organisation, including later finalised encounters, until completion, revocation or expiry.</span></label>}
            <label className="referral-choice"><input type="checkbox" {...register('reviewed')}/><span>{sharedTreatment
              ? 'I confirm the shared-treatment scope, recipient, purpose and recorded consent evidence are accurate.'
              : 'I confirm the selected resources, recipient, purpose and recorded consent evidence are accurate.'}</span></label>
            {Object.entries(errors).map(([field, error]) => <p role="alert" key={field}>{field}: {error.message}</p>)}
            {formError && <WorkflowNotice error message={formError}/>}
            <div className="filter-row"><button className="secondary" disabled={!ready}>Review draft</button><button type="button" className="primary" disabled={!ready} onClick={form.handleSubmit(values => prepare(values, true))}>Review and send</button></div>
          </form>
        </DataState>}
      </DataState>
    </> : <section className="referral-detail">
      <h3>{review.command.sendImmediately ? 'Review before sending' : 'Review before saving a draft'}</h3>
      <h4>Referral type: {review.command.referralType === 'SHARED_TREATMENT' ? 'Shared treatment' : 'Second opinion'}</h4>
      <dl><dt>Recipient</dt><dd>{review.recipient}</dd><dt>Reason</dt><dd>{review.command.reason}</dd><dt>Priority</dt><dd>{review.command.priority}</dd>
        <dt>Purpose</dt><dd>{review.command.purpose}</dd><dt>Consent basis</dt><dd>{review.command.consentType.replaceAll('_', ' ')}</dd>
        <dt>Evidence reference</dt><dd>{review.command.consentEvidenceReference}</dd><dt>Consent recorded</dt><dd>{dateTime(review.command.consentRecordedAt)}</dd>
        <dt>Access expires</dt><dd>{dateTime(review.command.accessExpiresAt)}</dd><dt>Consultation version reviewed</dt><dd>{review.version}</dd></dl>
      {review.command.clinicalSummary && <p className="referral-text">{review.command.clinicalSummary}</p>}
      {review.command.referralType === 'SHARED_TREATMENT'
        ? <WorkflowNotice message="Shared-treatment scope: both doctors treat this patient. After acceptance, access covers finalised history and clean documents in this organisation, including later finalised encounters, until completion, revocation or expiry. No drafts, cross-organisation records or changes to another doctor’s finalised records are authorised. Each doctor records new treatment through their own appointments and consultations."/>
        : <><h4>Only these resources will be selected ({review.labels.length})</h4><ul>{review.labels.map((label, index) => <li key={index}>{label}</li>)}</ul></>}
      <p>{review.command.sendImmediately ? 'Sending delivers the referral message. Protected access remains inactive until the recipient accepts.' : 'Saving creates a private sender draft. It does not send a message or activate shared access.'}</p>
      {mutation.isError && <WorkflowNotice error message={apiErrorMessage(mutation.error, 'The referral could not be confirmed.')}/>}
      {attempted && mutation.isError && <p role="status">Delivery may have succeeded. Retry here with the same request to avoid duplicates. If you close this form, check your sent referrals and drafts before starting again. Unsaved form data is not kept after closing or reloading.</p>}
      <div className="filter-row">
        {!attempted && <button className="secondary" disabled={mutation.isPending} onClick={() => { setReview(null); mutation.reset(); void record.refetch(); if (!sharedTreatment) void files.refetch(); void doctors.refetch() }}>Back to edit</button>}
        <button className="primary" disabled={mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? 'Verifying and saving…' : attempted ? 'Retry same request' : review.command.sendImmediately ? 'Confirm and send' : 'Confirm draft'}</button>
      </div>
    </section>}
  </div>
}
