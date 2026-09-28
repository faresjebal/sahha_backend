import { useEffect, useState } from 'react'
import { WorkflowNotice } from '../../components/workflow/WorkflowUI'
import type { ReferralResource } from '../../models/referral'
import type { SharedCareHistoryPage, SharedCareRecord } from '../../models/sharedCare'
import { sharedCareRestService } from '../../services/api/sharedCareRestService'
import { Pagination, dateTime } from '../workspace/LiveWorkspacePages'
import { ReadOnlyClinicalRecord } from './SharedReferralPreview'
import { SharedCareDocumentsPreview } from './SharedCareDocumentsPreview'

interface Props { referral:ReferralResource; userId:string; organisationId:string; now:number }
export function SharedCareHistoryPreview({ referral, userId, organisationId, now }:Props) {
  const [open, setOpen] = useState(false)
  if (referral.referralType !== 'SHARED_TREATMENT') return null
  const eligible = referral.status === 'ACTIVE' && Boolean(referral.sharingGrantId)
    && referral.organisationId === organisationId && [referral.senderUserId, referral.recipientUserId].includes(userId)
    && Number.isFinite(Date.parse(referral.accessExpiresAt)) && Date.parse(referral.accessExpiresAt) > now
  return <section aria-label="Shared-treatment history">
    <h3>Shared-treatment clinical history</h3>
    <p>Finalised encounters in this organisation only. Records and attributable corrections are read-only; each doctor uses their own appointment for new treatment.</p>
    {!eligible ? <p>History requires accepted, active and unexpired shared treatment.</p>
      : open ? <CareHistorySession key={`${userId}:${organisationId}:${referral.id}:${referral.patientRegistrationId}:${referral.version}:${referral.sharingGrantId}`}
        referral={referral} now={now} close={() => setOpen(false)}/>
        : <button type="button" className="secondary" onClick={() => setOpen(true)}>Open shared-care history</button>}
  </section>
}

function CareHistorySession({ referral, now, close }:{ referral:ReferralResource; now:number; close:() => void }) {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string | null>(null)
  const [view, setView] = useState<{ history:SharedCareHistoryPage; record:SharedCareRecord | null } | null>(null)
  const [error, setError] = useState(false)
  const [retry, setRetry] = useState(0)
  const [verifiedUntil, setVerifiedUntil] = useState(Date.parse(referral.accessExpiresAt))
  const expired = !Number.isFinite(verifiedUntil) || verifiedUntil <= now
  useEffect(() => {
    let disposed = false
    let timer:number | undefined
    setView(null); setError(false)
    if (expired) return
    const load = async () => {
      try {
        const history = await sharedCareRestService.list(referral, page)
        if (disposed) return
        const record = selected ? await sharedCareRestService.record(referral, selected) : null
        if (disposed) return
        setVerifiedUntil(Math.min(Date.parse(referral.accessExpiresAt), Date.parse(history.validUntil),
          record ? Date.parse(record.validUntil) : Number.POSITIVE_INFINITY))
        setView({ history, record })
        timer = window.setTimeout(() => void load(), 5_000)
      } catch {
        if (!disposed) { setView(null); setError(true) }
      }
    }
    void load()
    return () => { disposed = true; window.clearTimeout(timer) }
    // Referral/identity/scope/version changes remount this session. No clinical payload enters Query or browser storage.
  }, [page, selected, retry, expired])
  const changePage = (next:number) => { setView(null); setSelected(null); setPage(next) }
  const select = (id:string) => { setView(null); setSelected(id); setRetry(value => value + 1) }
  return <div>
    <button type="button" className="secondary" onClick={close}>Close shared-care history</button>
    {expired ? <WorkflowNotice error message="Shared-care access has expired. Close the history and refresh the referral."/>
      : error ? <><WorkflowNotice error message="Shared-care history could not be verified. Access may have ended or a service is unavailable."/>
        <button type="button" className="secondary" onClick={() => setRetry(value => value + 1)}>Retry shared-care history</button></>
        : !view ? <p role="status">Verifying shared-care access…</p> : <>
          {!view.history.content.length && <p>No finalised encounters are available in this organisation.</p>}
          <ul>{view.history.content.map(item => <li key={item.consultationId}>
            <span>Finalised {dateTime(item.finalizedAt)} · Author {item.doctorUserId}</span>{' '}
            <button type="button" className="secondary" onClick={() => select(item.consultationId)}>Open encounter {item.consultationId}</button>
          </li>)}</ul>
          <Pagination page={page} pages={view.history.totalPages} change={changePage}/>
          {view.record && <><ReadOnlyClinicalRecord record={view.record.consultation}/>
            <SharedCareDocumentsPreview key={view.record.consultation.id} referral={referral}
              consultationId={view.record.consultation.id} now={now}/></>}
        </>}
  </div>
}
