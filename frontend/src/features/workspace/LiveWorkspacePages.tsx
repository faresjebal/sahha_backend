import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CalendarDays, MessageSquare, Search, ShieldCheck } from 'lucide-react'
import { useAuth } from '../../app/auth/AuthProvider'
import { WorkflowPageHeader, WorkflowEmpty, WorkflowStatus } from '../../components/workflow/WorkflowUI'
import { accountRestService } from '../../services/api/accountRestService'
import { appointmentRestService } from '../../services/api/appointmentRestService'
import { availabilityRestService } from '../../services/api/availabilityRestService'
import { communicationRestService } from '../../services/api/communicationRestService'
import { consultationRestService } from '../../services/api/consultationRestService'
import { departmentRestService } from '../../services/api/departmentRestService'
import { organisationRestService } from '../../services/api/organisationRestService'
import { patientRegistryRestService } from '../../services/api/patientRegistryRestService'
import { staffDirectoryRestService } from '../../services/api/staffDirectoryRestService'
import { apiErrorMessage } from '../../services/api/ApiError'
import { doctorAppointments } from '../scheduling/doctorAppointments'

export const dateTime = (value: string) => new Intl.DateTimeFormat(undefined, { dateStyle:'medium', timeStyle:'short' }).format(new Date(value))
export const dayRange = (date: string, days = 1) => {
  const from = new Date(`${date}T00:00:00`)
  const to = new Date(from); to.setDate(to.getDate() + days)
  return { from:from.toISOString(), to:to.toISOString() }
}
export const localDate = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
}
export function DataState({ query, children }: { query: { isPending:boolean; isError:boolean; error:unknown; refetch:() => unknown }; children:ReactNode }) {
  if (query.isPending) return <p role="status">Loading current data…</p>
  if (query.isError) return <div role="alert"><p>{apiErrorMessage(query.error, 'Data could not be loaded.')}</p><button className="secondary" onClick={() => query.refetch()}>Retry</button></div>
  return <>{children}</>
}
export function UnavailablePage({ title = 'Not available in this version', copy = 'This workspace is outside the internship V1. No demo action or sample record is shown.' }: { title?:string; copy?:string }) {
  return <div className="page live-workspace-page workflow-page"><WorkflowPageHeader eyebrow="Sahha V1" title={title} copy={copy}/><WorkflowEmpty title="No live workflow available" copy="Use the connected workspaces in the navigation."/></div>
}
export function useAccountIdentity() {
  const { session } = useAuth()
  return useQuery({ queryKey:['current-account', session?.user.id], queryFn:accountRestService.me, enabled:Boolean(session), retry:false })
}
export function useOrganisationContext() {
  const { session } = useAuth()
  return useQuery({ queryKey:['organisation-contexts', session?.user.id], queryFn:organisationRestService.listMyContexts, enabled:Boolean(session?.user.organizationId) })
}
function Metric({ label, value }: { label:string; value:ReactNode }) {
  return <article><span>{label}</span><strong>{value}</strong></article>
}

