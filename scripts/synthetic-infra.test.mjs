import assert from 'node:assert/strict'
import { test } from 'node:test'
import path from 'node:path'
import { main } from './synthetic-infra.mjs'
import { ownsNative, validateListeners } from './synthetic/native-process.mjs'
import { redisConfiguration } from './synthetic/redis-runtime.mjs'
import { kafkaConfiguration, retainedKafkaConfiguration } from './synthetic/kafka-runtime.mjs'
import { STORAGE_PORTS, storageArguments, validateStorageManifest } from './synthetic/storage-runtime.mjs'

test('infrastructure CLI rejects expansive operations before accessing state', async () => {
  for (const args of [[],['install'],['reset'],['verify','all'],['verify','redis','--force']]) await assert.rejects(main(args),/Use:/)
})
test('Redis is loopback, authenticated, nonpersistent and memory-bounded', () => {
  const config = redisConfiguration('a'.repeat(64))
  for (const text of ['bind 127.0.0.1','port 16379','protected-mode yes','requirepass ','save ""','appendonly no','maxmemory 64mb','maxmemory-policy allkeys-lru']) assert.ok(config.includes(text))
  for (const value of ['', 'unsafe\nport 6379','a'.repeat(63)]) assert.throws(() => redisConfiguration(value),/credential/)
})
test('native ownership requires exact executable and standalone generation-specific arguments', () => {
  const executable = path.resolve('runtime','memurai.exe'), config = path.resolve('synthetic','generation','redis.conf')
  const record = {executable,identity:[config]}
  assert.equal(ownsNative(record,{ExecutablePath:executable,CommandLine:`"${executable}" "${config}"`}),true)
  for (const processInfo of [null,{ExecutablePath:executable,CommandLine:`"${executable}" "${config}.other"`},{ExecutablePath:path.resolve('other.exe'),CommandLine:`"${executable}" "${config}"`}]) assert.equal(ownsNative(record,processInfo),false)
})
test('readiness requires exact process-owned loopback listeners, not merely open ports', () => {
  assert.doesNotThrow(() => validateListeners([16379],[{LocalAddress:'127.0.0.1',LocalPort:16379}]))
  for (const values of [[],[{LocalAddress:'0.0.0.0',LocalPort:16379}],[{LocalAddress:'::',LocalPort:16379}],[{LocalAddress:'127.0.0.1',LocalPort:6379}],[{LocalAddress:'127.0.0.1',LocalPort:16379},{LocalAddress:'127.0.0.1',LocalPort:9999}]]) assert.throws(() => validateListeners([16379],values),/loopback/)
})
test('Kafka configuration confines data/listeners and never auto-creates application topics', () => {
  const directory = path.resolve('synthetic','generation','kafka'), config = kafkaConfiguration(directory)
  assert.ok(config.includes(`log.dirs=${path.join(directory,'data').replaceAll('\\','/')}`))
  assert.ok(config.includes('PLAINTEXT://127.0.0.1:19092'))
  assert.ok(config.includes('CONTROLLER://127.0.0.1:19093'))
  assert.ok(config.includes('auto.create.topics.enable=false'))
  assert.ok(config.includes('offsets.topic.replication.factor=1'))
  assert.ok(config.includes('log.cleaner.enable=false'))
  assert.throws(() => kafkaConfiguration('relative'),/directory/)
})

test('retained native Kafka disables only time-based deletion without changing cluster paths or listeners', () => {
  const directory = path.resolve('synthetic','generation','kafka')
  assert.equal(retainedKafkaConfiguration(directory),kafkaConfiguration(directory)+'log.retention.ms=-1\n')
  assert.throws(() => retainedKafkaConfiguration('relative'),/directory/)
})
test('storage binds isolated HTTP/gRPC ports and private data/config paths', () => {
  const directory = path.resolve('synthetic','storage'), runDirectory = path.join(directory,'run-example')
  const args = storageArguments(directory,runDirectory)
  for (const value of ['-ip.bind=127.0.0.1','-s3.ip.bind=127.0.0.1','-master.telemetry=false','-s3.iam=false','-s3.port.iceberg=0',`-logdir=${runDirectory}`,`-config_dir=${directory}`]) assert.ok(args.includes(value))
  for (const port of STORAGE_PORTS) assert.ok(args.some(value => value.endsWith(`=${port}`)))
})
test('storage credentials cannot redirect the offline SDK probe to another endpoint or bucket', () => {
  const value = {generation:'synthetic',version:'4.41',endpoint:'http://127.0.0.1:18333',bucket:'sahha-synthetic-files',accessKey:'a'.repeat(32),secretKey:'b'.repeat(64)}
  assert.equal(validateStorageManifest(value,'synthetic'),value)
  for (const change of [{generation:'other'},{version:'4.29'},{endpoint:'https://example.com'},{bucket:'real-files'},{secretKey:'weak'}]) assert.throws(() => validateStorageManifest({...value,...change},'synthetic'),/manifest/)
})
