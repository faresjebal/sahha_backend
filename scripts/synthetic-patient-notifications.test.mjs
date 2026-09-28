import test from 'node:test'
import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {main,validatePatientNotificationState,verifyPatientNotification} from './synthetic-patient-notifications.mjs'
const initial=()=>({kind:'sahha-synthetic-patient-notifications-v1',generation:randomUUID(),organisationId:randomUUID(),
  registrationId:randomUUID(),doctorUserId:randomUUID(),cases:{},deliveries:{}})
test('patient journal is generation bound, closed and refuses fabricated completion',()=>{
  const state=initial()
  assert.equal(validatePatientNotificationState(state,state.generation),state)
  for(const change of [{generation:randomUUID()},{password:'private'},{verifiedAt:new Date().toISOString()},{cases:[]},{deliveries:{'main:REQUESTED':{id:randomUUID(),mode:'LIVE'}}}])
    assert.throws(()=>validatePatientNotificationState({...state,...change},state.generation),/refused/)
})
test('patient journal preserves exact booking scope and command bodies',()=>{
  const state=initial()
  state.cases.main={booking:{bookingRequestId:randomUUID(),doctorUserId:state.doctorUserId,patientRegistrationId:state.registrationId,startsAt:'2027-01-04T09:00:00Z'},appointmentId:randomUUID()}
  state.cases.main.confirm={commandRequestId:randomUUID(),version:0}
  state.deliveries['main:REQUESTED']={id:randomUUID(),mode:'LIVE'}
  validatePatientNotificationState(state,state.generation)
  state.cases.main.booking.patientRegistrationId=randomUUID()
  assert.throws(()=>validatePatientNotificationState(state,state.generation),/refused/)
})
test('patient notification evidence excludes patient identifiers, clinical fields and arbitrary payload',()=>{
  const appointment=randomUUID(),value={id:randomUUID(),notificationType:'APPOINTMENT_CONFIRMED',resourceType:'APPOINTMENT',resourceId:appointment,
    appointmentStatus:'CONFIRMED',appointmentStartsAt:'2027-01-04T09:00:00Z',appointmentEndsAt:'2027-01-04T09:30:00Z',
    appointmentTimeZone:'UTC',appointmentLocationLabel:'Synthetic room',resourceVersion:1,eventOccurredAt:'2027-01-01T09:00:00Z',createdAt:'2027-01-01T09:00:01Z',read:false,readAt:null}
  assert.equal(verifyPatientNotification(value,appointment,'CONFIRMED'),value.id)
  for(const change of [{patientId:randomUUID()},{clinicalNotes:'private'},{resourceId:randomUUID()},{appointmentStatus:'CANCELLED'},{read:'false'}])
    assert.throws(()=>verifyPatientNotification({...value,...change},appointment,'CONFIRMED'),/refused/)
})
test('patient gate refuses unapproved CLI mutations before private IO',async()=>{
  for(const args of [[],['reset'],['verify','--reset']])await assert.rejects(main(args),/Use: node scripts\/synthetic-patient-notifications/)
})
