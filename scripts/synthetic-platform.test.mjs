import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { test } from 'node:test'
import { SERVICES } from './sahha.mjs'
import { main } from './synthetic-platform.mjs'
import { launchArguments } from './synthetic/app-runtime.mjs'
import { DEMO_COOKIES, serviceEnvironment } from './synthetic/environment.mjs'
import { BATCHES, batchNames } from './synthetic/platform-runtime.mjs'
import { checkService } from './health-smoke.mjs'

const runtime = {platform:true,redisPassword:'a'.repeat(64),hmacSecret:'b'.repeat(64),storage:{endpoint:'http://127.0.0.1:18333',bucket:'sahha-synthetic-files',accessKey:'c'.repeat(32),secretKey:'d'.repeat(64)}}
test('shipped cookie properties use the shared environment contract across all browser APIs',async () => {
  // This source contract complements Notification's Spring Binder/HTTP tests.
  // A launcher-only test cannot catch a service ignoring the supplied setting.
  for (const name of ['auth','organisation','patient','scheduling','clinical','communication','notification','file','gateway']) {
    const directory=name==='gateway'?'api-gateway':`${name}-service`
    const text=await readFile(new URL(`../${directory}/src/main/resources/application.properties`,import.meta.url),'utf8')
    const properties=new Map(text.split(/\r?\n/).filter(line=>line && !line.startsWith('#')).map(line=>{
      const separator=line.indexOf('=');return [line.slice(0,separator).trim(),line.slice(separator+1).trim()]
    }))
    const prefix=`sahha.${name}.${name==='auth'?'cookies':'security'}`
    const canonical=(key,setting)=>assert.ok(properties.get(`${prefix}.${key}`)?.startsWith('${'+setting+':'),
      `${directory} must prefer ${setting} for ${key}`)
    canonical(name==='auth'?'access-token-name':'access-token-cookie-name','AUTH_ACCESS_COOKIE_NAME')
    if(name!=='gateway') {
      canonical('csrf-token-name','AUTH_CSRF_COOKIE_NAME')
      canonical('csrf-header-name','AUTH_CSRF_HEADER_NAME')
      canonical(name==='auth'?'secure':'secure-cookies','AUTH_COOKIE_SECURE')
    }
    const environment=serviceEnvironment(directory,runtime,{AUTH_ACCESS_TOKEN_COOKIE_NAME:'foreign',AUTH_SECURE_COOKIES:'true'})
    assert.equal(environment.AUTH_ACCESS_COOKIE_NAME,DEMO_COOKIES.access)
    assert.equal(environment.AUTH_CSRF_COOKIE_NAME,DEMO_COOKIES.csrf)
    assert.equal(environment.AUTH_COOKIE_SECURE,'false')
    assert.equal(environment.AUTH_ACCESS_TOKEN_COOKIE_NAME,undefined)
    assert.equal(environment.AUTH_SECURE_COOKIES,undefined)
  }
})
test('bounded application batches cover every service and require explicit selection',async () => {
  assert.deepEqual([...new Set(Object.values(BATCHES).flat())].sort(),SERVICES.map(value => value.name).sort())
  for (const names of Object.values(BATCHES)) { assert.ok(names.length<=7); assert.ok(names.includes('config-server')) }
  assert.throws(() => batchNames('all'),/Unknown/)
  await assert.rejects(main(['start','all']),/Use:/)
})
test('platform arguments never import normal local secret files or give Audit nonexistent API policy',() => {
  for (const service of SERVICES) {
    const args=launchArguments(service,path.resolve('service.jar'),randomUUID(),randomUUID(),runtime)
    assert.doesNotMatch(args.join(' '),/\.env\.|profiles.active=local/)
    if (service.name==='config-server') assert.ok(args.includes('--spring.profiles.active=native'))
    else if (service.name!=='discovery-server') {
      assert.ok(args.some(value=>value.includes('configserver:http://127.0.0.1:8888')))
      if (service.name==='audit-service') assert.doesNotMatch(args.join(' '),/api-policy/)
    }
  }
})
test('only each service receives the secrets/dependencies it needs',() => {
  for (const service of SERVICES) {
    const env=serviceEnvironment(service.name,runtime,{AUTH_DB_PASSWORD:'foreign',JAVA_TOOL_OPTIONS:'foreign'})
    assert.equal(env.AUTH_DB_PASSWORD,undefined); assert.equal(env.JAVA_TOOL_OPTIONS,undefined)
    assert.equal(env.SPRING_DATA_REDIS_PASSWORD,['auth-service','communication-service'].includes(service.name)?runtime.redisPassword:undefined)
    assert.equal(env.SEAWEEDFS_S3_SECRET_KEY,service.name==='file-service'?runtime.storage.secretKey:undefined)
    assert.equal(env.PATIENT_IDENTIFIER_HMAC_SECRET,service.name==='patient-service'?runtime.hmacSecret:undefined)
    assert.equal(env.KAFKA_BOOTSTRAP_SERVERS,'127.0.0.1:19092')
    assert.equal(env.AUTH_MAIL_ENABLED,'false')
  }
})
test('events and synthetic scanning are explicit, and storage cannot target another instance',() => {
  assert.equal(serviceEnvironment('notification-service',runtime).NOTIFICATION_REFERRAL_CONSUMER_ENABLED,undefined)
  assert.equal(serviceEnvironment('notification-service',{...runtime,eventDelivery:true}).NOTIFICATION_REFERRAL_CONSUMER_ENABLED,'true')
  assert.equal(serviceEnvironment('file-service',runtime).FILE_SYNTHETIC_CLEAN_ENABLED,'false')
  assert.equal(serviceEnvironment('file-service',{...runtime,syntheticScan:true,eventDelivery:true}).FILE_OUTBOX_PUBLISHER_ENABLED,'true')
  assert.equal(serviceEnvironment('auth-service',runtime).AUTH_OUTBOX_PUBLISHER_ENABLED,undefined)
  assert.equal(serviceEnvironment('notification-service',{...runtime,eventDelivery:true}).NOTIFICATION_COMMUNICATION_CONSUMER_ENABLED,'true')
  assert.throws(()=>serviceEnvironment('auth-service',{platform:true}),/secrets/)
  assert.throws(()=>serviceEnvironment('file-service',{...runtime,storage:{...runtime.storage,endpoint:'http://127.0.0.1:8333'}}),/storage/)
})
test('operational probes challenge the active demo cookie namespace, not only normal development cookies',async () => {
  await assert.rejects(checkService({port:1},async (_url,options)=>{
    assert.ok(options.headers.Cookie.startsWith('SAHHA_DEMO_ACCESS=synthetic-invalid-token'))
    throw new Error('Synthetic expected stop')
  },'SAHHA_DEMO_ACCESS'),/Synthetic expected stop/)
  await assert.rejects(checkService({port:1},()=>assert.fail('Must not call'), 'unsafe;injection'),/cookie name/)
})
