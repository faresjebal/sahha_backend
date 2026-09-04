import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarClock,
  CalendarDays,
  Check,
  History,
  LoaderCircle,
  MapPin,
  RefreshCw,
  ShieldCheck,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../app/auth/AuthProvider'
import type {
  AppointmentResource,
  AppointmentStatus,
} from '../../models/scheduling'
import { apiErrorMessage } from '../../services/api/ApiError'
import { appointmentRestService } from '../../services/api/appointmentRestService'
import { availabilityRestService } from '../../services/api/availabilityRestService'
import { consultationRestService } from '../../services/api/consultationRestService'
import { AvailableSlotBrowser } from './AvailableSlotBrowser'

type Scope = 'doctor' | 'reception'
type AppointmentRangeView = 'upcoming' | 'history'
type LifecycleAction =
  | 'confirm'
  | 'reject'
  | 'reschedule'
  | 'cancel'
  | 'check-in'
  | 'start'
  | 'no-show'

const reasonedActions: LifecycleAction[] = ['reject', 'reschedule', 'cancel']

const RANGE_LENGTH_MS = 31 * 24 * 60 * 60 * 1_000

const appointmentRange = (
  anchor: number,
  view: AppointmentRangeView,
) => {
  const startOfToday = new Date(anchor)
  startOfToday.setHours(0, 0, 0, 0)
  const boundary = startOfToday.getTime()
  const from = view === 'upcoming'
    ? boundary
    : boundary - RANGE_LENGTH_MS
  const to = view === 'upcoming'
    ? boundary + RANGE_LENGTH_MS
    : boundary
  return {
    from:new Date(from).toISOString(),
    to:new Date(to).toISOString(),
  }
}

const dateInput = (daysFromNow: number) => {
  const value = new Date()
  value.setDate(value.getDate() + daysFromNow)
  return value.toISOString().slice(0, 10)
}

const appointmentActions = (
  appointment: AppointmentResource,
  scope: Scope,
): LifecycleAction[] => {
  if (appointment.status === 'REQUESTED' || appointment.status === 'RESCHEDULED') {
    return scope === 'doctor'
      ? ['confirm', 'reject', 'reschedule', 'cancel'] as LifecycleAction[]
      : ['reschedule', 'cancel'] as LifecycleAction[]
  }
  if (appointment.status === 'CONFIRMED') {
    const noShow: LifecycleAction[] =
      new Date(appointment.startsAt).getTime() <= Date.now()
      ? ['no-show']
      : []
    return scope === 'doctor'
      ? ['reschedule', 'cancel', ...noShow]
      : ['check-in', 'reschedule', 'cancel', ...noShow]
  }
  if (appointment.status === 'CHECKED_IN' && scope === 'doctor') return ['start']
  return []
}

const actionLabel = (action: LifecycleAction) => ({
  confirm:'Confirm appointment',
  reject:'Reject request',
  reschedule:'Reschedule appointment',
  cancel:'Cancel appointment',
  'check-in':'Check in patient',
  start:'Start appointment',
  'no-show':'Mark as no-show',
}[action])

const statusLabel = (status: AppointmentStatus) =>
  status.replaceAll('_', ' ').toLowerCase().replace(/^./, value =>
    value.toUpperCase())

