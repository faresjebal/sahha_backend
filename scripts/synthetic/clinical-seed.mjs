import { isDeepStrictEqual } from 'node:util'
import { accountIdentity, waitOrganisationRoute } from '../synthetic-demo.mjs'
import { GatewayClient } from './gateway-client.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const check=(condition,label)=>{if(!condition) throw new Error(`Synthetic clinical workflow refused: ${label}; existing records are not overwritten`)}
const NOTE='Initial synthetic demonstration note.'
const CORRECTED='Corrected synthetic demonstration note.'
const REASON='Synthetic append-only correction demonstration.'
const pause=()=>new Promise(resolve=>setTimeout(resolve,500))
export function clinicalDraft(version) {
  return {version,reasonForConsultation:'Synthetic internship encounter, not real care.',clinicalAssessment:'Simulated finding for software verification only.',
    treatmentPlan:'Demonstration only; no clinical recommendation.',followUpInstructions:'Synthetic follow-up workflow demonstration.',additionalNotes:NOTE,
    symptoms:[{name:'Synthetic example symptom',onsetDescription:'Demonstration only',severity:null,notes:null}],
    history:[{category:'ALLERGY',description:'Synthetic example allergy, not real patient information.',notes:null}],vitalSigns:null,
    examinationFindings:[{bodySystem:'General',finding:'Synthetic examination finding.',notes:null}],
    diagnoses:[{code:null,codeSystem:null,label:'Synthetic example finding',type:'PRIMARY',status:'CONFIRMED',notes:null}],
    medications:[{kind:'PRESCRIBED',name:'Synthetic example medication, not for clinical use',strength:null,form:null,dosage:'Demonstration only',
      frequency:'Demonstration only',route:'Demonstration only',duration:'Demonstration only',quantity:null,specialInstructions:'Software fixture, not a prescription.'}]}
}
export function matchesClinicalDraft(record,corrected=false) {
  const expected=clinicalDraft(record.version)
  if(corrected) expected.additionalNotes=CORRECTED
  return Object.entries(expected).every(([key,value])=>{
    if(Array.isArray(value)) return Array.isArray(record[key]) && record[key].length===value.length
      && value.every((item,index)=>Object.entries(item).every(([field,fieldValue])=>isDeepStrictEqual(record[key][index][field],fieldValue)))
    return isDeepStrictEqual(record[key],value)
  })
}
export function emptyClinicalDraft(record) {
  return Object.entries(clinicalDraft(0)).every(([key,value])=>key==='version' || (Array.isArray(value)?record[key]?.length===0:record[key]===null || record[key]===''))
}
export function validateClinicalState(state,generation,appointmentId) {
  check(uuid.test(generation) && uuid.test(appointmentId) && state?.kind==='sahha-synthetic-clinical-v1'
    && state.generation===generation && state.appointmentId===appointmentId, 'journal identity')
  check(Object.keys(state).every(key=>['kind','generation','appointmentId','consultationId'].includes(key)), 'unknown journal fields')
  if(state.consultationId!==undefined) check(uuid.test(state.consultationId), 'consultation identifier')
  return state
}
export async function seedClinical(accounts,seed,scheduling,state,save,createClient=()=>new GatewayClient(),wait=pause) {
  validateClinicalState(state,seed.generation,scheduling.appointmentId)
  check(scheduling.generation===seed.generation && uuid.test(scheduling.registrationId) && uuid.test(scheduling.patientId)
    && uuid.test(seed.organisations?.A) && uuid.test(seed.organisations?.B) && uuid.test(seed.memberships?.['A:doctorA']), 'scheduling provenance')
  const keys=['doctorA','doctorB','unrelatedDoctor','receptionist','orgAdmin','patient'],byKey=Object.fromEntries(accounts.map(value=>[value.key,value]))
  for(const key of keys) check(byKey[key]?.id===accountIdentity(key).id, 'account identity')
  const clients=Object.fromEntries(keys.map(key=>[key,createClient()]));let checks=0
  const verified=(condition,label)=>{check(condition,label);checks++}
  try {
    for(const key of keys) verified((await clients[key].login(byKey[key])).userId===byKey[key].id,'authenticated identity')
    await waitOrganisationRoute(clients.doctorA)
    for(const key of keys.filter(key=>key!=='patient')) verified((await clients[key].select(seed.organisations.A)).activeOrganisationId===seed.organisations.A,'active organisation')
    const doctor=clients.doctorA
    // Retry a read only, never a clinical write whose response could be lost.
    for(let attempt=0;attempt<120;attempt++) {
      try {await doctor.request('/api/v1/consultations/referral-sources?size=1');break}
      catch(error){if(error.status!==503 || attempt===119) throw error;await wait()}
    }
    if(!state.consultationId) {
      const created=await doctor.request('/api/v1/consultations','POST',{appointmentId:state.appointmentId},[200,201])
      check(uuid.test(created.id) && created.appointmentId===state.appointmentId,'created consultation scope')
      state.consultationId=created.id;await save(state)
      const replay=await doctor.request('/api/v1/consultations','POST',{appointmentId:state.appointmentId})
      verified(replay.id===created.id && replay.version===created.version,'consultation creation idempotency')
    }
    const route=`/api/v1/consultations/${state.consultationId}`
    let record=await doctor.request(`${route}/record`)
    const verifyScope=value=>verified(value.id===state.consultationId && value.organisationId===seed.organisations.A
      && value.appointmentId===state.appointmentId && value.patientRegistrationId===scheduling.registrationId && value.patientId===scheduling.patientId
      && value.doctorUserId===byKey.doctorA.id && value.doctorMembershipId===seed.memberships['A:doctorA'],'clinical owner and patient scope')
    verifyScope(record)
    const before=await doctor.request(`/api/v1/appointments/${state.appointmentId}`)
    if(before.status==='IN_PROGRESS') {
      const activeSummary=await doctor.request(`/api/v1/clinical/patients/${scheduling.registrationId}/summary`)
      verified(activeSummary.organisationId===seed.organisations.A && activeSummary.patientId===scheduling.patientId
        && activeSummary.careRelationship.appointmentId===state.appointmentId && Array.isArray(activeSummary.encounters),'active appointment patient-summary access')
    } else {
      check(before.status==='COMPLETED','unexpected existing appointment status')
      await doctor.request(`/api/v1/clinical/patients/${scheduling.registrationId}/summary`,'GET',undefined,[404]);checks++
    }
    if(record.status==='DRAFT') {
      if(!matchesClinicalDraft(record)) {
        check(emptyClinicalDraft(record),'existing draft content differs from fixture')
        record=await doctor.request(`${route}/draft-content`,'PUT',clinicalDraft(record.version))
      }
      verified(matchesClinicalDraft(record),'saved structured draft')
      record=await doctor.request(`${route}/finalize`,'POST',{version:record.version})
    }
    verified(record.status==='FINALIZED' && record.finalizedByUserId===byKey.doctorA.id && Number.isFinite(Date.parse(record.finalizedAt)),'attributable finalisation')
    check(Array.isArray(record.corrections) && record.corrections.length<=1,'unexpected corrections')
    if(record.corrections.length===0) {
      check(matchesClinicalDraft(record),'finalised fixture content differs')
      record=await doctor.request(`${route}/corrections`,'POST',{version:record.version,targetType:'CONSULTATION',targetId:null,fieldName:'additionalNotes',newValue:CORRECTED,reason:REASON})
    }
    const correction=record.corrections[0]
    verified(record.corrections.length===1 && correction.targetType==='CONSULTATION' && correction.targetId===null && correction.fieldName==='additionalNotes'
      && correction.oldValue===NOTE && correction.newValue===CORRECTED && correction.reason===REASON && correction.actorUserId===byKey.doctorA.id
      && Number.isFinite(Date.parse(correction.correctedAt)),'append-only correction attribution and preserved original')
    verified(matchesClinicalDraft(record,true),'effective corrected content')
    verifyScope(record)
    // Establish the baseline from committed database state, not a command's
    // in-memory entity (Instant precision may differ after PostgreSQL storage).
    record=await doctor.request(`${route}/record`)
    verified(record.status==='FINALIZED' && matchesClinicalDraft(record,true) && record.corrections.length===1
      && record.corrections[0].id===correction.id,'persisted signed record baseline')
    verifyScope(record)
    // Neither finalisation nor source replacement may mutate the signed record.
    await doctor.request(`${route}/draft-content`,'PUT',clinicalDraft(record.version),[409]);checks++
    await doctor.request(`${route}/finalize`,'POST',{version:record.version},[409]);checks++
    verified(isDeepStrictEqual(await doctor.request(`${route}/record`),record),'denied writes leave signed record unchanged')
    let appointment
    for(let attempt=0;attempt<120;attempt++) {
      appointment=await doctor.request(`/api/v1/appointments/${state.appointmentId}`)
      if(appointment.status==='COMPLETED') break
      check(appointment.status==='IN_PROGRESS','unexpected appointment status during Kafka projection')
      await wait()
    }
    verified(appointment.status==='COMPLETED','real Kafka finalisation-to-appointment completion')
    // A completed appointment is not an ongoing care grant. Own signed records
    // remain accessible, but a broad patient summary requires current care.
    await doctor.request(`/api/v1/clinical/patients/${scheduling.registrationId}/summary`,'GET',undefined,[404]);checks++
    verified(isDeepStrictEqual(await doctor.request(`${route}/record`),record),'author retains own immutable record after appointment completion')
    for(const key of keys.filter(key=>key!=='doctorA')) {
      // Clinical JWT validation requires active staff organisation context.
      const denied=key==='patient'?401:['doctorB','unrelatedDoctor'].includes(key)?404:403
      await clients[key].request(`${route}/record`,'GET',undefined,[denied]);checks++
    }
    await doctor.select(seed.organisations.B)
    await doctor.request(`${route}/record`,'GET',undefined,[403]);checks++
    await doctor.select(seed.organisations.A)
    return {generation:state.generation,verifiedAt:new Date().toISOString(),consultationId:record.id,appointmentId:state.appointmentId,
      registrationId:scheduling.registrationId,patientId:scheduling.patientId,status:record.status,correctionId:correction.id,checks}
  } finally {
    let failed=false
    for(const client of Object.values(clients)) {
      try{if(client.cookies.size) await client.request('/api/v1/auth/logout','POST',undefined,[204])}
      catch{failed=true}finally{client.dispose()}
    }
    if(failed) throw new Error('Synthetic clinical logout failed; all clients discarded')
  }
}
