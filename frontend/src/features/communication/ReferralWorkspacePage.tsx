import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../app/auth/AuthProvider'
import { WorkflowDrawer, WorkflowEmpty, WorkflowNotice, WorkflowPageHeader, WorkflowStatus } from '../../components/workflow/WorkflowUI'
import type { ReferralAction, ReferralCommand, ReferralDirection, ReferralResource } from '../../models/referral'
import { referralRestService } from '../../services/api/referralRestService'
import { apiErrorMessage } from '../../services/api/ApiError'
import { DataState, Pagination, dateTime } from '../workspace/LiveWorkspacePages'
import { ReferralComposer } from './ReferralComposer'
import { SharedReferralPreview } from './SharedReferralPreview'
import { SharedCareHistoryPreview } from './SharedCareHistoryPreview'

const actionLabels:Record<ReferralAction,string> = { send:'Send referral', accept:'Accept referral', reject:'Reject referral', revoke:'Revoke referral', complete:'Complete referral' }
const referralTypeLabel = (referral:ReferralResource) => referral.referralType === 'SHARED_TREATMENT' ? 'Shared treatment' : 'Second opinion'
export function referralActions(referral:ReferralResource, userId:string, now = Date.now()):ReferralAction[] {
  if (Date.parse(referral.accessExpiresAt) <= now) return []
  const sender = referral.senderUserId === userId
  const recipient = referral.recipientUserId === userId
  if (sender && referral.status === 'DRAFT') return ['send','revoke']
  if (referral.status === 'SENT') return sender ? ['revoke'] : recipient ? ['accept','reject'] : []
  if (referral.status === 'ACTIVE') return sender ? ['complete','revoke'] : recipient ? ['complete'] : []
  return []
}

export function ReferralWorkspacePage() {
  const { session } = useAuth()
  const [params, setParams] = useSearchParams()
  const [direction, setDirection] = useState<ReferralDirection>('ALL')
  const [page, setPage] = useState(0)
  const userId = session?.user.id || ''
  const organisationId = session?.user.organizationId || ''
  const selected = params.get('referral')
  const query = useQuery({
    queryKey:['referrals', userId, organisationId, direction, page],
    queryFn:() => referralRestService.list(direction, page),
    enabled:Boolean(userId && organisationId), retry:false,
    staleTime:0, refetchOnWindowFocus:true, refetchInterval:15_000,
  })
  const close = () => setParams(current => { const next = new URLSearchParams(current); next.delete('referral'); return next })
  return <div className="page live-workspace-page referral-workspace">
    <WorkflowPageHeader eyebrow="Private doctor collaboration" title="Referrals & selected sharing." copy="Only referrals you send or receive in the active organisation appear here. Messaging never grants patient-record access."/>
    <p className="workflow-notice">Create referrals from your own finalised consultations. Second opinions share selected information while you keep treating the patient. Shared treatment makes both doctors responsible after acceptance, with consented finalised-history and document access in this organisation.</p>
    {!organisationId ? <Link className="primary" to="/organisations/select">Select organisation</Link> : <>
      <div className="filter-row" role="group" aria-label="Referral direction">
        <button className="primary" onClick={() => setParams({ compose:'new' })}>New referral</button>
        {(['ALL','RECEIVED','SENT'] as const).map(value => <button key={value} className="secondary" aria-pressed={direction === value} onClick={() => { setDirection(value); setPage(0); close() }}>{value === 'ALL' ? 'All referrals' : value === 'RECEIVED' ? 'Received' : 'Sent'}</button>)}
        <button className="secondary" disabled={query.isFetching} onClick={() => query.refetch()}>Refresh referrals</button>
      </div>
      <DataState query={query}>
        <div className="referral-list">{query.data?.content.map(referral => <article className="workflow-card" key={referral.id}>
          <header><WorkflowStatus value={referral.status}/><span>{referral.priority}</span></header>
          <h2>{referral.reason}</h2>
          <p>{referral.senderDisplayName} → {referral.recipientDisplayName}</p>
          <p>{referralTypeLabel(referral)} · {referral.referralType === 'SHARED_TREATMENT' ? 'Finalised care history and documents' : referral.selectedItems.length + ' selected resources'} · Access expires {dateTime(referral.accessExpiresAt)}</p>
          <button className="primary" onClick={() => setParams({ referral:referral.id })}>Review referral</button>
        </article>)}</div>
        {!query.data?.content.length && <WorkflowEmpty title="No referrals in this view" copy="Only authorised participant referrals from Communication Service are shown. There are no sample records."/>}
        <Pagination page={page} pages={query.data?.totalPages || 0} change={setPage}/>
      </DataState>
      {selected && <ReferralDetail key={`${userId}:${organisationId}:${selected}`} id={selected} userId={userId} organisationId={organisationId} close={close}/>}
      {params.get('compose') === 'new' && <ReferralComposer key={userId + ':' + organisationId} userId={userId} organisationId={organisationId}
        close={() => setParams({})} created={referral => setParams({ referral:referral.id })}/>}
    </>}
  </div>
}

