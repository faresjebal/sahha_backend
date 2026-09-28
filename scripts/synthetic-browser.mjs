#!/usr/bin/env node
// Real Chromium -> frontend proxy -> Gateway -> Communication/Kafka/Notification.
// No mocked HTTP, synthetic WebSocket frames, direct domain SQL or data resets.
import {randomUUID} from 'node:crypto'
import {createRequire} from 'node:module'
import path from 'node:path'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {ROOT,tcpOpen} from './sahha.mjs'
import {confinedDirectory,currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {accountIdentity,pageItems,validateAccounts,waitOrganisationRoute} from './synthetic-demo.mjs'
import {GatewayClient} from './synthetic/gateway-client.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {conversationItems} from './synthetic/message-seed.mjs'
import {BROWSER_ORIGIN,BROWSER_SUBJECT,BROWSER_MESSAGE,BrowserStompStream,browserApiUrl,browserCheck as check,
  installMessageSocketControl,isBlockedDesignFont,messageCommand,reconcileEmptyMessage,validateBrowserSeed,validateBrowserState,verifyBrowserNotification} from './synthetic/browser-messages.mjs'

const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const KEYS=['doctorA','doctorB','unrelatedDoctor']
async function until(predicate,label,timeout=30000) {
  const deadline=Date.now()+timeout
  while(Date.now()<deadline) {if(await predicate()) return;await pause(200)}
  check(false,label)
}
async function saveJson(file,value) {
  try{await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error){if(error.code!=='ENOENT')throw error;await privateFile(file,JSON.stringify(value,null,2))}
}
async function browserRequest(context,route,method='GET',body,expected=200) {
  const headers={Accept:'application/json','X-Request-ID':`synthetic-${randomUUID()}`}
  if(method!=='GET') headers['X-XSRF-TOKEN']=(await browserRequest(context,'/api/v1/auth/csrf')).token
  const response=await context.request.fetch(browserApiUrl(route),{method,headers,...(body===undefined?{}:{data:body}),maxRedirects:0,timeout:20000})
  try {
    check(response.status()===expected,`browser API ${method} returned ${response.status()}, expected ${expected}; body suppressed`)
    return expected===204 || expected>=400?null:await response.json()
  } finally {await response.dispose()}
}
function watchPage(page,key,state,save) {
  const observation={connections:new Map(),historyReads:0,foreignMessage:false,foreignNotification:false,error:false,serverErrors:0,networkDenied:false,pageErrors:0}
  page.on('pageerror',()=>observation.pageErrors++)
  page.on('response',response=>{
    const url=new URL(response.url())
    if(url.pathname===`/api/v1/conversations/${state.conversationId}/messages` && response.request().method()==='GET' && response.status()===200) observation.historyReads++
  })
  page.on('websocket',socket=>{
    const url=new URL(socket.url()),channel=url.pathname=== '/api/v1/conversations/ws'?'messages':url.pathname==='/api/v1/notifications/ws'?'notifications':null
    if(!channel) return
    if(url.origin!=='ws://127.0.0.1:5173'){observation.networkDenied=true;return}
    const connection={connected:false,subscribed:false,closed:false};observation.connections.set(channel,connection)
    socket.on('close',()=>{connection.closed=true})
    const incoming=new BrowserStompStream(),outgoing=new BrowserStompStream()
    for(const [event,parser] of [['framereceived',incoming],['framesent',outgoing]]) socket.on(event,frame=>{
      try {
        for(const value of parser.push(frame.payload)) {
          if(event==='framesent') {
            if(value.command==='SUBSCRIBE' && value.destination===`/user/queue/${channel}`)connection.subscribed=true
            continue
          }
          if(value.command==='CONNECTED')connection.connected=true
          if(value.command==='ERROR'){observation.serverErrors++;connection.rejected=true}
          if(value.command!=='MESSAGE')continue
          const payload=value.payload
          if(channel==='messages' && payload?.conversationId===state.conversationId) {
            check(payload.messageType==='MESSAGE_CREATED' && payload.body===BROWSER_MESSAGE && payload.senderUserId===accountIdentity('doctorA').id,'live message content/owner')
            if(key==='unrelatedDoctor')observation.foreignMessage=true
            if(key==='doctorB') {check(!state.liveMessageId || state.liveMessageId===payload.messageId,'changed live message');state.liveMessageId=payload.messageId;void save().catch(()=>{observation.error=true})}
          }
          if(channel==='notifications' && payload?.notification?.resourceId===state.conversationId) {
            check(payload.messageType==='NOTIFICATION_CREATED','notification envelope')
            if(key!=='doctorB')observation.foreignNotification=true
            else {state.liveNotificationId=verifyBrowserNotification(payload.notification,state);void save().catch(()=>{observation.error=true})}
          }
        }
      } catch {observation.error=true}
    })
  })
  return observation
}
const ready=observation=>['messages','notifications'].every(key=>{
  const connection=observation.connections.get(key);return connection?.connected && connection.subscribed && !connection.closed && !connection.rejected
})
async function selectOrganisation(page,context,seed,label) {
  await page.goto(`${BROWSER_ORIGIN}/organisations/select`)
  await page.getByRole('button',{name:new RegExp(`Sahha Synthetic Clinic ${label}`)}).click()
  await page.waitForURL(url=>url.pathname!=='/organisations/select' && !url.pathname.startsWith('/login'))
  check((await browserRequest(context,'/api/v1/auth/session')).activeOrganisationId===seed.organisations[label],'browser active organisation')
}
async function openMessages(page) {
  await page.goto(`${BROWSER_ORIGIN}/doctor/messages`)
  await page.getByRole('heading',{name:'Private clinical conversations.',exact:true}).waitFor()
}
async function selectConversation(page) {
  await page.locator('.connected-messenger > aside button').filter({hasText:BROWSER_SUBJECT}).click()
  await page.getByRole('heading',{name:BROWSER_SUBJECT,exact:true}).waitFor()
}
async function runBrowser(manifest,accounts,seed,state,save,mode,reconcileEmpty) {
  const requireFrontend=createRequire(path.join(ROOT,'frontend','package.json'))
  const {createServer}=await import(pathToFileURL(requireFrontend.resolve('vite')).href)
  const {default:react}=await import(pathToFileURL(requireFrontend.resolve('@vitejs/plugin-react')).href)
  const {chromium}=requireFrontend('playwright-core')
  check(typeof chromium?.launch==='function','installed browser driver unavailable')
  const envDir=await confinedDirectory(path.join(generationPath(manifest.id),'browser-environment'),true)
  check(!await tcpOpen(5173),'frontend port already occupied; no adoption')
  let server,browser,stage='frontend startup',checks=0
  const contexts={},pages={},observations={},sender=new GatewayClient()
  const verified=(condition,label)=>{check(condition,label);checks++}
  try {
    server=await createServer({root:path.join(ROOT,'frontend'),configFile:false,envDir,plugins:[react()],logLevel:'silent',
      define:{'import.meta.env.VITE_USE_MOCKS':'"false"','import.meta.env.VITE_USE_AUTH_MOCKS':'"false"','import.meta.env.VITE_API_BASE_URL':'"/api/v1"'},
      server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api/v1':{target:'http://127.0.0.1:8079',changeOrigin:false,ws:true}}}})
    await server.listen()
    stage='conversation recovery'
    const byKey=Object.fromEntries(accounts.map(value=>[value.key,value]))
    verified((await sender.login(byKey.doctorA)).userId===accountIdentity('doctorA').id,'seed login')
    await waitOrganisationRoute(sender);verified((await sender.select(seed.organisations.A)).activeOrganisationId===seed.organisations.A,'seed organisation')
    let existing
    await until(async()=>{try{existing=conversationItems(await sender.request('/api/v1/conversations?size=100')).filter(value=>value.subject===BROWSER_SUBJECT);return true}
      catch(error){if(error.status!==503)throw error;return false}},'Communication route readiness',60000)
    check(existing.length<=1,'ambiguous existing browser conversation')
    if(!state.conversationRequestId){check(!existing.length && mode==='live','missing conversation command');state.conversationRequestId=randomUUID();await save()}
    const conversation=await sender.request('/api/v1/conversations','POST',{conversationRequestId:state.conversationRequestId,
      recipientUserId:accountIdentity('doctorB').id,subject:BROWSER_SUBJECT,patientRegistrationId:null},[201])
    verified(conversation.organisationId===seed.organisations.A && conversation.createdByUserId===accountIdentity('doctorA').id
      && conversation.subject===BROWSER_SUBJECT && conversation.patientRegistrationId===null && conversation.patientAccessGranted===false
      && conversation.participants?.length===2 && ['doctorA','doctorB'].every(key=>conversation.participants.some(value=>value.userId===accountIdentity(key).id))
      && (!state.conversationId || state.conversationId===conversation.id) && (!existing.length || existing[0].id===conversation.id),'retained participant-only conversation')
    state.conversationId=conversation.id;await save()
    const history=conversationItems(await sender.request(`/api/v1/conversations/${state.conversationId}/messages?size=100`))
    check(history.length<=1 && (!state.messageId || history[0]?.id===state.messageId),'retained single-message history')
    if(history.length){check(state.messageRequestId && history[0].body===BROWSER_MESSAGE && history[0].senderUserId===accountIdentity('doctorA').id,'retained message provenance');state.messageId=history[0].id;await save()}
    if(reconcileEmpty) {
      reconcileEmptyMessage(state,history);await save()
      console.log('Synthetic browser: authorised fresh-process history is empty; uncommitted command ID retained in reconciliation history.')
    }
    if(mode==='recover')check(state.liveVerifiedAt && state.messageId,'recover requires completed live acceptance')
    stage='real browser login'
    browser=await chromium.launch({headless:true,executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'})
    for(const [index,key] of KEYS.entries()) {
      stage=`${key} browser login`
      const context=await browser.newContext({viewport:{width:[1440,375,1024][index],height:960}});contexts[key]=context
      await context.addInitScript(installMessageSocketControl)
      await context.route('**/*',async route=>{
        if(new URL(route.request().url()).origin===BROWSER_ORIGIN)await route.continue()
        else {
          if(observations[key] && !isBlockedDesignFont(route.request().url(),route.request().resourceType()))observations[key].networkDenied=true
          await route.abort()
        }
      })
      const page=await context.newPage();pages[key]=page;page.setDefaultTimeout(30000)
      observations[key]=watchPage(page,key,state,save)
      await page.goto(`${BROWSER_ORIGIN}/login`)
      verified(await page.getByText('Demo identity',{exact:true}).count()===0,'real Auth mode')
      await page.getByLabel('Email',{exact:true}).fill(byKey[key].email)
      await page.getByLabel('Password',{exact:true}).fill(byKey[key].password)
      await page.getByRole('button',{name:/Enter workspace/i}).click()
      await page.waitForURL(url=>!url.pathname.startsWith('/login'))
      verified((await browserRequest(context,'/api/v1/auth/session')).userId===accountIdentity(key).id,'browser login identity')
      stage=`${key} organisation selection`
      await selectOrganisation(page,context,seed,'A');checks++
      await openMessages(page)
      stage=`${key} private stream connection`
      await until(()=>ready(observations[key]),'authenticated browser WebSocket subscriptions',60000);checks++
      console.log(`Synthetic browser: ${key} logged in; both private streams subscribed.`)
    }
    const a=pages.doctorA,b=pages.doctorB,recipient=contexts.doctorB
    await selectConversation(a);await selectConversation(b)
    const historyBefore=observations.doctorB.historyReads
    if(!history.length) {
      stage='browser send and live delivery'
      await a.route(browserApiUrl(`/api/v1/conversations/${state.conversationId}/messages`),async route=>{
        if(route.request().method()!=='POST'){await route.continue();return}
        try {state.messageRequestId=messageCommand(route.request().postDataJSON(),state);await save();await route.continue()}
        catch {observations.doctorA.error=true;await route.abort()}
      })
      let csrfRejections=0
      const sent=a.waitForResponse(response=>{
        if(new URL(response.url()).pathname!==`/api/v1/conversations/${state.conversationId}/messages` || response.request().method()!=='POST')return false
        // The real HTTP client retries one 403 after refreshing its CSRF token.
        return response.status()!==403 || ++csrfRejections>1
      })
      void sent.catch(()=>{})
      await a.getByLabel('Message',{exact:true}).fill(BROWSER_MESSAGE)
      await a.getByRole('button',{name:'Send message',exact:true}).click()
      const response=await sent;verified(response.status()===201,`browser message returned ${response.status()}, expected 201`)
      const message=await response.json();check(message.body===BROWSER_MESSAGE && message.senderUserId===accountIdentity('doctorA').id,'browser send response')
      state.messageId=message.id;await save()
      await until(()=>!!state.liveMessageId && !!state.liveNotificationId,'actual message and Kafka-notification WebSocket frames',60000)
      verified(state.liveMessageId===state.messageId,'exact recipient WebSocket message')
      await until(()=>observations.doctorB.historyReads>historyBefore,'WebSocket-triggered REST history recovery');checks++
    }
    stage='persisted delivery and browser recovery'
    check(state.liveMessageId===state.messageId && state.liveNotificationId,'no recorded live delivery; retained message is never recreated')
    await b.getByText(BROWSER_MESSAGE,{exact:true}).waitFor();checks++
    const messages=conversationItems(await browserRequest(recipient,`/api/v1/conversations/${state.conversationId}/messages?size=100`))
    verified(messages.length===1 && messages[0].id===state.messageId && messages[0].body===BROWSER_MESSAGE,'exact persisted browser message')
    let inbox
    await until(async()=>{inbox=pageItems(await browserRequest(recipient,'/api/v1/notifications?size=100'));return inbox.some(value=>value.id===state.liveNotificationId)},'Kafka inbox persistence')
    verified(inbox.filter(value=>value.resourceId===state.conversationId).length===1,'single durable notification')
    verifyBrowserNotification(inbox.find(value=>value.id===state.liveNotificationId),state);checks++
    await b.getByRole('button',{name:/^Notifications/}).first().click()
    const panel=b.getByRole('dialog',{name:'Notifications',exact:true})
    await panel.getByText('Live updates connected',{exact:true}).waitFor();checks++
    verified(!(await panel.innerText()).includes(BROWSER_MESSAGE),'notification UI has no message body')
    await b.getByRole('button',{name:'Close notifications',exact:true}).click()
    stage='socket-only reconnect recovery'
    const priorStream=observations.doctorB.connections.get('messages'),priorReads=observations.doctorB.historyReads
    verified(await b.evaluate(()=>window.__sahhaCloseMessageStreams())===1,'close the real message socket only')
    await until(()=>priorStream.closed && observations.doctorB.connections.get('messages')!==priorStream
      && ready(observations.doctorB) && observations.doctorB.historyReads>priorReads,'socket reconnect refreshes authoritative history',60000);checks++
    await b.getByText(BROWSER_MESSAGE,{exact:true}).waitFor();checks++
    stage='full-page REST recovery'
    const reads=observations.doctorB.historyReads
    await b.reload();await selectConversation(b);await b.getByText(BROWSER_MESSAGE,{exact:true}).waitFor()
    await until(()=>ready(observations.doctorB) && observations.doctorB.historyReads>reads,'browser reload/reconnect recovery');checks++
    const recovered=pageItems(await browserRequest(recipient,'/api/v1/notifications?size=100')).find(value=>value.id===state.liveNotificationId)
    verifyBrowserNotification(recovered,state);checks++
    stage='recipient and organisation isolation'
    for(const route of [`/api/v1/conversations/${state.conversationId}`,`/api/v1/conversations/${state.conversationId}/messages`]) {
      await browserRequest(contexts.unrelatedDoctor,route,'GET',undefined,404);checks++
    }
    for(const key of ['doctorA','unrelatedDoctor']) {
      const privateInbox=pageItems(await browserRequest(contexts[key],'/api/v1/notifications?size=100'))
      verified(!privateInbox.some(value=>value.resourceId===state.conversationId),'recipient-only notification inbox')
      await browserRequest(contexts[key],`/api/v1/notifications/${state.liveNotificationId}/read`,'POST',undefined,404);checks++
    }
    verified(!observations.unrelatedDoctor.foreignMessage && !observations.unrelatedDoctor.foreignNotification
      && !observations.doctorA.foreignNotification,'private WebSocket routing')
    const oldConnections=[...observations.doctorB.connections.values()]
    await selectOrganisation(b,recipient,seed,'B');checks++
    await openMessages(b);await until(()=>oldConnections.every(value=>value.closed),'old organisation sockets closed');checks++
    await browserRequest(recipient,`/api/v1/conversations/${state.conversationId}`,'GET',undefined,404);checks++
    verified(!conversationItems(await browserRequest(recipient,'/api/v1/conversations?size=100')).some(value=>value.id===state.conversationId),'wrong-organisation conversation list')
    verified(await b.getByText(BROWSER_MESSAGE,{exact:true}).count()===0,'old organisation message removed from DOM')
    await selectOrganisation(b,recipient,seed,'A');await openMessages(b);await selectConversation(b)
    await b.getByText(BROWSER_MESSAGE,{exact:true}).waitFor();checks++
    await until(()=>ready(observations.doctorB),'returned-organisation private subscriptions')
    for(const key of KEYS) {
      const size=await pages[key].evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}))
      verified(size.scroll<=size.width+1,'browser viewport fit')
      verified(!observations[key].error && !observations[key].networkDenied && observations[key].pageErrors===0,'browser protocol/network/runtime safety')
    }
    state.liveVerifiedAt??=new Date().toISOString();if(mode==='recover')state.recoveredAt=new Date().toISOString();await save()
    return {mode,checks,liveDeliveryRecorded:true,freshDelivery:history.length===0,recoveredAfterRestart:mode==='recover',socketReconnectVerified:true,mockedResponses:false}
  } catch(error) {
    console.log(JSON.stringify({stage,observations:Object.fromEntries(Object.entries(observations).map(([key,value])=>[key,
      {historyReads:value.historyReads,error:value.error,serverErrors:value.serverErrors,networkDenied:value.networkDenied,pageErrors:value.pageErrors,
        streams:Object.fromEntries(value.connections)}]))}))
    if(error.message?.startsWith('Synthetic browser refused:'))throw new Error(`${error.message}; stage=${stage}`)
    throw new Error(`Synthetic browser failed at ${stage}; credential-bearing diagnostics suppressed`)
  } finally {
    let cleanupFailed=false
    for(const context of Object.values(contexts)) {
      try{if((await context.cookies()).some(value=>value.name==='SAHHA_DEMO_ACCESS'))await browserRequest(context,'/api/v1/auth/logout','POST',undefined,204)}catch{cleanupFailed=true}
      finally{await context.close().catch(()=>{cleanupFailed=true})}
    }
    try{if(sender.cookies.size)await sender.request('/api/v1/auth/logout','POST',undefined,[204])}catch{cleanupFailed=true}finally{sender.dispose()}
    if(browser)await browser.close().catch(()=>{cleanupFailed=true})
    if(server)await server.close().catch(()=>{cleanupFailed=true})
    if(cleanupFailed)throw new Error('Synthetic browser cleanup failed; outer service/helper cleanup still runs')
    console.log('Synthetic browser: sessions logged out; Chromium and owned frontend stopped.')
  }
}
export async function main(args) {
  const reconcileEmpty=args.length===2 && args[0]==='live' && args[1]==='--reconcile-empty'
  if((args.length!==1 && !reconcileEmpty) || !['live','recover'].includes(args[0]))throw new Error('Use: node scripts/synthetic-browser.mjs live|recover [live only: --reconcile-empty]')
  await lock(async()=>{
    const manifest=await currentManifest(),directory=generationPath(manifest.id)
    const accounts=validateAccounts(parsePrivateJson(await readPrivate(path.join(directory,'demo-accounts.json'))),manifest.id)
    const seed=parsePrivateJson(await readPrivate(path.join(directory,'demo-seed-report.json')))
    validateBrowserSeed(seed,manifest.id)
    const journal=path.join(directory,'workflow-browser-messages.json');let state
    try{state=parsePrivateJson(await readPrivate(journal))}catch(error){if(error.code!=='ENOENT')throw error;state={kind:'sahha-synthetic-browser-messages-v1',generation:manifest.id,organisationId:seed.organisations?.A}}
    validateBrowserState(state,manifest.id,seed.organisations?.A)
    if(args[0]==='recover')check(state.liveVerifiedAt,'run live acceptance before recovery')
    if(reconcileEmpty)check(state.messageRequestId && !state.messageId && !state.liveMessageId && !state.liveNotificationId && !state.liveVerifiedAt,'no uncommitted-only command to reconcile')
    let queue=Promise.resolve(),saveFailure=false
    const save=()=>{
      validateBrowserState(state,manifest.id,seed.organisations.A)
      const snapshot=structuredClone(state)
      queue=queue.then(()=>saveJson(journal,snapshot)).catch(()=>{saveFailure=true})
      return queue.then(()=>check(!saveFailure,'private journal write failed'))
    }
    const evidence=await withSyntheticPlatform(manifest,batchNames('collaboration'),()=>runBrowser(manifest,accounts.accounts,seed,state,save,args[0],reconcileEmpty),{eventDelivery:true})
    await queue;check(!saveFailure,'private journal write failed')
    await saveJson(path.join(directory,`workflow-browser-${args[0]}-report.json`),{generation:manifest.id,verifiedAt:new Date().toISOString(),...evidence})
    console.log(`Synthetic browser ${evidence.mode}: ${evidence.checks} real browser/Gateway assertions passed; helpers stopped.`)
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
