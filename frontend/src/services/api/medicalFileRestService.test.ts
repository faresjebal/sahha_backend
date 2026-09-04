import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type {
  MedicalFileDownloadGrantResource,
  MedicalFileUploadTicketResource,
} from '../../models/medicalFile'
import { httpClient } from './httpClient'
import { medicalFileRestService } from './medicalFileRestService'

const jsonResponse = (body:unknown, status = 200) => new Response(
  JSON.stringify(body),
  { status, headers:{ 'Content-Type':'application/json' } },
)

const csrfResponse = () => jsonResponse({
  headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'file-csrf-token',
})

const ticket:MedicalFileUploadTicketResource = {
  fileId:'0cd80d4a-ccbf-41c6-91cc-e73561c9bf7b',
  uploadPath:'/api/v1/files/0cd80d4a-ccbf-41c6-91cc-e73561c9bf7b/content',
  uploadToken:'opaque-upload-ticket',
  expiresAt:'2026-08-30T00:05:00Z',
  contentType:'application/pdf',
  declaredSize:9,
  uploadStatus:'NEGOTIATED',
  scanStatus:'PENDING',
}

describe('Gateway medical-file REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('lists safe consultation metadata through Gateway', async () => {
    const files = [{
      fileId:ticket.fileId,
      consultationId:'4cc1bfdd-fca2-411f-a62a-95a6882197a1',
      originalFilename:'report.pdf',
      contentType:'application/pdf',
      size:9,
      uploadStatus:'STORED',
      scanStatus:'CLEAN',
      downloadAvailable:true,
      createdAt:'2026-08-30T00:00:00Z',
      uploadedAt:'2026-08-30T00:00:02Z',
      availableAt:'2026-08-30T00:00:03Z',
      rejectedAt:null,
    }]
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(files))
    vi.stubGlobal('fetch', fetchMock)

    await expect(medicalFileRestService.list(files[0].consultationId))
      .resolves.toEqual(files)
    expect(fetchMock).toHaveBeenCalledWith(
      `${env.apiBaseUrl}/files?consultationId=${files[0].consultationId}`,
      expect.objectContaining({ credentials:'include' }),
    )
  })

  it('negotiates and streams the exact selected file with one upload ticket', async () => {
    const stored = {
      fileId:ticket.fileId,
      uploadStatus:'STORED',
      scanStatus:'PENDING',
      size:9,
      checksumSha256:'a'.repeat(64),
      uploadedAt:'2026-08-30T00:00:02Z',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(ticket, 201))
      .mockResolvedValueOnce(jsonResponse(stored, 202))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['synthetic'], 'report.pdf', {
      type:'application/pdf',
    })

    const negotiated = await medicalFileRestService.negotiateUpload({
      consultationId:'4cc1bfdd-fca2-411f-a62a-95a6882197a1',
      originalFilename:file.name,
      contentType:file.type,
      declaredSize:file.size,
    })
    await expect(medicalFileRestService.uploadContent(negotiated, file))
      .resolves.toEqual(stored)

    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/files/${ticket.fileId}/content`,
    )
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method:'PUT',
      body:file,
      headers:expect.objectContaining({
        'Content-Type':'application/pdf',
        'X-Upload-Token':'opaque-upload-ticket',
        'X-XSRF-TOKEN':'file-csrf-token',
      }),
    })
  })

  it('issues a grant and downloads bytes without exposing object storage', async () => {
    const grant:MedicalFileDownloadGrantResource = {
      grantId:'0f642646-f44d-4a60-94dd-81231d5a780d',
      fileId:ticket.fileId,
      downloadPath:`/api/v1/files/${ticket.fileId}/content`,
      downloadToken:'opaque-download-grant',
      expiresAt:'2026-08-30T00:02:00Z',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(grant))
      .mockResolvedValueOnce(new Response('synthetic', {
        headers:{ 'Content-Type':'application/pdf' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    const issued = await medicalFileRestService.issueDownloadGrant(ticket.fileId)
    const bytes = await medicalFileRestService.download(issued)

    expect(bytes.size).toBe(9)
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/files/${ticket.fileId}/content`,
    )
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method:'GET',
      credentials:'include',
      headers:expect.objectContaining({
        'X-Download-Token':'opaque-download-grant',
      }),
    })
  })

  it('uses the explicit local synthetic scan command through Gateway', async () => {
    const scan = {
      fileId:ticket.fileId,
      uploadStatus:'STORED',
      scanStatus:'CLEAN',
      availableAt:'2026-08-30T00:00:03Z',
      rejectedAt:null,
      alreadyApplied:false,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(scan))
    vi.stubGlobal('fetch', fetchMock)

    await expect(medicalFileRestService.applySyntheticScan(
      ticket.fileId,
      'CLEAN',
    )).resolves.toEqual(scan)
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/files/${ticket.fileId}/synthetic-scan`,
    )
    expect(JSON.parse(String(
      (fetchMock.mock.calls[1][1] as RequestInit).body,
    ))).toEqual({ decision:'CLEAN' })
  })
})
