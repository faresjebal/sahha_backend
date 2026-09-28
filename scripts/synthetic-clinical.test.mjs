import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { test } from 'node:test'
import { ACCOUNT_KEYS, accountIdentity } from './synthetic-demo.mjs'
import { clinicalDraft, emptyClinicalDraft, matchesClinicalDraft, seedClinical, validateClinicalState } from './synthetic/clinical-seed.mjs'

function fixture() {
  const generation=randomUUID(),orgA=randomUUID(),orgB=randomUUID(),membership=randomUUID()
  const scheduling={generation,appointmentId:randomUUID(),patientId:randomUUID(),registrationId:randomUUID()}
  const seed={generation,organisations:{A:orgA,B:orgB},memberships:{'A:doctorA':membership}}
  const accounts=ACCOUNT_KEYS.map(accountIdentity),state={kind:'sahha-synthetic-clinical-v1',generation,appointmentId:scheduling.appointmentId}
  const clients=[],calls=[],saved=[]
  const model={record:null,created:0,corrections:0,completes:true,dropFinalization:false,dropCorrection:false,normalizeCommandTimestamp:false}
  const createClient=()=>{
    const client={key:null,org:null,cookies:new Map(),disposed:false,
      async login(account){this.key=account.key;this.cookies.set('test','memory-only');return {userId:account.id}},
      async select(org){this.org=org;return {activeOrganisationId:org}},
      dispose(){this.disposed=true;this.cookies.clear()},
      async request(route,method='GET',body,expected=[200]) {
        calls.push({key:this.key,method,route,body:structuredClone(body)})
        let status=200,result={}
        if(route==='/api/v1/auth/logout') status=204
        else if(route==='/api/v1/organisations/memberships') result=[]
        else if(route.startsWith('/api/v1/consultations/referral-sources')) result={items:[],totalPages:0}
        else if(route==='/api/v1/consultations' && method==='POST') {
          assert.equal(body.appointmentId,scheduling.appointmentId)
          if(!model.record) {
            const empty=Object.fromEntries(Object.entries(clinicalDraft(0)).map(([key,value])=>[key,key==='version'?0:Array.isArray(value)?[]:null]))
            model.record={...empty,id:randomUUID(),organisationId:orgA,appointmentId:scheduling.appointmentId,patientId:scheduling.patientId,
              patientRegistrationId:scheduling.registrationId,doctorUserId:accountIdentity('doctorA').id,doctorMembershipId:membership,
              status:'DRAFT',finalizedAt:null,finalizedByUserId:null,corrections:[]}
            model.created++;status=201
          }
          result=model.record
        } else if(route.startsWith('/api/v1/consultations/')) {
          if(this.key!=='doctorA' || this.org!==orgA) status=this.key==='patient'?401:['doctorB','unrelatedDoctor'].includes(this.key)?404:403
          else if(route.endsWith('/record')) result=model.record
          else if(route.endsWith('/draft-content')) {
            if(model.record.status==='FINALIZED') status=409
            else {
              assert.equal(saved.at(-1).consultationId,model.record.id,'Persist clinical identity before changing content')
              assert.equal(body.version,model.record.version)
              model.record={...model.record,...structuredClone(body),version:model.record.version+1};result=model.record
            }
          } else if(route.endsWith('/finalize')) {
            if(model.record.status==='FINALIZED') status=409
            else {
              model.record={...model.record,status:'FINALIZED',version:model.record.version+1,finalizedAt:'2026-09-17T09:00:00Z',finalizedByUserId:accountIdentity('doctorA').id}
              result=model.record
              if(model.dropFinalization){model.dropFinalization=false;throw new Error('Synthetic lost finalization response')}
            }
          } else if(route.endsWith('/corrections')) {
            assert.equal(model.record.corrections.length,0);assert.equal(body.version,model.record.version)
            const correction={id:randomUUID(),...body,oldValue:model.record.additionalNotes,actorUserId:accountIdentity('doctorA').id,correctedAt:'2026-09-17T09:01:00Z'}
            model.record={...model.record,additionalNotes:body.newValue,corrections:[correction],version:model.record.version+1}
            model.corrections++;result=model.record
            if(model.dropCorrection){model.dropCorrection=false;throw new Error('Synthetic lost correction response')}
            if(model.normalizeCommandTimestamp) result={...result,corrections:[{...correction,correctedAt:'2026-09-17T09:01:00.000000001Z'}]}
          } else assert.fail(`Unexpected clinical route ${route}`)
        } else if(route.startsWith('/api/v1/appointments/')) result={id:scheduling.appointmentId,status:model.completes && model.record?.status==='FINALIZED'?'COMPLETED':'IN_PROGRESS'}
        else if(route.startsWith('/api/v1/clinical/patients/')) {
          if(model.completes && model.record?.status==='FINALIZED') status=404
          result={organisationId:orgA,patientId:scheduling.patientId,careRelationship:{appointmentId:scheduling.appointmentId},encounters:[]}
        }
        else assert.fail(`Unexpected fake route ${route}`)
        if(!expected.includes(status)) throw Object.assign(new Error(`Synthetic expected ${status}`),{status})
        return structuredClone(result)
      }}
    clients.push(client);return client
  }
  return {model,state,scheduling,seed,calls,clients,run:()=>seedClinical(accounts,seed,scheduling,state,async value=>saved.push(structuredClone(value)),createClient,async()=>{})}
}
test('clinical journal cannot point at another generation/appointment/resource path',()=>{
  const f=fixture();assert.equal(validateClinicalState(f.state,f.seed.generation,f.scheduling.appointmentId),f.state)
  for(const change of [{kind:'other'},{generation:randomUUID()},{appointmentId:randomUUID()},{consultationId:'../outside'},{unknown:true}]) {
    assert.throws(()=>validateClinicalState({...f.state,...change},f.seed.generation,f.scheduling.appointmentId))
  }
})
test('draft comparison accepts only complete known fixtures and never classifies changed text as empty',()=>{
  const draft=clinicalDraft(3)
  assert.equal(matchesClinicalDraft(draft),true);assert.equal(emptyClinicalDraft(draft),false)
  draft.diagnoses[0].label='User-authored diagnosis'
  assert.equal(matchesClinicalDraft(draft),false)
  assert.equal(matchesClinicalDraft({...clinicalDraft(3),medications:[]}),false)
})
test('clinical seed and repeat preserve the same signed record and single append-only correction',async()=>{
  const f=fixture(),first=await f.run(),snapshot=structuredClone(f.model.record)
  const repeated=await f.run()
  assert.equal(first.status,'FINALIZED');assert.ok(first.checks>=25)
  assert.equal(repeated.consultationId,first.consultationId);assert.equal(repeated.correctionId,first.correctionId)
  assert.equal(f.model.created,1);assert.equal(f.model.corrections,1);assert.deepEqual(f.model.record,snapshot)
  assert.ok(f.clients.every(client=>client.disposed && client.cookies.size===0))
})
test('lost committed finalization response recovers without replacing or finalizing the record twice',async()=>{
  const f=fixture();f.model.dropFinalization=true
  await assert.rejects(f.run(),/lost finalization response/)
  const id=f.model.record.id,finalizedAt=f.model.record.finalizedAt
  const result=await f.run()
  assert.equal(result.consultationId,id);assert.equal(f.model.record.finalizedAt,finalizedAt)
  assert.equal(f.model.created,1);assert.equal(f.model.corrections,1)
})
test('lost correction response is resolved by reading correction history, never appending twice',async()=>{
  const f=fixture();f.model.dropCorrection=true
  await assert.rejects(f.run(),/lost correction response/)
  const id=f.model.record.corrections[0].id
  const result=await f.run()
  assert.equal(result.correctionId,id);assert.equal(f.model.corrections,1)
})
test('a changed finalised record is refused without any clinical write',async()=>{
  const f=fixture();await f.run();f.model.record.clinicalAssessment='User-authored assessment'
  const start=f.calls.length
  await assert.rejects(f.run(),/effective corrected content/)
  assert.ok(!f.calls.slice(start).some(call=>call.method!=='GET' && call.route.startsWith('/api/v1/consultations')))
  assert.equal(f.model.record.clinicalAssessment,'User-authored assessment')
})
test('no successful clinical gate is reported without the actual appointment completion projection',async()=>{
  const f=fixture();f.model.completes=false
  await assert.rejects(f.run(),/real Kafka finalisation-to-appointment completion/)
  assert.equal(f.calls.filter(call=>call.route.startsWith('/api/v1/appointments/')).length,121)
  assert.ok(f.clients.every(client=>client.disposed))
})
test('immutable-write checks use persisted snapshots even if command timestamp precision is normalized',async()=>{
  const f=fixture();f.model.normalizeCommandTimestamp=true
  const result=await f.run()
  assert.equal(result.status,'FINALIZED');assert.equal(f.model.corrections,1)
})
