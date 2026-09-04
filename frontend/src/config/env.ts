const normalizeBaseUrl = (value: string | undefined) => (value || '/api').replace(/\/$/, '')

export const env = {
  apiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL),
  appName: import.meta.env.VITE_APP_NAME || 'Sahha',
  useMocks: import.meta.env.VITE_USE_MOCKS !== 'false',
  useAuthMocks: import.meta.env.VITE_USE_AUTH_MOCKS !== 'false',
  requestTimeoutMs: Number(import.meta.env.VITE_REQUEST_TIMEOUT_MS || 12_000),
} as const
