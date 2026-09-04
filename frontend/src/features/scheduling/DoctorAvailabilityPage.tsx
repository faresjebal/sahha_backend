import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  Check,
  Clock3,
  LoaderCircle,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/auth/AuthProvider'
import type {
  SahhaDayOfWeek,
  TimeOffResource,
} from '../../models/scheduling'
import { apiErrorMessage } from '../../services/api/ApiError'
import { availabilityRestService } from '../../services/api/availabilityRestService'

const days: SahhaDayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
]

type DayRule = {
  enabled: boolean
  startTime: string
  endTime: string
  breakEnabled: boolean
  breakStartTime: string
  breakEndTime: string
}

const defaultRules = () => Object.fromEntries(days.map(day => [day, {
  enabled:['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'].includes(day),
  startTime:'09:00',
  endTime:'17:00',
  breakEnabled:false,
  breakStartTime:'12:30',
  breakEndTime:'13:30',
}])) as Record<SahhaDayOfWeek, DayRule>

const timeInput = (value: string | null | undefined, fallback: string) =>
  value ? value.slice(0, 5) : fallback

const dayLabel = (day: SahhaDayOfWeek) =>
  day[0] + day.slice(1).toLowerCase()

const dateInput = (daysFromNow = 1) => {
  const value = new Date()
  value.setDate(value.getDate() + daysFromNow)
  return value.toISOString().slice(0, 10)
}

