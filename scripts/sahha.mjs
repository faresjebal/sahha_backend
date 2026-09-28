#!/usr/bin/env node
// Native-only developer runner. No package installation, cloud provisioning,
// database reset, secret-file parsing, or infrastructure service management.
import { spawn, execFile } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { closeSync, openSync } from 'node:fs'
import { mkdir, open, readFile, readdir, realpath, unlink, writeFile } from 'node:fs/promises'
import { createConnection } from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const exec = promisify(execFile)
export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const STATE = path.join(ROOT, 'infrastructure', '.state', 'native')
export const SERVICES = Object.freeze([
  { name: 'discovery-server', port: 8761, profile: null },
  { name: 'config-server', port: 8888, profile: 'native' },
  { name: 'auth-service', port: 8081, profile: 'local,platform' },
  { name: 'organisation-service', port: 8082, profile: 'local,platform' },
  { name: 'patient-service', port: 8083, profile: 'local,platform' },
  { name: 'scheduling-service', port: 8084, profile: 'local,platform' },
  { name: 'clinical-service', port: 8085, profile: 'local,platform' },
  { name: 'communication-service', port: 8086, profile: 'local,platform' },
  { name: 'notification-service', port: 8087, profile: 'local,platform' },
  { name: 'file-service', port: 8088, profile: 'local,platform' },
  { name: 'audit-service', port: 8089, profile: 'local,platform' },
  { name: 'api-gateway', port: 8079, profile: 'local,platform' },
])
const FOUNDATION = new Set(['discovery-server', 'config-server', 'auth-service',
  'organisation-service', 'patient-service', 'api-gateway'])

export function selectServices(args, fallback = 'foundation') {
  const names = args.length ? args : [fallback]
  if (names.length === 1 && names[0] === 'all') return [...SERVICES]
  if (names.length === 1 && names[0] === 'foundation') return SERVICES.filter(s => FOUNDATION.has(s.name))
  for (const name of names) {
    if (!SERVICES.some(s => s.name === name)) throw new Error(`Unknown service: ${name}`)
  }
  return SERVICES.filter(s => names.includes(s.name))
}

export function portNumber(value, fallback) {
  const result = value === undefined || value === '' ? fallback : Number(value)
  if (!Number.isInteger(result) || result < 1 || result > 65535) throw new Error('Invalid TCP port')
  return result
}

export function requiresPostgres(services) {
  return services.some(s => !['discovery-server', 'config-server', 'api-gateway'].includes(s.name))
}

export function tcpOpen(port, host = '127.0.0.1', timeout = 1000) {
  return new Promise(resolve => {
    const socket = createConnection({ port, host })
    const finish = result => { socket.destroy(); resolve(result) }
    socket.setTimeout(timeout)
    socket.once('connect', () => finish(true))
    socket.once('error', () => finish(false))
    socket.once('timeout', () => finish(false))
  })
}

async function jsonGet(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(2500),
    redirect: 'error', headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function health(service) {
  try {
    const base = `http://127.0.0.1:${service.port}/actuator/health`
    const [liveness, readiness] = await Promise.all([jsonGet(`${base}/liveness`), jsonGet(`${base}/readiness`)])
    return liveness.status === 'UP' && readiness.status === 'UP'
  } catch { return false }
}

export function validState(record, service) {
  return record?.name === service.name && record?.port === service.port &&
    Number.isSafeInteger(record.pid) && record.pid > 0 &&
    typeof record.runId === 'string' && /^[a-f0-9-]{36}$/.test(record.runId) &&
    typeof record.jar === 'string' && path.dirname(record.jar) === path.join(ROOT, service.name, 'target')
}

// The unguessable launch marker AND the exact JAR path must match. A stale PID
// file can never authorize killing an unrelated process that reused that PID.
export function ownsProcess(record, commandLine) {
  if (typeof commandLine !== 'string' || !commandLine) return false
  const tokens = commandLine.match(/"[^"]*"|[^\s]+/g)?.map(t => t.replace(/^"|"$/g, '')) ?? []
  return tokens.includes(`-Dsahha.native.run=${record.runId}`) &&
    tokens.some((t, i) => t === '-jar' && tokens[i + 1] === record.jar)
}

export async function processCommand(pid) {
  if (!Number.isSafeInteger(pid) || pid < 1) throw new Error('Invalid process ID')
  if (process.platform === 'win32') {
    const command = `$ErrorActionPreference='Stop'; $p=Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}'; if ($p) { $p.CommandLine | ConvertTo-Json -Compress }`
    const { stdout } = await exec('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', command],
      { windowsHide: true, timeout: 10000 })
    return stdout.trim() ? JSON.parse(stdout) : null
  }
  try {
    const { stdout } = await exec('ps', ['-p', String(pid), '-o', 'args='], { timeout: 5000 })
    return stdout.trim() || null
  } catch (error) { if (error.code === 1) return null; throw error }
}

