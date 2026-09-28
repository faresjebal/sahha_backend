#!/usr/bin/env node
// Operator tooling only. No application database is adopted, cleaned or dropped.
import { spawn } from 'node:child_process'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { lstat, mkdir, open, readFile, readdir, realpath, rename, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ROOT, SERVICES, javaHomeFromSettings, tcpOpen } from './sahha.mjs'
import { createOperationLock } from './synthetic/operation-lock.mjs'

export const PORT = 15432
export const SYNTHETIC_DEPENDENCY_PORTS = Object.freeze([16379, 19092, 19093, 18333, 19333, 18081, 18889, 28333, 29333, 28081, 28889])
export const STATE = path.join(ROOT, 'infrastructure', '.state', 'synthetic-postgres')
export const DOMAINS = Object.freeze(['auth', 'organisation', 'patient', 'scheduling', 'clinical', 'communication', 'notification', 'file', 'audit'])
const ADMIN = 'sahha_synthetic_admin'
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const secret = /^[a-f0-9]{64}$/
const exe = name => process.platform === 'win32' ? `${name}.exe` : name
const samePath = (a, b) => process.platform === 'win32' ? path.resolve(a).toLowerCase() === path.resolve(b).toLowerCase() : path.resolve(a) === path.resolve(b)

export function generationPath(id, root = STATE) {
  if (typeof id !== 'string' || !uuid.test(id)) throw new Error('Invalid synthetic generation ID')
  return path.join(root, id)
}

export function migrationRuntimePath(sha256) {
  if (typeof sha256 !== 'string' || !secret.test(sha256)) throw new Error('Invalid migration artifact fingerprint')
  return path.join(STATE, 'migration-runtime', sha256)
}

export function parseArgs(args) {
  const [command = 'help', ...rest] = args
  if (command === 'reset') {
    if (rest.length !== 2 || rest[0] !== '--confirm' || !uuid.test(rest[1])) throw new Error('reset requires --confirm CURRENT-GENERATION-ID; previous data is retained')
  } else if (!['help', 'init', 'start', 'stop', 'status', 'verify', 'migrate'].includes(command) || rest.length) {
    throw new Error('Unknown synthetic database command or unexpected arguments')
  }
  return { command, confirmation: rest[1] }
}

