export type AppRole =
  | 'patient'
  | 'doctor'
  | 'staff'
  | 'hospital-super-admin'
  | 'hospital-operations'
  | 'receptionist'
  | 'platform-admin'

export type DoctorType = 'PRIVATE' | 'HOSPITAL'
export type StaffRole = 'NURSE' | 'LABORATORY' | 'PHARMACY' | 'BED_COORDINATOR'

export type Permission =
  | 'patient:read:self'
  | 'patient:read:administrative'
  | 'patient:read:clinical'
  | 'patient:write:administrative'
  | 'patient:write:clinical'
  | 'appointment:manage:self'
  | 'appointment:manage:practice'
  | 'appointment:manage:hospital'
  | 'hospital:operations'
  | 'hospital:finance'
  | 'hospital:statistics'
  | 'hospital:access'
  | 'hospital:audit'
  | 'staff:schedule'
  | 'staff:tasks'
  | 'staff:clinical'
  | 'hospital:laboratory'
  | 'hospital:pharmacy'
  | 'hospital:beds'
  | 'platform:admin'

export interface SessionUser {
  id: string
  displayName: string
  email: string
  initials: string
  role: AppRole
  permissions: Permission[]
  organizationId?: string
  departmentId?: string
  doctorType?: DoctorType
  staffRole?: StaffRole
}

export interface AuthSession {
  user: SessionUser
  expiresAt: string
  platformRoles?: string[]
  organisationRoles?: string[]
}

export interface LoginRequest {
  email: string
  password: string
  demoRole?: AppRole
  demoDoctorType?: DoctorType
  demoStaffRole?: StaffRole
}

export interface RegisterAccountRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phoneNumber?: string
}

export interface LoginResponse {
  session: AuthSession
}
