import type { CreateReferralCommand, ReferralCommand, ReferralDirection, ReferralPageResource, ReferralResource } from '../../models/referral'
import { httpClient } from './httpClient'

export const referralRestService = {
  create(command:CreateReferralCommand) {
    return httpClient.request<ReferralResource>('/referrals', { method:'POST', body:command })
  },
  list(direction:ReferralDirection = 'ALL', page = 0, size = 20) {
    return httpClient.request<ReferralPageResource>(`/referrals?${new URLSearchParams({ direction, page:String(page), size:String(size) })}`)
  },
  get(id:string) {
    return httpClient.request<ReferralResource>(`/referrals/${encodeURIComponent(id)}`)
  },
  command(id:string, command:ReferralCommand) {
    const { action, ...body } = command
    return httpClient.request<ReferralResource>(`/referrals/${encodeURIComponent(id)}/${action}`, { method:'POST', body })
  },
}
