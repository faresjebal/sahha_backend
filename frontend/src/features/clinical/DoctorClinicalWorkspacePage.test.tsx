import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ClinicalRecordResource } from '../../models/clinical'
import type { MedicalFileResource } from '../../models/medicalFile'
import { DoctorClinicalWorkspacePage } from './DoctorClinicalWorkspacePage'

const consultationService = vi.hoisted(() => ({
  create:vi.fn(),
  getRecord:vi.fn(),
  replaceDraft:vi.fn(),
  finalize:vi.fn(),
  correct:vi.fn(),
  getPatientSummary:vi.fn(),
}))
const appointmentService = vi.hoisted(() => ({ list:vi.fn() }))
const fileService = vi.hoisted(() => ({
  list:vi.fn(),
  negotiateUpload:vi.fn(),
  uploadContent:vi.fn(),
  applySyntheticScan:vi.fn(),
  issueDownloadGrant:vi.fn(),
  download:vi.fn(),
}))

vi.mock('../../services/api/consultationRestService', () => ({
  consultationRestService:consultationService,
}))
vi.mock('../../services/api/appointmentRestService', () => ({
  appointmentRestService:appointmentService,
}))
vi.mock('../../services/api/medicalFileRestService', () => ({
  medicalFileRestService:fileService,
}))
vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({ session:{ user:{ organizationId:'organisation-1' } } }),
}))

const record:ClinicalRecordResource = {
  id:'consultation-12345678',
  organisationId:'organisation-1',
  appointmentId:'appointment-12345678',
  patientRegistrationId:'registration-12345678',
  patientId:'patient-1',
  doctorUserId:'doctor-1',
  doctorMembershipId:'membership-1',
  status:'DRAFT',
  reasonForConsultation:'Persistent cough',
  clinicalAssessment:'Likely acute bronchitis.',
  treatmentPlan:'Supportive care.',
  followUpInstructions:'Return in seven days.',
  additionalNotes:'Synthetic internship record.',
  symptoms:[{
    id:'symptom-1', name:'Cough', onsetDescription:'Five days',
    severity:'MODERATE', notes:null,
  }],
  history:[],
  vitalSigns:null,
  examinationFindings:[{
    id:'exam-1', bodySystem:'Respiratory', finding:'Scattered rhonchi',
    notes:null,
  }],
  diagnoses:[{
    id:'diagnosis-1', code:'J20.9', codeSystem:'ICD-10',
    label:'Acute bronchitis', type:'PRIMARY', status:'CONFIRMED', notes:null,
  }],
  medications:[],
  corrections:[],
  finalizedAt:null,
  finalizedByUserId:null,
  createdAt:'2026-08-30T10:00:00Z',
  updatedAt:'2026-08-30T10:00:00Z',
  version:4,
}

