#!/usr/bin/env node
// API-only retained-data acceptance. No resets, SQL writes, Kafka or Notification claims.
import {randomUUID} from 'node:crypto'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {isDeepStrictEqual} from 'node:util'
import {currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {accountIdentity,validateAccounts} from './synthetic-demo.mjs'
import {withClients} from './synthetic-referrals.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {fileBytes,syntheticPdf} from './synthetic/file-seed.mjs'
import {matchesClinicalDraft} from './synthetic/clinical-seed.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const cases=['main','independent','supporting','expiry']
const stamp=value=>typeof value==='string' && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value))
const check=(value,label)=>{if(!value) throw new Error('Synthetic shared care refused: '+label+'; retained resources are not replaced')}
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
export function validateCareState(state,generation) {
  check(state?.kind==='sahha-synthetic-shared-care-v1' && uuid.test(generation) && state.generation===generation,'generation')
  const scope=['organisationId','otherOrganisationId','patientId','consultationId','fileId','secondFileId']
  check(Object.keys(state).every(key=>['kind','generation','cases','verifiedAt',...scope].includes(key)),'unknown state field')
  for(const key of scope) check(uuid.test(state[key]),'scope identifiers')
  check(state.organisationId!==state.otherOrganisationId && state.fileId!==state.secondFileId,'distinct scope')
  check(state.cases && typeof state.cases==='object' && !Array.isArray(state.cases),'case journal')
  for(const [name,entry] of Object.entries(state.cases)) {
    check(cases.includes(name) && entry && typeof entry==='object','known case')
    check(Object.keys(entry).every(key=>['requestId','consentAt','expiresAt','referralId','positiveAt','finishedAt','downloadToken'].includes(key)),'unknown case field')
    check(uuid.test(entry.requestId) && stamp(entry.consentAt) && stamp(entry.expiresAt)
      && Date.parse(entry.expiresAt)>Date.parse(entry.consentAt),'immutable command identity/window')
    if(entry.referralId!==undefined) check(uuid.test(entry.referralId),'referral identity')
    if(entry.positiveAt!==undefined) check(entry.referralId && stamp(entry.positiveAt),'positive checkpoint')
    if(entry.finishedAt!==undefined) check(entry.positiveAt && stamp(entry.finishedAt),'finished checkpoint')
    // Recovery token stays ONLY in the ACL-restricted local journal, never reports/logs/browser storage.
    if(entry.downloadToken!==undefined) check(name==='main' && entry.positiveAt && !entry.finishedAt
      && /^[A-Za-z0-9_-]{32,128}$/.test(entry.downloadToken),'private outstanding token')
  }
  if(state.verifiedAt!==undefined) check(stamp(state.verifiedAt) && cases.every(name=>state.cases[name]?.finishedAt),'complete checkpoint')
  return state
}
export function careCommand(state,name) {
  validateCareState(state,state.generation); check(cases.includes(name) && state.cases[name],'case command')
  const entry=state.cases[name]
  return {referralType:'SHARED_TREATMENT',referralRequestId:entry.requestId,
    recipientUserId:accountIdentity('doctorB').id,patientRegistrationId:state.patientId,sourceConsultationId:state.consultationId,
    reason:'Synthetic shared-treatment acceptance '+name,priority:'ROUTINE',clinicalSummary:null,
    purpose:'Synthetic joint-treatment access verification only; no real patient care.',
    consentType:'RECORDED_WRITTEN',consentEvidenceReference:'synthetic-shared-care-consent',
    consentRecordedAt:entry.consentAt,accessExpiresAt:entry.expiresAt,selectedItems:[],sendImmediately:true}
}
export function verifyCareReferral(value,state,name) {
  const body=careCommand(state,name),entry=state.cases[name]
  check(uuid.test(value?.id) && (!entry.referralId || value.id===entry.referralId),'referral identity')
  check(value.organisationId===state.organisationId && value.senderUserId===accountIdentity('doctorA').id,'referral sender/org')
  for(const key of ['referralType','recipientUserId','patientRegistrationId','reason','priority','clinicalSummary','purpose','consentType','consentEvidenceReference']) {
    check(value[key]===body[key],'referral immutable content')
  }
  check(Date.parse(value.consentRecordedAt)===Date.parse(body.consentRecordedAt)
    && Date.parse(value.accessExpiresAt)===Date.parse(body.accessExpiresAt),'referral immutable dates')
  check(Array.isArray(value.selectedItems) && value.selectedItems.length===0 && !Object.hasOwn(value,'sourceConsultationId'),'selected scope/privacy')
}
async function ready(client,route) {
  for(let attempt=0;attempt<45;attempt++) {
    try{return await client.request(route)}catch(error){if(error.status!==503 || attempt===44) throw error;await delay(1000)}
  }
}
async function saveJson(file,value) {
  try {await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error) {if(error.code!=='ENOENT') throw error;await privateFile(file,JSON.stringify(value,null,2))}
}

