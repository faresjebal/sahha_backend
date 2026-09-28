export type NotificationType =
  | 'APPOINTMENT_CONFIRMED'
  | 'APPOINTMENT_REJECTED'
  | 'APPOINTMENT_STARTED'
  | 'APPOINTMENT_COMPLETED'
  | 'APPOINTMENT_NO_SHOW'
  | 'APPOINTMENT_REQUESTED'
  | 'APPOINTMENT_RESCHEDULED'
  | 'APPOINTMENT_CANCELLED'
  | 'PATIENT_CHECKED_IN'
  | 'MESSAGE_RECEIVED'
  | 'REFERRAL_RECEIVED'
  | 'REFERRAL_ACCEPTED'
  | 'REFERRAL_REJECTED'
  | 'REFERRAL_REVOKED'
  | 'REFERRAL_COMPLETED'
  | 'REFERRAL_EXPIRED'

export type NotificationAppointmentStatus =
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'RESCHEDULED'
  | 'CANCELLED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'REJECTED'

export interface NotificationResource {
  id: string
  notificationType: NotificationType
  resourceType: string
  resourceId: string
  appointmentStatus: NotificationAppointmentStatus | null
  appointmentStartsAt: string | null
  appointmentEndsAt: string | null
  appointmentTimeZone: string | null
  appointmentLocationLabel: string | null
  resourceVersion: number
  eventOccurredAt: string
  createdAt: string
  read: boolean
  readAt: string | null
}

export interface NotificationPageResource {
  items: NotificationResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface UnreadNotificationCountResource {
  unreadCount: number
}

export interface MarkAllNotificationsReadResource {
  updatedCount: number
  readAt: string
}

export interface RealtimeNotificationMessage {
  messageType: 'NOTIFICATION_CREATED'
  notification: NotificationResource
}

const notificationTypes = new Set<NotificationType>([
  'APPOINTMENT_CONFIRMED',
  'APPOINTMENT_REJECTED',
  'APPOINTMENT_STARTED',
  'APPOINTMENT_COMPLETED',
  'APPOINTMENT_NO_SHOW',
  'APPOINTMENT_REQUESTED',
  'APPOINTMENT_RESCHEDULED',
  'APPOINTMENT_CANCELLED',
  'PATIENT_CHECKED_IN',
  'MESSAGE_RECEIVED',
  'REFERRAL_RECEIVED',
  'REFERRAL_ACCEPTED',
  'REFERRAL_REJECTED',
  'REFERRAL_REVOKED',
  'REFERRAL_COMPLETED',
  'REFERRAL_EXPIRED',
])

const appointmentStatuses = new Set<NotificationAppointmentStatus>([
  'REQUESTED',
  'CONFIRMED',
  'RESCHEDULED',
  'CANCELLED',
  'CHECKED_IN',
  'IN_PROGRESS',
  'COMPLETED',
  'NO_SHOW',
  'REJECTED',
])

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const isNotificationResource = (
  value: unknown,
): value is NotificationResource => {
  if (!isRecord(value)) return false
  return typeof value.id === 'string'
    && notificationTypes.has(value.notificationType as NotificationType)
    && typeof value.resourceType === 'string'
    && typeof value.resourceId === 'string'
    && (value.resourceType === 'CONVERSATION' || value.resourceType === 'REFERRAL'
      ? (value.resourceType === 'CONVERSATION'
          ? value.notificationType === 'MESSAGE_RECEIVED'
          : String(value.notificationType).startsWith('REFERRAL_'))
        && value.appointmentStatus === null
        && value.appointmentStartsAt === null
        && value.appointmentEndsAt === null
        && value.appointmentTimeZone === null
        && value.appointmentLocationLabel === null
      : value.resourceType === 'APPOINTMENT'
        && !String(value.notificationType).startsWith('REFERRAL_')
        && value.notificationType !== 'MESSAGE_RECEIVED'
        && appointmentStatuses.has(value.appointmentStatus as NotificationAppointmentStatus)
        && typeof value.appointmentStartsAt === 'string'
        && typeof value.appointmentEndsAt === 'string'
        && typeof value.appointmentTimeZone === 'string'
        && typeof value.appointmentLocationLabel === 'string')
    && typeof value.resourceVersion === 'number'
    && typeof value.eventOccurredAt === 'string'
    && typeof value.createdAt === 'string'
    && typeof value.read === 'boolean'
    && (value.readAt === null || typeof value.readAt === 'string')
}

export const isRealtimeNotificationMessage = (
  value: unknown,
): value is RealtimeNotificationMessage => isRecord(value)
  && value.messageType === 'NOTIFICATION_CREATED'
  && isNotificationResource(value.notification)
