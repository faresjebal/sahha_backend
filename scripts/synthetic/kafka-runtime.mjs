import { randomBytes, randomUUID } from 'node:crypto'
import { access, readdir, realpath } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { ROOT, javaExecutable, tcpOpen } from '../sahha.mjs'
import { confinedDirectory, generationPath, parsePrivateJson, privateFile, readPrivate, runPrivate } from '../synthetic-db.mjs'
import { isolatedEnvironment } from './app-runtime.mjs'
import { withNativeProcess } from './native-process.mjs'

export const KAFKA_PORTS = Object.freeze([19092,19093])
export function kafkaConfiguration(directory) {
  if (!path.isAbsolute(directory) || /[\r\n]/.test(directory)) throw new Error('Invalid Kafka directory')
  return `process.roles=broker,controller\nnode.id=1\ncontroller.quorum.bootstrap.servers=127.0.0.1:19093\nlisteners=PLAINTEXT://127.0.0.1:19092,CONTROLLER://127.0.0.1:19093\nadvertised.listeners=PLAINTEXT://127.0.0.1:19092,CONTROLLER://127.0.0.1:19093\ninter.broker.listener.name=PLAINTEXT\ncontroller.listener.names=CONTROLLER\nlistener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT\nlog.dirs=${path.join(directory,'data').replaceAll('\\','/')}\nnum.network.threads=2\nnum.io.threads=2\nnum.partitions=1\nnum.recovery.threads.per.data.dir=1\noffsets.topic.replication.factor=1\noffsets.topic.num.partitions=1\ntransaction.state.log.replication.factor=1\ntransaction.state.log.min.isr=1\ntransaction.state.log.num.partitions=1\nshare.coordinator.state.topic.replication.factor=1\nshare.coordinator.state.topic.min.isr=1\nlog.cleaner.enable=false\nlog.segment.bytes=16777216\nlog.retention.hours=168\nauto.create.topics.enable=false\ngroup.initial.rebalance.delay.ms=0\n`
}
export async function exactConfig(file,contents) {
  try { if (await readPrivate(file) !== contents) throw new Error('Isolated infrastructure configuration changed; refusing launch') }
  catch (error) { if (error.code !== 'ENOENT') throw error; await privateFile(file,contents) }
}
// Retained synthetic demonstrations only: Windows can lock mapped indexes during
// retention deletion and shut down the whole broker. Keep data until an explicit
// operator-owned generation cleanup; never reuse this retention policy in Azure.
export function retainedKafkaConfiguration(directory) {
  return kafkaConfiguration(directory)+'log.retention.ms=-1\n'
}
export async function prepareKafka(generation) {
  if (!process.env.SAHHA_KAFKA_HOME) throw new Error('Set SAHHA_KAFKA_HOME to the extracted Kafka 4.3.1 directory')
  const kafkaHome = await realpath(process.env.SAHHA_KAFKA_HOME)
  await access(path.join(kafkaHome,'libs','kafka-clients-4.3.1.jar'))
  await access(path.join(kafkaHome,'libs','kafka_2.13-4.3.1.jar'))
  const java = await javaExecutable(), directory = await confinedDirectory(path.join(generationPath(generation),'kafka'),true)
  const data = await confinedDirectory(path.join(directory,'data'),true), file = path.join(directory,'kafka.json')
  let manifest
  try { manifest = parsePrivateJson(await readPrivate(file)) }
  catch (error) {
    if (error.code !== 'ENOENT') throw error
    if ((await readdir(data)).length) throw new Error('Existing unmarked Kafka storage is never formatted')
    manifest = {generation,clusterId:randomBytes(16).toString('base64url'),version:'4.3.1'}
    await privateFile(file,JSON.stringify(manifest))
  }
  if (manifest.generation !== generation || manifest.version !== '4.3.1' || !/^[A-Za-z0-9_-]{22}$/.test(manifest.clusterId)) throw new Error('Kafka manifest mismatch')
  // A separate file preserves the old exact configuration and all broker data.
  const config = path.join(directory,'server-retained.properties'), logging = path.join(directory,'log4j2.properties')
  await exactConfig(config,retainedKafkaConfiguration(directory))
  await exactConfig(logging,'status=error\nname=SyntheticKafka\nappender.stderr.type=Console\nappender.stderr.name=STDERR\nappender.stderr.target=SYSTEM_ERR\nappender.stderr.layout.type=PatternLayout\nappender.stderr.layout.pattern=%level %logger %msg%n\nrootLogger.level=warn\nrootLogger.appenderRef.stderr.ref=STDERR\n')
  const base = ['-Xms64m','-Xmx256m',`-Dlog4j2.configurationFile=${pathToFileURL(logging).href}`,'-cp',path.join(kafkaHome,'libs','*')]
  let formatted = false
  try {
    const metadata = await readPrivate(path.join(data,'meta.properties'))
    if (!metadata.split(/\r?\n/).includes(`cluster.id=${manifest.clusterId}`) || !metadata.split(/\r?\n/).includes('node.id=1')) throw new Error('Kafka storage identity mismatch; never reformat')
    formatted = true
  } catch (error) { if (error.code !== 'ENOENT') throw error }
  if (!formatted) {
    if ((await readdir(data)).length) throw new Error('Partial or foreign Kafka storage retained; format refused')
    for (const port of KAFKA_PORTS) if (await tcpOpen(port)) throw new Error('Kafka port occupied; format refused')
    await runPrivate(java,[...base,'kafka.tools.StorageTool','format','--standalone','-t',manifest.clusterId,'-c',config],{env:isolatedEnvironment(),timeout:45000})
  }
  return {java,directory,config,base,clusterId:manifest.clusterId}
}
export async function withSyntheticKafka(generation,action) {
  const kafka = await prepareKafka(generation), marker = `-Dsahha.synthetic.kafka=${randomUUID()}`
  return withNativeProcess({name:'kafka',executable:kafka.java,args:[...kafka.base,marker,'kafka.Kafka',kafka.config],ports:KAFKA_PORTS,directory:kafka.directory,
    identity:[marker,'kafka.Kafka',kafka.config],ready:async () => (await Promise.all(KAFKA_PORTS.map(port => tcpOpen(port)))).every(Boolean)},() => action(kafka))
}
export async function verifyKafka(kafka) {
  const result = await runPrivate(kafka.java,[...kafka.base,path.join(ROOT,'scripts','synthetic','KafkaProbe.java'),kafka.clusterId],{env:isolatedEnvironment(),timeout:120000})
  if (!result.stdout.includes('SYNTHETIC_KAFKA_OK topics=9 acknowledged-roundtrip=true')) throw new Error('Missing Kafka verification evidence')
  console.log('Synthetic Kafka: exact cluster identity, nine single-replica topics and acknowledged producer/consumer roundtrip passed.')
}