export async function sharedCareGate(clients,state,save) {
  validateCareState(state,state.generation)
  const a=clients.doctorA,b=clients.doctorB
  const history='/api/v1/clinical/shared-care/'+state.patientId+'/consultations'
  const fileBase='/api/v1/files/shared-care/'+state.patientId
  const list=fileBase+'/consultations/'+state.consultationId
  const metadata=fileBase+'/'+state.fileId
  const transfer=(client,token,status,id=state.fileId)=>fileBytes(client,id,'GET',token,status,undefined,true,state.patientId,'SHARED_CARE')
  let checks=0
  const verified=(condition,label)=>{check(condition,label);checks++}
  const denied=async(client,route,status=404,method='GET',body)=>{await client.request(route,method,body,[status]);checks++}
  const original=await ready(a,'/api/v1/consultations/'+state.consultationId+'/record')
  verified(original.status==='FINALIZED' && original.organisationId===state.organisationId
    && original.patientRegistrationId===state.patientId && matchesClinicalDraft(original,true),'retained corrected clinical source')
  const deniedReads=async()=> {
    for(const client of [a,b]) {await denied(client,history);await denied(client,history+'/'+state.consultationId);await denied(client,list);await denied(client,metadata)}
    await denied(b,metadata+'/download-grants',404,'POST')
  }
  async function positive(client) {
    const historyPage=await ready(client,history)
    verified(historyPage.organisationId===state.organisationId && historyPage.patientRegistrationId===state.patientId
      && historyPage.content.some(item=>item.consultationId===state.consultationId),'discover finalised history')
    const record=await client.request(history+'/'+state.consultationId)
    verified(isDeepStrictEqual(record.consultation,original),'full effective record and attributable corrections')
    const documents=await ready(client,list)
    verified(documents.organisationId===state.organisationId && documents.patientRegistrationId===state.patientId
      && documents.consultationId===state.consultationId && [state.fileId,state.secondFileId].every(id=>
        documents.content.some(file=>file.fileId===id && file.scanStatus==='CLEAN' && file.downloadAvailable)),'both clean files discovered')
    const file=await client.request(metadata)
    verified(file.file.fileId===state.fileId && file.file.consultationId===state.consultationId
      && file.organisationId===state.organisationId && file.patientRegistrationId===state.patientId,'care file metadata scope')
    const grant=await client.request(metadata+'/download-grants','POST')
    verified(grant.fileId===state.fileId && grant.downloadPath===metadata+'/content'
      && /^[A-Za-z0-9_-]{32,128}$/.test(grant.downloadToken) && Date.parse(grant.expiresAt)>Date.now()
      && Date.parse(grant.expiresAt)-Date.now()<=300000,'bounded private file token')
    verified(syntheticPdf().equals(await transfer(client,grant.downloadToken,200)),'exact private care bytes')
    await transfer(client,grant.downloadToken,409);checks++
  }
  async function referral(name) {
    if(!state.cases[name]) {
      state.cases[name]={requestId:randomUUID(),consentAt:new Date(Date.now()-1000).toISOString(),
        expiresAt:new Date(Date.now()+(name==='expiry'?90000:24*60*60*1000)).toISOString()}
      await save(state)
    }
    const entry=state.cases[name]
    let value=entry.referralId ? await ready(a,'/api/v1/referrals/'+entry.referralId)
      : await a.request('/api/v1/referrals','POST',careCommand(state,name),[201])
    verifyCareReferral(value,state,name);checks++
    if(!entry.referralId) {entry.referralId=value.id;await save(state)}
    if(value.status==='SENT') {
      if(name==='main') {await deniedReads();console.log('Shared-care pending access: denied for both doctors.')}
      value=await b.request('/api/v1/referrals/'+value.id+'/accept','POST',{expectedVersion:value.version})
      verified(value.status==='ACTIVE' && uuid.test(value.sharingGrantId),'accepted shared care')
    }
    return value
  }
  async function terminate(name,action,value,client=a) {
    const expected=action==='complete'?'COMPLETED':'REVOKED'
    if(value.status==='ACTIVE') value=await client.request('/api/v1/referrals/'+value.id+'/'+action,'POST',
      {expectedVersion:value.version,...(action==='revoke'?{reason:'Synthetic shared-care acceptance complete.'}:{})})
    verified(value.status===expected,'expected termination');return value
  }

  let main=await referral('main')
  if(main.status==='ACTIVE') {
    await positive(a);await positive(b)
    const second=await b.request(fileBase+'/'+state.secondFileId+'/download-grants','POST')
    verified(syntheticPdf().equals(await transfer(b,second.downloadToken,200,state.secondFileId)),'previously unselected document is authorised by care')
    for(const [key,status] of [['unrelatedDoctor',404],['receptionist',403],['orgAdmin',403],['patient',401]]) {
      await denied(clients[key],history,status);await denied(clients[key],list,status);await denied(clients[key],metadata,status)
    }
    await b.select(state.otherOrganisationId)
    try {await denied(b,history);await denied(b,metadata)} finally {await b.select(state.organisationId)}
    const foreignPatient=randomUUID()
    await denied(b,'/api/v1/clinical/shared-care/'+foreignPatient+'/consultations')
    await denied(b,'/api/v1/files/shared-care/'+foreignPatient+'/'+state.fileId)
    await denied(b,'/api/v1/consultations/'+state.consultationId+'/record')
    await denied(b,'/api/v1/consultations/'+state.consultationId+'/corrections',404,'POST',
      {version:original.version,targetType:'CONSULTATION',targetId:null,fieldName:'clinicalAssessment',newValue:'Unauthorized synthetic change',reason:'Synthetic denial test.'})
    await denied(b,'/api/v1/files/uploads',404,'POST',{consultationId:state.consultationId,
      originalFilename:'unauthorised-synthetic.pdf',contentType:'application/pdf',declaredSize:syntheticPdf().length})
    await denied(b,'/api/v1/clinical/shared/'+state.patientId+'/CONSULTATION/'+state.consultationId)
    await denied(b,'/api/v1/files/shared/'+state.patientId+'/'+state.fileId)
    const token=await b.request(metadata+'/download-grants','POST')
    await transfer(clients.unrelatedDoctor,token.downloadToken,404);checks++
    state.cases.main.positiveAt=new Date().toISOString();state.cases.main.downloadToken=token.downloadToken;await save(state)
    console.log('Shared-care active access: both doctors, clinical/files and isolation checks passed.')
    main=await terminate('main','revoke',main)
  }
  verified(main.status==='REVOKED' && state.cases.main.positiveAt,'retained revoked care evidence')
  // A valid token issued before revocation cannot keep byte access alive.
  if(!state.cases.main.finishedAt) {
    check(state.cases.main.downloadToken,'outstanding-token recovery evidence')
    await transfer(b,state.cases.main.downloadToken,404);checks++
    await deniedReads();delete state.cases.main.downloadToken
    state.cases.main.finishedAt=new Date().toISOString();await save(state)
  }

  let independent=await referral('independent'),supporting=await referral('supporting')
  if(independent.status==='ACTIVE') {
    check(supporting.status==='ACTIVE','independent live support')
    await positive(b)
    state.cases.independent.positiveAt=new Date().toISOString()
    state.cases.supporting.positiveAt=new Date().toISOString();await save(state)
    independent=await terminate('independent','complete',independent,b)
  }
  verified(independent.status==='COMPLETED' && state.cases.independent.positiveAt,'recipient completion evidence')
  if(!state.cases.independent.finishedAt) {
    check(supporting.status==='ACTIVE','supporting care still active')
    await positive(b) // Ending one referral never invalidates an independent active care grant.
    state.cases.independent.finishedAt=new Date().toISOString();await save(state)
  }
  supporting=await terminate('supporting','revoke',supporting)
  if(!state.cases.supporting.finishedAt) {
    await deniedReads();state.cases.supporting.finishedAt=new Date().toISOString();await save(state)
  }
  console.log('Shared-care termination: revocation, completion and independent authority passed.')

  let expiry=await referral('expiry')
  if(!state.cases.expiry.positiveAt) {
    check(expiry.status==='ACTIVE','unexpired positive expiry evidence')
    await positive(b);state.cases.expiry.positiveAt=new Date().toISOString();await save(state)
  }
  const expiresAt=Date.parse(state.cases.expiry.expiresAt)
  while(Date.now()<=expiresAt+300) await delay(Math.min(1000,expiresAt+301-Date.now()))
  await deniedReads()
  expiry=await b.request('/api/v1/referrals/'+expiry.id)
  // Access already denied above from timestamps; lifecycle persistence is scheduled separately.
  for(let attempt=0;expiry.status==='ACTIVE' && attempt<65;attempt++) {
    await delay(1000);expiry=await b.request('/api/v1/referrals/'+expiry.id)
  }
  verified(expiry.status==='EXPIRED','scheduled expiry state')
  state.cases.expiry.finishedAt??=new Date().toISOString()
  verified(isDeepStrictEqual(await a.request('/api/v1/consultations/'+state.consultationId+'/record'),original),'original record remains unchanged and author-readable')
  const owned=await a.request('/api/v1/files/'+state.fileId+'/download-grants','POST')
  verified(syntheticPdf().equals(await fileBytes(a,state.fileId,'GET',owned.downloadToken,200)),'independent author file access survives termination')
  state.verifiedAt=new Date().toISOString();await save(state)
  return {checks,allCasesVerified:cases.every(name=>Boolean(state.cases[name].finishedAt)),syntheticScanOnly:true,
    referralNotificationsVerified:false,newTreatmentAppointmentVerified:false}
}
export async function main(args) {
  if(args.length!==1 || args[0]!=='verify') throw new Error('Use: node scripts/synthetic-shared-care.mjs verify')
  await lock(async()=> {
    const manifest=await currentManifest(),directory=generationPath(manifest.id)
    const read=async name=>parsePrivateJson(await readPrivate(path.join(directory,name)))
    const accounts=validateAccounts(await read('demo-accounts.json'),manifest.id),seed=await read('demo-seed-report.json')
    const clinical=await read('workflow-clinical-report.json'),first=await read('workflow-files-report.json'),second=await read('workflow-files-unselected-report.json')
    check([seed,clinical,first,second].every(value=>value.generation===manifest.id) && clinical.status==='FINALIZED'
      && first.consultationId===clinical.consultationId && second.consultationId===clinical.consultationId,'retained synthetic provenance')
    const scope={kind:'sahha-synthetic-shared-care-v1',generation:manifest.id,organisationId:seed.organisations.A,
      otherOrganisationId:seed.organisations.B,patientId:clinical.registrationId,consultationId:clinical.consultationId,
      fileId:first.fileId,secondFileId:second.fileId}
    const journal=path.join(directory,'workflow-shared-care.json')
    let state
    try {state=await read('workflow-shared-care.json')} catch(error) {if(error.code!=='ENOENT') throw error;state={...scope,cases:{}}}
    validateCareState(state,manifest.id);check(Object.entries(scope).every(([key,value])=>state[key]===value),'changed retained scope')
    const save=async value=>{validateCareState(value,manifest.id);await saveJson(journal,value)}
    await save(state)
    const names=batchNames('collaboration').filter(name=>name!=='notification-service').concat('clinical-service','file-service')
    const report=await withSyntheticPlatform(manifest,names,()=>withClients(accounts.accounts,seed,clients=>sharedCareGate(clients,state,save)))
    await saveJson(path.join(directory,'workflow-shared-care-report.json'),{generation:manifest.id,verifiedAt:state.verifiedAt,...report})
    console.log('Shared-treatment live gate: '+report.checks+' Gateway assertions passed; helpers stopped. Notification/new-appointment acceptance remains separate.')
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
