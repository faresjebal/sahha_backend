#!/usr/bin/env node
// Service-owned Gateway commands only; no direct writes to clinical/domain tables.
import { randomUUID } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { currentManifest, generationPath, lock, parsePrivateJson, privateFile, readPrivate } from './synthetic-db.mjs'
import { accountIdentity, pageItems, validateAccounts, waitOrganisationRoute } from './synthetic-demo.mjs'
import { GatewayClient } from './synthetic/gateway-client.mjs'
import { batchNames, withSyntheticPlatform } from './synthetic/platform-runtime.mjs'
import { seedClinical, validateClinicalState } from './synthetic/clinical-seed.mjs'
import { FILE_NAME, UNSELECTED_FILE_NAME, seedFiles, validateFileState } from './synthetic/file-seed.mjs'
import { seedMessages, validateMessageState } from './synthetic/message-seed.mjs'

const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const KIND = 'sahha-synthetic-scheduling-v1'
const DAYS = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']
const keys = ['receptionist','doctorA','doctorB','otherAdmin','patient']
const check = (condition, label) => { if (!condition) throw new Error(`Synthetic workflow refused: ${label}; existing data is not overwritten`) }
export const PATIENT = Object.freeze({ firstName:'Ada',lastName:'SahhaDemo',dateOfBirth:'1990-01-15',sex:'FEMALE',phoneNumber:'+21670000999',
  email:'patient@sahha.example.test',address:'24 Synthetic Street',city:'Tunis',countryCode:'TN',privacyNoticeAcknowledged:true })
