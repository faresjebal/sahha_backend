import { useQuery } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../app/auth/AuthProvider'
import { env } from '../../config/env'
import { ApiError } from '../../services/api/ApiError'
import { patientRegistryRestService } from '../../services/api/patientRegistryRestService'
import { DoctorNotificationCenter } from './DoctorNotificationCenter'

export function PatientNotificationCenter() {
  const { session } = useAuth()
  const [selection, setSelection] = useState('')
  const [open, setOpen] = useState(false)
  const enabled = !env.useAuthMocks && Boolean(session?.user.id)
  const registrations = useQuery({
    queryKey:['my-patient-registrations'],
    queryFn:patientRegistryRestService.listMyRegistrations,
    enabled, retry:false,
  })
  const active = registrations.data?.filter(item => item.status === 'ACTIVE') || []
  const selected = active.find(item => item.registrationId === selection) || active[0]
  if (!enabled || registrations.isPending || registrations.isError || !selected) {
    const unlinked = registrations.error instanceof ApiError && registrations.error.problem.status === 404
    return <div className="doctor-notification-center">
      <button className="icon-button" aria-label="Notifications" aria-expanded={open} onClick={()=>setOpen(!open)}><Bell/></button>
      {open&&<section className="doctor-notification-panel" role="dialog" aria-label="Notifications">
        <header><h2>Notifications</h2></header>
        <div className="notification-panel-state">
          {!enabled ? 'Use a real authenticated session to load private notifications.'
            : registrations.isPending ? 'Loading your patient registrations…'
            : registrations.isError && !unlinked
              ? <><p>Your notification access could not be verified.</p><button onClick={()=>registrations.refetch()}>Retry</button></>
              : <><p>Connect an active patient registration to receive appointment updates.</p><Link to="/patient/appointments">Open appointments</Link></>}
        </div>
      </section>}
    </div>
  }
  return <>
    {active.length > 1&&<select aria-label="Notification registration" value={selected.registrationId}
      onChange={event=>setSelection(event.target.value)} style={{ maxWidth:160 }}>
      {active.map(item=><option key={item.registrationId} value={item.registrationId}>{item.medicalRecordNumber}</option>)}
    </select>}
    <DoctorNotificationCenter key={session?.user.id + ':' + selected.registrationId}
      patientRegistrationId={selected.registrationId}
      navigationTargets={{ appointment:'/patient/appointments?registration=' + encodeURIComponent(selected.registrationId) }}/>
  </>
}
