import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react'
import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {MessageAttachmentDownloads} from './MessageAttachmentDownloads'
import {messageAttachmentRestService as files} from '../../services/api/messageAttachmentRestService'
const file={fileId:'file-1',originalFilename:'synthetic.pdf',contentType:'application/pdf',size:42}
const grant={fileId:'file-1',grantId:'grant',downloadPath:'/api/v1/files/message-attachments/file-1/content',
  downloadToken:'private-test-token',expiresAt:new Date(Date.now()+60000).toISOString()}
beforeEach(()=>{
  vi.spyOn(files,'grant').mockResolvedValue(grant);vi.spyOn(files,'download').mockResolvedValue(new Blob(['synthetic']))
  vi.spyOn(URL,'createObjectURL').mockReturnValue('blob:synthetic-attachment');vi.spyOn(URL,'revokeObjectURL').mockImplementation(()=>{})
  vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{})
})
afterEach(()=>{cleanup();vi.restoreAllMocks()})
const show=()=>render(<MessageAttachmentDownloads attachments={[file]}/>)
const click=()=>fireEvent.click(screen.getByRole('button',{name:/synthetic.pdf/}))
it('obtains fresh authority, saves private bytes and revokes its object URL on close',async()=>{
  const view=show();click();await waitFor(()=>expect(URL.createObjectURL).toHaveBeenCalledTimes(1))
  expect(files.grant).toHaveBeenCalledWith('file-1',expect.any(AbortSignal))
  expect(files.download).toHaveBeenCalledWith(grant,expect.any(AbortSignal))
  view.unmount();expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic-attachment')
})
it('does not download on a denied grant',async()=>{
  vi.mocked(files.grant).mockRejectedValue(new Error('Denied'));show();click();await screen.findByRole('alert')
  expect(files.download).not.toHaveBeenCalled();expect(URL.createObjectURL).not.toHaveBeenCalled()
})
it('discards late bytes after leaving the message',async()=>{
  let resolve!:(blob:Blob)=>void;vi.mocked(files.download).mockReturnValue(new Promise(done=>{resolve=done}))
  const view=show();click();await waitFor(()=>expect(files.download).toHaveBeenCalledTimes(1))
  view.unmount();await act(async()=>{resolve(new Blob(['private']))})
  expect(URL.createObjectURL).not.toHaveBeenCalled()
})
it('refuses a grant returned for a different file',async()=>{
  vi.mocked(files.grant).mockResolvedValue({...grant,fileId:'another-file'});show();click();await screen.findByRole('alert')
  expect(files.download).not.toHaveBeenCalled()
})
