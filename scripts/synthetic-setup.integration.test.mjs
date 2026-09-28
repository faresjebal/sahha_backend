import assert from 'node:assert/strict'
import {createHash} from 'node:crypto'
import {createServer} from 'node:net'
import path from 'node:path'
import {test} from 'node:test'
import {ROOT} from './sahha.mjs'
import {currentManifest,generationPath,lock,readPrivate,runPrivate} from './synthetic-db.mjs'
import {executeSetup} from './synthetic/setup-contracts.mjs'
import {requireSetupStopped} from './synthetic/setup-preflight.mjs'
import {verifyRedis,withSyntheticRedis} from './synthetic/redis-runtime.mjs'

test('native setup refuses foreign occupancy/concurrent commands and cleans a failed owned stage',{
  skip:process.env.SAHHA_SYNTHETIC_SETUP_TEST!=='true',timeout:180000
},async()=>{
  await lock(async()=>{
    await requireSetupStopped()
    const manifest=await currentManifest(),file=path.join(generationPath(manifest.id),'manifest.json')
    const fingerprint=()=>readPrivate(file).then(value=>createHash('sha256').update(value).digest('hex'))
    const before=await fingerprint(),occupied=createServer()
    await new Promise((resolve,reject)=>{occupied.once('error',reject);occupied.listen(15432,'127.0.0.1',resolve)})
    try {await assert.rejects(requireSetupStopped(),/occupied/);assert.equal(occupied.listening,true)}
    finally {await new Promise(resolve=>occupied.close(resolve))}
    const competitor=await runPrivate(process.execPath,[path.join(ROOT,'scripts','synthetic-db.mjs'),'status'],{allowFailure:true})
    assert.notEqual(competitor.code,0);assert.match(competitor.stderr,/operation locked/)
    const checkpoints=[],steps=[]
    await assert.rejects(executeSetup({snapshot:async()=>({generation:manifest.id}),quiescent:requireSetupStopped,
      checkpoint:async value=>checkpoints.push(value),run:async step=>{
        steps.push(step)
        if(step==='redis')await withSyntheticRedis(manifest.id,async()=>{throw new Error('Injected stage failure')})
      }}),/stopped at redis/)
    assert.deepEqual(steps,['database','migrations','redis']);assert.equal(checkpoints.at(-1).status,'failed')
    await requireSetupStopped()
    await withSyntheticRedis(manifest.id,verifyRedis);await requireSetupStopped()
    assert.equal(await fingerprint(),before)
    console.log('Native setup safety: occupied listener retained, concurrent CLI refused, failed-stage cleanup, Redis restart and unchanged generation passed.')
  })
  // The shared filesystem lock is released after the outer operation as well.
  await lock(requireSetupStopped)
})
