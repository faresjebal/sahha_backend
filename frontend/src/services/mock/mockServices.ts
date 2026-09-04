import { doctors as seedDoctors, patients as seedPatients } from '../../data'
import type { AppRole, AuthSession, DoctorType, Permission, SessionUser, StaffRole } from '../../models/auth'
import type { AppointmentResource, AuditEventResource, ClinicalOrderResource, DepartmentResource, Doctor, NotificationResource, Patient } from '../../models/healthcare'
import type { ServiceContainer } from '../contracts'

const wait = (ms = 360) => new Promise(resolve => window.setTimeout(resolve, ms))
const SESSION_KEY = 'aegis-demo-session-v2'
const APPOINTMENTS_KEY = 'aegis-demo-appointments-v2'
const PATIENTS_KEY = 'aegis-demo-patients-v1'
const DOCTORS_KEY = 'aegis-demo-doctors-v1'
const DEPARTMENTS_KEY = 'aegis-demo-departments-v1'
const ORDERS_KEY = 'aegis-demo-orders-v1'

const seedDepartments: DepartmentResource[] = [
  { id:'DEP-EM', name:'Emergency medicine', lead:'Dr. Lena Ortiz', patients:38, clinicians:12, occupancy:86, wait:'18 min', state:'High demand' },
  { id:'DEP-CARD', name:'Cardiology', lead:'Dr. Mara Voss', patients:24, clinicians:9, occupancy:72, wait:'11 min', state:'Stable' },
  { id:'DEP-NEUR', name:'Neurology', lead:'Dr. Elias Chen', patients:17, clinicians:7, occupancy:64, wait:'14 min', state:'Stable' },
  { id:'DEP-PED', name:'Pediatrics', lead:'Dr. Sanaa Idris', patients:31, clinicians:11, occupancy:79, wait:'22 min', state:'Monitor' },
  { id:'DEP-ORTH', name:'Orthopedics', lead:'Dr. Lucas Moreau', patients:19, clinicians:8, occupancy:58, wait:'9 min', state:'Stable' },
]

const seedAppointments: AppointmentResource[] = [
  { id:'APT-2398', doctorId:1, patientId:'PT-1048', startsAt:'2026-06-18T13:00:00.000Z', mode:'IN_CLINIC', reason:'Cardiology consultation', reminderChannels:['EMAIL'], durationMinutes:30, status:'COMPLETED', version:2, createdAt:'2026-06-04T10:00:00.000Z' },
  { id:'APT-2401', doctorId:3, patientId:'PT-0921', startsAt:'2026-07-20T08:00:00.000Z', mode:'IN_CLINIC', reason:'Diabetes review', reminderChannels:['EMAIL'], status:'COMPLETED', version:1, createdAt:'2026-07-12T09:00:00.000Z' },
  { id:'APT-2402', doctorId:1, patientId:'PT-1048', startsAt:'2026-07-22T09:30:00.000Z', mode:'VIDEO', reason:'Blood pressure follow-up', reminderChannels:['EMAIL','SMS'], status:'CONFIRMED', version:1, createdAt:'2026-07-13T11:20:00.000Z' },
  { id:'APT-2403', doctorId:1, patientId:'PT-1176', startsAt:'2026-07-20T11:00:00.000Z', mode:'IN_CLINIC', reason:'Cardiac MRI review', reminderChannels:['SMS'], status:'CONFIRMED', version:1, createdAt:'2026-07-18T08:15:00.000Z' },
  { id:'APT-2404', doctorId:1, patientId:'PT-0884', startsAt:'2026-07-20T13:15:00.000Z', mode:'IN_CLINIC', reason:'Medication review', reminderChannels:['EMAIL'], status:'REQUESTED', version:1, createdAt:'2026-07-19T15:40:00.000Z' },
]

const seedOrders: ClinicalOrderResource[] = [
  { id:'OR-4821', patientId:'PT-1048', patientName:'Nora Bennett', type:'Laboratory', request:'Metabolic panel', priority:'Routine', indication:'Medication monitoring', state:'Awaiting collection', createdAt:'2026-07-19T09:20:00.000Z' },
  { id:'OR-4816', patientId:'PT-1176', patientName:'Maeve Kelly', type:'Imaging', request:'Cardiac MRI', priority:'Urgent', indication:'Acute myocarditis assessment', state:'Scheduled', createdAt:'2026-07-18T13:05:00.000Z' },
]

