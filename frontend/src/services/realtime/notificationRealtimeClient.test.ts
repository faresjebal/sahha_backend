import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RealtimeNotificationMessage } from '../../models/notification'
import { httpClient } from '../api/httpClient'
import {
  NotificationRealtimeClient,
  notificationWebSocketUrl,
  type NotificationConnectionState,
} from './notificationRealtimeClient'

class FakeSocket {
  readyState = 0
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  readonly sent: string[] = []
  readonly close = vi.fn((code?: number) => {
    this.readyState = 3
    this.onclose?.({ code } as CloseEvent)
  })

  send(data: string) {
    this.sent.push(data)
  }

  open() {
    this.readyState = 1
    this.onopen?.(new Event('open'))
  }

  receive(data: string) {
    this.onmessage?.({ data } as MessageEvent)
  }

  serverClose(code = 1006) {
    this.readyState = 3
    this.onclose?.({ code } as CloseEvent)
  }
}

const message: RealtimeNotificationMessage = {
  messageType:'NOTIFICATION_CREATED',
  notification:{
    id:'f8177bb3-979d-4d83-a11a-18a5a1847ce6',
    notificationType:'APPOINTMENT_REQUESTED',
    resourceType:'APPOINTMENT',
    resourceId:'af08ed92-a705-4089-8924-ab820b544360',
    appointmentStatus:'REQUESTED',
    appointmentStartsAt:'2030-01-14T09:00:00Z',
    appointmentEndsAt:'2030-01-14T09:30:00Z',
    appointmentTimeZone:'Africa/Tunis',
    appointmentLocationLabel:'Synthetic consultation room',
    resourceVersion:0,
    eventOccurredAt:'2026-08-12T04:00:00Z',
    createdAt:'2026-08-12T04:00:01Z',
    read:false,
    readAt:null,
  },
}

describe('Notification STOMP-over-WebSocket client', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(httpClient, 'getCsrfToken').mockResolvedValue({
      headerName:'X-XSRF-TOKEN',
      parameterName:'_csrf',
      token:'realtime-csrf-token',
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('connects with CSRF, subscribes privately, and validates messages', async () => {
    const socket = new FakeSocket()
    const states: NotificationConnectionState[] = []
    const onNotification = vi.fn()
    const onConnected = vi.fn()
    const client = new NotificationRealtimeClient({
      onNotification,
      onConnected,
      onStateChange:state => states.push(state),
    }, {
      webSocketFactory:(url, protocols) => {
        expect(url).toBe(notificationWebSocketUrl())
        expect(protocols).toContain('v12.stomp')
        return socket
      },
      heartbeatMs:10_000,
    })

    client.start()
    await vi.waitFor(() => expect(httpClient.getCsrfToken).toHaveBeenCalled())
    socket.open()

    expect(socket.sent[0]).toContain('CONNECT\n')
    expect(socket.sent[0]).toContain('X-XSRF-TOKEN:realtime-csrf-token')
    socket.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0')
    expect(socket.sent[1]).toContain('destination:/user/queue/notifications')
    expect(onConnected).toHaveBeenCalledOnce()
    expect(states).toContain('connected')

    socket.receive(`MESSAGE\ndestination:/user/queue/notifications\n\n${JSON.stringify(message)}\0`)
    expect(onNotification).toHaveBeenCalledWith(message)

    client.stop()
    expect(socket.sent.at(-1)).toContain('DISCONNECT\n')
    expect(socket.close).toHaveBeenCalledWith(1000, 'Client disconnected')
  })

  it('reconnects with a fresh handshake and stops retries after teardown', async () => {
    const first = new FakeSocket()
    const second = new FakeSocket()
    const sockets = [first, second]
    const factory = vi.fn(() => sockets.shift()!)
    const client = new NotificationRealtimeClient({
      onNotification:vi.fn(),
      onConnected:vi.fn(),
      onStateChange:vi.fn(),
    }, {
      webSocketFactory:factory,
      baseReconnectDelayMs:100,
      maxReconnectDelayMs:100,
    })

    client.start()
    await vi.waitFor(() => expect(factory).toHaveBeenCalledTimes(1))
    first.serverClose()
    await vi.advanceTimersByTimeAsync(100)
    await vi.waitFor(() => expect(factory).toHaveBeenCalledTimes(2))
    expect(httpClient.getCsrfToken).toHaveBeenCalledTimes(2)

    client.stop()
    second.serverClose()
    await vi.advanceTimersByTimeAsync(500)
    expect(factory).toHaveBeenCalledTimes(2)
  })

  it('ignores malformed realtime payloads without exposing them to the UI', async () => {
    const socket = new FakeSocket()
    const onNotification = vi.fn()
    const onError = vi.fn()
    const client = new NotificationRealtimeClient({
      onNotification,
      onConnected:vi.fn(),
      onStateChange:vi.fn(),
      onError,
    }, { webSocketFactory:() => socket })

    client.start()
    await vi.waitFor(() => expect(httpClient.getCsrfToken).toHaveBeenCalled())
    socket.open()
    socket.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0')
    socket.receive('MESSAGE\ndestination:/user/queue/notifications\n\n{"messageType":"WRONG"}\0')

    expect(onNotification).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(
      'A malformed realtime notification was ignored.',
    )
    client.stop()
  })
})