export function javaHomeFromSettings(settings) {
  const home = settings.match(/^\s*java\.home\s*=\s*(.+)$/m)?.[1]?.trim()
  const version = settings.match(/^\s*java\.version\s*=\s*(.+)$/m)?.[1]?.trim()
  if (!home || !version?.startsWith('21.')) throw new Error('Native startup requires Java 21 on PATH')
  return home
}

export async function javaExecutable() {
  // Oracle's Windows javapath executable is a launcher shim that forks another
  // JVM. Resolve the real runtime so the recorded PID owns the listening port.
  const { stderr } = await exec('java', ['-XshowSettings:properties', '-version'],
    { windowsHide: true, timeout: 10000 })
  return realpath(path.join(javaHomeFromSettings(stderr), 'bin', process.platform === 'win32' ? 'java.exe' : 'java'))
}

export function selectOwnedChildren(record, snapshot) {
  if (!Number.isSafeInteger(record.pid) || record.pid < 1 || !Array.isArray(snapshot?.children)) throw new Error('Invalid process tree metadata')
  const parent = snapshot.parent
  if (parent === null) return []
  if (parent?.ProcessId !== record.pid || !ownsProcess(record, parent.CommandLine)) throw new Error('Process tree parent ownership changed')
  const createdAt = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T.*Z$/.test(value) ? Date.parse(value) : NaN
  const parentTime = createdAt(parent.createdAt)
  if (!Number.isFinite(parentTime)) throw new Error('Missing parent creation time; shutdown refused')
  return snapshot.children.filter(child => {
    if (!Number.isSafeInteger(child.ProcessId) || child.ProcessId < 1 || child.ProcessId === record.pid || child.ParentProcessId !== record.pid) throw new Error('Invalid child process identity')
    // Windows retains the original parent PID after it exits. An older process
    // can therefore point to a PID recycled for our JVM; it is not our child.
    const childTime = createdAt(child.createdAt)
    if (Number.isFinite(childTime) && childTime < parentTime) return false
    if (isSystemConsoleHost(child)) return false
    if (!Number.isFinite(childTime) || !ownsProcess(record, child.CommandLine)) throw new Error(`${record.name}: an unrecognised child process prevents safe shutdown; no child is adopted`)
    return true
  })
}

export async function ownedChildren(record) {
  if (!Number.isSafeInteger(record.pid) || record.pid < 1) throw new Error('Invalid process ID')
  if (process.platform !== 'win32') return []
  const command = `$ErrorActionPreference='Stop'; $fields=@('ProcessId','ParentProcessId','Name','ExecutablePath','CommandLine',@{Name='createdAt';Expression={if ($_.CreationDate) {$_.CreationDate.ToUniversalTime().ToString('o')}}}); $parent=Get-CimInstance Win32_Process -Filter 'ProcessId = ${record.pid}'; @{parent=($parent | Select-Object $fields); children=@(Get-CimInstance Win32_Process -Filter 'ParentProcessId = ${record.pid}' | Select-Object $fields)} | ConvertTo-Json -Depth 4 -Compress`
  const { stdout } = await exec('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', command],
    { windowsHide: true, timeout: 10000 })
  const children = selectOwnedChildren(record, JSON.parse(stdout))
  const owned = []
  for (const child of children) {
    const value = { ...record, pid: child.ProcessId }
    owned.push(...await ownedChildren(value), value)
  }
  return owned
}

