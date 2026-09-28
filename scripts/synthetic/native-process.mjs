// Short-lived, ownership-checked Windows helpers. Never adopts a listening port.
import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { closeSync, openSync } from 'node:fs'
import { unlink } from 'node:fs/promises'
import path from 'node:path'
import { isSystemConsoleHost, tcpOpen } from '../sahha.mjs'
import { confinedDirectory, privateFile, runPrivate } from '../synthetic-db.mjs'
import { isolatedEnvironment } from './environment.mjs'

const delay = ms => new Promise(resolve => setTimeout(resolve,ms))
const tokens = text => text?.match(/"[^"]*"|[^\s]+/g)?.map(token => token.replace(/^"|"$/g,'')) ?? []
export function ownsNative(record, processInfo) {
  if (typeof processInfo?.ExecutablePath !== 'string' || !path.isAbsolute(record.executable)
    || processInfo.ExecutablePath.toLowerCase() !== record.executable.toLowerCase()) return false
  const command = tokens(processInfo.CommandLine)
  return record.identity.length > 0 && record.identity.every(value => command.includes(value))
}
export function validateListeners(ports,listeners) {
  if (!Array.isArray(listeners) || listeners.some(value => !['127.0.0.1','::1'].includes(value.LocalAddress) || !ports.includes(value.LocalPort))
    || ports.some(port => !listeners.some(value => value.LocalPort === port))) {
    const safe = Array.isArray(listeners) ? listeners.map(value => /^[0-9a-f:.]+$/i.test(value.LocalAddress) && Number.isInteger(value.LocalPort) ? `${value.LocalAddress}:${value.LocalPort}` : 'invalid').join(', ') : 'invalid'
    throw new Error(`Native helper listeners are not exactly the allowed loopback ports. Observed: ${safe}`)
  }
}
export async function requireOwnedListeners(record) {
  const [processInfo] = await inspect(record.pid)
  if (!ownsNative(record,processInfo)) throw new Error('Ready native helper ownership mismatch')
  const result = await runPrivate('powershell.exe',['-NoLogo','-NoProfile','-NonInteractive','-Command',
    `$ErrorActionPreference='Stop'; @(Get-NetTCPConnection -State Listen -OwningProcess ${record.pid} | Select-Object LocalAddress,LocalPort) | ConvertTo-Json -Compress`])
  const values = JSON.parse(result.stdout)
  validateListeners(record.ports,Array.isArray(values) ? values : [values])
}
async function inspect(pid, children = false) {
  if (!Number.isSafeInteger(pid) || pid < 1) throw new Error('Invalid native helper PID')
  const result = await runPrivate('powershell.exe',['-NoLogo','-NoProfile','-NonInteractive','-Command',
    `$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process -Filter '${children ? 'ParentProcessId' : 'ProcessId'} = ${pid}' | Select-Object ProcessId,Name,ExecutablePath,CommandLine) | ConvertTo-Json -Compress`])
  if (!result.stdout.trim()) return []
  const value = JSON.parse(result.stdout)
  return Array.isArray(value) ? value : [value]
}
async function stop(record, shutdown) {
  const [current] = await inspect(record.pid)
  if (current) {
    if (!ownsNative(record,current)) throw new Error('Native helper ownership changed; shutdown refused')
    const children = await inspect(record.pid,true)
    if (children.some(child => !isSystemConsoleHost(child))) throw new Error('Unexpected native helper child; shutdown refused')
    if (shutdown) await shutdown()
    else process.kill(record.pid,'SIGTERM')
  }
  for (let count = 0; count < 20 && (await inspect(record.pid)).length; count++) await delay(250)
  if ((await inspect(record.pid)).length || (await Promise.all(record.ports.map(port => tcpOpen(port)))).some(Boolean)) throw new Error('Native helper remains active; ownership record retained')
  await unlink(record.statePath).catch(error => { if (error.code !== 'ENOENT') throw error })
  console.log(`${record.name}: owned native helper stopped.`)
}
export async function withNativeProcess({name,executable,args,ports,directory,identity,ready,shutdown,environment={}}, action) {
  if (process.platform !== 'win32') throw new Error('Native helper process guards currently require Windows')
  if (!/^[a-z-]+$/.test(name) || !path.isAbsolute(executable) || !ports.length || !identity.length || identity.some(value => !args.includes(value))) throw new Error('Invalid native helper specification')
  for (const port of ports) if (await tcpOpen(port)) throw new Error(`${name}: occupied port; existing process will not be adopted`)
  await confinedDirectory(directory,true)
  const runId = randomUUID()
  const record = {name,executable,identity,ports,statePath:path.join(directory,`${name}-${runId}.json`)}
  const output = openSync(path.join(directory,`${name}-${runId}.log`),'wx',0o600)
  let child
  try {
    child = spawn(executable,args,{cwd:directory,env:{...isolatedEnvironment(),...environment},windowsHide:true,detached:true,stdio:['ignore',output,output]})
    await new Promise((resolve,reject) => { child.once('spawn',resolve); child.once('error',reject) })
    record.pid = child.pid
    await privateFile(record.statePath,JSON.stringify(record))
    console.log(`${name}: waiting for isolated readiness.`)
    const deadline = Date.now()+90000
    while (Date.now() < deadline && child.exitCode === null && child.signalCode === null) {
      if (await ready()) { await requireOwnedListeners(record); return await action() }
      await delay(1000)
    }
    throw new Error(`${name}: readiness failed; private log retained`)
  } finally {
    closeSync(output); child?.unref()
    if (record.pid) await stop(record,shutdown)
  }
}
