// Opt-in isolated native helpers; no installers, Windows services or data reset.
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createServer } from 'node:net'
import path from 'node:path'
import { test } from 'node:test'
import { tcpOpen } from './sahha.mjs'
import { currentManifest, generationPath, lock, privateFile, readPrivate, requireAppsStopped } from './synthetic-db.mjs'
import { REDIS_PORT, verifyRedis, withSyntheticRedis } from './synthetic/redis-runtime.mjs'
import { KAFKA_PORTS, verifyKafka, withSyntheticKafka } from './synthetic/kafka-runtime.mjs'
import { STORAGE_PORTS, verifyStorage, withSyntheticStorage } from './synthetic/storage-runtime.mjs'

const ALL_PORTS = [REDIS_PORT,...KAFKA_PORTS,...STORAGE_PORTS]
const hash = value => createHash('sha256').update(value).digest('hex')
async function stopped() { for (const port of ALL_PORTS) assert.equal(await tcpOpen(port),false,`Native helper ${port} must stop`) }

test('native Redis/Kafka/storage restart, preservation, occupied-port/config refusal and cleanup', {skip:process.env.SAHHA_SYNTHETIC_INFRA_TEST !== 'true',timeout:600000},async () => {
  assert.equal(process.platform,'win32')
  await lock(async () => {
    await requireAppsStopped(); await stopped()
    const manifest = await currentManifest(), directory = generationPath(manifest.id)
    const components = [
      {name:'redis',port:REDIS_PORT,start:withSyntheticRedis,verify:verifyRedis,file:path.join(directory,'redis','redis.conf'),marker:path.join(directory,'redis','redis.json')},
      {name:'kafka',port:KAFKA_PORTS[0],start:withSyntheticKafka,verify:verifyKafka,file:path.join(directory,'kafka','server.properties'),marker:path.join(directory,'kafka','data','meta.properties')},
      {name:'storage',port:STORAGE_PORTS[0],start:withSyntheticStorage,verify:verifyStorage,file:path.join(directory,'storage','filer.toml'),marker:path.join(directory,'storage','storage.json')}
    ]
    for (const component of components) {
      await component.start(manifest.id,component.verify); await stopped()
      const first = hash(await readPrivate(component.marker))
      await component.start(manifest.id,async resource => {
        const result = await component.verify(resource)
        if (component.name === 'storage') assert.equal(result.sentinelCreated,false,'Existing storage generation sentinel survives restart')
      }); await stopped()
      assert.equal(hash(await readPrivate(component.marker)),first,'Cluster/credential identity is retained')
      const occupied = createServer()
      await new Promise((resolve,reject) => { occupied.once('error',reject); occupied.listen(component.port,'127.0.0.1',resolve) })
      try {
        await assert.rejects(component.start(manifest.id,() => assert.fail('Cannot adopt an occupied port')),/occupied port/)
        assert.equal(occupied.listening,true)
      } finally { await new Promise(resolve => occupied.close(resolve)) }
      const contents = await readPrivate(component.file)
      try {
        await privateFile(component.file,contents+'\n# Unexpected edit\n','w')
        await assert.rejects(component.start(manifest.id,() => assert.fail('Cannot launch changed config')),/configuration changed/)
        await stopped()
      } finally { await privateFile(component.file,contents,'w') }
      console.log(`Native infrastructure: ${component.name} repeat, identity preservation, occupied-port and changed-config refusals passed.`)
    }
    // Callback failure must still stop the exact helper; no reset/flush is used.
    await assert.rejects(withSyntheticRedis(manifest.id,async () => { throw new Error('Injected scoped callback failure') }),/Injected scoped callback failure/)
    await stopped()
    console.log('Native infrastructure: all eleven helper ports released, including injected-failure cleanup; persisted data retained.')
  })
})
