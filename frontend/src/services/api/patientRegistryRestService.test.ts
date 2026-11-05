import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type {
  CreatePatientRegistrationCommand,
  PatientAdministrativeResource,
} from '../../models/patient'
import { httpClient } from './httpClient'
import { patientRegistryRestService } from './patientRegistryRestService'
import { ApiError } from './ApiError'

const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers:{ 'Content-Type':'application/json' },
})

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

const command: CreatePatientRegistrationCommand = {
  firstName:'Amina',
  lastName:'Ben Salem',
  dateOfBirth:'1990-04-12',
  sex:'FEMALE',
  identifier:{ type:'NATIONAL_ID', value:'TN-0735-8821', countryCode:'TN' },
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
  privacyNoticeAcknowledged:true,
}

describe('Gateway patient registry REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('treats the precise missing account link as an empty list for a new identity', async () => {
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response({
      type:'urn:sahha:problem:patient-account-link-not-found',
      title:'Patient account link not found',status:404,
      detail:'The verified account could not be matched to an owned patient registration.',
    },404)))
    await expect(patientRegistryRestService.listMyRegistrations()).resolves.toEqual([])
  })

  it.each([401,403,404,503])('preserves an unrelated HTTP %s failure instead of creating an empty portal', async status => {
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response({
      type:'urn:sahha:problem:other-failure',title:'Request failed',status,
      detail:'An actual failure',
    },status)))
    await expect(patientRegistryRestService.listMyRegistrations()).rejects.toBeInstanceOf(ApiError)
  })

  it('does not convert a failed explicit record-link operation into an empty-list success', async () => {
    vi.spyOn(httpClient,'request').mockRejectedValue(new ApiError({
      type:'urn:sahha:problem:patient-account-link-not-found',
      title:'Patient account link not found',status:404,
      detail:'The registration could not be matched.',
    }))
    await expect(patientRegistryRestService.linkMyAccount({
      organisationId:'organisation-1',medicalRecordNumber:'PT-12AB34CD56EF',dateOfBirth:'1990-04-12',
    })).rejects.toBeInstanceOf(ApiError)
  })

  it('searches the tenant directory and reads a registration through Gateway', async () => {
    const page = { items:[patient], page:0, size:50, totalElements:1, totalPages:1 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(page))
      .mockResolvedValueOnce(response(patient))
    vi.stubGlobal('fetch', fetchMock)

    await expect(patientRegistryRestService.list('Amina')).resolves.toEqual(page)
    await expect(patientRegistryRestService.get(patient.registrationId)).resolves.toEqual(patient)

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/patients?query=Amina&page=0&size=50`,
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/patients/${patient.registrationId}`,
    )
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials:'include' })
  })

  it('checks duplicates before creating with one CSRF bootstrap', async () => {
    const csrfResource = {
      headerName:'X-XSRF-TOKEN',
      parameterName:'_csrf',
      token:'patient-csrf-token',
    }
    const duplicateResult = {
      reviewRequired:false,
      exactStrongIdentifierMatch:false,
      candidates:[],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(csrfResource))
      .mockResolvedValueOnce(response(duplicateResult))
      .mockResolvedValueOnce(response(patient, 201))
    vi.stubGlobal('fetch', fetchMock)

    await patientRegistryRestService.checkDuplicates(command)
    await patientRegistryRestService.create(command)

    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/patients/duplicate-check`,
    )
    expect(fetchMock.mock.calls[2][0]).toBe(`${env.apiBaseUrl}/patients`)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method:'POST',
      headers:expect.objectContaining({ 'X-XSRF-TOKEN':'patient-csrf-token' }),
    })
    expect(JSON.parse(String((fetchMock.mock.calls[2][1] as RequestInit).body)))
      .toEqual(command)
  })

  it('links and lists only the current patient account registrations', async () => {
    const linkCommand = {
      organisationId:'organisation-1',
      medicalRecordNumber:'PT-12AB34CD56EF',
      dateOfBirth:'1990-04-12',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'link-csrf',
      }))
      .mockResolvedValueOnce(response({
        linkId:'link-1', authUserId:'user-1', linkedAt:'2026-08-14T02:00:00Z',
      }, 201))
      .mockResolvedValueOnce(response([{
        registrationId:'registration-1', organisationId:'organisation-1',
        medicalRecordNumber:'PT-12AB34CD56EF', status:'ACTIVE',
        firstName:'Amina', lastName:'Ben Salem',
      }]))
    vi.stubGlobal('fetch', fetchMock)

    await patientRegistryRestService.linkMyAccount(linkCommand)
    await patientRegistryRestService.listMyRegistrations()

    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/patients/me/account-link`,
    )
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/patients/me/registrations`,
    )
  })
})
