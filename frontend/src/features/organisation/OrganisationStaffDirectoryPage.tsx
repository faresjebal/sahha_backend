import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Building2,
  CalendarDays,
  Check,
  ChevronRight,
  CirclePause,
  CirclePlay,
  LoaderCircle,
  Mail,
  RefreshCw,
  Search,
  ShieldCheck,
  Stethoscope,
  Trash2,
  UserCog,
  UserPlus,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import type {
  DepartmentResource,
  StaffMemberPageResource,
  StaffMemberResource,
} from '../../models/organisation'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { departmentRestService } from '../../services/api/departmentRestService'
import { staffDirectoryRestService } from '../../services/api/staffDirectoryRestService'

const assignmentSchema = z.object({
  departmentId:z.string().min(1, 'Select a department.'),
  positionTitle:z.string().trim().min(2, 'Enter a position title.').max(120),
  primaryAssignment:z.boolean(),
  startDate:z.string().min(1, 'Select a start date.'),
  plannedEndDate:z.string(),
})

type AssignmentValues = z.infer<typeof assignmentSchema>

const today = () => new Date().toISOString().slice(0, 10)
const initials = (name: string) => name.split(' ')
  .filter(Boolean).map(part => part[0]).slice(0, 2).join('').toUpperCase()
const roleLabel = (member: StaffMemberResource) =>
  member.roles.includes('DOCTOR') ? 'Doctor' : 'Receptionist'
const activeAssignments = (member: StaffMemberResource) =>
  member.departmentAssignments.filter(item => item.status === 'ACTIVE')

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'Select an organisation where you are an Organisation Administrator.'
    if (error.problem.status === 404) return 'This staff resource is not available in your active organisation.'
    if (error.problem.status === 409) return error.problem.type.includes('concurrent')
      ? 'This staff record changed in another request. Refresh and try again.'
      : 'The change conflicts with the current membership, department, or assignment state.'
  }
  return apiErrorMessage(error, fallback)
}

function StatusBadge({ status }: { status: StaffMemberResource['status'] }) {
  const label = status[0] + status.slice(1).toLowerCase()
  return <span className={`status status--${label.toLowerCase()}`}><i/>{label}</span>
}

