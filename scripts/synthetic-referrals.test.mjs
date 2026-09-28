import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {test} from 'node:test'
import {accountIdentity} from './synthetic-demo.mjs'
import {main,referralCommand,validateReferralState,verifyReferral} from './synthetic-referrals.mjs'

function fixture() {
  return {kind:'sahha-synthetic-referrals-v1',generation:randomUUID(),organisationId:randomUUID(),
    consultationId:randomUUID(),patientId:randomUUID(),selectedFileId:randomUUID(),unselectedFileId:randomUUID()}
}
function commands() {
  return {...fixture(),diagnosisId:randomUUID(),medicationId:randomUUID(),referralRequestId:randomUUID(),
    consentRecordedAt:'2026-09-18T10:00:00.000Z',accessExpiresAt:'2026-09-19T10:00:00.000Z',
    expiryRequestId:randomUUID(),expiryConsentAt:'2026-09-18T10:00:00.000Z',expiryAt:'2026-09-18T10:00:20.000Z'}
}
test('referral journal pins the generation, source and distinct private files',()=>{
  const state=fixture();assert.equal(validateReferralState(state,state.generation),state)
  for(const change of [{kind:'other'},{generation:randomUUID()},{patientId:'../outside'},
    {sourceUrl:'https://untrusted.example'},{selectedFileId:state.unselectedFileId},{organisationId:null},
    {consultationId:'unsafe\nheader'},{downloadToken:'private-value'},{referralRequestId:'not-a-uuid'}]) {
    assert.throws(()=>validateReferralState({...state,...change},state.generation),/Synthetic referrals refused/)
  }
})
test('journal refuses resources or verification checkpoints without their prerequisites',()=>{
  const state=fixture(),now=new Date().toISOString()
  for(const change of [{conversationId:randomUUID()},{messageRequestId:randomUUID()},{messageId:randomUUID()},
    {referralId:randomUUID()},{expiryId:randomUUID()},{referralRequestId:randomUUID()},{expiryRequestId:randomUUID()},
    ...['mentionVerifiedAt','clinicalReadVerifiedAt','expiryReadVerifiedAt','expiryDeniedAt','fileReadVerifiedAt','revokedAt','clinicalRevocationVerifiedAt'].map(key=>({[key]:now})),
    {revokedAt:'not-a-date'}]) assert.throws(()=>validateReferralState({...state,...change},state.generation))
})
test('selected referral command preserves replay identity and never selects the source implicitly',()=>{
  const state=commands(),before=structuredClone(state),body=referralCommand(state)
  assert.equal(body.sourceConsultationId,state.consultationId);assert.equal(body.patientRegistrationId,state.patientId)
  assert.equal(body.referralRequestId,state.referralRequestId);assert.equal(body.recipientUserId,accountIdentity('doctorB').id)
  assert.equal(body.sendImmediately,true);assert.equal(body.clinicalSummary,null)
  assert.deepEqual(body.selectedItems,[{resourceType:'DIAGNOSIS',resourceId:state.diagnosisId},{resourceType:'MEDICAL_DOCUMENT',resourceId:state.selectedFileId}])
  assert.deepEqual(referralCommand(state),body);assert.deepEqual(state,before)
  assert.throws(()=>referralCommand(fixture()),/missing selected referral command/)
})
test('short-lived grant uses a separate medication selection without extending retained expiry',()=>{
  const state=commands(),body=referralCommand(state,true)
  assert.equal(body.referralRequestId,state.expiryRequestId);assert.equal(body.accessExpiresAt,state.expiryAt)
  assert.deepEqual(body.selectedItems,[{resourceType:'MEDICATION',resourceId:state.medicationId}])
  assert.notEqual(body.referralRequestId,referralCommand(state).referralRequestId)
  assert.deepEqual(referralCommand(state,true),body)
})
test('retained referral responses must preserve exact identity, selection, owners, content and privacy',()=>{
  const state={...commands(),referralId:randomUUID()},body=referralCommand(state)
  const {sourceConsultationId,referralRequestId,sendImmediately,...content}=body
  const value={...content,id:state.referralId,organisationId:state.organisationId,senderUserId:accountIdentity('doctorA').id}
  verifyReferral(value,state)
  verifyReferral({...value,selectedItems:[...value.selectedItems].reverse(),accessExpiresAt:'2026-09-19T10:00:00Z'},state)
  for(const change of [{id:randomUUID()},{organisationId:randomUUID()},{patientRegistrationId:randomUUID()},
    {senderUserId:accountIdentity('doctorB').id},{recipientUserId:accountIdentity('doctorA').id},
    {sourceConsultationId},{purpose:'changed'},{consentRecordedAt:'2026-09-18T11:00:00Z'},
    {accessExpiresAt:'2026-09-20T10:00:00Z'},{selectedItems:[]},
    {selectedItems:[...value.selectedItems,{resourceType:'CONSULTATION',resourceId:state.consultationId}]}]) {
    assert.throws(()=>verifyReferral({...value,...change},state),/Synthetic referrals refused/)
  }
})
test('referral CLI rejects invalid or destructive commands before accessing private state',async()=>{
  for(const args of [[],['reset'],['seed'],['seed','all'],['seed','clinical','--reset'],['seed','files','--force']]) {
    await assert.rejects(main(args),/Use: node scripts\/synthetic-referrals/)
  }
})
