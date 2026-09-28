import { afterEach, expect, it, vi } from 'vitest'
import { httpClient } from './httpClient'
import { patientNotificationRestService } from './patientNotificationRestService'

afterEach(()=>vi.restoreAllMocks())
it('scopes every operation to the selected own registration and encodes identifiers', async () => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValue({})
  const service = patientNotificationRestService('registration/one')
  await service.list(2, 10)
  await service.unreadCount()
  await service.markRead('notification/one')
  await service.markAllRead()
  const base = '/notifications/patient/registrations/registration%2Fone'
  expect(request.mock.calls).toEqual([
    [base + '?page=2&size=10'], [base + '/unread-count'],
    [base + '/notification%2Fone/read', { method:'POST' }], [base + '/read-all', { method:'POST' }],
  ])
})
