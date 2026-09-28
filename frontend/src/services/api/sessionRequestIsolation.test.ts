import { afterEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from './httpClient'

const json = (value: unknown) => new Response(JSON.stringify(value), { headers:{'Content-Type':'application/json'} })
describe('HTTP session-generation isolation', () => {
  afterEach(() => { httpClient.resetSessionRequests(); vi.unstubAllGlobals() })
  it('rejects a late protected response even when fetch ignores cancellation', async () => {
    let finish!: (response:Response) => void
    const fetchMock = vi.fn().mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    const request = httpClient.request('/conversations')
    const assertion = expect(request).rejects.toBeDefined()
    httpClient.resetSessionRequests()
    expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true)
    finish(json({ content:['previous person'] }))
    await assertion
  })
  it('never submits a queued mutation using a new session after its old CSRF lookup finishes', async () => {
    let finish!: (response:Response) => void
    const fetchMock = vi.fn().mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    const request = httpClient.request('/consultations', { method:'POST', body:{ appointmentId:'synthetic' } })
    const assertion = expect(request).rejects.toBeDefined()
    httpClient.resetSessionRequests()
    finish(json({ headerName:'X-XSRF-TOKEN', token:'old-token', parameterName:'_csrf' }))
    await assertion
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
  it('rejects a late private download after the session changes', async () => {
    let finish!: (response:Response) => void
    vi.stubGlobal('fetch', vi.fn().mockReturnValueOnce(new Promise(resolve => { finish = resolve })))
    const request = httpClient.requestBlob('/files/synthetic/content')
    const assertion = expect(request).rejects.toBeDefined()
    httpClient.resetSessionRequests()
    finish(new Response('synthetic private bytes'))
    await assertion
  })
})
