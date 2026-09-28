import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { test } from 'node:test'
import { checkService } from './health-smoke.mjs'
import { ROOT, SERVICES, health, main, ownsProcess, portNumber, selectServices, tcpOpen, validState, withProcessLock, javaHomeFromSettings, isSystemConsoleHost, requiresPostgres, selectOwnedChildren } from './sahha.mjs'

test('only the exact Windows console host is left to OS lifetime management', () => {
  assert.equal(isSystemConsoleHost({ Name: 'conhost.exe', ExecutablePath: 'C:\\Windows\\System32\\conhost.exe' }, 'C:\\Windows'), true)
  assert.equal(isSystemConsoleHost({ Name: 'conhost.exe', ExecutablePath: 'C:\\tmp\\conhost.exe' }, 'C:\\Windows'), false)
  assert.equal(isSystemConsoleHost({ Name: 'java.exe', ExecutablePath: 'C:\\Windows\\System32\\conhost.exe' }, 'C:\\Windows'), false)
  assert.equal(isSystemConsoleHost({ Name: 'conhost.exe' }, 'C:\\Windows'), false)
})

test('native runtime resolution bypasses launcher shims and requires Java 21', () => {
  assert.equal(javaHomeFromSettings('    java.home = C:\\Program Files\\Java\\jdk-21\n    java.version = 21.0.12\n'),
    'C:\\Program Files\\Java\\jdk-21')
  assert.throws(() => javaHomeFromSettings('java.home = /jdk\njava.version = 17.0.12'), /requires Java 21/)
  assert.throws(() => javaHomeFromSettings('java.version = 21.0.12'), /requires Java 21/)
})

test('concurrent process control is refused and failure releases its own lock', async t => {
  const directory = await mkdtemp(path.join(tmpdir(), 'sahha-native-lock-test-'))
  t.after(() => rm(directory, { recursive: true, force: true }))
  await withProcessLock(async () => {
    await assert.rejects(withProcessLock(() => assert.fail('must not run'), directory), /Process control is locked/)
  }, directory)
  await assert.rejects(withProcessLock(() => { throw new Error('deliberate failure') }, directory), /deliberate failure/)
  assert.equal(await withProcessLock(() => 'released', directory), 'released')
})

test('foundation selects the required six applications in startup order', () => {
  assert.deepEqual(selectServices([]).map(s => s.name), ['discovery-server', 'config-server', 'auth-service',
    'organisation-service', 'patient-service', 'api-gateway'])
  assert.equal(selectServices(['all']).length, 12)
  assert.deepEqual(selectServices(['config-server', 'discovery-server', 'config-server']).map(s => s.name),
    ['discovery-server', 'config-server'])
})

test('unknown services and shell-like input are rejected before process operations', async () => {
  for (const input of ['../auth-service', 'all; shutdown', 'java', '--help', '']) {
    assert.throws(() => selectServices([input]), /Unknown service/)
  }
  await assert.rejects(main(['test', 'backend', '--skip']), /one build\/test target/)
  await assert.rejects(main(['reset']), /Unknown command/)
  await assert.rejects(main(['build', 'backend;echo']), /Invalid build\/test target/)
})

test('ports cannot inject extra Maven options or commands', () => {
  assert.equal(portNumber('16379', 6379), 16379)
  assert.equal(portNumber(undefined, 6379), 6379)
  for (const input of ['0', '-1', '65536', '1.5', '6379 -DskipTests', '6379&whoami']) {
    assert.throws(() => portNumber(input, 6379), /Invalid TCP port/)
  }
})

const service = SERVICES.find(s => s.name === 'auth-service')

test('all nine stateful services, including Audit, require PostgreSQL and local credentials', () => {
  const infrastructure = selectServices(['discovery-server', 'config-server', 'api-gateway'])
  assert.equal(requiresPostgres(infrastructure), false)
  const stateful = SERVICES.filter(s => !infrastructure.includes(s))
  assert.equal(stateful.length, 9)
  for (const service of stateful) {
    assert.equal(requiresPostgres([service]), true, service.name)
    assert.equal(service.profile, 'local,platform', service.name)
  }
})
const record = { name: service.name, port: service.port, pid: 1234,
  runId: '863bf4c1-c78a-461e-bfc3-b7c580fd89c8', jar: path.join(ROOT, service.name, 'target', 'auth-service-0.0.1-SNAPSHOT.jar') }

