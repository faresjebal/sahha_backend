import { env } from '../../config/env'
import type { ApiEnvelope, ApiProblem } from '../../models/api'
import { ApiError } from './ApiError'

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  rawBody?: BodyInit
  timeoutMs?: number
  csrf?: boolean
}

export interface BlobRequestOptions extends Omit<RequestInit, 'body'> {
  timeoutMs?: number
}

const requestUrl = (path:string) => {
  const normalizedPath = env.apiBaseUrl.endsWith('/api/v1')
    && path.startsWith('/api/v1/')
    ? path.slice('/api/v1'.length)
    : path
  return `${env.apiBaseUrl}${normalizedPath}`
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

const parseBlobResponse = async (response:Response):Promise<Blob> => {
  if (response.ok) return response.blob()
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('json') ? await response.json() : undefined
  const problem:ApiProblem = payload || {
    type:'about:blank',
    title:'Request failed',
    status:response.status,
    detail:response.statusText,
  }
  throw new ApiError(problem)
}

export interface CsrfTokenResource {
  headerName: string
  parameterName: string
  token: string
}

let csrfToken: CsrfTokenResource | null = null
let csrfRequest: Promise<CsrfTokenResource> | null = null
let sessionGeneration = 0
const sessionControllers = new Set<AbortController>()
const assertCurrentSession = (generation: number) => {
  if (generation !== sessionGeneration) throw new DOMException('Session changed', 'AbortError')
}

const invalidateCsrfToken = () => {
  csrfToken = null
  csrfRequest = null
}

const loadCsrfToken = () => {
  if (csrfToken) return Promise.resolve(csrfToken)
  if (csrfRequest) return csrfRequest
  const controller = new AbortController()
  const generation = sessionGeneration
  sessionControllers.add(controller)
  const timeout = window.setTimeout(() => controller.abort(), env.requestTimeoutMs)
  csrfRequest = fetch(requestUrl('/auth/csrf'), {
    method: 'GET',
    credentials: 'include',
    signal:controller.signal,
    headers: { Accept:'application/json' },
  })
    .then(parseResponse<CsrfTokenResource>)
    .then(resource => {
      assertCurrentSession(generation)
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
      sessionControllers.delete(controller)
      if (generation === sessionGeneration) csrfRequest = null
    })
  return csrfRequest
}

const requiresCsrf = (method: string | undefined) =>
  !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method || 'GET').toUpperCase())

export const httpClient = {
  invalidateCsrfToken,
  resetSessionRequests() {
    sessionGeneration++
    for (const controller of sessionControllers) controller.abort()
    sessionControllers.clear()
    invalidateCsrfToken()
  },

  getCsrfToken() {
    return loadCsrfToken()
  },

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const controller = new AbortController()
    const generation = sessionGeneration
    sessionControllers.add(controller)
    const timeout = window.setTimeout(() => controller.abort(), options.timeoutMs || env.requestTimeoutMs)
    try {
      const {
        body,
        rawBody,
        timeoutMs: _timeoutMs,
        csrf = true,
        ...requestOptions
      } = options
      if (body !== undefined && rawBody !== undefined) {
        throw new TypeError('Use either a JSON body or a raw body, not both.')
      }
      const csrfProtected = csrf && requiresCsrf(requestOptions.method)
      let mayRetryWithFreshCsrf = csrfProtected

      while (true) {
        try {
          const csrfResource = csrfProtected ? await loadCsrfToken() : null
          assertCurrentSession(generation)
          const result = await parseResponse<T>(await fetch(requestUrl(path), {
            ...requestOptions,
            credentials: 'include',
            signal: controller.signal,
            headers: {
              Accept: 'application/json',
              ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
              ...(csrfResource ? { [csrfResource.headerName]:csrfResource.token } : {}),
              ...requestOptions.headers,
            },
            body: rawBody ?? (body !== undefined ? JSON.stringify(body) : undefined),
          }))
          assertCurrentSession(generation)
          return result
        } catch (error) {
          assertCurrentSession(generation)
          if (
            mayRetryWithFreshCsrf
            && error instanceof ApiError
            && error.problem.status === 403
          ) {
            mayRetryWithFreshCsrf = false
            invalidateCsrfToken()
            continue
          }
          throw error
        }
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new ApiError({ type: 'timeout', title: 'Request timed out', status: 408, detail: 'The service did not respond in time.' })
      }
      throw error
    } finally {
      window.clearTimeout(timeout)
      sessionControllers.delete(controller)
    }
  },

  async requestBlob(
    path:string,
    options:BlobRequestOptions = {},
  ):Promise<Blob> {
    const controller = new AbortController()
    const generation = sessionGeneration
    sessionControllers.add(controller)
    const timeout = window.setTimeout(
      () => controller.abort(),
      options.timeoutMs || env.requestTimeoutMs,
    )
    try {
      const { timeoutMs: _timeoutMs, ...requestOptions } = options
      const result = await parseBlobResponse(await fetch(requestUrl(path), {
        ...requestOptions,
        method:requestOptions.method || 'GET',
        credentials:'include',
        signal:controller.signal,
        headers:{
          Accept:'application/octet-stream',
          ...requestOptions.headers,
        },
      }))
      assertCurrentSession(generation)
      return result
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new ApiError({
          type:'timeout',
          title:'Request timed out',
          status:408,
          detail:'The file service did not respond in time.',
        })
      }
      throw error
    } finally {
      window.clearTimeout(timeout)
      sessionControllers.delete(controller)
    }
  },
}
