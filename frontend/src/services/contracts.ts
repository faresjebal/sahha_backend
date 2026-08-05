import type { AuthSession, LoginRequest, RegisterAccountRequest } from '../models/auth'
import type { PageRequest, PageResponse } from '../models/api'
import type { AppointmentCommand, AppointmentResource, AuditEventResource, ClinicalOrderCommand, ClinicalOrderResource, DepartmentCommand, DepartmentResource, Doctor, DoctorCommand, NotificationResource, Patient, PatientRegistrationCommand } from '../models/healthcare'

export interface AuthService {
  restoreSession(): Promise<AuthSession | null>
  refreshSession(): Promise<AuthSession | null>
  selectActiveOrganisation(organisationId: string): Promise<AuthSession>
  login(request: LoginRequest): Promise<AuthSession>
  register(request: RegisterAccountRequest): Promise<void>
  confirmEmail(token: string): Promise<void>
  logout(): Promise<void>
}

export interface PatientService {
  list(request: PageRequest): Promise<PageResponse<Patient>>
  get(patientId: string): Promise<Patient>
  create(command: PatientRegistrationCommand): Promise<Patient>
}

export interface DoctorService {
  list(): Promise<Doctor[]>
  create(command: DoctorCommand): Promise<Doctor>
}

export interface DepartmentService {
  list(): Promise<DepartmentResource[]>
  create(command: DepartmentCommand): Promise<DepartmentResource>
}

export interface AppointmentService {
  list(): Promise<AppointmentResource[]>
  create(command: AppointmentCommand): Promise<AppointmentResource>
  cancel(appointmentId: string, reason: string): Promise<AppointmentResource>
  reschedule(appointmentId: string, startsAt: string, version: number): Promise<AppointmentResource>
  updateStatus(appointmentId: string, status: AppointmentResource['status'], version: number): Promise<AppointmentResource>
}

export interface ClinicalOrderService {
  list(): Promise<ClinicalOrderResource[]>
  create(command: ClinicalOrderCommand): Promise<ClinicalOrderResource>
}

export interface NotificationService {
  list(): Promise<NotificationResource[]>
  markRead(notificationId: string): Promise<void>
}

export interface AuditService {
  list(request: PageRequest): Promise<PageResponse<AuditEventResource>>
}

export interface ServiceContainer {
  auth: AuthService
  patients: PatientService
  doctors: DoctorService
  departments: DepartmentService
  appointments: AppointmentService
  orders: ClinicalOrderService
  notifications: NotificationService
  audit: AuditService
}
