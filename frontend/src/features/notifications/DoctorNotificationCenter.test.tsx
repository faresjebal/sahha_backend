import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  NotificationPageResource,
  NotificationResource,
} from '../../models/notification'
import type { NotificationRealtimeCallbacks } from '../../services/realtime/notificationRealtimeClient'
import { DoctorNotificationCenter } from './DoctorNotificationCenter'

const authState = vi.hoisted(() => ({
  session:{
    user:{
      id:'doctor-user-1',
      organizationId:'organisation-1',
    },
  },
}))

vi.mock('../../app/auth/AuthProvider', () => ({
  useAuth:() => authState,
}))

const notification: NotificationResource = {
  id:'notification-1',
  notificationType:'APPOINTMENT_REQUESTED',
  resourceType:'APPOINTMENT',
  resourceId:'appointment-1',
  appointmentStatus:'REQUESTED',
  appointmentStartsAt:'2030-01-14T09:00:00Z',
  appointmentEndsAt:'2030-01-14T09:30:00Z',
  appointmentTimeZone:'UTC',
  appointmentLocationLabel:'Synthetic consultation room',
  resourceVersion:0,
  eventOccurredAt:'2026-08-12T04:00:00Z',
  createdAt:'2026-08-12T04:00:01Z',
  read:false,
  readAt:null,
}

const page = (items: NotificationResource[]): NotificationPageResource => ({
  items,
  page:0,
  size:20,
  totalElements:items.length,
  totalPages:items.length ? 1 : 0,
  first:true,
  last:true,
})

const RouteProbe = () => {
  const location = useLocation()
  return <output aria-label="Current route">{location.pathname}</output>
}

describe('Doctor notification center', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    authState.session.user.organizationId = 'organisation-1'
  })

  const setup = () => {
    const service = {
      list:vi.fn().mockResolvedValue(page([notification])),
      unreadCount:vi.fn().mockResolvedValue({ unreadCount:1 }),
      markRead:vi.fn().mockResolvedValue({
        ...notification,
        read:true,
        readAt:'2026-08-12T04:05:00Z',
      }),
      markAllRead:vi.fn().mockResolvedValue({
        updatedCount:1,
        readAt:'2026-08-12T04:05:00Z',
      }),
    }
    const clients: Array<{ start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn> }> = []
    const callbacks: NotificationRealtimeCallbacks[] = []
    const createRealtimeClient = vi.fn((next: NotificationRealtimeCallbacks) => {
      callbacks.push(next)
      const client = { start:vi.fn(), stop:vi.fn() }
      clients.push(client)
      return client
    })
    const queryClient = new QueryClient({
      defaultOptions:{ queries:{ retry:false }, mutations:{ retry:false } },
    })
    const Wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/doctor/overview']}>
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    )
    const view = render(<>
      <DoctorNotificationCenter
        service={service}
        createRealtimeClient={createRealtimeClient}
        enabled
      />
      <RouteProbe/>
    </>, { wrapper:Wrapper })
    return {
      ...view,
      callbacks,
      clients,
      createRealtimeClient,
      queryClient,
      service,
    }
  }

  it('recovers via REST and merges a validated realtime notification', async () => {
    const { callbacks, service } = setup()

    const trigger = await screen.findByRole('button', {
      name:'Notifications, 1 unread',
    })
    expect(service.list).toHaveBeenCalledWith(0, 20)
    fireEvent.click(trigger)
    expect(screen.getByText('New appointment request')).toBeInTheDocument()

    act(() => {
      callbacks[0].onStateChange('connected')
      callbacks[0].onConnected()
    })
    await waitFor(() => expect(service.list.mock.calls.length).toBeGreaterThan(1))
    expect(screen.getByText('Live updates connected')).toBeInTheDocument()

    const checkedIn: NotificationResource = {
      ...notification,
      id:'notification-2',
      notificationType:'PATIENT_CHECKED_IN',
      appointmentStatus:'CHECKED_IN',
      resourceVersion:1,
    }
    act(() => callbacks[0].onNotification({
      messageType:'NOTIFICATION_CREATED',
      notification:checkedIn,
    }))

    expect(await screen.findByText('Patient checked in')).toBeInTheDocument()
    expect(screen.getByRole('button', {
      name:'Notifications, 2 unread',
    })).toBeInTheDocument()
  })

  it('marks notifications read and opens the real appointment route', async () => {
    const { service } = setup()
    fireEvent.click(await screen.findByRole('button', {
      name:'Notifications, 1 unread',
    }))
    fireEvent.click(screen.getByRole('button', {
      name:/New appointment request/,
    }))

    await waitFor(() => expect(service.markRead).toHaveBeenCalledWith(
      notification.id,
    ))
    expect(screen.getByRole('button', { name:'Notifications' }))
      .toBeInTheDocument()
    expect(screen.getByLabelText('Current route')).toHaveTextContent(
      '/doctor/appointments',
    )
  })

  it('merges a secure-message alert and opens the messenger route', async () => {
    const { callbacks, service } = setup()
    fireEvent.click(await screen.findByRole('button', {
      name:'Notifications, 1 unread',
    }))
    const messageNotification: NotificationResource = {
      id:'notification-message-1',
      notificationType:'MESSAGE_RECEIVED',
      resourceType:'CONVERSATION',
      resourceId:'conversation-1',
      appointmentStatus:null,
      appointmentStartsAt:null,
      appointmentEndsAt:null,
      appointmentTimeZone:null,
      appointmentLocationLabel:null,
      resourceVersion:2,
      eventOccurredAt:'2026-09-04T01:00:00Z',
      createdAt:'2026-09-04T01:00:01Z',
      read:false,
      readAt:null,
    }
    service.markRead.mockResolvedValueOnce({
      ...messageNotification,
      read:true,
      readAt:'2026-09-04T01:01:00Z',
    })
    act(() => callbacks[0].onNotification({
      messageType:'NOTIFICATION_CREATED',
      notification:messageNotification,
    }))

    fireEvent.click(await screen.findByRole('button', {
      name:/New secure message/,
    }))

    await waitFor(() => expect(service.markRead)
      .toHaveBeenCalledWith(messageNotification.id))
    expect(screen.getByLabelText('Current route')).toHaveTextContent(
      '/doctor/messages',
    )
  })

  it('restores the unread badge when marking a notification read fails', async () => {
    const { service } = setup()
    service.markRead.mockRejectedValueOnce(new Error('Synthetic failure'))

    fireEvent.click(await screen.findByRole('button', {
      name:'Notifications, 1 unread',
    }))
    fireEvent.click(screen.getByRole('button', {
      name:/New appointment request/,
    }))

    await waitFor(() => expect(screen.getByRole('button', {
      name:'Notifications, 1 unread',
    })).toBeInTheDocument())
  })

  it('tears down the old socket when the active organisation changes', async () => {
    const {
      clients,
      createRealtimeClient,
      rerender,
      service,
    } = setup()
    await waitFor(() => expect(createRealtimeClient).toHaveBeenCalledOnce())
    expect(clients[0].start).toHaveBeenCalledOnce()

    authState.session.user.organizationId = 'organisation-2'
    rerender(<>
      <DoctorNotificationCenter
        service={service}
        createRealtimeClient={createRealtimeClient}
        enabled
      />
      <RouteProbe/>
    </>)

    await waitFor(() => expect(createRealtimeClient).toHaveBeenCalledTimes(2))
    expect(clients[0].stop).toHaveBeenCalledOnce()
    expect(clients[1].start).toHaveBeenCalledOnce()
  })
})
