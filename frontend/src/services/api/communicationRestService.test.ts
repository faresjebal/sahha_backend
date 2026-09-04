import { beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import { httpClient } from './httpClient'
import { communicationRestService } from './communicationRestService'

const response = (body:unknown, status = 200) => new Response(
  JSON.stringify(body), { status, headers:{ 'Content-Type':'application/json' } },
)

describe('Gateway communication REST integration', () => {
  beforeEach(() => {
    httpClient.invalidateCsrfToken()
    vi.restoreAllMocks()
  })

  it('loads only the active organisation collaboration directory and conversations', async () => {
    const organisationId = '12ef57c8-0d93-4ba6-bafd-cb4f3ea61ab6'
    const emptyPage = { content:[], page:0, size:100, totalElements:0, totalPages:0 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(response({ ...emptyPage, size:50 }))
    vi.stubGlobal('fetch', fetchMock)

    await communicationRestService.doctors(organisationId)
    await communicationRestService.conversations()

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${env.apiBaseUrl}/organisations/${organisationId}/collaboration-doctors?page=0&size=100`,
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${env.apiBaseUrl}/conversations?page=0&size=50`,
    )
  })

  it('uses CSRF and request identifiers for conversation and message retries', async () => {
    const conversationId = '75f42e40-75c5-45ab-bf7a-13379da9dfc4'
    const conversationRequestId = 'df04a7e0-36c0-42b4-b49a-35af3e700564'
    const messageRequestId = '28b65883-901c-42c4-bb8d-1ea01a97a042'
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({
        headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'communication-csrf',
      }))
      .mockResolvedValueOnce(response({ id:conversationId }, 201))
      .mockResolvedValueOnce(response({ id:'9d996015-d1a7-4c87-aec8-3910132f3bd3' }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await communicationRestService.create({
      conversationRequestId,
      recipientUserId:'7fd67d7c-ecef-42ce-bc77-6e92f6b6a06d',
      subject:'Synthetic care coordination',
    })
    await communicationRestService.send(conversationId, {
      messageRequestId, body:'Synthetic message',
    })

    const createOptions = fetchMock.mock.calls[1][1] as RequestInit
    const sendOptions = fetchMock.mock.calls[2][1] as RequestInit
    expect(JSON.parse(createOptions.body as string).conversationRequestId)
      .toBe(conversationRequestId)
    expect(JSON.parse(sendOptions.body as string).messageRequestId).toBe(messageRequestId)
    for (const options of [createOptions, sendOptions]) {
      expect(options).toMatchObject({
        method:'POST', credentials:'include',
        headers:expect.objectContaining({ 'X-XSRF-TOKEN':'communication-csrf' }),
      })
    }
  })
})
