#!/usr/bin/env node
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { currentManifest, lock, requireAppsStopped } from './synthetic-db.mjs'
import { verifyRedis, withSyntheticRedis } from './synthetic/redis-runtime.mjs'
import { verifyKafka, withSyntheticKafka } from './synthetic/kafka-runtime.mjs'
import { verifyStorage, withSyntheticStorage } from './synthetic/storage-runtime.mjs'

export async function main(args) {
  if (args.length !== 2 || args[0] !== 'verify' || !['redis','kafka','storage'].includes(args[1])) throw new Error('Use: node scripts/synthetic-infra.mjs verify redis|kafka|storage')
  await lock(async () => {
    await requireAppsStopped()
    const manifest = await currentManifest()
    if (args[1] === 'redis') await withSyntheticRedis(manifest.id,verifyRedis)
    else if (args[1] === 'kafka') await withSyntheticKafka(manifest.id,verifyKafka)
    else await withSyntheticStorage(manifest.id,verifyStorage)
  })
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2)).catch(error => {
  console.error(/^Native helper listeners are not exactly/.test(error.message) ? error.message : 'Isolated infrastructure verification failed; private diagnostics retained, no existing process adopted.'); process.exitCode=1
})