export function DoctorOverviewPage() {
  const { session } = useAuth()
  const account = useAccountIdentity()
  const [date, setDate] = useState(localDate)
  const range = dayRange(date)
  const visits = useQuery({ queryKey:['appointments', session?.user.organizationId, range.from, range.to], queryFn:() => appointmentRestService.list(range.from, range.to), select:appointments => doctorAppointments(appointments, session?.user.id), enabled:Boolean(session?.user.organizationId) })
  const conversations = useQuery({ queryKey:['conversations', session?.user.organizationId], queryFn:() => communicationRestService.conversations(), enabled:Boolean(session?.user.organizationId) })
  if (!session?.user.organizationId) return <SelectOrganisation/>
  return <div className="page live-workspace-page doctor-work-page"><WorkflowPageHeader eyebrow="Your care day" title={account.data ? `Welcome, ${account.data.firstName}.` : 'Your clinical overview.'} copy="Your appointments and collaboration activity, scoped to the active organisation." actions={<Link className="primary" to="/doctor/appointments"><CalendarDays/>Manage appointments</Link>}/>
    <label className="search-field">Schedule date<input aria-label="Schedule date" type="date" required value={date} onChange={e => e.target.value && setDate(e.target.value)}/></label>
    <DataState query={visits}><section className="admin-metrics">
      <Metric label="Appointments on selected date" value={visits.data?.length || 0}/>
      <Metric label="Waiting patients" value={visits.data?.filter(item => item.status === 'CHECKED_IN').length || 0}/>
      <Metric label="Completed visits" value={visits.data?.filter(item => item.status === 'COMPLETED').length || 0}/>
    </section><section className="appointment-lifecycle-table">{visits.data?.map(item => <article key={item.id}><span><strong>{dateTime(item.startsAt)}</strong><small>Registration {item.patientRegistrationId.slice(0,8)}</small></span><span>{item.locationLabel}</span><WorkflowStatus value={item.status}/><Link className="secondary" to="/doctor/appointments">Open schedule</Link></article>)}</section>{!visits.data?.length && <WorkflowEmpty title="No appointments on this date" copy="New requests appear here when a patient or receptionist books your published availability."/>}</DataState>
    <section className="workflow-card"><DataState query={conversations}><Link className="secondary" to="/doctor/messages"><MessageSquare/>{conversations.data?.totalElements || 0} conversations</Link></DataState></section>
  </div>
}
function SelectOrganisation() {
  return <div className="page live-workspace-page workflow-page"><WorkflowEmpty title="Choose an organisation" copy="A clinical or administrative workspace requires an active organisation."/><Link className="primary" to="/organisations/select">Select organisation</Link></div>
}

export function DoctorPatientsPage({ registrationId }: { registrationId?:string }) {
  const { session } = useAuth()
  const [date, setDate] = useState(localDate)
  const [search, setSearch] = useState('')
  const range = dayRange(date, 31)
  const visits = useQuery({ queryKey:['appointments', session?.user.organizationId, range.from, range.to], queryFn:() => appointmentRestService.list(range.from, range.to), select:appointments => doctorAppointments(appointments, session?.user.id), enabled:Boolean(session?.user.organizationId) })
  const summary = useQuery({ queryKey:['patient-clinical-summary', session?.user.organizationId, registrationId], queryFn:() => consultationRestService.getPatientSummary(registrationId!), enabled:Boolean(registrationId && session?.user.organizationId), retry:false })
  if (!session?.user.organizationId) return <SelectOrganisation/>
  if (registrationId) return <div className="page live-workspace-page doctor-work-page"><WorkflowPageHeader eyebrow="Authorised clinical history" title={`Patient registration ${registrationId.slice(0,8)}`} copy="Only information permitted by your current care relationship is returned. Sharing remains explicit." actions={<Link className="secondary" to="/doctor/patients">Back to patients</Link>}/><DataState query={summary}>
    <Link className="primary" to="/doctor/appointments">Start or continue an appointment-backed consultation</Link>
    {summary.data?.encounters.map(item => <section className="workflow-card" key={item.consultationId}><h2>{item.reasonForConsultation || 'Finalised consultation'}</h2><p>{dateTime(item.finalizedAt)}</p><h3>Diagnoses</h3>{item.diagnoses.map((value,index) => <p key={index}>{value.label} · {value.status}</p>)}<h3>Medication</h3>{item.medications.map((value,index) => <p key={index}>{value.name} {value.strength} · {value.dosage} {value.frequency}</p>)}<h3>Allergies</h3>{item.allergies.map((value,index) => <p key={index}>{value}</p>)}</section>)}
    {!summary.data?.encounters.length && <WorkflowEmpty title="No finalised encounters returned" copy="Use the Clinical workspace for the current appointment."/>}
  </DataState></div>
  const ids = [...new Set((visits.data || []).filter(item => !['CANCELLED','REJECTED'].includes(item.status)).map(item => item.patientRegistrationId))].filter(id => id.toLowerCase().includes(search.toLowerCase()))
  return <div className="page live-workspace-page doctor-work-page"><WorkflowPageHeader eyebrow="Appointment-linked patients" title="Your patient workspace." copy="Patients on your schedule in the 31-day window below. This is not the organisation's administrative patient directory."/><div className="reception-filters"><label className="search-field">Window starts<input aria-label="Patient window starts" type="date" value={date} onChange={e => e.target.value && setDate(e.target.value)}/></label><label className="search-field"><Search/><input aria-label="Find patient registration" placeholder="Search registration ID" value={search} onChange={e => setSearch(e.target.value)}/></label></div><DataState query={visits}><section className="workflow-card">{ids.map(id => <p key={id}><Link to={`/doctor/patients/${id}`}>Registration {id}</Link></p>)}</section>{!ids.length && <WorkflowEmpty title="No matching patient registrations" copy="Select an earlier schedule window to find previous patients. Patient registration is managed by reception."/>}</DataState></div>
}

