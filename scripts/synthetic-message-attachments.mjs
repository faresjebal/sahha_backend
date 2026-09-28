#!/usr/bin/env node
// Attachment-only real React/Gateway/private-storage check. No full-app journey,
// Kafka, mocked responses, clinical grants, direct SQL, or fixture resets.
import {createHash,randomUUID} from 'node:crypto'
import {createRequire} from 'node:module'
import path from 'node:path'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {ROOT,tcpOpen} from './sahha.mjs'
import {confinedDirectory,currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {accountIdentity,validateAccounts} from './synthetic-demo.mjs'
import {withClients} from './synthetic-referrals.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {conversationItems} from './synthetic/message-seed.mjs'
import {syntheticPdf} from './synthetic/file-seed.mjs'
import {BROWSER_ORIGIN,browserApiUrl,isBlockedDesignFont} from './synthetic/browser-messages.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const SUBJECT='Synthetic private message attachment'
const BODY='Synthetic new upload only. Messaging does not grant patient-record access.'
const NAME='synthetic-message-only.pdf'
const BASE='/api/v1/files/message-attachments'
const check=(value,label)=>{if(!value)throw new Error('Attachment acceptance refused: '+label)}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms))
export function validateAttachmentState(state,generation) {
  check(state?.kind==='sahha-synthetic-message-attachments-v1' && state.generation===generation && uuid.test(generation),'generation')
  const fields=['kind','generation','organisationId','conversationRequestId','conversationId','messageRequestId','uploadRequestId',
    'fileId','messageId','browserUploadedAt','browserSentAt','verifiedAt']
  check(Object.keys(state).every(key=>fields.includes(key)),'unknown private journal field')
  for(const key of ['organisationId','conversationRequestId'])check(uuid.test(state[key]),'required identity')
  for(const [key,value] of Object.entries(state)) {
    if(key.endsWith('Id'))check(uuid.test(value),'identifier')
    if(key.endsWith('At'))check(typeof value==='string' && Number.isFinite(Date.parse(value)),'timestamp')
  }
  if(state.uploadRequestId || state.messageRequestId)check(state.uploadRequestId && state.messageRequestId && state.conversationId,'paired upload command')
  if(state.fileId)check(state.uploadRequestId,'file command')
  if(state.messageId)check(state.fileId && state.messageRequestId,'message binding')
  if(state.browserUploadedAt)check(state.fileId,'browser upload evidence')
  if(state.browserSentAt)check(state.messageId && state.browserUploadedAt,'browser send evidence')
  if(state.verifiedAt)check(state.browserSentAt,'verified browser evidence')
  return state
}
export function bindUpload(command,state) {
  check(command && Object.keys(command).sort().join(',')==='checksumSha256,contentType,conversationId,declaredSize,messageRequestId,originalFilename,uploadRequestId'
    && command.conversationId===state.conversationId && command.originalFilename===NAME && command.contentType==='application/pdf'
    && command.declaredSize===syntheticPdf().length && command.checksumSha256===createHash('sha256').update(syntheticPdf()).digest('hex')
    && uuid.test(command.uploadRequestId) && uuid.test(command.messageRequestId),'exact synthetic browser upload')
  if(!state.uploadRequestId){state.uploadRequestId=command.uploadRequestId;state.messageRequestId=command.messageRequestId}
  // On an interrupted rerun only IDs are rebound to their retained commands;
  // validate the browser's own matching message ID again before forwarding send.
  return {...command,uploadRequestId:state.uploadRequestId,messageRequestId:state.messageRequestId}
}
async function saveJson(file,value) {
  try{await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error){if(error.code!=='ENOENT')throw error;await privateFile(file,JSON.stringify(value,null,2))}
}
async function until(predicate,label) {
  const deadline=Date.now()+60000
  while(Date.now()<deadline){if(await predicate())return;await pause(250)}
  check(false,label)
}
async function request(context,route,method='GET',body,status=200,extra={}) {
  const headers={Accept:'application/json','X-Request-ID':'synthetic-'+randomUUID(),...extra}
  if(method!=='GET')headers['X-XSRF-TOKEN']=(await request(context,'/api/v1/auth/csrf')).token
  const response=await context.request.fetch(browserApiUrl(route),{method,headers,...(body===undefined?{}:{data:body}),maxRedirects:0,timeout:20000})
  try{check(response.status()===status,'browser API status '+response.status());return status===204 || status>=400?null:await response.json()}
  finally{await response.dispose()}
}
async function bytes(context,file,token,status) {
  const response=await context.request.get(browserApiUrl(BASE+'/'+file+'/content'),{headers:{'X-Download-Token':token},maxRedirects:0})
  try {
    check(response.status()===status,'binary response status '+response.status())
    if(status!==200)return null
    check(response.headers()['cache-control']?.includes('no-store') && response.headers()['x-content-type-options']==='nosniff'
      && response.headers()['content-disposition']?.startsWith('attachment;'),'private download headers')
    return await response.body()
  } finally{await response.dispose()}
}
async function run(manifest,accounts,seed,state,save,clients) {
  const requireFrontend=createRequire(path.join(ROOT,'frontend','package.json'))
  const {createServer}=await import(pathToFileURL(requireFrontend.resolve('vite')).href)
  const {default:react}=await import(pathToFileURL(requireFrontend.resolve('@vitejs/plugin-react')).href)
  const {chromium}=requireFrontend('playwright-core')
  const envDir=await confinedDirectory(path.join(generationPath(manifest.id),'browser-environment'),true)
  check(!await tcpOpen(5173),'occupied frontend; no adoption')
  let server,browser,stage='conversation',checks=0,networkError=false,pageError=false,commandError=false
  const contexts={},pages={},verified=(value,label)=>{check(value,label);checks++}
  const a=clients.doctorA,b=clients.doctorB
  try {
    await until(async()=>{try{await a.request('/api/v1/conversations?size=100');return true}catch(error){if(error.status!==503)throw error;return false}},'Communication readiness')
    const conversation=await a.request('/api/v1/conversations','POST',{conversationRequestId:state.conversationRequestId,
      recipientUserId:accountIdentity('doctorB').id,subject:SUBJECT,patientRegistrationId:null},[201])
    verified(conversation.subject===SUBJECT && conversation.organisationId===state.organisationId
      && conversation.patientRegistrationId===null && conversation.patientAccessGranted===false
      && (!state.conversationId || state.conversationId===conversation.id),'retained participant-only conversation')
    state.conversationId=conversation.id;await save()
    const route='/api/v1/conversations/'+state.conversationId
    const history=conversationItems(await a.request(route+'/messages?size=100'))
    check(history.length<=1 && (!state.messageId || history[0]?.id===state.messageId),'retained message history')
    if(history.length) {
      check(state.messageRequestId && state.fileId && history[0].body===BODY
        && history[0].attachments?.length===1 && history[0].attachments[0].fileId===state.fileId,'recovery provenance')
      state.messageId=history[0].id;await save()
    }
    stage='browser startup'
    server=await createServer({root:path.join(ROOT,'frontend'),configFile:false,envDir,plugins:[react()],logLevel:'silent',
      define:{'import.meta.env.VITE_USE_MOCKS':'"false"','import.meta.env.VITE_USE_AUTH_MOCKS':'"false"','import.meta.env.VITE_API_BASE_URL':'"/api/v1"'},
      server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api/v1':{target:'http://127.0.0.1:8079',changeOrigin:false,ws:true}}}})
    await server.listen()
    browser=await chromium.launch({headless:true,executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'})
    for(const key of ['doctorA','doctorB']) {
      stage=key+' browser login'
      const context=await browser.newContext({viewport:{width:key==='doctorA'?1440:390,height:960},acceptDownloads:true})
      contexts[key]=context
      await context.route('**/*',async intercepted=>{
        if(new URL(intercepted.request().url()).origin===BROWSER_ORIGIN)await intercepted.continue()
        else{if(!isBlockedDesignFont(intercepted.request().url(),intercepted.request().resourceType()))networkError=true;await intercepted.abort()}
      })
      const page=await context.newPage();pages[key]=page;page.setDefaultTimeout(30000)
      page.on('pageerror',()=>{pageError=true})
      await page.goto(BROWSER_ORIGIN+'/login')
      const account=accounts.find(value=>value.key===key)
      await page.getByLabel('Email',{exact:true}).fill(account.email)
      await page.getByLabel('Password',{exact:true}).fill(account.password)
      await page.getByRole('button',{name:/Enter workspace/i}).click()
      await page.waitForURL(url=>!url.pathname.startsWith('/login'))
      await page.goto(BROWSER_ORIGIN+'/organisations/select')
      await page.getByRole('button',{name:/Sahha Synthetic Clinic A/}).click()
      await page.waitForURL(url=>url.pathname!=='/organisations/select' && !url.pathname.startsWith('/login'))
      verified((await request(context,'/api/v1/auth/session')).activeOrganisationId===state.organisationId,'browser organisation')
      await page.goto(BROWSER_ORIGIN+'/doctor/messages')
      await page.locator('.connected-messenger > aside button').filter({hasText:SUBJECT}).click()
      await page.getByRole('heading',{name:SUBJECT,exact:true}).waitFor()
    }
    const sender=pages.doctorA,recipient=pages.doctorB
    if(!state.messageId) {
      stage='browser upload'
      let browserMessageId
      await sender.route(browserApiUrl(BASE+'/uploads'),async intercepted=>{
        try {
          const command=intercepted.request().postDataJSON()
          const bound=bindUpload(command,state);browserMessageId=command.messageRequestId;await save()
          await intercepted.continue({postData:JSON.stringify(bound)})
        } catch {commandError=true;await intercepted.abort()}
      })
      await sender.route(browserApiUrl(route+'/messages'),async intercepted=>{
        if(intercepted.request().method()!=='POST'){await intercepted.continue();return}
        try {
          const command=intercepted.request().postDataJSON()
          check(command.messageRequestId===browserMessageId && command.body===BODY && command.attachmentIds?.length===1
            && command.attachmentIds[0]===state.fileId,'browser send matches its own upload')
          await intercepted.continue({postData:JSON.stringify({...command,messageRequestId:state.messageRequestId})})
        } catch{commandError=true;await intercepted.abort()}
      })
      const ticketResponse=sender.waitForResponse(response=>new URL(response.url()).pathname===BASE+'/uploads'
        && response.request().method()==='POST' && response.status()===200)
      void ticketResponse.catch(()=>{})
      await sender.getByLabel('Message',{exact:true}).fill(BODY)
      await sender.getByLabel('Attachment files',{exact:true}).setInputFiles({name:NAME,mimeType:'application/pdf',buffer:syntheticPdf()})
      const ticket=await (await ticketResponse).json()
      check(!state.fileId || state.fileId===ticket.file.fileId,'retained file identity')
      state.fileId=ticket.file.fileId;await save()
      let metadata
      await until(async()=>{metadata=await a.request(BASE+'/'+state.fileId);return metadata.uploadStatus==='STORED'},'stored browser upload')
      state.browserUploadedAt=new Date().toISOString();await save()
      verified(metadata.size===syntheticPdf().length && metadata.originalFilename===NAME,'real browser bytes persisted')
      if(metadata.scanStatus==='PENDING') {
        await sender.getByText('Waiting for security scan',{exact:true}).waitFor()
        verified(await sender.getByRole('button',{name:'Send message',exact:true}).isDisabled(),'quarantine gates browser send')
        await b.request(BASE+'/'+state.fileId+'/download-grants','POST',undefined,[404]);checks++
        await a.request(route+'/messages','POST',{messageRequestId:state.messageRequestId,body:BODY,attachmentIds:[state.fileId]},[409]);checks++
        await a.request(BASE+'/'+state.fileId+'/synthetic-scan','POST',{decision:'CLEAN'})
        await sender.getByRole('button',{name:'Check scan status'}).click()
      }
      await sender.getByText('Ready to send',{exact:true}).waitFor()
      await b.request(BASE+'/'+state.fileId+'/download-grants','POST',undefined,[404]);checks++
      stage='browser send'
      const sent=sender.waitForResponse(response=>new URL(response.url()).pathname===route+'/messages'
        && response.request().method()==='POST' && response.status()===201)
      void sent.catch(()=>{})
      await sender.getByRole('button',{name:'Send message',exact:true}).click()
      const message=await (await sent).json()
      verified(message.attachments?.length===1 && message.attachments[0].fileId===state.fileId,'immutable sent attachment')
      state.messageId=message.id;state.browserSentAt=new Date().toISOString();await save()
    }
    check(state.browserUploadedAt && state.browserSentAt,'positive browser upload/send evidence')
    stage='recipient reload and download'
    await recipient.reload()
    await recipient.locator('.connected-messenger > aside button').filter({hasText:SUBJECT}).click()
    await recipient.getByText(BODY,{exact:true}).waitFor()
    const downloadEvent=recipient.waitForEvent('download');void downloadEvent.catch(()=>{})
    await recipient.getByRole('button',{name:new RegExp(NAME)}).click()
    const download=await downloadEvent
    verified(download.suggestedFilename()===NAME,'browser download filename')
    const stream=await download.createReadStream(),chunks=[]
    check(stream,'browser download stream')
    for await(const chunk of stream)chunks.push(chunk)
    verified(Buffer.concat(chunks).equals(syntheticPdf()),'real private storage bytes')
    await download.delete()
    verified(await recipient.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2),'mobile horizontal fit')
    const command={messageRequestId:state.messageRequestId,body:BODY,attachmentIds:[state.fileId]}
    verified((await a.request(route+'/messages','POST',command,[201])).id===state.messageId,'exact send retry')
    await a.request(route+'/messages','POST',{...command,attachmentIds:[]},[409]);checks++
    verified(conversationItems(await b.request(route+'/messages?size=100')).length===1,'no duplicate sends')
    stage='privacy and single-use token checks'
    for(const [key,status] of [['unrelatedDoctor',404],['receptionist',403],['orgAdmin',403],['patient',401]]) {
      await clients[key].request(BASE+'/'+state.fileId,'GET',undefined,[status]);checks++
      await clients[key].request(BASE+'/'+state.fileId+'/download-grants','POST',undefined,[status]);checks++
    }
    await b.select(seed.organisations.B)
    await b.request(BASE+'/'+state.fileId,'GET',undefined,[404]);checks++
    await b.select(seed.organisations.A)
    const grant=await request(contexts.doctorB,BASE+'/'+state.fileId+'/download-grants','POST')
    await bytes(contexts.doctorA,state.fileId,grant.downloadToken,404);checks++
    verified((await bytes(contexts.doctorB,state.fileId,grant.downloadToken,200)).equals(syntheticPdf()),'recipient-bound token')
    await bytes(contexts.doctorB,state.fileId,grant.downloadToken,404);checks++
    verified((await a.request(route)).patientAccessGranted===false,'messaging still grants no clinical access')
    verified(!networkError && !pageError && !commandError,'no page errors or off-Gateway requests')
    state.verifiedAt=new Date().toISOString();await save()
    return {checks,browserUpload:true,browserSend:true,browserDownload:true,privateStorage:true,
      fullApplicationTesting:false,kafkaStarted:false,syntheticScanOnly:true}
  } catch(error) {
    if(error.message?.startsWith('Attachment acceptance refused:'))throw new Error(error.message+'; stage='+stage)
    throw new Error('Attachment acceptance failed at '+stage+'; credential-bearing diagnostics suppressed')
  } finally {
    let failed=false
    for(const context of Object.values(contexts)) {
      try{if((await context.cookies()).some(value=>value.name==='SAHHA_DEMO_ACCESS'))await request(context,'/api/v1/auth/logout','POST',undefined,204)}
      catch{failed=true}finally{await context.close().catch(()=>{failed=true})}
    }
    if(browser)await browser.close().catch(()=>{failed=true})
    if(server)await server.close().catch(()=>{failed=true})
    if(failed)throw new Error('Attachment browser cleanup failed; outer helper cleanup still runs')
    console.log('Attachment browser sessions, Chromium and frontend stopped.')
  }
}
export {run as runAttachmentBrowserGate}
export async function main(args) {
  if(args.length!==1 || args[0]!=='verify')throw new Error('Use: node scripts/synthetic-message-attachments.mjs verify')
  await lock(async()=>{
    const manifest=await currentManifest(),directory=generationPath(manifest.id)
    const read=async name=>parsePrivateJson(await readPrivate(path.join(directory,name)))
    const accounts=validateAccounts(await read('demo-accounts.json'),manifest.id),seed=await read('demo-seed-report.json')
    check(seed.generation===manifest.id && uuid.test(seed.organisations?.A) && uuid.test(seed.organisations?.B),'retained seed')
    let state
    try{state=await read('workflow-message-attachments.json')}catch(error){
      if(error.code!=='ENOENT')throw error
      state={kind:'sahha-synthetic-message-attachments-v1',generation:manifest.id,organisationId:seed.organisations.A,conversationRequestId:randomUUID()}
    }
    validateAttachmentState(state,manifest.id);check(state.organisationId===seed.organisations.A,'retained organisation')
    const save=()=>{validateAttachmentState(state,manifest.id);return saveJson(path.join(directory,'workflow-message-attachments.json'),state)}
    await save()
    const report=await withSyntheticPlatform(manifest,[...batchNames('collaboration'),'file-service'],
      ()=>withClients(accounts.accounts,seed,clients=>run(manifest,accounts.accounts,seed,state,save,clients)),{syntheticScan:true})
    await saveJson(path.join(directory,'workflow-message-attachments-report.json'),{generation:manifest.id,verifiedAt:state.verifiedAt,...report})
    console.log('Attachment-only native gate: '+report.checks+' assertions passed; owned helpers stopped. Full-app testing not started.')
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main(process.argv.slice(2)).catch(error=>{console.error(error.message);process.exitCode=1})
