export type SahhaDayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export interface WeeklyAvailabilityResource {
  id: string
  dayOfWeek: SahhaDayOfWeek
  startTime: string
  endTime: string
}

export interface AvailabilityBreakResource {
  id: string
  dayOfWeek: SahhaDayOfWeek
  startTime: string
  endTime: string
  label: string | null
}

export interface TimeOffResource {
  id: string
  date: string
  startTime: string | null
  endTime: string | null
  reason: string
}

export interface DoctorAvailabilityResource {
  id: string
  organisationId: string
  doctorUserId: string
  doctorMembershipId: string
  timeZone: string
  appointmentDurationMinutes: number
  minimumLeadTimeMinutes: number
  bookingHorizonDays: number
  locationLabel: string
  weeklyWindows: WeeklyAvailabilityResource[]
  breaks: AvailabilityBreakResource[]
  timeOff: TimeOffResource[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface UpsertDoctorAvailabilityCommand {
  timeZone: string
  appointmentDurationMinutes: number
  minimumLeadTimeMinutes: number
  bookingHorizonDays: number
  locationLabel: string
  weeklyWindows: Array<{
    dayOfWeek: SahhaDayOfWeek
    startTime: string
    endTime: string
  }>
  breaks: Array<{
    dayOfWeek: SahhaDayOfWeek
    startTime: string
    endTime: string
    label: string | null
  }>
  timeOff: Array<{
    date: string
    startTime: string | null
    endTime: string | null
    reason: string
  }>
  version: number | null
}

export interface DoctorAvailabilitySummaryResource {
  doctorUserId: string
  doctorMembershipId: string
  timeZone: string
  locationLabel: string
  appointmentDurationMinutes: number
}

export interface PatientDoctorAvailabilityResource {
  doctorUserId: string
  displayName: string
  timeZone: string
  locationLabel: string
  appointmentDurationMinutes: number
}

export interface AvailableSlotResource {
  startsAt: string
  endsAt: string
  localDate: string
  localStartTime: string
  localEndTime: string
}

export interface AvailableSlotsResource {
  organisationId: string
  doctorUserId: string
  from: string
  to: string
  timeZone: string
  locationLabel: string
  appointmentDurationMinutes: number
  slots: AvailableSlotResource[]
}

export type AppointmentStatus =
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'RESCHEDULED'
  | 'CANCELLED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'REJECTED'

export interface BookAppointmentCommand {
  bookingRequestId: string
  patientRegistrationId: string
  doctorUserId: string
  startsAt: string
}

export interface AppointmentResource {
  id: string
  organisationId: string
  bookingRequestId: string
  patientRegistrationId: string
  patientId: string
  doctorUserId: string
  doctorMembershipId: string
  status: AppointmentStatus
  statusReason: string | null
  startsAt: string
  endsAt: string
  timeZone: string
  locationLabel: string
  bookedByUserId: string
  bookedByActorType: 'STAFF' | 'PATIENT'
  bookedByMembershipId: string | null
  bookedAt: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface PatientAppointmentResource {
  id: string
  organisationId: string
  doctorUserId: string
  status: AppointmentStatus
  statusReason: string | null
  startsAt: string
  endsAt: string
  timeZone: string
  locationLabel: string
  bookedAt: string
  updatedAt: string
  version: number
}

export interface AppointmentCommand {
  commandRequestId: string
  version: number
}

export interface ReasonedAppointmentCommand extends AppointmentCommand {
  reason: string
}

export interface RescheduleAppointmentCommand
  extends ReasonedAppointmentCommand {
  startsAt: string
}
