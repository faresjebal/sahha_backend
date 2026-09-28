export interface MessageAttachmentReference {
  fileId:string
  originalFilename:string
  contentType:string
  size:number
}
export interface MessageAttachmentResource extends MessageAttachmentReference {
  uploadStatus:'NEGOTIATED'|'UPLOADING'|'STORED'|'FAILED'
  scanStatus:'PENDING'|'CLEAN'|'REJECTED'
}
export interface MessageAttachmentUploadCommand {
  uploadRequestId:string
  conversationId:string
  messageRequestId:string
  originalFilename:string
  contentType:string
  declaredSize:number
  checksumSha256:string
}
export interface MessageAttachmentTicket {
  file:MessageAttachmentResource
  uploadPath:string|null
  uploadToken:string|null
  expiresAt:string|null
}
