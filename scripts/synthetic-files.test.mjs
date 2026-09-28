import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'
import { test } from 'node:test'
import { ACCOUNT_KEYS, accountIdentity } from './synthetic-demo.mjs'
import { FILE_NAME, UNSELECTED_FILE_NAME, fileBytes, seedFiles, syntheticPdf, validateFileState } from './synthetic/file-seed.mjs'

function fixture() {
  const generation=randomUUID(),org=randomUUID(),consultationId=randomUUID()
  const seed={generation,organisations:{A:org}},clinical={generation,consultationId,appointmentId:randomUUID(),patientId:randomUUID(),registrationId:randomUUID(),status:'FINALIZED'}
  const state={kind:'sahha-synthetic-files-v1',generation,consultationId},accounts=ACCOUNT_KEYS.map(accountIdentity),calls=[],clients=[],saved=[]
  const model={file:null,extraFiles:[],bytes:null,created:0,scans:0,dropUpload:false,dropScan:false},grants=new Map()
  const createClient=()=>{
    const client={key:null,cookies:new Map(),disposed:false,
      async login(account){this.key=account.key;this.cookies.set('test','memory-only');return {userId:account.id}},
      async select(id){return {activeOrganisationId:id}},dispose(){this.disposed=true;this.cookies.clear()},
      async request(route,method='GET',body,expected=[200]) {
        calls.push({key:this.key,route,method,body:structuredClone(body)})
        let status=200,result={}
        if(route==='/api/v1/auth/logout') status=204
        else if(route==='/api/v1/organisations/memberships') result=[]
        else if(route.startsWith('/api/v1/files?')) result=[...model.extraFiles,...(model.file?[model.file]:[])]
        else if(route==='/api/v1/files/uploads') {
          assert.equal(model.file,null)
          model.file={fileId:randomUUID(),consultationId,originalFilename:body.originalFilename,contentType:body.contentType,size:body.declaredSize,uploadStatus:'NEGOTIATED',scanStatus:'PENDING',downloadAvailable:false}
          model.created++;status=201
          result={fileId:model.file.fileId,uploadPath:`/api/v1/files/${model.file.fileId}/content`,uploadToken:randomBytes(32).toString('base64url'),expiresAt:new Date(Date.now()+60000).toISOString()}
        } else if(route.endsWith('/synthetic-scan')) {
          assert.equal(model.file.scanStatus,'PENDING');model.file.scanStatus='CLEAN';model.file.downloadAvailable=true;model.scans++
          result=model.file
          if(model.dropScan){model.dropScan=false;throw new Error('Synthetic lost scan response')}
        } else if(route.endsWith('/download-grants')) {
          if(this.key!=='doctorA') status=this.key==='patient'?401:this.key==='doctorB'?404:403
          else if(model.file.scanStatus!=='CLEAN') status=409
          else {
            const token=randomBytes(32).toString('base64url');grants.set(token,false)
            result={fileId:model.file.fileId,downloadPath:`/api/v1/files/${model.file.fileId}/content`,downloadToken:token,expiresAt:new Date(Date.now()+60000).toISOString()}
          }
        } else assert.fail(`Unexpected fake file route ${route}`)
        if(!expected.includes(status)) throw Object.assign(new Error(`Synthetic expected ${status}`),{status})
        return structuredClone(result)
      }}
    clients.push(client);return client
  }
  const transfer=async(client,id,method,token,expected,bytes,csrf=true)=>{
    assert.equal(id,model.file.fileId)
    if(method==='PUT') {
      assert.equal(saved.at(-1).uploadToken,token,'Persist private ticket before streaming bytes')
      if(!csrf){assert.equal(expected,403);assert.equal(model.bytes,null);return null}
      assert.equal(expected,202);assert.equal(model.bytes,null)
      model.bytes=Buffer.from(bytes);model.file.uploadStatus='STORED'
      if(model.dropUpload){model.dropUpload=false;throw new Error('Synthetic lost stored-upload response')}
      return structuredClone(model.file)
    }
    assert.ok(grants.has(token))
    const status=!client.key?401:client.key==='doctorB'?404:grants.get(token)?409:200
    assert.equal(status,expected)
    if(status===401 && model.anonymousCsrf) client.cookies.set('SAHHA_DEMO_XSRF','anonymous-synthetic-csrf')
    if(status!==200) return null
    grants.set(token,true);return Buffer.from(model.bytes)
  }
  return {model,state,clinical,seed,calls,clients,saved,run:(options={})=>seedFiles(accounts,seed,clinical,state,async value=>saved.push(structuredClone(value)),createClient,transfer,options)}
}
test('synthetic PDF is deterministic, labelled and contains valid cross-reference offsets',()=>{
  const bytes=syntheticPdf(),text=bytes.toString('ascii')
  assert.deepEqual(bytes,syntheticPdf());assert.ok(text.startsWith('%PDF-1.4'))
  assert.match(text,/Synthetic demo only\. Not a medical document\./)
  assert.equal(Number(text.match(/startxref\n(\d+)/)[1]),text.indexOf('xref\n'))
  const offsets=[...text.matchAll(/(\d{10}) 00000 n/g)].map(value=>Number(value[1]))
  assert.equal(offsets.length,5)
  offsets.forEach((offset,index)=>assert.ok(text.slice(offset).startsWith(`${index+1} 0 obj`)))
})
test('file journal rejects foreign scope and injected private tokens',()=>{
  const f=fixture();validateFileState(f.state,f.seed.generation,f.clinical.consultationId)
  for(const change of [{generation:randomUUID()},{consultationId:randomUUID()},{fileId:'../outside'},{storageEndpoint:'remote'},
    {uploadToken:'unsafe\nheader',fileId:randomUUID(),uploadExpiresAt:new Date().toISOString()}]) {
    assert.throws(()=>validateFileState({...f.state,...change},f.seed.generation,f.clinical.consultationId))
  }
})
test('file seed repeats recover identical bytes without another upload or scan',async()=>{
  const f=fixture(),first=await f.run(),snapshot=structuredClone(f.model.file),again=await f.run()
  assert.equal(first.fileId,again.fileId);assert.ok(first.checks>=20)
  assert.equal(first.malwareScannerVerified,false);assert.equal(first.syntheticScanOnly,true)
  assert.equal(f.model.created,1);assert.equal(f.model.scans,1);assert.deepEqual(f.model.file,snapshot)
  assert.equal(f.state.uploadToken,undefined);assert.equal(f.saved.at(-1).uploadToken,undefined)
  assert.ok(f.clients.every(client=>client.disposed && client.cookies.size===0))
})
test('stored upload response loss recovers metadata instead of reusing the consumed upload ticket',async()=>{
  const f=fixture();f.model.dropUpload=true
  await assert.rejects(f.run(),/lost stored-upload response/)
  const id=f.model.file.fileId;assert.ok(f.state.uploadToken)
  const result=await f.run()
  assert.equal(result.fileId,id);assert.equal(f.model.created,1);assert.equal(f.state.uploadToken,undefined)
})

