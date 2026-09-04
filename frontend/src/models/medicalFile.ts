export type MedicalFileUploadStatus = 'NEGOTIATED' | 'STORED' | 'FAILED'
export type MedicalFileScanStatus = 'PENDING' | 'CLEAN' | 'REJECTED'

export interface NegotiateMedicalFileUploadCommand {
  consultationId:string
  originalFilename:string
  contentType:string
  declaredSize:number
  expectedChecksumSha256?:string
}

export interface MedicalFileUploadTicketResource {
  fileId:string
  uploadPath:string
  uploadToken:string
  expiresAt:string
  contentType:string
  declaredSize:number
  uploadStatus:MedicalFileUploadStatus
  scanStatus:MedicalFileScanStatus
}

export interface MedicalFileUploadResource {
  fileId:string
  uploadStatus:MedicalFileUploadStatus
  scanStatus:MedicalFileScanStatus
  size:number
  checksumSha256:string
  uploadedAt:string
}

export interface MedicalFileResource {
  fileId:string
  consultationId:string
  originalFilename:string
  contentType:string
  size:number
  uploadStatus:MedicalFileUploadStatus
  scanStatus:MedicalFileScanStatus
  downloadAvailable:boolean
  createdAt:string
  uploadedAt:string | null
  availableAt:string | null
  rejectedAt:string | null
}

export interface MedicalFileDownloadGrantResource {
  grantId:string
  fileId:string
  downloadPath:string
  downloadToken:string
  expiresAt:string
}

export interface MedicalFileScanResource {
  fileId:string
  uploadStatus:MedicalFileUploadStatus
  scanStatus:MedicalFileScanStatus
  availableAt:string | null
  rejectedAt:string | null
  alreadyApplied:boolean
}
