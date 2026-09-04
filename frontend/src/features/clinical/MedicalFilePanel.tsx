import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Check,
  Download,
  FileHeart,
  LoaderCircle,
  RefreshCw,
  ShieldCheck,
  Upload,
} from 'lucide-react'
import { useRef, useState } from 'react'
import { useAuth } from '../../app/auth/AuthProvider'
import type { MedicalFileResource } from '../../models/medicalFile'
import { apiErrorMessage } from '../../services/api/ApiError'
import { medicalFileRestService } from '../../services/api/medicalFileRestService'

const fileSize = (bytes:number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const scanLabel = (file:MedicalFileResource) => {
  if (file.uploadStatus !== 'STORED') return 'Upload unavailable'
  if (file.scanStatus === 'CLEAN') return 'Available'
  if (file.scanStatus === 'REJECTED') return 'Rejected'
  return 'Awaiting scan'
}

export function MedicalFilePanel({ consultationId }:{ consultationId:string }) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'none'
  const queryKey = ['medical-files', organisationId, consultationId] as const
  const inputRef = useRef<HTMLInputElement>(null)
  const [selected, setSelected] = useState<File | null>(null)
  const [notice, setNotice] = useState('')

  const filesQuery = useQuery({
    queryKey,
    queryFn:() => medicalFileRestService.list(consultationId),
    enabled:organisationId !== 'none',
  })

  const uploadMutation = useMutation({
    mutationFn:async (file:File) => {
      const ticket = await medicalFileRestService.negotiateUpload({
        consultationId,
        originalFilename:file.name,
        contentType:file.type,
        declaredSize:file.size,
      })
      return medicalFileRestService.uploadContent(ticket, file)
    },
    onSuccess:() => {
      setSelected(null)
      if (inputRef.current) inputRef.current.value = ''
      setNotice('The file is stored privately and is awaiting its scan decision.')
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const scanMutation = useMutation({
    mutationFn:(fileId:string) =>
      medicalFileRestService.applySyntheticScan(fileId, 'CLEAN'),
    onSuccess:() => {
      setNotice('The local synthetic scan marked the file clean.')
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const downloadMutation = useMutation({
    mutationFn:async (file:MedicalFileResource) => {
      const grant = await medicalFileRestService.issueDownloadGrant(file.fileId)
      const blob = await medicalFileRestService.download(grant)
      return { blob, filename:file.originalFilename }
    },
    onSuccess:({ blob, filename }) => {
      const objectUrl = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = objectUrl
      anchor.download = filename
      anchor.rel = 'noopener'
      anchor.click()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
      setNotice('The one-time private download was authorised.')
    },
  })

  const error = uploadMutation.error || scanMutation.error ||
    downloadMutation.error

  return <section className="clinical-files" aria-labelledby="clinical-files-title">
    <header>
      <div><p className="eyebrow">Private object storage</p><h2 id="clinical-files-title">Medical documents</h2></div>
      <ShieldCheck/>
    </header>
    <p className="clinical-section-copy">Files travel through API Gateway and File Service. Sahha never exposes a permanent SeaweedFS URL.</p>

    <div className="clinical-upload">
      <label>
        <span>PDF or clinical image · maximum 10 MB</span>
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf,image/jpeg,image/png"
          onChange={event => {
            setNotice('')
            uploadMutation.reset()
            setSelected(event.target.files?.[0] || null)
          }}
        />
      </label>
      <button
        type="button"
        className="secondary"
        disabled={!selected || uploadMutation.isPending}
        onClick={() => selected && uploadMutation.mutate(selected)}
      >{uploadMutation.isPending
          ? <><LoaderCircle className="spin"/>Uploading…</>
          : <><Upload/>Upload securely</>}</button>
    </div>
    {selected&&<small className="clinical-selected-file">Selected: {selected.name} · {fileSize(selected.size)}</small>}
    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}
    {error&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(error, 'The file operation could not be completed.')}</p>}

    {filesQuery.isPending&&<div className="clinical-inline-state"><LoaderCircle className="spin"/>Loading protected files…</div>}
    {filesQuery.isError&&<div className="clinical-inline-state clinical-inline-state--error"><span>{apiErrorMessage(filesQuery.error, 'Medical files could not be loaded.')}</span><button type="button" className="secondary" onClick={()=>void filesQuery.refetch()}><RefreshCw/>Retry</button></div>}
    {filesQuery.data&&<div className="clinical-file-list">
      {filesQuery.data.map(file => <article key={file.fileId}>
        <FileHeart/>
        <span><strong>{file.originalFilename}</strong><small>{fileSize(file.size)} · {file.contentType}</small></span>
        <i className={`clinical-file-state clinical-file-state--${file.scanStatus.toLowerCase()}`}>{scanLabel(file)}</i>
        <div>
          {import.meta.env.DEV&&file.uploadStatus==='STORED'&&file.scanStatus==='PENDING'&&<button
            type="button"
            className="text-button"
            disabled={scanMutation.isPending}
            onClick={()=>scanMutation.mutate(file.fileId)}
          >Local clean scan</button>}
          <button
            type="button"
            className="secondary"
            disabled={!file.downloadAvailable || downloadMutation.isPending}
            onClick={()=>downloadMutation.mutate(file)}
          ><Download/>Download</button>
        </div>
      </article>)}
      {!filesQuery.data.length&&<div className="clinical-inline-state"><FileHeart/><span>No medical documents are attached yet.</span></div>}
    </div>}
    {import.meta.env.DEV&&<p className="clinical-development-note">The “Local clean scan” control exists only in the Vite development build. It exercises the explicit local backend hook and is not a malware scanner.</p>}
  </section>
}
