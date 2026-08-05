import type { ServiceContainer } from '../contracts'
import { httpClient } from './httpClient'
import { authRestService } from './authRestService'

export const restServices: ServiceContainer = {
  auth:authRestService,
  patients: {
    list: request => httpClient.request(`/patients?page=${request.page}&size=${request.size}&query=${encodeURIComponent(request.query || '')}`),
    get: patientId => httpClient.request(`/patients/${patientId}`),
    create: command => httpClient.request('/patients', { method: 'POST', body: command }),
  },
  doctors: {
    list: () => httpClient.request('/doctors'),
    create: command => httpClient.request('/doctors', { method: 'POST', body: command }),
  },
  departments: {
    list: () => httpClient.request('/departments'),
    create: command => httpClient.request('/departments', { method: 'POST', body: command }),
  },
  appointments: {
    list: () => httpClient.request('/appointments'),
    create: command => httpClient.request('/appointments', { method: 'POST', body: command }),
    cancel: (appointmentId, reason) => httpClient.request(`/appointments/${appointmentId}/cancellation`, { method: 'POST', body: { reason } }),
    reschedule: (appointmentId, startsAt, version) => httpClient.request(`/appointments/${appointmentId}/reschedule`, { method: 'POST', body: { startsAt, version } }),
    updateStatus: (appointmentId, status, version) => httpClient.request(`/appointments/${appointmentId}/status`, { method: 'POST', body: { status, version } }),
  },
  orders: {
    list: () => httpClient.request('/clinical-orders'),
    create: command => httpClient.request('/clinical-orders', { method: 'POST', body: command }),
  },
  notifications: {
    list: () => httpClient.request('/notifications'),
    markRead: notificationId => httpClient.request(`/notifications/${notificationId}/read`, { method: 'POST' }),
  },
  audit: {
    list: request => httpClient.request(`/audit-events?page=${request.page}&size=${request.size}&query=${encodeURIComponent(request.query || '')}`),
  },
}
