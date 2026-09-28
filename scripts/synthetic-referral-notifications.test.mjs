import test from 'node:test'
import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {main,validateNotificationState,verifyReferralNotification} from './synthetic-referral-notifications.mjs'

function state() {
  const generation=randomUUID()
  return {kind:'sahha-synthetic-referral-notifications-v1',generation,deliveries:{},
    care:{kind:'sahha-synthetic-shared-care-v1',generation,organisationId:randomUUID(),otherOrganisationId:randomUUID(),
      patientId:randomUUID(),consultationId:randomUUID(),fileId:randomUUID(),secondFileId:randomUUID(),cases:{}}}
}
test('pins closed journal to the retained care generation and forbids fabricated completion',()=>{
  const value=state();assert.equal(validateNotificationState(value,value.generation),value)
  for(const change of [{generation:randomUUID()},{password:'private'},{verifiedAt:new Date().toISOString()},
    {deliveries:{'main:RECEIVED:doctorB':randomUUID()}},{deliveries:[]}]){
    assert.throws(()=>validateNotificationState({...value,...change},value.generation),/refused/)
  }
})
test('permits only known private delivery identifiers after referral identity is recorded',()=>{
  const value=state()
  value.care.cases.main={requestId:randomUUID(),consentAt:'2026-09-24T00:00:00Z',expiresAt:'2026-09-25T00:00:00Z',referralId:randomUUID()}
  value.deliveries['main:RECEIVED:doctorB']=randomUUID()
  validateNotificationState(value,value.generation)
  validateNotificationState({...value,recoveredDeliveries:['main:RECEIVED:doctorB']},value.generation)
  assert.throws(()=>validateNotificationState({...value,recoveredDeliveries:['main:ACCEPTED:doctorA']},value.generation),/refused/)
  for(const key of ['main:RECEIVED:unrelatedDoctor','main:EXPIRED:doctorB','secret'])
    assert.throws(()=>validateNotificationState({...value,deliveries:{[key]:randomUUID()}},value.generation),/refused/)
})
test('live alert proof contains no patient, consent, clinical, routing-recipient or arbitrary extra payloads',()=>{
  const referral=randomUUID(),value={id:randomUUID(),notificationType:'REFERRAL_RECEIVED',resourceType:'REFERRAL',resourceId:referral,
    appointmentStatus:null,appointmentStartsAt:null,appointmentEndsAt:null,appointmentTimeZone:null,appointmentLocationLabel:null,
    resourceVersion:0,eventOccurredAt:'2026-09-24T00:00:00Z',createdAt:'2026-09-24T00:00:01Z',read:false,readAt:null}
  assert.equal(verifyReferralNotification(value,referral,'RECEIVED'),value.id)
  for(const change of [{patientId:randomUUID()},{consentEvidence:'private'},{recipientUserId:randomUUID()},
    {resourceId:randomUUID()},{notificationType:'MESSAGE_RECEIVED'},{appointmentLocationLabel:'private'},{resourceVersion:-1},{read:'false'}])
    assert.throws(()=>verifyReferralNotification({...value,...change},referral,'RECEIVED'),/refused/)
})
test('refuses reset, recovery edits and unknown CLI commands before private IO',async()=>{
  for(const args of [[],['reset'],['verify','--reset'],['verify','--reconcile']])
    await assert.rejects(main(args),/Use: node scripts\/synthetic-referral-notifications/)
})
