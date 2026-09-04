import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  LoaderCircle, MessageSquarePlus, Paperclip, RefreshCw, Search, Send,
  ShieldCheck,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import {
  WorkflowDrawer, WorkflowEmpty, WorkflowFormActions, WorkflowNotice,
  WorkflowPageHeader, formatDateTime,
} from '../../components/workflow/WorkflowUI'
import type { ConversationPageResource } from '../../models/communication'
import { apiErrorMessage } from '../../services/api/ApiError'
import { communicationRestService } from '../../services/api/communicationRestService'
import { CommunicationRealtimeClient } from '../../services/realtime/communicationRealtimeClient'

const optionalUuid = z.string().trim().refine(
  value => value === '' || /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value),
  'Use a valid patient registration UUID or leave this blank.',
)
const conversationSchema = z.object({
  recipientUserId:z.string().uuid('Choose an active doctor.'),
  subject:z.string().trim().min(4, 'Use at least 4 characters.').max(160),
  patientRegistrationId:optionalUuid,
})
type ConversationValues = z.infer<typeof conversationSchema>

const listKey = (organisationId:string) => ['conversations', organisationId] as const

export function DoctorMessengerPage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || ''
  const userId = auth.session?.user.id || ''
  const [activeId, setActiveId] = useState('')
  const [query, setQuery] = useState('')
  const [draft, setDraft] = useState('')
  const [open, setOpen] = useState(false)

  const conversationsQuery = useQuery({
    queryKey:listKey(organisationId),
    queryFn:() => communicationRestService.conversations(),
    enabled:Boolean(organisationId),
  })
  const doctorsQuery = useQuery({
    queryKey:['collaboration-doctors', organisationId],
    queryFn:() => communicationRestService.doctors(organisationId),
    enabled:Boolean(organisationId && open),
  })
  const conversations = conversationsQuery.data?.content || []
  const current = conversations.find(value => value.id === activeId) || conversations[0]
  const visible = useMemo(() => conversations.filter(value =>
    value.subject.toLowerCase().includes(query.trim().toLowerCase())), [conversations, query])

  useEffect(() => {
    if (!activeId && conversations[0]) setActiveId(conversations[0].id)
  }, [activeId, conversations])

  const messagesQuery = useQuery({
    queryKey:['conversation-messages', organisationId, current?.id],
    queryFn:() => communicationRestService.messages(current!.id),
    enabled:Boolean(organisationId && current?.id),
  })
  useEffect(() => {
    if (!organisationId) return
    const realtime = new CommunicationRealtimeClient({
      onMessage: message => {
        if (message.conversationId === current?.id) {
          void queryClient.invalidateQueries({ queryKey:['conversation-messages', organisationId, message.conversationId] })
        }
        void queryClient.invalidateQueries({ queryKey:listKey(organisationId) })
      },
      onConnected:() => undefined,
      onStateChange:() => undefined,
    })
    realtime.start()
    return () => realtime.stop()
  }, [organisationId, current?.id, queryClient])
  const markRead = useMutation({
    mutationFn:(conversationId:string) => communicationRestService.markRead(conversationId),
    onSuccess:updated => queryClient.setQueryData<ConversationPageResource>(
      listKey(organisationId), old => old ? {
        ...old,
        content:old.content.map(value => value.id === updated.id ? updated : value),
      } : old,
    ),
  })
  useEffect(() => {
    if (current?.unreadCount && !markRead.isPending) markRead.mutate(current.id)
  // Mutation identity is intentionally excluded; the server operation is idempotent.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current?.id, current?.unreadCount])

  const createMutation = useMutation({
    mutationFn:(values:ConversationValues) => communicationRestService.create({
      conversationRequestId:crypto.randomUUID(),
      recipientUserId:values.recipientUserId,
      subject:values.subject.trim(),
      ...(values.patientRegistrationId
        ? { patientRegistrationId:values.patientRegistrationId }
        : {}),
    }),
    onSuccess:created => {
      void queryClient.invalidateQueries({ queryKey:listKey(organisationId) })
      setActiveId(created.id)
      setOpen(false)
      form.reset()
    },
  })
  const sendMutation = useMutation({
    mutationFn:(body:string) => communicationRestService.send(current!.id, {
      messageRequestId:crypto.randomUUID(), body,
    }),
    onSuccess:() => {
      setDraft('')
      void queryClient.invalidateQueries({
        queryKey:['conversation-messages', organisationId, current?.id],
      })
      void queryClient.invalidateQueries({ queryKey:listKey(organisationId) })
    },
  })
  const form = useForm<ConversationValues>({
    resolver:zodResolver(conversationSchema),
    defaultValues:{ recipientUserId:'', subject:'', patientRegistrationId:'' },
  })
  const eligibleDoctors = doctorsQuery.data?.content.filter(value => value.userId !== userId) || []
  const messages = [...(messagesQuery.data?.content || [])].reverse()
  const peer = current?.participants.find(value => value.userId !== userId)
  const error = conversationsQuery.error || messagesQuery.error || createMutation.error
    || sendMutation.error || markRead.error

  return <div className="page workflow-page">
    <WorkflowPageHeader
      eyebrow="Communication Service · Active organisation"
      title="Private clinical conversations."
      copy="Only the selected doctors can open a thread. A patient reference supplies context, but never grants access to the medical record."
      actions={<button className="primary" onClick={()=>setOpen(true)}>
        <MessageSquarePlus/>New conversation
      </button>}
    />
    {error&&<WorkflowNotice error message={apiErrorMessage(error, 'The conversation could not be completed.')}/>}
    <div className="connected-messenger">
      <aside>
        <label className="search-field"><Search/><span className="sr-only">Search conversations</span>
          <input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Search conversations"/>
        </label>
        <div className="messenger-boundary"><ShieldCheck/><span><strong>Participant-only channel</strong>
          <small>Patient context is an opaque reference. Sharing is a separate, audited workflow.</small></span>
        </div>
        {conversationsQuery.isPending&&<div className="communication-inline-state"><LoaderCircle className="spin"/>Loading conversations…</div>}
        {conversationsQuery.isError&&<div className="communication-inline-state"><button className="secondary" onClick={()=>void conversationsQuery.refetch()}><RefreshCw/>Retry</button></div>}
        {visible.map(item => <button className={current?.id === item.id ? 'active' : ''}
          onClick={()=>setActiveId(item.id)} key={item.id}>
          <span className="conversation-mark">DR</span><span><strong>{item.subject}</strong>
            <small>{item.participants.find(value=>value.userId!==userId)?.displayName || 'Doctor'} · {item.unreadCount ? `${item.unreadCount} unread` : 'Up to date'}</small>
          </span>{item.unreadCount > 0&&<i className="conversation-unread" aria-label={`${item.unreadCount} unread`}>{item.unreadCount}</i>}
        </button>)}
        {!conversationsQuery.isPending&&!visible.length&&<WorkflowEmpty title="No conversations" copy="Start a secure thread with an active doctor."/>}
      </aside>
      {current?<section>
        <header><span className="conversation-mark">DR</span><span><h2>{current.subject}</h2>
          <p>{peer?.displayName || 'Clinical colleague'}{current.patientRegistrationId ? ` · Patient reference ${current.patientRegistrationId.slice(0,8)}` : ' · Direct conversation'}</p>
        </span><span className="communication-access-boundary"><ShieldCheck/>No shared record access</span></header>
        <div className="connected-message-stream">
          {messagesQuery.isPending&&<div className="communication-inline-state"><LoaderCircle className="spin"/>Loading messages…</div>}
          {messages.map(item=><article className={item.senderUserId===userId?'mine':''} key={item.id}>
            <p>{item.body}</p><span>{item.senderDisplayName} · {formatDateTime(item.sentAt)}</span>
          </article>)}
          {!messagesQuery.isPending&&!messages.length&&<WorkflowEmpty title="Start the conversation" copy="Send the first message in this private thread."/>}
        </div>
        <form onSubmit={event=>{event.preventDefault();if(draft.trim()&&!sendMutation.isPending)sendMutation.mutate(draft.trim())}}>
          <button type="button" className="icon-button" disabled title="Secure attachments are the next Phase 6 slice"><Paperclip/></button>
          <label><span className="sr-only">Message</span><textarea rows={2} value={draft}
            onChange={event=>setDraft(event.target.value)} maxLength={4000} placeholder={`Message ${peer?.displayName || 'doctor'}`}/></label>
          <button className="send-button" disabled={!draft.trim()||sendMutation.isPending} aria-label="Send message">
            {sendMutation.isPending?<LoaderCircle className="spin"/>:<Send/>}
          </button>
        </form>
      </section>:<WorkflowEmpty title="Choose a conversation" copy="Select a participant-scoped thread to read and reply."/>}
    </div>
    <WorkflowDrawer open={open} close={()=>setOpen(false)} title="Start a clinical conversation"
      eyebrow="Authorized collaboration" copy="A patient reference is optional and does not share clinical data.">
      <form className="workflow-form" onSubmit={form.handleSubmit(values=>createMutation.mutate(values))}>
        <div className="workflow-field-grid">
          <label className="wide"><span>Clinical colleague</span><select {...form.register('recipientUserId')} disabled={doctorsQuery.isPending}>
            <option value="">Choose an active doctor</option>{eligibleDoctors.map(doctor=><option value={doctor.userId} key={doctor.userId}>{doctor.displayName}</option>)}
          </select>{form.formState.errors.recipientUserId&&<small className="login-field-error">{form.formState.errors.recipientUserId.message}</small>}</label>
          <label className="wide"><span>Conversation subject</span><input {...form.register('subject')} placeholder="Clinical question or care coordination"/>
            {form.formState.errors.subject&&<small className="login-field-error">{form.formState.errors.subject.message}</small>}</label>
          <label className="wide"><span>Patient registration UUID <em>Optional</em></span><input {...form.register('patientRegistrationId')} placeholder="Only when you already have authorised care access"/>
            {form.formState.errors.patientRegistrationId&&<small className="login-field-error">{form.formState.errors.patientRegistrationId.message}</small>}</label>
          <p className="workflow-boundary wide"><ShieldCheck/>Mentioning a patient validates the sender's existing care relationship. It does not share notes, files, diagnoses, or prescriptions with the recipient.</p>
        </div>
        {doctorsQuery.isError&&<WorkflowNotice error message={apiErrorMessage(doctorsQuery.error, 'Active doctors could not be loaded.')}/>}
        <WorkflowFormActions cancel={()=>setOpen(false)} submitLabel={createMutation.isPending?'Creating…':'Create conversation'}/>
      </form>
    </WorkflowDrawer>
  </div>
}
