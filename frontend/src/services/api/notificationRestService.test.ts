import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import type { NotificationResource } from '../../models/notification'
import { httpClient } from './httpClient'
import { notificationRestService } from './notificationRestService'

const response = (body: unknown, status = 200) => new Response(
  JSON.stringify(body),
  { status, headers:{ 'Content-Type':'application/json' } },
)

const notification: NotificationResource = {
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
}

describe('Gateway notification REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('loads the active-organisation inbox and unread count', async () => {
    const page = {
      items:[notification],
      page:0,
      size:20,
      totalElements:1,
      totalPages:1,
      first:true,
      last:true,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(page))
      .mockResolvedValueOnce(response({ unreadCount:1 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(notificationRestService.list()).resolves.toEqual(page)
    await expect(notificationRestService.unreadCount()).resolves.toEqual({
      unreadCount:1,
    })

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/notifications?page=0&size=20`,
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/notifications/unread-count`,
    )
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      credentials:'include',
    })
  })

  it('marks one or all notifications read with the shared CSRF header', async () => {
    const readAt = '2026-08-12T04:05:00Z'
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN',
        parameterName:'_csrf',
        token:'notification-csrf-token',
      }))
      .mockResolvedValueOnce(response({
        ...notification,
        read:true,
        readAt,
      }))
      .mockResolvedValueOnce(response({ updatedCount:3, readAt }))
    vi.stubGlobal('fetch', fetchMock)

    await notificationRestService.markRead(notification.id)
    await expect(notificationRestService.markAllRead()).resolves.toEqual({
      updatedCount:3,
      readAt,
    })

    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/notifications/${notification.id}/read`,
    )
    expect(fetchMock.mock.calls[2][0]).toBe(
      `${env.apiBaseUrl}/notifications/read-all`,
    )
    for (const call of [fetchMock.mock.calls[1], fetchMock.mock.calls[2]]) {
      expect(call[1]).toMatchObject({
        method:'POST',
        credentials:'include',
        headers:expect.objectContaining({
          'X-XSRF-TOKEN':'notification-csrf-token',
        }),
      })
    }
  })
})
