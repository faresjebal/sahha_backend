// Opt-in native acceptance. Uses only the current isolated synthetic generation.
// Leaves its accounts, organisations and private evidence available for reuse.
import assert from 'node:assert/strict'
import { createHash, randomUUID } from 'node:crypto'
import { unlink } from 'node:fs/promises'
import path from 'node:path'
import { test } from 'node:test'
import { ROOT, SERVICES, tcpOpen } from './sahha.mjs'
import { cleanEnvironment, currentManifest, generationPath, lock, parsePrivateJson, PORT, prepareMigrationRuntime, privateFile, readPrivate, runPrivate, withCluster } from './synthetic-db.mjs'
import { accountsProperties, validateAccounts } from './synthetic-demo.mjs'
import { IDENTITY_SERVICES, withSyntheticApps } from './synthetic/app-runtime.mjs'

const cli = async () => {
  const result = await runPrivate(process.execPath, ['scripts/synthetic-demo.mjs','seed'], {timeout:600000,allowFailure:true})
  if (result.code !== 0) {
    // The CLI owns/redacts these bounded diagnostics. Never relay arbitrary
    // process output, private files or successful credential-bearing responses.
    const safe = result.stderr.trim().split(/\r?\n/).find(line => /^Synthetic (Gateway|acceptance failed):? /.test(line))
    throw new Error(safe ?? 'Synthetic seed failed; run its CLI for safe diagnostics')
  }
  return result
}
async function stopped() {
  for (const port of [PORT,...SERVICES.map(service => service.port)]) assert.equal(await tcpOpen(port),false,`Temporary port ${port} must be stopped`)
}
async function sql(m, domain, query) {
  const database = m.databases.find(value => value.service === domain)
  return (await runPrivate(path.join(m.pgBin,'psql.exe'), ['-X','-w','-qAt','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',String(PORT),'-U',database.username,'-d',database.database],
    {input:query,env:{...cleanEnvironment(),PGPASSWORD:database.password,PGCONNECT_TIMEOUT:'3'}})).stdout.trim()
}
async function snapshot(m) {
  const results = {}
  // Compare hashes in memory, never expose credentials, password hashes or DTOs
  // in assertion output. Login timestamps/session audit naturally change.
  const accounts = await sql(m,'auth',"SELECT id,normalized_email,password_hash,status,email_verified_at FROM user_account ORDER BY id;")
  results.accounts = createHash('sha256').update(accounts).digest('hex')
  assert.equal(await sql(m,'auth','SELECT count(*) FROM user_account;'),'8')
  assert.equal(await sql(m,'auth','SELECT count(*) FROM user_platform_role WHERE active;'),'1')
  assert.equal(await sql(m,'auth',"SELECT count(*) FROM security_event WHERE event_type LIKE 'SYNTHETIC_%';"),'9')
  assert.equal(await sql(m,'auth',"SELECT count(*) FROM auth_outbox_event WHERE event_type LIKE 'SYNTHETIC_%';"),'9')
  for (const [table,count] of Object.entries({organisation:2,organisation_membership:8,organisation_membership_role:8,department:2,staff_invitation:6,staff_department_assignment:6,doctor_profile:4})) {
    assert.equal(await sql(m,'organisation',`SELECT count(*) FROM ${table};`),String(count),`Expected isolated ${table} count`)
    results[table] = createHash('sha256').update(await sql(m,'organisation',`SELECT row_to_json(t) FROM ${table} t ORDER BY id;`)).digest('hex')
  }
  assert.equal(await sql(m,'auth',"SELECT count(*) FROM user_session WHERE status='ACTIVE';"),'0','Demo login sessions are logged out')
  return results
}

test('native Gateway seed, repeat preservation and offline refusal boundaries', {skip:process.env.SAHHA_SYNTHETIC_DEMO_TEST !== 'true',timeout:1500000}, async () => {
  assert.equal(process.platform,'win32')
  await stopped()
  const m = await currentManifest(), directory = generationPath(m.id)
  const first = await cli()
  assert.match(first.stdout,/Synthetic role seed passed \d+ Gateway assertions/)
  await stopped()
  const before = await lock(() => withCluster(m,() => snapshot(m)))
  console.log('Native demo: real Gateway role/tenant checks and expected isolated row counts passed.')
  const repeated = await cli()
  assert.match(repeated.stdout,/SYNTHETIC_AUTH_OK accounts=8 created=0/)
  assert.match(repeated.stdout,/Synthetic role seed passed \d+ Gateway assertions/)
  await stopped()
  const after = await lock(() => withCluster(m,() => snapshot(m)))
  assert.deepEqual(after,before,'Repeat seed preserves identities, credentials, organisations and staff aggregates')
  console.log('Native demo: repeat created no duplicate identity/staff/profile records and preserved credentials.')

  await lock(async () => {
    const {java,runtime} = await prepareMigrationRuntime()
    const db = await readPrivate(path.join(directory,'auth.properties'))
    const accounts = validateAccounts(parsePrivateJson(await readPrivate(path.join(directory,'demo-accounts.json'))),m.id)
    const created = []
    try {
      await withCluster(m,async () => {
        const wrongPassword = structuredClone(accounts)
        wrongPassword.accounts[7].password = 'Demo-Aa1!'+'A'.repeat(32)
        const cases = [
          [db.replace('127.0.0.1:15432','127.0.0.1:5432'),accountsProperties(accounts)],
          [db.replace('sahha_demo_auth_app','sahha_auth_app'),accountsProperties(accounts)],
          [db.replace(m.id,randomUUID()),accountsProperties(accounts)],
          [db,accountsProperties({...accounts,generation:randomUUID()})],
          [db,accountsProperties(wrongPassword)]
        ]
        for (const [properties,credentials] of cases) {
          const prefix = path.join(directory,`seed-guard-${randomUUID()}`)
          const dbFile = prefix+'-db.properties', accountsFile = prefix+'-accounts.properties'
          await privateFile(dbFile,properties); created.push(dbFile)
          await privateFile(accountsFile,credentials); created.push(accountsFile)
          const denied = await runPrivate(java,['-Xmx128m','-cp',path.join(runtime,'BOOT-INF','lib','*'),path.join(ROOT,'auth-service','src','synthetic','SeedSyntheticAccounts.java'),m.id,dbFile,accountsFile],{allowFailure:true})
          assert.notEqual(denied.code,0)
          assert.match(denied.stderr,/raw diagnostics suppressed/)
          for (const account of accounts.accounts) assert.equal((denied.stdout+denied.stderr).includes(account.password),false,'No credentials in diagnostics')
        }
        assert.deepEqual(await snapshot(m),after,'All refused invocations preserve existing data')
      })
      // An edited generation-local file cannot silently point a service elsewhere.
      const file = path.join(directory,'organisation.properties'), original = await readPrivate(file)
      try {
        await privateFile(file,original.replace('127.0.0.1:15432','127.0.0.1:5432'),'w')
        await assert.rejects(withSyntheticApps(m.id,IDENTITY_SERVICES,() => assert.fail('Must refuse before launching')),/datasource settings differ/)
        await stopped()
      } finally { await privateFile(file,original,'w') }
    } finally { for (const file of created) await unlink(file) }
  })
  await stopped()
  console.log('Native demo: five offline refusals, exact datasource guard and owned cleanup passed; no database reset.')
})
