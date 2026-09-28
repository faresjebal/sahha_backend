import {createHash} from 'node:crypto'
import {createReadStream} from 'node:fs'
import {access,lstat,readdir,readFile,realpath} from 'node:fs/promises'
import {createRequire} from 'node:module'
import path from 'node:path'
import {isDeepStrictEqual} from 'node:util'
import {ROOT,SERVICES,jarFor,javaExecutable,tcpOpen} from '../sahha.mjs'
import {PORT,STATE,SYNTHETIC_DEPENDENCY_PORTS,currentManifest,readPrivate,runPrivate} from '../synthetic-db.mjs'
import {checkPrerequisiteVersions,requireSetup as check} from './setup-contracts.mjs'

export const SETUP_PORTS=Object.freeze([...new Set([PORT,5173,...SERVICES.map(value=>value.port),...SYNTHETIC_DEPENDENCY_PORTS])])
export async function requireSetupStopped(probe=tcpOpen) {
  const occupied=[]
  for(const port of SETUP_PORTS)if(await probe(port))occupied.push(port)
  check(!occupied.length,`required ports are occupied (${occupied.join(',')}); no process will be adopted or stopped`)
}
export async function optionalManifest() {
  try{await readPrivate(path.join(STATE,'active.json'))}
  catch(error){if(error.code==='ENOENT')return null;throw error}
  // A present but broken pointer is never treated as a fresh installation.
  return currentManifest()
}
async function sourceFiles(directory) {
  const result=[]
  for(const entry of await readdir(directory,{withFileTypes:true})) {
    const file=path.join(directory,entry.name)
    check(!entry.isSymbolicLink(),'source symlinks/junctions are not accepted by setup fingerprinting')
    if(entry.isDirectory())result.push(...await sourceFiles(file))
    else if(entry.isFile())result.push(file)
  }
  return result
}
export async function inputFingerprint() {
  const hash=createHash('sha256'),globalSources=[path.join(ROOT,'pom.xml'),path.join(ROOT,'session-security','pom.xml'),
    ...await sourceFiles(path.join(ROOT,'session-security','src','main'))]
  const files=[...globalSources,path.join(ROOT,'frontend','package.json'),path.join(ROOT,'frontend','package-lock.json'),
    path.join(ROOT,'frontend','index.html'),path.join(ROOT,'frontend','vite.config.ts'),
    ...await sourceFiles(path.join(ROOT,'frontend','src')),...await sourceFiles(path.join(ROOT,'scripts')),
    path.join(ROOT,'auth-service','src','synthetic','SeedSyntheticAccounts.java')]
  for(const service of SERVICES) {
    const artifact=await jarFor(service),sources=[...globalSources,path.join(ROOT,service.name,'pom.xml'),
      ...await sourceFiles(path.join(ROOT,service.name,'src','main'))]
    const built=(await lstat(artifact)).mtimeMs
    for(const file of sources)check((await lstat(file)).mtimeMs<=built,`${service.name} has newer source/configuration; package it before setup`)
    files.push(artifact,...sources)
  }
  for(const file of [...new Set(files)].sort()) {
    hash.update(path.relative(ROOT,file).replaceAll('\\','/')+'\0')
    for await(const chunk of createReadStream(file))hash.update(chunk)
    hash.update('\0')
  }
  return hash.digest('hex')
}
export async function preflight() {
  check(process.platform==='win32','only Windows native tooling is verified')
  for(const name of ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH','NODE_OPTIONS'])check(!process.env[name],`unset ${name} before isolated setup`)
  await requireSetupStopped()
  const manifest=await optionalManifest(),java=await javaExecutable()
  const javaVersion=(await runPrivate(java,['-version'])).stderr.match(/version "(21\.[^"]+)"/)?.[1]
  const pgBin=await realpath(process.env.SAHHA_PG_BIN || manifest?.pgBin || 'C:\\Program Files\\PostgreSQL\\18\\bin')
  if(manifest)check(pgBin.toLowerCase()===manifest.pgBin.toLowerCase(),'configured PostgreSQL binaries differ from the retained generation')
  const postgres=[]
  for(const name of ['initdb','pg_ctl','psql','postgres']) {
    const result=await runPrivate(path.join(pgBin,name+'.exe'),['--version'])
    postgres.push(result.stdout.match(/\(PostgreSQL\) (18\.\S+)/)?.[1]??'unknown')
  }
  const redis=await realpath(process.env.SAHHA_REDIS_EXE || 'C:\\Program Files\\Memurai\\memurai.exe')
  check(path.basename(redis).toLowerCase()==='memurai.exe','installed Memurai binary is required')
  await access(path.join(path.dirname(redis),'memurai-cli.exe'))
  check(process.env.SAHHA_KAFKA_HOME && process.env.SAHHA_SEAWEED_EXE,'set SAHHA_KAFKA_HOME and SAHHA_SEAWEED_EXE to the approved installed binaries')
  const kafka=await realpath(process.env.SAHHA_KAFKA_HOME)
  for(const name of ['kafka-clients-4.3.1.jar','kafka_2.13-4.3.1.jar'])await access(path.join(kafka,'libs',name))
  const storage=await realpath(process.env.SAHHA_SEAWEED_EXE)
  const storageVersion=(await runPrivate(storage,['version'])).stdout.match(/\b4\.41\b/)?.[0]
  const versions={platform:process.platform,node:process.version,java:javaVersion,postgres,kafka:'4.3.1',storage:storageVersion}
  checkPrerequisiteVersions(versions)
  const frontend=path.join(ROOT,'frontend'),pkg=JSON.parse(await readFile(path.join(frontend,'package.json'),'utf8')),
    pinned=JSON.parse(await readFile(path.join(frontend,'package-lock.json'),'utf8'))
  for(const section of ['dependencies','devDependencies'])check(isDeepStrictEqual(pkg[section],pinned.packages?.['']?.[section]),'frontend manifest/lock mismatch; restore a consistent lockfile')
  for(const name of Object.keys({...pkg.dependencies,...pkg.devDependencies})) {
    const installed=JSON.parse(await readFile(path.join(frontend,'node_modules',name,'package.json'),'utf8'))
    check(installed.version===pinned.packages?.[`node_modules/${name}`]?.version,'frontend dependencies do not match the lockfile; run npm ci')
  }
  const requireFrontend=createRequire(path.join(frontend,'package.json'))
  check(typeof requireFrontend('playwright-core').chromium?.launch==='function','installed Chromium driver is unavailable')
  await access('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe')
  return {generation:manifest?.id??null,versions,inputFingerprint:await inputFingerprint()}
}
