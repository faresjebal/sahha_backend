import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {test} from 'node:test'
import {careCommand,main,validateCareState,verifyCareReferral} from './synthetic-shared-care.mjs'
import {accountIdentity} from './synthetic-demo.mjs'
import {fileBytes,syntheticPdf} from './synthetic/file-seed.mjs'

function fixture() {
  return {kind:'sahha-synthetic-shared-care-v1',generation:randomUUID(),organisationId:randomUUID(),
    otherOrganisationId:randomUUID(),patientId:randomUUID(),consultationId:randomUUID(),fileId:randomUUID(),secondFileId:randomUUID(),cases:{}}
}
function commanded() {
  const value=fixture()
  value.cases.main={requestId:randomUUID(),consentAt:'2026-09-21T10:00:00Z',expiresAt:'2026-09-22T10:00:00Z'}
  return value
}
test('care journal pins generation, distinct organisations/files and a closed allowlist',()=> {
  const value=fixture();assert.equal(validateCareState(value,value.generation),value)
  for(const change of [{generation:randomUUID()},{organisationId:'../outside'},{otherOrganisationId:value.organisationId},
    {secondFileId:value.fileId},{token:'unsafe'},{cases:[]},{cases:{unexpected:{}}},{verifiedAt:new Date().toISOString()}]) {
    assert.throws(()=>validateCareState({...value,...change},value.generation),/Synthetic shared care refused/)
  }
})
test('care journal requires immutable commands and verified prerequisites before checkpoints or private recovery token',()=> {
  const value=commanded(),entry=value.cases.main
  for(const change of [{requestId:'invalid'},{consentAt:'invalid'},{expiresAt:entry.consentAt},{referralId:'invalid'},
    {positiveAt:entry.consentAt},{finishedAt:entry.consentAt},{downloadToken:'a'.repeat(43)},{unexpectedField:1}]) {
    assert.throws(()=>validateCareState({...value,cases:{main:{...entry,...change}}},value.generation))
  }
  const active={...entry,referralId:randomUUID(),positiveAt:entry.consentAt,downloadToken:'a'.repeat(43)}
  validateCareState({...value,cases:{main:active}},value.generation)
  assert.throws(()=>validateCareState({...value,cases:{main:{...active,finishedAt:entry.consentAt}}},value.generation))
  assert.throws(()=>validateCareState({...value,cases:{main:{...active,downloadToken:'bad\nheader'}}},value.generation))
  assert.throws(()=>validateCareState({...value,cases:{expiry:active}},value.generation))
})
test('shared-care commands preserve explicit type, empty selections, recorded scope and retry identity',()=> {
  const value=commanded(),snapshot=structuredClone(value),command=careCommand(value,'main')
  assert.equal(command.referralType,'SHARED_TREATMENT');assert.deepEqual(command.selectedItems,[])
  assert.equal(command.referralRequestId,value.cases.main.requestId)
  assert.equal(command.sourceConsultationId,value.consultationId);assert.equal(command.patientRegistrationId,value.patientId)
  assert.equal(command.recipientUserId,accountIdentity('doctorB').id);assert.equal(command.clinicalSummary,null)
  assert.equal(command.sendImmediately,true);assert.deepEqual(careCommand(value,'main'),command);assert.deepEqual(value,snapshot)
  assert.throws(()=>careCommand(value,'other'));assert.throws(()=>careCommand(value,'expiry'))
})
test('case responses cannot change scope, type, identities, dates or disclose source metadata',()=> {
  const value=commanded(),body=careCommand(value,'main'),{sourceConsultationId,referralRequestId,sendImmediately,...rest}=body
  const response={...rest,id:randomUUID(),organisationId:value.organisationId,senderUserId:accountIdentity('doctorA').id}
  verifyCareReferral(response,value,'main')
  value.cases.main.referralId=response.id
  for(const change of [{id:randomUUID()},{referralType:'SECOND_OPINION'},{organisationId:randomUUID()},
    {patientRegistrationId:randomUUID()},{recipientUserId:accountIdentity('doctorA').id},{purpose:'changed'},
    {accessExpiresAt:'2026-09-23T10:00:00Z'},{selectedItems:[{resourceType:'CONSULTATION',resourceId:value.consultationId}]},
    {sourceConsultationId}]) assert.throws(()=>verifyCareReferral({...response,...change},value,'main'))
})
test('shared-care binary transfer stays Gateway-pinned, read-only, header-token and no-store',async()=> {
  const file=randomUUID(),patient=randomUUID(),token='a'.repeat(43),bytes=syntheticPdf();let calls=0
  const client={cookieHeader:()=>'',fetcher:async(url,options)=> {
    calls++;assert.equal(url,'http://127.0.0.1:8079/api/v1/files/shared-care/'+patient+'/'+file+'/content')
    assert.equal(options.method,'GET');assert.equal(options.headers['X-Download-Token'],token)
    assert.equal(options.cache,'no-store');assert.equal(options.redirect,'error')
    return new Response(bytes,{headers:{'Content-Type':'application/pdf','Content-Length':String(bytes.length),'Cache-Control':'no-store'}})
  }}
  assert.deepEqual(await fileBytes(client,file,'GET',token,200,undefined,true,patient,'SHARED_CARE'),bytes)
  for(const [method,registration,scope] of [['PUT',patient,'SHARED_CARE'],['GET',undefined,'SHARED_CARE'],['GET',patient,'unknown']]) {
    await assert.rejects(fileBytes(client,file,method,token,200,undefined,true,registration,scope),/scope/)
  }
  assert.equal(calls,1)
})
test('care CLI refuses reset, arbitrary stages and extra flags before touching private data',async()=> {
  for(const args of [[],['reset'],['verify','--force'],['verify','--reset'],['seed']]) await assert.rejects(main(args),/Use:/)
})
