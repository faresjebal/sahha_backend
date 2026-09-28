import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {test} from 'node:test'
import {main} from './synthetic-browser.mjs'
import {BROWSER_MESSAGE,BrowserStompStream,browserApiUrl,installMessageSocketControl,isBlockedDesignFont,messageCommand,reconcileEmptyMessage,validateBrowserSeed,validateBrowserState,verifyBrowserNotification} from './synthetic/browser-messages.mjs'
const fixture=()=>({kind:'sahha-synthetic-browser-messages-v1',generation:randomUUID(),organisationId:randomUUID()})
const commandState=()=>({...fixture(),conversationRequestId:randomUUID(),conversationId:randomUUID(),messageRequestId:randomUUID()})
test('browser gate rejects unknown/destructive commands before reading private state',async()=>{
  for(const args of [[],['reset'],['live','--force'],['recover','--reset'],['seed']])await assert.rejects(main(args),/Use: node scripts\/synthetic-browser/)
})
test('browser journal and seed must match exact generation and distinct organisations',()=>{
  const state=fixture(),seed={generation:state.generation,organisations:{A:state.organisationId,B:randomUUID()}}
  validateBrowserSeed(seed,state.generation);validateBrowserState(state,state.generation,state.organisationId)
  for(const change of [{generation:randomUUID()},{organisationId:randomUUID()},{password:'injected'},{messageId:randomUUID()},
    {conversationId:randomUUID()},{messageRequestId:randomUUID()},{liveVerifiedAt:new Date().toISOString()},{recoveredAt:new Date().toISOString()}]) {
    assert.throws(()=>validateBrowserState({...state,...change},state.generation,state.organisationId))
  }
  for(const organisations of [{A:seed.organisations.A,B:seed.organisations.A},{A:seed.organisations.A,B:'../outside'},{}]) {
    assert.throws(()=>validateBrowserSeed({...seed,organisations},state.generation))
  }
})
test('live checkpoints require the actual message and notification; recovery cannot invent evidence',()=>{
  const state={...commandState(),messageId:randomUUID(),liveNotificationId:randomUUID(),liveVerifiedAt:new Date().toISOString()}
  assert.throws(()=>validateBrowserState(state,state.generation,state.organisationId))
  state.liveMessageId=state.messageId;validateBrowserState(state,state.generation,state.organisationId)
  assert.throws(()=>validateBrowserState({...state,liveMessageId:randomUUID()},state.generation,state.organisationId))
  assert.throws(()=>validateBrowserState({...state,liveNotificationId:'../redirect'},state.generation,state.organisationId))
})
test('browser API paths cannot escape the frontend Gateway proxy or inject credentials',()=>{
  assert.equal(browserApiUrl('/api/v1/conversations?size=100'),'http://127.0.0.1:5173/api/v1/conversations?size=100')
  for(const route of ['https://outside.example/api/v1/','//outside/api/v1/','/api/v1/../admin','/api/v1/%2e%2e/admin','/api/v1/x#token','/api/v1/x\nHeader: secret'])assert.throws(()=>browserApiUrl(route))
})
test('socket-only test control preserves native construction and closes only genuine open message sockets',()=>{
  const instances=[]
  class NativeSocket {
    static OPEN=1
    constructor(...args){this.args=args;this.readyState=1;this.listeners={};instances.push(this)}
    addEventListener(name,listener){this.listeners[name]=listener}
    close(code,reason){this.readyState=3;this.closed={code,reason};this.listeners.close?.()}
    send(){assert.fail('The acceptance controller must never inject a frame')}
  }
  const browserWindow={WebSocket:NativeSocket,location:{href:'http://127.0.0.1:5173/doctor/messages'}}
  installMessageSocketControl(browserWindow)
  const messages=new browserWindow.WebSocket('ws://127.0.0.1:5173/api/v1/conversations/ws',['v12.stomp'])
  const notifications=new browserWindow.WebSocket('ws://127.0.0.1:5173/api/v1/notifications/ws',['v12.stomp'])
  assert.ok(messages instanceof NativeSocket);assert.equal(browserWindow.WebSocket.OPEN,NativeSocket.OPEN)
  assert.deepEqual(messages.args,['ws://127.0.0.1:5173/api/v1/conversations/ws',['v12.stomp']])
  assert.equal(browserWindow.__sahhaCloseMessageStreams(),1);assert.equal(messages.closed.code,1000)
  assert.equal(notifications.readyState,1);assert.equal(browserWindow.__sahhaCloseMessageStreams(),0);assert.equal(instances.length,2)
})
test('only known design-font resource requests are expected blocked assets, never off-origin APIs',()=>{
  assert.equal(isBlockedDesignFont('https://fonts.googleapis.com/css2?family=Outfit','stylesheet'),true)
  assert.equal(isBlockedDesignFont('https://fonts.gstatic.com/s/outfit/v1/font.woff2','font'),true)
  for(const [url,type] of [['https://fonts.googleapis.com/css2','fetch'],['https://fonts.gstatic.com/s/api','xhr'],
    ['https://fonts.googleapis.com/api/v1/','stylesheet'],['http://fonts.googleapis.com/css2','stylesheet'],
    ['https://fonts.googleapis.com:8443/css2','stylesheet'],['https://user:pass@fonts.googleapis.com/css2','stylesheet'],
    ['https://fonts.googleapis.com.evil.test/css2','stylesheet'],['http://127.0.0.1:8086/api/v1/conversations','fetch']]) {
    assert.equal(isBlockedDesignFont(url,type),false)
  }
})
test('only the real synthetic UI command can be journalled and ambiguous retries cannot silently change IDs',()=>{
  const state=commandState(),command={messageRequestId:state.messageRequestId,body:BROWSER_MESSAGE}
  assert.equal(messageCommand(command,state),state.messageRequestId)
  for(const change of [{body:'unexpected text'},{messageRequestId:randomUUID()},{patientId:randomUUID()},{messageRequestId:'invalid'}])assert.throws(()=>messageCommand({...command,...change},state))
})
test('explicit empty-history reconciliation retains the abandoned ID and cannot replace committed or observed messages',()=>{
  const state=commandState(),pending=state.messageRequestId
  for(const history of [[{id:randomUUID()}],null])assert.throws(()=>reconcileEmptyMessage({...state},history))
  for(const key of ['messageId','liveMessageId','liveNotificationId','liveVerifiedAt'])assert.throws(()=>reconcileEmptyMessage({...state,[key]:randomUUID()},[]))
  reconcileEmptyMessage(state,[])
  assert.deepEqual(state.abandonedMessageRequestIds,[pending]);assert.equal(state.messageRequestId,undefined)
  validateBrowserState(state,state.generation,state.organisationId)
  assert.throws(()=>reconcileEmptyMessage(state,[]))
  assert.throws(()=>validateBrowserState({...state,messageRequestId:pending},state.generation,state.organisationId))
  assert.throws(()=>validateBrowserState({...state,abandonedMessageRequestIds:[pending,pending]},state.generation,state.organisationId))
})
test('STOMP observer handles fragments/heartbeats and never retains CONNECT credentials or ERROR details',()=>{
  const stream=new BrowserStompStream()
  assert.deepEqual(stream.push('\nCONNECT\nX-XSRF-TOKEN:private-secret\n\n\0'),[{command:'CONNECT'}])
  assert.deepEqual(stream.push('SUBSCRIBE\ndestination:/user/queue/messages\n\n\0'),[{command:'SUBSCRIBE',destination:'/user/queue/messages'}])
  assert.deepEqual(stream.push('MESSAGE\ncontent-type:application/json\n\n{"messageType":'),[])
  assert.deepEqual(stream.push('"TEST"}\0\nERROR\nmessage:private-error\n\nprivate body\0'),[{command:'MESSAGE',payload:{messageType:'TEST'}},{command:'ERROR'}])
  assert.equal(stream.buffer,'')
})
test('malformed or oversized STOMP input fails without echoing bodies',()=>{
  assert.throws(()=>new BrowserStompStream().push('MESSAGE\n\nprivate-body\0'),error=>error.message.includes('body suppressed')&&!error.message.includes('private-body'))
  assert.throws(()=>new BrowserStompStream().push('x'.repeat(65537)),/oversized STOMP frame/)
})
test('message notifications are metadata-only and retain their exact conversation and notification identity',()=>{
  const state=commandState(),value={id:randomUUID(),notificationType:'MESSAGE_RECEIVED',resourceType:'CONVERSATION',resourceId:state.conversationId,
    appointmentStatus:null,appointmentStartsAt:null,appointmentEndsAt:null,appointmentTimeZone:null,appointmentLocationLabel:null,resourceVersion:1}
  assert.equal(verifyBrowserNotification(value,state),value.id);state.liveNotificationId=value.id
  for(const change of [{body:BROWSER_MESSAGE},{patientId:randomUUID()},{resourceId:randomUUID()},{id:randomUUID()},{appointmentLocationLabel:'unexpected'},
    {notificationType:'APPOINTMENT_REQUESTED'},{resourceVersion:0}])assert.throws(()=>verifyBrowserNotification({...value,...change},state))
})
