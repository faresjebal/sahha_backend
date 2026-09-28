import type {
  AssignOrganisationAdministratorCommand,
  CreateOrganisationCommand,
  OrganisationMembershipPageResource,
  OrganisationMembershipResource,
  OrganisationContextResource,
  OrganisationPageResource,
  OrganisationResource,
} from '../../models/organisation'
import { httpClient } from './httpClient'

export const organisationRestService = {
  currentProfile() {
    return httpClient.request<OrganisationResource>('/organisations/current/profile')
  },
  updateCurrentProfile(command: Pick<OrganisationResource, 'name'|'contactEmail'|'phoneNumber'|'address'|'city'|'region'|'postalCode'|'countryCode'|'version'>) {
    return httpClient.request<OrganisationResource>('/organisations/current/profile', { method:'PUT', body:command })
  },
  listMyContexts() {
    return httpClient.request<OrganisationContextResource[]>(
      '/organisations/memberships',
    )
  },

  list(page = 0, size = 100) {
    return httpClient.request<OrganisationPageResource>(
      `/platform/organisations?page=${page}&size=${size}`,
    )
  },

  get(organisationId: string) {
    return httpClient.request<OrganisationResource>(
      `/platform/organisations/${encodeURIComponent(organisationId)}`,
    )
  },

  create(command: CreateOrganisationCommand) {
    return httpClient.request<OrganisationResource>('/platform/organisations', {
      method:'POST',
      body:command,
    })
  },

  listAdministrators(organisationId: string, page = 0, size = 20) {
    return httpClient.request<OrganisationMembershipPageResource>(
      `/platform/organisations/${encodeURIComponent(organisationId)}/administrators?page=${page}&size=${size}`,
    )
  },

  getAdministrator(organisationId: string, membershipId: string) {
    return httpClient.request<OrganisationMembershipResource>(
      `/platform/organisations/${encodeURIComponent(organisationId)}/administrators/${encodeURIComponent(membershipId)}`,
    )
  },

  assignAdministrator(
    organisationId: string,
    command: AssignOrganisationAdministratorCommand,
  ) {
    return httpClient.request<OrganisationMembershipResource>(
      `/platform/organisations/${encodeURIComponent(organisationId)}/administrators`,
      { method:'POST', body:command },
    )
  },
}
