import { env } from '../../config/env'
import {
  isRealtimeNotificationMessage,
  type RealtimeNotificationMessage,
} from '../../models/notification'
import { httpClient } from '../api/httpClient'

export type NotificationConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'offline'

interface RealtimeSocket {
  readonly readyState: number
  onopen: ((event: Event) => void) | null
  onmessage: ((event: MessageEvent) => void) | null
  onerror: ((event: Event) => void) | null
  onclose: ((event: CloseEvent) => void) | null
  send(data: string): void
  close(code?: number, reason?: string): void
}

type WebSocketFactory = (
  url: string,
  protocols: string[],
) => RealtimeSocket

export interface NotificationRealtimeCallbacks {
  onNotification(message: RealtimeNotificationMessage): void
  onConnected(): void
  onStateChange(state: NotificationConnectionState): void
  onError?(message: string): void
}

export interface NotificationRealtimeOptions {
  patientRegistrationId?: string
  webSocketFactory?: WebSocketFactory
  baseReconnectDelayMs?: number
  maxReconnectDelayMs?: number
  heartbeatMs?: number
}

interface StompFrame {
  command: string
  headers: Record<string, string>
  body: string
}

const SOCKET_OPEN = 1
const STOMP_PROTOCOLS = ['v12.stomp', 'v11.stomp', 'v10.stomp']
const NOTIFICATION_DESTINATION = '/user/queue/notifications'

