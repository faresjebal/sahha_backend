import type {
  CreateStaffInvitationCommand,
  StaffInvitationPageResource,
  StaffInvitationResource,
  StaffInvitationVersionCommand,
} from '../../models/organisation'
import { httpClient } from './httpClient'

const invitationPath = (invitationId: string) =>
  `/staff-invitations/${encodeURIComponent(invitationId)}`

const myInvitationPath = (invitationId: string) =>
  `/my/staff-invitations/${encodeURIComponent(invitationId)}`

export const staffInvitationRestService = {
  list(page = 0, size = 100) {
    return httpClient.request<StaffInvitationPageResource>(
      `/staff-invitations?page=${page}&size=${size}`,
    )
  },

  get(invitationId: string) {
    return httpClient.request<StaffInvitationResource>(
      invitationPath(invitationId),
    )
  },

  create(command: CreateStaffInvitationCommand) {
    return httpClient.request<StaffInvitationResource>('/staff-invitations', {
      method:'POST',
      body:command,
    })
  },

  renew(invitationId: string, command: StaffInvitationVersionCommand) {
    return httpClient.request<StaffInvitationResource>(
      `${invitationPath(invitationId)}/renew`,
      { method:'POST', body:command },
    )
  },

  revoke(invitationId: string, command: StaffInvitationVersionCommand) {
    return httpClient.request<StaffInvitationResource>(
      `${invitationPath(invitationId)}/revoke`,
      { method:'POST', body:command },
    )
  },

  listMine(page = 0, size = 100) {
    return httpClient.request<StaffInvitationPageResource>(
      `/my/staff-invitations?page=${page}&size=${size}`,
    )
  },

  accept(invitationId: string, command: StaffInvitationVersionCommand) {
    return httpClient.request<StaffInvitationResource>(
      `${myInvitationPath(invitationId)}/accept`,
      { method:'POST', body:command },
    )
  },

  reject(invitationId: string, command: StaffInvitationVersionCommand) {
    return httpClient.request<StaffInvitationResource>(
      `${myInvitationPath(invitationId)}/reject`,
      { method:'POST', body:command },
    )
  },
}
