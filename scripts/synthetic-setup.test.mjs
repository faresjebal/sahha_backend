import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {test} from 'node:test'
import {createOperationLock} from './synthetic/operation-lock.mjs'
import {SETUP_STEPS,assertRetained,checkPrerequisiteVersions,executeSetup,identityDigests,parseSetupArgs} from './synthetic/setup-contracts.mjs'
import {SETUP_PORTS,requireSetupStopped} from './synthetic/setup-preflight.mjs'
import {main} from './synthetic-setup.mjs'
const deferred=()=>{let resolve;const promise=new Promise(value=>{resolve=value});return {promise,resolve}}
function lockFixture() {
  let occupied=false,acquired=0,released=0
  const lock=createOperationLock(async()=>{assert.equal(occupied,false,'already locked');occupied=true;acquired++
    return async()=>{occupied=false;released++}})
  return {lock,state:()=>({occupied,acquired,released})}
}
test('nested commands retain one operation lock and return their values',async()=>{
  const value=lockFixture()
  assert.equal(await value.lock(()=>value.lock(()=>value.lock(async()=>7))),7)
  assert.deepEqual(value.state(),{occupied:false,acquired:1,released:1})
})
test('independent async callers cannot bypass an occupied operation lock',async()=>{
  const value=lockFixture(),entered=deferred(),finish=deferred()
  const first=value.lock(async()=>{entered.resolve();await finish.promise})
  await entered.promise
  try{await assert.rejects(value.lock(async()=>assert.fail('No concurrent operation')),/already locked/)}
  finally{finish.resolve();await first}
})
test('failure releases the lock and descendants after completion acquire afresh',async()=>{
  const value=lockFixture(),resume=deferred();let later
  await assert.rejects(value.lock(async()=>{later=resume.promise.then(()=>value.lock(async()=>true));throw new Error('synthetic failure')}),/synthetic failure/)
  resume.resolve();assert.equal(await later,true)
  assert.deepEqual(value.state(),{occupied:false,acquired:2,released:2})
})
test('already-started nested operations finish cleanup before the outer lock releases',async()=>{
  const value=lockFixture(),entered=deferred(),finish=deferred();let nested,outerDone=false
  const outer=value.lock(async()=>{nested=value.lock(async()=>{entered.resolve();await finish.promise})}).then(()=>{outerDone=true})
  await entered.promise;assert.equal(value.state().occupied,true);assert.equal(outerDone,false)
  finish.resolve();await outer;await nested;assert.equal(value.state().released,1)
})
test('only check/run are accepted before filesystem reads or side effects',async()=>{
  for(const command of ['check','run'])assert.equal(parseSetupArgs([command]),command)
  for(const args of [[],['reset'],['run','--force'],['run','--reconcile-empty'],['run','--host','remote'],['run','../outside']]) {
    await assert.rejects(main(args),/Use: node scripts\/synthetic-setup/)
  }
})
const versions=()=>({platform:'win32',node:'v22.17.1',java:'21.0.8',postgres:['18.1','18.1','18.1','18.1'],kafka:'4.3.1',storage:'4.41'})
test('preflight refuses unsupported platforms and mixed native major versions',()=>{
  checkPrerequisiteVersions(versions())
  for(const change of [{platform:'linux'},{node:'v24.0.0'},{java:'17.0.1'},{postgres:['18.1','18.1','17.1','18.1']},
    {postgres:['18.1']},{kafka:'latest'},{storage:'4.42'}])assert.throws(()=>checkPrerequisiteVersions({...versions(),...change}))
})
test('occupied isolated ports fail closed and shared PostgreSQL is never probed',async()=>{
  assert.equal(SETUP_PORTS.includes(5432),false)
  for(const port of SETUP_PORTS)await assert.rejects(requireSetupStopped(async value=>value===port),/occupied/)
  const probed=[];await requireSetupStopped(async value=>{probed.push(value);return false});assert.deepEqual(probed,SETUP_PORTS)
})
test('retention digests include only pinned identifiers, not passwords or clinical content',()=>{
  const generation=randomUUID(),id=randomUUID()
  const result=identityDigests('workflow-messages.json',{generation,messageId:id,body:'private content',password:'secret'},generation)
  assert.deepEqual(Object.keys(result),['workflow-messages.json:messageId']);assert.match(Object.values(result)[0],/^[a-f0-9]{64}$/)
  assert.equal(JSON.stringify(result).includes(id),false)
  assert.throws(()=>identityDigests('workflow-messages.json',{generation,messageId:'private content'},generation),/identifier/)
  assert.throws(()=>identityDigests('unknown.json',{generation},generation),/provenance/)
  assert.throws(()=>identityDigests('workflow-messages.json',{generation:randomUUID()},generation),/provenance/)
})
test('nested booking and organisation identities are retained without clinical payload traversal',()=>{
  const generation=randomUUID(),id=randomUUID()
  const schedule=identityDigests('workflow-scheduling.json',{generation,booking:{bookingRequestId:id,startsAt:'private'}},generation)
  assert.deepEqual(Object.keys(schedule),['workflow-scheduling.json:booking.bookingRequestId'])
  const seed=identityDigests('demo-seed-report.json',{generation,organisations:{A:id},departments:{A:id},memberships:{'A:doctor':id}},generation)
  assert.equal(Object.keys(seed).length,3)
})
test('retention allows newly created identities but never changed or missing originals',()=>{
  assertRetained({original:'first'},{original:'first',added:'second'})
  for(const after of [{},{original:'changed'}])assert.throws(()=>assertRetained({original:'first'},after),/changed or disappeared/)
})
function execution(run=async()=>{}) {
  const calls=[],checkpoints=[];let active=0,maxActive=0,probes=0
  return {calls,checkpoints,statistics:()=>({maxActive,probes}),options:{
    snapshot:async()=>({generation:'retained'}),quiescent:async()=>{assert.equal(active,0);probes++},
    checkpoint:async value=>{checkpoints.push(structuredClone(value))},
    run:async step=>{calls.push(step);active++;maxActive=Math.max(maxActive,active);try{await run(step)}finally{active--}}}}
}
test('all sixteen allowlisted stages run in dependency order without overlapping helpers',async()=>{
  const value=execution();assert.deepEqual(await executeSetup(value.options),SETUP_STEPS)
  assert.deepEqual(value.calls,SETUP_STEPS);assert.equal(value.statistics().maxActive,1)
  assert.equal(value.statistics().probes,SETUP_STEPS.length*2)
  assert.equal(value.checkpoints.at(-1).status,'complete')
  assert.ok(SETUP_STEPS.indexOf('files-unselected')<SETUP_STEPS.indexOf('referral-clinical'))
  assert.ok(SETUP_STEPS.indexOf('referral-files')<SETUP_STEPS.indexOf('referral-revocation'))
  assert.equal(SETUP_STEPS.at(-1),'browser-recovery')
})
test('repeat execution revalidates every stage instead of trusting an old success checkpoint',async()=>{
  const value=execution();await executeSetup(value.options);await executeSetup(value.options)
  assert.deepEqual(value.calls,[...SETUP_STEPS,...SETUP_STEPS])
})
test('stage failures run the cleanup probe, suppress raw diagnostics and prevent later stages',async()=>{
  const value=execution(async step=>{if(step==='redis')throw new Error('secret diagnostic')})
  await assert.rejects(executeSetup(value.options),error=>error.message.includes('stopped at redis')&&!error.message.includes('secret'))
  assert.deepEqual(value.calls,['database','migrations','redis']);assert.equal(value.statistics().probes,6)
  assert.deepEqual(value.checkpoints.at(-1),{status:'failed',completed:['database','migrations'],activeStep:'redis'})
})
test('failed cleanup and changed newly-created identities cannot be counted as completed stages',async()=>{
  const value=execution();let calls=0
  value.options.quiescent=async()=>{if(++calls===2)throw new Error('helper still active')}
  await assert.rejects(executeSetup(value.options),/stopped at database/)
  assert.deepEqual(value.checkpoints.at(-1).completed,[])
  const changed=execution();let snapshots=0
  changed.options.snapshot=async()=>++snapshots===1?{}:{created:snapshots===2?'original':'changed'}
  await assert.rejects(executeSetup(changed.options),/stopped at migrations/)
  assert.deepEqual(changed.checkpoints.at(-1).completed,['database'])
})