test('anonymous CSRF cookies never trigger an authenticated logout attempt',async()=>{
  const f=fixture();f.model.anonymousCsrf=true
  await f.run()
  assert.ok(!f.calls.some(call=>call.key===null && call.route==='/api/v1/auth/logout'))
  assert.ok(f.clients.every(client=>client.disposed && client.cookies.size===0))
})
test('scan response loss reuses the existing decision and file',async()=>{
  const f=fixture();f.model.dropScan=true
  await assert.rejects(f.run(),/lost scan response/)
  const result=await f.run()
  assert.equal(result.fileId,f.model.file.fileId);assert.equal(f.model.scans,1)
})

test('unselected document uses its own identity and preserves the existing primary attachment',async()=>{
  const f=fixture()
  const primary={fileId:randomUUID(),consultationId:f.clinical.consultationId,originalFilename:FILE_NAME,contentType:'application/pdf',size:syntheticPdf().length,uploadStatus:'STORED',scanStatus:'CLEAN'}
  f.model.extraFiles=[structuredClone(primary)]
  const first=await f.run({filename:UNSELECTED_FILE_NAME}),repeat=await f.run({filename:UNSELECTED_FILE_NAME})
  assert.equal(first.fileId,repeat.fileId);assert.notEqual(first.fileId,primary.fileId)
  assert.equal(first.filename,UNSELECTED_FILE_NAME);assert.equal(f.model.created,1)
  assert.deepEqual(f.model.extraFiles,[primary])
})