test('process state is confined to the named service and its target directory', () => {
  assert.equal(validState(record, service), true)
  for (const change of [{ name: 'java' }, { port: 5432 }, { pid: 0 }, { pid: '1234' },
    { runId: 'anything' }, { jar: path.join(ROOT, 'other.jar') }]) {
    assert.equal(validState({ ...record, ...change }, service), false)
  }
})

test('PID reuse, foreign JARs, and partial marker matches cannot authorize shutdown', () => {
  const command = `java -Dsahha.native.run=${record.runId} -jar "${record.jar}" --server.port=8081`
  assert.equal(ownsProcess(record, command), true)
  for (const commandLine of [null, '', `java -jar "${record.jar}"`,
    command.replace(record.runId, 'another-run'), command.replace(record.jar, 'other.jar'),
    command.replace(record.runId, `${record.runId}-suffix`), command.replace('-jar', '-Djar')]) {
    assert.equal(ownsProcess(record, commandLine), false)
  }
})

const treeCommand = `java -Dsahha.native.run=${record.runId} -jar "${record.jar}"`
const treeParent = {ProcessId:record.pid, CommandLine:treeCommand, createdAt:'2026-09-19T22:09:02.023Z'}
const treeChild = {ProcessId:5678, ParentProcessId:record.pid, Name:'java.exe', CommandLine:treeCommand, createdAt:'2026-09-19T22:09:03.023Z'}

test('a recycled Windows parent PID never adopts or stops an older unrelated process', () => {
  const older = {...treeChild,ProcessId:6789,Name:'browser_assistant.exe',CommandLine:null,createdAt:'2026-09-17T09:11:04.880Z'}
  assert.deepEqual(selectOwnedChildren(record,{parent:treeParent,children:[older,treeChild]}),[treeChild])
  assert.deepEqual(selectOwnedChildren(record,{parent:treeParent,children:[older]}),[])
  assert.deepEqual(selectOwnedChildren(record,{parent:null,children:[older]}),[])
})

test('unknown contemporary descendants and changed parent ownership still block shutdown', () => {
  for (const change of [{CommandLine:null},{CommandLine:treeCommand.replace(record.runId,'another-run')},
    {CommandLine:null,createdAt:treeParent.createdAt},{CommandLine:null,createdAt:'not-a-date'}]) {
    assert.throws(()=>selectOwnedChildren(record,{parent:treeParent,children:[{...treeChild,...change}]}),/unrecognised child/)
  }
  for (const parent of [{...treeParent,CommandLine:null},{...treeParent,ProcessId:6789}]) {
    assert.throws(()=>selectOwnedChildren(record,{parent,children:[]}),/parent ownership/)
  }
})

test('process tree metadata fails closed while the exact system console host remains untouched', () => {
  for (const createdAt of [undefined,null,0,'not-a-date','2026-09-19']) {
    assert.throws(()=>selectOwnedChildren(record,{parent:{...treeParent,createdAt},children:[]}),/creation time/)
    assert.throws(()=>selectOwnedChildren(record,{parent:treeParent,children:[{...treeChild,createdAt}]}),/unrecognised child/)
  }
  for (const change of [{ProcessId:0},{ProcessId:record.pid},{ParentProcessId:6789}]) {
    assert.throws(()=>selectOwnedChildren(record,{parent:treeParent,children:[{...treeChild,...change}]}),/child process identity/)
  }
  assert.throws(()=>selectOwnedChildren(record,{parent:treeParent,children:{}}),/process tree metadata/)
  const host={...treeChild,Name:'conhost.exe',ExecutablePath:path.win32.join(process.env.SystemRoot??'C:\\Windows','System32','conhost.exe'),CommandLine:null}
  if(process.env.SystemRoot)assert.deepEqual(selectOwnedChildren(record,{parent:treeParent,children:[host]}),[])
  assert.throws(()=>selectOwnedChildren(record,{parent:treeParent,children:[{...host,ExecutablePath:'C:\\tmp\\conhost.exe'}]}),/unrecognised child/)
})

