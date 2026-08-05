export interface AppointmentCommand {
  doctorId: number
  patientId: string
  startsAt: string
  mode: 'VIDEO' | 'IN_CLINIC'
  reason: string
  reminderChannels: Array<'EMAIL' | 'SMS' | 'PUSH'>
  durationMinutes?: number
  departmentId?: string
  receptionistId?: string
  notes?: string
}

export type Doctor = {
  id: number
  name: string
  initials: string
  specialty: string
  location: string
  rating: number
  experience: number
  next: string
  accent: string
  languages: string[]
  doctorType?: 'PRIVATE' | 'HOSPITAL'
  licenseNumber?: string
  consultationFee?: number
  bio?: string
  email?: string
  phone?: string
  departmentId?: string
  hospitalId?: string
  verificationStatus?: 'PENDING' | 'VERIFIED' | 'SUSPENDED'
}

export type Patient = {
  id: string
  name: string
  initials: string
  age: number
  sex: string
  status: 'Stable' | 'Monitor' | 'Critical'
  condition: string
  lastVisit: string
  phone: string
  email: string
  blood: string
  allergies: string[]
  vitals: { label: string; value: string; note: string }[]
  medications: { name: string; dose: string; schedule: string }[]
  timeline: { date: string; title: string; detail: string; type: string }[]
  notes: string
  dateOfBirth?: string
  address?: string
  emergencyContact?: { name: string; phone: string; relationship?: string }
  preferredLanguage?: string
  accessibilityNeeds?: string
  consentStatus?: 'PENDING' | 'CONFIRMED'
  insurance?: { provider: string; memberId: string; status: string }
}

export interface PatientRegistrationCommand {
  firstName: string
  lastName: string
  dateOfBirth: string
  sex: string
  phone: string
  email: string
  address?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  emergencyContactRelationship?: string
  preferredLanguage?: string
  accessibilityNeeds?: string
  consentConfirmed?: boolean
}

export interface DoctorCommand {
  name: string
  specialty: string
  department: string
  email: string
  phone: string
  experience: number
  languages: string[]
  doctorType?: 'PRIVATE' | 'HOSPITAL'
  licenseNumber?: string
  consultationFee?: number
  bio?: string
  hospitalId?: string
}

export interface DepartmentResource {
  id: string
  name: string
  lead: string
  patients: number
  clinicians: number
  occupancy: number
  wait: string
  state: 'Stable' | 'Monitor' | 'High demand'
}

export interface DepartmentCommand {
  name: string
  lead: string
  capacity: number
  location: string
}

export interface ClinicalOrderResource {
  id: string
  patientId: string
  patientName: string
  type: string
  request: string
  priority: 'Routine' | 'Urgent'
  indication: string
  state: string
  createdAt: string
}

export type ClinicalOrderCommand = Omit<ClinicalOrderResource, 'id' | 'state' | 'createdAt'>

export interface AppointmentResource extends AppointmentCommand {
  id: string
  status: 'REQUESTED' | 'SCHEDULED' | 'CONFIRMED' | 'CHECKED_IN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
  version: number
  createdAt: string
}

export interface AuditEventResource {
  id: string
  occurredAt: string
  actorId: string
  actorName: string
  action: string
  resourceType: string
  resourceId: string
  organizationId?: string
  departmentId?: string
  result: 'SUCCESS' | 'DENIED' | 'FAILED'
  requestId: string
}

export interface NotificationResource {
  id: string
  type: string
  title: string
  body: string
  occurredAt: string
  readAt?: string
  route?: string
}