export const notificationWebSocketUrl = (
  apiBaseUrl = env.apiBaseUrl,
  origin = window.location.origin,
  patientRegistrationId?: string,
) => {
  const url = new URL(apiBaseUrl, origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = `${url.pathname.replace(/\/$/, '')}/notifications/ws`
  url.search = ''
  url.hash = ''
  if (patientRegistrationId) {
    url.pathname = url.pathname.replace(/\/notifications\/ws$/, '/notifications/patient/ws')
    url.searchParams.set('registrationId', patientRegistrationId)
  }
  return url.toString()
}

const unescapeHeader = (value: string) => value
  .replaceAll('\\r', '\r')
  .replaceAll('\\n', '\n')
  .replaceAll('\\c', ':')
  .replaceAll('\\\\', '\\')

const parseFrame = (rawFrame: string): StompFrame | null => {
  const normalized = rawFrame.replaceAll('\r\n', '\n').replace(/^\n+/, '')
  if (!normalized.trim()) return null
  const headerEnd = normalized.indexOf('\n\n')
  if (headerEnd < 0) return null
  const headerLines = normalized.slice(0, headerEnd).split('\n')
  const command = headerLines.shift()?.trim() || ''
  if (!command) return null
  const headers: Record<string, string> = {}
  for (const line of headerLines) {
    const separator = line.indexOf(':')
    if (separator < 0) continue
    const name = unescapeHeader(line.slice(0, separator))
    if (headers[name] !== undefined) continue
    headers[name] = unescapeHeader(line.slice(separator + 1))
  }
  return {
    command,
    headers,
    body:normalized.slice(headerEnd + 2),
  }
}

const frame = (
  command: string,
  headers: Record<string, string> = {},
  body = '',
) => `${command}\n${Object.entries(headers)
  .map(([name, value]) => `${name}:${value}`)
  .join('\n')}\n\n${body}\0`

export class NotificationRealtimeClient {
  private socket: RealtimeSocket | null = null
  private frameBuffer = ''
  private stopped = true
  private generation = 0
  private reconnectAttempt = 0
  private reconnectTimer: number | null = null
  private outgoingHeartbeatTimer: number | null = null
  private incomingHeartbeatTimer: number | null = null
  private lastServerActivityAt = 0

  private readonly webSocketFactory: WebSocketFactory
  private readonly baseReconnectDelayMs: number
  private readonly maxReconnectDelayMs: number
  private readonly heartbeatMs: number
  private readonly patientRegistrationId?: string

  constructor(
    private readonly callbacks: NotificationRealtimeCallbacks,
    options: NotificationRealtimeOptions = {},
  ) {
    this.webSocketFactory = options.webSocketFactory
      || ((url, protocols) => new WebSocket(url, protocols))
    this.baseReconnectDelayMs = options.baseReconnectDelayMs ?? 1_000
    this.maxReconnectDelayMs = options.maxReconnectDelayMs ?? 30_000
    this.heartbeatMs = options.heartbeatMs ?? 10_000
    this.patientRegistrationId = options.patientRegistrationId
  }

  start() {
    if (!this.stopped) return
    this.stopped = false
    this.generation += 1
    this.reconnectAttempt = 0
    void this.connect(this.generation, false)
  }

  stop() {
    if (this.stopped) return
    this.stopped = true
    this.generation += 1
    this.clearTimers()
    const socket = this.socket
    this.socket = null
    if (socket?.readyState === SOCKET_OPEN) {
      socket.send(frame('DISCONNECT', { receipt:'client-disconnect' }))
      socket.close(1000, 'Client disconnected')
    } else {
      socket?.close(1000, 'Client disconnected')
    }
    this.callbacks.onStateChange('offline')
  }

  private async connect(generation: number, reconnecting: boolean) {
    this.callbacks.onStateChange(reconnecting ? 'reconnecting' : 'connecting')
    try {
      const csrf = await httpClient.getCsrfToken()
      if (this.stopped || generation !== this.generation) return
      const socket = this.webSocketFactory(
        notificationWebSocketUrl(undefined, undefined, this.patientRegistrationId),
        STOMP_PROTOCOLS,
      )
      this.socket = socket
      this.frameBuffer = ''
      socket.onopen = () => {
        if (this.stopped || socket !== this.socket) return
        socket.send(frame('CONNECT', {
          'accept-version':'1.2,1.1,1.0',
          'heart-beat':`${this.heartbeatMs},${this.heartbeatMs}`,
          [csrf.headerName]:csrf.token,
        }))
      }
      socket.onmessage = event => this.receive(socket, String(event.data))
      socket.onerror = () => {
        if (socket !== this.socket) return
        this.callbacks.onError?.('The realtime connection reported a network error.')
      }
      socket.onclose = event => {
        if (socket !== this.socket) return
        this.socket = null
        this.stopHeartbeats()
        if (this.stopped) return
        if (event.code === 1008 || event.code === 4001) {
          httpClient.invalidateCsrfToken()
        }
        this.scheduleReconnect(generation)
      }
    } catch {
      if (this.stopped || generation !== this.generation) return
      this.callbacks.onError?.('The realtime security handshake could not start.')
      this.scheduleReconnect(generation)
    }
  }

  private receive(socket: RealtimeSocket, chunk: string) {
    if (socket !== this.socket || this.stopped) return
    this.lastServerActivityAt = Date.now()
    this.frameBuffer += chunk
    let end = this.frameBuffer.indexOf('\0')
    while (end >= 0) {
      const rawFrame = this.frameBuffer.slice(0, end)
      this.frameBuffer = this.frameBuffer.slice(end + 1)
      const parsed = parseFrame(rawFrame)
      if (parsed) this.handleFrame(socket, parsed)
      end = this.frameBuffer.indexOf('\0')
    }
    if (/^[\r\n]+$/.test(this.frameBuffer)) this.frameBuffer = ''
  }

  private handleFrame(socket: RealtimeSocket, received: StompFrame) {
    if (received.command === 'CONNECTED') {
      this.reconnectAttempt = 0
      socket.send(frame('SUBSCRIBE', {
        id:'doctor-notifications',
        destination:NOTIFICATION_DESTINATION,
        ack:'auto',
      }))
      this.startHeartbeats(socket, received.headers['heart-beat'])
      this.callbacks.onStateChange('connected')
      this.callbacks.onConnected()
      return
    }
    if (received.command === 'MESSAGE') {
      try {
        const payload: unknown = JSON.parse(received.body)
        if (!isRealtimeNotificationMessage(payload)) {
          throw new Error('Unexpected notification message shape')
        }
        this.callbacks.onNotification(payload)
      } catch {
        this.callbacks.onError?.('A malformed realtime notification was ignored.')
      }
      return
    }
    if (received.command === 'ERROR') {
      this.callbacks.onError?.(
        received.headers.message || 'The realtime server rejected the connection.',
      )
      httpClient.invalidateCsrfToken()
      socket.close(4001, 'STOMP error')
    }
  }

  private startHeartbeats(socket: RealtimeSocket, serverHeartbeat = '0,0') {
    this.stopHeartbeats()
    const [serverOutgoing = 0, serverIncoming = 0] = serverHeartbeat
      .split(',')
      .map(value => Number(value) || 0)
    const outgoingEvery = serverIncoming > 0 && this.heartbeatMs > 0
      ? Math.max(serverIncoming, this.heartbeatMs)
      : 0
    const incomingEvery = serverOutgoing > 0 && this.heartbeatMs > 0
      ? Math.max(serverOutgoing, this.heartbeatMs)
      : 0
    if (outgoingEvery > 0) {
      this.outgoingHeartbeatTimer = window.setInterval(() => {
        if (socket === this.socket && socket.readyState === SOCKET_OPEN) {
          socket.send('\n')
        }
      }, outgoingEvery)
    }
    if (incomingEvery > 0) {
      this.lastServerActivityAt = Date.now()
      this.incomingHeartbeatTimer = window.setInterval(() => {
        if (
          socket === this.socket
          && Date.now() - this.lastServerActivityAt > incomingEvery * 2.5
        ) {
          socket.close(4000, 'Heartbeat timeout')
        }
      }, incomingEvery)
    }
  }

  private scheduleReconnect(generation: number) {
    if (this.stopped || generation !== this.generation || this.reconnectTimer) {
      return
    }
    this.callbacks.onStateChange('reconnecting')
    const exponentialDelay = this.baseReconnectDelayMs
      * (2 ** this.reconnectAttempt)
    const delay = Math.min(exponentialDelay, this.maxReconnectDelayMs)
    this.reconnectAttempt += 1
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      void this.connect(generation, true)
    }, delay)
  }

  private stopHeartbeats() {
    if (this.outgoingHeartbeatTimer !== null) {
      window.clearInterval(this.outgoingHeartbeatTimer)
      this.outgoingHeartbeatTimer = null
    }
    if (this.incomingHeartbeatTimer !== null) {
      window.clearInterval(this.incomingHeartbeatTimer)
      this.incomingHeartbeatTimer = null
    }
  }

  private clearTimers() {
    this.stopHeartbeats()
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }
}
