import type {
  CreatePatientRegistrationCommand,
  PatientAdministrativePageResource,
  PatientAdministrativeResource,
  PatientDuplicateCheckCommand,
  PatientDuplicateCheckResource,
  UpdatePatientRegistrationCommand,
  LinkPatientAccountCommand,
  MyPatientRegistrationResource,
  PatientAccountLinkResource,
} from '../../models/patient'
import { httpClient } from './httpClient'
import { ApiError } from './ApiError'

export const patientRegistryRestService = {
  list(query = '', page = 0, size = 50) {
    const parameters = new URLSearchParams({
      query,
      page:String(page),
      size:String(size),
    })
    return httpClient.request<PatientAdministrativePageResource>(
      `/patients?${parameters.toString()}`,
    )
  },

  get(registrationId: string) {
    return httpClient.request<PatientAdministrativeResource>(
      `/patients/${encodeURIComponent(registrationId)}`,
    )
  },

  checkDuplicates(command: PatientDuplicateCheckCommand) {
    return httpClient.request<PatientDuplicateCheckResource>(
      '/patients/duplicate-check',
      { method:'POST', body:command },
    )
  },

  create(command: CreatePatientRegistrationCommand) {
    return httpClient.request<PatientAdministrativeResource>('/patients', {
      method:'POST',
      body:command,
    })
  },

  update(
    registrationId: string,
    command: UpdatePatientRegistrationCommand,
  ) {
    return httpClient.request<PatientAdministrativeResource>(
      `/patients/${encodeURIComponent(registrationId)}`,
      { method:'PUT', body:command },
    )
  },

  linkMyAccount(command: LinkPatientAccountCommand) {
    return httpClient.request<PatientAccountLinkResource>(
      '/patients/me/account-link',
      { method:'POST', body:command },
    )
  },

  async listMyRegistrations() {
    try {
      return await httpClient.request<MyPatientRegistrationResource[]>(
        '/patients/me/registrations',
      )
    } catch (error) {
      // A newly verified identity can legitimately have no clinic record yet.
      // This applies only to listing; explicit linking and protected reads keep
      // their backend ownership checks and continue to surface denials.
      if (error instanceof ApiError
          && error.problem.status === 404
          && error.problem.type === 'urn:sahha:problem:patient-account-link-not-found') {
        return []
      }
      throw error
    }
  },
}