function ReferralDetail({ id, userId, organisationId, close }: { id:string; userId:string; organisationId:string; close:() => void }) {
  const client = useQueryClient()
  const queryKey = ['referral', userId, organisationId, id]
  const query = useQuery({ queryKey, queryFn:() => referralRestService.get(id), retry:false, staleTime:0, gcTime:0, refetchOnWindowFocus:true, refetchInterval:5_000 })
  const [action, setAction] = useState<ReferralAction | null>(null)
  const [reviewedVersion, setReviewedVersion] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [now, setNow] = useState(Date.now)
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1_000); return () => window.clearInterval(timer) }, [])
  const mutation = useMutation({
    mutationFn:(command:ReferralCommand) => referralRestService.command(id, command),
    onMutate:async () => { await client.cancelQueries({ queryKey }) },
    onSuccess:async updated => {
      await client.cancelQueries({ queryKey })
      client.setQueryData(queryKey, updated)
      void client.invalidateQueries({ queryKey:['referrals', userId, organisationId] })
      setAction(null); setReason('')
    },
    onError:() => { void client.invalidateQueries({ queryKey }) },
  })
  const referral = query.data
  const actions = referral ? referralActions(referral, userId, now) : []
  const expired = referral && Date.parse(referral.accessExpiresAt) <= now
  const needsReason = action === 'reject' || action === 'revoke'
  const submit = () => {
    if (!referral || !action || !actions.includes(action) || referral.version !== reviewedVersion) return
    const command:ReferralCommand = action === 'reject' || action === 'revoke'
      ? { action, expectedVersion:referral.version, reason:reason.trim() }
      : { action, expectedVersion:referral.version }
    mutation.mutate(command)
  }
  return <WorkflowDrawer open wide close={close} eyebrow="Participant-only referral" title="Review referral" copy="Review the referral type, consent and expiry before acting. Protected content is checked by its owning service.">
    <DataState query={query}>{referral && <div className="referral-detail">
      <header><WorkflowStatus value={referral.status}/><span>Version {referral.version}</span></header>
      <p>{referralTypeLabel(referral)} — {referral.referralType === 'SHARED_TREATMENT'
        ? 'Both doctors participate after acceptance. Each doctor records treatment through their own appointments and consultations.'
        : 'The sending doctor remains responsible for treatment. Only selected resources are shared.'}</p>
      {referral.referralType === 'SHARED_TREATMENT' && <WorkflowNotice message="After acceptance, open shared-care history to read finalised encounters and their protected documents. Selected-file previews remain separately authorised."/>}
      {expired && <WorkflowNotice error message="The access window has ended. No shared content can be opened."/>}
      <h3>{referral.reason}</h3>
      <dl><dt>From</dt><dd>{referral.senderDisplayName}</dd><dt>To</dt><dd>{referral.recipientDisplayName}</dd><dt>Patient registration</dt><dd>{referral.patientRegistrationId}</dd><dt>Purpose</dt><dd>{referral.purpose}</dd><dt>Consent</dt><dd>{referral.consentType.replaceAll('_',' ')} · {dateTime(referral.consentRecordedAt)}</dd><dt>Evidence reference</dt><dd>{referral.consentEvidenceReference}</dd><dt>Access expires</dt><dd>{dateTime(referral.accessExpiresAt)}</dd></dl>
      {referral.clinicalSummary && <section><h3>Referral summary</h3><p className="referral-text">{referral.clinicalSummary}</p></section>}
      {referral.decisionReason && <p>Decision reason: {referral.decisionReason}</p>}
      <SharedReferralPreview key={`${referral.id}:${referral.version}:${referral.sharingGrantId}`} referral={referral} userId={userId} organisationId={organisationId} now={now}/>
      <SharedCareHistoryPreview referral={referral} userId={userId} organisationId={organisationId} now={now}/>
      {!action && <div className="filter-row">{actions.map(value => <button className="secondary" key={value} disabled={mutation.isPending || query.isFetching} onClick={() => { mutation.reset(); setReviewedVersion(referral.version); setAction(value) }}>{actionLabels[value]}</button>)}</div>}
      {action && <form className="live-profile-form" onSubmit={event => { event.preventDefault(); submit() }}>
        <h3>{actionLabels[action]}?</h3>
        <p>{action === 'accept' ? referral.referralType === 'SHARED_TREATMENT'
          ? 'Accept shared-treatment responsibility with the sending doctor under the recorded consent and expiry. This authorises same-organisation finalised history and documents, never editing another doctor’s finalised records.'
          : 'Accept the referral and activate access only to its selected resources until expiry.'
          : action === 'send' ? 'Send this draft and its recorded consent to the named recipient. Access remains inactive until acceptance.' : action === 'complete' ? 'Completion ends shared access for this referral.' : action === 'revoke' ? 'Revocation immediately ends future access through this referral.' : 'Reject this referral without activating shared access.'}</p>
        {needsReason && <label>Decision reason<textarea required minLength={3} maxLength={500} value={reason} onChange={event => setReason(event.target.value)}/></label>}
        {(!actions.includes(action) || referral.version !== reviewedVersion) && <WorkflowNotice error message="The referral changed. Cancel this action and review its current state."/>}
        <div className="filter-row"><button type="button" className="secondary" disabled={mutation.isPending} onClick={() => { setAction(null); setReason('') }}>Cancel</button><button className="primary" disabled={mutation.isPending || query.isFetching || !actions.includes(action) || referral.version !== reviewedVersion || (needsReason && reason.trim().length < 3)}>{mutation.isPending ? 'Saving…' : 'Confirm ' + action}</button></div>
      </form>}
      {mutation.isSuccess && <WorkflowNotice message="Referral updated by Communication Service."/>}
      {mutation.isError && <WorkflowNotice error message={apiErrorMessage(mutation.error, 'Referral could not be updated. Reload before retrying.')}/>}
    </div>}</DataState>
  </WorkflowDrawer>
}
