import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { test } from 'node:test'
import { accountIdentity, ACCOUNT_KEYS } from './synthetic-demo.mjs'
import { PATIENT, availabilityBody, main, seedScheduling, validateWorkflowState } from './synthetic-workflow.mjs'

function fixture() {
  const generation=randomUUID(), orgA=randomUUID(), orgB=randomUUID()
  const accounts=ACCOUNT_KEYS.map(accountIdentity), seed={generation,organisations:{A:orgA,B:orgB}}
  const state={kind:'sahha-synthetic-scheduling-v1',generation}
  const calls=[],clients=[],availability={},commands=new Map(),saved=[]
  const model={registration:null,appointment:null,created:0,dropBookingResponse:false,failLogout:false}
  const now=()=>new Date('2026-09-17T09:00:00Z')
  const createClient=()=>{
    const client={cookies:new Map(),key:null,org:null,disposed:false,
      async login(account){this.key=account.key;this.cookies.set('test','in-memory');return {userId:account.id}},
      async select(org){this.org=org;return {activeOrganisationId:org}},
      dispose(){this.disposed=true;this.cookies.clear()},
      async request(route,method='GET',body,expected=[200]) {
        calls.push({key:this.key,route,method,body:structuredClone(body)})
        let status=200,result={}
        if (route==='/api/v1/auth/logout') {
          if (model.failLogout && this.key==='doctorA') throw new Error('Synthetic logout failure')
          status=204
        } else if (route==='/api/v1/organisations/memberships') result=[]
        else if (route.startsWith('/api/v1/patients?')) result={items:model.registration?[model.registration]:[],totalPages:model.registration?1:0}
        else if (route==='/api/v1/patients/duplicate-check') result={reviewRequired:!!model.registration,candidates:model.registration?[{patientId:model.registration.patientId,activeOrganisationRegistrationId:model.registration.registrationId}]:[]}
        else if (route==='/api/v1/patients' && method==='POST') {
          assert.equal(this.key,'receptionist');assert.equal(model.registration,null)
          model.registration={...PATIENT,registrationId:randomUUID(),patientId:randomUUID(),organisationId:orgA,registeredBy:accountIdentity('receptionist').id,
            registrationStatus:'ACTIVE',medicalRecordNumber:'PT-0123456789AB'}
          status=201;result=model.registration
        } else if (route==='/api/v1/patients/me/account-link') {status=201;result={authUserId:accountIdentity(this.key).id}}
        else if (route==='/api/v1/patients/me/registrations') result=[model.registration]
        else if (route==='/api/v1/patients') status=403
        else if (route.startsWith('/api/v1/patients/')) {status=this.org===orgB?404:200;result=model.registration}
        else if (route==='/api/v1/availability/me') {
          if (this.key==='receptionist') status=403
          else if (method==='PUT') {
            assert.equal(availability[this.key],undefined)
            availability[this.key]={...structuredClone(body),organisationId:orgA,doctorUserId:accountIdentity(this.key).id,version:0}
            result=availability[this.key]
          } else {status=availability[this.key]?200:404;result=availability[this.key]}
        } else if (route.startsWith('/api/v1/availability/mine/')) result=['doctorA','doctorB'].map(key=>({doctorUserId:accountIdentity(key).id}))
        else if (route.startsWith('/api/v1/availability/doctors/')) result={organisationId:orgA,doctorUserId:accountIdentity('doctorA').id,slots:[{startsAt:'2026-09-18T09:00:00Z',endsAt:'2026-09-18T09:30:00Z'}]}
        else if (route==='/api/v1/appointments' && method==='POST') {
          assert.deepEqual(saved.at(-1).booking,body,'Journal must precede the booking command')
          if (model.appointment) assert.equal(model.appointment.bookingRequestId,body.bookingRequestId)
          else {model.created++;model.appointment={...body,id:randomUUID(),organisationId:orgA,patientId:model.registration.patientId,status:'REQUESTED',version:0};status=201}
          result=model.appointment
          if (model.dropBookingResponse) {model.dropBookingResponse=false;throw new Error('Synthetic lost committed response')}
        } else if (route.startsWith('/api/v1/appointments/mine?')) result=[model.appointment]
        else if (route.startsWith('/api/v1/appointments/') && method==='POST') {
          const command=route.split('/').at(-1)
          if (this.key==='receptionist' && command==='confirm') status=403
          else if (commands.has(body.commandRequestId)) result=commands.get(body.commandRequestId)
          else {
            assert.equal(body.version,model.appointment.version)
            const mapping={confirm:['REQUESTED','CONFIRMED','doctorA'],'check-in':['CONFIRMED','CHECKED_IN','receptionist'],start:['CHECKED_IN','IN_PROGRESS','doctorA']}
            assert.equal(model.appointment.status,mapping[command][0]);assert.equal(this.key,mapping[command][2])
            model.appointment={...model.appointment,status:mapping[command][1],version:model.appointment.version+1}
            result=model.appointment;commands.set(body.commandRequestId,result)
          }
        } else if (route.startsWith('/api/v1/appointments/')) {status=this.key==='doctorB'?403:200;result=model.appointment}
        else assert.fail(`Unexpected fake route: ${method} ${route}`)
        if (!expected.includes(status)) throw Object.assign(new Error(`Synthetic expected ${status}`),{status})
        return structuredClone(result)
      }}
    clients.push(client);return client
  }
  const save=async value=>{saved.push(structuredClone(value))}
  return {state,seed,model,availability,calls,clients,saved,run:()=>seedScheduling(accounts,seed,state,save,createClient,now)}
}
test('workflow CLI refuses unrequested operations without starting services',async()=>{
  for (const args of [[],['seed'],['seed','all'],['reset','scheduling'],['seed','scheduling','--force']]) await assert.rejects(main(args),/Use:/)
})
test('journal refuses foreign generations, redirected identifiers and malformed booking state',()=>{
  const f=fixture()
  assert.equal(validateWorkflowState(f.state,f.seed.generation),f.state)
  for (const change of [{kind:'foreign'},{generation:randomUUID()},{organisationId:'../outside'},{appointmentId:randomUUID()},{unknown:true},
    {booking:{bookingRequestId:'unsafe',doctorUserId:randomUUID(),patientRegistrationId:randomUUID(),startsAt:'2026-09-17T09:00:00Z'}}]) {
    assert.throws(()=>validateWorkflowState({...f.state,...change},f.seed.generation))
  }
})
test('initial and repeated seeds use the same registration, appointment and existing availability',async()=>{
  const f=fixture(),first=await f.run(),snapshot=structuredClone(f.model),rules=structuredClone(f.availability)
  const repeated=await f.run()
  assert.equal(first.status,'IN_PROGRESS');assert.ok(first.checks>=35)
  assert.equal(repeated.appointmentId,first.appointmentId);assert.equal(repeated.registrationId,first.registrationId)
  assert.equal(f.model.created,1);assert.deepEqual(f.model,snapshot);assert.deepEqual(f.availability,rules)
  assert.equal(f.calls.filter(call=>call.method==='PUT').length,2)
  assert.equal(f.calls.filter(call=>call.route==='/api/v1/patients' && call.method==='POST').length,1)
  assert.ok(f.clients.every(client=>client.disposed && client.cookies.size===0))
})
test('a committed booking with a lost response recovers using the saved command, not a new appointment',async()=>{
  const f=fixture();f.model.dropBookingResponse=true
  await assert.rejects(f.run(),/lost committed response/)
  assert.equal(f.state.appointmentId,undefined);assert.equal(f.model.created,1)
  const key=f.state.booking.bookingRequestId,result=await f.run()
  assert.equal(f.state.booking.bookingRequestId,key);assert.equal(f.model.created,1)
  assert.equal(result.status,'IN_PROGRESS');assert.ok(f.clients.every(client=>client.disposed))
})
test('edited seeded availability is refused and never replaced',async()=>{
  const f=fixture();await f.run()
  f.availability.doctorA.locationLabel='User-edited room'
  const before=f.calls.length
  await assert.rejects(f.run(),/existing availability configuration/)
  assert.ok(!f.calls.slice(before).some(call=>call.method==='PUT'))
  assert.equal(f.availability.doctorA.locationLabel,'User-edited room')
  assert.ok(f.clients.every(client=>client.disposed))
})
test('foreign workflow context fails before logging in or writing anything',async()=>{
  const f=fixture();f.state.organisationId=randomUUID()
  await assert.rejects(f.run(),/changed organisation/)
  assert.equal(f.clients.length,0);assert.equal(f.saved.length,0)
})
test('all temporary clients are logged out/discarded even when one logout fails',async()=>{
  const f=fixture();f.model.failLogout=true
  await assert.rejects(f.run(),/logout failed/)
  assert.equal(f.calls.filter(call=>call.route==='/api/v1/auth/logout').length,5)
  assert.ok(f.clients.every(client=>client.disposed))
})
test('availability is a fresh synthetic object with no injected version or external resources',()=>{
  const first=availabilityBody();first.weeklyWindows[0].startTime='00:00'
  assert.equal(availabilityBody().weeklyWindows[0].startTime,'09:00')
  assert.equal(availabilityBody().version,null)
})
