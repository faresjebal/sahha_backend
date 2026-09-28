import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bell,
  CalendarClock,
  CheckCheck,
  LoaderCircle,
  MessageSquare,
  Radio,
  RefreshCw,
  Share2,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../app/auth/AuthProvider'
import { env } from '../../config/env'
import type {
  NotificationPageResource,
  NotificationResource,
  RealtimeNotificationMessage,
  UnreadNotificationCountResource,
} from '../../models/notification'
import { apiErrorMessage } from '../../services/api/ApiError'
import { notificationRestService } from '../../services/api/notificationRestService'
import { patientNotificationRestService } from '../../services/api/patientNotificationRestService'
import {
  NotificationRealtimeClient,
  type NotificationConnectionState,
  type NotificationRealtimeCallbacks,
} from '../../services/realtime/notificationRealtimeClient'

type NotificationService = typeof notificationRestService
type RealtimeClient = Pick<NotificationRealtimeClient, 'start' | 'stop'>
type RealtimeClientFactory = (
  callbacks: NotificationRealtimeCallbacks,
) => RealtimeClient

const defaultRealtimeClientFactory: RealtimeClientFactory = callbacks =>
  new NotificationRealtimeClient(callbacks)

export interface DoctorNotificationCenterProps {
  patientRegistrationId?: string
  service?: NotificationService
  createRealtimeClient?: RealtimeClientFactory
  enabled?: boolean
  navigationTargets?: { appointment?: string; conversation?: string; referral?: string }
}

const listKey = (organisationId: string) => [
  'notifications', organisationId, 0, 20,
] as const
const unreadKey = (organisationId: string) => [
  'notification-unread-count', organisationId,
] as const

const notificationTitle = (notification: NotificationResource) => ({
  APPOINTMENT_CONFIRMED:'Appointment confirmed',
  APPOINTMENT_REJECTED:'Appointment request declined',
  APPOINTMENT_STARTED:'Appointment started',
  APPOINTMENT_COMPLETED:'Appointment completed',
  APPOINTMENT_NO_SHOW:'Appointment marked as missed',
  APPOINTMENT_REQUESTED:'New appointment request',
  APPOINTMENT_RESCHEDULED:'Appointment rescheduled',
  APPOINTMENT_CANCELLED:'Appointment cancelled',
  PATIENT_CHECKED_IN:'Patient checked in',
  MESSAGE_RECEIVED:'New secure message',
  REFERRAL_RECEIVED:'New referral request',
  REFERRAL_ACCEPTED:'Referral accepted',
  REFERRAL_REJECTED:'Referral declined',
  REFERRAL_REVOKED:'Referral revoked',
  REFERRAL_COMPLETED:'Referral completed',
  REFERRAL_EXPIRED:'Referral expired',
}[notification.notificationType])

const formatAppointmentTime = (notification: NotificationResource) => {
  if (notification.resourceType === 'REFERRAL') return 'Open referrals to review the current status'
  if (!notification.appointmentStartsAt) return 'Open the private conversation'
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle:'medium',
      timeStyle:'short',
      ...(notification.appointmentTimeZone
        ? { timeZone:notification.appointmentTimeZone }
        : {}),
    }).format(new Date(notification.appointmentStartsAt))
  } catch {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle:'medium',
      timeStyle:'short',
    }).format(new Date(notification.appointmentStartsAt))
  }
}

const connectionLabel: Record<NotificationConnectionState, string> = {
  connecting:'Connecting live updates',
  connected:'Live updates connected',
  reconnecting:'Recovering live updates',
  offline:'Live updates offline',
}