export function validateManifest(m) {
  if (!m || m.kind !== 'sahha-isolated-synthetic-postgres-v1' || !uuid.test(m.id ?? '') || m.port !== PORT || m.admin !== ADMIN
    || !secret.test(m.adminPassword ?? '') || typeof m.pgBin !== 'string' || !path.isAbsolute(m.pgBin) || /[\r\n"]/.test(m.pgBin)
    || !Array.isArray(m.databases) || m.databases.length !== DOMAINS.length) throw new Error('Invalid private synthetic manifest; refusing operation')
  for (const [index, domain] of DOMAINS.entries()) {
    const entry = m.databases[index]
    if (entry?.service !== domain || entry.database !== `sahha_demo_${domain}` || entry.username !== `sahha_demo_${domain}_app`
      || !secret.test(entry.password ?? '')) throw new Error('Synthetic database/owner allowlist mismatch')
  }
  if (m.systemIdentifier !== null && !/^\d{10,24}$/.test(m.systemIdentifier)) throw new Error('Invalid cluster identity')
  return m
}

export function cleanEnvironment(source = process.env) {
  return Object.fromEntries(Object.entries(source).filter(([key]) => !/^PG/i.test(key)
    && !/^(JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS|CLASSPATH)$/i.test(key)))
}

export function parsePrivateJson(value) {
  try { return JSON.parse(value) }
  catch { throw new Error('Invalid private state JSON; contents suppressed to protect credentials') }
}

export function postgresConfiguration() {
  return `# Isolated synthetic instance; never used by the shared Windows service.\nlisten_addresses = '127.0.0.1'\nport = ${PORT}\nshared_buffers = '32MB'\nmax_connections = 40\npassword_encryption = 'scram-sha-256'\nunix_socket_directories = ''\nlog_statement = 'none'\nlog_min_error_statement = 'panic'\nlog_error_verbosity = 'terse'\nlog_min_messages = 'panic'\nlog_connections = ''\nlog_disconnections = off\nlog_duration = off\nlog_min_duration_statement = -1\n`
}

export function hbaConfiguration(m) {
  validateManifest(m)
  return `# No trust authentication, remote access or replication.\nhost all ${ADMIN} 127.0.0.1/32 scram-sha-256\n`
    + m.databases.map(d => `host ${d.database} ${d.username} 127.0.0.1/32 scram-sha-256\n`).join('')
    + 'host all all 0.0.0.0/0 reject\nhost all all ::/0 reject\nlocal all all reject\n'
}

export function serviceProperties(m, d) {
  validateManifest(m)
  if (!m.databases.includes(d)) throw new Error('Unknown synthetic service')
  return `# PRIVATE synthetic connection only; do not copy into tracked configuration.\nsahha.synthetic.generation=${m.id}\nspring.datasource.url=jdbc:postgresql://127.0.0.1:${PORT}/${d.database}\nspring.datasource.username=${d.username}\nspring.datasource.password=${d.password}\nspring.datasource.hikari.maximum-pool-size=3\nspring.datasource.hikari.minimum-idle=0\nspring.jpa.hibernate.ddl-auto=validate\nspring.jpa.open-in-view=false\nspring.flyway.enabled=true\nspring.flyway.clean-disabled=true\nspring.flyway.validate-on-migrate=true\nspring.flyway.locations=classpath:db/migration\n`
}

// Subprocess output is captured, bounded and never included in exceptions: SQL
// errors and runtime diagnostics may contain submitted secrets or connection data.
export async function runPrivate(command, args, { cwd = ROOT, env = cleanEnvironment(), input, timeout = 60000, allowFailure = false, quiet = false } = {}) {
  return new Promise((resolve, reject) => {
    // pg_ctl's detached Windows server must not inherit a pipe whose lifetime
    // would keep the launcher waiting after pg_ctl itself has exited.
    const child = spawn(command, args, { cwd, env, windowsHide: true, shell: false, stdio: quiet ? 'ignore' : ['pipe', 'pipe', 'pipe'] })
    let stdout = '', stderr = '', timedOut = false
    const timer = setTimeout(() => { timedOut = true; child.kill() }, timeout)
    child.stdout?.on('data', data => { if (stdout.length < 1_000_000) stdout += data.toString() })
    child.stderr?.on('data', data => { if (stderr.length < 1_000_000) stderr += data.toString() })
    child.stdin?.on('error', () => {})
    child.once('error', () => { clearTimeout(timer); reject(new Error(`Cannot run ${path.basename(command)}; check the installed native prerequisite`)) })
    child.once('close', code => {
      clearTimeout(timer)
      if (timedOut || (code !== 0 && !allowFailure)) reject(new Error(`${path.basename(command)} failed${timedOut ? ' (timeout)' : ''}; raw diagnostics suppressed to protect secrets`))
      else resolve({ code, stdout, stderr })
    })
    child.stdin?.end(input)
  })
}

export async function confinedDirectory(directory, create = false) {
  const relative = path.relative(ROOT, directory)
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Synthetic state must stay inside this repository')
  let current = ROOT
  for (const part of relative.split(path.sep)) {
    current = path.join(current, part)
    if (create) await mkdir(current, { mode: 0o700 }).catch(error => { if (error.code !== 'EEXIST') throw error })
    const info = await lstat(current)
    if (!info.isDirectory() || info.isSymbolicLink() || !samePath(await realpath(current), current)) throw new Error('Symlink/junction or unexpected synthetic state directory refused')
  }
  return directory
}

export async function privateFile(file, content, flag = 'wx') {
  await confinedDirectory(path.dirname(file))
  if (flag !== 'wx') {
    const info = await lstat(file)
    if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1) throw new Error('Unsafe private state file refused')
  }
  await writeFile(file, content, { mode: 0o600, flag })
}

