import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  Check,
  Clock3,
  Link2,
  LoaderCircle,
  MapPin,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import type { AvailableSlotResource } from '../../models/scheduling'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { appointmentRestService } from '../../services/api/appointmentRestService'
import { availabilityRestService } from '../../services/api/availabilityRestService'
import { patientRegistryRestService } from '../../services/api/patientRegistryRestService'

const dateValue = (days: number) => {
  const value = new Date()
  value.setDate(value.getDate() + days)
  return value.toISOString().slice(0, 10)
}

const appointmentRange = () => {
  const from = new Date()
  from.setDate(from.getDate() - 7)
  const to = new Date(from)
  to.setDate(to.getDate() + 31)
  return { from:from.toISOString(), to:to.toISOString() }
}

const formatDateTime = (value: string) => new Intl.DateTimeFormat(undefined, {
  weekday:'short', day:'numeric', month:'short', hour:'2-digit', minute:'2-digit',
}).format(new Date(value))

const statusLabel = (value: string) => value.toLowerCase()
  .split('_').map(part => part[0].toUpperCase() + part.slice(1)).join(' ')

export function PatientAppointmentsPage() {
  const queryClient = useQueryClient()
  const [registrationId, setRegistrationId] = useState('')
  const [doctorUserId, setDoctorUserId] = useState('')
  const [from, setFrom] = useState(dateValue(1))
  const [to, setTo] = useState(dateValue(7))
  const [selectedSlot, setSelectedSlot] = useState<AvailableSlotResource | null>(null)
  const [notice, setNotice] = useState('')
  const [linkForm, setLinkForm] = useState({
    organisationId:'', medicalRecordNumber:'', dateOfBirth:'',
  })

  const registrationsQuery = useQuery({
    queryKey:['my-patient-registrations'],
    queryFn:patientRegistryRestService.listMyRegistrations,
    retry:false,
  })

  const registrations = registrationsQuery.data || []
  useEffect(() => {
    if (!registrations.length) return
    if (!registrations.some(item => item.registrationId === registrationId)) {
      setRegistrationId(registrations[0].registrationId)
    }
  }, [registrationId, registrations])

  const doctorsQuery = useQuery({
    queryKey:['my-patient-doctors', registrationId],
    queryFn:() => availabilityRestService.listMyPatientDoctors(registrationId),
    enabled:Boolean(registrationId),
  })

  useEffect(() => {
    const doctors = doctorsQuery.data || []
    if (!doctors.length) {
      setDoctorUserId('')
      return
    }
    if (!doctors.some(item => item.doctorUserId === doctorUserId)) {
      setDoctorUserId(doctors[0].doctorUserId)
      setSelectedSlot(null)
    }
  }, [doctorUserId, doctorsQuery.data])

  const slotsKey = ['my-patient-slots', registrationId, doctorUserId, from, to]
  const slotsQuery = useQuery({
    queryKey:slotsKey,
    queryFn:() => availabilityRestService.myPatientSlots(
      registrationId, doctorUserId, from, to,
    ),
    enabled:Boolean(registrationId && doctorUserId && from && to && to >= from),
  })

  const range = useMemo(appointmentRange, [])
  const appointmentsKey = ['my-patient-appointments', registrationId, range.from, range.to]
  const appointmentsQuery = useQuery({
    queryKey:appointmentsKey,
    queryFn:() => appointmentRestService.listMine(
      registrationId, range.from, range.to,
    ),
    enabled:Boolean(registrationId),
  })

  const linkMutation = useMutation({
    mutationFn:patientRegistryRestService.linkMyAccount,
    onSuccess:() => {
      setNotice('Your verified account is now linked to the patient registration.')
      void queryClient.invalidateQueries({ queryKey:['my-patient-registrations'] })
    },
  })

  const bookingMutation = useMutation({
    mutationFn:appointmentRestService.bookMine,
    onSuccess:() => {
      setNotice('Your appointment request was sent.')
      setSelectedSlot(null)
      void queryClient.invalidateQueries({ queryKey:appointmentsKey })
      void queryClient.invalidateQueries({ queryKey:slotsKey })
    },
  })

  const submitLink = (event: FormEvent) => {
    event.preventDefault()
    setNotice('')
    linkMutation.mutate({
      ...linkForm,
      medicalRecordNumber:linkForm.medicalRecordNumber.trim().toUpperCase(),
    })
  }

  const requestSlot = () => {
    if (!selectedSlot || !registrationId || !doctorUserId) return
    bookingMutation.mutate({
      bookingRequestId:crypto.randomUUID(),
      patientRegistrationId:registrationId,
      doctorUserId,
      startsAt:selectedSlot.startsAt,
    })
  }

  const selectedDoctor = doctorsQuery.data?.find(
    doctor => doctor.doctorUserId === doctorUserId,
  )
  const appointments = appointmentsQuery.data || []
  const upcoming = appointments.filter(item => new Date(item.endsAt) >= new Date())
  const recent = appointments.filter(item => new Date(item.endsAt) < new Date())

  if (registrationsQuery.isPending) {
    return <div className="page patient-appointment-self patient-appointment-loading">
      <LoaderCircle className="spin"/><strong>Opening your private appointment space…</strong>
    </div>
  }

  if (registrationsQuery.isError
      && (!(registrationsQuery.error instanceof ApiError)
        || registrationsQuery.error.problem.status !== 404)) {
    return <div className="page patient-appointment-self patient-appointment-loading">
      <ShieldCheck/>
      <strong>Your private appointment space could not be opened.</strong>
      <p className="form-message form-message--error" role="alert">
        {apiErrorMessage(registrationsQuery.error, 'Your linked patient registrations could not be loaded.')}
      </p>
      <button className="secondary soft" onClick={()=>registrationsQuery.refetch()}>
        <RefreshCw/>Retry
      </button>
    </div>
  }

  if (registrationsQuery.isError || registrations.length === 0) {
    return <div className="page patient-appointment-self">
      <header className="patient-appointment-heading">
        <div><p className="eyebrow">Verified patient access</p><h1>Connect your existing record.</h1><p>This one-time step proves that the signed-in account owns the registration. Sahha checks the verified account email, organisation, medical record number, and date of birth together.</p></div>
        <ShieldCheck/>
      </header>
      <form className="patient-link-card" onSubmit={submitLink}>
        <div><Link2/><span><strong>Account-to-record link</strong><small>Ask your clinic for the organisation ID and medical record number shown on your registration.</small></span></div>
        <label><span>Organisation ID</span><input required value={linkForm.organisationId} onChange={event=>setLinkForm(current=>({...current,organisationId:event.target.value}))} placeholder="00000000-0000-0000-0000-000000000000"/></label>
        <label><span>Medical record number</span><input required pattern="PT-[0-9A-Fa-f]{12}" value={linkForm.medicalRecordNumber} onChange={event=>setLinkForm(current=>({...current,medicalRecordNumber:event.target.value}))} placeholder="PT-12AB34CD56EF"/></label>
        <label><span>Date of birth</span><input required type="date" max={dateValue(-1)} value={linkForm.dateOfBirth} onChange={event=>setLinkForm(current=>({...current,dateOfBirth:event.target.value}))}/></label>
        {linkMutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(linkMutation.error, 'The registration could not be matched to this verified account.')}</p>}
        <button className="primary soft" disabled={linkMutation.isPending}>{linkMutation.isPending?<><LoaderCircle className="spin"/>Verifying…</>:<><ShieldCheck/>Verify and connect</>}</button>
      </form>
    </div>
  }

  return <div className="page patient-appointment-self">
    <header className="patient-appointment-heading">
      <div><p className="eyebrow">Your schedule · Gateway connected</p><h1>Appointments that stay simple.</h1><p>Choose a published doctor slot and follow the status of requests that belong only to your linked patient registration.</p></div>
      <CalendarDays/>
    </header>

    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}

    <section className="patient-booking-card">
      <header><div><p className="eyebrow">Request a consultation</p><h2>Find an available time</h2></div><Clock3/></header>
      <div className="patient-booking-filters">
        <label><span>Patient registration</span><select value={registrationId} onChange={event=>{setRegistrationId(event.target.value);setSelectedSlot(null)}}>{registrations.filter(item=>item.status==='ACTIVE').map(item=><option value={item.registrationId} key={item.registrationId}>{item.firstName} {item.lastName} · {item.medicalRecordNumber}</option>)}</select></label>
        <label><span>Doctor</span><select value={doctorUserId} onChange={event=>{setDoctorUserId(event.target.value);setSelectedSlot(null)}} disabled={doctorsQuery.isPending}><option value="">{doctorsQuery.isPending?'Loading doctors…':'Choose a doctor'}</option>{doctorsQuery.data?.map(doctor=><option value={doctor.doctorUserId} key={doctor.doctorUserId}>{doctor.displayName} · {doctor.locationLabel}</option>)}</select></label>
        <label><span>From</span><input type="date" min={dateValue(0)} value={from} onChange={event=>{setFrom(event.target.value);setSelectedSlot(null)}}/></label>
        <label><span>To</span><input type="date" min={from} value={to} onChange={event=>{setTo(event.target.value);setSelectedSlot(null)}}/></label>
      </div>
      {selectedDoctor&&<div className="patient-doctor-context"><MapPin/><span><strong>{selectedDoctor.displayName}</strong><small>{selectedDoctor.locationLabel} · {selectedDoctor.appointmentDurationMinutes} minutes · {selectedDoctor.timeZone}</small></span></div>}
      {doctorsQuery.isError&&<p className="form-message form-message--error">{apiErrorMessage(doctorsQuery.error, 'Available doctors could not be loaded.')}</p>}
      {slotsQuery.isPending&&doctorUserId&&<div className="patient-slot-state"><LoaderCircle className="spin"/>Calculating open times…</div>}
      {slotsQuery.isError&&<div className="patient-slot-state"><span>{apiErrorMessage(slotsQuery.error, 'Available slots could not be loaded.')}</span><button className="secondary" onClick={()=>slotsQuery.refetch()}><RefreshCw/>Retry</button></div>}
      <div className="patient-slot-grid">{slotsQuery.data?.slots.map(slot=><button type="button" className={selectedSlot?.startsAt===slot.startsAt?'selected':''} onClick={()=>{bookingMutation.reset();setSelectedSlot(slot)}} key={slot.startsAt}><span>{new Intl.DateTimeFormat(undefined,{weekday:'short',day:'numeric',month:'short'}).format(new Date(slot.startsAt))}</span><strong>{slot.localStartTime.slice(0,5)}</strong><small>{slot.localEndTime.slice(0,5)}</small></button>)}</div>
      {slotsQuery.data&&!slotsQuery.data.slots.length&&<div className="patient-slot-state"><CalendarDays/>No open times in this date range.</div>}
      {bookingMutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(bookingMutation.error, 'This slot could not be requested.')}</p>}
      <footer><span>{selectedSlot?`Selected: ${formatDateTime(selectedSlot.startsAt)}`:'Choose an open time to continue.'}</span><button className="primary soft" disabled={!selectedSlot||bookingMutation.isPending} onClick={requestSlot}>{bookingMutation.isPending?<><LoaderCircle className="spin"/>Requesting…</>:<><CalendarDays/>Request appointment</>}</button></footer>
    </section>

    <section className="patient-appointment-ledger">
      <header><div><p className="eyebrow">Status history</p><h2>Your linked appointments</h2></div><button className="secondary soft" onClick={()=>appointmentsQuery.refetch()} disabled={appointmentsQuery.isFetching}><RefreshCw className={appointmentsQuery.isFetching?'spin':''}/>Refresh</button></header>
      {appointmentsQuery.isError&&<p className="form-message form-message--error">{apiErrorMessage(appointmentsQuery.error, 'Your appointments could not be loaded.')}</p>}
      {[...upcoming,...recent].map(appointment=><article key={appointment.id}>
        <div className="visit-date"><strong>{new Date(appointment.startsAt).getDate()}</strong><span>{new Intl.DateTimeFormat(undefined,{month:'short'}).format(new Date(appointment.startsAt)).toUpperCase()}<small>{new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit'}).format(new Date(appointment.startsAt))}</small></span></div>
        <span><strong>{selectedDoctor?.doctorUserId===appointment.doctorUserId?selectedDoctor.displayName:`Doctor ${appointment.doctorUserId.slice(0,8)}`}</strong><small><MapPin/>{appointment.locationLabel} · {appointment.timeZone}</small>{appointment.statusReason&&<em>{appointment.statusReason}</em>}</span>
        <i className={`appointment-status appointment-status--${appointment.status.toLowerCase()}`}>{statusLabel(appointment.status)}</i>
      </article>)}
      {appointmentsQuery.data&&!appointments.length&&<div className="patient-slot-state"><CalendarDays/>No appointments in the current 31-day window.</div>}
    </section>
  </div>
}
