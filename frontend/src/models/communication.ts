import type { PageResponse } from './api'

export interface CollaborationDoctorResource {
  membershipId: string
  organisationId: string
  userId: string
  displayName: string
  membershipVersion: number
}

export type CollaborationDoctorPageResource = PageResponse<CollaborationDoctorResource>

export interface ConversationParticipantResource {
  userId: string
  membershipId: string
  displayName: string
  joinedAt: string
  lastReadAt: string | null
}

export interface ConversationResource {
  id: string
  organisationId: string
  subject: string
  patientRegistrationId: string | null
  patientAccessGranted: false
  createdByUserId: string
  createdAt: string
  lastMessageAt: string
  version: number
  unreadCount: number
  participants: ConversationParticipantResource[]
}

export type ConversationPageResource = PageResponse<ConversationResource>

export interface ConversationMessageResource {
  id: string
  conversationId: string
  senderUserId: string
  senderDisplayName: string
  body: string
  sentAt: string
  attachments?:import('./messageAttachment').MessageAttachmentReference[]
}

export type ConversationMessagePageResource = PageResponse<ConversationMessageResource>

export interface CreateConversationCommand {
  conversationRequestId: string
  recipientUserId: string
  subject: string
  patientRegistrationId?: string
  sourceConsultationId?: string
}

export interface SendConversationMessageCommand {
  messageRequestId: string
  body: string
  attachmentIds?:string[]
}