export async function readPrivate(file) {
  await confinedDirectory(path.dirname(file))
  const info = await lstat(file)
  if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1) throw new Error('Unsafe private state file refused')
  return readFile(file, 'utf8')
}

export const lock = createOperationLock(async () => {
  await confinedDirectory(STATE, true)
  const file = path.join(STATE, 'operation.lock')
  let handle
  try { handle = await open(file, 'wx', 0o600) } catch { throw new Error('Synthetic database operation locked; inspect the recorded PID before removing a stale lock') }
  try { await handle.writeFile(JSON.stringify({ pid: process.pid })) }
  catch(error) { await handle.close(); await unlink(file); throw error }
  return async () => { await handle.close(); await unlink(file) }
})

async function restrictGeneration(directory) {
  if (process.platform !== 'win32') return
  const result = await runPrivate('whoami.exe', ['/user', '/fo', 'csv', '/nh'])
  const sid = result.stdout.match(/S-1-5-21-\d+-\d+-\d+-\d+/)?.[0]
  if (!sid) throw new Error('Cannot determine Windows owner SID; no secrets written')
  await runPrivate('icacls.exe', [directory, '/inheritance:r', '/grant:r', `*${sid}:(OI)(CI)F`, '*S-1-5-18:(OI)(CI)F'])
}

export async function currentManifest() {
  const active = parsePrivateJson(await readPrivate(path.join(STATE, 'active.json')))
  const directory = generationPath(active.id)
  const m = validateManifest(parsePrivateJson(await readPrivate(path.join(directory, 'manifest.json'))))
  if (m.id !== active.id || m.systemIdentifier === null) throw new Error('Incomplete synthetic generation; no operation allowed')
  return m
}

async function psql(m, database, username, password, sql, allowFailure = false) {
  return runPrivate(path.join(m.pgBin, exe('psql')), ['-X', '-w', '-qAt', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', String(PORT), '-U', username, '-d', database],
    { input: sql, env: { ...cleanEnvironment(), PGPASSWORD: password, PGCONNECT_TIMEOUT: '3' }, allowFailure, timeout: 30000 })
}
const adminSql = (m, database, sql) => psql(m, database, ADMIN, m.adminPassword, sql)

async function databaseIdentity(m) {
  const result = await adminSql(m, 'postgres', "SELECT system_identifier FROM pg_control_system();")
  const id = result.stdout.trim()
  if (!/^\d{10,24}$/.test(id) || (m.systemIdentifier !== null && m.systemIdentifier !== id)) throw new Error('Synthetic PostgreSQL cluster identity mismatch')
  return id
}

export function ownsPostgres(commandLine, executablePath, data, binary) {
  const tokens = commandLine?.match(/"[^"]*"|[^\s]+/g)?.map(t => t.replace(/^"|"$/g, '')) ?? []
  return typeof executablePath === 'string' && samePath(executablePath, binary)
    && tokens.some((token, index) => token === '-D' && tokens[index + 1] && samePath(tokens[index + 1], data))
}

async function ownedPostgres(m) {
  const data = await confinedDirectory(path.join(generationPath(m.id), 'data'))
  let lines
  try { lines = (await readPrivate(path.join(data, 'postmaster.pid'))).trim().split(/\r?\n/) }
  catch (error) { if (error.code === 'ENOENT') return false; throw error }
  const pid = Number(lines[0])
  if (!Number.isSafeInteger(pid) || pid < 1 || !samePath(lines[1], data) || Number(lines[3]) !== PORT) throw new Error('Synthetic PostgreSQL PID record mismatch')
  if (process.platform !== 'win32') throw new Error('Process ownership verification is currently supported on Windows only')
  const query = `$p=Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}'; if ($p) { $p | Select-Object CommandLine,ExecutablePath | ConvertTo-Json -Compress }`
  const result = await runPrivate('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', query])
  if (!result.stdout.trim()) return false
  const processInfo = JSON.parse(result.stdout)
  if (!ownsPostgres(processInfo.CommandLine, processInfo.ExecutablePath, data, path.join(m.pgBin, exe('postgres')))) throw new Error('PostgreSQL process ownership mismatch; no process stopped')
  return true
}

