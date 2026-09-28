#!/usr/bin/env node
// Single owned platform session; retained synthetic data, no reset/cloud/Docker.
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {currentManifest,generationPath,lock,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {validateAccounts} from './synthetic-demo.mjs'
import {seedScheduling,validateWorkflowState} from './synthetic-workflow.mjs'
import {seedClinical,validateClinicalState} from './synthetic/clinical-seed.mjs'
import {seedFiles,validateFileState,FILE_NAME,UNSELECTED_FILE_NAME} from './synthetic/file-seed.mjs'
import {seedMessages,validateMessageState} from './synthetic/message-seed.mjs'
import {validateCareState,sharedCareGate} from './synthetic-shared-care.mjs'
import {withClients} from './synthetic-referrals.mjs'
import {validateNotificationState,runReferralBrowserGate} from './synthetic-referral-notifications.mjs'
import {validatePatientNotificationState,runPatientBrowserGate} from './synthetic-patient-notifications.mjs'
import {validateAttachmentState,runAttachmentBrowserGate} from './synthetic-message-attachments.mjs'
import {validateJointState,jointTreatmentGate} from './synthetic/joint-treatment-gate.mjs'
import {batchNames,withSyntheticPlatform} from './synthetic/platform-runtime.mjs'
import {inputFingerprint,requireSetupStopped} from './synthetic/setup-preflight.mjs'

const check=(value,label)=>{if(!value)throw new Error('Full-app acceptance refused: '+label)}
async function saveJson(file,value) {
  try{await readPrivate(file);await privateFile(file,JSON.stringify(value,null,2),'w')}
  catch(error){if(error.code!=='ENOENT')throw error;await privateFile(file,JSON.stringify(value,null,2))}
}
export async function main(args) {
  if(args.length!==1 || args[0]!=='verify')throw new Error('Use: node scripts/synthetic-acceptance.mjs verify')
  await lock(async()=>{
    await requireSetupStopped()
    const manifest=await currentManifest(),directory=generationPath(manifest.id)
    const read=async name=>parsePrivateJson(await readPrivate(path.join(directory,name)))
    const accounts=validateAccounts(await read('demo-accounts.json'),manifest.id),seed=await read('demo-seed-report.json')
    check(seed.generation===manifest.id,'retained seed generation')
    const fingerprint=await inputFingerprint(),stages=[]
    const report={kind:'sahha-full-app-acceptance-v1',generation:manifest.id,inputFingerprint:fingerprint,
      startedAt:new Date().toISOString(),status:'IN_PROGRESS',stages,cleanupVerified:false,
      installedMachineOnly:true,retainedData:true,syntheticScanOnly:true}
    const checkpoint=()=>saveJson(path.join(directory,'full-app-acceptance-report.json'),report)
    const stage=async(name,action)=>{
      console.log('Full-app acceptance: '+name)
      const result=await action()
      check(Number.isInteger(result?.checks) && result.checks>0,'stage verification evidence')
      stages.push({name,checks:result.checks,verifiedAt:new Date().toISOString()});await checkpoint()
      console.log('Full-app acceptance: '+name+' passed '+result.checks+' assertions.')
      return result
    }
    const journal=async(name,validate,...scope)=>{
      const value=validate(await read(name),manifest.id,...scope)
      return {value,save:async(next=value)=>{validate(next,manifest.id,...scope);await saveJson(path.join(directory,name),next)}}
    }
    await checkpoint()
    try {
      const names=[...batchNames('foundation'),'clinical-service','communication-service','notification-service','file-service']
      await withSyntheticPlatform(manifest,names,async()=>{
        const scheduling=await journal('workflow-scheduling.json',validateWorkflowState)
        const scheduled=await stage('registry / scheduling / patient visibility',()=>seedScheduling(accounts.accounts,seed,scheduling.value,scheduling.save))
        const clinical=await journal('workflow-clinical.json',validateClinicalState,scheduled.appointmentId)
        const signed=await stage('clinical / immutable corrections / Kafka completion',()=>seedClinical(accounts.accounts,seed,scheduled,clinical.value,clinical.save))
        for(const [suffix,filename] of [['files',FILE_NAME],['files-unselected',UNSELECTED_FILE_NAME]]) {
          const files=await journal('workflow-'+suffix+'.json',validateFileState,signed.consultationId)
          await stage('private '+suffix,()=>seedFiles(accounts.accounts,seed,signed,files.value,files.save,undefined,undefined,{filename}))
        }
        const messages=await journal('workflow-messages.json',validateMessageState,seed.organisations.A)
        await stage('messaging / Kafka inbox / isolation',()=>seedMessages(accounts.accounts,seed,messages.value,messages.save))
        const care=await journal('workflow-shared-care.json',validateCareState)
        await stage('shared-care / expiry / revocation recovery',()=>withClients(accounts.accounts,seed,c=>sharedCareGate(c,care.value,care.save)))
        const referrals=await journal('workflow-referral-notifications.json',validateNotificationState)
        await stage('referral browser / inbox recovery',()=>withClients(accounts.accounts,seed,c=>runReferralBrowserGate(manifest,accounts.accounts,seed,referrals.value,referrals.save,c)))
        const patient=await journal('workflow-patient-notifications.json',validatePatientNotificationState)
        await stage('patient browser / booking retry / inbox recovery',()=>withClients(accounts.accounts,seed,c=>runPatientBrowserGate(manifest,accounts.accounts,patient.value,patient.save,c)))
        const attachments=await journal('workflow-message-attachments.json',validateAttachmentState)
        await stage('attachment browser / private bytes recovery',()=>withClients(accounts.accounts,seed,c=>runAttachmentBrowserGate(manifest,accounts.accounts,seed,attachments.value,attachments.save,c)))
        let joint
        try{joint=await read('workflow-joint-treatment.json')}catch(error){
          if(error.code!=='ENOENT')throw error
          joint={kind:'sahha-joint-treatment-v1',generation:manifest.id,organisationId:seed.organisations.A,
            registrationId:scheduled.registrationId,sourceConsultationId:signed.consultationId,appointments:{}}
        }
        validateJointState(joint,manifest.id)
        check(joint.organisationId===seed.organisations.A && joint.registrationId===scheduled.registrationId
          && joint.sourceConsultationId===signed.consultationId,'joint-treatment retained scope')
        const save=async()=>{validateJointState(joint,manifest.id);await saveJson(path.join(directory,'workflow-joint-treatment.json'),joint)}
        await save()
        await stage('both doctors new treatment / browser history and documents',()=>withClients(accounts.accounts,seed,c=>jointTreatmentGate(manifest,accounts.accounts,seed,joint,save,c)))
      },{eventDelivery:true,syntheticScan:true})
      check(await inputFingerprint()===fingerprint,'inputs changed during acceptance')
      report.status='PASSED';report.finishedAt=new Date().toISOString()
    } catch(error) {
      report.status='FAILED';report.failedAfter=stages.length;await checkpoint();throw error
    } finally {
      await requireSetupStopped();report.cleanupVerified=true;await checkpoint()
    }
    console.log('Full-app acceptance: '+stages.length+' stages, '+stages.reduce((sum,s)=>sum+s.checks,0)+' assertions passed; owned services/helpers stopped.')
    console.log('Existing notification live evidence is retained recovery, not fabricated new deliveries. Clean-machine/CI/cloud and production malware scanning are separate gates.')
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main(process.argv.slice(2)).catch(error=>{
  console.error(error.message);process.exitCode=1
})