export function isSystemConsoleHost(processInfo, systemRoot = process.env.SystemRoot) {
  return typeof systemRoot === 'string' && processInfo.Name?.toLowerCase() === 'conhost.exe' &&
    typeof processInfo.ExecutablePath === 'string' &&
    processInfo.ExecutablePath.toLowerCase() === path.win32.join(systemRoot, 'System32', 'conhost.exe').toLowerCase()
}

function stateFile(service) { return path.join(STATE, `${service.name}.json`) }

async function loadState(service) {
  try {
    const record = JSON.parse(await readFile(stateFile(service), 'utf8'))
    if (!validState(record, service)) throw new Error(`Invalid launcher state for ${service.name}; refusing process control`)
    return record
  } catch (error) { if (error.code === 'ENOENT') return null; throw error }
}

export async function jarFor(service) {
  const directory = path.join(ROOT, service.name, 'target')
  const jars = (await readdir(directory)).filter(name => name.endsWith('.jar') && name.startsWith(`${service.name}-`))
  if (jars.length !== 1) throw new Error(`Expected one packaged JAR for ${service.name}; run build backend`)
  const jar = await realpath(path.join(directory, jars[0]))
  if (path.dirname(jar) !== directory) throw new Error('JAR must be inside its service target directory')
  return jar
}

const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

export async function withProcessLock(action, directory = STATE) {
  await mkdir(directory, { recursive: true })
  const lockPath = path.join(directory, 'process-control.lock')
  let lock
  try { lock = await open(lockPath, 'wx', 0o600) }
  catch (error) {
    if (error.code === 'EEXIST') throw new Error(`Process control is locked: ${lockPath}. Check the recorded launcher PID before removing a stale lock.`)
    throw error
  }
  try {
    await lock.writeFile(JSON.stringify({ launcherPid: process.pid, createdAt: new Date().toISOString() }))
    return await action()
  } finally { await lock.close(); await unlink(lockPath) }
}

async function start(services) {
  if (process.env.SERVER_PORT || process.env.SPRING_PROFILES_ACTIVE || process.env.CONFIG_CLIENT_ENABLED === 'false') {
    throw new Error('Unset SERVER_PORT, SPRING_PROFILES_ACTIVE and CONFIG_CLIENT_ENABLED=false before managed native startup')
  }
  // Complete all preflight checks before starting anything.
  const prepared = []
  const java = await javaExecutable()
  for (const service of services) {
    const record = await loadState(service)
    const command = record ? await processCommand(record.pid) : null
    const owned = record && ownsProcess(record, command)
    if (command && !owned) throw new Error(`Stale ownership for ${service.name}; no process was changed`)
    if (owned) {
      if (!await health(service)) throw new Error(`${service.name} is managed but not ready; inspect its private log`)
      prepared.push({ service, running: true })
      continue
    }
    if (await tcpOpen(service.port)) throw new Error(`${service.name}: port ${service.port} is occupied by an unmanaged process; it will not be adopted or stopped`)
    prepared.push({ service, jar: await jarFor(service) })
  }
  if (requiresPostgres(services) && !await tcpOpen(5432)) {
    throw new Error('Native PostgreSQL is not reachable on localhost:5432; see docs/NATIVE_DEVELOPMENT.md')
  }
  await mkdir(STATE, { recursive: true })
  for (const entry of prepared) {
    const { service, jar } = entry
    if (entry.running) { console.log(`${service.name}: already managed and ready`); continue }
    const runId = randomUUID()
    const log = path.join(STATE, `${service.name}-${runId}.log`)
    const output = openSync(log, 'a', 0o600)
    const args = ['-Xms64m', '-Xmx256m', `-Dsahha.native.run=${runId}`, '-jar', jar,
      `--server.port=${service.port}`, '--server.address=127.0.0.1',
      '--eureka.instance.ip-address=127.0.0.1', '--eureka.instance.prefer-ip-address=true',
      '--eureka.client.initial-instance-info-replication-interval-seconds=1',
      '--eureka.client.registry-fetch-interval-seconds=5']
    if (service.profile) args.push(`--spring.profiles.active=${service.profile}`)
    const child = spawn(java, args, { cwd: ROOT, detached: true, windowsHide: true,
      stdio: ['ignore', output, output] })
    try {
      await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject) })
      await writeFile(stateFile(service), JSON.stringify({ name: service.name, port: service.port,
        pid: child.pid, runId, jar, startedAt: new Date().toISOString() }, null, 2), { mode: 0o600 })
    } catch (error) { if (child.pid) child.kill(); throw error }
    finally { closeSync(output); child.unref() }
    console.log(`${service.name}: started PID ${child.pid}; waiting for health and readiness`)
    const deadline = Date.now() + 120000
    while (Date.now() < deadline && child.exitCode === null && child.signalCode === null) {
      if (await health(service)) break
      await delay(1000)
    }
    if (!await health(service)) throw new Error(`${service.name} did not become ready. Private log: ${log}. Use stop to stop only managed services; nothing was deleted.`)
    console.log(`${service.name}: liveness UP, readiness UP`)
  }
  console.log('REST services are ready. Kafka publishers/consumers retain their configured values; this is not end-to-end event verification.')
}

