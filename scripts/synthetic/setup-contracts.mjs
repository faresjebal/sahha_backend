import {createHash} from 'node:crypto'

export const SETUP_STEPS=Object.freeze(['database','migrations','redis','kafka','storage','identities',
  'scheduling','clinical','files','files-unselected','messages','referral-clinical','referral-files',
  'referral-revocation','browser-live','browser-recovery'])
export const requireSetup=(condition,message)=>{if(!condition)throw new Error(`Synthetic setup refused: ${message}`)}
export function parseSetupArgs(args) {
  requireSetup(args.length===1 && ['check','run'].includes(args[0]),'Use: node scripts/synthetic-setup.mjs check|run')
  return args[0]
}
export function checkPrerequisiteVersions({platform,node,java,postgres,kafka,storage}) {
  requireSetup(platform==='win32','only the verified Windows-native process guards are supported')
  requireSetup(/^v?22\./.test(node),'Node 22 is required')
  requireSetup(/^21\./.test(java),'Java 21 is required')
  requireSetup(Array.isArray(postgres) && postgres.length===4 && postgres.every(value=>/^18\./.test(value)),'all four PostgreSQL binaries must be version 18')
  requireSetup(kafka==='4.3.1' && storage==='4.41','use the approved Kafka 4.3.1 and SeaweedFS 4.41')
}
export function assertRetained(before,after) {
  requireSetup(Object.entries(before).every(([key,value])=>after[key]===value),'an existing resource or command identity changed or disappeared')
}
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
// Fixed, identifier-only paths. Never traverse arbitrary clinical content,
// account credentials, consent text or transport/download tokens.
export const IDENTITY_PATHS=Object.freeze({
  'demo-seed-report.json':['organisations.*','departments.*','memberships.*'],
  'workflow-scheduling.json':['organisationId','appointmentId','booking.bookingRequestId','booking.patientRegistrationId','booking.doctorUserId'],
  'workflow-clinical.json':['appointmentId','consultationId'],
  'workflow-files.json':['consultationId','fileId'],
  'workflow-files-unselected.json':['consultationId','fileId'],
  'workflow-messages.json':['organisationId','conversationRequestId','conversationId','messageRequestId','messageId','notificationId'],
  'workflow-referrals.json':['organisationId','consultationId','patientId','selectedFileId','unselectedFileId','diagnosisId','medicationId',
    'conversationRequestId','conversationId','messageRequestId','messageId','referralRequestId','referralId','expiryRequestId','expiryId'],
  'workflow-browser-messages.json':['organisationId','conversationRequestId','conversationId','messageRequestId','messageId','liveMessageId','liveNotificationId'],
})
export function identityDigests(file,value,generation) {
  requireSetup(Object.hasOwn(IDENTITY_PATHS,file) && value?.generation===generation,'identity journal provenance')
  const result={}
  for(const selector of IDENTITY_PATHS[file]) {
    const parts=selector.split('.'),wild=parts.at(-1)==='*'
    const selected=(wild?parts.slice(0,-1):parts).reduce((object,key)=>object?.[key],value)
    if(selected===undefined)continue
    for(const [key,id] of wild?Object.entries(selected):[[selector,selected]]) {
      requireSetup(typeof id==='string' && uuid.test(id),'invalid retained resource identifier')
      result[`${file}:${wild?parts.slice(0,-1).join('.')+'.'+key:key}`]=createHash('sha256').update(id).digest('hex')
    }
  }
  return result
}

// Subcommands own their finally-based shutdown. Always verify quiescence even
// when a stage rejects; never advance or claim success after an unclean stage.
export async function executeSetup({run,quiescent,snapshot,checkpoint,announce=()=>{}}) {
  const completed=[],retained=await snapshot()
  for(const step of SETUP_STEPS) {
    announce(step,completed.length+1,SETUP_STEPS.length)
    try {
      await quiescent()
      await checkpoint({status:'running',completed:[...completed],activeStep:step})
      try {await run(step)} finally {await quiescent()}
      const current=await snapshot()
      assertRetained(retained,current);Object.assign(retained,current)
      completed.push(step)
      await checkpoint({status:'running',completed:[...completed],activeStep:null})
    } catch {
      await checkpoint({status:'failed',completed:[...completed],activeStep:step})
      throw new Error(`Synthetic setup stopped at ${step}; prior data retained, raw diagnostics suppressed`)
    }
  }
  await checkpoint({status:'complete',completed:[...completed],activeStep:null})
  return completed
}
