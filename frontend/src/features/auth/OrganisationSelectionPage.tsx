import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Building2, HeartPulse, LoaderCircle, Mail, ShieldCheck, X } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../app/auth/AuthProvider'
import { roleHome } from '../../app/auth/roleRoutes'
import { ApiError } from '../../services/api/ApiError'
import { organisationRestService } from '../../services/api/organisationRestService'
import type { StaffInvitationResource } from '../../models/organisation'
import { staffInvitationRestService } from '../../services/api/staffInvitationRestService'

const roleLabel = (role: string) => ({
  ORGANIZATION_ADMIN:'Organisation administrator',
  DOCTOR:'Doctor',
  RECEPTIONIST:'Receptionist',
}[role] || role)

const errorMessage = (error: unknown) => {
  if (!(error instanceof ApiError)) return 'The organisation could not be selected.'
  if (error.problem.status === 404) return 'This membership is no longer active.'
  if (error.problem.status === 503) return 'Organisation verification is temporarily unavailable.'
  return error.problem.detail || 'The organisation could not be selected.'
}

export function OrganisationSelectionPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [invitationNotice, setInvitationNotice] = useState('')
  const contexts = useQuery({
    queryKey:['organisation-contexts', auth.session?.user.id],
    queryFn:organisationRestService.listMyContexts,
  })
  const selection = useMutation({
    mutationFn:(organisationId: string) => auth.selectActiveOrganisation(organisationId),
    onSuccess:async session => {
      await queryClient.invalidateQueries()
      navigate(roleHome[session.user.role], { replace:true })
    },
  })
  const invitations = useQuery({
    queryKey:['my-staff-invitations', auth.session?.user.id],
    queryFn:() => staffInvitationRestService.listMine(),
  })
  const invitationDecision = useMutation({
    mutationFn:({ invitation, decision }: {
      invitation: StaffInvitationResource
      decision: 'accept' | 'reject'
    }) => staffInvitationRestService[decision](invitation.id, {
      version:invitation.version,
    }),
    onSuccess:async (invitation, variables) => {
      setInvitationNotice(variables.decision === 'accept'
        ? 'Membership activated. Select the organisation below to enter its workspace.'
        : 'Invitation rejected.')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey:['my-staff-invitations'] }),
        queryClient.invalidateQueries({ queryKey:['organisation-contexts'] }),
      ])
    },
  })

  const actionableInvitations = invitations.data?.items.filter(
    invitation => invitation.status === 'PENDING' || invitation.status === 'EXPIRED',
  ) || []

  const continueWithoutOrganisation = () => {
    if (!auth.session) return
    navigate(roleHome[auth.session.user.role], { replace:true })
  }

  return <main className="organisation-selector">
    <section className="organisation-selector__panel">
      <header>
        <span className="organisation-selector__brand"><HeartPulse/>Sahha</span>
        <p className="eyebrow">Secure workspace context</p>
        <h1>Where are you working today?</h1>
        <p>Your permissions are limited to the organisation you select. Roles from separate organisations are never combined.</p>
      </header>

      {contexts.isLoading&&<div className="organisation-selector__state"><LoaderCircle className="spin"/><strong>Loading your memberships</strong></div>}
      {contexts.isError&&<div className="organisation-selector__state is-error" role="alert"><strong>Memberships unavailable</strong><span>{errorMessage(contexts.error)}</span><button className="secondary" onClick={()=>contexts.refetch()}>Try again</button></div>}

      {contexts.data&&contexts.data.length>0&&<div className="organisation-selector__list">
        {contexts.data.map(context=><button
          key={context.membershipId}
          className="organisation-selector__choice"
          disabled={selection.isPending}
          onClick={()=>selection.mutate(context.organisationId)}
        >
          <span><Building2/></span>
          <div><strong>{context.organisationName}</strong><small>{context.organisationType.replaceAll('_', ' ').toLowerCase()}</small><p>{context.roles.map(roleLabel).join(' · ')}</p></div>
          {selection.isPending&&selection.variables===context.organisationId?<LoaderCircle className="spin"/>:<ArrowRight/>}
        </button>)}
      </div>}

      {(invitations.isPending||actionableInvitations.length>0||invitations.isError)&&<section className="organisation-selector__invitations" aria-label="Staff invitations">
        <div><Mail/><span><strong>Staff invitations</strong><small>Matched by your verified Sahha account email.</small></span></div>
        {invitationNotice&&<p className="organisation-selector__invitation-notice" role="status"><ShieldCheck/>{invitationNotice}</p>}
        {invitations.isPending&&<p><LoaderCircle className="spin"/>Checking invitations…</p>}
        {invitations.isError&&<p className="organisation-selector__error" role="alert">Invitations could not be checked. Your organisation memberships are still available above.</p>}
        {actionableInvitations.map(invitation=><article key={invitation.id}>
          <span><strong>{invitation.organisationName}</strong><small>{invitation.role==='DOCTOR'?'Doctor':'Receptionist'} · {invitation.status==='EXPIRED'?'Expired':'Expires'} {new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(invitation.expiresAt))}</small></span>
          {invitation.status==='PENDING'?<div><button className="secondary" disabled={invitationDecision.isPending} onClick={()=>invitationDecision.mutate({invitation,decision:'reject'})}><X/>Reject</button><button className="primary" disabled={invitationDecision.isPending} onClick={()=>invitationDecision.mutate({invitation,decision:'accept'})}>{invitationDecision.isPending&&invitationDecision.variables?.invitation.id===invitation.id?<LoaderCircle className="spin"/>:<ArrowRight/>}Accept</button></div>:<small>Ask the organisation administrator to renew this invitation.</small>}
        </article>)}
        {invitationDecision.isError&&<p className="organisation-selector__error" role="alert">{errorMessage(invitationDecision.error)}</p>}
      </section>}

      {contexts.data?.length===0&&<div className="organisation-selector__state">
        <ShieldCheck/>
        <strong>No organisation membership</strong>
        <span>You can continue using your personal Sahha workspace.</span>
        <button className="primary" onClick={continueWithoutOrganisation}>Continue <ArrowRight/></button>
      </div>}

      {selection.isError&&<p className="organisation-selector__error" role="alert">{errorMessage(selection.error)}</p>}
      <footer><ShieldCheck/>The server validates your membership before renewing the access cookie. Your refresh token is not rotated.</footer>
    </section>
  </main>
}
