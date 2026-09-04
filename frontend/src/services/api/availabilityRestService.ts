import type {
  AvailableSlotsResource,
  DoctorAvailabilityResource,
  DoctorAvailabilitySummaryResource,
  UpsertDoctorAvailabilityCommand,
  PatientDoctorAvailabilityResource,
} from '../../models/scheduling'
import { ApiError } from './ApiError'
import { httpClient } from './httpClient'

export const availabilityRestService = {
  async getMine() {
    try {
      return await httpClient.request<DoctorAvailabilityResource>(
        '/availability/me',
      )
    } catch (error) {
      if (error instanceof ApiError && error.problem.status === 404) return null
      throw error
    }
  },

  updateMine(command: UpsertDoctorAvailabilityCommand) {
    return httpClient.request<DoctorAvailabilityResource>(
      '/availability/me',
      { method:'PUT', body:command },
    )
  },

  listDoctors() {
    return httpClient.request<DoctorAvailabilitySummaryResource[]>(
      '/availability/doctors',
    )
  },

  slots(doctorUserId: string, from: string, to: string) {
    const parameters = new URLSearchParams({ from, to })
    return httpClient.request<AvailableSlotsResource>(
      `/availability/doctors/${encodeURIComponent(doctorUserId)}/slots?${parameters}`,
    )
  },

  listMyPatientDoctors(registrationId: string) {
    return httpClient.request<PatientDoctorAvailabilityResource[]>(
      `/availability/mine/registrations/${encodeURIComponent(registrationId)}/doctors`,
    )
  },

  myPatientSlots(
    registrationId: string,
    doctorUserId: string,
    from: string,
    to: string,
  ) {
    const parameters = new URLSearchParams({ from, to })
    return httpClient.request<AvailableSlotsResource>(
      `/availability/mine/registrations/${encodeURIComponent(registrationId)}/doctors/${encodeURIComponent(doctorUserId)}/slots?${parameters}`,
    )
  },
}
