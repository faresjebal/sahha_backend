export interface ApiEnvelope<T> {
  data: T
  meta?: {
    requestId?: string
    timestamp: string
  }
}

export interface ApiProblem {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  requestId?: string
  fieldErrors?: Record<string, string>
  errors?: Record<string, string>
}

export interface PageRequest {
  page: number
  size: number
  sort?: string
  direction?: 'asc' | 'desc'
  query?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error' | 'stale'