async function startCluster(m) {
  const directory = generationPath(m.id)
  if (await ownedPostgres(m)) { await databaseIdentity(m); return false }
  if (await tcpOpen(PORT)) throw new Error('Synthetic port is occupied by an unmanaged process; not adopted')
  await runPrivate(path.join(m.pgBin, exe('pg_ctl')), ['start', '-D', path.join(directory, 'data'), '-l', path.join(directory, 'postgres.log'), '-w', '-t', '30'], { timeout: 40000, quiet: true })
  if (!await ownedPostgres(m)) throw new Error('Started PostgreSQL ownership could not be verified')
  await databaseIdentity(m)
  return true
}

async function stopCluster(m) {
  if (!await ownedPostgres(m)) {
    if (await tcpOpen(PORT)) throw new Error('Synthetic port is unmanaged; it will not be stopped')
    return
  }
  await runPrivate(path.join(m.pgBin, exe('pg_ctl')), ['stop', '-D', path.join(generationPath(m.id), 'data'), '-m', 'fast', '-w', '-t', '30'], { timeout: 40000, quiet: true })
  if (await tcpOpen(PORT)) throw new Error('Synthetic port remains occupied after owned shutdown')
}

export async function withCluster(m, action) {
  const wasRunning = await ownedPostgres(m)
  try { await startCluster(m); return await action() }
  finally { if (!wasRunning) await stopCluster(m) }
}

export async function requireAppsStopped() {
  const occupied = []
  for (const service of SERVICES) if (await tcpOpen(service.port)) occupied.push(service.name)
  if (occupied.length || await tcpOpen(5173)) throw new Error('Stop all Sahha applications/frontend before synthetic init/reset/migration')
}

export async function requireSyntheticDependenciesStopped(probe = tcpOpen) {
  for (const port of SYNTHETIC_DEPENDENCY_PORTS) {
    if (await probe(port)) throw new Error('Stop all isolated synthetic dependency helpers before init/reset; occupied ports are never adopted')
  }
}

async function verify(m) {
  await databaseIdentity(m)
  let checks = 1
  for (const d of m.databases) {
    const result = await psql(m, d.database, d.username, d.password, `SELECT current_database(), current_user, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls FROM pg_roles WHERE rolname=current_user; SELECT generation, service FROM sahha_synthetic.environment;`)
    if (result.stdout.trim().replaceAll('\r\n', '\n') !== `${d.database}|${d.username}|f|f|f|f|f\n${m.id}|${d.service}`) throw new Error(`Synthetic owner/marker verification failed for ${d.service}`)
    checks += 2
    for (const other of m.databases.filter(value => value !== d)) {
      const denied = await psql(m, other.database, d.username, d.password, 'SELECT 1;', true)
      if (denied.code === 0) throw new Error('Cross-service synthetic database access was not denied')
      checks++
    }
    const markerWrite = await psql(m, d.database, d.username, d.password, 'BEGIN; UPDATE sahha_synthetic.environment SET service=service; ROLLBACK;', true)
    if (markerWrite.code === 0) throw new Error('Application role could modify the operator marker')
    checks++
  }
  const wrongPassword = await psql(m, 'postgres', ADMIN, randomBytes(32).toString('hex'), 'SELECT 1;', true)
  if (wrongPassword.code === 0) throw new Error('Password authentication is not enforced')
  console.log(`Synthetic database isolation: ${checks + 1} checks passed; no application seed implied.`)
}

