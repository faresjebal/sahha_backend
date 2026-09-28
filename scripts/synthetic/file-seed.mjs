import { createHash, randomUUID } from 'node:crypto'
import { accountIdentity, waitOrganisationRoute } from '../synthetic-demo.mjs'
import { GatewayClient, gatewayPath } from './gateway-client.mjs'
import { DEMO_COOKIES } from './environment.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const check=(condition,label)=>{if(!condition) throw new Error(`Synthetic file workflow refused: ${label}; existing files are not replaced`)}
export const FILE_NAME='synthetic-demonstration-only.pdf'
export const UNSELECTED_FILE_NAME='synthetic-unselected-demonstration.pdf'
export function syntheticPdf() {
  const stream='BT /F1 10 Tf 10 50 Td (Synthetic demo only. Not a medical document.) Tj ET\n'
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 100] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`]
  let text='%PDF-1.4\n';const offsets=[0]
  for(const [index,value] of objects.entries()){offsets.push(Buffer.byteLength(text));text+=`${index+1} 0 obj\n${value}\nendobj\n`}
  const start=Buffer.byteLength(text)
  text+='xref\n0 6\n0000000000 65535 f \n'+offsets.slice(1).map(offset=>`${String(offset).padStart(10,'0')} 00000 n \n`).join('')
  return Buffer.from(text+`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${start}\n%%EOF\n`)
}
export function validateFileState(state,generation,consultationId) {
  check(uuid.test(generation) && uuid.test(consultationId) && state?.kind==='sahha-synthetic-files-v1'
    && state.generation===generation && state.consultationId===consultationId,'journal identity')
  check(Object.keys(state).every(key=>['kind','generation','consultationId','fileId','uploadToken','uploadExpiresAt'].includes(key)),'unknown journal fields')
  if(state.fileId!==undefined) check(uuid.test(state.fileId),'file identifier')
  if(state.uploadToken!==undefined) check(state.fileId && /^[A-Za-z0-9_-]{32,128}$/.test(state.uploadToken)
    && Number.isFinite(Date.parse(state.uploadExpiresAt)),'private upload ticket')
  return state
}
// Binary content stays on Gateway too. Tokens are header-bound and kept private;
// no presigned URL, credential-bearing query string or internal service URL is used.
export async function fileBytes(client,fileId,method,token,expected,bytes,csrf=true,sharedPatientId,scope='SELECTED') {
  check(uuid.test(fileId) && ['GET','PUT'].includes(method) && typeof token==='string' && !/[\r\n]/.test(token),'binary request scope')
  if(sharedPatientId!==undefined) check(uuid.test(sharedPatientId) && method==='GET','shared binary request scope')
  check(['SELECTED','SHARED_CARE'].includes(scope) && (scope!=='SHARED_CARE' || sharedPatientId!==undefined),'binary access scope')
  if(method==='PUT') check(bytes instanceof Uint8Array && bytes.length>0 && bytes.length<=1024*1024,'bounded synthetic upload')
  const route=sharedPatientId===undefined ? '/api/v1/files/'+fileId+'/content'
    : '/api/v1/files/'+(scope==='SHARED_CARE'?'shared-care':'shared')+'/'+sharedPatientId+'/'+fileId+'/content'
  const response=await client.fetcher(gatewayPath(route),{method,redirect:'error',cache:'no-store',signal:AbortSignal.timeout(20000),
    headers:{Cookie:client.cookieHeader(),'X-Request-ID':`synthetic-${randomUUID()}`,
      ...(method==='GET'?{'X-Download-Token':token}:{'X-Upload-Token':token,'Content-Type':'application/pdf',...(csrf?{'X-XSRF-TOKEN':client.csrf}:{})})},
    ...(method==='PUT'?{body:bytes}:{})})
  // The configured SPA CSRF cookie may be refreshed by a binary request, just
  // as for JSON. Authentication and servlet-session cookies remain forbidden.
  for(const cookie of response.headers.getSetCookie()) {
    const [pair,...attributes]=cookie.split(';'),separator=pair.indexOf('='),name=pair.slice(0,separator)
    const category=name===DEMO_COOKIES.csrf?'demo CSRF':name==='JSESSIONID'?'servlet session':'unrecognised'
    if(name!==DEMO_COOKIES.csrf) {
      await response.body?.cancel()
      check(false,`${method} ${response.status} unexpected binary-response cookie (${category})`)
    }
    const value=pair.slice(separator+1)
    client.binaryCsrfUpdates=(client.binaryCsrfUpdates??0)+1
    if(!value || attributes.some(attribute=>/^\s*max-age=0$/i.test(attribute))) {client.cookies.delete(name);client.csrf=null}
    else {
      let decoded
      try{decoded=decodeURIComponent(value)}catch{await response.body?.cancel();throw new Error('Invalid binary CSRF cookie; value suppressed')}
      client.cookies.set(name,value);client.csrf=decoded
    }
  }
  if(response.status!==expected) {await response.body?.cancel();throw new Error(`Synthetic Gateway file transfer returned ${response.status}; body suppressed`)}
  if(expected!==200 && expected!==202){await response.body?.cancel();return null}
  check(response.headers.get('cache-control')?.includes('no-store'),'binary response cache policy')
  if(method==='PUT') {
    try{return await response.json()}catch{throw new Error('Invalid upload response; body suppressed')}
  }
  check(response.headers.get('content-type')==='application/pdf' && Number(response.headers.get('content-length'))===syntheticPdf().length,'download type and size')
  return Buffer.from(await response.arrayBuffer())
}
export async function seedFiles(accounts,seed,clinical,state,save,createClient=()=>new GatewayClient(),transfer=fileBytes,{filename=FILE_NAME}={}) {
  validateFileState(state,seed.generation,clinical.consultationId)
  check([FILE_NAME,UNSELECTED_FILE_NAME].includes(filename),'unknown synthetic document fixture')
  check(clinical.generation===seed.generation && clinical.status==='FINALIZED' && uuid.test(clinical.appointmentId)
    && uuid.test(clinical.patientId) && uuid.test(clinical.registrationId) && uuid.test(seed.organisations?.A),'clinical provenance')
  const keys=['doctorA','doctorB','receptionist','orgAdmin','patient'],byKey=Object.fromEntries(accounts.map(value=>[value.key,value]))
  for(const key of keys) check(byKey[key]?.id===accountIdentity(key).id,'account identity')
  const clients=Object.fromEntries(keys.map(key=>[key,createClient()])),anonymous=createClient()
  let checks=0
  const verified=(condition,label)=>{check(condition,label);checks++}
  try {
    for(const key of keys) verified((await clients[key].login(byKey[key])).userId===byKey[key].id,'authenticated identity')
    await waitOrganisationRoute(clients.doctorA)
    for(const key of keys.filter(key=>key!=='patient')) verified((await clients[key].select(seed.organisations.A)).activeOrganisationId===seed.organisations.A,'active organisation')
    const doctor=clients.doctorA,bytes=syntheticPdf(),listRoute=`/api/v1/files?consultationId=${state.consultationId}`
    const selectFixture=values=>{
      check(Array.isArray(values) && values.length<=2 && new Set(values.map(value=>value.originalFilename)).size===values.length
        && new Set(values.map(value=>value.fileId)).size===values.length
        && values.every(value=>[FILE_NAME,UNSELECTED_FILE_NAME].includes(value.originalFilename)
          && value.consultationId===state.consultationId && uuid.test(value.fileId) && value.contentType==='application/pdf'
          && value.size===bytes.length),'unexpected existing attachments or changed retained file metadata')
      return values.filter(value=>value.originalFilename===filename)
    }
    let files
    for(let attempt=0;attempt<60;attempt++) {
      try{files=selectFixture(await doctor.request(listRoute));break}
      catch(error){if(error.status!==503 || attempt===59) throw error;await new Promise(resolve=>setTimeout(resolve,1000))}
    }
    if(!files.length) {
      check(!state.fileId,'journalled file disappeared')
      const ticket=await doctor.request('/api/v1/files/uploads','POST',{consultationId:state.consultationId,originalFilename:filename,contentType:'application/pdf',
        declaredSize:bytes.length,expectedChecksumSha256:createHash('sha256').update(bytes).digest('hex')},[201])
      check(uuid.test(ticket.fileId) && ticket.uploadPath===`/api/v1/files/${ticket.fileId}/content`,'upload ticket scope')
      state.fileId=ticket.fileId;state.uploadToken=ticket.uploadToken;state.uploadExpiresAt=ticket.expiresAt
      validateFileState(state,seed.generation,clinical.consultationId);await save(state)
      files=selectFixture(await doctor.request(listRoute))
    }
    let file=files[0]
    verified(file?.consultationId===state.consultationId && uuid.test(file.fileId) && file.originalFilename===filename
      && file.contentType==='application/pdf' && file.size===bytes.length,'retained file metadata')
    if(state.fileId) check(file.fileId===state.fileId,'retained file identity')
    else {state.fileId=file.fileId;await save(state)}
    if(file.uploadStatus==='NEGOTIATED') {
      check(typeof state.uploadToken==='string' && Date.parse(state.uploadExpiresAt)>Date.now(),'missing/expired upload ticket; retain quarantined file for review')
      await transfer(doctor,file.fileId,'PUT',state.uploadToken,403,bytes,false);checks++
      const uploaded=await transfer(doctor,file.fileId,'PUT',state.uploadToken,202,bytes)
      verified(uploaded.fileId===file.fileId && uploaded.uploadStatus==='STORED' && uploaded.scanStatus==='PENDING','checksum-verified private upload')
      file=selectFixture(await doctor.request(listRoute))[0]
    }
    check(file.uploadStatus==='STORED','file upload did not complete')
    // A committed upload survives response loss: metadata is read before using a
    // ticket again. The credential is removed from the retained private journal.
    delete state.uploadToken;delete state.uploadExpiresAt;await save(state)
    if(file.scanStatus==='PENDING') {
      await doctor.request(`/api/v1/files/${file.fileId}/download-grants`,'POST',undefined,[409]);checks++
      const scan=await doctor.request(`/api/v1/files/${file.fileId}/synthetic-scan`,'POST',{decision:'CLEAN'})
      verified(scan.fileId===file.fileId && scan.scanStatus==='CLEAN','explicit synthetic-only scan decision')
      file=selectFixture(await doctor.request(listRoute))[0]
    }
    verified(file.scanStatus==='CLEAN' && file.downloadAvailable===true,'clean file availability')
    for(const key of keys.filter(key=>key!=='doctorA')) {
      // File JWT validation requires active staff organisation context.
      const denied=key==='patient'?401:key==='doctorB'?404:403
      await clients[key].request(`/api/v1/files/${file.fileId}/download-grants`,'POST',undefined,[denied]);checks++
    }
    const grant=await doctor.request(`/api/v1/files/${file.fileId}/download-grants`,'POST')
    verified(grant.fileId===file.fileId && grant.downloadPath===`/api/v1/files/${file.fileId}/content`
      && /^[A-Za-z0-9_-]{32,128}$/.test(grant.downloadToken) && Date.parse(grant.expiresAt)>Date.now()
      && Date.parse(grant.expiresAt)-Date.now()<=300000,'short-lived scoped download grant')
    await transfer(anonymous,file.fileId,'GET',grant.downloadToken,401);checks++
    await transfer(clients.doctorB,file.fileId,'GET',grant.downloadToken,404);checks++
    verified(bytes.equals(await transfer(doctor,file.fileId,'GET',grant.downloadToken,200)),'private object byte-for-byte recovery')
    await transfer(doctor,file.fileId,'GET',grant.downloadToken,409);checks++
    return {generation:state.generation,verifiedAt:new Date().toISOString(),consultationId:state.consultationId,fileId:file.fileId,filename,checks,
      binaryCsrfUpdates:Object.values(clients).reduce((sum,client)=>sum+(client.binaryCsrfUpdates??0),0),
      syntheticScanOnly:true,malwareScannerVerified:false}
  } finally {
    let failed=false
    for(const client of [...Object.values(clients),anonymous]) {
      try{if(client!==anonymous && client.cookies.size) await client.request('/api/v1/auth/logout','POST',undefined,[204])}
      catch{failed=true}finally{client.dispose()}
    }
    if(failed) throw new Error('Synthetic file logout failed; all clients discarded')
  }
}
