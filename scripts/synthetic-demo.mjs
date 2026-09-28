#!/usr/bin/env node
import { randomBytes } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ROOT } from './sahha.mjs'
import { currentManifest, generationPath, lock, parsePrivateJson, prepareMigrationRuntime, privateFile, readPrivate, requireAppsStopped, runPrivate, withCluster } from './synthetic-db.mjs'
import { GatewayClient } from './synthetic/gateway-client.mjs'
import { DEMO_COOKIES, IDENTITY_SERVICES, withSyntheticApps } from './synthetic/app-runtime.mjs'

export const ACCOUNT_KEYS = Object.freeze(['platform','orgAdmin','otherAdmin','doctorA','doctorB','unrelatedDoctor','receptionist','patient'])
export function accountIdentity(key) {
  const index = ACCOUNT_KEYS.indexOf(key)
  if (index < 0) throw new Error('Unknown synthetic account')
  return { key, id:`d1000000-0000-4000-8000-${String(index + 1).padStart(12,'0')}`, email:`${key.toLowerCase()}@sahha.example.test` }
}
export function validateAccounts(value, generation) {
  if (value?.generation !== generation || !Array.isArray(value.accounts) || value.accounts.length !== ACCOUNT_KEYS.length) throw new Error('Synthetic account manifest mismatch')
  for (const [index,key] of ACCOUNT_KEYS.entries()) {
    const account = value.accounts[index], expected = accountIdentity(key)
    if (account.key !== key || account.id !== expected.id || account.email !== expected.email || !/^Demo-Aa1![A-Za-z0-9_-]{32}$/.test(account.password)) throw new Error('Synthetic identity or password manifest mismatch')
  }
  return value
}
export function accountsProperties(value) {
  validateAccounts(value, value.generation)
  return `generation=${value.generation}\n` + value.accounts.map(account => `${account.key}.password=${account.password}\n`).join('')
}
async function accountsFor(m) {
  const file = path.join(generationPath(m.id), 'demo-accounts.json')
  try { return validateAccounts(parsePrivateJson(await readPrivate(file)),m.id) }
  catch (error) { if (error.code !== 'ENOENT') throw error }
  const value = { generation:m.id, accounts:ACCOUNT_KEYS.map(key => ({ ...accountIdentity(key),password:`Demo-Aa1!${randomBytes(24).toString('base64url')}` })) }
  await privateFile(file, JSON.stringify(value,null,2))
  return value
}

function check(condition, message) { if (!condition) throw new Error(`Synthetic acceptance failed: ${message}`) }
export function pageItems(page) {
  check(Array.isArray(page?.items) && Number.isInteger(page.totalPages) && page.totalPages <= 1, 'unexpected paginated API contract or additional pages')
  return page.items
}
function one(items, predicate, label) {
  const values = items.filter(predicate)
  check(values.length <= 1, `ambiguous ${label}; existing data will not be overwritten`)
  return values[0]
}
const organisationBody = label => ({ name:`Sahha Synthetic Clinic ${label}`, type:'CLINIC', contactEmail:`clinic-${label.toLowerCase()}@sahha.example.test`,
  phoneNumber:'+21670000000', address:'12 Synthetic Street', city:'Tunis',region:'Tunis',postalCode:'1000',countryCode:'TN',timeZone:'Africa/Tunis' })

export async function waitOrganisationRoute(client, attempts = 60, pause = () => new Promise(resolve => setTimeout(resolve,1000))) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    try { await client.request('/api/v1/organisations/memberships'); return }
    catch (error) { if (error.status !== 503 || attempt === attempts - 1) throw error; await pause() }
  }
}