test('startup requires liveness and readiness, not optional aggregate health, and fails closed', async t => {
  let aggregate = 'DOWN', liveness = 'UP', readiness = 'UP', code = 200, malformed = false
  const server = createServer((request, response) => {
    response.writeHead(code, { 'Content-Type': 'application/json' })
    response.end(malformed ? 'invalid' : JSON.stringify({ status: request.url.endsWith('/readiness') ? readiness
      : request.url.endsWith('/liveness') ? liveness : aggregate }))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const service = { port: server.address().port }
  assert.equal(await tcpOpen(service.port), true)
  assert.equal(await health(service), true)
  readiness = 'OUT_OF_SERVICE'
  assert.equal(await health(service), false)
  readiness = 'UP'; liveness = 'DOWN'
  assert.equal(await health(service), false)
  liveness = 'UP'; code = 401
  assert.equal(await health(service), false)
  code = 200; malformed = true
  assert.equal(await health(service), false)
})

test('every domain/gateway client opts in to required remote config; tests stay isolated', async () => {
  for (const service of SERVICES.filter(s => !['discovery-server', 'config-server'].includes(s.name))) {
    const base = path.join(ROOT, service.name)
    const pom = await readFile(path.join(base, 'pom.xml'), 'utf8')
    const main = await readFile(path.join(base, 'src/main/resources/application.properties'), 'utf8')
    const platform = await readFile(path.join(base, 'src/main/resources/application-platform.properties'), 'utf8')
    const testConfig = await readFile(path.join(base, 'src/test/resources/application.properties'), 'utf8')
    assert.match(pom, /<artifactId>spring-cloud-starter-config<\/artifactId>/, service.name)
    assert.match(main, /spring.cloud.config.enabled=\$\{CONFIG_CLIENT_ENABLED:false\}/, service.name)
    assert.match(platform, /spring.config.import=configserver:\$\{CONFIG_SERVER_URL:/, service.name)
    assert.doesNotMatch(platform, /optional:configserver:/)
    assert.match(testConfig, /^spring.cloud.config.enabled=false$/m, service.name)
  }
})

test('Config Server contains only reviewed public operational settings', async () => {
  const config = await readFile(path.join(ROOT, 'config-server/src/main/resources/config-repository/application.properties'), 'utf8')
  const allowed = new Set(['sahha.platform.config-version', 'info.sahha.configuration.version',
    'management.info.env.enabled', 'management.endpoints.web.exposure.include',
    'management.endpoint.health.probes.enabled', 'management.endpoint.health.show-details',
    'management.endpoint.health.show-components', 'eureka.instance.metadata-map.config-version', 'spring.jpa.open-in-view'])
  for (const line of config.split(/\r?\n/).filter(line => line && !line.startsWith('#'))) {
    assert.equal(allowed.has(line.split('=')[0]), true, `Unreviewed remote configuration key: ${line.split('=')[0]}`)
  }
})

test('every application ships an explicit standalone-safe probe policy with the required local dependencies', async () => {
  for (const service of SERVICES) {
    const resources = path.join(ROOT, service.name, 'src/main/resources')
    const main = await readFile(path.join(resources, 'application.properties'), 'utf8')
    assert.match(main, /^spring.config.import=.*classpath:health-policy.properties/m, service.name)
    const source = await readFile(path.join(resources, 'health-policy.properties'), 'utf8')
    const policy = Object.fromEntries(source.split(/\r?\n/).filter(line => line && !line.startsWith('#'))
      .map(line => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)] }))
    const required = requiresPostgres([service]) ? 'readinessState,db'
      : service.name === 'config-server' ? 'readinessState,configServer' : 'readinessState'
    assert.equal(policy['management.endpoint.health.group.readiness.include'], required, service.name)
    assert.equal(policy['management.endpoint.health.group.liveness.include'], 'livenessState', service.name)
    assert.equal(policy['management.endpoints.web.exposure.include'], 'health,info', service.name)
    assert.equal(policy['management.endpoint.health.probes.enabled'], 'true', service.name)
    assert.equal(policy['management.endpoint.health.validate-group-membership'], 'true', service.name)
    for (const group of ['', '.group.readiness', '.group.liveness']) {
      for (const kind of ['details', 'components']) {
        assert.equal(policy[`management.endpoint.health${group}.show-${kind}`], 'never', service.name)
      }
    }
    assert.equal(policy['management.endpoint.health.status.http-mapping.down'], '503', service.name)
    assert.equal(policy['management.endpoint.health.status.http-mapping.out-of-service'], '503', service.name)
    if (requiresPostgres([service])) {
      assert.equal(policy['spring.datasource.hikari.connection-timeout'], '3000', service.name)
      assert.equal(policy['spring.datasource.hikari.validation-timeout'], '1000', service.name)
    }
  }
})

