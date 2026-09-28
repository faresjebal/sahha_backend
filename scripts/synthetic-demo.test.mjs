import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import path from 'node:path'
import { ROOT, SERVICES } from './sahha.mjs'
import { ACCOUNT_KEYS, accountIdentity, accountsProperties, main, pageItems, validateAccounts, waitOrganisationRoute } from './synthetic-demo.mjs'
import { DEMO_COOKIES, IDENTITY_SERVICES, isolatedEnvironment, launchArguments } from './synthetic/app-runtime.mjs'
import { GATEWAY, GatewayClient, gatewayPath } from './synthetic/gateway-client.mjs'

const fixture = () => ({generation:randomUUID(),accounts:ACCOUNT_KEYS.map(key => ({...accountIdentity(key),password:`Demo-Aa1!${randomBytes(24).toString('base64url')}`}))})
test('synthetic identities are deterministic, distinct, reserved-domain accounts', () => {
  assert.equal(ACCOUNT_KEYS.length,8)
  assert.equal(new Set(ACCOUNT_KEYS.map(key => accountIdentity(key).id)).size,8)
  for (const key of ACCOUNT_KEYS) assert.match(accountIdentity(key).email, /^[a-z]+@sahha\.example\.test$/)
  assert.throws(() => accountIdentity('administrator'), /Unknown/)
})
test('existing account manifests cannot change identity, generation or password contract silently', () => {
  const value = fixture()
  assert.equal(validateAccounts(value,value.generation),value)
  for (const change of [{id:randomUUID()},{email:'real@example.com'},{key:'platformAdmin'},{password:'weak'}]) {
    const copy = structuredClone(value); Object.assign(copy.accounts[0],change)
    assert.throws(() => validateAccounts(copy,value.generation),/mismatch/)
  }
  assert.throws(() => validateAccounts(value,randomUUID()),/mismatch/)
})
test('offline properties carry generated credentials without granting organisation roles', () => {
  const value = fixture(), props = accountsProperties(value)
  for (const account of value.accounts) assert.ok(props.includes(`${account.key}.password=${account.password}\n`))
  assert.doesNotMatch(props,/organisationRoles|PLATFORM_ADMIN|DOCTOR|RECEPTIONIST/)
})
test('CLI rejects unexpected commands before any state or process action', async () => {
  for (const args of [[],['reset'],['seed','--force'],['seed','--database','production']]) await assert.rejects(main(args),/Use:/)
})
test('seed uses the actual items page contract and refuses ambiguous pagination', () => {
  assert.deepEqual(pageItems({items:[],totalPages:0}),[])
  assert.deepEqual(pageItems({items:[{id:'synthetic'}],totalPages:1}),[{id:'synthetic'}])
  for (const page of [null,{content:[],totalPages:0},{items:[],totalPages:2}]) assert.throws(() => pageItems(page),/paginated/)
})
test('routing readiness retries only transient unavailable reads, never denied sessions or commands', async () => {
  let calls = 0
  await waitOrganisationRoute({request:async route => {
    assert.equal(route,'/api/v1/organisations/memberships')
    if (++calls < 3) throw Object.assign(new Error('Unavailable'),{status:503})
  }},3,async () => {})
  assert.equal(calls,3)
  for (const status of [401,403,500]) {
    let attempts = 0
    await assert.rejects(waitOrganisationRoute({request:async () => { attempts++; throw Object.assign(new Error('Denied'),{status}) }},3,async () => {}),/Denied/)
    assert.equal(attempts,1)
  }
  await assert.rejects(waitOrganisationRoute({request:async () => { throw Object.assign(new Error('Unavailable'),{status:503}) }},2,async () => {}),/Unavailable/)
})
test('isolated application children do not inherit database secrets, mail, publisher or Java flags', () => {
  const env = isolatedEnvironment({PATH:'safe',SystemRoot:'windows',AUTH_DB_PASSWORD:'secret',AUTH_MAIL_ENABLED:'true',AUTH_OUTBOX_PUBLISHER_ENABLED:'true',SPRING_PROFILES_ACTIVE:'local',JAVA_TOOL_OPTIONS:'unsafe',PGHOST:'remote'})
  assert.equal(env.PATH,'safe')
  assert.equal(env.SystemRoot,'windows')
  assert.equal(env.AUTH_MAIL_ENABLED,'false')
  assert.equal(env.AUTH_SESSION_CACHE_ENABLED,'false')
  for (const key of ['AUTH_DB_PASSWORD','AUTH_OUTBOX_PUBLISHER_ENABLED','SPRING_PROFILES_ACTIVE','JAVA_TOOL_OPTIONS','PGHOST']) assert.equal(env[key],undefined)
  assert.equal(env.AUTH_ACCESS_COOKIE_NAME,DEMO_COOKIES.access)
})
test('only allowlisted identity applications receive generation-scoped connection files', () => {
  const generation = randomUUID(), runId = randomUUID()
  for (const name of IDENTITY_SERVICES) {
    const service = SERVICES.find(value => value.name === name), jar = path.join(ROOT,name,'target',`${name}.jar`)
    const args = launchArguments(service,jar,generation,runId)
    assert.ok(args.includes('--server.address=127.0.0.1'))
    assert.ok(args.includes('--spring.profiles.active=synthetic'))
    assert.ok(args.includes(`-Dsahha.native.run=${runId}`))
    assert.ok(!args.join(' ').includes('.env.database.local'))
    if (['auth-service','organisation-service'].includes(name)) assert.ok(args.some(value => value.startsWith('--spring.config.additional-location=file:') && value.includes(generation)))
  }
  assert.throws(() => launchArguments({name:'foreign',port:5432},'foreign.jar',generation,runId),/Unknown/)
  assert.throws(() => launchArguments(SERVICES.find(value => value.name === 'clinical-service'),'clinical.jar',generation,runId),/Unknown/)
})
test('Gateway client pins its host and blocks path escape or external URLs', () => {
  assert.equal(gatewayPath('/api/v1/organisations/memberships'),GATEWAY+'/api/v1/organisations/memberships')
  for (const value of ['https://example.test/api/v1/auth','//example.test/api/v1/auth','/api/v1/../internal','/api/v1/%2e%2e/internal','/api/v1/other\\path','/api/v1/test\nsecret','/actuator/env']) assert.throws(() => gatewayPath(value),/pinned/)
})
test('state-changing calls obtain CSRF, send isolated cookies and forbid redirects', async () => {
  const calls = []
  const client = new GatewayClient(async (url,options) => {
    calls.push({url,options})
    return url.endsWith('/csrf') ? Response.json({headerName:'X-XSRF-TOKEN',token:'synthetic-csrf'},{headers:{'set-cookie':`${DEMO_COOKIES.csrf}=synthetic-csrf; Path=/`}}) : Response.json({saved:true})
  })
  await client.request('/api/v1/departments','POST',{name:'Synthetic'})
  assert.equal(calls.length,2)
  assert.equal(calls[1].options.headers['X-XSRF-TOKEN'],'synthetic-csrf')
  assert.equal(calls[1].options.headers.Cookie,`${DEMO_COOKIES.csrf}=synthetic-csrf`)
  assert.equal(calls[1].options.redirect,'error')
  assert.equal(calls[1].options.cache,'no-store')
  client.dispose(); assert.equal(client.cookieHeader(),'')
})
test('unexpected cookie namespace and response details never become a demo authority or error dump', async () => {
  const client = new GatewayClient(async () => Response.json({private:'do-not-echo'}, {status:403}))
  await assert.rejects(client.request('/api/v1/staff'), error => !error.message.includes('do-not-echo') && error.message.includes('403'))
  const mixed = new GatewayClient(async () => Response.json({}, {headers:{'set-cookie':'SAHHA_ACCESS_TOKEN=normal-user-secret'}}))
  await assert.rejects(mixed.request('/api/v1/auth/session'),/namespace/)
  const malformed = new GatewayClient(async () => new Response('private malformed contents', {status:200}))
  await assert.rejects(malformed.request('/api/v1/auth/session'), error => !error.message.includes('private malformed contents') && error.message.includes('suppressed'))
})
test('offline Auth bootstrap stays outside production sources and guards its exact owned database', async () => {
  const source = await readFile(path.join(ROOT,'auth-service','src','synthetic','SeedSyntheticAccounts.java'),'utf8')
  assert.match(source,/jdbc:postgresql:\/\/127\.0\.0\.1:15432\/sahha_demo_auth/)
  assert.match(source,/sahha_synthetic\.environment/)
  assert.match(source,/c\.rollback\(\)/)
  assert.match(source,/PASSWORDS\.matches/)
  assert.doesNotMatch(source,/UPDATE user_account|DELETE FROM|DROP |TRUNCATE|sahha_demo_organisation/)
  assert.match(source,/ISOLATED_SYNTHETIC_ONLY/)
  const events = await readFile(path.join(ROOT,'auth-service','src','main','java','com','sahha','auth','entity','SecurityEventType.java'),'utf8')
  assert.match(events,/SYNTHETIC_ACCOUNT_BOOTSTRAPPED/)
  assert.match(events,/SYNTHETIC_PLATFORM_ROLE_BOOTSTRAPPED/)
})
