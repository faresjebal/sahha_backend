import { env } from '../../config/env'
import type { ApiEnvelope, ApiProblem } from '../../models/api'
import { ApiError } from './ApiError'

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  timeoutMs?: number
  csrf?: boolean
}

const parseResponse = async <T>(response: Response): Promise<T> => {
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : undefined
  if (!response.ok) {
    const problem: ApiProblem = payload || {
      type: 'about:blank',
      title: 'Request failed',
      status: response.status,
      detail: response.statusText,
    }
    throw new ApiError(problem)
  }
  return (payload as ApiEnvelope<T>)?.data ?? payload
}

interface CsrfTokenResource {
  headerName: string
  parameterName: string
  token: string
}

let csrfToken: CsrfTokenResource | null = null
let csrfRequest: Promise<CsrfTokenResource> | null = null

const loadCsrfToken = () => {
  if (csrfToken) return Promise.resolve(csrfToken)
  if (csrfRequest) return csrfRequest
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), env.requestTimeoutMs)
  csrfRequest = fetch(`${env.apiBaseUrl}/auth/csrf`, {
    method: 'GET',
    credentials: 'include',
    signal:controller.signal,
    headers: { Accept:'application/json' },
  })
    .then(parseResponse<CsrfTokenResource>)
    .then(resource => {
      if (!resource.headerName || !resource.token) {
        throw new ApiError({
          type:'urn:sahha:problem:invalid-csrf-response',
          title:'Security token unavailable',
          status:502,
          detail:'The authentication service returned an invalid security token.',
        })
      }
      csrfToken = resource
      return resource
    })
    .catch(error => {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new ApiError({
          type:'timeout',
          title:'Request timed out',
          status:408,
          detail:'API Gateway did not return a security token in time.',
        })
      }
      throw error
    })
    .finally(() => {
      window.clearTimeout(timeout)
      csrfRequest = null
    })
  return csrfRequest
}

const requiresCsrf = (method: string | undefined) =>
  !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method || 'GET').toUpperCase())

export const httpClient = {
  invalidateCsrfToken() {
    csrfToken = null
    csrfRequest = null
  },

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), options.timeoutMs || env.requestTimeoutMs)
    try {
      const { body, timeoutMs: _timeoutMs, csrf = true, ...requestOptions } = options
      const csrfResource = csrf && requiresCsrf(requestOptions.method)
        ? await loadCsrfToken()
        : null
      return await parseResponse<T>(await fetch(`${env.apiBaseUrl}${path}`, {
        ...requestOptions,
        credentials: 'include',
        signal: controller.signal,
        headers: {
          Accept: 'application/json',
          ...(body ? { 'Content-Type': 'application/json' } : {}),
          ...(csrfResource ? { [csrfResource.headerName]:csrfResource.token } : {}),
          ...requestOptions.headers,
        },
        body: body ? JSON.stringify(body) : undefined,
      }))
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new ApiError({ type: 'timeout', title: 'Request timed out', status: 408, detail: 'The service did not respond in time.' })
      }
      throw error
    } finally {
      window.clearTimeout(timeout)
    }
  },
}
