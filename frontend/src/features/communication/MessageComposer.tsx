import {useEffect,useRef,useState} from 'react'
import {LoaderCircle,Paperclip,Send} from 'lucide-react'
import {WorkflowNotice} from '../../components/workflow/WorkflowUI'
import type {SendConversationMessageCommand} from '../../models/communication'
import type {MessageAttachmentResource,MessageAttachmentUploadCommand} from '../../models/messageAttachment'
import {communicationRestService} from '../../services/api/communicationRestService'
import {messageAttachmentRestService} from '../../services/api/messageAttachmentRestService'
import {apiErrorMessage} from '../../services/api/ApiError'

interface Upload {
  id:string; file:File; command?:MessageAttachmentUploadCommand
  resource?:MessageAttachmentResource; busy:boolean; error?:string
}
export function MessageComposer({conversationId,peerName,onSent}:{
  conversationId:string;peerName:string;onSent:(conversationId:string)=>void
}) {
  const [draft,setDraft]=useState('')
  const [requestId,setRequestId]=useState(()=>crypto.randomUUID())
  const [uploads,setUploads]=useState<Upload[]>([])
  const [sending,setSending]=useState(false)
  const [error,setError]=useState('')
  const pending=useRef<SendConversationMessageCommand|null>(null)
  const input=useRef<HTMLInputElement>(null)
  const live=useRef(false)
  const active=useRef(new Set<string>())
  const abort=useRef<AbortController|null>(null)
  useEffect(()=>{
    live.current=true; abort.current=new AbortController()
    return ()=>{live.current=false;active.current.clear();abort.current?.abort()}
  },[])
  const current=(id:string)=>live.current&&active.current.has(id)
  const update=(id:string,change:Partial<Upload>)=>{
    if(current(id))setUploads(old=>old.map(value=>value.id===id?{...value,...change}:value))
  }
  const upload=async(entry:Upload)=>{
    update(entry.id,{busy:true,error:undefined})
    try {
      let command=entry.command
      if(!command) {
        const digest=await crypto.subtle.digest('SHA-256',await entry.file.arrayBuffer())
        if(!current(entry.id))return
        command={uploadRequestId:entry.id,conversationId,messageRequestId:requestId,
          originalFilename:entry.file.name,contentType:entry.file.type,declaredSize:entry.file.size,
          checksumSha256:Array.from(new Uint8Array(digest),value=>value.toString(16).padStart(2,'0')).join('')}
        update(entry.id,{command})
      }
      const ticket=await messageAttachmentRestService.negotiate(command,abort.current?.signal)
      if(!current(entry.id))return
      const resource=ticket.file.uploadStatus==='STORED'?ticket.file
        :await messageAttachmentRestService.upload(ticket,entry.file,abort.current?.signal)
      update(entry.id,{resource,busy:false})
    } catch(failure) {update(entry.id,{busy:false,error:apiErrorMessage(failure,'Upload could not finish. Retry preserves this upload request.')})}
  }
  const refresh=async(entry:Upload)=>{
    if(!entry.resource || !current(entry.id))return
    try {
      const resource=await messageAttachmentRestService.metadata(entry.resource.fileId,abort.current?.signal)
      if(resource.fileId!==entry.resource.fileId)throw new Error('Attachment changed')
      update(entry.id,{resource,error:undefined})
    } catch(failure) {update(entry.id,{error:apiErrorMessage(failure,'Attachment access could not be verified.')})}
  }
  useEffect(()=>{
    const waiting=uploads.filter(value=>value.resource?.uploadStatus==='STORED'&&value.resource.scanStatus==='PENDING'&&!value.error)
    if(!waiting.length)return
    const timer=window.setInterval(()=>{for(const entry of waiting)void refresh(entry)},5000)
    return ()=>window.clearInterval(timer)
  // Every continuation additionally checks the live composer and its exact upload.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[uploads])
  const select=(files:File[])=>{
    if(pending.current || sending)return
    if(uploads.length+files.length>5){setError('Attach at most five files to one message.');return}
    if(files.some(file=>file.size<=0||file.size>10*1024*1024
      || !['application/pdf','image/png','image/jpeg'].includes(file.type))){
      setError('Choose PDF, PNG or JPEG files, each no larger than 10 MB.');return
    }
    setError('')
    const added=files.map(file=>({id:crypto.randomUUID(),file,busy:true}))
    for(const entry of added)active.current.add(entry.id)
    setUploads(old=>[...old,...added])
    for(const entry of added)void upload(entry)
  }
  const ready=uploads.every(value=>!value.busy&&!value.error&&value.resource?.uploadStatus==='STORED'&&value.resource.scanStatus==='CLEAN')
  const send=async()=>{
    if(!draft.trim()||sending||!ready)return
    if(!pending.current)pending.current={messageRequestId:requestId,body:draft.trim(),
      ...(uploads.length?{attachmentIds:uploads.map(value=>value.resource!.fileId)}:{})}
    setSending(true);setError('')
    try {
      await communicationRestService.send(conversationId,pending.current)
      if(!live.current)return
      setDraft('');setUploads([]);active.current.clear();pending.current=null;setRequestId(crypto.randomUUID())
      onSent(conversationId)
    } catch(failure) {
      if(live.current)setError(apiErrorMessage(failure,'Sending was not confirmed. Retry sends the exact same message and attachments.'))
    } finally {if(live.current)setSending(false)}
  }
  return <>
    {error&&<WorkflowNotice error message={error}/>}
    {uploads.length>0&&<div className="message-attachment-drafts" aria-label="Message attachments">
      <p>New uploads for this message only. Sending waits for a clean security scan.</p>
      {uploads.map(entry=><div key={entry.id} className="message-attachment-draft">
        <span><strong>{entry.file.name}</strong><small>{entry.error || (entry.busy?'Uploading…'
          :entry.resource?.scanStatus==='CLEAN'?'Ready to send'
          :entry.resource?.scanStatus==='REJECTED'?'Rejected by security scan'
          :'Waiting for security scan')}</small></span>
        {!pending.current&&<>
          {entry.error&&<button type="button" className="text-button" onClick={()=>void upload(entry)}>Retry upload</button>}
          {entry.resource?.scanStatus==='PENDING'&&!entry.busy&&<button type="button" className="text-button" onClick={()=>void refresh(entry)}>Check scan status</button>}
          <button type="button" className="text-button" aria-label={'Remove '+entry.file.name} onClick={()=>{
            active.current.delete(entry.id);setUploads(old=>old.filter(value=>value.id!==entry.id))
          }}>Remove</button>
        </>}
      </div>)}
    </div>}
    {pending.current&&!sending&&<p className="message-attachment-hint" role="status">Retry keeps the same message and attachments. The draft stays locked until sending is confirmed.</p>}
    <form onSubmit={event=>{event.preventDefault();void send()}}>
      <input ref={input} type="file" multiple hidden aria-label="Attachment files" accept="application/pdf,image/png,image/jpeg"
        onChange={event=>{const files=Array.from(event.currentTarget.files||[]);event.currentTarget.value='';select(files)}}/>
      <button type="button" className="icon-button" aria-label="Attach files" title="Attach new PDF, PNG or JPEG files"
        disabled={sending||Boolean(pending.current)||uploads.length>=5} onClick={()=>input.current?.click()}><Paperclip/></button>
      <label><span className="sr-only">Message</span><textarea rows={2} value={draft} disabled={sending||Boolean(pending.current)}
        onChange={event=>setDraft(event.target.value)} maxLength={4000} placeholder={'Message '+peerName}/></label>
      <button className="send-button" disabled={!draft.trim()||sending||!ready} aria-label="Send message">
        {sending?<LoaderCircle className="spin"/>:<Send/>}
      </button>
    </form>
  </>
}
