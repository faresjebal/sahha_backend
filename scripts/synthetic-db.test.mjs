import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { test } from 'node:test'
import { ROOT } from './sahha.mjs'
import { DOMAINS, PORT, STATE, SYNTHETIC_DEPENDENCY_PORTS, cleanEnvironment, generationPath, hbaConfiguration, main, migrationRuntimePath, ownsPostgres, parseArgs, parsePrivateJson, postgresConfiguration, requireSyntheticDependenciesStopped, runPrivate, serviceProperties, validateManifest } from './synthetic-db.mjs'

const manifest = () => ({ kind:'sahha-isolated-synthetic-postgres-v1', id:randomUUID(), port:15432,
  admin:'sahha_synthetic_admin', adminPassword:randomBytes(32).toString('hex'), pgBin:path.resolve('test-postgres', 'bin'), systemIdentifier:'1234567890123456789',
  databases:DOMAINS.map(service => ({ service, database:`sahha_demo_${service}`, username:`sahha_demo_${service}_app`, password:randomBytes(32).toString('hex') })) })

test('fixed separate port and state never target shared PostgreSQL or its credential file', () => {
  assert.equal(PORT, 15432)
  assert.equal(STATE, path.join(ROOT, 'infrastructure', '.state', 'synthetic-postgres'))
  assert.equal(DOMAINS.length, 9)
  assert.ok(DOMAINS.includes('audit'))
})

test('migration runtime is fingerprinted so different packaged dependency sets cannot mix', () => {
  const first = randomBytes(32).toString('hex'), second = randomBytes(32).toString('hex')
  assert.equal(migrationRuntimePath(first), path.join(STATE, 'migration-runtime', first))
  assert.notEqual(migrationRuntimePath(first), migrationRuntimePath(second))
  for (const value of [undefined, '../outside', '', 'short', 'A'.repeat(64)]) assert.throws(() => migrationRuntimePath(value), /fingerprint/)
})

test('only generated UUID children can identify a generation', () => {
  const id = randomUUID()
  assert.equal(generationPath(id), path.join(STATE, id))
  for (const value of ['..', '.', '/', '../auth', '', undefined, 12, 'a/b', '00000000-0000-0000-0000-000000000000']) {
    assert.throws(() => generationPath(value), /Invalid synthetic generation/)
  }
})

test('reset requires the explicit current-generation confirmation shape', () => {
  const id = randomUUID()
  assert.deepEqual(parseArgs(['reset','--confirm',id]), { command:'reset',confirmation:id })
  for (const args of [['reset'], ['reset',id], ['reset','--force'], ['reset','--confirm','../data'], ['init','--port','5432'], ['stop','all'], ['drop'], ['purge'], ['status','--host','remote']]) {
    assert.throws(() => parseArgs(args))
  }
})

