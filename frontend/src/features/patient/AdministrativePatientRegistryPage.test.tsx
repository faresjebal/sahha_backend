import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  PatientAdministrativePageResource,
  PatientAdministrativeResource,
} from '../../models/patient'
import { AdministrativePatientRegistryPage } from './AdministrativePatientRegistryPage'

const patientService = vi.hoisted(() => ({
  list:vi.fn(),
  get:vi.fn(),
  checkDuplicates:vi.fn(),
  create:vi.fn(),
  update:vi.fn(),
}))

vi.mock('../../services/api/patientRegistryRestService', () => ({
  patientRegistryRestService:patientService,
}))

vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => ({ session:{ user:{ organizationId:'organisation-1' } } }),
}))

const patient: PatientAdministrativeResource = {
  registrationId:'registration-1',
  patientId:'patient-1',
  organisationId:'organisation-1',
  medicalRecordNumber:'PT-123456789ABC',
  registrationStatus:'ACTIVE',
  firstName:'Amina',
  lastName:'Ben Salem',
  dateOfBirth:'1990-04-12',
  sex:'FEMALE',
  identifierType:'NATIONAL_ID',
  maskedIdentifier:'****8821',
  identifierCountryCode:'TN',
  phoneNumber:'+216 20 123 456',
  email:'amina@example.test',
  address:'12 Synthetic Street',
  city:'Tunis',
  region:'Tunis',
  postalCode:'1000',
  countryCode:'TN',
  emergencyContactName:null,
  emergencyContactPhone:null,
  emergencyContactRelationship:null,
  preferredLanguage:'Arabic',
  accessibilityNeeds:null,
  privacyNoticeAcknowledgedAt:'2026-08-05T10:00:00Z',
  registeredBy:'user-1',
  updatedBy:'user-1',
  registeredAt:'2026-08-05T10:00:00Z',
  createdAt:'2026-08-05T10:00:00Z',
  updatedAt:'2026-08-05T10:00:00Z',
  registrationVersion:0,
  identityVersion:0,
}

const page: PatientAdministrativePageResource = {
  items:[patient],
  page:0,
  size:50,
  totalElements:1,
  totalPages:1,
}

const renderPage = (mode: 'directory' | 'registration' = 'directory') => {
  const queryClient = new QueryClient({
    defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
  })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return render(<AdministrativePatientRegistryPage mode={mode}/>, { wrapper:Wrapper })
}

const completeRegistration = () => {
  fireEvent.change(screen.getByLabelText('Legal first name'), { target:{ value:'Sami' } })
  fireEvent.change(screen.getByLabelText('Legal last name'), { target:{ value:'Trabelsi' } })
  fireEvent.change(screen.getByLabelText('Date of birth'), { target:{ value:'1988-03-02' } })
  fireEvent.change(screen.getByLabelText('Document value'), { target:{ value:'TN-1234-5678' } })
  fireEvent.change(screen.getByLabelText('Mobile phone'), { target:{ value:'+216 21 222 333' } })
  fireEvent.change(screen.getByLabelText('Home address'), { target:{ value:'4 Synthetic Avenue' } })
  fireEvent.click(screen.getByLabelText(/patient received the privacy notice/i))
}

describe('Administrative patient registry page', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    patientService.list.mockResolvedValue(page)
    patientService.get.mockResolvedValue(patient)
    patientService.checkDuplicates.mockResolvedValue({
      reviewRequired:false,
      exactStrongIdentifierMatch:false,
      candidates:[],
    })
    patientService.create.mockResolvedValue(patient)
  })

  it('renders and opens only the administrative patient projection', async () => {
    renderPage()

    expect(await screen.findByText('PT-123456789ABC · 36 years')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name:/Amina Ben Salem/i }))

    expect(await screen.findByRole('heading', { name:'Amina Ben Salem' })).toBeInTheDocument()
    expect(screen.getByText('****8821', { exact:false })).toBeInTheDocument()
    expect(screen.getByText(/Clinical information is not part of this response/i)).toBeInTheDocument()
    expect(screen.queryByText(/diagnosis value/i)).not.toBeInTheDocument()
    expect(patientService.get).toHaveBeenCalledWith('registration-1')
  })

  it('checks duplicates before registering a new patient', async () => {
    renderPage('registration')
    completeRegistration()
    fireEvent.click(screen.getByRole('button', { name:/check and register/i }))

    await waitFor(() => expect(patientService.checkDuplicates).toHaveBeenCalledOnce())
    await waitFor(() => expect(patientService.create).toHaveBeenCalledOnce())
    expect(patientService.create.mock.calls[0][0]).not.toHaveProperty('organisationId')
  })

  it('requires an explicit selection when a duplicate candidate is returned', async () => {
    patientService.checkDuplicates.mockResolvedValueOnce({
      reviewRequired:true,
      exactStrongIdentifierMatch:true,
      candidates:[{
        patientId:'existing-patient',
        maskedDisplayName:'A*** B***',
        birthYear:1988,
        sex:'FEMALE',
        maskedIdentifier:'****5678',
        score:100,
        exactStrongIdentifierMatch:true,
        matchReasons:['STRONG_IDENTIFIER'],
        registeredInActiveOrganisation:false,
        activeOrganisationRegistrationId:null,
      }],
    })
    renderPage('registration')
    completeRegistration()
    fireEvent.click(screen.getByRole('button', { name:/check and register/i }))

    expect(await screen.findByRole('heading', { name:/review possible existing patients/i })).toBeInTheDocument()
    expect(patientService.create).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name:'Use this patient identity' }))

    await waitFor(() => expect(patientService.create).toHaveBeenCalledWith(
      expect.objectContaining({
        duplicateDecision:'LINK_EXISTING',
        selectedPatientId:'existing-patient',
        duplicateDecisionReason:'SAME_PERSON_CONFIRMED',
      }),
    ))
  })
})
