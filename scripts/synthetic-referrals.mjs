#!/usr/bin/env node
// Recoverable, API-only selected-sharing acceptance. No data resets or SQL writes.
import {randomUUID} from 'node:crypto'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {isDeepStrictEqual} from 'node:util'
import {currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {accountIdentity,validateAccounts,waitOrganisationRoute} from './synthetic-demo.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {GatewayClient} from './synthetic/gateway-client.mjs'
import {conversationItems} from './synthetic/message-seed.mjs'
import {matchesClinicalDraft} from './synthetic/clinical-seed.mjs'
import {fileBytes,syntheticPdf} from './synthetic/file-seed.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const check=(condition,label)=>{if(!condition) throw new Error(`Synthetic referrals refused: ${label}; retained resources are not replaced`)}
const stamp=value=>typeof value==='string' && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value))
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const SUBJECT='Synthetic finalised-source patient discussion'
const MESSAGE='Synthetic patient context only; a message is not a clinical access grant.'
const fields=['kind','generation','organisationId','consultationId','patientId','selectedFileId','unselectedFileId',
  'diagnosisId','medicationId','conversationRequestId','conversationId','messageRequestId','messageId',
  'referralRequestId','referralId','consentRecordedAt','accessExpiresAt','expiryRequestId','expiryId','expiryConsentAt','expiryAt',
  'mentionVerifiedAt','clinicalReadVerifiedAt','expiryReadVerifiedAt','expiryDeniedAt','fileReadVerifiedAt','revokedAt','clinicalRevocationVerifiedAt']
