import { env } from '../../config/env'
import { httpClient } from '../api/httpClient'

export interface RealtimeMessage {
  messageType: 'MESSAGE_CREATED'
  messageId: string
  conversationId: string
  senderUserId: string
  senderDisplayName: string
  body: string
  sentAt: string
}

export type CommunicationConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'offline'
type SocketLike = Pick<WebSocket, 'readyState' | 'send' | 'close'> & {
  onopen: ((event: Event) => void) | null
  onmessage: ((event: MessageEvent) => void) | null
  onerror: ((event: Event) => void) | null
  onclose: ((event: CloseEvent) => void) | null
}
type Factory = (url: string, protocols: string[]) => SocketLike

export interface CommunicationRealtimeCallbacks {
  onMessage(message: RealtimeMessage): void
  onConnected(): void
  onStateChange(state: CommunicationConnectionState): void
  onError?(message: string): void
}

export const communicationWebSocketUrl = (apiBaseUrl = env.apiBaseUrl, origin = window.location.origin) => {
  const url = new URL(apiBaseUrl, origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = `${url.pathname.replace(/\/$/, '')}/conversations/ws`
  url.search = ''
  return url.toString()
}

const stomp = (command: string, headers: Record<string, string> = {}) => `${command}\n${Object.entries(headers).map(([k,v]) => `${k}:${v}`).join('\n')}\n\n\0`
const valid = (value: unknown): value is RealtimeMessage => {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return item.messageType === 'MESSAGE_CREATED'
    && ['messageId','conversationId','senderUserId','senderDisplayName','body','sentAt'].every(key => typeof item[key] === 'string')
}

export class CommunicationRealtimeClient {
  private socket: SocketLike | null = null
  private stopped = true
  private generation = 0
  private reconnectTimer: number | null = null
  private attempt = 0
  constructor(private readonly callbacks: CommunicationRealtimeCallbacks, private readonly factory: Factory = (url, protocols) => new WebSocket(url, protocols)) {}

  start() { if (!this.stopped) return; this.stopped = false; this.generation++; this.attempt = 0; void this.connect(this.generation, false) }
  stop() { this.stopped = true; this.generation++; if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer); this.reconnectTimer = null; const socket = this.socket; this.socket = null; if (socket?.readyState === WebSocket.OPEN) socket.send(stomp('DISCONNECT')); socket?.close(1000, 'Client disconnected'); this.callbacks.onStateChange('offline') }
  private async connect(generation: number, retry: boolean) {
    this.callbacks.onStateChange(retry ? 'reconnecting' : 'connecting')
    try {
      const csrf = await httpClient.getCsrfToken(); if (this.stopped || generation !== this.generation) return
      const socket = this.factory(communicationWebSocketUrl(), ['v12.stomp','v11.stomp','v10.stomp']); this.socket = socket
      socket.onopen = () => socket.send(stomp('CONNECT', {'accept-version':'1.2,1.1,1.0','heart-beat':'10000,10000',[csrf.headerName]:csrf.token}))
      socket.onmessage = event => this.receive(String(event.data))
      socket.onerror = () => this.callbacks.onError?.('The realtime message connection reported a network error.')
      socket.onclose = event => { if (socket !== this.socket) return; this.socket = null; if (!this.stopped) this.schedule(generation, event.code === 1008 || event.code === 4001) }
    } catch { if (!this.stopped) this.schedule(generation, false) }
  }
  private receive(raw: string) {
    const end = raw.indexOf('\0'); const frame = end >= 0 ? raw.slice(0, end) : raw; const split = frame.indexOf('\n\n'); if (split < 0) return
    const command = frame.slice(0, split).split('\n')[0]; const body = frame.slice(split + 2)
    if (command === 'CONNECTED') { this.attempt = 0; this.socket?.send(stomp('SUBSCRIBE', {id:'communication-messages', destination:'/user/queue/messages', ack:'auto'})); this.callbacks.onStateChange('connected'); this.callbacks.onConnected() }
    else if (command === 'MESSAGE') { try { const value: unknown = JSON.parse(body); if (valid(value)) this.callbacks.onMessage(value); else throw new Error() } catch { this.callbacks.onError?.('A malformed realtime message was ignored.') } }
    else if (command === 'ERROR') { httpClient.invalidateCsrfToken(); this.callbacks.onError?.('The realtime message connection was rejected.') }
  }
  private schedule(generation: number, invalidate: boolean) { if (invalidate) httpClient.invalidateCsrfToken(); const delay = Math.min(30000, 1000 * (2 ** this.attempt++)); this.callbacks.onStateChange('reconnecting'); this.reconnectTimer = window.setTimeout(() => { this.reconnectTimer = null; void this.connect(generation, true) }, delay) }
}