async function stop(services) {
  for (const service of [...services].reverse()) {
    const record = await loadState(service)
    if (!record) { console.log(`${service.name}: unmanaged; untouched`); continue }
    const command = await processCommand(record.pid)
    if (command && !ownsProcess(record, command)) throw new Error(`${service.name}: ownership mismatch; refusing to stop PID ${record.pid}`)
    if (command) {
      // Also handle services started by an older launcher through an Oracle
      // shim. Verify every descendant before stopping any process in that tree.
      const targets = [...await ownedChildren(record), record]
      for (const target of targets) {
        const current = await processCommand(target.pid)
        if (!current) continue
        if (!ownsProcess(target, current)) throw new Error('Process ownership changed; shutdown refused')
        try { process.kill(target.pid, 'SIGTERM') }
        catch (error) { if (error.code !== 'ESRCH') throw error }
      }
      for (let i = 0; i < 40; i++) {
        if (!await processCommand(record.pid)) break
        await delay(250)
      }
      if (await processCommand(record.pid)) throw new Error(`${service.name}: process did not stop; state retained`)
      if (await tcpOpen(service.port)) throw new Error(`${service.name}: port still occupied after termination; state retained for inspection`)
    }
    await unlink(stateFile(service))
    console.log(`${service.name}: stopped; logs and all database/object data retained`)
  }
}

async function registry() {
  try {
    const response = await jsonGet('http://127.0.0.1:8761/eureka/apps')
    const apps = response.applications?.application ?? []
    return (Array.isArray(apps) ? apps : [apps]).flatMap(app => {
      const instances = app.instance ?? []
      return (Array.isArray(instances) ? instances : [instances]).map(instance => ({ ...instance, app: app.name }))
    })
  } catch { return [] }
}

async function status(services) {
  const instances = await registry()
  for (const service of services) {
    const ready = await health(service)
    const instance = instances.find(i => i.app?.toLowerCase() === service.name && Number(i.port?.$) === service.port && i.status === 'UP')
    const version = instance?.metadata?.['config-version'] ?? '-'
    const record = await loadState(service)
    const managed = record && ownsProcess(record, await processCommand(record.pid))
    console.log(`${service.name.padEnd(23)} ${service.port}  ${ready ? 'UP   ' : 'DOWN '}  ${managed ? 'managed  ' : 'unmanaged'}  discovery=${instance ? 'UP' : '-'}  config=${version}`)
  }
}

async function check() {
  for (const [name, port] of [['PostgreSQL', 5432], ['Auth Redis', portNumber(process.env.AUTH_REDIS_PORT, 6379)],
    ['Sharing Redis', portNumber(process.env.REDIS_PORT, 6379)], ['Kafka', 9092], ['SeaweedFS S3', 8333]]) {
    console.log(`${name}: ${await tcpOpen(port) ? 'reachable' : 'not listening'} on localhost:${port}`)
  }
  console.log('TCP reachability is not authentication or workflow verification. See docs/NATIVE_DEVELOPMENT.md for native prerequisites.')
}

