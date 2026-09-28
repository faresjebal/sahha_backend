import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react'
import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {MessageComposer} from './MessageComposer'
import {communicationRestService} from '../../services/api/communicationRestService'
import {messageAttachmentRestService as attachments} from '../../services/api/messageAttachmentRestService'
import type {MessageAttachmentTicket} from '../../models/messageAttachment'

const resource={fileId:'file-1',originalFilename:'synthetic.pdf',contentType:'application/pdf',size:14,
  uploadStatus:'STORED' as const,scanStatus:'PENDING' as const}
const ticket:MessageAttachmentTicket={file:{...resource,uploadStatus:'NEGOTIATED'},uploadPath:'/api/v1/files/message-attachments/file-1/content',
  uploadToken:'x'.repeat(43),expiresAt:new Date(Date.now()+60000).toISOString()}
const hashing=vi.fn()
const randomUUID=crypto.randomUUID.bind(crypto)
const onSent=vi.fn()
const choose=()=>{
  const file=new File(['%PDF-1.4\n%%EOF'],'synthetic.pdf',{type:'application/pdf'})
  Object.defineProperty(file,'arrayBuffer',{value:async()=>new Uint8Array([37,80,68,70]).buffer})
  fireEvent.change(screen.getByLabelText('Attachment files'),{target:{files:[file]}})
}
const show=()=>render(<MessageComposer conversationId="thread" peerName="Colleague" onSent={onSent}/>)
const write=()=>fireEvent.change(screen.getByLabelText('Message'),{target:{value:'Synthetic message'}})
beforeEach(()=>{
  onSent.mockReset();hashing.mockReset().mockResolvedValue(new Uint8Array(32).buffer)
  vi.stubGlobal('crypto',{randomUUID,subtle:{digest:hashing}})
  vi.spyOn(attachments,'negotiate').mockResolvedValue(ticket)
  vi.spyOn(attachments,'upload').mockResolvedValue(resource)
  vi.spyOn(attachments,'metadata').mockResolvedValue({...resource,scanStatus:'CLEAN'})
  vi.spyOn(communicationRestService,'send').mockResolvedValue({id:'message',conversationId:'thread',senderUserId:'doctor',
    senderDisplayName:'Synthetic',body:'Synthetic message',sentAt:new Date().toISOString(),attachments:[]})
})
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals()})

it('waits for a clean scan and sends the exact upload-bound message request',async()=>{
  show();write();choose();await screen.findByText('Waiting for security scan')
  expect(screen.getByRole('button',{name:'Send message'})).toBeDisabled()
  fireEvent.click(screen.getByRole('button',{name:'Check scan status'}));await screen.findByText('Ready to send')
  fireEvent.click(screen.getByRole('button',{name:'Send message'}))
  await waitFor(()=>expect(onSent).toHaveBeenCalledWith('thread'))
  expect(communicationRestService.send).toHaveBeenCalledWith('thread',{body:'Synthetic message',attachmentIds:['file-1'],
    messageRequestId:vi.mocked(attachments.negotiate).mock.calls[0][0].messageRequestId})
  expect(screen.getByLabelText('Message')).toHaveValue('')
})
it('recovers a lost upload response without uploading the bytes twice',async()=>{
  vi.mocked(attachments.upload).mockRejectedValueOnce(new Error('Response lost'))
  vi.mocked(attachments.negotiate).mockResolvedValueOnce(ticket).mockResolvedValueOnce({file:resource,uploadPath:null,uploadToken:null,expiresAt:null})
  show();choose();await screen.findByRole('button',{name:'Retry upload'})
  fireEvent.click(screen.getByRole('button',{name:'Retry upload'}));await screen.findByText('Waiting for security scan')
  expect(attachments.upload).toHaveBeenCalledTimes(1)
  expect(vi.mocked(attachments.negotiate).mock.calls[1][0]).toEqual(vi.mocked(attachments.negotiate).mock.calls[0][0])
})
it('freezes an uncertain send and retries the identical command',async()=>{
  vi.mocked(communicationRestService.send).mockRejectedValueOnce(new Error('Response lost'))
  show();write();fireEvent.click(screen.getByRole('button',{name:'Send message'}))
  await screen.findByRole('alert');expect(screen.getByLabelText('Message')).toBeDisabled()
  fireEvent.click(screen.getByRole('button',{name:'Send message'}))
  await waitFor(()=>expect(onSent).toHaveBeenCalledTimes(1))
  const calls=vi.mocked(communicationRestService.send).mock.calls
  expect(calls[1]).toEqual(calls[0])
})
it('does not advance a late upload after the composer unmounts',async()=>{
  let resolve!:(value:MessageAttachmentTicket)=>void
  vi.mocked(attachments.negotiate).mockReturnValue(new Promise(done=>{resolve=done}))
  const view=show();choose();await waitFor(()=>expect(attachments.negotiate).toHaveBeenCalledTimes(1))
  view.unmount();await act(async()=>{resolve(ticket)})
  expect(attachments.upload).not.toHaveBeenCalled()
})
it('removing a pending attachment prevents later upload continuation',async()=>{
  let resolve!:(value:MessageAttachmentTicket)=>void
  vi.mocked(attachments.negotiate).mockReturnValue(new Promise(done=>{resolve=done}))
  show();choose();await waitFor(()=>expect(attachments.negotiate).toHaveBeenCalledTimes(1))
  fireEvent.click(screen.getByRole('button',{name:'Remove synthetic.pdf'}))
  await act(async()=>{resolve(ticket)})
  expect(attachments.upload).not.toHaveBeenCalled();expect(screen.queryByText('synthetic.pdf')).not.toBeInTheDocument()
})
it('rejects unsupported files and more than five selections before network calls',async()=>{
  show()
  fireEvent.change(screen.getByLabelText('Attachment files'),{target:{files:[new File(['x'],'unsafe.svg',{type:'image/svg+xml'})]}})
  await screen.findByRole('alert');expect(attachments.negotiate).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('Attachment files'),{target:{files:Array.from({length:6},()=>new File(['x'],'synthetic.pdf',{type:'application/pdf'}))}})
  expect(screen.getByRole('alert')).toHaveTextContent('at most five');expect(attachments.negotiate).not.toHaveBeenCalled()
})
it('does not allow rejected scans to send',async()=>{
  vi.mocked(attachments.upload).mockResolvedValue({...resource,scanStatus:'REJECTED'})
  show();write();choose();await screen.findByText('Rejected by security scan')
  expect(screen.getByRole('button',{name:'Send message'})).toBeDisabled()
  expect(communicationRestService.send).not.toHaveBeenCalled()
})