export function validateReferralState(state,generation) {
  check(state?.kind==='sahha-synthetic-referrals-v1' && state.generation===generation && uuid.test(generation),'journal generation')
  check(Object.keys(state).every(key=>fields.includes(key)),'unknown journal field')
  for(const key of ['organisationId','consultationId','patientId','selectedFileId','unselectedFileId']) check(uuid.test(state[key]),'journal scope')
  check(state.selectedFileId!==state.unselectedFileId,'distinct selected/unselected files')
  for(const [key,value] of Object.entries(state)) {
    if(key.endsWith('Id') && value!==undefined) check(uuid.test(value),'journal identifier')
    if(key.endsWith('At') && value!==undefined) check(stamp(value),'journal timestamp')
  }
  for(const [resource,request] of [['conversationId','conversationRequestId'],['messageId','messageRequestId'],['referralId','referralRequestId'],['expiryId','expiryRequestId']]) {
    if(state[resource]) check(state[request],'missing recovery command')
  }
  if(state.messageRequestId) check(state.conversationId,'message conversation')
  if(state.referralRequestId) check(state.diagnosisId && stamp(state.consentRecordedAt) && stamp(state.accessExpiresAt),'main referral command')
  if(state.expiryRequestId) check(state.medicationId && stamp(state.expiryConsentAt) && stamp(state.expiryAt),'expiry command')
  if(state.mentionVerifiedAt) check(state.messageId && state.diagnosisId,'mention checkpoint')
  if(state.clinicalReadVerifiedAt) check(state.referralId,'clinical read checkpoint')
  if(state.expiryReadVerifiedAt) check(state.expiryId,'expiry read checkpoint')
  if(state.expiryDeniedAt) check(state.expiryReadVerifiedAt,'expiry denial checkpoint')
  if(state.fileReadVerifiedAt) check(state.clinicalReadVerifiedAt && state.expiryDeniedAt,'file read checkpoint')
  if(state.revokedAt) check(state.referralId && state.fileReadVerifiedAt,'revocation checkpoint')
  if(state.clinicalRevocationVerifiedAt) check(state.revokedAt,'clinical revocation checkpoint')
  return state
}
export function referralCommand(state,expiry=false) {
  validateReferralState(state,state.generation)
  check(expiry?state.expiryRequestId:state.referralRequestId,'missing selected referral command')
  return {referralRequestId:expiry?state.expiryRequestId:state.referralRequestId,recipientUserId:accountIdentity('doctorB').id,
    patientRegistrationId:state.patientId,sourceConsultationId:state.consultationId,
    reason:expiry?'Synthetic expiring medication share':'Synthetic selected diagnosis and document share',priority:'ROUTINE',
    clinicalSummary:null,purpose:'Synthetic acceptance only, no real care.',consentType:'RECORDED_WRITTEN',
    consentEvidenceReference:'synthetic-demo-consent-only',consentRecordedAt:expiry?state.expiryConsentAt:state.consentRecordedAt,
    accessExpiresAt:expiry?state.expiryAt:state.accessExpiresAt,sendImmediately:true,
    selectedItems:expiry?[{resourceType:'MEDICATION',resourceId:state.medicationId}]:[
      {resourceType:'DIAGNOSIS',resourceId:state.diagnosisId},{resourceType:'MEDICAL_DOCUMENT',resourceId:state.selectedFileId}]}
}
export function verifyReferral(value,state,expiry=false) {
  const body=referralCommand(state,expiry)
  const retainedId=expiry?state.expiryId:state.referralId
  check(!retainedId || value?.id===retainedId,'changed referral identity')
  check(uuid.test(value?.id) && value.organisationId===state.organisationId && value.patientRegistrationId===state.patientId
    && value.senderUserId===accountIdentity('doctorA').id && value.recipientUserId===body.recipientUserId,'referral owner/context')
  for(const key of ['reason','priority','clinicalSummary','purpose','consentType','consentEvidenceReference']) check(value[key]===body[key],'changed referral content')
  for(const key of ['consentRecordedAt','accessExpiresAt']) check(Date.parse(value[key])===Date.parse(body[key]),'changed referral dates')
  const selected=items=>items.map(item=>`${item.resourceType}:${item.resourceId}`).sort()
  check(Array.isArray(value.selectedItems) && isDeepStrictEqual(selected(value.selectedItems),selected(body.selectedItems)),'changed selected resources')
  check(!Object.hasOwn(value,'sourceConsultationId'),'private source metadata leakage')
}
async function ready(client,route) {
  for(let attempt=0;attempt<60;attempt++) {
    try{return await client.request(route)}catch(error){if(error.status!==503 || attempt===59) throw error;await pause(1000)}
  }
}
export async function withClients(accounts,seed,action) {
  const keys=['doctorA','doctorB','unrelatedDoctor','receptionist','orgAdmin','patient'],clients={}
  for(const key of keys) clients[key]=new GatewayClient()
  try {
    for(const key of keys) {
      const account=accounts.find(value=>value.key===key)
      check(account?.id===accountIdentity(key).id,'synthetic identity')
      check((await clients[key].login(account)).userId===account.id,'authenticated identity')
    }
    await waitOrganisationRoute(clients.doctorA)
    for(const key of keys.filter(key=>key!=='patient')) check((await clients[key].select(seed.organisations.A)).activeOrganisationId===seed.organisations.A,'active organisation')
    return await action(clients)
  } finally {
    let failed=false
    for(const client of Object.values(clients)) {
      try{if(client.cookies.size) await client.request('/api/v1/auth/logout','POST',undefined,[204])}
      catch{failed=true}finally{client.dispose()}
    }
    if(failed) throw new Error('Synthetic referral logout failed; every client discarded')
  }
}
async function clinicalGate(clients,state,save) {
  const a=clients.doctorA,b=clients.doctorB
  let checks=11
  const verified=(condition,label)=>{check(condition,label);checks++}
  const shared=(type,id)=>`/api/v1/clinical/shared/${state.patientId}/${type}/${id}`
  const record=await ready(a,`/api/v1/consultations/${state.consultationId}/record`)
  verified(record.status==='FINALIZED' && record.patientRegistrationId===state.patientId
    && record.organisationId===state.organisationId && record.doctorUserId===accountIdentity('doctorA').id
    && matchesClinicalDraft(record,true),'owned finalised synthetic record')
  for(const [key,id] of [['diagnosisId',record.diagnoses[0].id],['medicationId',record.medications[0].id]]) {
    check(uuid.test(id) && (!state[key] || state[key]===id),'retained source children');state[key]=id
  }
  await save(state)
  // Scheduling is deliberately absent from this bounded batch. Its active-care
  // denials are covered separately; an unavailable dependency is not a 404 proof.
  // No continuing-care appointment is invented for this explicit-source handoff.
  if(!state.conversationRequestId) {
    const existing=conversationItems(await ready(a,'/api/v1/conversations?size=100')).filter(value=>value.subject===SUBJECT)
    check(existing.length===0,'existing patient conversation without its command');state.conversationRequestId=randomUUID();await save(state)
  }
  const conversation=await a.request('/api/v1/conversations','POST',{conversationRequestId:state.conversationRequestId,
    recipientUserId:accountIdentity('doctorB').id,subject:SUBJECT,patientRegistrationId:state.patientId,sourceConsultationId:state.consultationId},[201])
  verified(uuid.test(conversation.id) && (!state.conversationId || state.conversationId===conversation.id)
    && conversation.patientRegistrationId===state.patientId && conversation.patientAccessGranted===false
    && !Object.hasOwn(conversation,'sourceConsultationId'),'source-authorised mention without access')
  state.conversationId=conversation.id;state.messageRequestId??=randomUUID();await save(state)
  const message=await a.request(`/api/v1/conversations/${conversation.id}/messages`,'POST',{messageRequestId:state.messageRequestId,body:MESSAGE},[201])
  verified(uuid.test(message.id) && (!state.messageId || state.messageId===message.id) && message.body===MESSAGE,'retained patient-context message')
  state.messageId=message.id;await save(state)
  const visible=conversationItems(await b.request(`/api/v1/conversations/${conversation.id}/messages?size=100`))
  verified(visible.length===1 && visible[0].id===message.id,'single participant-visible message')
  if(!state.referralId) {
    await b.request(shared('DIAGNOSIS',state.diagnosisId),'GET',undefined,[404]);checks++
    state.mentionVerifiedAt=new Date().toISOString();await save(state)
  }
  let referral
  if(!state.referralId) {
    if(!state.referralRequestId) {
      const existing=conversationItems(await a.request('/api/v1/referrals?size=100')).filter(value=>value.reason==='Synthetic selected diagnosis and document share')
      check(existing.length===0,'existing selected referral without command')
      state.referralRequestId=randomUUID();state.consentRecordedAt=new Date(Date.now()-1000).toISOString();state.accessExpiresAt=new Date(Date.now()+86400000).toISOString();await save(state)
    }
    check(Date.parse(state.accessExpiresAt)>Date.now(),'expired unconfirmed creation command')
    referral=await a.request('/api/v1/referrals','POST',referralCommand(state),[201]);verifyReferral(referral,state)
    state.referralId=referral.id;await save(state)
  } else {referral=await a.request(`/api/v1/referrals/${state.referralId}`);verifyReferral(referral,state)}
  if(referral.status==='SENT') {
    await b.request(shared('DIAGNOSIS',state.diagnosisId),'GET',undefined,[404]);checks++
    referral=await b.request(`/api/v1/referrals/${referral.id}/accept`,'POST',{expectedVersion:referral.version})
    verified(referral.status==='ACTIVE' && uuid.test(referral.sharingGrantId),'explicit accepted grant')
  }
  if(referral.status==='ACTIVE' && Date.parse(referral.accessExpiresAt)>Date.now()) {
    const selected=await b.request(shared('DIAGNOSIS',state.diagnosisId))
    verified(selected.resourceId===state.diagnosisId && selected.resourceType==='DIAGNOSIS'
      && selected.patientRegistrationId===state.patientId && Date.parse(selected.validUntil)<=Date.parse(state.accessExpiresAt)
      && isDeepStrictEqual(selected.diagnosis,record.diagnoses[0]) && Object.keys(selected).sort().join(',')===
        'diagnosis,patientRegistrationId,resourceId,resourceType,validUntil','only selected diagnosis content')
    state.clinicalReadVerifiedAt=new Date().toISOString();await save(state)
  } else {
    check(state.clinicalReadVerifiedAt && state.revokedAt && referral.status==='REVOKED','unverified or expired retained grant')
    await b.request(shared('DIAGNOSIS',state.diagnosisId),'GET',undefined,[404]);checks++
    state.clinicalRevocationVerifiedAt=new Date().toISOString();await save(state)
  }
  await b.request(shared('CONSULTATION',state.consultationId),'GET',undefined,[404]);checks++
  await b.request(`/api/v1/consultations/${state.consultationId}/record`,'GET',undefined,[404]);checks++
  for(const [key,status] of [['unrelatedDoctor',404],['receptionist',403],['orgAdmin',403],['patient',401]]) {
    await clients[key].request(shared('DIAGNOSIS',state.diagnosisId),'GET',undefined,[status]);checks++
  }
  // Separate resource: its expiry cannot be masked by the long-lived diagnosis/file grant.
  if(!state.expiryRequestId) {
    state.expiryRequestId=randomUUID();state.expiryConsentAt=new Date(Date.now()-1000).toISOString();state.expiryAt=new Date(Date.now()+20000).toISOString();await save(state)
  }
  let expiry
  if(!state.expiryId) {
    check(Date.parse(state.expiryAt)>Date.now(),'expired unconfirmed short-lived command; manual recovery required')
    expiry=await a.request('/api/v1/referrals','POST',referralCommand(state,true),[201]);verifyReferral(expiry,state,true)
    state.expiryId=expiry.id;await save(state)
  } else {expiry=await a.request(`/api/v1/referrals/${state.expiryId}`);verifyReferral(expiry,state,true)}
  if(expiry.status==='SENT' && Date.parse(state.expiryAt)>Date.now()) expiry=await b.request(`/api/v1/referrals/${expiry.id}/accept`,'POST',{expectedVersion:expiry.version})
  if(Date.parse(state.expiryAt)>Date.now()) {
    const selected=await b.request(shared('MEDICATION',state.medicationId))
    verified(selected.resourceId===state.medicationId && selected.resourceType==='MEDICATION'
      && selected.patientRegistrationId===state.patientId && Date.parse(selected.validUntil)<=Date.parse(state.expiryAt)
      && isDeepStrictEqual(selected.medication,record.medications[0]) && Object.keys(selected).sort().join(',')===
        'medication,patientRegistrationId,resourceId,resourceType,validUntil','short-lived selected medication')
    state.expiryReadVerifiedAt=new Date().toISOString();await save(state)
    check(Date.parse(state.expiryAt)-Date.now()<=25000,'bounded expiry wait')
    while(Date.now()<=Date.parse(state.expiryAt)+300) await pause(250)
  }
  check(state.expiryReadVerifiedAt,'no recorded positive expiry acceptance; do not recreate the referral')
  await b.request(shared('MEDICATION',state.medicationId),'GET',undefined,[404]);checks++
  state.expiryDeniedAt=new Date().toISOString();await save(state)
  verified(isDeepStrictEqual(await a.request(`/api/v1/consultations/${state.consultationId}/record`),record),'original clinical record unchanged')
  return {checks,phase:'clinical',grantStatus:referral.status,expiryDenied:true,revocationDenied:!!state.clinicalRevocationVerifiedAt}
}
async function filesGate(clients,seed,state,save) {
  check(state.clinicalReadVerifiedAt && state.expiryDeniedAt,'clinical acceptance prerequisites')
  const a=clients.doctorA,b=clients.doctorB,base=`/api/v1/files/shared/${state.patientId}/${state.selectedFileId}`
  let checks=11
  const verified=(condition,label)=>{check(condition,label);checks++}
  const transfer=(client,token,status)=>fileBytes(client,state.selectedFileId,'GET',token,status,undefined,true,state.patientId)
  let referral=await ready(a,`/api/v1/referrals/${state.referralId}`);verifyReferral(referral,state)
  if(referral.status==='ACTIVE') {
    const file=await ready(b,base)
    verified(file.fileId===state.selectedFileId && file.consultationId===state.consultationId && file.scanStatus==='CLEAN','selected file metadata')
    const grant=await b.request(`${base}/download-grants`,'POST')
    verified(grant.fileId===state.selectedFileId && grant.downloadPath===`${base}/content`
      && /^[A-Za-z0-9_-]{32,128}$/.test(grant.downloadToken) && Date.parse(grant.expiresAt)-Date.now()<=300000
      && Date.parse(grant.expiresAt)<=Date.parse(referral.accessExpiresAt) && Date.parse(grant.expiresAt)>Date.now(),'share-bounded file grant')
    await transfer(clients.unrelatedDoctor,grant.downloadToken,404);checks++
    verified(syntheticPdf().equals(await transfer(b,grant.downloadToken,200)),'exact selected private file bytes')
    await transfer(b,grant.downloadToken,409);checks++
    for(const [key,status] of [['unrelatedDoctor',404],['receptionist',403],['orgAdmin',403],['patient',401]]) {
      await clients[key].request(base,'GET',undefined,[status]);checks++
    }
    await b.select(seed.organisations.B);await b.request(base,'GET',undefined,[404]);checks++;await b.select(seed.organisations.A)
    await b.request(`/api/v1/files/shared/${state.patientId}/${state.unselectedFileId}`,'GET',undefined,[404]);checks++
    await b.request(`/api/v1/files/shared/${state.patientId}/${state.unselectedFileId}/download-grants`,'POST',undefined,[404]);checks++
    const outstanding=await b.request(`${base}/download-grants`,'POST')
    state.fileReadVerifiedAt=new Date().toISOString();await save(state)
    referral=await a.request(`/api/v1/referrals/${referral.id}/revoke`,'POST',{expectedVersion:referral.version,reason:'Synthetic acceptance revocation.'})
    verified(referral.status==='REVOKED','explicit sender revocation')
    await transfer(b,outstanding.downloadToken,404);checks++
  }
  check(referral.status==='REVOKED' && state.fileReadVerifiedAt,'retained revoked grant and positive file evidence')
  await b.request(base,'GET',undefined,[404]);checks++
  await b.request(`${base}/download-grants`,'POST',undefined,[404]);checks++
  await b.request(`/api/v1/files/shared/${state.patientId}/${state.unselectedFileId}`,'GET',undefined,[404]);checks++
  await b.request(`/api/v1/files/shared/${state.patientId}/${state.unselectedFileId}/download-grants`,'POST',undefined,[404]);checks++
  state.revokedAt??=new Date().toISOString();await save(state)
  return {phase:'files',checks,grantStatus:'REVOKED',unselectedDenied:true,revocationDenied:true}
}
async function saveJson(file,value) {
  try{await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error){if(error.code!=='ENOENT') throw error;await privateFile(file,JSON.stringify(value,null,2))}
}
export async function main(args) {
  if(args.length!==2 || args[0]!=='seed' || !['clinical','files'].includes(args[1])) throw new Error('Use: node scripts/synthetic-referrals.mjs seed clinical|files')
  await lock(async()=>{
    const manifest=await currentManifest(),directory=generationPath(manifest.id),read=async name=>parsePrivateJson(await readPrivate(path.join(directory,name)))
    const accounts=validateAccounts(await read('demo-accounts.json'),manifest.id),seed=await read('demo-seed-report.json'),clinical=await read('workflow-clinical-report.json')
    const primary=await read('workflow-files-report.json'),secondary=await read('workflow-files-unselected-report.json')
    check(uuid.test(seed.organisations?.A) && uuid.test(seed.organisations?.B) && seed.organisations.A!==seed.organisations.B,'distinct organisation provenance')
    check([seed,clinical,primary,secondary].every(value=>value.generation===manifest.id) && clinical.status==='FINALIZED'
      && primary.consultationId===clinical.consultationId && secondary.consultationId===clinical.consultationId,'seed provenance')
    const expected={kind:'sahha-synthetic-referrals-v1',generation:manifest.id,organisationId:seed.organisations.A,
      consultationId:clinical.consultationId,patientId:clinical.registrationId,selectedFileId:primary.fileId,unselectedFileId:secondary.fileId}
    const journal=path.join(directory,'workflow-referrals.json');let state
    try{state=await read('workflow-referrals.json')}catch(error){if(error.code!=='ENOENT') throw error;state={...expected}}
    validateReferralState(state,manifest.id);check(Object.entries(expected).every(([key,value])=>state[key]===value),'changed seed scope')
    const save=async value=>{validateReferralState(value,manifest.id);await saveJson(journal,value)}
    const names=batchNames('collaboration').filter(name=>name!=='notification-service').concat(args[1]==='clinical'?'clinical-service':'file-service')
    const result=await withSyntheticPlatform(manifest,names,()=>withClients(accounts.accounts,seed,clients=>args[1]==='clinical'
      ?clinicalGate(clients,state,save):filesGate(clients,seed,state,save)),{eventDelivery:true})
    await saveJson(path.join(directory,`workflow-referrals-${args[1]}-report.json`),{generation:manifest.id,verifiedAt:new Date().toISOString(),...result})
    console.log(`Synthetic referrals ${result.phase}: ${result.checks} Gateway assertions passed; grant ${result.grantStatus}; helpers stopped.`)
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
