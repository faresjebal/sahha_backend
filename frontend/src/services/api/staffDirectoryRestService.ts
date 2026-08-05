import type {
  ChangeStaffMembershipStatusCommand,
  CreateStaffDepartmentAssignmentCommand,
  DoctorProfileResource,
  EndStaffDepartmentAssignmentCommand,
  StaffDepartmentAssignmentResource,
  StaffMemberPageResource,
  StaffMemberResource,
  UpsertDoctorProfileCommand,
} from '../../models/organisation'
import { httpClient } from './httpClient'

const staffPath = (membershipId: string) =>
  `/staff/${encodeURIComponent(membershipId)}`

export const staffDirectoryRestService = {
  list(page = 0, size = 100) {
    return httpClient.request<StaffMemberPageResource>(
      `/staff?page=${page}&size=${size}`,
    )
  },

  get(membershipId: string) {
    return httpClient.request<StaffMemberResource>(staffPath(membershipId))
  },

  changeStatus(
    membershipId: string,
    command: ChangeStaffMembershipStatusCommand,
  ) {
    return httpClient.request<StaffMemberResource>(
      `${staffPath(membershipId)}/status`,
      { method:'PUT', body:command },
    )
  },

  assignDepartment(
    membershipId: string,
    command: CreateStaffDepartmentAssignmentCommand,
  ) {
    return httpClient.request<StaffDepartmentAssignmentResource>(
      `${staffPath(membershipId)}/department-assignments`,
      { method:'POST', body:command },
    )
  },

  endDepartmentAssignment(
    membershipId: string,
    assignmentId: string,
    command: EndStaffDepartmentAssignmentCommand,
  ) {
    return httpClient.request<StaffDepartmentAssignmentResource>(
      `${staffPath(membershipId)}/department-assignments/${encodeURIComponent(assignmentId)}/end`,
      { method:'PUT', body:command },
    )
  },

  getDoctorProfile(membershipId: string) {
    return httpClient.request<DoctorProfileResource>(
      `${staffPath(membershipId)}/doctor-profile`,
    )
  },

  getMyDoctorProfile() {
    return httpClient.request<DoctorProfileResource>('/my/doctor-profile')
  },

  upsertMyDoctorProfile(command: UpsertDoctorProfileCommand) {
    return httpClient.request<DoctorProfileResource>('/my/doctor-profile', {
      method:'PUT',
      body:command,
    })
  },
}
