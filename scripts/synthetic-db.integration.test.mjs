// Opt-in real native database gate. Generates/retains synthetic data only;
// never touches the shared PostgreSQL instance or existing application data.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import path from 'node:path'
import { test } from 'node:test'
import { DOMAINS, PORT, STATE, cleanEnvironment, generationPath, runPrivate, validateManifest } from './synthetic-db.mjs'
import { ROOT, tcpOpen } from './sahha.mjs'

const cli = (args, allowFailure = false) => runPrivate(process.execPath, ['scripts/synthetic-db.mjs', ...args], { timeout: 600000, allowFailure })
test('reset refuses an occupied dependency without replacing the active generation', { skip:process.env.SAHHA_SYNTHETIC_DB_RESET_REFUSAL_TEST !== 'true', timeout:30000 }, async () => {
  const previous = await current()
  const occupied = createServer(socket => socket.end())
  await new Promise((resolve, reject) => { occupied.once('error', reject); occupied.listen(16379, '127.0.0.1', resolve) })
  try {
    const refused = await cli(['reset', '--confirm', previous.id], true)
    assert.notEqual(refused.code, 0)
    assert.match(refused.stderr, /helpers before init\/reset/)
    assert.equal((await current()).id, previous.id)
    assert.equal(occupied.listening, true)
  } finally { await new Promise(resolve => occupied.close(resolve)) }
  assert.equal(await tcpOpen(16379), false)
})
async function current() {
  const active = JSON.parse(await readFile(path.join(STATE, 'active.json'), 'utf8'))
  return validateManifest(JSON.parse(await readFile(path.join(generationPath(active.id), 'manifest.json'), 'utf8')))
}
async function ownerSql(m, sql) {
  const d = m.databases[0]
  return runPrivate(path.join(m.pgBin, 'psql.exe'), ['-X','-w','-qAt','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',String(PORT),'-U',d.username,'-d',d.database],
    { input:sql, env:{ ...cleanEnvironment(), PGPASSWORD:d.password, PGCONNECT_TIMEOUT:'3' } })
}

test('native synthetic isolation, migrations, repeat startup and recoverable reset', { skip:process.env.SAHHA_SYNTHETIC_DB_TEST !== 'true', timeout:900000 }, async () => {
  assert.equal(process.platform, 'win32', 'Native process-ownership gate is Windows-only')
  assert.equal(await tcpOpen(PORT), false, 'Stop the owned synthetic instance before this gate')
  let previous
  try { previous = await current() }
  catch (error) {
    if (error.code !== 'ENOENT') throw error
    const initialized = await cli(['init'])
    assert.match(initialized.stdout, /101 checks passed/)
    previous = await current()
  }
  let checks = 0
  try {
    const initial = await cli(['verify'])
    assert.match(initial.stdout, /101 checks passed/); checks++
    assert.equal(await tcpOpen(PORT), false); checks++
    console.log('Native gate: owner, cross-database, password and marker checks passed; instance stopped.')
    for (let pass = 0; pass < 2; pass++) {
      const result = await cli(['migrate'])
      for (const domain of DOMAINS) { assert.match(result.stdout, new RegExp(`MIGRATION_OK ${domain} applied=\\d+ pending=0`)); checks++ }
      assert.equal(await tcpOpen(PORT), false); checks++
      console.log(`Native gate: all nine owned migrations ${pass ? 'revalidated' : 'applied'}; instance stopped.`)
    }
    const wrong = await cli(['reset','--confirm','00000000-0000-4000-8000-000000000000'], true)
    assert.notEqual(wrong.code, 0); checks++
    assert.equal((await current()).id, previous.id); checks++
    const occupied = createServer()
    await new Promise((resolve, reject) => { occupied.once('error', reject); occupied.listen(PORT, '127.0.0.1', resolve) })
    try {
      const refused = await cli(['start'], true)
      assert.notEqual(refused.code, 0); checks++
      assert.equal(occupied.listening, true); checks++
    } finally { await new Promise(resolve => occupied.close(resolve)) }
    await cli(['start'])
    await ownerSql(previous, "CREATE TABLE public.synthetic_reset_sentinel (label text PRIMARY KEY); INSERT INTO public.synthetic_reset_sentinel VALUES ('synthetic-preserved');")
    const refused = await cli(['reset','--confirm',previous.id], true)
    assert.notEqual(refused.code, 0); checks++
    assert.equal((await current()).id, previous.id); checks++
    await cli(['stop'])
    const reset = await cli(['reset','--confirm',previous.id])
    assert.match(reset.stdout, /101 checks passed/); checks++
    const next = await current()
    assert.notEqual(next.id, previous.id); checks++
    assert.notEqual(next.systemIdentifier, previous.systemIdentifier); checks++
    assert.equal((await readFile(path.join(generationPath(previous.id), 'data', 'PG_VERSION'), 'utf8')).trim(), '18'); checks++
    assert.equal(await tcpOpen(PORT), false); checks++
    console.log('Native gate: occupied-port/running-reset guards passed; new generation created and previous generation retained.')
    await cli(['start'])
    assert.equal((await ownerSql(next, "SELECT to_regclass('public.synthetic_reset_sentinel') IS NULL;")).stdout.trim(), 't'); checks++
    await cli(['stop'])
    // Read the retained generation with its own pg_ctl and exact manifest PGDATA.
    // No active pointer is rewritten, no dataset is deleted, and finally stops it.
    const oldData = path.join(generationPath(previous.id), 'data')
    const pgCtl = path.join(previous.pgBin, 'pg_ctl.exe')
    try {
      await runPrivate(pgCtl, ['start','-D',oldData,'-l',path.join(generationPath(previous.id),'postgres.log'),'-w','-t','30'], { quiet:true })
      assert.equal((await ownerSql(previous, 'SELECT label FROM public.synthetic_reset_sentinel;')).stdout.trim(), 'synthetic-preserved'); checks++
    } finally { await runPrivate(pgCtl, ['stop','-D',oldData,'-m','fast','-w','-t','30'], { quiet:true }) }
    const finalMigration = await cli(['migrate'])
    for (const domain of DOMAINS) { assert.match(finalMigration.stdout, new RegExp(`MIGRATION_OK ${domain} applied=\\d+ pending=0`)); checks++ }
    assert.equal(await tcpOpen(PORT), false); checks++
    console.log(`Native gate: ${checks} assertions passed; retained data recovered, active generation migrated, all temporary database processes stopped. Root: ${ROOT}`)
  } finally {
    // The CLI checks the exact binary/PGDATA/PID before any stop.
    await cli(['stop'])
  }
})
