export type OrganisationType = 'HOSPITAL' | 'CLINIC' | 'PRIVATE_PRACTICE'
export type OrganisationStatus = 'ACTIVE' | 'SUSPENDED'
export type OrganisationMembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'REMOVED'
export type OrganisationRole = 'ORGANIZATION_ADMIN' | 'DOCTOR' | 'RECEPTIONIST'

export interface OrganisationResource {
  id: string
  name: string
  legalName: string | null
  type: OrganisationType
  status: OrganisationStatus
  contactEmail: string
  phoneNumber: string
  address: string
  city: string
  region: string
  postalCode: string | null
  countryCode: string
  timeZone: string
  createdBy: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface OrganisationPageResource {
  items: OrganisationResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateOrganisationCommand {
  name: string
  legalName: string | null
  type: OrganisationType
  contactEmail: string
  phoneNumber: string
  address: string
  city: string
  region: string
  postalCode: string | null
  countryCode: string
  timeZone: string
}

export interface OrganisationMembershipResource {
  id: string
  organisationId: string
  userId: string
  email: string
  displayName: string
  status: OrganisationMembershipStatus
  roles: OrganisationRole[]
  joinedAt: string
  createdBy: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface OrganisationMembershipPageResource {
  items: OrganisationMembershipResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AssignOrganisationAdministratorCommand {
  email: string
}

export interface OrganisationContextResource {
  membershipId: string
  organisationId: string
  organisationName: string
  organisationType: OrganisationType
  roles: OrganisationRole[]
  membershipVersion: number
}

export type DepartmentStatus = 'ACTIVE' | 'INACTIVE'

export interface DepartmentResource {
  id: string
  organisationId: string
  name: string
  code: string
  description: string | null
  status: DepartmentStatus
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface DepartmentPageResource {
  items: DepartmentResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateDepartmentCommand {
  name: string
  code: string
  description: string | null
}

export interface UpdateDepartmentCommand extends CreateDepartmentCommand {
  version: number
}

export interface ChangeDepartmentStatusCommand {
  status: DepartmentStatus
  version: number
}

export type StaffInvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'REVOKED'
  | 'EXPIRED'

export interface StaffInvitationResource {
  id: string
  organisationId: string
  organisationName: string
  email: string
  role: Exclude<OrganisationRole, 'ORGANIZATION_ADMIN'>
  status: StaffInvitationStatus
  expiresAt: string
  resolvedAt: string | null
  resolvedByUserId: string | null
  acceptedMembershipId: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface StaffInvitationPageResource {
  items: StaffInvitationResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateStaffInvitationCommand {
  email: string
  role: StaffInvitationResource['role']
}

export interface StaffInvitationVersionCommand {
  version: number
}

export type StaffMembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'REMOVED'
export type StaffDepartmentAssignmentStatus = 'ACTIVE' | 'ENDED'

export interface StaffDepartmentAssignmentResource {
  id: string
  departmentId: string
  departmentName: string
  departmentCode: string
  positionTitle: string
  primaryAssignment: boolean
  startDate: string
  plannedEndDate: string | null
  status: StaffDepartmentAssignmentStatus
  endedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface DoctorProfileResource {
  id: string
  organisationId: string
  membershipId: string
  specialty: string
  professionalTitle: string
  licenceNumber: string
  registrationAuthority: string
  biography: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface StaffMemberResource {
  membershipId: string
  organisationId: string
  userId: string
  email: string
  displayName: string
  status: StaffMembershipStatus
  roles: Array<'DOCTOR' | 'RECEPTIONIST'>
  departmentAssignments: StaffDepartmentAssignmentResource[]
  doctorProfile: DoctorProfileResource | null
  joinedAt: string
  updatedAt: string
  version: number
}

export interface StaffMemberPageResource {
  items: StaffMemberResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ChangeStaffMembershipStatusCommand {
  status: StaffMembershipStatus
  version: number
}

export interface CreateStaffDepartmentAssignmentCommand {
  departmentId: string
  positionTitle: string
  primaryAssignment: boolean
  startDate: string
  plannedEndDate: string | null
}

export interface EndStaffDepartmentAssignmentCommand {
  endDate: string
  version: number
}

export interface UpsertDoctorProfileCommand {
  specialty: string
  professionalTitle: string
  licenceNumber: string
  registrationAuthority: string
  biography: string | null
  version: number | null
}
