#!/usr/bin/env node
// Retained synthetic data only. Real React/Gateway/Kafka, no intercepted API replies.
import {randomUUID} from 'node:crypto'
import {createRequire} from 'node:module'
import path from 'node:path'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {ROOT,tcpOpen} from './sahha.mjs'
import {confinedDirectory,currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {accountIdentity,validateAccounts} from './synthetic-demo.mjs'
import {withClients} from './synthetic-referrals.mjs'
import {validateWorkflowState} from './synthetic-workflow.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {BROWSER_ORIGIN,BrowserStompStream,browserApiUrl,isBlockedDesignFont} from './synthetic/browser-messages.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const expected=['main:REQUESTED','main:CONFIRMED','main:RESCHEDULED','main:CANCELLED','declined:REQUESTED','declined:REJECTED']
const check=(value,label)=>{if(!value)throw new Error('Patient notification acceptance refused: '+label)}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const stamp=value=>typeof value==='string' && Number.isFinite(Date.parse(value))
export function validatePatientNotificationState(state,generation) {
  check(state?.kind==='sahha-synthetic-patient-notifications-v1' && state.generation===generation && uuid.test(generation),'generation')
  check(Object.keys(state).every(key=>['kind','generation','organisationId','registrationId','doctorUserId','cases','deliveries','verifiedAt'].includes(key)),'unknown journal field')
  for(const key of ['organisationId','registrationId','doctorUserId'])check(uuid.test(state[key]),'scope')
  check(state.cases && !Array.isArray(state.cases) && state.deliveries && !Array.isArray(state.deliveries),'journal objects')
  for(const [name,entry] of Object.entries(state.cases)) {
    check(['main','declined'].includes(name) && entry && typeof entry==='object','case')
    check(Object.keys(entry).every(key=>['booking','appointmentId','confirm','reschedule','cancel','reject'].includes(key)),'case fields')
    const booking=entry.booking
    check(booking && Object.keys(booking).sort().join(',')==='bookingRequestId,doctorUserId,patientRegistrationId,startsAt'
      && uuid.test(booking.bookingRequestId) && booking.doctorUserId===state.doctorUserId
      && booking.patientRegistrationId===state.registrationId && stamp(booking.startsAt),'immutable booking')
    if(entry.appointmentId)check(uuid.test(entry.appointmentId),'appointment')
    for(const key of ['confirm','reschedule','cancel','reject'])if(entry[key]) {
      const command=entry[key]
      check(entry.appointmentId && uuid.test(command.commandRequestId) && Number.isInteger(command.version) && command.version>=0,'command identity')
      check(Object.keys(command).sort().join(',')===(key==='confirm'?'commandRequestId,version'
        :key==='reschedule'?'commandRequestId,reason,startsAt,version':'commandRequestId,reason,version'),'command fields')
      if(key!=='confirm')check(command.reason==='Synthetic patient notification acceptance','synthetic reason')
      if(key==='reschedule')check(stamp(command.startsAt),'reschedule time')
    }
  }
  for(const [key,value] of Object.entries(state.deliveries))check(expected.includes(key) && uuid.test(value?.id)
    && ['LIVE','RECOVERED'].includes(value.mode) && Object.keys(value).sort().join(',')==='id,mode'
    && state.cases[key.split(':')[0]]?.appointmentId,'delivery evidence')
  if(state.verifiedAt)check(stamp(state.verifiedAt) && expected.every(key=>state.deliveries[key]),'completion evidence')
  return state
}
export function verifyPatientNotification(value,appointmentId,type) {
  const fields=['id','notificationType','resourceType','resourceId','appointmentStatus','appointmentStartsAt',
    'appointmentEndsAt','appointmentTimeZone','appointmentLocationLabel','resourceVersion','eventOccurredAt','createdAt','read','readAt']
  check(value && uuid.test(value.id) && uuid.test(appointmentId) && value.resourceId===appointmentId
    && value.resourceType==='APPOINTMENT' && value.notificationType==='APPOINTMENT_'+type
    && value.appointmentStatus===type && Object.keys(value).sort().join(',')===fields.sort().join(',')
    && stamp(value.appointmentStartsAt) && stamp(value.appointmentEndsAt) && Date.parse(value.appointmentEndsAt)>Date.parse(value.appointmentStartsAt)
    && typeof value.appointmentTimeZone==='string' && typeof value.appointmentLocationLabel==='string'
    && Number.isInteger(value.resourceVersion) && value.resourceVersion>=0 && stamp(value.eventOccurredAt) && stamp(value.createdAt)
    && typeof value.read==='boolean' && (value.readAt===null || stamp(value.readAt)),'minimum private alert')
  return value.id
}
async function until(predicate,label) {
  const deadline=Date.now()+60000
  while(Date.now()<deadline){if(await predicate())return;await pause(200)}
  check(false,label)
}
async function saveJson(file,value) {
  try {await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error){if(error.code!=='ENOENT')throw error;await privateFile(file,JSON.stringify(value,null,2))}
}
async function request(context,route,method='GET',body,status=200) {
  const headers={Accept:'application/json','X-Request-ID':'synthetic-'+randomUUID()}
  if(method!=='GET')headers['X-XSRF-TOKEN']=(await request(context,'/api/v1/auth/csrf')).token
  const response=await context.request.fetch(browserApiUrl(route),{method,headers,...(body===undefined?{}:{data:body}),maxRedirects:0,timeout:20000})
  try {check(response.status()===status,'browser API status '+response.status());return status===204 || status>=400?null:await response.json()}
  finally {await response.dispose()}
}
async function run(manifest,accounts,state,save,clients) {
  const requireFrontend=createRequire(path.join(ROOT,'frontend','package.json'))
  const {createServer}=await import(pathToFileURL(requireFrontend.resolve('vite')).href)
  const {default:react}=await import(pathToFileURL(requireFrontend.resolve('@vitejs/plugin-react')).href)
  const {chromium}=requireFrontend('playwright-core')
  const envDir=await confinedDirectory(path.join(generationPath(manifest.id),'browser-environment'),true)
  check(!await tcpOpen(5173),'occupied frontend; no adoption')
  let server,browser,context,page,stage='startup',checks=0
  const observation={connected:false,subscribed:false,notifications:[],error:false}
  const existing=new Set(Object.keys(state.cases).filter(key=>state.cases[key].appointmentId))
  const verified=(value,label)=>{check(value,label);checks++}
  const base='/api/v1/notifications/patient/registrations/'+state.registrationId
  const inbox=async()=> (await request(context,base+'?size=100')).items
  const observe=async()=>{
    observation.connected=false;observation.subscribed=false
    page=await context.newPage();page.setDefaultTimeout(30000)
    const observedPage=page
    page.on('pageerror',()=>{observation.error=true})
    page.on('websocket',socket=>{
      const url=new URL(socket.url())
      if(url.pathname!=='/api/v1/notifications/patient/ws')return
      if(url.origin!=='ws://127.0.0.1:5173' || url.searchParams.get('registrationId')!==state.registrationId){observation.error=true;return}
      const incoming=new BrowserStompStream(),outgoing=new BrowserStompStream()
      socket.on('close',()=>{if(page===observedPage){observation.connected=false;observation.subscribed=false}})
      for(const [event,parser] of [['framereceived',incoming],['framesent',outgoing]])socket.on(event,frame=>{
        if(page!==observedPage)return
        try{for(const value of parser.push(frame.payload)) {
          if(value.command==='CONNECTED')observation.connected=true
          if(value.command==='SUBSCRIBE' && value.destination==='/user/queue/notifications')observation.subscribed=true
          if(value.command==='ERROR')observation.error=true
          if(value.command==='MESSAGE')observation.notifications.push(value.payload.notification)
        }}catch{observation.error=true}
      })
    })
    await page.goto(BROWSER_ORIGIN+'/patient/appointments')
    await until(()=>observation.connected && observation.subscribed,'patient live subscription')
    // The registration-loading placeholder is also named Notifications.
    // Wait for the API-backed trigger and rendered panel, not an immediate visibility snapshot.
    await page.locator('.notification-trigger').waitFor({state:'visible'})
    await page.locator('.notification-trigger').click()
    await page.getByText('Your private appointment inbox').waitFor({state:'visible'})
    verified(true,'real patient inbox')
  }
  async function delivered(name,type,offline=false) {
    let rows
    const key=name+':'+type
    await until(async()=>{rows=(await inbox()).filter(value=>value.resourceId===state.cases[name].appointmentId
      && value.notificationType==='APPOINTMENT_'+type);return rows.length>0},'durable '+key)
    verified(rows.length===1,'one durable '+key)
    const id=verifyPatientNotification(rows[0],state.cases[name].appointmentId,type);checks++
    if(!state.deliveries[key]) {
      const observed=()=>observation.notifications.some(value=>value.id===id)
      const recovered=offline || (existing.has(name) && !observed())
      if(!recovered)await until(observed,'live '+key)
      state.deliveries[key]={id,mode:recovered?'RECOVERED':'LIVE'};await save()
    } else verified(state.deliveries[key].id===id,'stable recovered notification')
    return rows[0]
  }
  async function slot(exclude) {
    const from=new Date(Date.now()+86400000).toISOString().slice(0,10)
    const to=new Date(Date.now()+3*86400000).toISOString().slice(0,10)
    const availability=await clients.receptionist.request('/api/v1/availability/doctors/'+state.doctorUserId+'/slots?from='+from+'&to='+to)
    const selected=availability.slots?.find(value=>value.startsAt!==exclude)
    verified(availability.organisationId===state.organisationId && selected,'available synthetic slot')
    return selected.startsAt
  }
  async function book(name) {
    if(!state.cases[name]) {
      state.cases[name]={booking:{bookingRequestId:randomUUID(),patientRegistrationId:state.registrationId,
        doctorUserId:state.doctorUserId,startsAt:await slot()}};await save()
    }
    const entry=state.cases[name]
    const appointment=entry.appointmentId?await clients.doctorA.request('/api/v1/appointments/'+entry.appointmentId)
      :await clients.receptionist.request('/api/v1/appointments','POST',entry.booking,[200,201])
    verified(appointment.bookingRequestId===entry.booking.bookingRequestId && appointment.organisationId===state.organisationId
      && appointment.patientRegistrationId===state.registrationId && appointment.doctorUserId===state.doctorUserId,'retained appointment scope')
    entry.appointmentId=appointment.id;await save()
    {
      const replay=await clients.receptionist.request('/api/v1/appointments','POST',entry.booking)
      verified(replay.id===appointment.id && replay.version===appointment.version,'idempotent booking')
    }
    if(appointment.status!=='REQUESTED') {
      // Recover a known, progressed appointment by ID; never rewrite its original booking.
      verified(appointment.id===entry.appointmentId,'retained appointment recovery')
      if(entry.reschedule)verified(Date.parse(appointment.startsAt)===Date.parse(entry.reschedule.startsAt),'retained rescheduled time')
    }
    await delivered(name,'REQUESTED')
    return appointment
  }
  async function transition(name,appointment,action,source,target) {
    const entry=state.cases[name]
    if(appointment.status===source) {
      if(!entry[action]) {
        entry[action]={commandRequestId:randomUUID(),version:appointment.version,
          ...(action==='confirm'?{}:{reason:'Synthetic patient notification acceptance'}),
          ...(action==='reschedule'?{startsAt:await slot(appointment.startsAt)}:{})}
        await save()
      }
      appointment=await clients.doctorA.request('/api/v1/appointments/'+appointment.id+'/'+action,'POST',entry[action])
      verified(appointment.status===target,'appointment '+target)
      const replay=await clients.doctorA.request('/api/v1/appointments/'+appointment.id+'/'+action,'POST',entry[action])
      verified(replay.id===appointment.id && replay.version===appointment.version,'idempotent '+action)
    }
    await delivered(name,target,action==='cancel')
    return appointment
  }
  try {
    server=await createServer({root:path.join(ROOT,'frontend'),configFile:false,envDir,plugins:[react()],logLevel:'silent',
      define:{'import.meta.env.VITE_USE_MOCKS':JSON.stringify('false'),'import.meta.env.VITE_USE_AUTH_MOCKS':JSON.stringify('false'),'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api/v1')},
      server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api/v1':{target:'http://127.0.0.1:8079',changeOrigin:false,ws:true}}}})
    await server.listen()
    browser=await chromium.launch({headless:true,executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'})
    context=await browser.newContext({viewport:{width:1440,height:960}})
    await context.route('**/*',async route=>{
      if(new URL(route.request().url()).origin===BROWSER_ORIGIN)await route.continue()
      else{if(!isBlockedDesignFont(route.request().url(),route.request().resourceType()))observation.error=true;await route.abort()}
    })
    const account=accounts.find(value=>value.key==='patient')
    verified((await request(context,'/api/v1/auth/login','POST',{email:account.email,password:account.password,deviceName:'Synthetic patient notifications'})).userId===account.id,'patient login')
    const registrations=await request(context,'/api/v1/patients/me/registrations')
    verified(registrations.some(value=>value.registrationId===state.registrationId && value.organisationId===state.organisationId && value.status==='ACTIVE'),'verified patient registration')
    stage='browser subscription';await observe()
    console.log('Patient browser: private Gateway notification stream ready.')
    stage='main appointment'
    let main=await book('main')
    main=await transition('main',main,'confirm','REQUESTED','CONFIRMED')
    main=await transition('main',main,'reschedule','CONFIRMED','RESCHEDULED')
    stage='offline cancellation'
    await page.close();page=null
    main=await transition('main',main,'cancel','RESCHEDULED','CANCELLED')
    verified(main.status==='CANCELLED','final cancelled status')
    await observe()
    await page.getByRole('button',{name:/Appointment cancelled/}).first().waitFor()
    stage='declined appointment'
    let declined=await book('declined')
    declined=await transition('declined',declined,'reject','REQUESTED','REJECTED')
    verified(declined.status==='REJECTED','final rejected status')
    stage='privacy and read recovery'
    for(const key of ['doctorA','doctorB','unrelatedDoctor','receptionist','orgAdmin']) {
      stage='patient inbox denial: '+key
      await clients[key].request(base,'GET',undefined,[404]);checks++
      stage='staff read denial: '+key
      await clients[key].request('/api/v1/notifications/'+state.deliveries['main:CONFIRMED'].id+'/read','POST',undefined,[404]);checks++
    }
    stage='patient staff-inbox denial'
    await request(context,'/api/v1/notifications','GET',undefined,403);checks++
    stage='foreign registration denial'
    await request(context,'/api/v1/notifications/patient/registrations/'+randomUUID(),'GET',undefined,404);checks++
    stage='foreign notification denial'
    await request(context,base+'/'+randomUUID()+'/read','POST',undefined,404);checks++
    stage='CSRF denial'
    const noCsrf=await context.request.post(browserApiUrl(base+'/read-all'))
    verified(noCsrf.status()===403,'patient CSRF required');await noCsrf.dispose()
    console.log('Patient browser: role/resource isolation and CSRF denials passed.')
    stage='UI read acknowledgement'
    await page.getByRole('button',{name:/Appointment request declined/}).first().click()
    await until(async()=> (await inbox()).find(value=>value.id===state.deliveries['declined:REJECTED'].id)?.read,'UI read acknowledgement')
    stage='read acknowledgement reload'
    await page.close();page=null;await observe()
    verified((await inbox()).find(value=>value.id===state.deliveries['declined:REJECTED'].id)?.read,'read survives reload')
    stage='mark all read'
    const count=await request(context,base+'/unread-count')
    if(count.unreadCount)await page.getByRole('button',{name:'Mark all read'}).click()
    await until(async()=> (await request(context,base+'/unread-count')).unreadCount===0,'mark all read recovery')
    stage='viewport and protocol checks'
    for(const width of [1440,375]) {
      await page.setViewportSize({width,height:960})
      const size=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}))
      verified(size.scroll<=size.width+1,'patient viewport fit')
    }
    verified(!observation.error,'browser network/protocol safety')
    const live=Object.values(state.deliveries).filter(value=>value.mode==='LIVE').length
    verified(live>=3 && state.deliveries['main:CANCELLED'].mode==='RECOVERED','live plus offline REST evidence')
    state.verifiedAt=new Date().toISOString();await save()
    return {checks,liveDeliveries:live,recoveredDeliveries:expected.length-live,lifecycleTypes:5,readReloadRecovery:true,mockedResponses:false}
  } catch(error) {
    const diagnostic=Number.isInteger(error.status)?'; httpStatus='+error.status:''
    const category=error.name==='TimeoutError'?'timeout'
      :error.message==='Invalid synthetic Gateway JSON; response body suppressed'?'invalid-json':'other'
    throw new Error((error.message?.startsWith('Patient notification acceptance refused:')?error.message:'Patient notification acceptance failed; private diagnostics suppressed')+'; stage='+stage+diagnostic+'; category='+category)
  } finally {
    let failed=false
    if(context) {
      try{if((await context.cookies()).some(value=>value.name==='SAHHA_DEMO_ACCESS'))await request(context,'/api/v1/auth/logout','POST',undefined,204)}catch{failed=true}
      finally{await context.close().catch(()=>{failed=true})}
    }
    if(browser)await browser.close().catch(()=>{failed=true})
    if(server)await server.close().catch(()=>{failed=true})
    if(failed)throw new Error('Patient browser cleanup failed; outer helper cleanup still runs')
    console.log('Patient browser, Chromium and owned frontend stopped.')
  }
}
export {run as runPatientBrowserGate}
export async function main(args) {
  if(args.length!==1 || args[0]!=='verify')throw new Error('Use: node scripts/synthetic-patient-notifications.mjs verify')
  await lock(async()=>{
    const manifest=await currentManifest(),directory=generationPath(manifest.id)
    const read=async name=>parsePrivateJson(await readPrivate(path.join(directory,name)))
    const accounts=validateAccounts(await read('demo-accounts.json'),manifest.id),seed=await read('demo-seed-report.json')
    const scheduling=validateWorkflowState(await read('workflow-scheduling.json'),manifest.id)
    check(seed.generation===manifest.id && scheduling.organisationId===seed.organisations.A && scheduling.booking,'retained provenance')
    let state
    try{state=await read('workflow-patient-notifications.json')}catch(error){
      if(error.code!=='ENOENT')throw error
      state={kind:'sahha-synthetic-patient-notifications-v1',generation:manifest.id,organisationId:seed.organisations.A,
        registrationId:scheduling.booking.patientRegistrationId,doctorUserId:accountIdentity('doctorA').id,cases:{},deliveries:{}}
    }
    validatePatientNotificationState(state,manifest.id)
    check(state.organisationId===seed.organisations.A && state.registrationId===scheduling.booking.patientRegistrationId
      && state.doctorUserId===accountIdentity('doctorA').id,'changed retained scope')
    const save=()=>{validatePatientNotificationState(state,manifest.id);return saveJson(path.join(directory,'workflow-patient-notifications.json'),state)}
    await save()
    const report=await withSyntheticPlatform(manifest,[...batchNames('foundation'),'notification-service'],
      ()=>withClients(accounts.accounts,seed,clients=>run(manifest,accounts.accounts,state,save,clients)),{eventDelivery:true})
    await saveJson(path.join(directory,'workflow-patient-notifications-report.json'),{generation:manifest.id,verifiedAt:state.verifiedAt,...report})
    console.log('Patient notification live gate: '+report.checks+' assertions, '+report.liveDeliveries+' live deliveries, '+report.recoveredDeliveries+' recovered; helpers stopped.')
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