test('operational smoke permits optional aggregate failure but refuses leaked details, sessions and open Actuator paths', async () => {
  const service = SERVICES.find(s => s.name === 'audit-service')
  const respond = (change = () => {}) => async (url, options) => {
    assert.match(url, /^http:\/\/127\.0\.0\.1:8089\/actuator/)
    assert.equal(options.redirect, 'error')
    const route = new URL(url).pathname
    let result = { status: 403, body: {}, headers: {} }
    if (options.method === 'GET') {
      if (['/actuator/health/liveness', '/actuator/health/readiness'].includes(route)) {
        result = { status: 200, body: { status: 'UP' }, headers: {} }
      } else if (route === '/actuator/health') {
        result = { status: 503, body: { status: 'DOWN', groups: ['liveness', 'readiness'] }, headers: {} }
      } else if (route === '/actuator/info') result = { status: 200, body: {}, headers: {} }
    }
    change(result, route)
    return new Response(JSON.stringify(result.body), { status: result.status, headers: result.headers })
  }
  assert.deepEqual(await checkService(service, respond()), {
    service: 'audit-service', checks: 9, liveness: 'UP', readiness: 'UP', aggregate: 'DOWN',
  })
  for (const change of [
    result => { result.body.components = { db: { private: 'synthetic' } } },
    result => { result.headers['set-cookie'] = 'JSESSIONID=synthetic' },
    (result, route) => { if (route === '/actuator/env') result.status = 200 },
    (result, route) => { if (route.endsWith('/readiness')) result.status = 503 },
  ]) await assert.rejects(checkService(service, respond(change)))
})

test('implemented HTTP APIs ship safe error/logging defaults and Notification OpenAPI has an explicit switch', async () => {
  const modules = ['auth', 'organisation', 'patient', 'scheduling', 'clinical', 'communication', 'notification', 'file']
    .map(name => `${name}-service`).concat('api-gateway')
  for (const name of modules) {
    const resources = path.join(ROOT, name, 'src/main/resources')
    const main = await readFile(path.join(resources, 'application.properties'), 'utf8')
    assert.match(main, /^spring.config.import=.*classpath:api-policy.properties/m, name)
    const source = await readFile(path.join(resources, 'api-policy.properties'), 'utf8')
    const policy = Object.fromEntries(source.split(/\r?\n/).filter(line => line && !line.startsWith('#'))
      .map(line => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)] }))
    for (const kind of ['message', 'stacktrace', 'binding-errors', 'path']) {
      assert.equal(policy[`server.error.include-${kind}`], 'never', name)
    }
    for (const key of ['server.error.include-exception', 'spring.mvc.log-request-details',
      'spring.http.codecs.log-request-details', 'spring.jpa.show-sql']) assert.equal(policy[key], 'false', name)
    assert.match(policy['logging.pattern.level'], /requestId:%X\{requestId:-unavailable\}/, name)
    for (const logger of ['org.springframework.web.servlet.PageNotFound', 'org.hibernate.SQL',
      'org.hibernate.orm.jdbc.bind', 'org.hibernate.orm.jdbc.extract',
      'org.hibernate.engine.jdbc.spi.SqlExceptionHelper', 'org.hibernate.orm.jdbc.error']) {
      assert.equal(policy[`logging.level.${logger}`], 'OFF', name)
    }
  }
  const notification = await readFile(path.join(ROOT, 'notification-service/src/main/resources/application.properties'), 'utf8')
  assert.match(notification, /^springdoc.api-docs.enabled=\$\{NOTIFICATION_OPENAPI_ENABLED:true\}$/m)
  assert.match(notification, /^springdoc.swagger-ui.enabled=\$\{NOTIFICATION_OPENAPI_ENABLED:true\}$/m)
})

test('service-owned protocol and documentation conventions stay aligned', async () => {
  for (const relative of ['exception/HttpProtocolProblemDetailsHandler.java', 'config/ApiContractCustomizer.java']) {
    let baseline
    for (const name of ['auth', 'organisation', 'patient', 'scheduling', 'clinical', 'communication', 'notification', 'file']) {
      const source = (await readFile(path.join(ROOT, `${name}-service/src/main/java/com/sahha/${name}`, relative), 'utf8'))
        .replace(`package com.sahha.${name}.`, 'package com.sahha.SERVICE.').replaceAll('\r\n', '\n')
      if (baseline === undefined) baseline = source
      else assert.equal(source, baseline, `${name}: ${relative}`)
    }
  }
})
