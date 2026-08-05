import type {
  ChangeDepartmentStatusCommand,
  CreateDepartmentCommand,
  DepartmentPageResource,
  DepartmentResource,
  UpdateDepartmentCommand,
} from '../../models/organisation'
import { httpClient } from './httpClient'

export const departmentRestService = {
  list(page = 0, size = 100) {
    return httpClient.request<DepartmentPageResource>(
      `/departments?page=${page}&size=${size}`,
    )
  },

  get(departmentId: string) {
    return httpClient.request<DepartmentResource>(
      `/departments/${encodeURIComponent(departmentId)}`,
    )
  },

  create(command: CreateDepartmentCommand) {
    return httpClient.request<DepartmentResource>('/departments', {
      method:'POST',
      body:command,
    })
  },

  update(departmentId: string, command: UpdateDepartmentCommand) {
    return httpClient.request<DepartmentResource>(
      `/departments/${encodeURIComponent(departmentId)}`,
      { method:'PUT', body:command },
    )
  },

  changeStatus(
    departmentId: string,
    command: ChangeDepartmentStatusCommand,
  ) {
    return httpClient.request<DepartmentResource>(
      `/departments/${encodeURIComponent(departmentId)}/status`,
      { method:'PUT', body:command },
    )
  },
}
