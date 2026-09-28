import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { ConversationResource } from '../../models/communication'
import { communicationRestService } from '../../services/api/communicationRestService'
import { consultationRestService } from '../../services/api/consultationRestService'
import { DoctorMessengerPage } from './DoctorMessengerPage'

const ids={sender:'00000000-0000-4000-8000-000000000001',recipient:'00000000-0000-4000-8000-000000000002',
  patient:'00000000-0000-4000-8000-000000000003',source:'00000000-0000-4000-8000-000000000004'}
const auth=vi.hoisted(()=>({session:{user:{id:'',organizationId:'org-a'}}}))
const streams=vi.hoisted(()=>({callbacks:[] as Array<{onConnected:()=>void}>}))
vi.mock('../../app/auth/AuthProvider',()=>({useAuth:()=>auth}))
vi.mock('../../services/realtime/communicationRealtimeClient',()=>({CommunicationRealtimeClient:class {
  constructor(callbacks:{onConnected:()=>void}){streams.callbacks.push(callbacks)} start(){} stop(){}
}}))
const sourcePage={content:[{consultationId:ids.source,patientRegistrationId:ids.patient,appointmentId:'appointment',
  finalizedAt:'2026-09-18T10:00:00Z',version:1}],page:0,size:20,totalElements:1,totalPages:1}
const result:ConversationResource={id:'thread',organisationId:'org-a',subject:'Synthetic handoff',patientRegistrationId:ids.patient,
  patientAccessGranted:false,createdByUserId:ids.sender,createdAt:'2026-09-18T10:00:00Z',lastMessageAt:'2026-09-18T10:00:00Z',
  version:0,unreadCount:0,participants:[]}
const clients:QueryClient[]=[]
function show() {
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});clients.push(client)
  const view=()=> <QueryClientProvider client={client}><MemoryRouter><DoctorMessengerPage/></MemoryRouter></QueryClientProvider>
  const rendered=render(view());return {client,refresh:()=>rendered.rerender(view())}
}
async function open() {
  fireEvent.click(screen.getByRole('button',{name:'New conversation'}))
  await screen.findByRole('option',{name:'Synthetic colleague'})
  fireEvent.change(screen.getByLabelText('Clinical colleague'),{target:{value:ids.recipient}})
  fireEvent.change(screen.getByLabelText('Conversation subject'),{target:{value:'Synthetic handoff'}})
}
async function selectSource() {
  fireEvent.change(screen.getByLabelText('Patient context'),{target:{value:'finalised'}})
  await screen.findByRole('option',{name:new RegExp(ids.patient)})
  fireEvent.change(screen.getByLabelText('Finalised consultation'),{target:{value:ids.source}})
}
const submit=()=>fireEvent.click(screen.getByRole('button',{name:'Create conversation'}))
beforeEach(()=>{
  streams.callbacks=[]
  auth.session.user.id=ids.sender;auth.session.user.organizationId='org-a'
  vi.spyOn(communicationRestService,'conversations').mockResolvedValue({content:[],page:0,size:50,totalElements:0,totalPages:0})
  vi.spyOn(communicationRestService,'doctors').mockResolvedValue({content:[{userId:ids.recipient,organisationId:'org-a',
    membershipId:'membership',membershipVersion:0,displayName:'Synthetic colleague'}],page:0,size:100,totalElements:1,totalPages:1})
  vi.spyOn(communicationRestService,'create').mockResolvedValue(result)
  vi.spyOn(consultationRestService,'listReferralSources').mockResolvedValue(sourcePage)
  vi.spyOn(consultationRestService,'getRecord')
})
afterEach(()=>{cleanup();for(const client of clients.splice(0))client.clear();vi.restoreAllMocks()})

it('recovers conversation and current message history when the real stream reconnects',async()=>{
  vi.mocked(communicationRestService.conversations).mockResolvedValue({content:[result],page:0,size:50,totalElements:1,totalPages:1})
  const history=vi.spyOn(communicationRestService,'messages').mockResolvedValue({content:[],page:0,size:50,totalElements:0,totalPages:0})
  show();await waitFor(()=>expect(history).toHaveBeenCalledWith('thread'))
  const beforeHistory=history.mock.calls.length,beforeList=vi.mocked(communicationRestService.conversations).mock.calls.length
  act(()=>streams.callbacks.at(-1)!.onConnected())
  await waitFor(()=>expect(history.mock.calls.length).toBeGreaterThan(beforeHistory))
  expect(vi.mocked(communicationRestService.conversations).mock.calls.length).toBeGreaterThan(beforeList)
})