test('unknown fixture names and duplicate existing filenames fail closed',async()=>{
  const f=fixture()
  await assert.rejects(f.run({filename:'unreviewed.pdf'}),/unknown synthetic document/)
  assert.equal(f.clients.length,0)
  await f.run();f.model.extraFiles=[{...f.model.file,fileId:randomUUID()}]
  await assert.rejects(f.run(),/unexpected existing attachments/);assert.equal(f.model.created,1)
  f.model.extraFiles=[{...f.model.file,originalFilename:UNSELECTED_FILE_NAME}]
  await assert.rejects(f.run(),/unexpected existing attachments/);assert.equal(f.model.created,1)
})
test('changed attachment metadata is refused and never replaced',async()=>{
  const f=fixture();await f.run();f.model.file.originalFilename='User-edited document.pdf'
  await assert.rejects(f.run(),/retained file metadata/)
  assert.equal(f.model.created,1);assert.equal(f.model.file.originalFilename,'User-edited document.pdf')
})
test('missing expired negotiation token fails closed without creating a replacement',async()=>{
  const f=fixture()
  f.model.file={fileId:randomUUID(),consultationId:f.clinical.consultationId,originalFilename:FILE_NAME,contentType:'application/pdf',size:syntheticPdf().length,uploadStatus:'NEGOTIATED',scanStatus:'PENDING'}
  await assert.rejects(f.run(),/missing\/expired upload ticket/)
  assert.equal(f.model.created,0);assert.equal(f.model.bytes,null)
})
test('binary transfers pin Gateway and use header-only tokens with no cache or redirects',async()=>{
  const id=randomUUID(),token='a'.repeat(43),bytes=syntheticPdf()
  const client={cookieHeader:()=> 'demo-cookie=memory-only',fetcher:async(url,options)=>{
    assert.equal(url,`http://127.0.0.1:8079/api/v1/files/${id}/content`)
    assert.equal(options.headers['X-Download-Token'],token);assert.equal(options.cache,'no-store');assert.equal(options.redirect,'error')
    return new Response(bytes,{headers:{'Content-Type':'application/pdf','Content-Length':String(bytes.length),'Cache-Control':'no-store'}})
  }}
  assert.deepEqual(await fileBytes(client,id,'GET',token,200),bytes)
  await assert.rejects(fileBytes(client,'../outside','GET',token,200),/scope/)
  await assert.rejects(fileBytes(client,id,'POST',token,200),/scope/)
  await assert.rejects(fileBytes(client,id,'GET','unsafe\nheader',200),/scope/)
})
test('failed binary responses never disclose private body content',async()=>{
  const client={cookieHeader:()=>'',fetcher:async()=>new Response('sensitive synthetic diagnostic',{status:500})}
  await assert.rejects(fileBytes(client,randomUUID(),'GET','a'.repeat(43),200),error=>{
    assert.match(error.message,/500; body suppressed/);assert.ok(!error.message.includes('sensitive'));return true
  })
})

test('shared binary transfers are Gateway-pinned, header-only and read-only',async()=>{
  const id=randomUUID(),patientId=randomUUID(),token='a'.repeat(43),bytes=syntheticPdf();let calls=0
  const client={cookieHeader:()=> 'demo-cookie=memory-only',fetcher:async(url,options)=>{
    calls++;assert.equal(url,`http://127.0.0.1:8079/api/v1/files/shared/${patientId}/${id}/content`)
    assert.equal(options.method,'GET');assert.equal(options.headers['X-Download-Token'],token)
    assert.equal(options.redirect,'error');assert.equal(options.cache,'no-store')
    return new Response(bytes,{headers:{'Content-Type':'application/pdf','Content-Length':String(bytes.length),'Cache-Control':'no-store'}})
  }}
  assert.deepEqual(await fileBytes(client,id,'GET',token,200,undefined,true,patientId),bytes)
  await assert.rejects(fileBytes(client,id,'PUT',token,200,bytes,true,patientId),/shared binary request scope/)
  await assert.rejects(fileBytes(client,id,'GET',token,200,undefined,true,'../outside'),/shared binary request scope/)
  assert.equal(calls,1)
})

test('binary responses accept only configured CSRF cookie updates, never session cookies',async()=>{
  const bytes=syntheticPdf(),id=randomUUID(),token='a'.repeat(43)
  let cookie='SAHHA_DEMO_XSRF=new-synthetic-csrf; Path=/; SameSite=Lax'
  const client={cookies:new Map(),csrf:null,cookieHeader:()=>'',fetcher:async()=>new Response(bytes,{headers:{
    'Content-Type':'application/pdf','Content-Length':String(bytes.length),'Cache-Control':'no-store','Set-Cookie':cookie}})}
  assert.deepEqual(await fileBytes(client,id,'GET',token,200),bytes)
  assert.equal(client.csrf,'new-synthetic-csrf');assert.equal(client.cookies.get('SAHHA_DEMO_XSRF'),'new-synthetic-csrf')
  cookie='SAHHA_DEMO_XSRF=; Path=/; Max-Age=0'
  await fileBytes(client,id,'GET',token,200);assert.equal(client.csrf,null);assert.equal(client.cookies.size,0)
  cookie='SAHHA_DEMO_XSRF=%invalid-private-value; Path=/'
  await assert.rejects(fileBytes(client,id,'GET',token,200),error=>{
    assert.match(error.message,/Invalid binary CSRF cookie/);assert.ok(!error.message.includes('private-value'));return true
  })
  for(const name of ['JSESSIONID','SAHHA_DEMO_ACCESS','SAHHA_DEMO_REFRESH','XSRF-TOKEN','untrusted']) {
    cookie=`${name}=private-cookie-value; Path=/`
    await assert.rejects(fileBytes(client,id,'GET',token,200),error=>{
      assert.match(error.message,/unexpected binary-response cookie/);assert.ok(!error.message.includes('private-cookie-value'));return true
    })
  }
})
