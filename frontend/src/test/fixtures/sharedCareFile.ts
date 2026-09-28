import type { SharedCareFile, SharedCareFilePage } from '../../models/sharedCareFile'
import { careReferral } from './sharedCare'
import { sharedFile } from './sharedReferral'

export const careFile = { ...sharedFile, originalFilename:'synthetic-care.pdf' }
export const careFileMetadata:SharedCareFile = {
  organisationId:careReferral.organisationId, patientRegistrationId:careReferral.patientRegistrationId,
  validUntil:careReferral.accessExpiresAt, file:careFile,
}
export const careFiles:SharedCareFilePage = {
  organisationId:careReferral.organisationId, patientRegistrationId:careReferral.patientRegistrationId,
  consultationId:careFile.consultationId, validUntil:careReferral.accessExpiresAt,
  content:[careFile], page:0, size:20, totalElements:1, totalPages:1,
}
export const careFileGrant = {
  grantId:'care-file-grant', fileId:careFile.fileId,
  downloadPath:'/api/v1/files/shared-care/registration-1/file-1/content',
  downloadToken:'synthetic-care-download-token', expiresAt:careReferral.accessExpiresAt,
}
