// Fresh, journalled shared-treatment acceptance. Synthetic Gateway writes only.
import {randomUUID} from 'node:crypto'
import {createRequire} from 'node:module'
import path from 'node:path'
import {pathToFileURL} from 'node:url'
import {ROOT,tcpOpen} from '../sahha.mjs'
import {accountIdentity} from '../synthetic-demo.mjs'
import {confinedDirectory,generationPath} from '../synthetic-db.mjs'
import {clinicalDraft,matchesClinicalDraft,emptyClinicalDraft} from './clinical-seed.mjs'
import {syntheticPdf,FILE_NAME} from './file-seed.mjs'
import {BROWSER_ORIGIN,browserApiUrl,isBlockedDesignFont} from './browser-messages.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const check=(value,label)=>{if(!value)throw new Error('Joint-treatment acceptance refused: '+label)}
const stamp=value=>typeof value==='string' && Number.isFinite(Date.parse(value))
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms))
export function validateJointState(s,generation) {
  check(s?.kind==='sahha-joint-treatment-v1' && s.generation===generation && uuid.test(generation),'generation')
  check(Object.keys(s).every(key=>['kind','generation','organisationId','registrationId','sourceConsultationId','referralCommand',
    'referralId','appointments','positiveAt','browserAt','revokedAt','verifiedAt'].includes(key)),'journal fields')
  for(const key of ['organisationId','registrationId','sourceConsultationId'])check(uuid.test(s[key]),'scope')
  if(s.referralCommand) {
    const c=s.referralCommand
    check(Object.keys(c).sort().join(',')==='accessExpiresAt,clinicalSummary,consentEvidenceReference,consentRecordedAt,consentType,patientRegistrationId,priority,purpose,reason,recipientUserId,referralRequestId,referralType,selectedItems,sendImmediately,sourceConsultationId'
      && uuid.test(c.referralRequestId) && c.referralType==='SHARED_TREATMENT' && c.recipientUserId===accountIdentity('doctorB').id
      && c.patientRegistrationId===s.registrationId && c.sourceConsultationId===s.sourceConsultationId && c.selectedItems?.length===0
      && c.reason==='Synthetic full-app joint treatment' && c.purpose==='Synthetic acceptance only, no real care.'
      && c.consentType==='RECORDED_WRITTEN' && c.consentEvidenceReference==='synthetic-full-app-consent'
      && c.priority==='ROUTINE' && c.clinicalSummary===null && c.sendImmediately===true
      && stamp(c.consentRecordedAt) && stamp(c.accessExpiresAt) && Date.parse(c.accessExpiresAt)>Date.parse(c.consentRecordedAt),'immutable referral command')
  }
  if(s.referralId)check(uuid.test(s.referralId) && s.referralCommand,'referral identity')
  check(s.appointments && !Array.isArray(s.appointments),'appointment journal')
  for(const [key,e] of Object.entries(s.appointments)) {
    check(['doctorA','doctorB'].includes(key) && e && Object.keys(e).every(k=>['booking','appointmentId','reschedule','confirm','check-in','start','consultationId','finalizedAt'].includes(k)),'appointment fields')
    check(s.referralId && e.booking && Object.keys(e.booking).sort().join(',')==='bookingRequestId,doctorUserId,patientRegistrationId,startsAt'
      && uuid.test(e.booking.bookingRequestId) && e.booking.doctorUserId===accountIdentity(key).id
      && e.booking.patientRegistrationId===s.registrationId && stamp(e.booking.startsAt),'immutable booking command')
    for(const k of ['appointmentId','consultationId'])if(e[k])check(uuid.test(e[k]),'resource identity')
    for(const k of ['reschedule','confirm','check-in','start'])if(e[k]) {
      const c=e[k];check(uuid.test(c.commandRequestId) && Number.isInteger(c.version) && c.version>=0
        && Object.keys(c).sort().join(',')===(k==='reschedule'?'commandRequestId,reason,startsAt,version':'commandRequestId,version'),'transition command')
      if(k==='reschedule')check(stamp(c.startsAt) && c.reason==='Synthetic original booking retry','reschedule scope')
    }
    if(e.finalizedAt)check(e.consultationId && stamp(e.finalizedAt),'finalisation evidence')
  }
  for(const k of ['positiveAt','browserAt','revokedAt','verifiedAt'])if(s[k])check(stamp(s[k]) && s.referralId,'checkpoint')
  if(s.positiveAt)check(['doctorA','doctorB'].every(k=>s.appointments[k]?.finalizedAt),'both doctors treated')
  if(s.revokedAt)check(s.positiveAt && s.browserAt,'positive evidence before revocation')
  if(s.verifiedAt)check(s.revokedAt,'completed gate')
  return s
}
async function until(predicate,label) {
  const end=Date.now()+60000
  while(Date.now()<end){if(await predicate())return;await pause(300)}
  check(false,label)
}
async function browserRequest(context,route,method='GET',body,status=200) {
  const headers={Accept:'application/json'}
  if(method!=='GET')headers['X-XSRF-TOKEN']=(await browserRequest(context,'/api/v1/auth/csrf')).token
  const response=await context.request.fetch(browserApiUrl(route),{method,headers,...(body===undefined?{}:{data:body}),maxRedirects:0})
  try{check(response.status()===status,'browser API status '+response.status());return status===204?null:await response.json()}
  finally{await response.dispose()}
}
async function browserCare(manifest,accounts,s,save,revoke) {
  check(!await tcpOpen(5173),'occupied browser port')
  const local=createRequire(path.join(ROOT,'frontend','package.json'))
  const {createServer}=await import(pathToFileURL(local.resolve('vite')).href)
  const {default:react}=await import(pathToFileURL(local.resolve('@vitejs/plugin-react')).href)
  const {chromium}=local('playwright-core')
  const envDir=await confinedDirectory(path.join(generationPath(manifest.id),'browser-environment'),true)
  let server,browser,stage='startup',checks=0,error=false
  const contexts=[],pages=[]
  try {
    server=await createServer({root:path.join(ROOT,'frontend'),configFile:false,envDir,plugins:[react()],logLevel:'silent',
      define:{'import.meta.env.VITE_USE_MOCKS':'"false"','import.meta.env.VITE_USE_AUTH_MOCKS':'"false"','import.meta.env.VITE_API_BASE_URL':'"/api/v1"'},
      server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api/v1':{target:'http://127.0.0.1:8079',changeOrigin:false,ws:true}}}})
    await server.listen()
    browser=await chromium.launch({headless:true,executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'})
    for(const key of ['doctorA','doctorB']) {
      stage=key+' shared-history browser'
      const context=await browser.newContext({viewport:{width:key==='doctorA'?1440:390,height:960},acceptDownloads:true});contexts.push(context)
      await context.route('**/*',async route=>{
        if(new URL(route.request().url()).origin===BROWSER_ORIGIN)await route.continue()
        else {if(!isBlockedDesignFont(route.request().url(),route.request().resourceType()))error=true;await route.abort()}
      })
      const account=accounts.find(a=>a.key===key)
      await browserRequest(context,'/api/v1/auth/login','POST',{email:account.email,password:account.password,deviceName:'Synthetic joint treatment'})
      await browserRequest(context,'/api/v1/auth/active-organisation','POST',{organisationId:s.organisationId})
      const page=await context.newPage();pages.push(page);page.setDefaultTimeout(30000);page.on('pageerror',()=>{error=true})
      await page.goto(BROWSER_ORIGIN+'/doctor/referrals?referral='+s.referralId)
      await page.getByRole('button',{name:'Open shared-care history',exact:true}).click()
      for(const e of Object.values(s.appointments))await page.getByRole('button',{name:'Open encounter '+e.consultationId,exact:true}).waitFor()
      checks+=2
      await page.getByRole('button',{name:'Open encounter '+s.sourceConsultationId,exact:true}).click()
      await page.getByRole('button',{name:'Open encounter documents',exact:true}).click()
      const downloading=page.waitForEvent('download');void downloading.catch(()=>{})
      await page.getByRole('button',{name:'Download document '+FILE_NAME,exact:true}).click()
      const download=await downloading,stream=await download.createReadStream(),chunks=[]
      check(stream,'private browser PDF')
      for await(const chunk of stream)chunks.push(chunk)
      check(Buffer.concat(chunks).equals(syntheticPdf()),'private browser PDF bytes');checks++
      await download.delete()
      check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),'desktop/mobile fit');checks++
    }
    check(!error,'browser runtime or off-Gateway request');checks++
    s.browserAt=new Date().toISOString();await save()
    stage='live revocation';await revoke()
    for(const page of pages) {
      await until(async()=>await page.getByRole('button',{name:'Download document '+FILE_NAME,exact:true}).count()===0,'revocation removes document download')
      check(await page.getByRole('button',{name:'Open encounter '+s.sourceConsultationId,exact:true}).count()===0,'revocation removes protected history');checks+=2
    }
    return checks
  } catch(failure) {
    throw new Error((failure.message?.startsWith('Joint-treatment acceptance refused:')?failure.message:'Joint-treatment browser failed; private diagnostics suppressed')+'; stage='+stage)
  } finally {
    let failed=false
    for(const context of contexts) {
      try{if((await context.cookies()).some(c=>c.name==='SAHHA_DEMO_ACCESS'))await browserRequest(context,'/api/v1/auth/logout','POST',undefined,204)}
      catch{failed=true}finally{await context.close().catch(()=>{failed=true})}
    }
    if(browser)await browser.close().catch(()=>{failed=true})
    if(server)await server.close().catch(()=>{failed=true})
    if(failed)throw new Error('Joint-treatment browser cleanup failed')
  }
}
export async function jointTreatmentGate(manifest,accounts,seed,s,save,clients) {
  validateJointState(s,manifest.id)
  const a=clients.doctorA,b=clients.doctorB,reception=clients.receptionist
  const history='/api/v1/clinical/shared-care/'+s.registrationId+'/consultations'
  let checks=0
  const verified=(value,label)=>{check(value,label);checks++}
  if(!s.referralCommand) {
    s.referralCommand={referralType:'SHARED_TREATMENT',referralRequestId:randomUUID(),recipientUserId:accountIdentity('doctorB').id,
      patientRegistrationId:s.registrationId,sourceConsultationId:s.sourceConsultationId,reason:'Synthetic full-app joint treatment',
      priority:'ROUTINE',clinicalSummary:null,purpose:'Synthetic acceptance only, no real care.',consentType:'RECORDED_WRITTEN',
      consentEvidenceReference:'synthetic-full-app-consent',consentRecordedAt:new Date(Date.now()-1000).toISOString(),
      accessExpiresAt:new Date(Date.now()+86400000).toISOString(),selectedItems:[],sendImmediately:true}
    await save()
  }
  let referral=s.referralId?await a.request('/api/v1/referrals/'+s.referralId)
    :await a.request('/api/v1/referrals','POST',s.referralCommand,[201])
  check(referral.organisationId===s.organisationId && referral.patientRegistrationId===s.registrationId
    && referral.referralType==='SHARED_TREATMENT' && referral.recipientUserId===accountIdentity('doctorB').id,'referral provenance')
  s.referralId=referral.id;await save()
  if(referral.status==='SENT') {
    await b.request(history,'GET',undefined,[404]);checks++
    referral=await b.request('/api/v1/referrals/'+referral.id+'/accept','POST',{expectedVersion:referral.version})
  }
  if(!s.revokedAt) {
    verified(referral.status==='ACTIVE' && Date.parse(referral.accessExpiresAt)>Date.now(),'active shared treatment')
    for(const [key,status] of [['unrelatedDoctor',404],['receptionist',403],['orgAdmin',403],['patient',401]]) {
      await clients[key].request(history,'GET',undefined,[status]);checks++
    }
    for(const key of ['doctorA','doctorB']) {
      const doctor=clients[key],other=clients[key==='doctorA'?'doctorB':'doctorA']
      if(!s.appointments[key]) {
        const from=new Date().toISOString().slice(0,10),to=new Date(Date.now()+3*86400000).toISOString().slice(0,10)
        const available=await reception.request('/api/v1/availability/doctors/'+accountIdentity(key).id+'/slots?from='+from+'&to='+to)
        const slot=available.slots.find(v=>Date.parse(v.startsAt)>Date.now()+300000);check(slot,'fresh treatment slot')
        s.appointments[key]={booking:{bookingRequestId:randomUUID(),patientRegistrationId:s.registrationId,doctorUserId:accountIdentity(key).id,startsAt:slot.startsAt}}
        await save()
      }
      const entry=s.appointments[key]
      let appointment=entry.appointmentId?await doctor.request('/api/v1/appointments/'+entry.appointmentId)
        :await reception.request('/api/v1/appointments','POST',entry.booking,[200,201])
      entry.appointmentId=appointment.id;await save()
      if(key==='doctorA' && appointment.status==='REQUESTED') {
        if(!entry.reschedule) {
          const date=entry.booking.startsAt.slice(0,10)
          const slots=await reception.request('/api/v1/availability/doctors/'+accountIdentity(key).id+'/slots?from='+date+'&to='+date)
          const next=slots.slots.find(v=>Date.parse(v.startsAt)>Date.parse(entry.booking.startsAt));check(next,'reschedule slot')
          entry.reschedule={commandRequestId:randomUUID(),version:appointment.version,startsAt:next.startsAt,reason:'Synthetic original booking retry'}
          await save()
        }
        appointment=await doctor.request('/api/v1/appointments/'+appointment.id+'/reschedule','POST',entry.reschedule)
      }
      const replay=await reception.request('/api/v1/appointments','POST',entry.booking)
      verified(replay.id===appointment.id && replay.version===appointment.version,'original booking replays after progress')
      if(entry.reschedule) {
        await reception.request('/api/v1/appointments','POST',{...entry.booking,startsAt:entry.reschedule.startsAt},[409]);checks++
      }
      for(const [action,allowed,target,caller] of [['confirm',['REQUESTED','RESCHEDULED'],'CONFIRMED',doctor],
        ['check-in',['CONFIRMED'],'CHECKED_IN',reception],['start',['CHECKED_IN'],'IN_PROGRESS',doctor]]) {
        if(!allowed.includes(appointment.status))continue
        if(!entry[action]){entry[action]={commandRequestId:randomUUID(),version:appointment.version};await save()}
        appointment=await caller.request('/api/v1/appointments/'+appointment.id+'/'+action,'POST',entry[action])
        verified(appointment.status===target,'own appointment '+target)
      }
      let record
      if(!entry.consultationId) {
        record=await doctor.request('/api/v1/consultations','POST',{appointmentId:appointment.id},[200,201])
        entry.consultationId=record.id;await save()
      }
      const route='/api/v1/consultations/'+entry.consultationId
      record=await doctor.request(route+'/record')
      verified(record.doctorUserId===accountIdentity(key).id && record.patientRegistrationId===s.registrationId,'own treatment record')
      await other.request(route+'/record','GET',undefined,[404]);checks++
      if(record.status==='DRAFT') {
        if(!matchesClinicalDraft(record)) {
          check(emptyClinicalDraft(record),'unexpected retained treatment content')
          record=await doctor.request(route+'/draft-content','PUT',clinicalDraft(record.version))
        }
        record=await doctor.request(route+'/finalize','POST',{version:record.version})
      }
      verified(record.status==='FINALIZED' && record.finalizedByUserId===accountIdentity(key).id,'attributable treatment finalisation')
      entry.finalizedAt=record.finalizedAt;await save()
      await doctor.request(route+'/draft-content','PUT',clinicalDraft(record.version),[409]);checks++
      await until(async()=>(await doctor.request('/api/v1/appointments/'+appointment.id)).status==='COMPLETED','Kafka treatment completion');checks++
    }
    for(const doctor of [a,b]) {
      const listed=await doctor.request(history+'?size=100')
      verified([s.sourceConsultationId,...Object.values(s.appointments).map(e=>e.consultationId)]
        .every(id=>listed.content.some(c=>c.consultationId===id)),'both doctors discover all finalised treatment history')
    }
    s.positiveAt=new Date().toISOString();await save()
    checks+=await browserCare(manifest,accounts,s,save,async()=>{
      const current=await a.request('/api/v1/referrals/'+s.referralId)
      check(current.status==='ACTIVE','referral changed before revocation')
      const ended=await a.request('/api/v1/referrals/'+s.referralId+'/revoke','POST',{expectedVersion:current.version,reason:'Synthetic acceptance finished'})
      check(ended.status==='REVOKED','explicit care revocation')
      s.revokedAt=new Date().toISOString();await save()
    })
  }
  for(const key of ['doctorA','doctorB']) {
    await clients[key].request(history,'GET',undefined,[404]);checks++
    verified((await clients[key].request('/api/v1/consultations/'+s.appointments[key].consultationId+'/record')).status==='FINALIZED','author retains own signed record')
  }
  s.verifiedAt=new Date().toISOString();await save()
  return {checks,bothDoctorsTreated:true,originalBookingReplay:true,browserHistoryAndDocuments:true,revocation:true}
}
