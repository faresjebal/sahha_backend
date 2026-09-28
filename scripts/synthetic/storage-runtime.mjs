import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { readdir, realpath } from 'node:fs/promises'
import path from 'node:path'
import { ROOT, SERVICES, jarFor, javaExecutable, tcpOpen } from '../sahha.mjs'
import { confinedDirectory, generationPath, migrationRuntimePath, parsePrivateJson, privateFile, readPrivate, runPrivate } from '../synthetic-db.mjs'
import { isolatedEnvironment } from './app-runtime.mjs'
import { exactConfig } from './kafka-runtime.mjs'
import { withNativeProcess } from './native-process.mjs'

export const STORAGE_PORTS = Object.freeze([18333,19333,18081,18889,28333,29333,28081,28889])
export function validateStorageManifest(value,generation) {
  if (value?.generation !== generation || value.version !== '4.41' || value.endpoint !== 'http://127.0.0.1:18333'
    || value.bucket !== 'sahha-synthetic-files' || !/^[a-f0-9]{32}$/.test(value.accessKey ?? '') || !/^[a-f0-9]{64}$/.test(value.secretKey ?? '')) throw new Error('Invalid isolated storage manifest')
  return value
}
export function storageArguments(directory,runDirectory) {
  return [`-config_dir=${directory}`,'-logtostderr=true',`-logdir=${runDirectory}`,'server',`-dir=${path.join(directory,'data')}`,
    '-ip=127.0.0.1','-ip.bind=127.0.0.1','-master.telemetry=false','-master.port=19333','-master.port.grpc=29333','-master.volumeSizeLimitMB=64',
    '-volume.port=18081','-volume.port.grpc=28081','-volume.max=2','-filer','-filer.port=18889','-filer.port.grpc=28889',
    '-s3','-s3.ip.bind=127.0.0.1','-s3.port=18333','-s3.port.grpc=28333','-s3.port.iceberg=0','-s3.iam=false',`-s3.config=${path.join(directory,'s3.json')}`]
}
export async function prepareStorage(generation) {
  if (!process.env.SAHHA_SEAWEED_EXE) throw new Error('Set SAHHA_SEAWEED_EXE to the installed SeaweedFS 4.41 weed.exe')
  const executable = await realpath(process.env.SAHHA_SEAWEED_EXE)
  const version = await runPrivate(executable,['version'],{env:isolatedEnvironment()})
  if (!/\b4\.41\b/.test(version.stdout)) throw new Error('Synthetic storage requires verified SeaweedFS 4.41')
  const directory = await confinedDirectory(path.join(generationPath(generation),'storage'),true)
  for (const name of ['data','filer']) await confinedDirectory(path.join(directory,name),true)
  const file = path.join(directory,'storage.json')
  let manifest
  try { manifest = validateStorageManifest(parsePrivateJson(await readPrivate(file)),generation) }
  catch (error) {
    if (error.code !== 'ENOENT') throw error
    if ((await readdir(path.join(directory,'data'))).length || (await readdir(path.join(directory,'filer'))).length) throw new Error('Existing unmarked storage is never adopted')
    manifest = {generation,version:'4.41',endpoint:'http://127.0.0.1:18333',bucket:'sahha-synthetic-files',accessKey:randomBytes(16).toString('hex'),secretKey:randomBytes(32).toString('hex')}
    await privateFile(file,JSON.stringify(manifest))
  }
  const s3 = {identities:[{name:'synthetic-file-service',credentials:[{accessKey:manifest.accessKey,secretKey:manifest.secretKey}],actions:['Admin','Read','Write','List','Tagging']}]}
  await exactConfig(path.join(directory,'s3.json'),JSON.stringify(s3))
  await exactConfig(path.join(directory,'filer.toml'),`[filer.options]\nrecursive_delete = false\n[leveldb2]\nenabled = true\ndir = ${JSON.stringify(path.join(directory,'filer').replaceAll('\\','/'))}\n`)
  for (const name of ['security','notification','replication','master']) await exactConfig(path.join(directory,`${name}.toml`),'# Isolated synthetic configuration; no inherited remote integration.\n')
  const properties = path.join(directory,'client.properties')
  await exactConfig(properties,Object.entries(manifest).map(([key,value]) => `${key}=${value}\n`).join(''))
  const java = await javaExecutable(), artifact = await jarFor(SERVICES.find(service => service.name === 'file-service'))
  const fingerprint = createHash('sha256')
  for await (const chunk of createReadStream(artifact)) fingerprint.update(chunk)
  const runtime = await confinedDirectory(migrationRuntimePath(fingerprint.digest('hex')),true)
  await runPrivate(path.join(path.dirname(java),'jar.exe'),['-xf',artifact,'BOOT-INF/lib'],{cwd:runtime,env:isolatedEnvironment()})
  return {executable,directory,manifest,properties,java,runtime}
}
export async function withSyntheticStorage(generation,action) {
  const storage = await prepareStorage(generation)
  const runDirectory = await confinedDirectory(path.join(storage.directory,`run-${randomUUID()}`),true)
  const args = storageArguments(storage.directory,runDirectory)
  return withNativeProcess({name:'storage',executable:storage.executable,args,ports:STORAGE_PORTS,directory:storage.directory,
    identity:[`-logdir=${runDirectory}`,`-s3.config=${path.join(storage.directory,'s3.json')}`],
    ready:async () => {
      if (!(await Promise.all(STORAGE_PORTS.map(port => tcpOpen(port)))).every(Boolean)) return false
      try { return (await fetch(storage.manifest.endpoint,{redirect:'error',signal:AbortSignal.timeout(1500)})).status === 403 } catch { return false }
    }},() => action(storage))
}
export async function verifyStorage(storage) {
  const result = await runPrivate(storage.java,['-Xmx128m','-cp',path.join(storage.runtime,'BOOT-INF','lib','*'),path.join(ROOT,'scripts','synthetic','StorageProbe.java'),storage.manifest.generation,storage.properties],{env:isolatedEnvironment(),timeout:90000})
  if (!result.stdout.includes('SYNTHETIC_STORAGE_OK signed-roundtrip=true anonymous-denied=true wrong-key-denied=true generation-preserved=true')) throw new Error('Missing storage verification evidence')
  console.log('Synthetic storage: signed byte roundtrip, anonymous/wrong-key denial, retained generation sentinel and owned-object cleanup passed.')
  return {sentinelCreated:result.stdout.includes('SYNTHETIC_STORAGE_SENTINEL_CREATED=true')}
}
