import test from 'node:test'
import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {main} from './synthetic-acceptance.mjs'
import {validateJointState} from './synthetic/joint-treatment-gate.mjs'
const state=()=>({kind:'sahha-joint-treatment-v1',generation:randomUUID(),organisationId:randomUUID(),
  registrationId:randomUUID(),sourceConsultationId:randomUUID(),appointments:{}})
test('acceptance refuses reset, arbitrary commands and extra arguments before launch',async()=>{
  for(const args of [[],['reset'],['verify','--reset']])await assert.rejects(()=>main(args),/Use:/)
})
test('joint-treatment journal binds generation and excludes credentials',()=>{
  const s=state();assert.equal(validateJointState(s,s.generation),s)
  assert.throws(()=>validateJointState(s,randomUUID()))
  assert.throws(()=>validateJointState({...s,accessToken:'private'},s.generation))
})
test('joint-treatment journal refuses fabricated positive and final evidence',()=>{
  const s=state()
  for(const key of ['positiveAt','browserAt','revokedAt','verifiedAt'])
    assert.throws(()=>validateJointState({...s,[key]:new Date().toISOString()},s.generation))
})
test('joint-treatment refuses foreign, partial or unrecognised appointment scope',()=>{
  const s=state()
  for(const appointments of [{doctorC:{}},{doctorA:{}},{doctorA:{appointmentId:randomUUID()}}])
    assert.throws(()=>validateJointState({...s,appointments},s.generation))
})
