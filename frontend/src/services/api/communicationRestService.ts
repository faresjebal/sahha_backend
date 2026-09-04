import type {
  CollaborationDoctorPageResource,
  ConversationMessagePageResource,
  ConversationMessageResource,
  ConversationPageResource,
  ConversationResource,
  CreateConversationCommand,
  SendConversationMessageCommand,
} from '../../models/communication'
import { httpClient } from './httpClient'

export const communicationRestService = {
  doctors(organisationId: string, page = 0, size = 100) {
    const query = new URLSearchParams({ page:String(page), size:String(size) })
    return httpClient.request<CollaborationDoctorPageResource>(
      `/organisations/${organisationId}/collaboration-doctors?${query}`,
    )
  },
  conversations(page = 0, size = 50) {
    const query = new URLSearchParams({ page:String(page), size:String(size) })
    return httpClient.request<ConversationPageResource>(`/conversations?${query}`)
  },
  create(command: CreateConversationCommand) {
    return httpClient.request<ConversationResource>('/conversations', {
      method:'POST', body:command,
    })
  },
  messages(conversationId: string, page = 0, size = 50) {
    const query = new URLSearchParams({ page:String(page), size:String(size) })
    return httpClient.request<ConversationMessagePageResource>(
      `/conversations/${conversationId}/messages?${query}`,
    )
  },
  send(conversationId: string, command: SendConversationMessageCommand) {
    return httpClient.request<ConversationMessageResource>(
      `/conversations/${conversationId}/messages`, { method:'POST', body:command },
    )
  },
  markRead(conversationId: string) {
    return httpClient.request<ConversationResource>(
      `/conversations/${conversationId}/read`, { method:'POST' },
    )
  },
}
