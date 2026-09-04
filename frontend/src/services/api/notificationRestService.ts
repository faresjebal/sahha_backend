import type {
  MarkAllNotificationsReadResource,
  NotificationPageResource,
  NotificationResource,
  UnreadNotificationCountResource,
} from '../../models/notification'
import { httpClient } from './httpClient'

export const notificationRestService = {
  list(page = 0, size = 20) {
    const query = new URLSearchParams({
      page:String(page),
      size:String(size),
    })
    return httpClient.request<NotificationPageResource>(
      `/notifications?${query.toString()}`,
    )
  },

  unreadCount() {
    return httpClient.request<UnreadNotificationCountResource>(
      '/notifications/unread-count',
    )
  },

  markRead(notificationId: string) {
    return httpClient.request<NotificationResource>(
      `/notifications/${notificationId}/read`,
      { method:'POST' },
    )
  },

  markAllRead() {
    return httpClient.request<MarkAllNotificationsReadResource>(
      '/notifications/read-all',
      { method:'POST' },
    )
  },
}