export function DoctorNotificationCenter({
  service: suppliedService,
  createRealtimeClient: suppliedRealtimeClient,
  patientRegistrationId,
  enabled = !env.useAuthMocks,
  navigationTargets = { appointment:'/doctor/appointments', conversation:'/doctor/messages', referral:'/doctor/referrals' },
}: DoctorNotificationCenterProps) {
  const auth = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const panelRef = useRef<HTMLDivElement>(null)
  const organisationId = auth.session?.user.organizationId || ''
  const userId = auth.session?.user.id || ''
  const patientMode = patientRegistrationId !== undefined
  const scope = patientMode ? 'patient:' + userId + ':' + patientRegistrationId : organisationId
  const service = useMemo(() => suppliedService || (patientMode
    ? patientNotificationRestService(patientRegistrationId!) : notificationRestService),
  [suppliedService, patientMode, patientRegistrationId])
  const createRealtimeClient = useMemo(() => suppliedRealtimeClient || (patientMode
    ? (callbacks: NotificationRealtimeCallbacks) => new NotificationRealtimeClient(callbacks, { patientRegistrationId })
    : defaultRealtimeClientFactory), [suppliedRealtimeClient, patientMode, patientRegistrationId])
  const liveEnabled = enabled && Boolean(userId && (patientMode ? patientRegistrationId : organisationId))
  const [open, setOpen] = useState(false)
  const [connection, setConnection] = useState<NotificationConnectionState>(
    'offline',
  )
  const [realtimeWarning, setRealtimeWarning] = useState('')

  const notificationsKey = useMemo(
    () => listKey(scope),
    [scope],
  )
  const notificationUnreadKey = useMemo(
    () => unreadKey(scope),
    [scope],
  )
  const inboxQuery = useQuery({
    queryKey:notificationsKey,
    queryFn:() => service.list(0, 20),
    enabled:liveEnabled,
  })
  const unreadQuery = useQuery({
    queryKey:notificationUnreadKey,
    queryFn:() => service.unreadCount(),
    enabled:liveEnabled,
  })

  useEffect(() => {
    if (!open) return
    const closeOutside = (event: PointerEvent) => {
      if (!panelRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  useEffect(() => {
    if (!liveEnabled) {
      setConnection('offline')
      return
    }
    let active = true
    const recoverInbox = () => {
      if (!active) return
      setRealtimeWarning('')
      void queryClient.invalidateQueries({ queryKey:notificationsKey })
      void queryClient.invalidateQueries({ queryKey:notificationUnreadKey })
      if (patientMode) {
        void queryClient.invalidateQueries({ queryKey:['my-patient-appointments', patientRegistrationId] })
        void queryClient.invalidateQueries({ queryKey:['my-patient-slots', patientRegistrationId] })
      } else {
        void queryClient.invalidateQueries({ queryKey:['referrals', userId, organisationId] })
        void queryClient.invalidateQueries({ queryKey:['referral', userId, organisationId] })
      }
    }
    const receiveNotification = ({
      notification,
    }: RealtimeNotificationMessage) => {
      if (!active) return
      if (patientMode && notification.resourceType !== 'APPOINTMENT') return
      let alreadyKnown = false
      queryClient.setQueryData<NotificationPageResource>(
        notificationsKey,
        current => {
          alreadyKnown = current?.items.some(item =>
            item.id === notification.id) ?? false
          if (alreadyKnown) return current
          if (!current) {
            return {
              items:[notification],
              page:0,
              size:20,
              totalElements:1,
              totalPages:1,
              first:true,
              last:true,
            }
          }
          return {
            ...current,
            items:[notification, ...current.items].slice(0, current.size),
            totalElements:current.totalElements + 1,
            totalPages:Math.ceil((current.totalElements + 1) / current.size),
            last:(current.totalElements + 1) <= current.size,
          }
        },
      )
      if (!alreadyKnown && !notification.read) {
        queryClient.setQueryData<UnreadNotificationCountResource>(
          notificationUnreadKey,
          current => ({ unreadCount:(current?.unreadCount ?? 0) + 1 }),
        )
      }
      if (notification.resourceType === 'APPOINTMENT') {
        void queryClient.invalidateQueries({
          queryKey:patientMode
            ? ['my-patient-appointments', patientRegistrationId] : ['appointments', organisationId],
        })
        if (patientMode) void queryClient.invalidateQueries({ queryKey:['my-patient-slots', patientRegistrationId] })
      }
      if (notification.resourceType === 'REFERRAL') {
        void queryClient.invalidateQueries({ queryKey:['referrals', userId, organisationId] })
        void queryClient.invalidateQueries({ queryKey:['referral', userId, organisationId, notification.resourceId] })
      }
    }
    const realtimeClient = createRealtimeClient({
      onNotification:receiveNotification,
      onConnected:recoverInbox,
      onStateChange:state => {
        if (active) setConnection(state)
      },
      onError:message => {
        if (active) setRealtimeWarning(message)
      },
    })
    realtimeClient.start()
    return () => {
      active = false
      realtimeClient.stop()
    }
  }, [
    createRealtimeClient,
    liveEnabled,
    notificationUnreadKey,
    notificationsKey,
    organisationId,
    queryClient,
    userId,
    patientMode,
    patientRegistrationId,
  ])

  const markRead = useMutation({
    mutationFn:(notificationId: string) => service.markRead(notificationId),
    onMutate:notificationId => {
      void queryClient.cancelQueries({ queryKey:notificationsKey })
      void queryClient.cancelQueries({ queryKey:notificationUnreadKey })
      const previousInbox = queryClient.getQueryData<NotificationPageResource>(
        notificationsKey,
      )
      const previousUnread = queryClient
        .getQueryData<UnreadNotificationCountResource>(notificationUnreadKey)
      const target = previousInbox?.items.find(item => item.id === notificationId)
      if (!target || target.read) {
        return { previousInbox, previousUnread, changed:false }
      }

      const seenAt = new Date().toISOString()
      queryClient.setQueryData<NotificationPageResource>(
        notificationsKey,
        current => current ? {
          ...current,
          items:current.items.map(item => item.id === notificationId
            ? { ...item, read:true, readAt:seenAt }
            : item),
        } : current,
      )
      queryClient.setQueryData<UnreadNotificationCountResource>(
        notificationUnreadKey,
        current => ({
          unreadCount:Math.max(0, (current?.unreadCount ?? 1) - 1),
        }),
      )
      return { previousInbox, previousUnread, changed:true }
    },
    onSuccess:updated => {
      queryClient.setQueryData<NotificationPageResource>(
        notificationsKey,
        current => current ? {
          ...current,
          items:current.items.map(item =>
            item.id === updated.id ? updated : item),
        } : current,
      )
    },
    onError:(_error, _notificationId, context) => {
      if (!context?.changed) return
      queryClient.setQueryData(notificationsKey, context.previousInbox)
      queryClient.setQueryData(notificationUnreadKey, context.previousUnread)
    },
  })

  const markAllRead = useMutation({
    mutationFn:() => service.markAllRead(),
    onSuccess:result => {
      queryClient.setQueryData<NotificationPageResource>(
        notificationsKey,
        current => current ? {
          ...current,
          items:current.items.map(item => ({
            ...item,
            read:true,
            readAt:item.readAt || result.readAt,
          })),
        } : current,
      )
      queryClient.setQueryData<UnreadNotificationCountResource>(
        notificationUnreadKey,
        { unreadCount:0 },
      )
    },
  })

  const openNotification = (notification: NotificationResource) => {
    if (!notification.read && !markRead.isPending) {
      markRead.mutate(notification.id)
    }
    if (notification.resourceType === 'APPOINTMENT' && navigationTargets.appointment) {
      navigate(navigationTargets.appointment)
      setOpen(false)
    } else if (notification.resourceType === 'CONVERSATION' && navigationTargets.conversation) {
      navigate(navigationTargets.conversation)
      setOpen(false)
    } else if (notification.resourceType === 'REFERRAL' && navigationTargets.referral) {
      navigate(navigationTargets.referral)
      setOpen(false)
    }
  }

  const unreadCount = unreadQuery.data?.unreadCount ?? 0
  const notifications = inboxQuery.data?.items ?? []
  const mutationError = markRead.error || markAllRead.error

  return <div className="doctor-notification-center" ref={panelRef}>
    <button
      className="icon-button notification-trigger"
      aria-label={unreadCount
        ? `Notifications, ${unreadCount} unread`
        : 'Notifications'}
      aria-expanded={open}
      aria-controls="doctor-notification-panel"
      onClick={() => setOpen(value => !value)}
    >
      <Bell/>
      {unreadCount > 0&&<span className="notification-trigger__badge">
        {unreadCount > 99 ? '99+' : unreadCount}
      </span>}
    </button>

    {open&&<section
      id="doctor-notification-panel"
      className="doctor-notification-panel"
      role="dialog"
      aria-label="Notifications"
    >
      <header>
        <div>
          <p className="eyebrow">{patientMode ? 'Your private appointment inbox' : 'Private organisation inbox'}</p>
          <h2>Notifications</h2>
        </div>
        <button
          className="icon-button"
          aria-label="Close notifications"
          onClick={() => setOpen(false)}
        ><X/></button>
      </header>
      <div className={`notification-connection notification-connection--${connection}`}>
        <Radio/>
        <span>{connectionLabel[connection]}</span>
      </div>
      {realtimeWarning&&<p className="notification-warning" role="status">
        {realtimeWarning} Inbox recovery remains available.
      </p>}
      <div className="notification-panel-actions">
        <span>{unreadQuery.isError ? 'Unread count unavailable' : `${unreadCount} unread`}</span>
        <button
          type="button"
          disabled={!unreadCount || markAllRead.isPending}
          onClick={() => markAllRead.mutate()}
        >
          {markAllRead.isPending
            ? <LoaderCircle className="spin"/>
            : <CheckCheck/>}
          Mark all read
        </button>
      </div>
      {unreadQuery.isError&&<p className="notification-warning" role="alert">Unread count could not be loaded. <button type="button" onClick={() => unreadQuery.refetch()}>Retry count</button></p>}
      {inboxQuery.data && inboxQuery.data.totalElements > 20 && <p className="notification-warning">Showing your latest 20 notifications.</p>}

      {!liveEnabled&&<div className="notification-panel-state">
        <Bell/><strong>Live inbox paused in role preview.</strong>
        <span>Use a real authenticated session to load private notifications.</span>
      </div>}
      {liveEnabled&&inboxQuery.isPending&&<div className="notification-panel-state">
        <LoaderCircle className="spin"/>Loading your private inbox…
      </div>}
      {liveEnabled&&inboxQuery.isError&&<div className="notification-panel-state notification-panel-state--error">
        <p>{apiErrorMessage(inboxQuery.error, 'Notifications could not be loaded.')}</p>
        <button type="button" onClick={() => inboxQuery.refetch()}>
          <RefreshCw/>Retry
        </button>
      </div>}
      {liveEnabled&&!inboxQuery.isPending&&!inboxQuery.isError&&<div className="notification-list">
        {notifications.map(notification => <button
          type="button"
          className={notification.read ? '' : 'is-unread'}
          key={notification.id}
          onClick={() => openNotification(notification)}
        >
          <span className="notification-list__icon">{notification.resourceType === 'CONVERSATION'
            ? <MessageSquare/>
            : notification.resourceType === 'REFERRAL' ? <Share2/> : <CalendarClock/>}</span>
          <span>
            <strong>{notificationTitle(notification)}</strong>
            <small>{formatAppointmentTime(notification)}</small>
            <em>{notification.appointmentLocationLabel || 'Participant-only channel'}</em>
          </span>
          {!notification.read&&<i aria-label="Unread"/>}
        </button>)}
        {!notifications.length&&<div className="notification-panel-state">
          <Bell/><strong>You are all caught up.</strong>
          <span>{patientMode ? 'Updates to your appointments will appear here.' : 'New appointment, referral and secure-message activity will appear here.'}</span>
        </div>}
      </div>}
      {mutationError&&<p className="notification-warning" role="alert">
        {apiErrorMessage(mutationError, 'The read status could not be updated.')}
      </p>}
    </section>}
  </div>
}
