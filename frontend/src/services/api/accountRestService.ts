import { httpClient } from './httpClient'

export interface AccountResource {
  id: string
  email: string
  firstName: string
  lastName: string
  status: string
  emailVerified: boolean
}
export interface DeviceSessionResource {
  sessionId: string
  deviceName: string
  createdAt: string
  lastActivityAt: string
  idleExpiresAt: string
  absoluteExpiresAt: string
  current: boolean
}
export interface OwnAccountResource extends AccountResource {
  phoneNumber: string | null
  version: number
}
export const accountRestService = {
  me: () => httpClient.request<OwnAccountResource>('/auth/account'),
  updateProfile: (command: { firstName:string; lastName:string; phoneNumber:string|null; version:number }) => httpClient.request<OwnAccountResource>('/auth/account/profile', { method:'PUT', body:command }),
  sessions: () => httpClient.request<DeviceSessionResource[]>('/auth/sessions'),
  revokeSession: (id: string) => httpClient.request<void>(`/auth/sessions/${encodeURIComponent(id)}`, { method:'DELETE' }),
  find: (email: string) => httpClient.request<AccountResource>(`/auth/platform/accounts?${new URLSearchParams({ email })}`),
  changeStatus: (id: string, action: 'SUSPEND' | 'REACTIVATE' | 'DISABLE') => httpClient.request<void>(`/auth/platform/accounts/${encodeURIComponent(id)}/status`, { method:'PUT', body:{ action } }),
}
