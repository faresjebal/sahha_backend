import { randomBytes } from 'node:crypto'
import path from 'node:path'
import { SERVICES } from '../sahha.mjs'
import { generationPath, parsePrivateJson, privateFile, readPrivate, requireAppsStopped, withCluster } from '../synthetic-db.mjs'
import { withSyntheticApps } from './app-runtime.mjs'
import { withSyntheticRedis, verifyRedis } from './redis-runtime.mjs'
import { withSyntheticKafka, verifyKafka } from './kafka-runtime.mjs'
import { withSyntheticStorage, verifyStorage } from './storage-runtime.mjs'

const BASE = ['discovery-server','config-server','auth-service','organisation-service','api-gateway']
export const BATCHES = Object.freeze({
  foundation:[...BASE,'patient-service','scheduling-service'],
  clinical:[...BASE,'clinical-service','file-service'],
  collaboration:[...BASE,'communication-service','notification-service'],
  audit:[...BASE,'audit-service']
})
export function batchNames(name) {
  if (!Object.hasOwn(BATCHES,name)) throw new Error('Unknown isolated application batch')
  return [...BATCHES[name]]
}
async function appSecrets(generation) {
  const file = path.join(generationPath(generation),'app-secrets.json')
  let value
  try { value = parsePrivateJson(await readPrivate(file)) }
  catch (error) {
    if (error.code !== 'ENOENT') throw error
    value = {generation,hmacSecret:randomBytes(32).toString('hex')}
    await privateFile(file,JSON.stringify(value))
  }
  if (value.generation !== generation || !/^[a-f0-9]{64}$/.test(value.hmacSecret ?? '')) throw new Error('Invalid private application secret manifest')
  return value
}
// The caller holds the generation lock. Dependencies are stopped in reverse
// nesting order even if application startup, API checks or callbacks fail.
export async function withSyntheticPlatform(manifest,names,action,{eventDelivery=false,syntheticScan=false}={}) {
  if (!Array.isArray(names) || names.some(name => !SERVICES.some(service => service.name === name)) || BASE.some(name => !names.includes(name))) throw new Error('Invalid isolated platform selection')
  await requireAppsStopped()
  const secrets = await appSecrets(manifest.id)
  return withCluster(manifest,() => withSyntheticRedis(manifest.id,async redis => {
    await verifyRedis(redis)
    const runtime = {platform:true,hmacSecret:secrets.hmacSecret,redisPassword:redis.password,eventDelivery,syntheticScan}
    const apps = () => withSyntheticApps(manifest.id,names,() => action(runtime),runtime)
    const storage = () => names.includes('file-service') ? withSyntheticStorage(manifest.id,async resource => {
      await verifyStorage(resource); runtime.storage=resource.manifest; return apps()
    }) : apps()
    return eventDelivery ? withSyntheticKafka(manifest.id,async kafka => { await verifyKafka(kafka); return storage() }) : storage()
  }))
}