const readCollection = <T,>(key: string, fallback: T[]): T[] => {
  try { return JSON.parse(localStorage.getItem(key) || 'null') || fallback }
  catch { return fallback }
}
const writeCollection = <T,>(key: string, value: T[]) => localStorage.setItem(key, JSON.stringify(value))

const rolePermissions: Record<AppRole, Permission[]> = {
  patient: ['patient:read:self', 'appointment:manage:self'],
  doctor: ['patient:read:clinical', 'patient:write:clinical', 'appointment:manage:practice'],
  staff: ['staff:schedule', 'staff:tasks'],
  'hospital-operations': ['patient:read:administrative', 'appointment:manage:hospital', 'hospital:operations', 'hospital:statistics'],
  'hospital-super-admin': ['patient:read:administrative', 'appointment:manage:hospital', 'hospital:operations', 'hospital:finance', 'hospital:statistics', 'hospital:access', 'hospital:audit'],
  receptionist: ['patient:read:administrative', 'patient:write:administrative'],
  'platform-admin': ['platform:admin'],
}

const roleIdentity: Record<AppRole, Omit<SessionUser, 'role' | 'permissions'>> = {
  patient: { id: 'USR-P-2401', displayName: 'Nora Bennett', email: 'nora.b@example.com', initials: 'NB' },
  doctor: { id: 'USR-D-1042', displayName: 'Dr. Mara Voss', email: 'mara.v@sahha.health', initials: 'MV', organizationId: 'ORG-STH', departmentId: 'DEP-CARD', doctorType:'PRIVATE' },
  staff: { id:'USR-S-2001', displayName:'Elena Petrov', email:'elena.p@sthelena.health', initials:'EP', organizationId:'ORG-STH', departmentId:'DEP-CARD', staffRole:'NURSE' },
  'hospital-operations': { id: 'USR-O-1042', displayName: 'Omar Haddad', email: 'omar.h@sthelena.health', initials: 'OH', organizationId: 'ORG-STH' },
  'hospital-super-admin': { id: 'USR-A-1001', displayName: 'Leila Mansour', email: 'leila.m@sthelena.health', initials: 'LM', organizationId: 'ORG-STH' },
  receptionist: { id: 'USR-R-1098', displayName: 'Sofia Alvarez', email: 'sofia.a@sthelena.health', initials: 'SA', organizationId: 'ORG-STH' },
  'platform-admin': { id: 'USR-PA-001', displayName: 'Avery Kim', email: 'avery.k@sahha.health', initials: 'AK' },
}

const staffIdentities: Record<StaffRole, Omit<SessionUser, 'role' | 'permissions'>> = {
  NURSE:{ id:'USR-S-2001', displayName:'Elena Petrov', email:'elena.p@sthelena.health', initials:'EP', organizationId:'ORG-STH', departmentId:'DEP-CARD', staffRole:'NURSE' },
  LABORATORY:{ id:'USR-S-2002', displayName:'Mateo Silva', email:'mateo.s@sthelena.health', initials:'MS', organizationId:'ORG-STH', departmentId:'DEP-LAB', staffRole:'LABORATORY' },
  PHARMACY:{ id:'USR-S-2003', displayName:'Amina Diallo', email:'amina.d@sthelena.health', initials:'AD', organizationId:'ORG-STH', departmentId:'DEP-PHARM', staffRole:'PHARMACY' },
  BED_COORDINATOR:{ id:'USR-S-2004', displayName:'Jonas Berg', email:'jonas.b@sthelena.health', initials:'JB', organizationId:'ORG-STH', departmentId:'DEP-EM', staffRole:'BED_COORDINATOR' },
}

const staffPermissions: Record<StaffRole, Permission[]> = {
  NURSE:['staff:schedule','staff:tasks','staff:clinical'],
  LABORATORY:['staff:schedule','staff:tasks','hospital:laboratory'],
  PHARMACY:['staff:schedule','staff:tasks','hospital:pharmacy'],
  BED_COORDINATOR:['staff:schedule','staff:tasks','hospital:beds'],
}

const makeSession = (role: AppRole, doctorType: DoctorType = 'PRIVATE', staffRole: StaffRole = 'NURSE'): AuthSession => ({
  user: { ...(role === 'staff' ? staffIdentities[staffRole] : roleIdentity[role]), role, permissions: role === 'staff' ? staffPermissions[staffRole] : rolePermissions[role], ...(role === 'doctor' ? { doctorType } : {}) },
  expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
})