export function OrganisationStaffDirectoryPage({
  doctorsOnly = false,
}: { doctorsOnly?: boolean }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'no-active-organisation'
  const staffKey = ['organisation', organisationId, 'staff'] as const
  const departmentKey = ['organisation', organisationId, 'departments'] as const
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<StaffMemberResource | null>(null)
  const [notice, setNotice] = useState('')
  const [removeArmed, setRemoveArmed] = useState(false)
  const staffQuery = useQuery({
    queryKey:staffKey,
    queryFn:() => staffDirectoryRestService.list(),
  })
  const departmentsQuery = useQuery({
    queryKey:departmentKey,
    queryFn:() => departmentRestService.list(),
  })
  const activeDepartments = departmentsQuery.data?.items.filter(
    department => department.status === 'ACTIVE',
  ) || []
  const {
    register,
    handleSubmit,
    reset,
    formState:{ errors },
  } = useForm<AssignmentValues>({
    resolver:zodResolver(assignmentSchema),
    defaultValues:{
      departmentId:'',
      positionTitle:'',
      primaryAssignment:false,
      startDate:today(),
      plannedEndDate:'',
    },
  })

  const refreshStaff = async (message: string) => {
    setNotice(message)
    const result = await staffDirectoryRestService.list()
    queryClient.setQueryData<StaffMemberPageResource>(staffKey, result)
    if (selected) {
      setSelected(result.items.find(item => item.membershipId === selected.membershipId) || null)
    }
  }

  const statusMutation = useMutation({
    mutationFn:({ member, status }: {
      member: StaffMemberResource
      status: StaffMemberResource['status']
    }) => staffDirectoryRestService.changeStatus(member.membershipId, {
      status,
      version:member.version,
    }),
    onSuccess:async member => {
      setSelected(member)
      setRemoveArmed(false)
      await refreshStaff(`${member.displayName} is now ${member.status.toLowerCase()}.`)
    },
  })

  const assignmentMutation = useMutation({
    mutationFn:({ member, values }: {
      member: StaffMemberResource
      values: AssignmentValues
    }) => staffDirectoryRestService.assignDepartment(member.membershipId, {
      departmentId:values.departmentId,
      positionTitle:values.positionTitle.trim(),
      primaryAssignment:values.primaryAssignment,
      startDate:values.startDate,
      plannedEndDate:values.plannedEndDate || null,
    }),
    onSuccess:async assignment => {
      reset({
        departmentId:'',
        positionTitle:'',
        primaryAssignment:false,
        startDate:today(),
        plannedEndDate:'',
      })
      await refreshStaff(`${assignment.departmentName} assignment created.`)
    },
  })

  const endAssignmentMutation = useMutation({
    mutationFn:({ member, assignmentId, version }: {
      member: StaffMemberResource
      assignmentId: string
      version: number
    }) => staffDirectoryRestService.endDepartmentAssignment(
      member.membershipId,
      assignmentId,
      { endDate:today(), version },
    ),
    onSuccess:async assignment => {
      await refreshStaff(`${assignment.departmentName} assignment ended.`)
    },
  })

  const items = staffQuery.data?.items || []
  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return items.filter(member => {
      if (doctorsOnly && !member.roles.includes('DOCTOR')) return false
      const searchable = `${member.displayName} ${member.email} ${member.roles.join(' ')} ${member.doctorProfile?.specialty || ''} ${activeAssignments(member).map(item => item.departmentName).join(' ')}`
      return !normalized || searchable.toLowerCase().includes(normalized)
    })
  }, [doctorsOnly, items, query])

  const openMember = (member: StaffMemberResource) => {
    setSelected(member)
    setNotice('')
    setRemoveArmed(false)
    statusMutation.reset()
    assignmentMutation.reset()
  }

  const submitAssignment = handleSubmit(async values => {
    if (!selected) return
    try {
      await assignmentMutation.mutateAsync({ member:selected, values })
    } catch {
      // The mutation state renders the safe API problem.
    }
  })

  const mutationError = statusMutation.error
    || assignmentMutation.error
    || endAssignmentMutation.error

  return <div className="page organization-page staff-directory-page">
    <div className="page-intro"><div><p className="eyebrow">Active organisation · Authoritative staff access</p><h1>{doctorsOnly?'Clinical teams and doctors.':'People, roles, and accountable placement.'}</h1><p>{doctorsOnly?'Review doctor profiles, membership state, and department placement.':'Manage accepted doctors and receptionists without combining identities or roles from other organisations.'}</p></div><button className="primary soft" onClick={()=>navigate('/hospital/admin/access')}><UserPlus/>Invite staff member</button></div>

    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}

    <section className="department-summary" aria-label="Staff summary">
      <article><UserCog/><span><strong>{staffQuery.data?.totalElements ?? '—'}</strong><small>Staff memberships</small></span></article>
      <article><Stethoscope/><span><strong>{items.filter(item=>item.roles.includes('DOCTOR')).length}</strong><small>Doctors</small></span></article>
      <article><CirclePause/><span><strong>{items.filter(item=>item.status==='SUSPENDED').length}</strong><small>Suspended</small></span></article>
    </section>

    <label className="search-field soft-field"><Search/><span className="sr-only">Search staff</span><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Search name, email, role, specialty, or department"/></label>

    {staffQuery.isPending&&<div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>Loading staff directory</strong><span>Reading only memberships in the active organisation.</span></div>}
    {staffQuery.isError&&<div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Staff directory unavailable</strong><span>{errorMessage(staffQuery.error, 'The staff directory could not be loaded.')}</span><button className="secondary" onClick={()=>void staffQuery.refetch()}><RefreshCw/>Try again</button></div>}

    {!staffQuery.isPending&&!staffQuery.isError&&<section className="staff-directory-list" aria-label="Organisation staff">
      {visible.map(member=><button key={member.membershipId} onClick={()=>openMember(member)}>
        <span className="staff-initials">{initials(member.displayName)}</span>
        <span><strong>{member.displayName}</strong><small>{roleLabel(member)} · {member.email}</small></span>
        <span><strong>{member.doctorProfile?.specialty || (member.roles.includes('DOCTOR')?'Profile incomplete':'Patient services')}</strong><small>{activeAssignments(member).map(item=>item.departmentName).join(' · ') || 'No active department'}</small></span>
        <StatusBadge status={member.status}/><ChevronRight/>
      </button>)}
    </section>}

    {!staffQuery.isPending&&!staffQuery.isError&&!visible.length&&<div className="empty-state"><UserCog/><h2>{items.length?'No staff member found':'No accepted staff yet'}</h2><p>{items.length?'Try another name, role, or department.':'Create an invitation and ask the doctor or receptionist to accept it.'}</p><button className="primary soft" onClick={()=>navigate('/hospital/admin/access')}><Mail/>Open invitations</button></div>}

    {selected&&<div className="staff-directory-drawer"><button className="nav-scrim" aria-label="Close staff details" onClick={()=>setSelected(null)}/><aside role="dialog" aria-modal="true" aria-labelledby="staff-member-title">
      <header><div><p className="eyebrow">{roleLabel(selected)} membership</p><h2 id="staff-member-title">{selected.displayName}</h2></div><button className="icon-button" onClick={()=>setSelected(null)} aria-label="Close staff details"><X/></button></header>
      <div className="staff-directory-person"><span className="staff-initials">{initials(selected.displayName)}</span><span><strong>{selected.email}</strong><small>Joined {new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(selected.joinedAt))}</small></span><StatusBadge status={selected.status}/></div>

      {selected.roles.includes('DOCTOR')&&<section className="staff-profile-summary"><ShieldCheck/><div><strong>{selected.doctorProfile?.professionalTitle || 'Professional profile incomplete'}</strong><p>{selected.doctorProfile?`${selected.doctorProfile.specialty} · ${selected.doctorProfile.registrationAuthority}`:'The doctor must complete their organisation-specific profile.'}</p>{selected.doctorProfile&&<small>Licence {selected.doctorProfile.licenceNumber}</small>}</div></section>}

      <section><div className="panel-heading"><div><p className="eyebrow">Department placement</p><h3>Assignments</h3></div></div>{selected.departmentAssignments.map(assignment=><article className="staff-assignment-row" key={assignment.id}><Building2/><span><strong>{assignment.departmentName}</strong><small>{assignment.positionTitle} · Since {assignment.startDate}{assignment.primaryAssignment?' · Primary':''}</small></span><span className={`status status--${assignment.status.toLowerCase()}`}><i/>{assignment.status==='ACTIVE'?'Active':'Ended'}</span>{assignment.status==='ACTIVE'&&selected.status!=='REMOVED'&&<button className="text-button" disabled={endAssignmentMutation.isPending} onClick={()=>endAssignmentMutation.mutate({member:selected,assignmentId:assignment.id,version:assignment.version})}>End</button>}</article>)}{!selected.departmentAssignments.length&&<p className="staff-directory-muted">No department assignment has been recorded.</p>}</section>

      {selected.status==='ACTIVE'&&<form className="staff-assignment-form" onSubmit={submitAssignment} noValidate><h3>Assign department</h3><label><span>Department</span><select {...register('departmentId')} aria-invalid={Boolean(errors.departmentId)}><option value="">Choose department</option>{activeDepartments.map((department:DepartmentResource)=><option key={department.id} value={department.id}>{department.name} · {department.code}</option>)}</select>{errors.departmentId&&<small className="login-field-error">{errors.departmentId.message}</small>}</label><label><span>Position title</span><input {...register('positionTitle')} placeholder={selected.roles.includes('DOCTOR')?'Attending physician':'Reception coordinator'}/>{errors.positionTitle&&<small className="login-field-error">{errors.positionTitle.message}</small>}</label><div><label><span>Start date</span><input type="date" {...register('startDate')}/></label><label><span>Planned end <small>optional</small></span><input type="date" {...register('plannedEndDate')}/></label></div><label className="staff-assignment-check"><input type="checkbox" {...register('primaryAssignment')}/><span>Primary department assignment</span></label><button className="secondary" disabled={assignmentMutation.isPending||!activeDepartments.length}>{assignmentMutation.isPending?<><LoaderCircle className="spin"/>Assigning…</>:<><Building2/>Create assignment</>}</button></form>}

      {mutationError&&<p className="form-message form-message--error" role="alert">{errorMessage(mutationError, 'The staff record could not be changed.')}</p>}

      <footer className="staff-lifecycle-actions">{selected.status==='ACTIVE'&&<button className="secondary" disabled={statusMutation.isPending} onClick={()=>statusMutation.mutate({member:selected,status:'SUSPENDED'})}><CirclePause/>Suspend access</button>}{selected.status==='SUSPENDED'&&<button className="secondary" disabled={statusMutation.isPending} onClick={()=>statusMutation.mutate({member:selected,status:'ACTIVE'})}><CirclePlay/>Reactivate</button>}{selected.status!=='REMOVED'&&!removeArmed&&<button className="text-button danger-text" onClick={()=>setRemoveArmed(true)}><Trash2/>Remove membership</button>}{removeArmed&&<button className="danger-button" disabled={statusMutation.isPending} onClick={()=>statusMutation.mutate({member:selected,status:'REMOVED'})}>Confirm permanent removal</button>}</footer>
    </aside></div>}
  </div>
}
