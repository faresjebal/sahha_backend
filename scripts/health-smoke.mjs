// Read-only native operational checks. No service start/stop, business calls or data writes.
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { selectServices } from './sahha.mjs'

export async function checkService(service, request = fetch, accessCookieName = 'SAHHA_ACCESS_TOKEN') {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(accessCookieName)) throw new Error('Invalid operational smoke cookie name')
  let checks = 0
  const probe = async (route, method = 'GET') => {
    const response = await request(`http://127.0.0.1:${service.port}${route}`, {
      method, redirect: 'error', signal: AbortSignal.timeout(5000),
      headers: { Authorization: 'Bearer synthetic-invalid-token',
        Cookie: `${accessCookieName}=synthetic-invalid-token; JSESSIONID=synthetic-invalid-session` },
    })
    assert.equal(response.headers.has('set-cookie'), false, 'Operational request created a cookie')
    checks++
    return response
  }
  for (const group of ['liveness', 'readiness']) {
    const response = await probe(`/actuator/health/${group}`)
    assert.equal(response.status, 200, `${group} is not UP`)
    const body = await response.json()
    // Do not interpolate untrusted response bodies into failure messages/logs.
    assert.equal(body.status, 'UP', `${group} is not UP`)
    assert.equal(Object.keys(body).length, 1, 'Probe exposed more than status')
  }
  const aggregate = await probe('/actuator/health')
  assert.equal([200, 503].includes(aggregate.status), true, 'Aggregate health was not readable')
  const overall = await aggregate.json()
  assert.equal(Object.keys(overall).every(key => ['status', 'groups'].includes(key)), true,
    'Aggregate health exposed private details')
  assert.equal(aggregate.status === 200 ? overall.status === 'UP'
    : ['DOWN', 'OUT_OF_SERVICE'].includes(overall.status), true, 'Aggregate status mapping is inconsistent')
  const info = await probe('/actuator/info')
  assert.equal(info.status, 200, 'Public operational info was not readable')
  for (const route of ['/actuator/health/db', '/actuator/health/readiness/db', '/actuator/env', '/actuator']) {
    assert.equal((await probe(route)).status, 403, 'Non-public Actuator path was not denied')
  }
  assert.equal((await probe('/actuator/health', 'POST')).status, 403, 'Operational write was not denied')
  return { service: service.name, checks, liveness: 'UP', readiness: 'UP', aggregate: overall.status }
}

export async function main(args) {
  let checks = 0
  for (const service of selectServices(args, 'all')) {
    try {
      const result = await checkService(service)
      console.log(JSON.stringify(result))
      checks += result.checks
    } catch {
      // Endpoint bodies and connection configuration are intentionally not echoed.
      throw new Error(`${service.name}: operational smoke check failed`)
    }
  }
  console.log(`Operational checks passed: ${checks}. This is not end-to-end workflow acceptance.`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1 })
}
