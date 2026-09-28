import { useEffect, useRef, useState } from 'react'
import { WorkflowNotice } from '../../components/workflow/WorkflowUI'
import type { MedicalFileResource } from '../../models/medicalFile'
import type { ReferralResource } from '../../models/referral'
import type { SharedCareFilePage } from '../../models/sharedCareFile'
import { sharedCareFileRestService } from '../../services/api/sharedCareFileRestService'
import { Pagination } from '../workspace/LiveWorkspacePages'

export function SharedCareDocumentsPreview({ referral, consultationId, now }:{
  referral:ReferralResource; consultationId:string; now:number
}) {
  const [open, setOpen] = useState(false)
  if (referral.referralType !== 'SHARED_TREATMENT' || referral.status !== 'ACTIVE' || !referral.sharingGrantId
    || !Number.isFinite(Date.parse(referral.accessExpiresAt)) || Date.parse(referral.accessExpiresAt) <= now) return null
  return <section aria-label="Shared-care documents">
    <h4>Encounter documents</h4>
    <p>Only clean, available documents from this finalised encounter are shown. Downloads require a fresh access check.</p>
    {open ? <DocumentSession key={[referral.organisationId, referral.id, referral.version, referral.sharingGrantId,
      referral.accessExpiresAt, referral.patientRegistrationId, consultationId].join(':')}
      referral={referral} consultationId={consultationId} now={now} close={() => setOpen(false)}/>
      : <button type="button" className="secondary" onClick={() => setOpen(true)}>Open encounter documents</button>}
  </section>
}

function DocumentSession({ referral, consultationId, now, close }:{
  referral:ReferralResource; consultationId:string; now:number; close:() => void
}) {
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(0)
  const [view, setView] = useState<SharedCareFilePage | null>(null)
  const [error, setError] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [downloaded, setDownloaded] = useState(false)
  const [verifiedUntil, setVerifiedUntil] = useState(Date.parse(referral.accessExpiresAt))
  const current = useRef(false)
  const liveExpiry = useRef(verifiedUntil)
  const generation = useRef(0)
  const urls = useRef(new Set<string>())
  const expired = !Number.isFinite(verifiedUntil) || verifiedUntil <= now
  useEffect(() => {
    let disposed = false
    let timer:number | undefined
    current.current = !expired; generation.current++
    setView(null); setError(false); setDownloading(false); setDownloaded(false)
    const load = async () => {
      try {
        const result = await sharedCareFileRestService.list(referral, consultationId, page)
        if (disposed || !current.current) return
        liveExpiry.current = Math.min(Date.parse(referral.accessExpiresAt), Date.parse(result.validUntil))
        setVerifiedUntil(liveExpiry.current)
        setView(result)
        timer = window.setTimeout(() => void load(), 5_000)
      } catch {
        if (!disposed && current.current) { current.current = false; setView(null); setError(true) }
      }
    }
    if (!expired) void load()
    return () => {
      disposed = true; current.current = false; generation.current++; window.clearTimeout(timer)
      for (const url of urls.current) URL.revokeObjectURL(url)
      urls.current.clear()
    }
    // Parent remounts on identity/referral changes. Payloads/tokens never enter Query or browser storage.
  }, [page, retry, expired])

  const download = async (file:MedicalFileResource) => {
    const started = generation.current
    const stillCurrent = () => current.current && started === generation.current
      && Date.now() < liveExpiry.current && Date.now() < Date.parse(referral.accessExpiresAt)
    setDownloading(true); setDownloaded(false)
    try {
      const blob = await sharedCareFileRestService.download(referral, file, stillCurrent)
      if (!stillCurrent()) return
      const url = URL.createObjectURL(blob); urls.current.add(url)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = file.originalFilename; anchor.rel = 'noopener'; anchor.click()
      window.setTimeout(() => { URL.revokeObjectURL(url); urls.current.delete(url) }, 0)
      setDownloaded(true)
    } catch {
      if (stillCurrent()) { current.current = false; setView(null); setError(true) }
    } finally { if (stillCurrent()) setDownloading(false) }
  }
  const changePage = (next:number) => { current.current = false; setView(null); setPage(next) }
  return <article className="workflow-card">
    <button type="button" className="secondary" onClick={close}>Close encounter documents</button>
    {expired ? <WorkflowNotice error message="Shared-care document access has expired. The document list has been cleared."/>
      : error ? <><WorkflowNotice error message="Shared-care documents could not be verified. The document list has been cleared."/>
        <button type="button" className="secondary" onClick={() => setRetry(value => value + 1)}>Retry encounter documents</button></>
        : !view ? <p role="status">Verifying document access…</p> : <>
          {!view.content.length && <p>No clean documents are available for this encounter.</p>}
          <ul>{view.content.map(file => <li key={file.fileId}>
            <span className="referral-text">{file.originalFilename} · {file.contentType} · {file.size} bytes</span>{' '}
            <button type="button" className="secondary" disabled={downloading} onClick={() => void download(file)}>Download document {file.originalFilename}</button>
          </li>)}</ul>
          <Pagination page={page} pages={view.totalPages} change={changePage}/>
          {downloading && <p role="status">Authorising document download…</p>}
          {downloaded && <p role="status">The authorised document download has started.</p>}
        </>}
    <p>Access is rechecked before returning bytes. Copies already downloaded cannot be recalled by revoking sharing.</p>
  </article>
}
