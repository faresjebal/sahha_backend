// Small, dependency-free contracts for the real browser acceptance runner.
export const BROWSER_ORIGIN='http://127.0.0.1:5173'
export const BROWSER_SUBJECT='Synthetic live browser delivery demonstration'
export const BROWSER_MESSAGE='Synthetic live browser message only. No patient data or clinical access.'
export const browserCheck=(condition,label)=>{if(!condition) throw new Error(`Synthetic browser refused: ${label}`)}
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const fields=['kind','generation','organisationId','conversationRequestId','conversationId','messageRequestId','messageId',
  'liveMessageId','liveNotificationId','liveVerifiedAt','recoveredAt','abandonedMessageRequestIds','messageReconciledAt']
export function validateBrowserSeed(seed,generation) {
  browserCheck(seed?.generation===generation && uuid.test(generation) && uuid.test(seed.organisations?.A)
    && uuid.test(seed.organisations?.B) && seed.organisations.A!==seed.organisations.B,'seed provenance')
}
export function validateBrowserState(state,generation,organisationId) {
  browserCheck(uuid.test(generation) && uuid.test(organisationId) && state?.kind==='sahha-synthetic-browser-messages-v1'
    && state.generation===generation && state.organisationId===organisationId,'journal scope')
  browserCheck(Object.keys(state).every(key=>fields.includes(key)),'unknown journal field')
  for(const [key,value] of Object.entries(state)) {
    if(key.endsWith('Id')) browserCheck(uuid.test(value),'journal identifier')
    if(key.endsWith('At')) browserCheck(typeof value==='string' && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value)),'journal timestamp')
  }
  if(state.conversationId) browserCheck(state.conversationRequestId,'conversation command missing')
  if(state.messageRequestId) browserCheck(state.conversationId,'message conversation missing')
  for(const key of ['messageId','liveMessageId','liveNotificationId']) if(state[key]) browserCheck(state.messageRequestId,'message command missing')
  if(state.messageId && state.liveMessageId) browserCheck(state.messageId===state.liveMessageId,'changed live message identity')
  if(state.liveVerifiedAt) browserCheck(state.messageId && state.liveMessageId===state.messageId && state.liveNotificationId,'live evidence missing')
  if(state.recoveredAt) browserCheck(state.liveVerifiedAt,'recovery requires a verified live run')
  if(state.abandonedMessageRequestIds) browserCheck(Array.isArray(state.abandonedMessageRequestIds)
    && state.abandonedMessageRequestIds.length>0 && state.abandonedMessageRequestIds.length<=16
    && state.abandonedMessageRequestIds.every(value=>typeof value==='string' && uuid.test(value) && value!==state.messageRequestId)
    && new Set(state.abandonedMessageRequestIds).size===state.abandonedMessageRequestIds.length
    && state.messageReconciledAt,'invalid uncommitted-command reconciliation')
  if(state.messageReconciledAt) browserCheck(state.abandonedMessageRequestIds,'reconciliation command history missing')
  return state
}
// Explicit operator-only recovery after a fresh process start and an authorised
// empty-history read. Retain the abandoned ID; never discard a committed message
// or positive delivery evidence, and never silently change a pending command.
export function reconcileEmptyMessage(state,history) {
  browserCheck(Array.isArray(history) && history.length===0 && state.messageRequestId
    && !state.messageId && !state.liveMessageId && !state.liveNotificationId && !state.liveVerifiedAt,
  'reconciliation requires authoritative empty history and no positive delivery evidence')
  state.abandonedMessageRequestIds=[...(state.abandonedMessageRequestIds??[]),state.messageRequestId]
  state.messageReconciledAt=new Date().toISOString()
  delete state.messageRequestId
  validateBrowserState(state,state.generation,state.organisationId)
}
export function browserApiUrl(route) {
  browserCheck(typeof route==='string' && route.startsWith('/api/v1/') && !/[\\\r\n#]/.test(route)
    && !route.split(/[/?]/).includes('..') && !/%2e|%2f|%5c/i.test(route),'Gateway-only browser API path')
  return BROWSER_ORIGIN+route
}
// Known design-only font requests remain blocked: this gate sends no traffic
// outside its frontend/Gateway. Never excuse an off-origin fetch/XHR/API call.
export function isBlockedDesignFont(url,resourceType) {
  const value=new URL(url)
  return value.protocol==='https:' && !value.username && !value.password && !value.port
    && ((resourceType==='stylesheet' && value.hostname==='fonts.googleapis.com' && value.pathname==='/css2')
      || (resourceType==='font' && value.hostname==='fonts.gstatic.com' && value.pathname.startsWith('/s/')))
}
export function messageCommand(value,state) {
  browserCheck(value && Object.keys(value).sort().join(',')==='body,messageRequestId' && value.body===BROWSER_MESSAGE
    && uuid.test(value.messageRequestId),'unexpected browser message command')
  browserCheck(!state.messageRequestId || state.messageRequestId===value.messageRequestId,'changed pending message command; recover existing data first')
  return value.messageRequestId
}
// Test-only control: construct genuine native sockets unchanged; expose only a
// scoped close operation. No HTTP responses, frames, subscriptions or credentials
// are injected. This lets acceptance distinguish socket recovery from page reload.
export function installMessageSocketControl(browserWindow=window) {
  const NativeSocket=browserWindow.WebSocket,sockets=new Set()
  browserWindow.WebSocket=new Proxy(NativeSocket,{construct(target,args){
    const socket=Reflect.construct(target,args)
    if(new URL(String(args[0]),browserWindow.location.href).pathname==='/api/v1/conversations/ws') {
      sockets.add(socket);socket.addEventListener('close',()=>sockets.delete(socket),{once:true})
    }
    return socket
  }})
  Object.defineProperty(browserWindow,'__sahhaCloseMessageStreams',{value:()=>{
    let closed=0
    for(const socket of sockets)if(socket.readyState===NativeSocket.OPEN){socket.close(1000,'Synthetic reconnect acceptance');closed++}
    return closed
  }})
}
const notificationFields=['id','notificationType','resourceType','resourceId','appointmentStatus','appointmentStartsAt','appointmentEndsAt',
  'appointmentTimeZone','appointmentLocationLabel','resourceVersion','eventOccurredAt','createdAt','read','readAt']
export function verifyBrowserNotification(value,state) {
  browserCheck(value?.resourceType==='CONVERSATION' && value.resourceId===state.conversationId && value.notificationType==='MESSAGE_RECEIVED'
    && uuid.test(value.id) && Object.keys(value).every(key=>notificationFields.includes(key))
    && ['appointmentStatus','appointmentStartsAt','appointmentEndsAt','appointmentTimeZone','appointmentLocationLabel'].every(key=>value[key]===null)
    && Number.isInteger(value.resourceVersion) && value.resourceVersion>=1,'private notification contract')
  if(state.liveNotificationId) browserCheck(value.id===state.liveNotificationId,'changed notification identity')
  return value.id
}
// Never retain CONNECT credentials or arbitrary server ERROR details. Only the
// protocol command, subscription destination and MESSAGE JSON leave this parser.
export class BrowserStompStream {
  buffer=''
  push(chunk) {
    this.buffer+=String(chunk);browserCheck(this.buffer.length<=65536,'oversized STOMP frame')
    const frames=[];let end
    while((end=this.buffer.indexOf('\0'))>=0) {
      const raw=this.buffer.slice(0,end).replace(/^[\r\n]+/,'');this.buffer=this.buffer.slice(end+1)
      const split=raw.indexOf('\n\n');if(split<0) continue
      const lines=raw.slice(0,split).split('\n'),command=lines[0]
      let payload
      if(command==='MESSAGE') {
        try{payload=JSON.parse(raw.slice(split+2))}catch{throw new Error('Synthetic browser refused: invalid STOMP JSON; body suppressed')}
      }
      frames.push({command,...(command==='SUBSCRIBE'?{destination:lines.find(line=>line.startsWith('destination:'))?.slice(12)}:{}),
        ...(command==='MESSAGE'?{payload}:{})})
    }
    if(/^[\r\n]*$/.test(this.buffer)) this.buffer=''
    return frames
  }
}