export function availabilityBody() {
  return {timeZone:'Africa/Tunis',appointmentDurationMinutes:30,minimumLeadTimeMinutes:0,bookingHorizonDays:30,locationLabel:'Synthetic demonstration room',
    weeklyWindows:DAYS.map(dayOfWeek=>({dayOfWeek,startTime:'09:00',endTime:'17:00'})),breaks:[],timeOff:[],version:null}
}
export function validateWorkflowState(state,generation) {
  check(state?.kind===KIND && state.generation===generation && uuid.test(generation), 'workflow generation identity')
  check(Object.keys(state).every(key=>['kind','generation','organisationId','booking','appointmentId'].includes(key)), 'unknown workflow state')
  if (state.organisationId !== undefined) check(uuid.test(state.organisationId), 'organisation identifier')
  if (state.appointmentId !== undefined) check(uuid.test(state.appointmentId) && state.booking, 'appointment identifier')
  if (state.booking !== undefined) {
    check(Object.keys(state.booking).sort().join(',')==='bookingRequestId,doctorUserId,patientRegistrationId,startsAt', 'booking fields')
    for (const key of ['bookingRequestId','doctorUserId','patientRegistrationId']) check(uuid.test(state.booking[key]), 'booking identifiers')
    check(state.organisationId && typeof state.booking.startsAt==='string' && /^\d{4}-\d{2}-\d{2}T/.test(state.booking.startsAt)
      && Number.isFinite(Date.parse(state.booking.startsAt)), 'booking time/context')
  }
  return state
}
async function readReady(client,route,pause=()=>new Promise(resolve=>setTimeout(resolve,1000))) {
  for (let attempt=0;attempt<60;attempt++) {
    try { return await client.request(route) }
    catch (error) { if (error.status!==503 || attempt===59) throw error; await pause() }
  }
}
function verifyAvailability(value,organisationId,doctorUserId) {
  const expected=availabilityBody()
  check(value?.organisationId===organisationId && value.doctorUserId===doctorUserId, 'availability owner')
  for (const key of ['timeZone','appointmentDurationMinutes','minimumLeadTimeMinutes','bookingHorizonDays','locationLabel']) check(value[key]===expected[key], 'existing availability configuration')
  check(value.breaks?.length===0 && value.timeOff?.length===0 && value.weeklyWindows?.length===7, 'existing availability exceptions')
  check(new Set(value.weeklyWindows.map(window=>window.dayOfWeek)).size===7 && value.weeklyWindows.every(window=>DAYS.includes(window.dayOfWeek)
    && /^09:00(?::00)?$/.test(window.startTime) && /^17:00(?::00)?$/.test(window.endTime)), 'existing availability windows')
}
export async function seedScheduling(accounts,seed,state,save,createClient=()=>new GatewayClient(),now=()=>new Date()) {
  validateWorkflowState(state,seed.generation)
  check(uuid.test(seed.organisations?.A) && uuid.test(seed.organisations?.B) && seed.organisations.A!==seed.organisations.B, 'seed organisations')
  const byKey=Object.fromEntries(accounts.map(account=>[account.key,account]))
  for (const key of keys) check(byKey[key]?.id===accountIdentity(key).id, 'seed account identity')
  if (state.organisationId) check(state.organisationId===seed.organisations.A, 'changed organisation')
  if (state.booking) check(state.booking.doctorUserId===byKey.doctorA.id, 'changed booking doctor')
  const clients=Object.fromEntries(keys.map(key=>[key,createClient()]))
  let checks=0
  const verified=(condition,label)=>{check(condition,label);checks++}
  try {
    for (const key of keys) verified((await clients[key].login(byKey[key])).userId===byKey[key].id, 'authenticated seed identity')
    await waitOrganisationRoute(clients.receptionist)
    for (const key of keys.filter(key=>key!=='patient')) {
      const org=seed.organisations[key==='otherAdmin'?'B':'A']
      verified((await clients[key].select(org)).activeOrganisationId===org, 'active organisation')
    }
    const reception=clients.receptionist, doctor=clients.doctorA, patient=clients.patient, org=seed.organisations.A
    const found=pageItems(await readReady(reception,`/api/v1/patients?query=${encodeURIComponent(PATIENT.phoneNumber)}&size=100`))
    check(found.length<=1, 'ambiguous synthetic patient')
    const duplicateBody=Object.fromEntries(['firstName','lastName','dateOfBirth','sex','phoneNumber','email'].map(key=>[key,PATIENT[key]]))
    let registration
    if (found.length) registration=await reception.request(`/api/v1/patients/${found[0].registrationId}`)
    else {
      check(!state.booking, 'previously booked patient disappeared')
      const duplicates=await reception.request('/api/v1/patients/duplicate-check','POST',duplicateBody)
      verified(duplicates.reviewRequired===false && duplicates.candidates?.length===0, 'new patient duplicate review')
      registration=await reception.request('/api/v1/patients','POST',PATIENT,[201])
    }
    verified(uuid.test(registration.registrationId) && uuid.test(registration.patientId) && registration.organisationId===org
      && registration.registrationStatus==='ACTIVE' && registration.registeredBy===byKey.receptionist.id, 'registration provenance')
    for (const [key,value] of Object.entries(duplicateBody)) verified(registration[key]===value, 'retained patient identity')
    const repeatedDuplicate=await reception.request('/api/v1/patients/duplicate-check','POST',duplicateBody)
    verified(repeatedDuplicate.reviewRequired===true && repeatedDuplicate.candidates?.some(value=>value.patientId===registration.patientId
      && value.activeOrganisationRegistrationId===registration.registrationId), 'duplicate resolves existing registration')
    const linked=await patient.request('/api/v1/patients/me/account-link','POST',{organisationId:org,medicalRecordNumber:registration.medicalRecordNumber,dateOfBirth:PATIENT.dateOfBirth},[201])
    verified(linked.authUserId===byKey.patient.id, 'explicit verified patient-account link')
    const owned=await patient.request('/api/v1/patients/me/registrations')
    verified(owned.length===1 && owned[0].registrationId===registration.registrationId && owned[0].organisationId===org, 'patient registration scope')
    for (const key of ['doctorA','doctorB']) {
      let availability
      try { availability=await readReady(clients[key],'/api/v1/availability/me') }
      catch (error) { if (error.status!==404) throw error; availability=await clients[key].request('/api/v1/availability/me','PUT',availabilityBody()) }
      verifyAvailability(availability,org,byKey[key].id);checks++
    }
    const directory=await patient.request(`/api/v1/availability/mine/registrations/${registration.registrationId}/doctors`)
    verified(['doctorA','doctorB'].every(key=>directory.some(value=>value.doctorUserId===byKey[key].id)), 'patient doctor directory')
    if (!state.booking) {
      const from=now().toISOString().slice(0,10), to=new Date(now().getTime()+2*86400000).toISOString().slice(0,10)
      const available=await reception.request(`/api/v1/availability/doctors/${byKey.doctorA.id}/slots?from=${from}&to=${to}`)
      const slot=available.slots?.find(value=>Date.parse(value.startsAt)>now().getTime()+300000)
      verified(available.organisationId===org && available.doctorUserId===byKey.doctorA.id && slot, 'server-calculated future slot')
      state.organisationId=org
      state.booking={bookingRequestId:randomUUID(),patientRegistrationId:registration.registrationId,doctorUserId:byKey.doctorA.id,startsAt:slot.startsAt}
      // Persist the exact idempotency key/body BEFORE a potentially committed POST.
      await save(state)
    }
    check(state.booking.patientRegistrationId===registration.registrationId, 'changed booking patient')
    let appointment=state.appointmentId ? await doctor.request(`/api/v1/appointments/${state.appointmentId}`)
      : await reception.request('/api/v1/appointments','POST',state.booking,[200,201])
    const verifyAppointment=value=>verified(uuid.test(value.id) && value.organisationId===org && value.patientRegistrationId===registration.registrationId
      && value.patientId===registration.patientId && value.doctorUserId===byKey.doctorA.id && value.bookingRequestId===state.booking.bookingRequestId
      && Date.parse(value.startsAt)===Date.parse(state.booking.startsAt), 'appointment scope and retained command')
    verifyAppointment(appointment)
    state.appointmentId=appointment.id;await save(state)
    // Prove duplicate commands reuse the appointment, never allocate a second slot.
    const replay=await reception.request('/api/v1/appointments','POST',state.booking)
    verified(replay.id===appointment.id && replay.version===appointment.version, 'booking idempotency')
    for (const [source,target,command,client] of [
      ['REQUESTED','CONFIRMED','confirm',doctor],['CONFIRMED','CHECKED_IN','check-in',reception],['CHECKED_IN','IN_PROGRESS','start',doctor]]) {
      if (appointment.status!==source) continue
      const body={commandRequestId:randomUUID(),version:appointment.version}
      const updated=await client.request(`/api/v1/appointments/${appointment.id}/${command}`,'POST',body)
      verified(updated.status===target && updated.version>appointment.version, 'authorised appointment transition')
      const replayed=await client.request(`/api/v1/appointments/${appointment.id}/${command}`,'POST',body)
      verified(replayed.id===updated.id && replayed.version===updated.version, 'transition idempotency')
      appointment=updated
    }
    verified(['IN_PROGRESS','COMPLETED'].includes(appointment.status), 'appointment ready for clinical workflow')
    verifyAppointment(appointment)
    const start=Date.parse(appointment.startsAt), range=new URLSearchParams({from:new Date(start-86400000).toISOString(),to:new Date(start+86400000).toISOString()})
    const mine=await patient.request(`/api/v1/appointments/mine?registrationId=${registration.registrationId}&${range}`)
    verified(mine.some(value=>value.id===appointment.id && value.status===appointment.status), 'patient live appointment status')
    for (const [client,route,status] of [[clients.doctorB,`/api/v1/appointments/${appointment.id}`,403],
      [clients.otherAdmin,`/api/v1/patients/${registration.registrationId}`,404],[patient,'/api/v1/patients',403],
      [reception,'/api/v1/availability/me',403]]) {
      await client.request(route,'GET',undefined,[status]);checks++
    }
    await reception.request(`/api/v1/appointments/${appointment.id}/confirm`,'POST',{commandRequestId:randomUUID(),version:appointment.version},[403]);checks++
    return {generation:state.generation,verifiedAt:new Date().toISOString(),registrationId:registration.registrationId,patientId:registration.patientId,
      appointmentId:appointment.id,status:appointment.status,checks}
  } finally {
    let failed=false
    for (const client of Object.values(clients)) {
      try { if (client.cookies.size) await client.request('/api/v1/auth/logout','POST',undefined,[204]) }
      catch { failed=true }
      finally { client.dispose() }
    }
    if (failed) throw new Error('Synthetic workflow logout failed; every client discarded')
  }
}
async function saveJson(file,value) {
  try { await readPrivate(file); await privateFile(file,JSON.stringify(value,null,2),'w') }
  catch (error) { if (error.code!=='ENOENT') throw error; await privateFile(file,JSON.stringify(value,null,2)) }
}
export async function main(args) {
  if (args.length!==2 || args[0]!=='seed' || !['scheduling','clinical','files','files-unselected','messages'].includes(args[1])) throw new Error('Use: node scripts/synthetic-workflow.mjs seed scheduling|clinical|files|files-unselected|messages')
  await lock(async()=>{
    const manifest=await currentManifest(), directory=generationPath(manifest.id)
    const accounts=validateAccounts(parsePrivateJson(await readPrivate(path.join(directory,'demo-accounts.json'))),manifest.id)
    const seed=parsePrivateJson(await readPrivate(path.join(directory,'demo-seed-report.json')))
    check(seed.generation===manifest.id, 'identity seed generation')
    if(args[1]==='messages') {
      const journal=path.join(directory,'workflow-messages.json')
      let state
      try{state=validateMessageState(parsePrivateJson(await readPrivate(journal)),manifest.id,seed.organisations?.A)}
      catch(error){if(error.code!=='ENOENT') throw error;state={kind:'sahha-synthetic-messages-v1',generation:manifest.id,organisationId:seed.organisations?.A}}
      validateMessageState(state,manifest.id,seed.organisations?.A)
      const evidence=await withSyntheticPlatform(manifest,batchNames('collaboration'),()=>seedMessages(accounts.accounts,seed,state,value=>saveJson(journal,value)),{eventDelivery:true})
      await saveJson(path.join(directory,'workflow-messages-report.json'),evidence)
      console.log(`Synthetic messaging workflow: ${evidence.checks} Gateway/Kafka assertions passed; WebSocket/patient-context gates remain separate; helpers stopped.`)
      return
    }
    if(['files','files-unselected'].includes(args[1])) {
      const clinical=parsePrivateJson(await readPrivate(path.join(directory,'workflow-clinical-report.json')))
      check(clinical.generation===manifest.id && clinical.status==='FINALIZED' && uuid.test(clinical.consultationId), 'clinical report generation/scope')
      const fileJournal=path.join(directory,`workflow-${args[1]}.json`)
      let fileState
      try{fileState=validateFileState(parsePrivateJson(await readPrivate(fileJournal)),manifest.id,clinical.consultationId)}
      catch(error){if(error.code!=='ENOENT') throw error;fileState={kind:'sahha-synthetic-files-v1',generation:manifest.id,consultationId:clinical.consultationId}}
      validateFileState(fileState,manifest.id,clinical.consultationId)
      const filename=args[1]==='files'?FILE_NAME:UNSELECTED_FILE_NAME
      const evidence=await withSyntheticPlatform(manifest,batchNames('clinical'),()=>seedFiles(accounts.accounts,seed,clinical,fileState,value=>saveJson(fileJournal,value),undefined,undefined,{filename}),{eventDelivery:true,syntheticScan:true})
      await saveJson(path.join(directory,`workflow-${args[1]}-report.json`),evidence)
      console.log(`Synthetic file workflow: ${evidence.checks} Gateway/storage assertions passed; synthetic scan only, no malware-scanner claim; helpers stopped.`)
      return
    }
    if(args[1]==='clinical') {
      const scheduling=parsePrivateJson(await readPrivate(path.join(directory,'workflow-scheduling-report.json')))
      check(scheduling.generation===manifest.id && uuid.test(scheduling.appointmentId) && uuid.test(scheduling.registrationId) && uuid.test(scheduling.patientId), 'scheduling report generation/scope')
      const clinicalFile=path.join(directory,'workflow-clinical.json')
      let clinicalState
      try{clinicalState=validateClinicalState(parsePrivateJson(await readPrivate(clinicalFile)),manifest.id,scheduling.appointmentId)}
      catch(error){if(error.code!=='ENOENT') throw error;clinicalState={kind:'sahha-synthetic-clinical-v1',generation:manifest.id,appointmentId:scheduling.appointmentId}}
      validateClinicalState(clinicalState,manifest.id,scheduling.appointmentId)
      const names=batchNames('foundation').filter(name=>name!=='patient-service').concat('clinical-service')
      const evidence=await withSyntheticPlatform(manifest,names,()=>seedClinical(accounts.accounts,seed,scheduling,clinicalState,value=>saveJson(clinicalFile,value)),{eventDelivery:true})
      await saveJson(path.join(directory,'workflow-clinical-report.json'),evidence)
      console.log(`Synthetic clinical workflow: ${evidence.checks} Gateway/Kafka assertions passed; data retained and temporary helpers stopped.`)
      return
    }
    const file=path.join(directory,'workflow-scheduling.json')
    let state
    try { state=validateWorkflowState(parsePrivateJson(await readPrivate(file)),manifest.id) }
    catch (error) { if (error.code!=='ENOENT') throw error; state={kind:KIND,generation:manifest.id} }
    const evidence=await withSyntheticPlatform(manifest,batchNames('foundation'),()=>seedScheduling(accounts.accounts,seed,state,value=>saveJson(file,value)),{eventDelivery:true})
    await saveJson(path.join(directory,'workflow-scheduling-report.json'),evidence)
    console.log(`Synthetic scheduling workflow: ${evidence.checks} Gateway assertions passed; data retained and temporary helpers stopped.`)
  })
}
if (process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
