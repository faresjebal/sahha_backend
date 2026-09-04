import type {
  MedicalFileDownloadGrantResource,
  MedicalFileResource,
  MedicalFileScanResource,
  MedicalFileUploadResource,
  MedicalFileUploadTicketResource,
  NegotiateMedicalFileUploadCommand,
} from '../../models/medicalFile'
import { httpClient } from './httpClient'

export const medicalFileRestService = {
  list(consultationId:string) {
    const query = new URLSearchParams({ consultationId })
    return httpClient.request<MedicalFileResource[]>(`/files?${query}`)
  },

  negotiateUpload(command:NegotiateMedicalFileUploadCommand) {
    return httpClient.request<MedicalFileUploadTicketResource>('/files/uploads', {
      method:'POST',
      body:command,
    })
  },

  uploadContent(ticket:MedicalFileUploadTicketResource, file:File) {
    if (file.size !== ticket.declaredSize || file.type !== ticket.contentType) {
      throw new TypeError('Selected file does not match the negotiated upload.')
    }
    return httpClient.request<MedicalFileUploadResource>(ticket.uploadPath, {
      method:'PUT',
      rawBody:file,
      headers:{
        'Content-Type':file.type,
        'X-Upload-Token':ticket.uploadToken,
      },
    })
  },

  issueDownloadGrant(fileId:string) {
    return httpClient.request<MedicalFileDownloadGrantResource>(
      `/files/${fileId}/download-grants`,
      { method:'POST' },
    )
  },

  download(grant:MedicalFileDownloadGrantResource) {
    return httpClient.requestBlob(grant.downloadPath, {
      headers:{ 'X-Download-Token':grant.downloadToken },
    })
  },

  applySyntheticScan(fileId:string, decision:'CLEAN' | 'REJECTED') {
    return httpClient.request<MedicalFileScanResource>(
      `/files/${fileId}/synthetic-scan`,
      { method:'POST', body:{ decision } },
    )
  },
}
