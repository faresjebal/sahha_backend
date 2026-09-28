import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { test } from 'node:test'
import { ACCOUNT_KEYS, accountIdentity } from './synthetic-demo.mjs'
import { MESSAGE, SUBJECT, conversationItems, seedMessages, validateMessageState } from './synthetic/message-seed.mjs'

function fixture() {
  const generation=randomUUID(),orgA=randomUUID(),orgB=randomUUID()
  const seed={generation,organisations:{A:orgA,B:orgB}},accounts=ACCOUNT_KEYS.map(accountIdentity)
  const state={kind:'sahha-synthetic-messages-v1',generation,organisationId:orgA},saved=[],clients=[],calls=[]
  const model={conversation:null,message:null,notification:null,conversations:0,messages:0,notifications:0,delivers:true}
  const page=items=>({items,totalPages:items.length?1:0})
  const communicationPage=content=>({content,totalPages:content.length?1:0})
  const createClient=()=>{
    const client={key:null,org:null,cookies:new Map(),disposed:false,
      async login(account){this.key=account.key;this.cookies.set('test','memory-only');return {userId:account.id}},
      async select(org){this.org=org;return {activeOrganisationId:org}},
      dispose(){this.disposed=true;this.cookies.clear()},
      async request(route,method='GET',body,expected=[200]) {
        calls.push({key:this.key,route,method,body:structuredClone(body),expected:[...expected]})
        let status=200,result={}
        if(route==='/api/v1/auth/logout') {
          if(model.failLogout===this.key) throw new Error('Synthetic logout failure')
          status=204
        } else if(route==='/api/v1/organisations/memberships') result=[]
        else if(route.startsWith('/api/v1/conversations')) {
          const denied=!this.key || this.key==='patient'?401:!['doctorA','doctorB','unrelatedDoctor'].includes(this.key)?403
            :this.org!==orgA || this.key==='unrelatedDoctor'?404:null
          if(denied) status=denied
          else if(route==='/api/v1/conversations?size=100') result=communicationPage(model.conversation?[model.conversation]:[])
          else if(route==='/api/v1/conversations' && method==='POST') {
            assert.equal(body.subject,SUBJECT);assert.equal(body.patientRegistrationId,null)
            assert.equal(body.conversationRequestId,saved.at(-1).conversationRequestId,'Save command before create')
            if(!model.conversation) {
              model.conversation={id:randomUUID(),organisationId:orgA,subject:SUBJECT,createdByUserId:accountIdentity('doctorA').id,
                patientRegistrationId:null,patientAccessGranted:false,participants:['doctorA','doctorB'].map(key=>({userId:accountIdentity(key).id})),unreadCount:0}
              model.conversations++
            }
            result=model.conversation;status=201
            if(model.dropConversation){model.dropConversation=false;throw new Error('Lost conversation response')}
          } else if(route.endsWith('/messages?size=100') || route.endsWith('/messages') && method==='GET') result=communicationPage(model.message?[model.message]:[])
          else if(route.endsWith('/messages') && method==='POST') {
            assert.equal(body.messageRequestId,saved.at(-1).messageRequestId,'Save command before send')
            if(model.message && model.message.body!==body.body) status=409
            else {
              if(!model.message) {
                model.message={id:randomUUID(),conversationId:model.conversation.id,senderUserId:accountIdentity('doctorA').id,
                  senderDisplayName:'Synthetic Doctor',body:body.body,sentAt:'2026-09-17T12:00:00Z'};model.messages++
                if(model.delivers) {
                  model.notification={id:randomUUID(),notificationType:'MESSAGE_RECEIVED',resourceType:'CONVERSATION',resourceId:model.conversation.id,
                    appointmentStatus:null,appointmentStartsAt:null,appointmentEndsAt:null,appointmentTimeZone:null,appointmentLocationLabel:null,
                    resourceVersion:1,eventOccurredAt:'2026-09-17T12:00:00Z',createdAt:'2026-09-17T12:00:01Z',read:false,readAt:null}
                  if(model.leakNotification) model.notification.body=MESSAGE
                  model.notifications++
                }
              }
              result=model.message;status=201
              if(model.dropMessage){model.dropMessage=false;throw new Error('Lost message response')}
            }
          } else if(route.endsWith('/read')) result={...model.conversation,unreadCount:0}
          else result=model.conversation
        } else if(route.startsWith('/api/v1/notifications')) {
          if(route.includes('?')) result=page(this.key==='doctorB' && this.org===orgA && model.notification?[model.notification]:[])
          else if(this.key!=='doctorB' || this.org!==orgA || !route.includes(model.notification?.id)) status=404
          else {
            model.notification.read=true;model.notification.readAt??='2026-09-17T12:01:00Z';result=model.notification
            if(model.dropRead){model.dropRead=false;throw new Error('Lost read response')}
          }
        } else assert.fail(`Unexpected synthetic route ${route}`)
        if(!expected.includes(status)) throw Object.assign(new Error(`Synthetic unexpected ${status}`),{status})
        return structuredClone(result)
      }}
    clients.push(client);return client
  }
  return {state,seed,model,calls,clients,saved,run:()=>seedMessages(accounts,seed,state,async value=>saved.push(structuredClone(value)),createClient,async()=>{})}
}

