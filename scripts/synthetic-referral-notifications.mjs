#!/usr/bin/env node
// Real React -> Gateway -> Kafka -> private Notification inbox/WebSocket; synthetic data only.
import {randomUUID} from 'node:crypto'
import {createRequire} from 'node:module'
import path from 'node:path'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {ROOT,tcpOpen} from './sahha.mjs'
import {confinedDirectory,currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {accountIdentity,validateAccounts} from './synthetic-demo.mjs'
import {withClients} from './synthetic-referrals.mjs'
import {careCommand,validateCareState,verifyCareReferral} from './synthetic-shared-care.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {BROWSER_ORIGIN,BrowserStompStream,browserApiUrl,isBlockedDesignFont} from './synthetic/browser-messages.mjs'

const names=['main','independent','supporting','expiry'], keys=['doctorA','doctorB','unrelatedDoctor']
const expected=['main:RECEIVED:doctorB','main:ACCEPTED:doctorA','main:COMPLETED:doctorA',
  'independent:RECEIVED:doctorB','independent:REJECTED:doctorA','supporting:RECEIVED:doctorB',
  'supporting:REVOKED:doctorB','expiry:RECEIVED:doctorB','expiry:EXPIRED:doctorA','expiry:EXPIRED:doctorB']
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const check=(value,label)=>{if(!value)throw new Error('Referral notification acceptance refused: '+label)}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms))
export function validateNotificationState(state,generation) {
  check(state?.kind==='sahha-synthetic-referral-notifications-v1' && state.generation===generation,'journal generation')
  check(Object.keys(state).every(key=>['kind','generation','care','deliveries','recoveredDeliveries','verifiedAt'].includes(key)),'unknown journal field')
  validateCareState(state.care,generation)
  check(state.deliveries && !Array.isArray(state.deliveries) && typeof state.deliveries==='object','delivery journal')
  for(const [key,value] of Object.entries(state.deliveries)) check(expected.includes(key) && uuid.test(value)
    && state.care.cases[key.split(':')[0]]?.referralId,'delivery evidence')
  if(state.recoveredDeliveries!==undefined)check(Array.isArray(state.recoveredDeliveries)
    && new Set(state.recoveredDeliveries).size===state.recoveredDeliveries.length
    && state.recoveredDeliveries.every(key=>expected.includes(key) && state.deliveries[key]),'recovered evidence')
  if(state.verifiedAt!==undefined)check(Number.isFinite(Date.parse(state.verifiedAt)) && expected.every(key=>state.deliveries[key]),'complete evidence')
  return state
}
export function verifyReferralNotification(value,referralId,type) {
  const fields=['id','notificationType','resourceType','resourceId','appointmentStatus','appointmentStartsAt',
    'appointmentEndsAt','appointmentTimeZone','appointmentLocationLabel','resourceVersion','eventOccurredAt','createdAt','read','readAt']
  check(value && uuid.test(value.id) && value.resourceType==='REFERRAL' && value.resourceId===referralId
    && ['RECEIVED','ACCEPTED','REJECTED','REVOKED','COMPLETED','EXPIRED'].includes(type) && uuid.test(referralId)
    && value.notificationType==='REFERRAL_'+type && Object.keys(value).every(key=>fields.includes(key))
    && ['appointmentStatus','appointmentStartsAt','appointmentEndsAt','appointmentTimeZone','appointmentLocationLabel'].every(key=>value[key]===null)
    && Number.isInteger(value.resourceVersion) && value.resourceVersion>=0 && typeof value.read==='boolean'
    && Number.isFinite(Date.parse(value.eventOccurredAt)) && Number.isFinite(Date.parse(value.createdAt))
    && (value.readAt===null || Number.isFinite(Date.parse(value.readAt))),'private notification contract')
  return value.id
}
async function until(predicate,label,timeout=60000) {
  const end=Date.now()+timeout
  while(Date.now()<end){if(await predicate())return;await pause(200)}
  check(false,label)
}
async function saveJson(file,value) {
  try {await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error){if(error.code!=='ENOENT')throw error;await privateFile(file,JSON.stringify(value,null,2))}
}
async function request(context,route,method='GET',body,expectedStatus=200) {
  const headers={Accept:'application/json','X-Request-ID':'synthetic-'+randomUUID()}
  if(method!=='GET')headers['X-XSRF-TOKEN']=(await request(context,'/api/v1/auth/csrf')).token
  const response=await context.request.fetch(browserApiUrl(route),{method,headers,...(body===undefined?{}:{data:body}),maxRedirects:0,timeout:20000})
  try {
    check(response.status()===expectedStatus,'browser API '+method+' returned '+response.status()+'; body suppressed')
    return expectedStatus===204 || expectedStatus>=400?null:await response.json()
  } finally {await response.dispose()}
}
async function run(manifest,accounts,seed,state,save,clients) {
  const requireFrontend=createRequire(path.join(ROOT,'frontend','package.json'))
  const {createServer}=await import(pathToFileURL(requireFrontend.resolve('vite')).href)
  const {default:react}=await import(pathToFileURL(requireFrontend.resolve('@vitejs/plugin-react')).href)
  const {chromium}=requireFrontend('playwright-core')
  const envDir=await confinedDirectory(path.join(generationPath(manifest.id),'browser-environment'),true)
  check(!await tcpOpen(5173),'occupied frontend; no adoption')
  let server,browser,stage='startup',checks=0
  const contexts={},pages={},observations={}
  const recoverableReferrals=new Set(Object.values(state.care.cases).map(value=>value.referralId).filter(Boolean))
  state.recoveredDeliveries??=[]
  const verified=(value,label)=>{check(value,label);checks++}
  const a=clients.doctorA,b=clients.doctorB
  try {
    server=await createServer({root:path.join(ROOT,'frontend'),configFile:false,envDir,plugins:[react()],logLevel:'silent',
      define:{'import.meta.env.VITE_USE_MOCKS':JSON.stringify('false'),'import.meta.env.VITE_USE_AUTH_MOCKS':JSON.stringify('false'),'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api/v1')},
      server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api/v1':{target:'http://127.0.0.1:8079',changeOrigin:false,ws:true}}}})
    await server.listen()
    browser=await chromium.launch({headless:true,executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'})
    for(const [index,key] of keys.entries()) {
      stage=key+' browser authentication'
      const context=await browser.newContext({viewport:{width:[1440,375,1024][index],height:960}});contexts[key]=context
      const observation={connected:false,subscribed:false,notifications:[],error:false};observations[key]=observation
      await context.route('**/*',async route=>{
        if(new URL(route.request().url()).origin===BROWSER_ORIGIN)await route.continue()
        else{if(!isBlockedDesignFont(route.request().url(),route.request().resourceType()))observation.error=true;await route.abort()}
      })
      const account=accounts.find(value=>value.key===key)
      verified((await request(context,'/api/v1/auth/login','POST',{email:account.email,password:account.password,deviceName:'Synthetic referral browser'})).userId===account.id,'browser identity')
      verified((await request(context,'/api/v1/auth/active-organisation','POST',{organisationId:state.care.organisationId})).activeOrganisationId===state.care.organisationId,'browser organisation')
      const page=await context.newPage();pages[key]=page;page.setDefaultTimeout(30000)
      page.on('pageerror',()=>{observation.error=true})
      page.on('websocket',socket=>{
        const url=new URL(socket.url());if(url.pathname!=='/api/v1/notifications/ws')return
        if(url.origin!=='ws://127.0.0.1:5173'){observation.error=true;return}
        const incoming=new BrowserStompStream(),outgoing=new BrowserStompStream()
        socket.on('close',()=>{observation.connected=false;observation.subscribed=false})
        for(const [event,parser] of [['framereceived',incoming],['framesent',outgoing]])socket.on(event,frame=>{
          try {for(const value of parser.push(frame.payload)) {
            if(value.command==='CONNECTED')observation.connected=true
            if(value.command==='SUBSCRIBE' && value.destination==='/user/queue/notifications')observation.subscribed=true
            if(value.command==='ERROR')observation.error=true
            if(value.command==='MESSAGE' && value.payload?.notification?.resourceType==='REFERRAL')observation.notifications.push(value.payload.notification)
          }}catch{observation.error=true}
        })
      })
      await page.goto(BROWSER_ORIGIN+'/doctor/referrals')
      await until(()=>observation.connected && observation.subscribed,key+' private subscription')
      await page.getByRole('button',{name:/^Notifications/}).click()
      console.log('Referral browser: '+key+' private notification stream ready.')
    }
    const inbox=async key=>(await request(contexts[key],'/api/v1/notifications?size=100')).items
    async function delivered(name,type,key) {
      const entry=state.care.cases[name],evidenceKey=name+':'+type+':'+key
      let rows
      await until(async()=>{rows=(await inbox(key)).filter(value=>value.resourceId===entry.referralId && value.notificationType==='REFERRAL_'+type);return rows.length>0},'durable '+evidenceKey)
      verified(rows.length===1,'one durable alert '+evidenceKey)
      const id=verifyReferralNotification(rows[0],entry.referralId,type);checks++
      if(!state.deliveries[evidenceKey]) {
        const observed=()=>observations[key].notifications.some(value=>value.id===id)
        if(recoverableReferrals.has(entry.referralId) && !observed()) {
          // Explicitly classify interrupted-run REST recovery, never invent a live frame.
          state.recoveredDeliveries.push(evidenceKey)
        } else await until(observed,'real WebSocket '+evidenceKey)
        state.deliveries[evidenceKey]=id;await save();checks++
      } else verified(state.deliveries[evidenceKey]===id,'stable recovery identity')
      for(const other of keys.filter(value=>value!==key)) {
        verified(!(await inbox(other)).some(value=>value.id===id),'foreign inbox denial')
        verified(!observations[other].notifications.some(value=>value.id===id),'foreign WebSocket denial')
      }
    }
    for(const name of names) {
      stage=name+' lifecycle'
      if(!state.care.cases[name]) {
        state.care.cases[name]={requestId:randomUUID(),consentAt:new Date(Date.now()-1000).toISOString(),
          expiresAt:new Date(Date.now()+(name==='expiry'?90000:86400000)).toISOString()};await save()
      }
      const entry=state.care.cases[name]
      let referral=entry.referralId?await a.request('/api/v1/referrals/'+entry.referralId)
        :await a.request('/api/v1/referrals','POST',careCommand(state.care,name),[201])
      verifyCareReferral(referral,state.care,name);checks++
      verified(referral.organisationId===state.care.organisationId && referral.referralType==='SHARED_TREATMENT'
        && referral.senderUserId===accountIdentity('doctorA').id && referral.recipientUserId===accountIdentity('doctorB').id,'referral identity/scope')
      entry.referralId=referral.id;await save()
      await delivered(name,'RECEIVED','doctorB');entry.positiveAt??=new Date().toISOString();await save()
      const route='/api/v1/referrals/'+referral.id
      if(name==='main') {
        if(referral.status==='SENT')referral=await b.request(route+'/accept','POST',{expectedVersion:referral.version})
        await delivered(name,'ACCEPTED','doctorA')
        if(referral.status==='ACTIVE')referral=await b.request(route+'/complete','POST',{expectedVersion:referral.version,reason:'Synthetic notification completion'})
        verified(referral.status==='COMPLETED','completed lifecycle');await delivered(name,'COMPLETED','doctorA')
      } else if(name==='expiry') {
        await until(async()=>{referral=await a.request(route);return referral.status==='EXPIRED'},'scheduled referral expiry',165000)
        await delivered(name,'EXPIRED','doctorA');await delivered(name,'EXPIRED','doctorB')
      } else {
        const reject=name==='independent',type=reject?'REJECTED':'REVOKED'
        if(referral.status==='SENT')referral=await (reject?b:a).request(route+(reject?'/reject':'/revoke'),'POST',
          {expectedVersion:referral.version,reason:'Synthetic notification lifecycle verification'})
        verified(referral.status===type,'terminal lifecycle');await delivered(name,type,reject?'doctorA':'doctorB')
      }
      entry.finishedAt=new Date().toISOString();await save()
      console.log('Referral notifications: '+name+' lifecycle and private delivery passed.')
    }
    stage='read and reload recovery'
    const received=state.deliveries['main:RECEIVED:doctorB']
    await clients.unrelatedDoctor.request('/api/v1/notifications/'+received+'/read','POST',undefined,[404]);checks++
    await clients.receptionist.request('/api/v1/notifications/'+received+'/read','POST',undefined,[404]);checks++
    await clients.orgAdmin.request('/api/v1/notifications/'+received+'/read','POST',undefined,[404]);checks++
    await request(contexts.doctorB,'/api/v1/notifications/'+received+'/read','POST');checks++
    await pages.doctorB.reload()
    await until(()=>observations.doctorB.connected && observations.doctorB.subscribed,'reconnected notification stream')
    verified((await inbox('doctorB')).some(value=>value.id===received && value.read),'read state survives reload')
    await pages.doctorB.getByRole('button',{name:/^Notifications/}).click()
    await pages.doctorB.getByText('Referral expired',{exact:true}).first().waitFor();checks++
    await pages.doctorA.getByText('Referral completed',{exact:true}).first().waitFor();checks++
    await b.select(seed.organisations.B)
    verified(!(await b.request('/api/v1/notifications?size=100')).items.some(value=>Object.values(state.deliveries).includes(value.id)),'wrong organisation inbox')
    await b.request('/api/v1/notifications/'+received+'/read','POST',undefined,[404]);checks++
    for(const key of keys) {
      const size=await pages[key].evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}))
      verified(size.scroll<=size.width+1,'viewport fit');verified(!observations[key].error,'browser protocol/runtime safety')
    }
    const live=expected.filter(key=>!state.recoveredDeliveries.includes(key))
    verified(new Set(live.map(key=>key.split(':')[1])).size===6,'real WebSocket evidence for all six lifecycle types')
    state.verifiedAt=new Date().toISOString();await save()
    return {checks,liveNotificationEvidence:live.length,recoveredDeliveries:state.recoveredDeliveries.length,
      lifecycleTypes:6,mockedResponses:false,readReloadRecovery:true}
  } catch(error) {
    throw new Error((error.message?.startsWith('Referral notification acceptance refused:')?error.message:'Referral notification acceptance failed; private diagnostics suppressed')+'; stage='+stage)
  } finally {
    let failed=false
    for(const context of Object.values(contexts)) {
      try{if((await context.cookies()).some(value=>value.name==='SAHHA_DEMO_ACCESS'))await request(context,'/api/v1/auth/logout','POST',undefined,204)}catch{failed=true}
      finally{await context.close().catch(()=>{failed=true})}
    }
    if(browser)await browser.close().catch(()=>{failed=true})
    if(server)await server.close().catch(()=>{failed=true})
    if(failed)throw new Error('Referral browser cleanup failed; outer service/helper cleanup still runs')
    console.log('Referral browser sessions, Chromium and owned frontend stopped.')
  }
}
export {run as runReferralBrowserGate}
export async function main(args) {
  if(args.length!==1 || args[0]!=='verify')throw new Error('Use: node scripts/synthetic-referral-notifications.mjs verify')
  await lock(async()=>{
    const manifest=await currentManifest(),directory=generationPath(manifest.id)
    const read=async name=>parsePrivateJson(await readPrivate(path.join(directory,name)))
    const accounts=validateAccounts(await read('demo-accounts.json'),manifest.id),seed=await read('demo-seed-report.json')
    const shared=validateCareState(await read('workflow-shared-care.json'),manifest.id)
    check(seed.generation===manifest.id && seed.organisations.A===shared.organisationId && seed.organisations.B===shared.otherOrganisationId,'retained provenance')
    let state
    try{state=await read('workflow-referral-notifications.json')}catch(error){
      if(error.code!=='ENOENT')throw error
      state={kind:'sahha-synthetic-referral-notifications-v1',generation:manifest.id,care:{...shared,cases:{}},deliveries:{}}
      delete state.care.verifiedAt
    }
    validateNotificationState(state,manifest.id)
    for(const key of ['organisationId','otherOrganisationId','patientId','consultationId','fileId','secondFileId'])check(state.care[key]===shared[key],'changed retained scope')
    const save=()=>{validateNotificationState(state,manifest.id);return saveJson(path.join(directory,'workflow-referral-notifications.json'),state)}
    await save()
    const report=await withSyntheticPlatform(manifest,[...batchNames('collaboration'),'clinical-service'],
      ()=>withClients(accounts.accounts,seed,clients=>run(manifest,accounts.accounts,seed,state,save,clients)),{eventDelivery:true})
    await saveJson(path.join(directory,'workflow-referral-notifications-report.json'),{generation:manifest.id,verifiedAt:state.verifiedAt,...report})
    console.log('Referral notification live gate: '+report.checks+' assertions, six lifecycle types; '+report.liveNotificationEvidence+' live and '+report.recoveredDeliveries+' recovered private deliveries; helpers stopped.')
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