it('keeps unlinked conversations independent of Clinical and does not preselect a patient',async()=>{
  show();await open();expect(consultationRestService.listReferralSources).not.toHaveBeenCalled()
  expect(screen.getByLabelText(/Patient registration UUID/)).toHaveValue('')
  submit();await waitFor(()=>expect(communicationRestService.create).toHaveBeenCalledTimes(1))
  expect(communicationRestService.create).toHaveBeenCalledWith({conversationRequestId:expect.any(String),recipientUserId:ids.recipient,subject:'Synthetic handoff'})
})
it('retains the existing active-care patient-reference contract',async()=>{
  show();await open();fireEvent.change(screen.getByLabelText(/Patient registration UUID/),{target:{value:ids.patient}})
  submit();await waitFor(()=>expect(communicationRestService.create).toHaveBeenCalledTimes(1))
  expect(communicationRestService.create).toHaveBeenCalledWith({conversationRequestId:expect.any(String),recipientUserId:ids.recipient,
    subject:'Synthetic handoff',patientRegistrationId:ids.patient})
  expect(consultationRestService.listReferralSources).not.toHaveBeenCalled()
})
it('uses minimal finalised-source metadata and revalidates it without loading clinical content',async()=>{
  show();await open();await selectSource()
  expect(screen.getByLabelText(/Patient registration UUID/)).toHaveValue(ids.patient)
  expect(screen.getByLabelText(/Patient registration UUID/)).toHaveAttribute('readonly')
  submit();await waitFor(()=>expect(communicationRestService.create).toHaveBeenCalledTimes(1))
  expect(communicationRestService.create).toHaveBeenCalledWith({conversationRequestId:expect.any(String),recipientUserId:ids.recipient,
    subject:'Synthetic handoff',patientRegistrationId:ids.patient,sourceConsultationId:ids.source})
  expect(consultationRestService.listReferralSources).toHaveBeenCalledTimes(2)
  expect(consultationRestService.getRecord).not.toHaveBeenCalled()
})
it.each(['removed','changed-patient','unavailable'])('refuses a %s source without falling back to a source-less request',async condition=>{
  show();await open();await selectSource()
  if(condition==='removed')vi.mocked(consultationRestService.listReferralSources).mockResolvedValue({...sourcePage,content:[]})
  if(condition==='changed-patient')vi.mocked(consultationRestService.listReferralSources).mockResolvedValue({...sourcePage,content:[{...sourcePage.content[0],patientRegistrationId:ids.recipient}]})
  if(condition==='unavailable')vi.mocked(consultationRestService.listReferralSources).mockRejectedValue(new Error('Source authority unavailable'))
  submit();await screen.findByRole('alert');expect(communicationRestService.create).not.toHaveBeenCalled()
})
it('reuses the exact command after a lost creation response',async()=>{
  vi.mocked(communicationRestService.create).mockRejectedValueOnce(new Error('Response lost')).mockResolvedValueOnce(result)
  show();await open();await selectSource();submit();await screen.findByRole('alert');submit()
  await waitFor(()=>expect(communicationRestService.create).toHaveBeenCalledTimes(2))
  expect(vi.mocked(communicationRestService.create).mock.calls[1][0]).toEqual(vi.mocked(communicationRestService.create).mock.calls[0][0])
})
it('clears patient/source references when returning to the active-care mode',async()=>{
  show();await open();await selectSource()
  fireEvent.change(screen.getByLabelText('Patient context'),{target:{value:'care'}})
  expect(screen.getByLabelText(/Patient registration UUID/)).toHaveValue('');submit()
  await waitFor(()=>expect(communicationRestService.create).toHaveBeenCalledTimes(1))
  expect(vi.mocked(communicationRestService.create).mock.calls[0][0]).not.toHaveProperty('sourceConsultationId')
})
it('discards the patient/source and unsent draft on organisation change',async()=>{
  const view=show();await open();await selectSource();auth.session.user.organizationId='org-b';view.refresh()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button',{name:'New conversation'}))
  expect(screen.getByLabelText('Patient context')).toHaveValue('care')
  expect(screen.getByLabelText(/Patient registration UUID/)).toHaveValue('')
  expect(screen.getByLabelText('Conversation subject')).toHaveValue('')
  expect(communicationRestService.create).not.toHaveBeenCalled()
})
