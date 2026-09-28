import {afterEach,expect,it,vi} from 'vitest'
import {messageAttachmentRestService as service} from './messageAttachmentRestService'
import {httpClient} from './httpClient'
afterEach(()=>vi.restoreAllMocks())
it('keeps attachment operations on encoded Gateway paths',async()=>{
  const request=vi.spyOn(httpClient,'request').mockResolvedValue({})
  await service.metadata('file/unsafe');await service.grant('file/unsafe')
  expect(request.mock.calls[0][0]).toBe('/files/message-attachments/file%2Funsafe')
  expect(request.mock.calls[1][0]).toBe('/files/message-attachments/file%2Funsafe/download-grants')
})
it('sends a one-use token in a header, never the URL',async()=>{
  const request=vi.spyOn(httpClient,'requestBlob').mockResolvedValue(new Blob())
  const grant={fileId:'file',grantId:'grant',downloadPath:'/api/v1/files/message-attachments/file/content',
    downloadToken:'private-token',expiresAt:new Date(Date.now()+60000).toISOString()}
  await service.download(grant)
  expect(request).toHaveBeenCalledWith(grant.downloadPath,{headers:{'X-Download-Token':'private-token'},signal:undefined})
})
it('rejects substituted and expired paths before making requests',()=>{
  const request=vi.spyOn(httpClient,'requestBlob')
  const grant={fileId:'file',grantId:'grant',downloadPath:'https://external.invalid/file',
    downloadToken:'private-token',expiresAt:new Date(Date.now()+60000).toISOString()}
  expect(()=>service.download(grant)).toThrow(TypeError)
  expect(()=>service.download({...grant,downloadPath:'/api/v1/files/message-attachments/file/content',expiresAt:'2000-01-01T00:00:00Z'})).toThrow(TypeError)
  expect(request).not.toHaveBeenCalled()
})