export function ColleagueDirectoryPage() {
  const { session } = useAuth()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const doctors = useQuery({ queryKey:['collaboration-doctors', session?.user.organizationId, page], queryFn:() => communicationRestService.doctors(session!.user.organizationId!, page), enabled:Boolean(session?.user.organizationId) })
  if (!session?.user.organizationId) return <SelectOrganisation/>
  return <div className="page live-workspace-page doctor-work-page"><WorkflowPageHeader eyebrow="Active organisation" title="Find a colleague." copy="Active doctor memberships from Organisation Service. A conversation never grants patient-record access."/><label className="search-field"><Search/><input aria-label="Search colleagues on this page" placeholder="Search colleagues on this page" value={search} onChange={e => setSearch(e.target.value)}/></label><DataState query={doctors}><div className="doctor-grid">{doctors.data?.content.filter(item => item.displayName.toLowerCase().includes(search.toLowerCase())).map(item => <article className="doctor-card" key={item.userId}><h2>{item.displayName}</h2><p>Active colleague</p><Link className="secondary" to={`/doctor/messages?recipient=${encodeURIComponent(item.userId)}`}>Message colleague</Link></article>)}</div>{!doctors.data?.content.some(item => item.displayName.toLowerCase().includes(search.toLowerCase())) && <WorkflowEmpty title="No colleagues found" copy="Try another name on this page or ask your administrator about active doctor memberships."/>}<Pagination page={page} pages={doctors.data?.totalPages || 0} change={setPage}/></DataState></div>
}
export function Pagination({ page, pages, change }: { page:number; pages:number; change:(page:number) => void }) {
  return <div className="filter-row"><button type="button" className="secondary" disabled={page === 0} onClick={() => change(page-1)}>Previous page</button><span>Page {page+1} of {Math.max(1,pages)}</span><button type="button" className="secondary" disabled={page+1 >= pages} onClick={() => change(page+1)}>Next page</button></div>
}