const notifications: NotificationResource[] = [
  { id: 'NT-401', type: 'APPOINTMENT', title: 'Appointment confirmed', body: 'Your consultation is confirmed for 16 July at 11:30.', occurredAt: new Date().toISOString(), route: '/patient/appointments' },
  { id: 'NT-402', type: 'RESULT', title: 'New result available', body: 'A recent laboratory result is ready to review.', occurredAt: new Date(Date.now() - 3_600_000).toISOString(), route: '/patient/results' },
]

const auditEvents: AuditEventResource[] = [
  { id:'AUD-8841', occurredAt:new Date().toISOString(), actorId:'USR-A-1001', actorName:'Leila Mansour', action:'PERMISSION_CHANGED', resourceType:'USER', resourceId:'USR-R-1098', organizationId:'ORG-STH', result:'SUCCESS', requestId:'REQ-73AE19' },
]

export const mockServices: ServiceContainer = {
  auth: {
    async restoreSession() {
      await wait(180)
      const raw = localStorage.getItem(SESSION_KEY)
      if (!raw) return null
      const session = JSON.parse(raw) as AuthSession
      return new Date(session.expiresAt).getTime() > Date.now() ? session : null
    },
    async login(request) {
      await wait(520)
      const session = makeSession(request.demoRole || 'patient', request.demoDoctorType, request.demoStaffRole)
      localStorage.setItem(SESSION_KEY, JSON.stringify(session))
      return session
    },
    async refreshSession() {
      return this.restoreSession()
    },
    async selectActiveOrganisation() {
      const session = await this.restoreSession()
      if (!session) throw new Error('No authenticated session.')
      return session
    },
    async register() {
      await wait(520)
    },
    async confirmEmail() {
      await wait(360)
    },
    async logout() {
      await wait(180)
      localStorage.removeItem(SESSION_KEY)
    },
  },
  patients: {
    async list(request) {
      await wait()
      const patients = readCollection(PATIENTS_KEY, seedPatients)
      const query = request.query?.toLowerCase() || ''
      const filtered = patients.filter(patient => `${patient.name} ${patient.id} ${patient.email}`.toLowerCase().includes(query))
      const start = request.page * request.size
      return { content: filtered.slice(start, start + request.size), page: request.page, size: request.size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / request.size) }
    },
    async get(patientId) {
      await wait()
      const patients = readCollection(PATIENTS_KEY, seedPatients)
      const patient = patients.find(item => item.id === patientId)
      if (!patient) throw new Error('Patient not found')
      return patient
    },
    async create(command) {
      await wait()
      const current = readCollection(PATIENTS_KEY, seedPatients)
      if (current.some(item => item.email.toLowerCase() === command.email.toLowerCase())) throw new Error('A patient with this email already exists.')
      const born = new Date(command.dateOfBirth)
      const today = new Date()
      let computedAge = today.getFullYear() - born.getFullYear()
      if (today.getMonth() < born.getMonth() || (today.getMonth() === born.getMonth() && today.getDate() < born.getDate())) computedAge -= 1
      const age = Math.max(0, computedAge)
      const name = `${command.firstName.trim()} ${command.lastName.trim()}`
      const patient: Patient = { id:`PT-${2100 + current.length}`, name, initials:name.split(' ').map(value=>value[0]).join('').slice(0,2).toUpperCase(), age, sex:command.sex, status:'Stable', condition:'Registration review', lastVisit:'No visits yet', phone:command.phone, email:command.email, blood:'Not recorded', allergies:['Not recorded'], vitals:[], medications:[], timeline:[{ date:'TODAY', title:'Patient registered', detail:'Administrative registration created; clinical intake remains pending.', type:'Registration' }], notes:'No clinical notes have been added.', dateOfBirth:command.dateOfBirth, address:command.address, emergencyContact:command.emergencyContactName&&command.emergencyContactPhone?{name:command.emergencyContactName,phone:command.emergencyContactPhone,relationship:command.emergencyContactRelationship}:undefined, preferredLanguage:command.preferredLanguage||'English', accessibilityNeeds:command.accessibilityNeeds, consentStatus:command.consentConfirmed?'CONFIRMED':'PENDING' }
      writeCollection(PATIENTS_KEY, [...current, patient])
      return patient
    },
  },
  doctors: {
    async list() { await wait(); return readCollection(DOCTORS_KEY, seedDoctors) },
    async create(command) {
      await wait()
      const current = readCollection(DOCTORS_KEY, seedDoctors)
      const name = command.name.trim().startsWith('Dr.') ? command.name.trim() : `Dr. ${command.name.trim()}`
      const doctor: Doctor = { id:Math.max(0,...current.map(item=>item.id))+1, name, initials:name.replace('Dr.','').trim().split(' ').map(value=>value[0]).join('').slice(0,2).toUpperCase(), specialty:command.specialty, location:command.department, rating:0, experience:command.experience, next:'Schedule pending', accent:['#6c9a8b','#e8998d','#eed2cc'][current.length%3], languages:command.languages, doctorType:command.doctorType||'HOSPITAL', licenseNumber:command.licenseNumber, consultationFee:command.consultationFee, bio:command.bio, email:command.email, phone:command.phone, hospitalId:command.hospitalId||'ORG-STH', verificationStatus:'PENDING' }
      writeCollection(DOCTORS_KEY, [...current, doctor])
      return doctor
    },
  },
  departments: {
    async list() { await wait(); return readCollection(DEPARTMENTS_KEY, seedDepartments) },
    async create(command) {
      await wait()
      const current = readCollection(DEPARTMENTS_KEY, seedDepartments)
      if (current.some(item=>item.name.toLowerCase()===command.name.toLowerCase())) throw new Error('A department with this name already exists.')
      const department: DepartmentResource = { id:`DEP-${String(current.length+1).padStart(3,'0')}`, name:command.name.trim(), lead:command.lead.trim() || 'Lead not assigned', patients:0, clinicians:0, occupancy:0, wait:'No wait data', state:'Stable' }
      writeCollection(DEPARTMENTS_KEY, [...current, department])
      return department
    },
  },
  appointments: {
    async list() { await wait(); return readCollection(APPOINTMENTS_KEY, seedAppointments) },
    async create(command) {
      await wait()
      const resource: AppointmentResource = { ...command, id: `APT-${Date.now()}`, status: 'REQUESTED', version: 1, createdAt: new Date().toISOString() }
      const existing = readCollection(APPOINTMENTS_KEY, seedAppointments)
      writeCollection(APPOINTMENTS_KEY, [resource, ...existing])
      return resource
    },
    async cancel(appointmentId, reason) {
      await wait()
      const existing = readCollection(APPOINTMENTS_KEY, seedAppointments)
      const current = existing.find(item => item.id === appointmentId)
      if (!current) throw new Error('Appointment not found')
      const updated = { ...current, status: 'CANCELLED' as const, reason: `${current.reason} · Cancellation: ${reason}`, version: current.version + 1 }
      writeCollection(APPOINTMENTS_KEY, existing.map(item => item.id === appointmentId ? updated : item))
      return updated
    },
    async reschedule(appointmentId, startsAt, version) {
      await wait()
      const existing = readCollection(APPOINTMENTS_KEY, seedAppointments)
      const current = existing.find(item => item.id === appointmentId)
      if (!current || current.version !== version) throw new Error('Appointment changed. Refresh and try again.')
      const updated = { ...current, startsAt, version: current.version + 1 }
      writeCollection(APPOINTMENTS_KEY, existing.map(item => item.id === appointmentId ? updated : item))
      return updated
    },
    async updateStatus(appointmentId, status, version) {
      await wait()
      const existing = readCollection(APPOINTMENTS_KEY, seedAppointments)
      const current = existing.find(item => item.id === appointmentId)
      if (!current || current.version !== version) throw new Error('Appointment changed. Refresh and try again.')
      const updated: AppointmentResource = { ...current, status, version: current.version + 1 }
      writeCollection(APPOINTMENTS_KEY, existing.map(item => item.id === appointmentId ? updated : item))
      return updated
    },
  },
  orders: {
    async list() { await wait(); return readCollection(ORDERS_KEY, seedOrders) },
    async create(command) {
      await wait()
      const current = readCollection(ORDERS_KEY, seedOrders)
      const order: ClinicalOrderResource = { ...command, id:`OR-${Date.now().toString().slice(-6)}`, state:'Sent', createdAt:new Date().toISOString() }
      writeCollection(ORDERS_KEY, [order, ...current])
      return order
    },
  },
  notifications: {
    async list() { await wait(); return notifications },
    async markRead() { await wait(120) },
  },
  audit: {
    async list(request) { await wait(); return { content: auditEvents, page: request.page, size: request.size, totalElements: auditEvents.length, totalPages: 1 } },
  },
}
