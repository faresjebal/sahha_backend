import type { NotificationPageResource, NotificationResource,
  UnreadNotificationCountResource, MarkAllNotificationsReadResource } from '../../models/notification'
import { httpClient } from './httpClient'

export const patientNotificationRestService = (registrationId: string) => {
  const base = '/notifications/patient/registrations/' + encodeURIComponent(registrationId)
  return {
    list(page = 0, size = 20) {
      return httpClient.request<NotificationPageResource>(base + '?' + new URLSearchParams({
        page:String(page), size:String(size),
      }))
    },
    unreadCount() {
      return httpClient.request<UnreadNotificationCountResource>(base + '/unread-count')
    },
    markRead(id: string) {
      return httpClient.request<NotificationResource>(base + '/' + encodeURIComponent(id) + '/read', { method:'POST' })
    },
    markAllRead() {
      return httpClient.request<MarkAllNotificationsReadResource>(base + '/read-all', { method:'POST' })
    },
  }
}
