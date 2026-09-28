#!/usr/bin/env node
// Ordered installed-machine acceptance. No installation, reset, arbitrary
// subcommands, database adoption, Docker, external mail or cloud provisioning.
import {randomUUID} from 'node:crypto'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {main as database,currentManifest,generationPath,lock,confinedDirectory,parsePrivateJson,privateFile,readPrivate} from './synthetic-db.mjs'
import {main as identities,validateAccounts} from './synthetic-demo.mjs'
import {main as infrastructure} from './synthetic-infra.mjs'
import {main as workflow} from './synthetic-workflow.mjs'
import {main as referrals} from './synthetic-referrals.mjs'
import {main as browser} from './synthetic-browser.mjs'
import {IDENTITY_PATHS,executeSetup,identityDigests,parseSetupArgs,requireSetup as check} from './synthetic/setup-contracts.mjs'
import {inputFingerprint,optionalManifest,preflight,requireSetupStopped} from './synthetic/setup-preflight.mjs'

async function snapshot(manifest) {
  if(!manifest)return {}
  const current=await currentManifest()
  check(current.id===manifest.id,'active generation changed during the ordered operation')
  const result={generation:manifest.id},directory=generationPath(manifest.id)
  for(const name of Object.keys(IDENTITY_PATHS)) {
    let value
    try{value=parsePrivateJson(await readPrivate(path.join(directory,name)))}
    catch(error){if(error.code==='ENOENT')continue;throw error}
    Object.assign(result,identityDigests(name,value,manifest.id))
  }
  try {
    const value=validateAccounts(parsePrivateJson(await readPrivate(path.join(directory,'demo-accounts.json'))),manifest.id)
    // The account validator pins all eight identities without exposing secrets.
    for(const account of value.accounts)result[`account:${account.key}`]=account.id
  } catch(error){if(error.code!=='ENOENT')throw error}
  return result
}
export async function main(args) {
  const command=parseSetupArgs(args),prerequisites=await preflight()
  console.log('Synthetic setup preflight passed: Windows, pinned native tools, frontend lock, 12 fresh JARs and free isolated ports. No services started.')
  if(command==='check')return prerequisites
  return lock(async()=>{
    await requireSetupStopped()
    let manifest=await optionalManifest(),clean=true
    check((manifest?.id??null)===prerequisites.generation,'generation changed since preflight')
    const runId=randomUUID(),startedAt=new Date().toISOString(),initial=await snapshot(manifest)
    let reportFile
    const checkpoint=async progress=>{
      if(!manifest)return
      if(!reportFile)reportFile=path.join(await confinedDirectory(path.join(generationPath(manifest.id),'setup-runs'),true),`${runId}.json`)
      const value={kind:'sahha-ordered-native-setup-v1',generation:manifest.id,runId,startedAt,updatedAt:new Date().toISOString(),
        inputFingerprint:prerequisites.inputFingerprint,versions:prerequisites.versions,initialIdentityCount:Object.keys(initial).length,
        cleanupVerified:clean,installedMachineOnly:true,...progress}
      try{await readPrivate(reportFile);await privateFile(reportFile,JSON.stringify(value,null,2),'w')}
      catch(error){if(error.code!=='ENOENT')throw error;await privateFile(reportFile,JSON.stringify(value,null,2))}
    }
    const completed=await executeSetup({snapshot:()=>snapshot(manifest),checkpoint,
      quiescent:async()=>{clean=false;await requireSetupStopped();clean=true},
      announce:(step,index,total)=>console.log(`Synthetic setup [${index}/${total}]: ${step}`),
      run:async step=>{
        clean=false
        if(step==='database') {if(!manifest){await database(['init']);manifest=await currentManifest()}return}
        if(step==='migrations')return database(['migrate'])
        if(['redis','kafka','storage'].includes(step))return infrastructure(['verify',step])
        if(step==='identities')return identities(['seed'])
        if(['scheduling','clinical','files','files-unselected','messages'].includes(step))return workflow(['seed',step])
        if(step.startsWith('referral-'))return referrals(['seed',step==='referral-files'?'files':'clinical'])
        if(step==='browser-live')return browser(['live'])
        if(step==='browser-recovery') {
          await browser(['recover'])
          check(await inputFingerprint()===prerequisites.inputFingerprint,'code/build inputs changed during acceptance; rerun against consistent inputs')
          return
        }
        check(false,'unknown setup stage')
      }})
    console.log(`Synthetic setup: ${completed.length} ordered stages passed; retained identities verified and helpers stopped. Clean-machine/full V1 acceptance remains separate.`)
    return {completed,cleanupVerified:clean,generation:manifest.id}
  })
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main(process.argv.slice(2)).catch(error=>{
  console.error(/^Synthetic setup (refused:|stopped at )/.test(error.message)?error.message:
    'Synthetic setup failed; verify installed prerequisite paths or the last stage/private setup-runs report. Raw diagnostics suppressed; no automatic reset or recovery override.');process.exitCode=1
})