export function DoctorAvailabilityPage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'none'
  const [rules, setRules] = useState(defaultRules)
  const [timeZone, setTimeZone] = useState(
    Intl.DateTimeFormat().resolvedOptions().timeZone || 'Africa/Tunis',
  )
  const [locationLabel, setLocationLabel] = useState('Main consultation room')
  const [duration, setDuration] = useState(30)
  const [leadMinutes, setLeadMinutes] = useState(720)
  const [horizonDays, setHorizonDays] = useState(60)
  const [timeOff, setTimeOff] = useState<TimeOffResource[]>([])
  const [newTimeOff, setNewTimeOff] = useState({
    date:dateInput(),
    reason:'',
  })
  const [notice, setNotice] = useState('')
  const [formError, setFormError] = useState('')

  const availabilityQuery = useQuery({
    queryKey:['doctor-availability', organisationId],
    queryFn:availabilityRestService.getMine,
    enabled:organisationId !== 'none',
  })

  useEffect(() => {
    const resource = availabilityQuery.data
    if (!resource) return
    const next = defaultRules()
    days.forEach(day => {
      const window = resource.weeklyWindows.find(value => value.dayOfWeek === day)
      const availabilityBreak = resource.breaks.find(value => value.dayOfWeek === day)
      next[day] = {
        enabled:Boolean(window),
        startTime:timeInput(window?.startTime, '09:00'),
        endTime:timeInput(window?.endTime, '17:00'),
        breakEnabled:Boolean(availabilityBreak),
        breakStartTime:timeInput(availabilityBreak?.startTime, '12:30'),
        breakEndTime:timeInput(availabilityBreak?.endTime, '13:30'),
      }
    })
    setRules(next)
    setTimeZone(resource.timeZone)
    setLocationLabel(resource.locationLabel)
    setDuration(resource.appointmentDurationMinutes)
    setLeadMinutes(resource.minimumLeadTimeMinutes)
    setHorizonDays(resource.bookingHorizonDays)
    setTimeOff(resource.timeOff)
  }, [availabilityQuery.data])

  const mutation = useMutation({
    mutationFn:availabilityRestService.updateMine,
    onSuccess:resource => {
      queryClient.setQueryData(
        ['doctor-availability', organisationId],
        resource,
      )
      void queryClient.invalidateQueries({
        queryKey:['availability-doctors', organisationId],
      })
      setNotice('Your organisation availability is published.')
      setFormError('')
    },
    onError:error => setFormError(apiErrorMessage(
      error,
      'The availability could not be published.',
    )),
  })

  const summary = useMemo(() => {
    let availableMinutes = 0
    days.forEach(day => {
      const rule = rules[day]
      if (!rule.enabled) return
      const minutes = (value: string) => {
        const [hours, minute] = value.split(':').map(Number)
        return hours * 60 + minute
      }
      availableMinutes += Math.max(0, minutes(rule.endTime) - minutes(rule.startTime))
      if (rule.breakEnabled) {
        availableMinutes -= Math.max(
          0,
          minutes(rule.breakEndTime) - minutes(rule.breakStartTime),
        )
      }
    })
    return {
      hours:Math.max(0, availableMinutes / 60),
      slots:Math.max(0, Math.floor(availableMinutes / duration)),
    }
  }, [duration, rules])

  const updateRule = (day: SahhaDayOfWeek, change: Partial<DayRule>) =>
    setRules(current => ({
      ...current,
      [day]:{ ...current[day], ...change },
    }))

  const publish = () => {
    setNotice('')
    const enabledDays = days.filter(day => rules[day].enabled)
    if (!enabledDays.length) {
      setFormError('Enable at least one consultation day.')
      return
    }
    for (const day of enabledDays) {
      const rule = rules[day]
      if (rule.endTime <= rule.startTime) {
        setFormError(`${dayLabel(day)} must end after it starts.`)
        return
      }
      if (rule.breakEnabled && (
        rule.breakStartTime < rule.startTime
        || rule.breakEndTime > rule.endTime
        || rule.breakEndTime <= rule.breakStartTime
      )) {
        setFormError(`${dayLabel(day)} break must stay inside consultation hours.`)
        return
      }
    }
    if (!locationLabel.trim() || !timeZone.trim()) {
      setFormError('Add the consultation location and time zone.')
      return
    }
    mutation.mutate({
      timeZone:timeZone.trim(),
      appointmentDurationMinutes:duration,
      minimumLeadTimeMinutes:leadMinutes,
      bookingHorizonDays:horizonDays,
      locationLabel:locationLabel.trim(),
      weeklyWindows:enabledDays.map(day => ({
        dayOfWeek:day,
        startTime:rules[day].startTime,
        endTime:rules[day].endTime,
      })),
      breaks:enabledDays
        .filter(day => rules[day].breakEnabled)
        .map(day => ({
          dayOfWeek:day,
          startTime:rules[day].breakStartTime,
          endTime:rules[day].breakEndTime,
          label:'Protected break',
        })),
      timeOff:timeOff.map(value => ({
        date:value.date,
        startTime:value.startTime,
        endTime:value.endTime,
        reason:value.reason,
      })),
      version:availabilityQuery.data?.version ?? null,
    })
  }

  const addTimeOff = () => {
    if (!newTimeOff.date || newTimeOff.reason.trim().length < 3) {
      setFormError('Add a date and a short reason for the absence.')
      return
    }
    setTimeOff(current => [...current, {
      id:`draft-${crypto.randomUUID()}`,
      date:newTimeOff.date,
      startTime:null,
      endTime:null,
      reason:newTimeOff.reason.trim(),
    }])
    setNewTimeOff({ date:dateInput(), reason:'' })
    setFormError('')
  }

  if (availabilityQuery.isPending) {
    return <div className="page doctor-work-page scheduling-state"><LoaderCircle className="spin"/><h2>Loading availability</h2></div>
  }

  if (availabilityQuery.isError) {
    return <div className="page doctor-work-page scheduling-state"><Clock3/><h2>Availability is unavailable</h2><p>{apiErrorMessage(availabilityQuery.error, 'Check that Scheduling Service is running.')}</p><button className="secondary" onClick={() => availabilityQuery.refetch()}><RefreshCw/>Retry</button></div>
  }

  return <div className="page doctor-work-page">
    <div className="page-intro"><div><p className="eyebrow">Active organisation · Practice schedule</p><h1>Availability that protects clinical focus.</h1><p>Publish consultation windows, protected breaks, booking rules, and planned time away for this organisation.</p></div><button className="primary" disabled={mutation.isPending} onClick={publish}>{mutation.isPending?<><LoaderCircle className="spin"/>Publishing</>:<><Check/>Publish schedule</>}</button></div>
    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}
    {formError&&<p className="form-message form-message--error" role="alert">{formError}</p>}
    <div className="availability-layout availability-live">
      <section><header><p className="eyebrow">Weekly pattern</p><h2>Consultation hours</h2></header>{days.map(day=>{const rule=rules[day];return <article className={rule.enabled?'enabled':''} key={day}><button type="button" role="switch" aria-label={`${dayLabel(day)} availability`} aria-checked={rule.enabled} onClick={()=>updateRule(day,{enabled:!rule.enabled})}><i/></button><strong>{dayLabel(day)}</strong>{rule.enabled?<div className="availability-periods"><span><label><span className="sr-only">{dayLabel(day)} start time</span><input type="time" value={rule.startTime} onChange={event=>updateRule(day,{startTime:event.target.value})}/></label><em>to</em><label><span className="sr-only">{dayLabel(day)} end time</span><input type="time" value={rule.endTime} onChange={event=>updateRule(day,{endTime:event.target.value})}/></label></span><span className="break-period"><label><input type="checkbox" checked={rule.breakEnabled} onChange={event=>updateRule(day,{breakEnabled:event.target.checked})}/>Break</label>{rule.breakEnabled&&<><input aria-label={`${dayLabel(day)} break start`} type="time" value={rule.breakStartTime} onChange={event=>updateRule(day,{breakStartTime:event.target.value})}/><em>to</em><input aria-label={`${dayLabel(day)} break end`} type="time" value={rule.breakEndTime} onChange={event=>updateRule(day,{breakEndTime:event.target.value})}/></>}</span></div>:<span className="day-off">Not available</span>}</article>})}</section>
      <aside><p className="eyebrow">Booking rules</p><label className="booking-rule-input"><span>Consultation location</span><input value={locationLabel} maxLength={160} onChange={event=>setLocationLabel(event.target.value)}/></label><label className="booking-rule-input"><span>Time zone</span><input value={timeZone} maxLength={64} onChange={event=>setTimeZone(event.target.value)}/></label><label className="booking-rule-input"><span>Appointment length</span><select value={duration} onChange={event=>setDuration(Number(event.target.value))}>{[15,20,30,45,60].map(value=><option value={value} key={value}>{value} minutes</option>)}</select></label><label className="booking-rule-input"><span>Minimum lead time</span><select value={leadMinutes} onChange={event=>setLeadMinutes(Number(event.target.value))}><option value={0}>No minimum</option><option value={60}>1 hour</option><option value={360}>6 hours</option><option value={720}>12 hours</option><option value={1440}>24 hours</option></select></label><label className="booking-rule-input"><span>Booking horizon</span><select value={horizonDays} onChange={event=>setHorizonDays(Number(event.target.value))}>{[14,30,60,90,180].map(value=><option value={value} key={value}>{value} days</option>)}</select></label><div className="schedule-preview"><CalendarDays/><span><strong>{summary.hours.toFixed(summary.hours%1?1:0)} hours available</strong><small>{summary.slots} calculated slots in a full week</small></span></div></aside>
    </div>
    <section className="availability-time-off"><header><div><p className="eyebrow">Exceptions and holidays</p><h2>Planned time away</h2></div></header><div className="time-off-composer"><label><span>Date</span><input type="date" min={dateInput(0)} value={newTimeOff.date} onChange={event=>setNewTimeOff(current=>({...current,date:event.target.value}))}/></label><label><span>Reason</span><input value={newTimeOff.reason} maxLength={160} onChange={event=>setNewTimeOff(current=>({...current,reason:event.target.value}))} placeholder="Holiday, conference, or absence"/></label><button type="button" className="secondary" onClick={addTimeOff}><Plus/>Add full day</button></div><div className="time-off-list">{timeOff.map(value=><article key={value.id}><CalendarDays/><span><strong>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(`${value.date}T12:00:00`))}</strong><small>{value.reason} · {value.startTime?`${timeInput(value.startTime,'')}–${timeInput(value.endTime,'')}`:'Full day'}</small></span><button type="button" className="icon-button" aria-label={`Remove time off ${value.date}`} onClick={()=>setTimeOff(current=>current.filter(item=>item.id!==value.id))}><Trash2/></button></article>)}{!timeOff.length&&<p className="day-off">No planned time away.</p>}</div></section>
  </div>
}