async function createGeneration() {
  await requireAppsStopped()
  await requireSyntheticDependenciesStopped()
  if (process.platform !== 'win32') throw new Error('This native bootstrap currently supports Windows; no partial non-Windows setup is attempted')
  if (await tcpOpen(PORT)) throw new Error('Stop the owned synthetic database before init/reset; occupied ports are never adopted')
  const pgBin = await realpath(process.env.SAHHA_PG_BIN || 'C:\\Program Files\\PostgreSQL\\18\\bin')
  for (const name of ['initdb', 'pg_ctl', 'psql', 'postgres']) {
    const version = await runPrivate(path.join(pgBin, exe(name)), ['--version'])
    if (!/\(PostgreSQL\) 18\./.test(version.stdout)) throw new Error('Install PostgreSQL 18 binaries; mixed major versions are refused')
  }
  const m = validateManifest({ kind: 'sahha-isolated-synthetic-postgres-v1', id: randomUUID(), port: PORT, pgBin,
    admin: ADMIN, adminPassword: randomBytes(32).toString('hex'), systemIdentifier: null,
    databases: DOMAINS.map(service => ({ service, database: `sahha_demo_${service}`, username: `sahha_demo_${service}_app`, password: randomBytes(32).toString('hex') })) })
  const directory = generationPath(m.id)
  await confinedDirectory(directory, true)
  await restrictGeneration(directory)
  await privateFile(path.join(directory, 'manifest.json'), JSON.stringify(m, null, 2))
  const pwfile = path.join(directory, 'init.password')
  await privateFile(pwfile, m.adminPassword + '\n')
  try {
    await runPrivate(path.join(pgBin, exe('initdb')), ['-D', path.join(directory, 'data'), '-U', ADMIN, '--pwfile', pwfile, '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C', '--no-instructions'], { timeout: 60000 })
  } finally { await unlink(pwfile) }
  await privateFile(path.join(directory, 'data', 'postgresql.conf'), postgresConfiguration(), 'w')
  await privateFile(path.join(directory, 'data', 'pg_hba.conf'), hbaConfiguration(m), 'w')
  try {
    await startCluster(m)
    m.systemIdentifier = await databaseIdentity(m)
    await privateFile(path.join(directory, 'manifest.json'), JSON.stringify(m, null, 2), 'w')
    for (const d of m.databases) {
      await adminSql(m, 'postgres', `CREATE ROLE ${d.username} LOGIN PASSWORD '${d.password}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS; CREATE DATABASE ${d.database} OWNER ${d.username}; REVOKE ALL ON DATABASE ${d.database} FROM PUBLIC; GRANT CONNECT ON DATABASE ${d.database} TO ${d.username};`)
      await adminSql(m, d.database, `REVOKE CREATE ON SCHEMA public FROM PUBLIC; CREATE SCHEMA sahha_synthetic AUTHORIZATION ${ADMIN}; REVOKE ALL ON SCHEMA sahha_synthetic FROM PUBLIC; CREATE TABLE sahha_synthetic.environment (generation uuid PRIMARY KEY, service text NOT NULL); INSERT INTO sahha_synthetic.environment VALUES ('${m.id}', '${d.service}'); GRANT USAGE ON SCHEMA sahha_synthetic TO ${d.username}; GRANT SELECT ON sahha_synthetic.environment TO ${d.username};`)
      await privateFile(path.join(directory, `${d.service}.properties`), serviceProperties(m, d))
    }
    await verify(m)
  } finally { await stopCluster(m) }
  const active = path.join(STATE, 'active.json')
  const staged = path.join(STATE, `active-${m.id}.tmp`)
  await privateFile(staged, JSON.stringify({ id: m.id }))
  try { await readPrivate(active) } catch (error) { if (error.code !== 'ENOENT') throw error }
  await rename(staged, active)
  console.log(`Synthetic generation ready and stopped: ${m.id}. Private settings: ${directory}. No existing data deleted.`)
}

