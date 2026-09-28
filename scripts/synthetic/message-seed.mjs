import { randomUUID } from 'node:crypto'
import { isDeepStrictEqual } from 'node:util'
import { accountIdentity, pageItems, waitOrganisationRoute } from '../synthetic-demo.mjs'
import { GatewayClient } from './gateway-client.mjs'

const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
const check=(condition,label)=>{if(!condition) throw new Error(`Synthetic messaging refused: ${label}; existing conversations are not replaced`)}
export const SUBJECT='Synthetic doctor collaboration demonstration'
export const MESSAGE='Synthetic demonstration only. This message contains no patient information and grants no clinical access.'
export function conversationItems(page) {
  check(Array.isArray(page?.content) && Number.isInteger(page.totalPages) && page.totalPages>=0 && page.totalPages<=1,
    'unexpected Communication content-page contract or additional pages')
  return page.content
}
const notificationFields=['id','notificationType','resourceType','resourceId','appointmentStatus','appointmentStartsAt','appointmentEndsAt',
  'appointmentTimeZone','appointmentLocationLabel','resourceVersion','eventOccurredAt','createdAt','read','readAt']
export function validateMessageState(state,generation,organisationId) {
  check(uuid.test(generation) && uuid.test(organisationId) && state?.kind==='sahha-synthetic-messages-v1'
    && state.generation===generation && state.organisationId===organisationId,'journal identity')
  check(Object.keys(state).every(key=>['kind','generation','organisationId','conversationRequestId','messageRequestId',
    'conversationId','messageId','notificationId'].includes(key)),'unknown journal fields')
  for(const key of ['conversationRequestId','messageRequestId','conversationId','messageId','notificationId']) {
    if(state[key]!==undefined) check(uuid.test(state[key]),'journal identifiers')
  }
  if(state.conversationId) check(state.conversationRequestId,'missing conversation command')
  if(state.messageRequestId || state.messageId) check(state.conversationId,'missing conversation identity')
  if(state.messageId) check(state.messageRequestId,'missing message command')
  if(state.notificationId) check(state.messageId,'missing notification source')
  return state
}
async function readReady(client,route,pause) {
  for(let attempt=0;attempt<60;attempt++) {
    try{return await client.request(route)}
    catch(error){if(error.status!==503 || attempt===59) throw error;await pause()}
  }
}
export async function seedMessages(accounts,seed,state,save,createClient=()=>new GatewayClient(),pause=()=>new Promise(resolve=>setTimeout(resolve,1000))) {
  validateMessageState(state,seed.generation,seed.organisations?.A)
  check(uuid.test(seed.organisations?.B) && seed.organisations.A!==seed.organisations.B,'distinct organisations')
  const keys=['doctorA','doctorB','unrelatedDoctor','receptionist','orgAdmin','patient'],byKey=Object.fromEntries(accounts.map(value=>[value.key,value]))
  for(const key of keys) check(byKey[key]?.id===accountIdentity(key).id,'account identity')
  const clients=Object.fromEntries(keys.map(key=>[key,createClient()])),anonymous=createClient()
  let checks=0
  const verified=(condition,label)=>{check(condition,label);checks++}
  try {
    for(const key of keys) verified((await clients[key].login(byKey[key])).userId===byKey[key].id,'authenticated identity')
    await waitOrganisationRoute(clients.doctorA)
    for(const key of keys.filter(key=>key!=='patient')) verified((await clients[key].select(seed.organisations.A)).activeOrganisationId===seed.organisations.A,'active organisation')
    const sender=clients.doctorA,recipient=clients.doctorB
    const conversations=conversationItems(await readReady(sender,'/api/v1/conversations?size=100',pause)).filter(value=>value.subject===SUBJECT)
    check(conversations.length<=1,'ambiguous existing fixture')
    if(!state.conversationRequestId) {
      check(conversations.length===0,'existing conversation without its recovery command')
      state.conversationRequestId=randomUUID();await save(state)
    }
    const creation={conversationRequestId:state.conversationRequestId,recipientUserId:byKey.doctorB.id,subject:SUBJECT,patientRegistrationId:null}
    const verifyConversation=value=>verified(uuid.test(value?.id) && value.organisationId===state.organisationId && value.subject===SUBJECT
      && value.createdByUserId===byKey.doctorA.id && value.patientRegistrationId===null && value.patientAccessGranted===false
      && value.participants?.length===2 && ['doctorA','doctorB'].every(key=>value.participants.some(participant=>participant.userId===byKey[key].id)),'participant-only conversation fixture')
    if(conversations.length) {
      verifyConversation(conversations[0])
      if(state.conversationId) check(conversations[0].id===state.conversationId,'changed conversation identity')
    } else check(!state.conversationId,'journalled conversation disappeared')
    const conversation=await sender.request('/api/v1/conversations','POST',creation,[201])
    verifyConversation(conversation)
    if(state.conversationId) check(state.conversationId===conversation.id,'retained conversation identity')
    if(conversations.length) check(conversations[0].id===conversation.id,'recovery command owns existing conversation')
    state.conversationId=conversation.id;await save(state)
    const route=`/api/v1/conversations/${conversation.id}`
    verified((await sender.request('/api/v1/conversations','POST',creation,[201])).id===conversation.id,'conversation replay')
    verifyConversation(await recipient.request(route))
    const messages=conversationItems(await sender.request(`${route}/messages?size=100`))
    check(messages.length<=1,'unexpected additional messages')
    const verifyMessage=value=>verified(uuid.test(value?.id) && value.conversationId===conversation.id && value.senderUserId===byKey.doctorA.id
      && value.body===MESSAGE,'immutable message fixture')
    if(messages.length) {
      verifyMessage(messages[0]);check(state.messageRequestId,'existing message without its recovery command')
      if(state.messageId) check(messages[0].id===state.messageId,'changed message identity')
    } else check(!state.messageId,'journalled message disappeared')
    if(!state.messageRequestId){state.messageRequestId=randomUUID();await save(state)}
    const command={messageRequestId:state.messageRequestId,body:MESSAGE}
    const message=await sender.request(`${route}/messages`,'POST',command,[201]);verifyMessage(message)
    if(state.messageId) check(message.id===state.messageId,'retained message identity')
    if(messages.length) check(message.id===messages[0].id,'message recovery command')
    state.messageId=message.id;await save(state)
    verified((await sender.request(`${route}/messages`,'POST',command,[201])).id===message.id,'message replay')
    const persisted=conversationItems(await recipient.request(`${route}/messages?size=100`))
    verified(persisted.length===1 && persisted[0].id===message.id,'single recipient-visible message');verifyMessage(persisted[0])
    await sender.request(`${route}/messages`,'POST',{...command,body:'Changed synthetic replay must be refused.'},[409]);checks++
    verified(isDeepStrictEqual(conversationItems(await recipient.request(`${route}/messages?size=100`)),persisted),'conflicting replay cannot change immutable message')
    verified((await recipient.request(`${route}/read`,'POST')).unreadCount===0,'participant read marker')
    const inboxRoute='/api/v1/notifications?size=100'
    const matching=items=>items.filter(value=>value.resourceType==='CONVERSATION' && value.resourceId===conversation.id)
    let notification
    for(let attempt=0;attempt<60;attempt++) {
      const matches=matching(pageItems(await readReady(recipient,inboxRoute,pause)))
      check(matches.length<=1,'duplicate message notifications')
      if(matches.length){notification=matches[0];break}
      await pause()
    }
    verified(notification?.notificationType==='MESSAGE_RECEIVED' && uuid.test(notification.id),'real Kafka recipient inbox projection')
    verified(Object.keys(notification).every(key=>notificationFields.includes(key)) && notification.resourceVersion>=1
      && ['appointmentStatus','appointmentStartsAt','appointmentEndsAt','appointmentTimeZone','appointmentLocationLabel'].every(key=>notification[key]===null),'metadata-only message notification')
    if(state.notificationId) check(notification.id===state.notificationId,'retained notification identity')
    state.notificationId=notification.id;await save(state)
    for(const key of ['doctorA','unrelatedDoctor','receptionist','orgAdmin']) {
      verified(matching(pageItems(await clients[key].request(inboxRoute))).length===0,'private recipient inbox')
      await clients[key].request(`/api/v1/notifications/${notification.id}/read`,'POST',undefined,[404]);checks++
    }
    const read=await recipient.request(`/api/v1/notifications/${notification.id}/read`,'POST')
    verified(read.id===notification.id && read.read===true,'owned notification read')
    const storedRead=matching(pageItems(await recipient.request(inboxRoute)))[0]
    await recipient.request(`/api/v1/notifications/${notification.id}/read`,'POST')
    verified(storedRead?.read===true && isDeepStrictEqual(matching(pageItems(await recipient.request(inboxRoute)))[0],storedRead),'idempotent notification read')
    for(const key of ['unrelatedDoctor','receptionist','orgAdmin','patient']) {
      // Messaging principals require an active organisation. A context-free
      // patient session is rejected during authentication, before role policy.
      const denied=key==='patient'?401:key==='unrelatedDoctor'?404:403
      await clients[key].request(route,'GET',undefined,[denied]);checks++
      await clients[key].request(`${route}/messages`,'GET',undefined,[denied]);checks++
      await clients[key].request(`${route}/messages`,'POST',{messageRequestId:randomUUID(),body:MESSAGE},[denied]);checks++
    }
    await anonymous.request(route,'GET',undefined,[401]);checks++
    await recipient.select(seed.organisations.B)
    await recipient.request(route,'GET',undefined,[404]);checks++
    await recipient.request(`/api/v1/notifications/${notification.id}/read`,'POST',undefined,[404]);checks++
    await recipient.select(seed.organisations.A)
    verified(conversationItems(await recipient.request(`${route}/messages?size=100`)).length===1,'denied sends did not append messages')
    return {generation:state.generation,verifiedAt:new Date().toISOString(),conversationId:conversation.id,messageId:message.id,
      notificationId:notification.id,checks,kafkaInboxVerified:true,realtimeStreamVerified:false,patientContextVerified:false}
  } finally {
    let failed=false
    for(const client of [...Object.values(clients),anonymous]) {
      try{if(client.cookies.size) await client.request('/api/v1/auth/logout','POST',undefined,[204])}
      catch{failed=true}finally{client.dispose()}
    }
    if(failed) throw new Error('Synthetic messaging logout failed; every client discarded')
  }
}
