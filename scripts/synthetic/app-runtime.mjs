import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { closeSync, openSync } from 'node:fs'
import { unlink } from 'node:fs/promises'
import path from 'node:path'
import { ROOT, SERVICES, health, jarFor, javaExecutable, ownedChildren, ownsProcess, processCommand, tcpOpen } from '../sahha.mjs'
import { confinedDirectory, currentManifest, generationPath, privateFile, readPrivate, requireAppsStopped, serviceProperties } from '../synthetic-db.mjs'
import { serviceEnvironment } from './environment.mjs'
import { requireOwnedListeners } from './native-process.mjs'

export { DEMO_COOKIES, isolatedEnvironment } from './environment.mjs'
export const IDENTITY_SERVICES = Object.freeze(['discovery-server','auth-service','organisation-service','api-gateway'])
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

export function launchArguments(service, jar, generation, runId, runtime = {}) {
  if ((!runtime.platform && !IDENTITY_SERVICES.includes(service.name)) || !SERVICES.some(value => value.name === service.name && value.port === service.port)) throw new Error('Unknown synthetic application')
  const directory = generationPath(generation)
  const args = ['-Xms32m','-Xmx256m',`-Dsahha.native.run=${runId}`,`-Dsahha.synthetic.generation=${generation}`,'-jar',jar,
    `--server.port=${service.port}`,'--server.address=127.0.0.1',`--spring.profiles.active=${service.name === 'config-server' ? 'native' : 'synthetic'}`,
    '--eureka.instance.ip-address=127.0.0.1','--eureka.instance.prefer-ip-address=true',
    '--eureka.client.initial-instance-info-replication-interval-seconds=1','--eureka.client.registry-fetch-interval-seconds=5']
  if (!['discovery-server','config-server'].includes(service.name)) {
    const imports = ['classpath:health-policy.properties']
    if (service.name !== 'audit-service') imports.push('classpath:api-policy.properties')
    if (runtime.platform) imports.push('configserver:http://127.0.0.1:8888')
    args.push(`--spring.config.import=${imports.join(',')}`)
    if (service.name !== 'api-gateway') args.push(`--spring.config.additional-location=file:${path.join(directory, service.name.replace('-service','') + '.properties').replaceAll('\\','/')}`)
  }
  return args
}

async function stop(record) {
  if (!record.pid) return
  const command = await processCommand(record.pid)
  if (command && !ownsProcess(record, command)) throw new Error('Synthetic process ownership changed; stop refused')
  if (command) for (const target of [...await ownedChildren(record), record]) {
    const current = await processCommand(target.pid)
    if (current && !ownsProcess(target, current)) throw new Error('Synthetic descendant ownership changed')
    if (current) process.kill(target.pid, 'SIGTERM')
  }
  for (let i = 0; i < 40 && await processCommand(record.pid); i++) await delay(250)
  if (await processCommand(record.pid) || await tcpOpen(record.port)) throw new Error('Synthetic service did not stop; ownership record retained')
  await unlink(record.statePath).catch(error => { if (error.code !== 'ENOENT') throw error })
  console.log(`${record.name}: owned synthetic process stopped.`)
}

export async function withSyntheticApps(generation, names, action, runtime = {}) {
  await requireAppsStopped()
  if (names.some(name => !(runtime.platform ? SERVICES.map(service => service.name) : IDENTITY_SERVICES).includes(name))) throw new Error('Unknown application for this synthetic launcher mode')
  if (runtime.platform && !names.includes('config-server')) throw new Error('Isolated platform batches require their own Config Server')
  const services = SERVICES.filter(service => names.includes(service.name))
  const manifest = await currentManifest()
  if (manifest.id !== generation) throw new Error('Active synthetic generation changed; launch refused')
  // Refuse edited datasource files before starting any application, even when
  // their pathname is inside the generation. Never adopt development settings.
  for (const service of services) {
    const database = manifest.databases.find(value => `${value.service}-service` === service.name)
    if (database && await readPrivate(path.join(generationPath(generation), `${database.service}.properties`)) !== serviceProperties(manifest, database)) {
      throw new Error('Synthetic datasource settings differ from the verified manifest; launch refused')
    }
  }
  const java = await javaExecutable()
  const prepared = await Promise.all(services.map(async service => ({ service, jar:await jarFor(service),env:serviceEnvironment(service.name,runtime) })))
  const directory = await confinedDirectory(path.join(generationPath(generation), 'apps'), true)
  const records = []
  try {
    for (const {service,jar,env} of prepared) {
      const runId = randomUUID()
      const record = { ...service, jar, runId, executable:java,ports:[service.port],identity:[`-Dsahha.native.run=${runId}`,'-jar',jar],statePath:path.join(directory, `${service.name}-${runId}.json`) }
      const output = openSync(path.join(directory, `${service.name}-${runId}.log`), 'wx', 0o600)
      const child = spawn(java, launchArguments(service, jar, generation, runId, runtime), { cwd:ROOT, env, windowsHide:true,
        detached:true, stdio:['ignore',output,output] })
      try {
        await new Promise((resolve,reject) => { child.once('spawn', resolve); child.once('error', reject) })
        record.pid = child.pid
        records.push(record)
        await privateFile(record.statePath, JSON.stringify(record))
      } finally { closeSync(output); child.unref() }
      console.log(`${service.name}: waiting for isolated readiness.`)
      const deadline = Date.now() + 120000
      while (Date.now() < deadline && child.exitCode === null && !await health(service)) await delay(1000)
      if (!await health(service)) throw new Error(`${service.name}: synthetic readiness failed; private log retained in the generation directory`)
      await requireOwnedListeners(record)
    }
    return await action()
  } finally {
    const failures = []
    for (const record of records.reverse()) try { await stop(record) } catch { failures.push(record.name) }
    if (failures.length) throw new Error(`Manual ownership-checked cleanup required for: ${failures.join(', ')}`)
  }
}
