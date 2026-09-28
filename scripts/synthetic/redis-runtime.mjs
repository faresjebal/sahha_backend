import { randomBytes, randomUUID } from 'node:crypto'
import { access, realpath } from 'node:fs/promises'
import path from 'node:path'
import { confinedDirectory, generationPath, parsePrivateJson, privateFile, readPrivate, runPrivate } from '../synthetic-db.mjs'
import { isolatedEnvironment } from './app-runtime.mjs'
import { withNativeProcess } from './native-process.mjs'

export const REDIS_PORT = 16379
export function redisConfiguration(password) {
  if (!/^[a-f0-9]{64}$/.test(password)) throw new Error('Invalid isolated Redis credential')
  return `bind 127.0.0.1\nport ${REDIS_PORT}\nprotected-mode yes\nrequirepass ${password}\nsave ""\nappendonly no\nmaxmemory 64mb\nmaxmemory-policy allkeys-lru\nloglevel warning\n`
}
export async function prepareRedis(generation) {
  const executable = await realpath(process.env.SAHHA_REDIS_EXE ?? 'C:\\Program Files\\Memurai\\memurai.exe')
  const cli = path.join(path.dirname(executable),'memurai-cli.exe')
  if (path.basename(executable).toLowerCase() !== 'memurai.exe') throw new Error('Use the installed native Memurai executable')
  await access(cli)
  const directory = await confinedDirectory(path.join(generationPath(generation),'redis'),true)
  const manifestFile = path.join(directory,'redis.json'), configFile = path.join(directory,'redis.conf')
  let manifest
  try { manifest = parsePrivateJson(await readPrivate(manifestFile)) }
  catch (error) {
    if (error.code !== 'ENOENT') throw error
    manifest = {generation,port:REDIS_PORT,password:randomBytes(32).toString('hex')}
    await privateFile(manifestFile,JSON.stringify(manifest))
  }
  if (manifest.generation !== generation || manifest.port !== REDIS_PORT) throw new Error('Redis generation/port mismatch')
  const config = redisConfiguration(manifest.password)
  try { if (await readPrivate(configFile) !== config) throw new Error('Isolated Redis configuration changed; launch refused') }
  catch (error) { if (error.code !== 'ENOENT') throw error; await privateFile(configFile,config) }
  const command = async (args,authenticated=true) => {
    const result = await runPrivate(cli,['-h','127.0.0.1','-p',String(REDIS_PORT),'--raw',...args],
      {env:{...isolatedEnvironment(),...(authenticated ? {REDISCLI_AUTH:manifest.password} : {})},timeout:5000,allowFailure:true})
    return {code:result.code,value:result.stdout.trim()}
  }
  return {directory,executable,configFile,command,password:manifest.password}
}
export async function withSyntheticRedis(generation,action) {
  const redis = await prepareRedis(generation)
  // A fresh pathname is also a unique launch marker, not only a reusable PID.
  const runConfig = path.join(redis.directory,`redis-${randomUUID()}.conf`)
  await privateFile(runConfig,redisConfiguration(redis.password))
  return withNativeProcess({name:'redis',executable:redis.executable,args:[runConfig],ports:[REDIS_PORT],directory:redis.directory,identity:[runConfig],
    ready:async () => { try { return (await redis.command(['PING'])).value === 'PONG' } catch { return false } },
    shutdown:async () => { await redis.command(['SHUTDOWN','NOSAVE']) }},() => action(redis))
}
export async function verifyRedis(redis) {
  const key = `sahha:synthetic:probe:${randomBytes(16).toString('hex')}`
  if (!(await redis.command(['PING'],false)).value.includes('NOAUTH')) throw new Error('Anonymous Redis access was not denied')
  if ((await redis.command(['SET',key,'synthetic-only','EX','30'])).value !== 'OK') throw new Error('Synthetic Redis write failed')
  try {
    if ((await redis.command(['GET',key])).value !== 'synthetic-only') throw new Error('Synthetic Redis read failed')
    const ttl = Number((await redis.command(['TTL',key])).value)
    if (!(ttl > 0 && ttl <= 30)) throw new Error('Synthetic Redis TTL failed')
  } finally { await redis.command(['DEL',key]) }
  console.log('Synthetic Redis: authentication, scoped write/read, TTL and owned-key cleanup passed.')
}
