import {useEffect,useRef,useState} from 'react'
import {Paperclip} from 'lucide-react'
import type {MessageAttachmentReference} from '../../models/messageAttachment'
import {messageAttachmentRestService} from '../../services/api/messageAttachmentRestService'
import {apiErrorMessage} from '../../services/api/ApiError'

export function MessageAttachmentDownloads({attachments}:{attachments:MessageAttachmentReference[]}) {
  const [busy,setBusy]=useState('')
  const [error,setError]=useState('')
  const live=useRef(false)
  const urls=useRef(new Set<string>())
  const abort=useRef<AbortController|null>(null)
  useEffect(()=>{
    live.current=true;abort.current=new AbortController()
    return ()=>{live.current=false;abort.current?.abort();for(const url of urls.current)URL.revokeObjectURL(url);urls.current.clear()}
  },[])
  const download=async(file:MessageAttachmentReference)=>{
    if(busy)return
    setBusy(file.fileId);setError('')
    try {
      const grant=await messageAttachmentRestService.grant(file.fileId,abort.current?.signal)
      if(!live.current)return
      if(grant.fileId!==file.fileId)throw new Error('Unexpected attachment')
      const blob=await messageAttachmentRestService.download(grant,abort.current?.signal)
      if(!live.current)return
      const url=URL.createObjectURL(blob);urls.current.add(url)
      const link=document.createElement('a');link.href=url;link.download=file.originalFilename
      document.body.appendChild(link);link.click();link.remove()
      window.setTimeout(()=>{if(urls.current.delete(url))URL.revokeObjectURL(url)},1000)
    } catch(failure) {if(live.current)setError(apiErrorMessage(failure,'Attachment access could not be verified. Try again when access is available.'))}
    finally {if(live.current)setBusy('')}
  }
  if(!attachments.length)return null
  return <div className="message-attachment-links">
    {attachments.map(file=><button key={file.fileId} type="button" className="attachment-download" disabled={Boolean(busy)}
      onClick={()=>void download(file)}><Paperclip size={14}/>{busy===file.fileId?'Downloading…':file.originalFilename}
      <small>{Math.max(1,Math.ceil(file.size/1024))} KB</small></button>)}
    {error&&<small role="alert">{error}</small>}
  </div>
}
