import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => { vi.unstubAllEnvs(); vi.resetModules() })

describe('safe application configuration defaults', () => {
  it('uses real adapters and the Gateway API prefix when no local settings exist', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    vi.stubEnv('VITE_USE_MOCKS', '')
    vi.stubEnv('VITE_USE_AUTH_MOCKS', '')
    vi.stubEnv('VITE_APP_NAME', '')
    const { env } = await import('./env')
    expect(env.apiBaseUrl).toBe('/api/v1')
    expect(env.useMocks).toBe(false)
    expect(env.useAuthMocks).toBe(false)
    expect(env.appName).toBe('Sahha')
  })

  it('retains explicitly selected development mocks and configurable Gateway origin', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://gateway.example.test/api/v1/')
    vi.stubEnv('VITE_USE_MOCKS', 'true')
    vi.stubEnv('VITE_USE_AUTH_MOCKS', 'true')
    const { env } = await import('./env')
    expect(env.apiBaseUrl).toBe('https://gateway.example.test/api/v1')
    expect(env.useMocks).toBe(true)
    expect(env.useAuthMocks).toBe(true)
  })
})
