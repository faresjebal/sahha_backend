import { randomUUID } from 'node:crypto'
import { DEMO_COOKIES } from './app-runtime.mjs'

export const GATEWAY = 'http://127.0.0.1:8079'
export function gatewayPath(value) {
  if (typeof value !== 'string' || !value.startsWith('/api/v1/') || /[\\\r\n#]/.test(value)
    || value.split(/[/?]/).includes('..') || /%2e|%2f|%5c/i.test(value)) throw new Error('Only pinned Gateway API paths are allowed')
  return GATEWAY + value
}
export class GatewayClient {
  constructor(fetcher = fetch) { this.fetcher = fetcher; this.cookies = new Map(); this.csrf = null }
  cookieHeader() { return [...this.cookies].map(([name,value]) => `${name}=${value}`).join('; ') }
  async request(route, method = 'GET', body, expected = [200], extra = {}) {
    const url = gatewayPath(route)
    if (!['GET','POST','PUT','DELETE'].includes(method)) throw new Error('Unsupported demo HTTP method')
    if (method !== 'GET' && !this.csrf) await this.refreshCsrf()
    const response = await this.fetcher(url, { method, redirect:'error', cache:'no-store', signal:AbortSignal.timeout(20000),
      headers:{ Accept:'application/json','X-Request-ID':`synthetic-${randomUUID()}`, Cookie:this.cookieHeader(),
        ...(method !== 'GET' ? { 'Content-Type':'application/json','X-XSRF-TOKEN':this.csrf } : {}), ...extra },
      ...(body === undefined ? {} : {body:JSON.stringify(body)}) })
    for (const cookie of response.headers.getSetCookie()) {
      const [pair, ...attributes] = cookie.split(';'), index = pair.indexOf('=')
      const name = pair.slice(0,index), value = pair.slice(index + 1)
      if (!Object.values(DEMO_COOKIES).includes(name)) throw new Error('Unexpected cookie namespace in synthetic session')
      if (!value || attributes.some(attribute => /^\s*max-age=0$/i.test(attribute))) this.cookies.delete(name)
      else this.cookies.set(name,value)
      if (name === DEMO_COOKIES.csrf) this.csrf = value ? decodeURIComponent(value) : null
    }
    if (!expected.includes(response.status)) throw Object.assign(new Error(`Synthetic Gateway ${method} ${route.split('?')[0]} returned ${response.status}; response body suppressed`), {status:response.status})
    if (response.status === 204) return null
    try { return await response.json() }
    catch { throw new Error('Invalid synthetic Gateway JSON; response body suppressed') }
  }
  async refreshCsrf() {
    const response = await this.request('/api/v1/auth/csrf')
    if (typeof response.token !== 'string' || response.headerName !== 'X-XSRF-TOKEN') throw new Error('Unexpected CSRF contract')
    this.csrf = response.token
  }
  async login(account) {
    await this.refreshCsrf()
    const session = await this.request('/api/v1/auth/login','POST',{ email:account.email,password:account.password,deviceName:'Isolated synthetic seed' })
    await this.refreshCsrf()
    return session
  }
  async select(organisationId) {
    const result = await this.request('/api/v1/auth/active-organisation','POST',{organisationId})
    await this.refreshCsrf()
    return result
  }
  dispose() { this.cookies.clear(); this.csrf = null }
}