export async function seedOrganisations(accounts, createClient = () => new GatewayClient()) {
  const clients = Object.fromEntries(accounts.map(account => [account.key,createClient()]))
  const accountByKey = Object.fromEntries(accounts.map(account => [account.key,account]))
  const evidence = { organisations:{}, departments:{}, memberships:{}, checks:0 }
  const verified = (condition, message) => { check(condition,message); evidence.checks++ }
  try {
    for (const account of accounts) {
      const session = await clients[account.key].login(account)
      verified(session.userId === account.id, `${account.key} login identity`)
      verified(JSON.stringify(session.platformRoles) === JSON.stringify(account.key === 'platform' ? ['PLATFORM_ADMIN'] : []), `${account.key} global role isolation`)
    }
    // Service health alone does not prove Eureka/Gateway routing is ready.
    // Retry only this read-only probe, never a potentially committed command.
    await waitOrganisationRoute(clients.platform)
    const platform = clients.platform
    for (const [label,adminKey] of [['A','orgAdmin'],['B','otherAdmin']]) {
      const body = organisationBody(label)
      const existing = await platform.request('/api/v1/platform/organisations?size=100')
      let org = one(pageItems(existing), value => value.name === body.name, 'organisation')
      if (!org) org = await platform.request('/api/v1/platform/organisations','POST',body,[201])
      verified(org.name === body.name && org.status === 'ACTIVE' && org.createdBy === accountByKey.platform.id && org.contactEmail === body.contactEmail, `${label} organisation provenance`)
      evidence.organisations[label] = org.id
      const admins = await platform.request(`/api/v1/platform/organisations/${org.id}/administrators?size=100`)
      let membership = one(pageItems(admins), value => value.userId === accountByKey[adminKey].id, 'administrator')
      if (!membership) membership = await platform.request(`/api/v1/platform/organisations/${org.id}/administrators`,'POST',{email:accountByKey[adminKey].email},[201])
      verified(membership.status === 'ACTIVE' && JSON.stringify(membership.roles) === '["ORGANIZATION_ADMIN"]', `${label} admin authority`)
      const admin = clients[adminKey]
      const selected = await admin.select(org.id)
      verified(selected.activeOrganisationId === org.id && JSON.stringify(selected.organisationRoles) === '["ORGANIZATION_ADMIN"]', `${label} admin context`)
      const departments = await admin.request('/api/v1/departments?size=100')
      let department = one(pageItems(departments), value => value.code === `DEMO_${label}`, 'department')
      if (!department) department = await admin.request('/api/v1/departments','POST',{name:`Synthetic general care ${label}`,code:`DEMO_${label}`,description:'Synthetic demonstration only'},[201])
      verified(department.organisationId === org.id && department.status === 'ACTIVE', `${label} department scope`)
      evidence.departments[label] = department.id
      const staff = label === 'A' ? [['doctorA','DOCTOR'],['doctorB','DOCTOR'],['unrelatedDoctor','DOCTOR'],['receptionist','RECEPTIONIST']] : [['doctorA','RECEPTIONIST'],['doctorB','DOCTOR']]
      for (const [key,role] of staff) {
        const directory = await admin.request('/api/v1/staff?size=100')
        let member = one(pageItems(directory), value => value.userId === accountByKey[key].id, 'staff membership')
        if (!member) {
          const invitations = await admin.request('/api/v1/staff-invitations?size=100')
          let invitation = one(pageItems(invitations), value => value.email === accountByKey[key].email && value.role === role && value.status === 'PENDING', 'pending invitation')
          if (!invitation) invitation = await admin.request('/api/v1/staff-invitations','POST',{email:accountByKey[key].email,role},[201])
          const accepted = await clients[key].request(`/api/v1/my/staff-invitations/${invitation.id}/accept`,'POST',{version:invitation.version})
          verified(accepted.status === 'ACCEPTED' && accepted.organisationId === org.id, `${key} explicit invitation acceptance`)
          const reread = await admin.request('/api/v1/staff?size=100')
          member = one(pageItems(reread), value => value.userId === accountByKey[key].id, 'accepted membership')
        }
        verified(member?.status === 'ACTIVE' && member.organisationId === org.id && JSON.stringify(member.roles) === JSON.stringify([role]), `${key} exact role in ${label}`)
        evidence.memberships[`${label}:${key}`] = member.membershipId
        if (!member.departmentAssignments.some(assignment => assignment.departmentId === department.id && assignment.status === 'ACTIVE')) {
          await admin.request(`/api/v1/staff/${member.membershipId}/department-assignments`,'POST',{departmentId:department.id,positionTitle:`Synthetic ${role.toLowerCase()}`,primaryAssignment:true,startDate:new Date().toISOString().slice(0,10)},[201])
        }
        const user = clients[key]
        const context = await user.select(org.id)
        verified(context.membershipId === member.membershipId && context.activeOrganisationId === org.id && JSON.stringify(context.organisationRoles) === JSON.stringify([role]), `${key} current context in ${label}`)
        if (role === 'DOCTOR') {
          if (!member.doctorProfile) await user.request('/api/v1/my/doctor-profile','PUT',{specialty:'General medicine',professionalTitle:'Synthetic demo doctor',licenceNumber:`DEMO-${key}-${label}`,registrationAuthority:'Synthetic training fixture - not verified',biography:'Synthetic demonstration profile only',version:null})
          const profile = await user.request('/api/v1/my/doctor-profile')
          verified(profile.membershipId === member.membershipId && profile.organisationId === org.id, `${key} own doctor profile`)
        } else { await user.request('/api/v1/my/doctor-profile','GET',undefined,[403]); evidence.checks++ }
      }
    }
    // Exercise real Auth/Organisation authority boundaries through Gateway.
    await platform.request('/api/v1/staff','GET',undefined,[403]); evidence.checks++
    await clients.orgAdmin.request('/api/v1/my/doctor-profile','GET',undefined,[403]); evidence.checks++
    await clients.orgAdmin.request(`/api/v1/departments/${evidence.departments.B}`,'GET',undefined,[404]); evidence.checks++
    await clients.receptionist.request('/api/v1/platform/organisations','GET',undefined,[403]); evidence.checks++
    const patientContexts = await clients.patient.request('/api/v1/organisations/memberships')
    verified(patientContexts.length === 0, 'patient receives no staff memberships')
    const doctor = clients.doctorA
    await doctor.select(evidence.organisations.A)
    const oldCookie = doctor.cookieHeader()
    const next = await doctor.select(evidence.organisations.B)
    verified(JSON.stringify(next.organisationRoles) === '["RECEPTIONIST"]', 'doctor-to-receptionist context switch does not union roles')
    await doctor.request('/api/v1/my/doctor-profile','GET',undefined,[403]); evidence.checks++
    const stale = createClient()
    try { await stale.request('/api/v1/auth/session','GET',undefined,[401],{Cookie:oldCookie}); evidence.checks++ } finally { stale.dispose() }
    await doctor.select(evidence.organisations.A)
    verified(doctor.cookies.has(DEMO_COOKIES.access), 'current session uses isolated cookies')
    return evidence
  } finally {
    for (const client of Object.values(clients)) {
      try { if (client.cookies.has(DEMO_COOKIES.access)) await client.request('/api/v1/auth/logout','POST',undefined,[204]) }
      catch { /* Cleanup cannot weaken authority; credentials are discarded below. */ }
      finally { client.dispose() }
    }
  }
}

