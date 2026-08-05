import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Check,
  Clock3,
  LoaderCircle,
  Mail,
  RefreshCw,
  RotateCw,
  Search,
  ShieldCheck,
  UserPlus,
  UserRound,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import type {
  CreateStaffInvitationCommand,
  StaffInvitationPageResource,
  StaffInvitationResource,
} from '../../models/organisation'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { staffInvitationRestService } from '../../services/api/staffInvitationRestService'

const invitationSchema = z.object({
  email:z.string().trim().email('Enter a valid email address.').max(254),
  role:z.enum(['DOCTOR', 'RECEPTIONIST']),
})

type InvitationFormValues = z.infer<typeof invitationSchema>

const defaultValues: InvitationFormValues = {
  email:'',
  role:'DOCTOR',
}

const roleLabel = (role: StaffInvitationResource['role']) =>
  role === 'DOCTOR' ? 'Doctor' : 'Receptionist'

const statusLabel = (status: StaffInvitationResource['status']) => ({
  PENDING:'Pending',
  ACCEPTED:'Accepted',
  REJECTED:'Rejected',
  REVOKED:'Revoked',
  EXPIRED:'Expired',
}[status])

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'Select an active organisation where you are an Organisation Administrator.'
    if (error.problem.status === 404) return 'This invitation is not available in your active organisation.'
    if (error.problem.status === 409) return error.problem.type.includes('concurrent')
      ? 'This invitation changed in another request. Refresh and try again.'
      : 'A pending invitation already exists, or this invitation can no longer be changed.'
  }
  return apiErrorMessage(error, fallback)
}

function replaceInvitation(
  current: StaffInvitationPageResource | undefined,
  invitation: StaffInvitationResource,
): StaffInvitationPageResource {
  if (!current) {
    return { items:[invitation], page:0, size:100, totalElements:1, totalPages:1 }
  }
  const existed = current.items.some(item => item.id === invitation.id)
  return {
    ...current,
    items:[invitation, ...current.items.filter(item => item.id !== invitation.id)]
      .sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
    totalElements:existed ? current.totalElements : current.totalElements + 1,
  }
}