export function PatientWorkspacePage({ mode }: { mode:'overview'|'doctors' }) {
  const [registration, setRegistration] = useState('')
  const [search, setSearch] = useState('')
  const [date] = useState(localDate)
  const registrations = useQuery({ queryKey:['my-patient-registrations'], queryFn:patientRegistryRestService.listMyRegistrations, retry:false })
  const active = registrations.data?.filter(item => item.status === 'ACTIVE') || []
  const selected = active.find(item => item.registrationId === registration) || active[0]
  const id = selected?.registrationId
  const range = dayRange(date,31)
  const doctors = useQuery({ queryKey:['my-patient-doctors', id], queryFn:() => availabilityRestService.listMyPatientDoctors(id!), enabled:Boolean(id) })
  const visits = useQuery({ queryKey:['my-patient-appointments', id, range.from, range.to], queryFn:() => appointmentRestService.listMine(id!, range.from, range.to), enabled:Boolean(id) && mode === 'overview' })
  return <div className="page live-workspace-page patient-detail-page"><WorkflowPageHeader eyebrow="Your private patient portal" title={mode === 'doctors' ? 'Find care.' : 'Your upcoming care.'} copy="Data comes from your linked patient registrations, not a sample patient." actions={<Link className="primary" to="/patient/appointments">Book or manage appointments</Link>}/><DataState query={registrations}>
    {!active.length ? <WorkflowEmpty title="Link your patient registration" copy="Open Appointments to link your verified account using your organisation, record number and date of birth."/> : <><label className="search-field">Patient registration<select aria-label="Patient registration" value={id} onChange={e => setRegistration(e.target.value)}>{active.map(item => <option key={item.registrationId} value={item.registrationId}>{item.firstName} {item.lastName} · {item.medicalRecordNumber}</option>)}</select></label>
    {mode === 'overview' ? <DataState query={visits}><section className="reception-appointments">{visits.data?.map(item => <article key={item.id}><CalendarDays/><span><strong>{dateTime(item.startsAt)}</strong><small>{doctors.data?.find(doctor => doctor.doctorUserId === item.doctorUserId)?.displayName || `Doctor ${item.doctorUserId.slice(0,8)}`} · {item.locationLabel}</small></span><WorkflowStatus value={item.status}/></article>)}</section>{!visits.data?.length && <WorkflowEmpty title="No appointments in the next 31 days" copy="Find a doctor with published availability to request a visit."/>}</DataState> : <><label className="search-field"><Search/><input aria-label="Search available doctors" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search doctor or location"/></label><DataState query={doctors}><div className="doctor-grid">{doctors.data?.filter(item => `${item.displayName} ${item.locationLabel}`.toLowerCase().includes(search.toLowerCase())).map(item => <article className="doctor-card" key={item.doctorUserId}><h2>{item.displayName}</h2><p>{item.locationLabel} · {item.appointmentDurationMinutes} minutes</p><small>{item.timeZone}</small><Link className="primary" to={`/patient/appointments?registration=${encodeURIComponent(id!)}&doctor=${encodeURIComponent(item.doctorUserId)}`}>View published slots</Link></article>)}</div>{!doctors.data?.some(item => `${item.displayName} ${item.locationLabel}`.toLowerCase().includes(search.toLowerCase())) && <WorkflowEmpty title="No matching published doctor availability" copy="Contact your organisation or try again later."/>}</DataState></>}
    </>}
  </DataState></div>
}

export function OrganisationOverviewPage() {
  const { session } = useAuth()
  const contexts = useOrganisationContext()
  const departments = useQuery({ queryKey:['department-count', session?.user.organizationId], queryFn:() => departmentRestService.list(0,1), enabled:Boolean(session?.user.organizationId) })
  const staff = useQuery({ queryKey:['staff-count', session?.user.organizationId], queryFn:() => staffDirectoryRestService.list(0,1), enabled:Boolean(session?.user.organizationId) })
  const selected = contexts.data?.find(item => item.organisationId === session?.user.organizationId)
  if (!session?.user.organizationId) return <SelectOrganisation/>
  return <div className="page live-workspace-page organization-page"><WorkflowPageHeader eyebrow="Organisation administration" title="Your organisation at a glance." copy="Administrative information only. This role does not imply clinical access." actions={<Link className="secondary" to="/organisations/select">Change organisation</Link>}/><DataState query={contexts}>{selected && <section className="workflow-card"><h2>{selected.organisationName}</h2><p>{selected.organisationType.replaceAll('_',' ')}</p><p>{selected.roles.join(', ')}</p></section>}</DataState><section className="admin-metrics"><DataState query={departments}><Metric label="Departments" value={departments.data?.totalElements ?? 0}/></DataState><DataState query={staff}><Metric label="Staff memberships" value={staff.data?.totalElements ?? 0}/></DataState></section></div>
}

export function PlatformOverviewPage() {
  const organisations = useQuery({ queryKey:['platform-organisations-count'], queryFn:() => organisationRestService.list(0,1) })
  return <div className="page live-workspace-page"><WorkflowPageHeader eyebrow="Platform administration" title="Your platform workspace." copy="Real organisation administration and account security. Clinical records are not part of platform authority."/><DataState query={organisations}><section className="admin-metrics"><Metric label="Organisations" value={organisations.data?.totalElements || 0}/></section></DataState><section className="workflow-card"><Link className="primary" to="/platform/organizations">Manage organisations</Link><Link className="secondary" to="/platform/users">Find an account</Link></section></div>
}
export function PlatformAccountsPage() {
  const client = useQueryClient()
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState('')
  const account = useQuery({ queryKey:['platform-account', submitted], queryFn:() => accountRestService.find(submitted), enabled:Boolean(submitted), retry:false })
  const mutation = useMutation({ mutationFn:(action:'SUSPEND'|'REACTIVATE'|'DISABLE') => accountRestService.changeStatus(account.data!.id, action), onSuccess:() => client.invalidateQueries({ queryKey:['platform-account', submitted] }) })
  return <div className="page live-workspace-page"><WorkflowPageHeader eyebrow="Authentication accounts" title="Find an account." copy="Resolve one account by exact email. No patient or clinical directory is exposed."/><form className="search-field" onSubmit={e => { e.preventDefault(); if (mutation.isPending) return; mutation.reset(); setSubmitted(email.trim()) }}><input aria-label="Account email" disabled={mutation.isPending} type="email" required value={email} onChange={e => setEmail(e.target.value)}/><button className="primary" disabled={mutation.isPending}>Find account</button></form>{submitted && <DataState query={account}>{account.data && <section className="workflow-card"><h2>{account.data.firstName} {account.data.lastName}</h2><p>{account.data.email}</p><WorkflowStatus value={account.data.status}/><div className="filter-row"><button className="secondary" disabled={mutation.isPending || account.data.status !== 'ACTIVE'} onClick={() => mutation.mutate('SUSPEND')}>Suspend account</button><button className="secondary" disabled={mutation.isPending || account.data.status !== 'SUSPENDED'} onClick={() => mutation.mutate('REACTIVATE')}>Reactivate account</button></div>{mutation.isError && <p role="alert">{apiErrorMessage(mutation.error,'Account update failed.')}</p>}{mutation.isSuccess && <p role="status">Account status updated.</p>}</section>}</DataState>}</div>
}
export function AccountSecurityPage() {
  const client = useQueryClient()
  const sessions = useQuery({ queryKey:['my-device-sessions'], queryFn:accountRestService.sessions })
  const mutation = useMutation({ mutationFn:accountRestService.revokeSession, onSuccess:() => client.invalidateQueries({ queryKey:['my-device-sessions'] }) })
  return <div className="page live-workspace-page"><WorkflowPageHeader eyebrow="Your account security" title="Signed-in devices." copy="These are your own active sessions, not the organisation's global security policy."/><DataState query={sessions}><section className="workflow-card">{sessions.data?.map(item => <article key={item.sessionId}><h2><ShieldCheck/>{item.deviceName}</h2><p>Last active {dateTime(item.lastActivityAt)}</p>{item.current ? <strong>Current session — use Sign out to end it</strong> : <button className="secondary" disabled={mutation.isPending} onClick={() => mutation.mutate(item.sessionId)}>Revoke session</button>}</article>)}</section>{mutation.isError && <p role="alert">{apiErrorMessage(mutation.error,'Session revocation failed.')}</p>}</DataState></div>
}
