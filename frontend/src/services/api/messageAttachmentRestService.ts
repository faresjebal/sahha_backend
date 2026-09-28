import type { MessageAttachmentResource, MessageAttachmentTicket, MessageAttachmentUploadCommand } from '../../models/messageAttachment'
import type { MedicalFileDownloadGrantResource } from '../../models/medicalFile'
import { httpClient } from './httpClient'

const base='/files/message-attachments'
const route=(id:string)=>base+'/'+encodeURIComponent(id)
const contentPath=(id:string)=>'/api/v1'+route(id)+'/content'
export const messageAttachmentRestService={
  negotiate(command:MessageAttachmentUploadCommand,signal?:AbortSignal) {
    return httpClient.request<MessageAttachmentTicket>(base+'/uploads',{method:'POST',body:command,signal})
  },
  upload(ticket:MessageAttachmentTicket,file:File,signal?:AbortSignal) {
    if(ticket.uploadPath!==contentPath(ticket.file.fileId) || !ticket.uploadToken
      || !ticket.expiresAt || !Number.isFinite(Date.parse(ticket.expiresAt)) || Date.parse(ticket.expiresAt)<=Date.now()
      || file.size!==ticket.file.size || file.type!==ticket.file.contentType)
      throw new TypeError('The attachment upload ticket does not match this file.')
    return httpClient.request<MessageAttachmentResource>(ticket.uploadPath,{method:'PUT',rawBody:file,signal,
      headers:{'Content-Type':file.type,'X-Upload-Token':ticket.uploadToken}})
  },
  metadata(fileId:string,signal?:AbortSignal) {
    return httpClient.request<MessageAttachmentResource>(route(fileId),{signal})
  },
  grant(fileId:string,signal?:AbortSignal) {
    return httpClient.request<MedicalFileDownloadGrantResource>(route(fileId)+'/download-grants',{method:'POST',signal})
  },
  download(grant:MedicalFileDownloadGrantResource,signal?:AbortSignal) {
    if(grant.downloadPath!==contentPath(grant.fileId) || !grant.downloadToken
      || !Number.isFinite(Date.parse(grant.expiresAt)) || Date.parse(grant.expiresAt)<=Date.now())
      throw new TypeError('The private attachment download grant is invalid or expired.')
    return httpClient.requestBlob(grant.downloadPath,{signal,headers:{'X-Download-Token':grant.downloadToken}})
  },
}