export function OrganisationStaffInvitationsPage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'no-active-organisation'
  const queryKey = ['organisation', organisationId, 'staff-invitations'] as const
  const [composerOpen, setComposerOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [notice, setNotice] = useState('')
  const invitationsQuery = useQuery({
    queryKey,
    queryFn:() => staffInvitationRestService.list(),
  })
  const {
    register,
    handleSubmit,
    reset,
    formState:{ errors },
  } = useForm<InvitationFormValues>({
    resolver:zodResolver(invitationSchema),
    defaultValues,
  })

  const createInvitation = useMutation({
    mutationFn:(command: CreateStaffInvitationCommand) =>
      staffInvitationRestService.create(command),
    onSuccess:invitation => {
      queryClient.setQueryData<StaffInvitationPageResource>(
        queryKey,
        current => replaceInvitation(current, invitation),
      )
      setNotice(`Invitation created for ${invitation.email}.`)
      setComposerOpen(false)
      reset(defaultValues)
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const changeInvitation = useMutation({
    mutationFn:({ invitation, action }: {
      invitation: StaffInvitationResource
      action: 'renew' | 'revoke'
    }) => staffInvitationRestService[action](invitation.id, {
      version:invitation.version,
    }),
    onSuccess:(invitation, variables) => {
      queryClient.setQueryData<StaffInvitationPageResource>(
        queryKey,
        current => replaceInvitation(current, invitation),
      )
      setNotice(variables.action === 'renew'
        ? `Invitation renewed for ${invitation.email}.`
        : `Invitation revoked for ${invitation.email}.`)
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const invitations = invitationsQuery.data?.items || []
  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return invitations
    return invitations.filter(invitation =>
      `${invitation.email} ${roleLabel(invitation.role)} ${statusLabel(invitation.status)}`
        .toLowerCase().includes(normalized),
    )
  }, [invitations, query])

  const submit = handleSubmit(async values => {
    setNotice('')
    try {
      await createInvitation.mutateAsync({
        email:values.email.trim().toLowerCase(),
        role:values.role,
      })
    } catch {
      // The mutation state renders the safe API problem below.
    }
  })

  return <div className="page organization-page super-admin-page staff-invitations-page">
    <div className="page-intro"><div><p className="eyebrow">Active organisation · Authoritative onboarding</p><h1>Staff invitations and access.</h1><p>Invite doctors and receptionists by verified account email. Membership and role access begin only after that account accepts.</p></div><button className="primary soft" onClick={()=>{setComposerOpen(true);setNotice('');createInvitation.reset()}}><UserPlus/>Invite staff member</button></div>

    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}

    {composerOpen&&<form className="order-composer compact-composer staff-invitation-composer" onSubmit={submit} noValidate>
      <header><div><p className="eyebrow">Minimum necessary access</p><h2>Create a staff invitation</h2></div><button type="button" className="icon-button" aria-label="Close invitation form" onClick={()=>setComposerOpen(false)}><X/></button></header>
      <p className="tenant-create__notice"><ShieldCheck/>The person must sign in with this exact verified email. The form never searches or exposes Sahha accounts.</p>
      <div className="order-fields">
        <label><span>Staff email</span><input type="email" {...register('email')} aria-invalid={Boolean(errors.email)} placeholder="doctor@example.com"/>{errors.email&&<small className="login-field-error" role="alert">{errors.email.message}</small>}</label>
        <label><span>Organisation role</span><select {...register('role')}><option value="DOCTOR">Doctor</option><option value="RECEPTIONIST">Receptionist</option></select></label>
      </div>
      {createInvitation.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(createInvitation.error, 'The invitation could not be created.')}</p>}
      <footer><button type="button" className="secondary" onClick={()=>setComposerOpen(false)}>Cancel</button><button className="primary" disabled={createInvitation.isPending}>{createInvitation.isPending?<><LoaderCircle className="spin"/>Creating…</>:<><Mail/>Create invitation</>}</button></footer>
    </form>}

    <section className="staff-invitation-boundary"><ShieldCheck/><span><strong>Identity remains private</strong><small>Organisation administrators submit an email but cannot query the Auth account directory. Acceptance is checked server-side against the signed-in account.</small></span></section>

    <label className="search-field soft-field"><Search/><span className="sr-only">Search staff invitations</span><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Search email, role, or status"/></label>

    {invitationsQuery.isPending&&<div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>Loading invitations</strong><span>Reading only the active organisation.</span></div>}
    {invitationsQuery.isError&&<div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Invitations unavailable</strong><span>{errorMessage(invitationsQuery.error, 'The staff invitation directory could not be loaded.')}</span><button className="secondary" onClick={()=>void invitationsQuery.refetch()}><RefreshCw/>Try again</button></div>}
    {changeInvitation.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(changeInvitation.error, 'The invitation could not be changed.')}</p>}

    {!invitationsQuery.isPending&&!invitationsQuery.isError&&<section className="staff-invitation-list" aria-label="Staff invitations">
      {visible.map(invitation=><article key={invitation.id}>
        <span className="staff-invitation-avatar">{invitation.role==='DOCTOR'?<UserRound/>:<Mail/>}</span>
        <div><strong>{invitation.email}</strong><small>{roleLabel(invitation.role)} · Created {new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(invitation.createdAt))}</small></div>
        <span className={`status status--${statusLabel(invitation.status).toLowerCase()}`}><i/>{statusLabel(invitation.status)}</span>
        <span className="staff-invitation-expiry"><Clock3/><small>{invitation.status==='PENDING'||invitation.status==='EXPIRED'?'Expires':'Resolved'} </small><strong>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(invitation.status==='PENDING'||invitation.status==='EXPIRED'?invitation.expiresAt:invitation.resolvedAt||invitation.updatedAt))}</strong></span>
        <div className="staff-invitation-actions">{(invitation.status==='PENDING'||invitation.status==='EXPIRED')&&<button className="secondary" disabled={changeInvitation.isPending} onClick={()=>changeInvitation.mutate({invitation,action:'renew'})}><RotateCw/>Renew</button>}{invitation.status==='PENDING'&&<button className="text-button" disabled={changeInvitation.isPending} onClick={()=>changeInvitation.mutate({invitation,action:'revoke'})}>Revoke</button>}</div>
      </article>)}
    </section>}

    {!invitationsQuery.isPending&&!invitationsQuery.isError&&!visible.length&&<div className="empty-state"><UserPlus/><h2>{invitations.length?'No invitation found':'No staff invitations yet'}</h2><p>{invitations.length?'Try another email, role, or status.':'Create the first invitation for a doctor or receptionist.'}</p>{!invitations.length&&<button className="primary soft" onClick={()=>setComposerOpen(true)}><UserPlus/>Invite staff member</button>}</div>}
  </div>
}