async function run(command, args, cwd = ROOT, env = process.env) {
  const child = spawn(command, args, { cwd, env, stdio: 'inherit', windowsHide: true,
    shell: process.platform === 'win32' && command.endsWith('.cmd') })
  await new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', code => code === 0 ? resolve() : reject(new Error(`${path.basename(command)} exited with ${code}`)))
  })
}

async function validate(command, target) {
  if (!['all', 'backend', 'frontend', 'tools'].includes(target) || (command === 'build' && target === 'tools')) throw new Error('Invalid build/test target')
  const mvn = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'
  const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm'
  if (command === 'test' && ['all', 'tools'].includes(target)) {
    await run(process.execPath, ['--test', 'scripts/synthetic-acceptance.test.mjs'])
    await run(process.execPath, ['--test', 'scripts/synthetic-message-attachments.test.mjs'])
    await run(process.execPath, ['--test', 'scripts/synthetic-referral-notifications.test.mjs'])
    await run(process.execPath, ['--test', 'scripts/synthetic-patient-notifications.test.mjs'])
    await run(process.execPath, ['--test', 'scripts/sahha.test.mjs', 'scripts/synthetic-db.test.mjs', 'scripts/synthetic-demo.test.mjs', 'scripts/synthetic-infra.test.mjs', 'scripts/synthetic-platform.test.mjs', 'scripts/synthetic-workflow.test.mjs', 'scripts/synthetic-clinical.test.mjs', 'scripts/synthetic-files.test.mjs', 'scripts/synthetic-messages.test.mjs', 'scripts/synthetic-referrals.test.mjs', 'scripts/synthetic-shared-care.test.mjs', 'scripts/synthetic-browser.test.mjs', 'scripts/synthetic-setup.test.mjs', 'scripts/synthetic-setup.integration.test.mjs'])
  }
  if (['all', 'backend'].includes(target)) {
    const port = portNumber(process.env.AUTH_TEST_REDIS_PORT, 6379)
    await run(mvn, command === 'build' ? ['package', '-DskipTests'] : ['test', `-Dsahha.test.redis.port=${port}`])
  }
  if (['all', 'frontend'].includes(target)) {
    const cwd = path.join(ROOT, 'frontend')
    if (command === 'test') await run(npm, ['run', 'typecheck'], cwd)
    await run(npm, command === 'build' ? ['run', 'build'] : ['test', '--', '--maxWorkers=1'], cwd)
  }
}

export async function main(args) {
  const [command = 'help', ...rest] = args
  switch (command) {
    case 'start': { const selected = selectServices(rest); return withProcessLock(() => start(selected)) }
    case 'stop': { const selected = selectServices(rest, 'all'); return withProcessLock(() => stop(selected)) }
    case 'status': return status(selectServices(rest, 'all'))
    case 'check': if (rest.length) throw new Error('check takes no arguments'); return check()
    case 'build': case 'test':
      if (rest.length > 1) throw new Error('Specify one build/test target')
      return validate(command, rest[0] ?? 'all')
    case 'help':
      console.log('Sahha native developer commands (run from repository root):\n' +
        '  node scripts/sahha.mjs check\n  node scripts/sahha.mjs build [all|backend|frontend]\n' +
        '  node scripts/sahha.mjs test [all|backend|frontend|tools]\n' +
        '  node scripts/sahha.mjs start [foundation|all|service names...]\n' +
        '  node scripts/sahha.mjs status [all|foundation|service names...]\n' +
        '  node scripts/sahha.mjs stop [all|foundation|service names...]\n' +
        'Only launcher-owned Java processes are stopped. No database reset is implemented.\n' +
        'Start the frontend separately with npm run dev in frontend/.')
      return
    default: throw new Error(`Unknown command: ${command}`)
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1 })
}