async function waitGateway() {
  for (let attempt = 0; attempt < 45; attempt++) {
    try { const client = new GatewayClient(); await client.refreshCsrf(); client.dispose(); return } catch { await new Promise(resolve => setTimeout(resolve,1000)) }
  }
  throw new Error('Gateway could not discover isolated Auth within the readiness window')
}

export async function main(args) {
  if (args.length !== 1 || args[0] !== 'seed') throw new Error('Use: node scripts/synthetic-demo.mjs seed (temporary services stop afterward)')
  await lock(async () => {
    await requireAppsStopped()
    const m = await currentManifest()
    const accountManifest = await accountsFor(m)
    const directory = generationPath(m.id)
    const propsFile = path.join(directory, 'demo-accounts.properties')
    try { check(await readPrivate(propsFile) === accountsProperties(accountManifest), 'account file mismatch') }
    catch (error) { if (error.code !== 'ENOENT') throw error; await privateFile(propsFile, accountsProperties(accountManifest)) }
    const {java,runtime} = await prepareMigrationRuntime()
    await withCluster(m, async () => {
      const result = await runPrivate(java, ['-Xmx128m','-cp',path.join(runtime,'BOOT-INF','lib','*'),path.join(ROOT,'auth-service','src','synthetic','SeedSyntheticAccounts.java'),m.id,path.join(directory,'auth.properties'),propsFile])
      const safe = result.stdout.trim().split(/\r?\n/).find(line => /^SYNTHETIC_AUTH_OK accounts=8 created=\d+$/.test(line))
      if (!safe) throw new Error('Missing verified Auth seed result')
      console.log(safe)
      const evidence = await withSyntheticApps(m.id, IDENTITY_SERVICES, async () => { await waitGateway(); return seedOrganisations(accountManifest.accounts) })
      const report = path.join(directory,'demo-seed-report.json')
      const contents = JSON.stringify({generation:m.id,verifiedAt:new Date().toISOString(),...evidence},null,2)
      try { await readPrivate(report); await privateFile(report,contents,'w') } catch (error) { if (error.code !== 'ENOENT') throw error; await privateFile(report,contents) }
      console.log(`Synthetic role seed passed ${evidence.checks} Gateway assertions. Private demo accounts: ${path.join(directory,'demo-accounts.json')}`)
    })
  })
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1 })
