import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  Check,
  Clock3,
  LoaderCircle,
  MapPin,
  RefreshCw,
  Search,
  UserRound,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/auth/AuthProvider'
import type { AvailableSlotResource } from '../../models/scheduling'
import { apiErrorMessage } from '../../services/api/ApiError'
import { appointmentRestService } from '../../services/api/appointmentRestService'
import { availabilityRestService } from '../../services/api/availabilityRestService'
import { patientRegistryRestService } from '../../services/api/patientRegistryRestService'

const dateInput = (days: number) => {
  const value = new Date()
  value.setDate(value.getDate() + days)
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

type SelectedSlot = {
  slot: AvailableSlotResource
  bookingRequestId: string
}

export function AvailableSlotBrowser() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'none'
  const [doctorUserId, setDoctorUserId] = useState('')
  const [from, setFrom] = useState(dateInput(1))
  const [to, setTo] = useState(dateInput(7))
  const [selectedSlot, setSelectedSlot] = useState<SelectedSlot | null>(null)
  const [patientQuery, setPatientQuery] = useState('')
  const [patientRegistrationId, setPatientRegistrationId] = useState('')
  const [bookingNotice, setBookingNotice] = useState('')

  const doctorsQuery = useQuery({
    queryKey:['availability-doctors', organisationId],
    queryFn:availabilityRestService.listDoctors,
    enabled:organisationId !== 'none',
  })

  useEffect(() => {
    if (!doctorsQuery.data) return
    const stillPublished = doctorsQuery.data.some(
      value => value.doctorUserId === doctorUserId,
    )
    if (!stillPublished) {
      setDoctorUserId(doctorsQuery.data[0]?.doctorUserId || '')
      setSelectedSlot(null)
    }
  }, [doctorUserId, doctorsQuery.data])

  const slotsKey = [
    'available-slots',
    organisationId,
    doctorUserId,
    from,
    to,
  ] as const
  const slotsQuery = useQuery({
    queryKey:slotsKey,
    queryFn:() => availabilityRestService.slots(doctorUserId, from, to),
    enabled:Boolean(
      organisationId !== 'none'
      && doctorsQuery.data?.some(value => value.doctorUserId === doctorUserId)
      && from
      && to
      && to >= from,
    ),
  })

  const patientsQuery = useQuery({
    queryKey:['appointment-patients', organisationId, patientQuery],
    queryFn:() => patientRegistryRestService.list(patientQuery, 0, 25),
    enabled:Boolean(selectedSlot && organisationId !== 'none'),
  })

  const activePatients = useMemo(
    () => patientsQuery.data?.items.filter(
      patient => patient.registrationStatus === 'ACTIVE',
    ) || [],
    [patientsQuery.data],
  )

  useEffect(() => {
    if (!patientsQuery.data) return
    const stillVisible = activePatients.some(
      patient => patient.registrationId === patientRegistrationId,
    )
    if (!stillVisible) {
      setPatientRegistrationId(activePatients[0]?.registrationId || '')
    }
  }, [activePatients, patientRegistrationId, patientsQuery.data])

  const bookingMutation = useMutation({
    mutationFn:appointmentRestService.book,
    onSuccess:appointment => {
      setBookingNotice(
        `Appointment ${appointment.id.slice(0, 8)} was requested.`,
      )
      setSelectedSlot(null)
      setPatientQuery('')
      setPatientRegistrationId('')
      void queryClient.invalidateQueries({ queryKey:slotsKey })
      void queryClient.invalidateQueries({
        queryKey:['appointments', organisationId],
      })
    },
  })

  const selectedDoctor = doctorsQuery.data?.find(
    value => value.doctorUserId === doctorUserId,
  )
  const selectedPatient = activePatients.find(
    patient => patient.registrationId === patientRegistrationId,
  )

  const chooseSlot = (slot: AvailableSlotResource) => {
    bookingMutation.reset()
    setBookingNotice('')
    setPatientQuery('')
    setPatientRegistrationId('')
    setSelectedSlot({ slot, bookingRequestId:crypto.randomUUID() })
  }

  const bookSelected = () => {
    if (!selectedSlot || !patientRegistrationId || !doctorUserId) return
    bookingMutation.mutate({
      bookingRequestId:selectedSlot.bookingRequestId,
      patientRegistrationId,
      doctorUserId,
      startsAt:selectedSlot.slot.startsAt,
    })
  }

  return <section className="available-slot-browser">
    <header>
      <div>
        <p className="eyebrow">Published availability</p>
        <h2>Find and request an open consultation time</h2>
        <p>Slots are calculated from the doctor&apos;s active schedule and rechecked transactionally when you book.</p>
      </div>
      <Clock3/>
    </header>

    {bookingNotice&&<div className="inline-success" role="status"><Check/>{bookingNotice}</div>}

    {doctorsQuery.isError
      ? <div className="slot-state">
        <p>{apiErrorMessage(doctorsQuery.error, 'Doctor availability could not be loaded.')}</p>
        <button className="secondary" onClick={()=>doctorsQuery.refetch()}><RefreshCw/>Retry</button>
      </div>
      : <div className="slot-filters">
        <label>
          <span>Doctor schedule</span>
          <select
            value={doctorUserId}
            onChange={event=>{
              setDoctorUserId(event.target.value)
              setSelectedSlot(null)
            }}
            disabled={doctorsQuery.isPending}
          >
            <option value="">{doctorsQuery.isPending?'Loading schedules':'Choose a doctor'}</option>
            {doctorsQuery.data?.map(value=><option value={value.doctorUserId} key={value.doctorUserId}>Doctor {value.doctorUserId.slice(0,8)} · {value.locationLabel}</option>)}
          </select>
        </label>
        <label><span>From</span><input type="date" min={dateInput(0)} value={from} onChange={event=>{setFrom(event.target.value);setSelectedSlot(null)}}/></label>
        <label><span>To</span><input type="date" min={from} value={to} onChange={event=>{setTo(event.target.value);setSelectedSlot(null)}}/></label>
      </div>}

    {selectedDoctor&&<div className="slot-context"><MapPin/><span><strong>{selectedDoctor.locationLabel}</strong><small>{selectedDoctor.timeZone} · {selectedDoctor.appointmentDurationMinutes} minute visits</small></span></div>}
    {slotsQuery.isPending&&doctorUserId&&<div className="slot-state"><RefreshCw className="spin"/>Calculating available slots…</div>}
    {slotsQuery.isError&&<div className="slot-state"><p>{apiErrorMessage(slotsQuery.error, 'Available slots could not be calculated.')}</p><button className="secondary" onClick={()=>slotsQuery.refetch()}><RefreshCw/>Retry</button></div>}
    {slotsQuery.data&&<div className="slot-results">
      {slotsQuery.data.slots.map(slot=><button
        type="button"
        className={selectedSlot?.slot.startsAt===slot.startsAt?'selected':''}
        aria-pressed={selectedSlot?.slot.startsAt===slot.startsAt}
        onClick={()=>chooseSlot(slot)}
        key={slot.startsAt}
      ><CalendarDays/><span><strong>{new Intl.DateTimeFormat(undefined,{weekday:'short',month:'short',day:'numeric'}).format(new Date(`${slot.localDate}T12:00:00`))}</strong><small>{slot.localStartTime.slice(0,5)}–{slot.localEndTime.slice(0,5)}</small></span></button>)}
      {!slotsQuery.data.slots.length&&<div className="slot-state">No slots match this date range.</div>}
    </div>}

    {selectedSlot&&<div className="slot-booking-panel">
      <header>
        <div><p className="eyebrow">Selected appointment</p><h3>{new Intl.DateTimeFormat(undefined,{dateStyle:'full',timeStyle:'short'}).format(new Date(selectedSlot.slot.startsAt))}</h3></div>
        <button type="button" className="icon-button" aria-label="Close booking form" onClick={()=>setSelectedSlot(null)}><X/></button>
      </header>
      <div className="slot-patient-search">
        <label className="search-field"><Search/><span className="sr-only">Search patients</span><input value={patientQuery} onChange={event=>setPatientQuery(event.target.value)} placeholder="Search patient name, MRN, phone, or email"/></label>
        <label><span>Active patient registration</span><select value={patientRegistrationId} onChange={event=>setPatientRegistrationId(event.target.value)} disabled={patientsQuery.isPending}><option value="">{patientsQuery.isPending?'Loading patients':'Choose a patient'}</option>{activePatients.map(patient=><option value={patient.registrationId} key={patient.registrationId}>{patient.firstName} {patient.lastName} · {patient.medicalRecordNumber}</option>)}</select></label>
      </div>
      {patientsQuery.isError&&<p className="form-message form-message--error">{apiErrorMessage(patientsQuery.error, 'The patient directory could not be loaded.')}</p>}
      {!patientsQuery.isPending&&!patientsQuery.isError&&!activePatients.length&&<p className="slot-state">No active patient registration matches this search.</p>}
      {selectedPatient&&<div className="slot-patient-summary"><UserRound/><span><strong>{selectedPatient.firstName} {selectedPatient.lastName}</strong><small>{selectedPatient.medicalRecordNumber} · Born {selectedPatient.dateOfBirth}</small></span></div>}
      {bookingMutation.isError&&<p className="form-message form-message--error" role="alert">{apiErrorMessage(bookingMutation.error, 'The appointment could not be requested.')}</p>}
      <footer><button type="button" className="secondary" onClick={()=>setSelectedSlot(null)}>Cancel</button><button type="button" className="primary" disabled={!patientRegistrationId||bookingMutation.isPending} onClick={bookSelected}>{bookingMutation.isPending?<><LoaderCircle className="spin"/>Requesting…</>:<><CalendarDays/>Request appointment</>}</button></footer>
    </div>}
  </section>
}