test('generation replacement refuses every isolated dependency listener before creating data', async () => {
  assert.equal(SYNTHETIC_DEPENDENCY_PORTS.length, 11)
  for (const occupied of SYNTHETIC_DEPENDENCY_PORTS) {
    await assert.rejects(requireSyntheticDependenciesStopped(async port => port === occupied), /helpers before init\/reset/)
  }
  const checked = []
  await requireSyntheticDependenciesStopped(async port => { checked.push(port); return false })
  assert.deepEqual(checked, SYNTHETIC_DEPENDENCY_PORTS)
  const source = await readFile(new URL('./synthetic-db.mjs', import.meta.url), 'utf8')
  assert.match(source, /async function createGeneration\(\) \{\s+await requireAppsStopped\(\)\s+await requireSyntheticDependenciesStopped\(\)/)
})

test('malformed commands fail before any state or process action', async () => {
  await assert.rejects(main(['reset']), /requires --confirm/)
  await assert.rejects(main(['start','--port','5432']), /unexpected arguments/)
})

test('private manifest restricts every database and owner independently', () => {
  const m = manifest()
  assert.equal(validateManifest(m), m)
  for (let index = 0; index < DOMAINS.length; index++) {
    for (const change of [{ database:'sahha_auth' }, { database:'postgres' }, { username:'postgres' }, { service:'foreign' }, { password:"x'; DROP DATABASE postgres;--" }]) {
      const copy = structuredClone(m)
      Object.assign(copy.databases[index], change)
      assert.throws(() => validateManifest(copy), /allowlist/)
    }
  }
})

test('invalid control manifests cannot redirect the host, port, generation or bootstrap role', () => {
  for (const change of [{ port:5432 }, { port:'15432' }, { id:'../outside' }, { kind:'production' }, { admin:'postgres' }, { adminPassword:'guessable' },
    { pgBin:'relative' }, { pgBin:path.resolve('path\nwith-newline') }, { databases:[] }, { systemIdentifier:'injected' }]) {
    assert.throws(() => validateManifest({ ...manifest(), ...change }))
  }
})

test('inherited PostgreSQL and Java injection variables never reach native subprocesses', () => {
  assert.deepEqual(cleanEnvironment({ PATH:'path', SystemRoot:'windows', PGHOST:'remote', PGSERVICEFILE:'secret', PGPASSFILE:'unrelated', PGOPTIONS:'unsafe', pgport:'5432',
    JAVA_TOOL_OPTIONS:'unsafe', _JAVA_OPTIONS:'unsafe', JDK_JAVA_OPTIONS:'unsafe', CLASSPATH:'unsafe' }), { PATH:'path', SystemRoot:'windows' })
})

test('corrupt private JSON never copies a parser excerpt into diagnostics', () => {
  assert.deepEqual(parsePrivateJson('{"id":"synthetic"}'), { id:'synthetic' })
  assert.throws(() => parsePrivateJson('synthetic-secret-that-must-not-be-printed'), error => {
    assert.match(error.message, /contents suppressed/)
    assert.ok(!error.message.includes('synthetic-secret'))
    return true
  })
})
test('native forward-slash PGDATA matches the exact Windows path only', { skip:process.platform !== 'win32' }, () => {
  const binary = 'C:\\Program Files\\PostgreSQL\\18\\bin\\postgres.exe'
  const data = path.join(generationPath(randomUUID()), 'data')
  assert.ok(ownsPostgres(`"${binary.replaceAll('\\','/')}" -D "${data.replaceAll('\\','/')}"`, binary, data, binary))
})

test('generated PostgreSQL policy is loopback-only, bounded and does not log SQL/parameters', () => {
  const value = postgresConfiguration()
  assert.match(value, /listen_addresses = '127\.0\.0\.1'/)
  assert.match(value, /port = 15432/)
  assert.match(value, /shared_buffers = '32MB'/)
  assert.match(value, /password_encryption = 'scram-sha-256'/)
  assert.match(value, /log_statement = 'none'/)
  assert.match(value, /log_min_error_statement = 'panic'/)
  assert.match(value, /unix_socket_directories = ''/)
})

test('host authentication allows only the exact database-role pair and rejects everything else', () => {
  const m = manifest()
  const value = hbaConfiguration(m)
  assert.equal(value.split('\n').filter(line => line.endsWith('scram-sha-256')).length, 10)
  for (const d of m.databases) assert.ok(value.includes(`host ${d.database} ${d.username} 127.0.0.1/32 scram-sha-256`))
  assert.match(value, /host all all 0\.0\.0\.0\/0 reject/)
  assert.match(value, /host all all ::\/0 reject/)
  assert.match(value, /local all all reject/)
  assert.equal(value.split('\n').some(line => !line.startsWith('#') && /\btrust\b/.test(line)), false)
})

test('per-service settings contain only that service credential, never the operator credential', () => {
  const m = manifest()
  for (const d of m.databases) {
    const value = serviceProperties(m, d)
    assert.ok(value.includes(`jdbc:postgresql://127.0.0.1:15432/${d.database}`))
    assert.ok(value.includes(d.password))
    assert.ok(value.includes('spring.flyway.clean-disabled=true'))
    for (const other of m.databases.filter(item => item !== d)) assert.ok(!value.includes(other.password))
    assert.ok(!value.includes(m.adminPassword))
  }
  assert.throws(() => serviceProperties(m, { ...m.databases[0] }), /Unknown/)
})

test('shutdown requires both exact postgres binary and exact PGDATA, not a reused PID', () => {
  const binary = path.join(manifest().pgBin, process.platform === 'win32' ? 'postgres.exe' : 'postgres')
  const data = path.join(generationPath(randomUUID()), 'data')
  assert.ok(ownsPostgres(`"${binary}" -D "${data}"`, binary, data, binary))
  for (const command of ['', undefined, `"${binary}" -D "${ROOT}"`, `"${binary}" --other "${data}"`, `"${binary}" -D "${data}-other"`]) {
    assert.equal(ownsPostgres(command, binary, data, binary), false)
  }
  assert.equal(ownsPostgres(`"${binary}" -D "${data}"`, path.resolve('foreign.exe'), data, binary), false)
})

test('subprocess failure never echoes sensitive diagnostic output', async () => {
  await assert.rejects(runPrivate(process.execPath, ['-e', 'process.stderr.write("synthetic-sensitive-value");process.exit(1)']), error => {
    assert.match(error.message, /raw diagnostics suppressed/)
    assert.ok(!error.message.includes('synthetic-sensitive-value'))
    return true
  })
})

test('subprocess timeout is bounded and redacted', async () => {
  await assert.rejects(runPrivate(process.execPath, ['-e', 'setInterval(()=>{},1000)'], { timeout:100 }), /timeout/)
})

test('background control commands can use no inherited pipes', async () => {
  const result = await runPrivate(process.execPath, ['-e', 'process.stdout.write("private");process.stderr.write("private")'], { quiet:true })
  assert.deepEqual(result, { code:0, stdout:'', stderr:'' })
})

test('tooling does not add destructive database cleanup or application bypasses', async () => {
  const source = await readFile(new URL('./synthetic-db.mjs', import.meta.url), 'utf8')
  assert.doesNotMatch(source, /DROP DATABASE|TRUNCATE|\.env\.database\.local|pg_ctl.+register/)
  assert.match(source, /--auth=scram-sha-256/)
  assert.match(source, /finally \{ await stopCluster\(m\) \}/)
  const migration = await readFile(new URL('./synthetic/MigrateDatabase.java', import.meta.url), 'utf8')
  assert.match(migration, /\.cleanDisabled\(true\)/)
  assert.match(migration, /\.baselineOnMigrate\(false\)/)
  assert.match(migration, /sahha_synthetic\.environment/)
  assert.doesNotMatch(migration, /flyway\.(clean|repair|baseline)\(/)
})