export async function prepareMigrationRuntime() {
  const settings = await runPrivate('java', ['-XshowSettings:properties', '-version'])
  const javaHome = javaHomeFromSettings(settings.stderr)
  const jarTool = path.join(javaHome, 'bin', exe('jar'))
  const java = path.join(javaHome, 'bin', exe('java'))
  const target = path.join(ROOT, 'auth-service', 'target')
  const jars = (await readdir(target)).filter(name => /^auth-service-.*\.jar$/.test(name))
  if (jars.length !== 1) throw new Error('Package Auth once before migration to supply the pinned Flyway/JDBC runtime')
  const artifact = await realpath(path.join(target, jars[0]))
  if (!samePath(path.dirname(artifact), target)) throw new Error('Migration artifact must stay inside Auth target directory')
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(artifact)) hash.update(chunk)
  // Different packaged dependency sets never mix stale JARs. This non-secret
  // runtime cache is shared between generations, not their data or credentials.
  const runtime = migrationRuntimePath(hash.digest('hex'))
  await confinedDirectory(runtime, true)
  await runPrivate(jarTool, ['-xf', artifact, 'BOOT-INF/lib'], { cwd: runtime })
  return { java, runtime }
}

async function migrate(m) {
  await requireAppsStopped()
  const directory = generationPath(m.id)
  const { java, runtime } = await prepareMigrationRuntime()
  await withCluster(m, async () => {
    await verify(m)
    for (const d of m.databases) {
      const result = await runPrivate(java, ['-Xmx128m', '-cp', path.join(runtime, 'BOOT-INF', 'lib', '*'),
        path.join(ROOT, 'scripts', 'synthetic', 'MigrateDatabase.java'), d.service, m.id,
        path.join(directory, `${d.service}.properties`), path.join(ROOT, `${d.service}-service`, 'src', 'main', 'resources', 'db', 'migration')], { timeout: 60000 })
      const evidence = result.stdout.trim().split(/\r?\n/).find(line => /^MIGRATION_OK [a-z]+ applied=\d+ pending=0$/.test(line))
      if (!evidence) throw new Error(`No verified migration result for ${d.service}`)
      console.log(evidence)
    }
  })
}

export async function main(args) {
  const { command, confirmation } = parseArgs(args)
  if (command === 'help') {
    console.log('Isolated synthetic PostgreSQL (Windows, port 15432):\n  node scripts/synthetic-db.mjs init|status|start|stop|verify|migrate\n  node scripts/synthetic-db.mjs reset --confirm CURRENT-GENERATION-ID\nReset requires all isolated helpers stopped and creates a new generation; previous data is retained. Seed identities separately with synthetic-demo.mjs. See docs/SYNTHETIC_BOOTSTRAP.md.')
    return
  }
  await lock(async () => {
    if (command === 'init') {
      try { await readPrivate(path.join(STATE, 'active.json')); throw new Error('Synthetic generation already exists; use status or explicitly confirmed reset') }
      catch (error) { if (error.code !== 'ENOENT') throw error }
      return createGeneration()
    }
    const m = await currentManifest()
    if (command === 'reset') {
      if (confirmation !== m.id) throw new Error('Reset confirmation does not match the active generation; nothing changed')
      console.log(`Previous synthetic generation retained: ${m.id}`)
      return createGeneration()
    }
    if (command === 'start') { await startCluster(m); console.log(`Owned synthetic PostgreSQL ready on ${PORT}; use stop when done.`) }
    if (command === 'stop') { await stopCluster(m); console.log('Owned synthetic PostgreSQL stopped; all data retained.') }
    if (command === 'status') console.log(`Synthetic generation ${m.id}: ${await ownedPostgres(m) ? 'RUNNING' : 'STOPPED'}; private settings: ${generationPath(m.id)}; shared PostgreSQL untouched.`)
    if (command === 'verify') await withCluster(m, () => verify(m))
    if (command === 'migrate') await migrate(m)
  })
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1 })
}