export function AppointmentLifecyclePage({ scope }: { scope: Scope }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'none'
  const [rangeView, setRangeView] = useState<AppointmentRangeView>('upcoming')
  const [rangeAnchor, setRangeAnchor] = useState(() => Date.now())
  const range = useMemo(
    () => appointmentRange(rangeAnchor, rangeView),
    [rangeAnchor, rangeView],
  )
  const [selected, setSelected] = useState<AppointmentResource | null>(null)
  const [action, setAction] = useState<LifecycleAction>('reschedule')
  const [reason, setReason] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [notice, setNotice] = useState('')

  const appointmentsKey = [
    'appointments', organisationId, range.from, range.to,
  ] as const
  const appointmentsQuery = useQuery({
    queryKey:appointmentsKey,
    queryFn:() => appointmentRestService.list(range.from, range.to),
    enabled:organisationId !== 'none',
  })

  const slotsQuery = useQuery({
    queryKey:[
      'appointment-reschedule-slots',
      organisationId,
      selected?.doctorUserId,
    ],
    queryFn:() => availabilityRestService.slots(
      selected!.doctorUserId,
      dateInput(0),
      dateInput(30),
    ),
    enabled:Boolean(selected && action === 'reschedule'),
  })

  const consultationMutation = useMutation({
    mutationFn:(appointmentId:string) =>
      consultationRestService.create({ appointmentId }),
    onSuccess:consultation => {
      navigate(`/doctor/clinical/${consultation.id}`)
    },
  })

  useEffect(() => {
    if (action !== 'reschedule' || !slotsQuery.data) return
    const valid = slotsQuery.data.slots.some(slot => slot.startsAt === startsAt)
    if (!valid) setStartsAt(slotsQuery.data.slots[0]?.startsAt || '')
  }, [action, slotsQuery.data, startsAt])

  const mutation = useMutation({
    mutationFn:async ({
      appointment,
      nextAction,
      transitionReason,
      nextStartsAt,
    }: {
      appointment: AppointmentResource
      nextAction: LifecycleAction
      transitionReason: string
      nextStartsAt: string
    }) => {
      const commandRequestId = crypto.randomUUID()
      if (nextAction === 'confirm') {
        return appointmentRestService.confirm(appointment.id, {
          commandRequestId,
          version:appointment.version,
        })
      }
      const command = {
        commandRequestId,
        version:appointment.version,
      }
      if (nextAction === 'check-in') {
        return appointmentRestService.checkIn(appointment.id, command)
      }
      if (nextAction === 'start') {
        return appointmentRestService.start(appointment.id, command)
      }
      if (nextAction === 'no-show') {
        return appointmentRestService.noShow(appointment.id, command)
      }
      if (nextAction === 'reschedule') {
        return appointmentRestService.reschedule(appointment.id, {
          ...command,
          startsAt:nextStartsAt,
          reason:transitionReason.trim(),
        })
      }
      const reasonedCommand = {
        ...command,
        reason:transitionReason.trim(),
      }
      return nextAction === 'reject'
        ? appointmentRestService.reject(appointment.id, reasonedCommand)
        : appointmentRestService.cancel(appointment.id, reasonedCommand)
    },
    onSuccess:(appointment, variables) => {
      queryClient.setQueryData<AppointmentResource[]>(
        appointmentsKey,
        current => current?.map(value =>
          value.id === appointment.id ? appointment : value) || [appointment],
      )
      void queryClient.invalidateQueries({
        queryKey:['available-slots', organisationId],
      })
      void queryClient.invalidateQueries({
        queryKey:['appointment-reschedule-slots', organisationId],
      })
      setNotice(`Appointment ${appointment.id.slice(0, 8)} is now ${statusLabel(appointment.status).toLowerCase()}.`)
      setSelected(null)
      if (variables.nextAction === 'start') {
        consultationMutation.mutate(appointment.id)
      }
    },
  })

  const open = (appointment: AppointmentResource) => {
    const available = appointmentActions(appointment, scope)
    if (!available.length) return
    mutation.reset()
    setSelected(appointment)
    setAction(available[0])
    setReason('')
    setStartsAt('')
  }

  const submit = () => {
    if (!selected) return
    const reasonRequired = reasonedActions.includes(action)
    if (reasonRequired && !reason.trim()) return
    if (action === 'reschedule' && !startsAt) return
    mutation.mutate({
      appointment:selected,
      nextAction:action,
      transitionReason:reason,
      nextStartsAt:startsAt,
    })
  }

  const appointments = appointmentsQuery.data || []
  const waitingAppointments = appointments.filter(
    appointment => appointment.status === 'CHECKED_IN',
  )
  const allowedActions = selected
    ? appointmentActions(selected, scope)
    : []
  const canSubmit = Boolean(
    selected
    && (!reasonedActions.includes(action) || reason.trim())
    && (action !== 'reschedule' || startsAt),
  )

  const changeRange = (view: AppointmentRangeView) => {
    setRangeAnchor(Date.now())
    setRangeView(view)
    setSelected(null)
  }

  return <div className="page workflow-page appointment-lifecycle-page">
    <header className="appointment-lifecycle-heading">
      <div>
        <p className="eyebrow">{scope === 'doctor' ? 'Clinical schedule' : 'Patient services'} · Live Scheduling Service</p>
        <h1>Appointments with accountable states.</h1>
        <p>{scope === 'doctor'
          ? 'Confirm requests, respond to checked-in patients, and close each visit through an accountable state transition.'
          : 'Book available slots, check patients in, and maintain a non-clinical waiting list without receiving clinical authority.'}</p>
      </div>
      <ShieldCheck/>
    </header>

    {scope === 'reception'&&<AvailableSlotBrowser/>}
    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}
    {consultationMutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(consultationMutation.error, 'The appointment is in progress, but its consultation could not be opened. Use Open consultation to retry safely.')}</p>}

    <section className="appointment-range-toolbar" aria-labelledby="appointment-range-title">
      <div>
        <p className="eyebrow">Schedule window</p>
        <h2 id="appointment-range-title">
          {rangeView === 'upcoming' ? 'Today and upcoming' : 'Appointment history'}
        </h2>
        <p>{rangeView === 'upcoming'
          ? 'Today plus the next 30 days.'
          : 'The previous 31 days, kept separate from active scheduling.'}</p>
      </div>
      <div className="appointment-range-switch" role="group" aria-label="Appointment date range">
        <button
          type="button"
          className={rangeView === 'upcoming' ? 'active' : ''}
          aria-pressed={rangeView === 'upcoming'}
          onClick={() => changeRange('upcoming')}
        ><CalendarDays/>Upcoming</button>
        <button
          type="button"
          className={rangeView === 'history' ? 'active' : ''}
          aria-pressed={rangeView === 'history'}
          onClick={() => changeRange('history')}
        ><History/>History</button>
      </div>
    </section>

    {appointmentsQuery.isPending&&<div className="appointment-lifecycle-state"><LoaderCircle className="spin"/>Loading appointments…</div>}
    {appointmentsQuery.isError&&<div className="appointment-lifecycle-state"><p>{apiErrorMessage(appointmentsQuery.error, 'Appointments could not be loaded.')}</p><button className="secondary" onClick={()=>appointmentsQuery.refetch()}><RefreshCw/>Retry</button></div>}

    {scope==='reception'&&!appointmentsQuery.isPending&&!appointmentsQuery.isError&&<section className="appointment-waiting-list" aria-labelledby="appointment-waiting-title">
      <header><div><p className="eyebrow">Live patient flow</p><h2 id="appointment-waiting-title">Waiting patients</h2></div><span>{waitingAppointments.length} waiting</span></header>
      {waitingAppointments.map(appointment=><article key={appointment.id}>
        <CalendarClock/>
        <span><strong>Registration {appointment.patientRegistrationId.slice(0,8)}</strong><small>Checked in {new Intl.DateTimeFormat(undefined,{dateStyle:'medium',timeStyle:'short'}).format(new Date(appointment.updatedAt))}</small></span>
        <span><strong>Doctor {appointment.doctorUserId.slice(0,8)}</strong><small>{appointment.locationLabel}</small></span>
        <i className="appointment-status appointment-status--checked_in">Waiting</i>
      </article>)}
      {!waitingAppointments.length&&<div className="appointment-waiting-empty"><CalendarClock/><span><strong>No patients are waiting</strong><small>Confirmed appointments appear here after reception checks them in.</small></span></div>}
    </section>}

    {!appointmentsQuery.isPending&&!appointmentsQuery.isError&&<section className="appointment-lifecycle-table">
      <header><span>Visit</span><span>Patient</span><span>Doctor</span><span>Location</span><span>Status</span><span/></header>
      {appointments.map(appointment => {
        const available = appointmentActions(appointment, scope)
        const canOpenConsultation = scope === 'doctor'
          && appointment.status === 'IN_PROGRESS'
        return <article key={appointment.id}>
          <span><strong>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium',timeStyle:'short'}).format(new Date(appointment.startsAt))}</strong><small>{appointment.id.slice(0,8)} · {Math.round((new Date(appointment.endsAt).getTime()-new Date(appointment.startsAt).getTime())/60_000)} min</small></span>
          <span><strong>Registration {appointment.patientRegistrationId.slice(0,8)}</strong><small>Administrative identifier only</small></span>
          <span><strong>Doctor {appointment.doctorUserId.slice(0,8)}</strong><small>Organisation-scoped</small></span>
          <span><strong>{appointment.locationLabel}</strong><small>{appointment.timeZone}</small></span>
          <span><i className={`appointment-status appointment-status--${appointment.status.toLowerCase()}`}>{statusLabel(appointment.status)}</i>{appointment.statusReason&&<small>{appointment.statusReason}</small>}</span>
          <button
            className={canOpenConsultation ? 'primary' : 'secondary'}
            disabled={!available.length&&!canOpenConsultation||consultationMutation.isPending}
            onClick={()=>canOpenConsultation
              ? consultationMutation.mutate(appointment.id)
              : open(appointment)}
          >{canOpenConsultation?'Open consultation':available.length?'Manage':'Closed'}</button>
        </article>
      })}
      {!appointments.length&&<div className="appointment-lifecycle-state"><CalendarDays/><strong>No appointments in this range</strong><span>{rangeView === 'history'?'No appointments were recorded during the previous 31 days.':scope === 'reception'?'Book a published slot above to create the first request.':'New requests will appear after reception books an available slot.'}</span></div>}
    </section>}

    {selected&&<div className="appointment-command-backdrop" role="presentation" onMouseDown={event=>{if(event.target===event.currentTarget)setSelected(null)}}>
      <section className="appointment-command-dialog" role="dialog" aria-modal="true" aria-labelledby="appointment-command-title">
        <header><div><p className="eyebrow">Version {selected.version} · {statusLabel(selected.status)}</p><h2 id="appointment-command-title">Manage appointment {selected.id.slice(0,8)}</h2></div><button className="icon-button" aria-label="Close appointment command" onClick={()=>setSelected(null)}><X/></button></header>
        <div className="appointment-command-summary"><CalendarClock/><span><strong>{new Intl.DateTimeFormat(undefined,{dateStyle:'full',timeStyle:'short'}).format(new Date(selected.startsAt))}</strong><small><MapPin/>{selected.locationLabel}</small></span></div>
        <label><span>Allowed action</span><select value={action} onChange={event=>{setAction(event.target.value as LifecycleAction);setReason('');mutation.reset()}}>{allowedActions.map(value=><option value={value} key={value}>{actionLabel(value)}</option>)}</select></label>
        {action==='reschedule'&&<label><span>Exact published slot</span><select value={startsAt} onChange={event=>setStartsAt(event.target.value)} disabled={slotsQuery.isPending||slotsQuery.isError}><option value="">{slotsQuery.isPending?'Loading available slots':'Choose a new slot'}</option>{slotsQuery.data?.slots.map(slot=><option value={slot.startsAt} key={slot.startsAt}>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium',timeStyle:'short'}).format(new Date(slot.startsAt))}</option>)}</select></label>}
        {slotsQuery.isError&&action==='reschedule'&&<p className="form-message form-message--error">{apiErrorMessage(slotsQuery.error, 'Available reschedule slots could not be loaded.')}</p>}
        {reasonedActions.includes(action)&&<label><span>{action==='reject'?'Rejection':action==='cancel'?'Cancellation':'Reschedule'} reason</span><textarea rows={4} maxLength={500} value={reason} onChange={event=>setReason(event.target.value)} placeholder="Record the reason for the audit history"/></label>}
        {mutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(mutation.error, 'The appointment could not be updated. Refresh and try again.')}</p>}
        <footer><button className="secondary" onClick={()=>setSelected(null)}>Close</button><button className="primary" disabled={!canSubmit||mutation.isPending} onClick={submit}>{mutation.isPending?<><LoaderCircle className="spin"/>Applying…</>:actionLabel(action)}</button></footer>
      </section>
    </div>}
  </div>
}
