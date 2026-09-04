import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/auth/AuthProvider'
import { communicationRestService } from '../../services/api/communicationRestService'

export function DoctorConversationUnreadBadge() {
  const auth = useAuth()
  const organisationId = auth.session?.user.organizationId || ''
  const query = useQuery({
    queryKey:['conversations', organisationId],
    queryFn:() => communicationRestService.conversations(),
    enabled:Boolean(organisationId),
  })
  const count = query.data?.content.reduce(
    (total, conversation) => total + conversation.unreadCount,
    0,
  ) || 0
  return count > 0
    ? <b aria-label={`${count} unread messages`}>{count > 99 ? '99+' : count}</b>
    : null
}
