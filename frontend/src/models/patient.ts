export type PatientSex = 'FEMALE' | 'MALE' | 'INTERSEX' | 'UNDISCLOSED'
export type PatientIdentifierType = 'NATIONAL_ID' | 'PASSPORT'
export type DuplicateDecision = 'CREATE_NEW' | 'LINK_EXISTING'
export type DuplicateDecisionReason =
  | 'SAME_PERSON_CONFIRMED'
  | 'DEMOGRAPHIC_MATCH_CONFIRMED_DISTINCT'
  | 'CONTACT_INFORMATION_SHARED'
  | 'DATA_ENTRY_CORRECTION'
  | 'OTHER_REVIEWED'

export interface PatientIdentifierCommand {
  type: PatientIdentifierType
  value: string
  countryCode: string
}

export interface PatientDuplicateCheckCommand {
  firstName: string
  lastName: string
  dateOfBirth: string
  sex: PatientSex
  phoneNumber: string
  email: string | null
  identifier: PatientIdentifierCommand | null
}

export interface CreatePatientRegistrationCommand
  extends PatientDuplicateCheckCommand {
  address: string
  city: string | null
  region: string | null
  postalCode: string | null
  countryCode: string
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  emergencyContactRelationship: string | null
  preferredLanguage: string | null
  accessibilityNeeds: string | null
  privacyNoticeAcknowledged: boolean
  duplicateDecision?: DuplicateDecision
  selectedPatientId?: string
  duplicateDecisionReason?: DuplicateDecisionReason
}

export interface UpdatePatientRegistrationCommand {
  firstName: string
  lastName: string
  dateOfBirth: string
  sex: PatientSex
  identifier: PatientIdentifierCommand | null
  removeIdentifier: boolean
  phoneNumber: string
  email: string | null
  address: string
  city: string | null
  region: string | null
  postalCode: string | null
  countryCode: string
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  emergencyContactRelationship: string | null
  preferredLanguage: string | null
  accessibilityNeeds: string | null
  registrationVersion: number
  identityVersion: number
  duplicateDecision?: DuplicateDecision
  duplicateDecisionReason?: DuplicateDecisionReason
}

export type DuplicateMatchReason =
  | 'STRONG_IDENTIFIER'
  | 'NAME_AND_DATE_OF_BIRTH'
  | 'PHONE_NUMBER'
  | 'EMAIL'

export interface DuplicateCandidateResource {
  patientId: string
  maskedDisplayName: string
  birthYear: number
  sex: PatientSex
  maskedIdentifier: string | null
  score: number
  exactStrongIdentifierMatch: boolean
  matchReasons: DuplicateMatchReason[]
  registeredInActiveOrganisation: boolean
  activeOrganisationRegistrationId: string | null
}

export interface PatientDuplicateCheckResource {
  reviewRequired: boolean
  exactStrongIdentifierMatch: boolean
  candidates: DuplicateCandidateResource[]
}

export interface PatientAdministrativeSummaryResource {
  registrationId: string
  patientId: string
  medicalRecordNumber: string
  firstName: string
  lastName: string
  dateOfBirth: string
  sex: PatientSex
  phoneNumber: string
  email: string | null
  registrationStatus: 'ACTIVE' | 'INACTIVE'
  registeredAt: string
  registrationVersion: number
  identityVersion: number
}

export interface PatientAdministrativeResource
  extends PatientAdministrativeSummaryResource {
  organisationId: string
  identifierType: PatientIdentifierType | null
  maskedIdentifier: string | null
  identifierCountryCode: string | null
  address: string
  city: string | null
  region: string | null
  postalCode: string | null
  countryCode: string
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  emergencyContactRelationship: string | null
  preferredLanguage: string | null
  accessibilityNeeds: string | null
  privacyNoticeAcknowledgedAt: string
  registeredBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface PatientAdministrativePageResource {
  items: PatientAdministrativeSummaryResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
