import type {
  AppointmentCommand,
  AppointmentResource,
  BookAppointmentCommand,
  ReasonedAppointmentCommand,
  RescheduleAppointmentCommand,
  PatientAppointmentResource,
} from '../../models/scheduling'
import { httpClient } from './httpClient'

export const appointmentRestService = {
  list(from: string, to: string) {
    const query = new URLSearchParams({ from, to })
    return httpClient.request<AppointmentResource[]>(
      `/appointments?${query.toString()}`,
    )
  },

  get(appointmentId: string) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}`,
    )
  },

  book(command: BookAppointmentCommand) {
    return httpClient.request<AppointmentResource>('/appointments', {
      method:'POST',
      body:command,
    })
  },

  confirm(appointmentId: string, command: AppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/confirm`,
      { method:'POST', body:command },
    )
  },

  reject(appointmentId: string, command: ReasonedAppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/reject`,
      { method:'POST', body:command },
    )
  },

  reschedule(appointmentId: string, command: RescheduleAppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/reschedule`,
      { method:'POST', body:command },
    )
  },

  cancel(appointmentId: string, command: ReasonedAppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/cancel`,
      { method:'POST', body:command },
    )
  },

  checkIn(appointmentId: string, command: AppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/check-in`,
      { method:'POST', body:command },
    )
  },

  start(appointmentId: string, command: AppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/start`,
      { method:'POST', body:command },
    )
  },

  complete(appointmentId: string, command: AppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/complete`,
      { method:'POST', body:command },
    )
  },

  noShow(appointmentId: string, command: AppointmentCommand) {
    return httpClient.request<AppointmentResource>(
      `/appointments/${appointmentId}/no-show`,
      { method:'POST', body:command },
    )
  },

  listMine(registrationId: string, from: string, to: string) {
    const query = new URLSearchParams({ registrationId, from, to })
    return httpClient.request<PatientAppointmentResource[]>(
      `/appointments/mine?${query.toString()}`,
    )
  },

  bookMine(command: BookAppointmentCommand) {
    return httpClient.request<PatientAppointmentResource>(
      '/appointments/mine',
      { method:'POST', body:command },
    )
  },
}
