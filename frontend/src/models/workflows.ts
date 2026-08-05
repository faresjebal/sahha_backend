import type { StaffRole } from './auth'

export type EncounterStatus = 'DRAFT' | 'IN_PROGRESS' | 'SIGNED' | 'LOCKED'
export type TestStatus = 'ORDERED' | 'SCHEDULED' | 'COLLECTED' | 'PROCESSING' | 'RESULTED' | 'REVIEWED'
export type PrescriptionStatus = 'DRAFT' | 'ACTIVE' | 'DISPENSED' | 'COMPLETED' | 'CANCELLED' | 'DISCONTINUED' | 'REFILL_REQUESTED'
export type BedStatus = 'AVAILABLE' | 'RESERVED' | 'OCCUPIED' | 'CLEANING' | 'MAINTENANCE' | 'OUT_OF_SERVICE'
export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'VOID'
export type ReviewStatus = 'ELIGIBLE' | 'SUBMITTED' | 'PENDING_MODERATION' | 'PUBLISHED' | 'REJECTED' | 'HIDDEN' | 'FLAGGED'

export interface StaffMemberResource {
  id: string
  name: string
  initials: string
  email: string
  phone: string
  role: StaffRole
  departmentId: string
  shift: string
  status: 'ACTIVE' | 'INVITED' | 'SUSPENDED'
  hireDate: string
}

export interface StaffTaskResource {
  id: string
  staffRole: StaffRole
  title: string
  detail: string
  patientId?: string
  departmentId: string
  priority: 'ROUTINE' | 'HIGH' | 'URGENT'
  status: 'TODO' | 'IN_PROGRESS' | 'DONE'
  dueAt: string
}

export interface ObservationResource {
  id: string
  patientId: string
  staffId: string
  type: string
  value: string
  unit?: string
  note?: string
  recordedAt: string
}

export interface MedicationAdministrationResource {
  id: string
  prescriptionId: string
  patientId: string
  staffId: string
  status: 'GIVEN' | 'HELD' | 'REFUSED'
  note?: string
  scheduledAt: string
  recordedAt: string
}

export interface BedResource {
  id: string
  label: string
  departmentId: string
  type: string
  status: BedStatus
  patientId?: string
  updatedAt: string
}

export interface AdmissionResource {
  id: string
  patientId: string
  departmentId: string
  bedId?: string
  status: 'PENDING' | 'ADMITTED' | 'TRANSFER_PENDING' | 'DISCHARGE_PENDING' | 'DISCHARGED'
  admittedAt: string
}

export interface EncounterResource {
  id: string
  patientId: string
  doctorId: number
  appointmentId?: string
  status: EncounterStatus
  subjective: string
  objective: string
  assessment: string
  plan: string
  createdAt: string
  signedAt?: string
}

export interface DiagnosisResource {
  id: string
  encounterId: string
  patientId: string
  doctorId: number
  code?: string
  description: string
  diagnosedAt: string
}

export interface PrescriptionResource {
  id: string
  encounterId?: string
  patientId: string
  doctorId: number
  medication: string
  dosage: string
  frequency: string
  instructions: string
  startDate: string
  endDate?: string
  status: PrescriptionStatus
}

export interface TestResource {
  id: string
  patientId: string
  doctorId: number
  name: string
  type: 'LABORATORY' | 'IMAGING'
  indication: string
  priority: 'ROUTINE' | 'URGENT'
  status: TestStatus
  result?: string
  attachmentId?: string
  orderedAt: string
}

export interface AttachmentResource {
  id: string
  ownerType: 'PATIENT' | 'ENCOUNTER' | 'TEST' | 'MESSAGE'
  ownerId: string
  fileName: string
  fileType: string
  sizeLabel: string
  uploadedBy: string
  uploadedAt: string
}

export interface ReviewResource {
  id: string
  appointmentId: string
  patientId: string
  doctorId: number
  rating: number
  comment: string
  status: ReviewStatus
  createdAt: string
}

export interface ConversationResource {
  id: string
  type: 'DIRECT' | 'GROUP' | 'CASE_DISCUSSION'
  title: string
  participantIds: string[]
  patientId?: string
  updatedAt: string
}

export interface MessageResource {
  id: string
  conversationId: string
  senderId: string
  senderName: string
  body: string
  sentAt: string
  readBy: string[]
  attachmentIds: string[]
}

export interface IncidentResource {
  id: string
  title: string
  description: string
  departmentId: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: IncidentStatus
  reportedBy: string
  assignedTo?: string
  createdAt: string
  resolvedAt?: string
}

export interface HandoverResource {
  id: string
  departmentId: string
  title: string
  detail: string
  priority: 'ROUTINE' | 'HIGH' | 'URGENT'
  owner: string
  acknowledgedBy: string[]
  createdAt: string
}

export interface InvoiceResource {
  id: string
  patientId: string
  appointmentId?: string
  doctorId?: number
  description: string
  totalAmount: number
  paidAmount: number
  status: InvoiceStatus
  issuedAt: string
  dueAt: string
}

export interface PaymentResource {
  id: string
  invoiceId: string
  amount: number
  method: 'CARD' | 'CASH' | 'BANK_TRANSFER' | 'INSURANCE'
  reference?: string
  paidAt: string
}

export interface BudgetResource {
  id: string
  departmentId: string
  year: number
  allocated: number
  spent: number
  description: string
}

export interface ExpenseResource {
  id: string
  budgetId: string
  category: string
  description: string
  amount: number
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'
  createdAt: string
}

export interface DoctorAssignmentResource {
  id: string
  doctorId: number
  departmentId: string
  position: string
  startDate: string
  endDate?: string
}