const summary = {
  organisationId:'organisation-1',
  patientRegistrationId:record.patientRegistrationId,
  patientId:record.patientId,
  careRelationship:{
    appointmentId:record.appointmentId,
    appointmentStatus:'IN_PROGRESS',
  },
  encounters:[],
  generatedAt:'2026-08-30T10:00:00Z',
}

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/doctor/clinical/${record.id}`]}>
        <DoctorClinicalWorkspacePage consultationId={record.id}/>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Doctor clinical workspace', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    consultationService.getRecord.mockResolvedValue(record)
    consultationService.getPatientSummary.mockResolvedValue(summary)
    consultationService.replaceDraft.mockImplementation(async (_id, command) => ({
      ...record,
      ...command,
      symptoms:record.symptoms,
      examinationFindings:record.examinationFindings,
      diagnoses:record.diagnoses,
      version:5,
    }))
    consultationService.finalize.mockResolvedValue({
      ...record,
      status:'FINALIZED',
      finalizedAt:'2026-08-30T10:15:00Z',
      finalizedByUserId:'doctor-1',
      version:6,
    })
    fileService.list.mockResolvedValue([])
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL:vi.fn(() => 'blob:synthetic-file'),
      revokeObjectURL:vi.fn(),
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('saves with the explicit version and finalises only after a complete record', async () => {
    renderPage()

    expect(await screen.findByDisplayValue('Persistent cough')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Follow-up instructions'), {
      target:{ value:'Return in ten days.' },
    })
    fireEvent.click(screen.getByRole('button', { name:/save now/i }))

    await waitFor(() => expect(consultationService.replaceDraft)
      .toHaveBeenCalledWith(
        record.id,
        expect.objectContaining({
          version:4,
          followUpInstructions:'Return in ten days.',
        }),
      ))
    await waitFor(() => expect(screen.getByText('Version 5')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name:/finalise consultation/i }))
    await waitFor(() => expect(consultationService.finalize).toHaveBeenCalledWith(
      record.id,
      { version:5 },
    ))
    expect(await screen.findByText('Final clinical record.')).toBeInTheDocument()
    expect(screen.getByText(/signed source is locked/i)).toBeInTheDocument()
  })

  it('downloads a clean file through a one-time Gateway grant', async () => {
    const medicalFile:MedicalFileResource = {
      fileId:'file-12345678',
      consultationId:record.id,
      originalFilename:'synthetic-report.pdf',
      contentType:'application/pdf',
      size:9,
      uploadStatus:'STORED',
      scanStatus:'CLEAN',
      downloadAvailable:true,
      createdAt:'2026-08-30T10:00:00Z',
      uploadedAt:'2026-08-30T10:01:00Z',
      availableAt:'2026-08-30T10:02:00Z',
      rejectedAt:null,
    }
    fileService.list.mockResolvedValue([medicalFile])
    fileService.issueDownloadGrant.mockResolvedValue({
      grantId:'grant-1',
      fileId:medicalFile.fileId,
      downloadPath:`/api/v1/files/${medicalFile.fileId}/content`,
      downloadToken:'one-time-token',
      expiresAt:'2026-08-30T10:04:00Z',
    })
    fileService.download.mockResolvedValue(new Blob(['synthetic'], {
      type:'application/pdf',
    }))
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name:'Download' }))

    await waitFor(() => expect(fileService.issueDownloadGrant)
      .toHaveBeenCalledWith(medicalFile.fileId))
    expect(fileService.download).toHaveBeenCalledWith(expect.objectContaining({
      downloadToken:'one-time-token',
    }))
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled()
  })

  it('autosaves a valid changed draft after the bounded pause', async () => {
    renderPage()

    fireEvent.change(await screen.findByLabelText('Clinical assessment'), {
      target:{ value:'Updated synthetic assessment.' },
    })

    await waitFor(() => expect(consultationService.replaceDraft)
      .toHaveBeenCalledWith(
        record.id,
        expect.objectContaining({
          version:4,
          clinicalAssessment:'Updated synthetic assessment.',
        }),
      ), { timeout:3000 })
  })

  it('negotiates and uploads the selected file without a storage URL', async () => {
    const file = new File(['synthetic'], 'synthetic-report.pdf', {
      type:'application/pdf',
    })
    const ticket = {
      fileId:'file-12345678',
      uploadPath:'/api/v1/files/file-12345678/content',
      uploadToken:'one-time-upload-token',
      expiresAt:'2026-08-30T10:05:00Z',
      contentType:file.type,
      declaredSize:file.size,
      uploadStatus:'NEGOTIATED',
      scanStatus:'PENDING',
    }
    fileService.negotiateUpload.mockResolvedValue(ticket)
    fileService.uploadContent.mockResolvedValue({
      fileId:ticket.fileId,
      uploadStatus:'STORED',
      scanStatus:'PENDING',
      size:file.size,
      checksumSha256:'a'.repeat(64),
      uploadedAt:'2026-08-30T10:01:00Z',
    })
    renderPage()

    fireEvent.change(await screen.findByLabelText(/PDF or clinical image/i), {
      target:{ files:[file] },
    })
    fireEvent.click(screen.getByRole('button', { name:/upload securely/i }))

    await waitFor(() => expect(fileService.negotiateUpload).toHaveBeenCalledWith({
      consultationId:record.id,
      originalFilename:file.name,
      contentType:file.type,
      declaredSize:file.size,
    }))
    expect(fileService.uploadContent).toHaveBeenCalledWith(ticket, file)
    expect(screen.getByText(/stored privately and is awaiting/i)).toBeInTheDocument()
  })

  it('appends an attributable correction to a finalised source', async () => {
    const finalized:ClinicalRecordResource = {
      ...record,
      status:'FINALIZED',
      finalizedAt:'2026-08-30T10:15:00Z',
      finalizedByUserId:'doctor-1',
      version:6,
    }
    consultationService.getRecord.mockResolvedValue(finalized)
    consultationService.correct.mockResolvedValue({
      ...finalized,
      clinicalAssessment:'Corrected synthetic assessment.',
      corrections:[{
        id:'correction-1',
        targetType:'CONSULTATION',
        targetId:null,
        fieldName:'clinicalAssessment',
        oldValue:finalized.clinicalAssessment,
        newValue:'Corrected synthetic assessment.',
        reason:'Corrected a synthetic dictation error.',
        actorUserId:'doctor-1',
        consultationVersion:7,
        correctedAt:'2026-08-30T10:20:00Z',
      }],
      version:7,
    })
    renderPage()

    fireEvent.change(await screen.findByLabelText('Corrected value'), {
      target:{ value:'Corrected synthetic assessment.' },
    })
    fireEvent.change(screen.getByLabelText('Reason for correction'), {
      target:{ value:'Corrected a synthetic dictation error.' },
    })
    fireEvent.click(screen.getByRole('button', { name:/append correction/i }))

    await waitFor(() => expect(consultationService.correct).toHaveBeenCalledWith(
      record.id,
      {
        version:6,
        targetType:'CONSULTATION',
        targetId:null,
        fieldName:'clinicalAssessment',
        newValue:'Corrected synthetic assessment.',
        reason:'Corrected a synthetic dictation error.',
      },
    ))
    expect(await screen.findByText(/correction was appended/i)).toBeInTheDocument()
    expect(screen.getByText('Version 7')).toBeInTheDocument()
  })
})
