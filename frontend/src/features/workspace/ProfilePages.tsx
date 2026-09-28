import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../app/auth/AuthProvider'
import { WorkflowDrawer, WorkflowPageHeader, WorkflowNotice } from '../../components/workflow/WorkflowUI'
import { accountRestService, type OwnAccountResource } from '../../services/api/accountRestService'
import { organisationRestService } from '../../services/api/organisationRestService'
import { apiErrorMessage } from '../../services/api/ApiError'
import type { OrganisationResource } from '../../models/organisation'
import { DataState, useAccountIdentity } from './LiveWorkspacePages'

export function OwnProfilePage() {
  const query = useAccountIdentity()
  const [editing, setEditing] = useState(false)
  return <div className="page live-workspace-page patient-detail-page"><WorkflowPageHeader eyebrow="Authenticated account" title="Your profile." copy="Your account identity is saved in Auth Service. Patient registration and clinical records use their own authorised workflows."/><DataState query={query}>{query.data && <section className="workflow-card"><h2>{query.data.firstName} {query.data.lastName}</h2><dl><dt>Email</dt><dd>{query.data.email}</dd><dt>Phone</dt><dd>{query.data.phoneNumber || 'Not provided'}</dd><dt>Account</dt><dd>{query.data.status} · {query.data.emailVerified ? 'Email verified' : 'Email not verified'}</dd></dl><button className="primary" onClick={() => setEditing(true)}>Edit profile</button><p>Email changes and clinical-record corrections are not performed here.</p>{editing && <AccountEditor account={query.data} close={() => setEditing(false)}/>}</section>}</DataState></div>
}
function AccountEditor({ account, close }: { account:OwnAccountResource; close:() => void }) {
  const client = useQueryClient()
  const [firstName, setFirst] = useState(account.firstName)
  const [lastName, setLast] = useState(account.lastName)
  const [phoneNumber, setPhone] = useState(account.phoneNumber || '')
  const mutation = useMutation({ mutationFn:accountRestService.updateProfile, onSuccess:updated => {
    client.setQueryData(['current-account', updated.id], updated)
    close()
  } })
  return <WorkflowDrawer open close={close} title="Edit account profile" eyebrow="Your identity" copy="Changes are version-checked and audited. Email and account permissions are unchanged."><form className="live-profile-form" onSubmit={e => { e.preventDefault(); mutation.mutate({ firstName:firstName.trim(), lastName:lastName.trim(), phoneNumber:phoneNumber.trim() || null, version:account.version }) }}><label>First name<input required maxLength={100} value={firstName} onChange={e => setFirst(e.target.value)}/></label><label>Last name<input required maxLength={100} value={lastName} onChange={e => setLast(e.target.value)}/></label><label>Phone number<input type="tel" pattern="\+[1-9][0-9]{7,14}" placeholder="+21620123456" value={phoneNumber} onChange={e => setPhone(e.target.value)}/></label>{mutation.isError && <WorkflowNotice error message={apiErrorMessage(mutation.error,'Profile could not be saved. Reload it if another edit changed its version.')}/>}<button className="primary" disabled={mutation.isPending || !firstName.trim() || !lastName.trim()}>{mutation.isPending ? 'Saving…' : 'Save profile'}</button></form></WorkflowDrawer>
}
export function OrganisationSettingsPage() {
  const { session } = useAuth()
  const [editing, setEditing] = useState(false)
  const query = useQuery({ queryKey:['organisation-profile', session?.user.organizationId], queryFn:organisationRestService.currentProfile, enabled:Boolean(session?.user.organizationId) })
  return <div className="page live-workspace-page organization-page"><WorkflowPageHeader eyebrow="Active organisation administration" title="Organisation settings." copy="These changes affect the active organisation's administrative profile only." actions={<Link className="secondary" to="/organisations/select">Change organisation</Link>}/>{!session?.user.organizationId ? <p>Select an organisation first.</p> : <DataState query={query}>{query.data && <section className="workflow-card"><h2>{query.data.name}</h2><p>{query.data.type.replaceAll('_',' ')} · {query.data.status}</p><dl><dt>Email</dt><dd>{query.data.contactEmail}</dd><dt>Phone</dt><dd>{query.data.phoneNumber}</dd><dt>Address</dt><dd>{query.data.address}, {query.data.city}, {query.data.region}, {query.data.countryCode}</dd><dt>Time zone</dt><dd>{query.data.timeZone}</dd></dl><button className="primary" onClick={() => setEditing(true)}>Edit organisation profile</button>{editing && <OrganisationEditor organisation={query.data} close={() => setEditing(false)}/>}</section>}</DataState>}</div>
}
function OrganisationEditor({ organisation, close }: { organisation:OrganisationResource; close:() => void }) {
  const client = useQueryClient()
  const [form, setForm] = useState({ name:organisation.name, contactEmail:organisation.contactEmail, phoneNumber:organisation.phoneNumber, address:organisation.address, city:organisation.city, region:organisation.region, postalCode:organisation.postalCode || '', countryCode:organisation.countryCode })
  const mutation = useMutation({ mutationFn:organisationRestService.updateCurrentProfile, onSuccess:updated => {
    client.setQueryData(['organisation-profile', organisation.id], updated)
    void client.invalidateQueries({ queryKey:['organisation-contexts'] })
    void client.invalidateQueries({ queryKey:['platform-organisations'] })
    close()
  } })
  const fields: Array<{key:keyof typeof form; label:string; min?:number; max:number; type?:string; optional?:boolean}> = [
    {key:'name',label:'Organisation name',min:2,max:160}, {key:'contactEmail',label:'Contact email',max:254,type:'email'},
    {key:'phoneNumber',label:'Phone number',min:6,max:32,type:'tel'}, {key:'address',label:'Address',min:4,max:300},
    {key:'city',label:'City',min:2,max:100}, {key:'region',label:'Region',min:2,max:100},
    {key:'postalCode',label:'Postal code',max:20,optional:true}, {key:'countryCode',label:'Country code',min:2,max:2},
  ]
  return <WorkflowDrawer open close={close} title="Edit organisation profile" eyebrow="Administrative changes" copy="Organisation type, status, time zone, memberships and permissions are unchanged."><form className="live-profile-form" onSubmit={e => { e.preventDefault(); mutation.mutate({ ...form, name:form.name.trim(), contactEmail:form.contactEmail.trim(), postalCode:form.postalCode.trim() || null, countryCode:form.countryCode.toUpperCase(), version:organisation.version }) }}>{fields.map(field => <label key={field.key}>{field.label}<input type={field.type || 'text'} required={!field.optional} minLength={field.min} maxLength={field.max} pattern={field.key === 'countryCode' ? '[A-Za-z]{2}' : undefined} value={form[field.key]} onChange={e => setForm(current => ({ ...current, [field.key]:e.target.value }))}/></label>)}{mutation.isError && <WorkflowNotice error message={apiErrorMessage(mutation.error,'Organisation profile could not be saved. Reload it if its version changed.')}/>}<button className="primary" disabled={mutation.isPending}>{mutation.isPending ? 'Saving…' : 'Save organisation profile'}</button></form></WorkflowDrawer>
}