test('message journal validates generation, organisation, UUIDs and command dependencies',()=>{
  const f=fixture();assert.equal(validateMessageState(f.state,f.seed.generation,f.seed.organisations.A),f.state)
  for(const change of [{generation:randomUUID()},{organisationId:randomUUID()},{unknown:true},{conversationId:randomUUID()},
    {messageRequestId:randomUUID()},{notificationId:randomUUID()},{conversationRequestId:'../outside'}]) {
    assert.throws(()=>validateMessageState({...f.state,...change},f.seed.generation,f.seed.organisations.A))
  }
})
test('Communication content pages are distinct from Notification items pages and cannot be truncated silently',()=>{
  assert.deepEqual(conversationItems({content:[],totalPages:0}),[])
  assert.deepEqual(conversationItems({content:[{id:'synthetic'}],totalPages:1}),[{id:'synthetic'}])
  for(const page of [{items:[],totalPages:0},{content:[],totalPages:2},{content:[],totalPages:-1},{content:[]}]) {
    assert.throws(()=>conversationItems(page),/content-page contract/)
  }
})
test('first and repeated messaging seed retain one conversation, immutable message and private notification',async()=>{
  const f=fixture(),first=await f.run(),message=structuredClone(f.model.message),notification=structuredClone(f.model.notification)
  const repeated=await f.run()
  for(const key of ['conversationId','messageId','notificationId']) assert.equal(repeated[key],first[key])
  assert.equal(f.model.conversations,1);assert.equal(f.model.messages,1);assert.equal(f.model.notifications,1)
  assert.deepEqual(f.model.message,message);assert.deepEqual(f.model.notification,notification)
  assert.ok(first.checks>=40);assert.equal(first.kafkaInboxVerified,true)
  assert.equal(first.realtimeStreamVerified,false);assert.equal(first.patientContextVerified,false)
  assert.ok(f.clients.every(client=>client.disposed && client.cookies.size===0))
})
for(const [flag,label] of [['dropConversation','conversation'],['dropMessage','message'],['dropRead','read']]) {
  test(`committed ${label} response loss recovers with the original command and no duplicates`,async()=>{
    const f=fixture();f.model[flag]=true
    await assert.rejects(f.run(),new RegExp(`Lost ${label} response`))
    const id=f.model.conversation.id
    const result=await f.run();assert.equal(result.conversationId,id)
    assert.equal(f.model.conversations,1);assert.equal(f.model.messages,1);assert.equal(f.model.notifications,1)
    assert.ok(f.clients.every(client=>client.disposed))
  })
}
test('changed existing message is refused before sending or overwriting content',async()=>{
  const f=fixture();await f.run();f.model.message.body='User-authored content'
  const start=f.calls.length
  await assert.rejects(f.run(),/immutable message fixture/)
  assert.ok(!f.calls.slice(start).some(call=>call.method==='POST' && call.route.endsWith('/messages')))
  assert.equal(f.model.message.body,'User-authored content')
})
test('changed conversation scope is refused before a recovery command',async()=>{
  const f=fixture();await f.run();f.model.conversation.patientAccessGranted=true
  const start=f.calls.length
  await assert.rejects(f.run(),/participant-only conversation/)
  assert.ok(!f.calls.slice(start).some(call=>call.method==='POST' && call.route==='/api/v1/conversations'))
})
test('missing Kafka projection cannot produce a success report and polling is bounded',async()=>{
  const f=fixture();f.model.delivers=false
  await assert.rejects(f.run(),/real Kafka recipient inbox projection/)
  assert.equal(f.calls.filter(call=>call.route==='/api/v1/notifications?size=100').length,60)
  assert.ok(f.clients.every(client=>client.disposed))
})
test('notification content leakage fails the metadata-only gate',async()=>{
  const f=fixture();f.model.leakNotification=true
  await assert.rejects(f.run(),/metadata-only/)
  assert.ok(f.clients.every(client=>client.disposed))
})

test('context-free patients, staff roles and unrelated doctors require distinct exact denials',async()=>{
  const f=fixture();await f.run()
  for(const [key,status] of [['patient',401],['receptionist',403],['orgAdmin',403],['unrelatedDoctor',404]]) {
    const denied=f.calls.filter(call=>call.key===key && call.route.startsWith('/api/v1/conversations/'))
    assert.equal(denied.length,3)
    assert.ok(denied.every(call=>call.expected.length===1 && call.expected[0]===status))
    assert.deepEqual(denied.map(call=>call.method),['GET','GET','POST'])
  }
})
test('foreign context fails before login and logout failure still disposes every client',async()=>{
  const foreign=fixture();foreign.state.organisationId=randomUUID()
  await assert.rejects(foreign.run(),/journal identity/);assert.equal(foreign.clients.length,0)
  const f=fixture();f.model.failLogout='doctorA'
  await assert.rejects(f.run(),/every client discarded/)
  assert.ok(f.clients.every(client=>client.disposed && client.cookies.size===0))
})
