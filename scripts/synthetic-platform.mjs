#!/usr/bin/env node
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SERVICES } from './sahha.mjs'
import { checkService } from './health-smoke.mjs'
import { currentManifest, generationPath, lock, parsePrivateJson, privateFile, readPrivate } from './synthetic-db.mjs'
import { validateAccounts, waitOrganisationRoute } from './synthetic-demo.mjs'
import { GatewayClient } from './synthetic/gateway-client.mjs'
import { DEMO_COOKIES } from './synthetic/environment.mjs'
import { batchNames, withSyntheticPlatform } from './synthetic/platform-runtime.mjs'

export async function verifyPlatformBatch(manifest,names) {
  const results=[]
  for (const service of SERVICES.filter(value => names.includes(value.name))) {
    results.push(await checkService(service,fetch,DEMO_COOKIES.access))
    if (['discovery-server','config-server'].includes(service.name)) continue
    const response = await fetch(`http://127.0.0.1:${service.port}/actuator/info`,{redirect:'error',signal:AbortSignal.timeout(5000)})
    const info = await response.json()
    if (!response.ok || info?.sahha?.configuration?.version !== 'native-v1') throw new Error(`${service.name}: Config Server marker missing`)
  }
  const directory = generationPath(manifest.id)
  const accounts = validateAccounts(parsePrivateJson(await readPrivate(path.join(directory,'demo-accounts.json'))),manifest.id)
  const report = parsePrivateJson(await readPrivate(path.join(directory,'demo-seed-report.json')))
  if (report.generation !== manifest.id) throw new Error('Seed this generation before platform verification')
  const receptionist = new GatewayClient(), patient = new GatewayClient()
  try {
    await receptionist.login(accounts.accounts.find(account => account.key === 'receptionist'))
    await waitOrganisationRoute(receptionist)
    const context = await receptionist.select(report.organisations.A)
    if (JSON.stringify(context.organisationRoles) !== '["RECEPTIONIST"]') throw new Error('Incorrect live receptionist scope')
    await receptionist.request('/api/v1/my/doctor-profile','GET',undefined,[403])
    if (names.includes('patient-service')) {
      // Discovery cache propagation affects newly started domain routes too.
      let listed = false
      for (let attempt=0;attempt<60;attempt++) {
        try { await receptionist.request('/api/v1/patients?size=10'); listed=true; break }
        catch (error) { if (error.status !== 503) throw error; await new Promise(resolve => setTimeout(resolve,1000)) }
      }
      if (!listed) throw new Error('Patient route did not become available')
      await patient.login(accounts.accounts.find(account => account.key === 'patient'))
      await patient.request('/api/v1/patients','GET',undefined,[403])
    }
  } finally {
    let cleanupFailed=false
    for (const client of [receptionist,patient]) {
      try { if (client.cookies.size) await client.request('/api/v1/auth/logout','POST',undefined,[204]) }
      catch { cleanupFailed=true }
      finally { client.dispose() }
    }
    if (cleanupFailed) throw new Error('Synthetic batch logout failed; every client was discarded')
  }
  return {generation:manifest.id,verifiedAt:new Date().toISOString(),services:results,operationalChecks:results.reduce((sum,value) => sum+value.checks,0),gatewayRoleCheck:true}
}
export async function main(args) {
  if (args.length !== 2 || args[0] !== 'smoke') throw new Error('Use: node scripts/synthetic-platform.mjs smoke foundation|clinical|collaboration|audit')
  const names = batchNames(args[1])
  await lock(async () => {
    const manifest=await currentManifest()
    const evidence=await withSyntheticPlatform(manifest,names,() => verifyPlatformBatch(manifest,names),{eventDelivery:args[1] === 'collaboration'})
    const file=path.join(generationPath(manifest.id),`platform-${args[1]}-report.json`), contents=JSON.stringify(evidence,null,2)
    try { await readPrivate(file); await privateFile(file,contents,'w') }
    catch (error) { if (error.code !== 'ENOENT') throw error; await privateFile(file,contents) }
    console.log(`Isolated ${args[1]} batch: ${evidence.operationalChecks} operational assertions, Config markers and Gateway role checks passed; helpers stopped.`)
  })
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2)).catch(error => {console.error(error.message);process.exitCode=1})
