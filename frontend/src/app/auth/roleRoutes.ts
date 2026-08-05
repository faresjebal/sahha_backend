import type { AppRole } from '../../models/auth'

export const roleHome: Record<AppRole, string> = {
  patient: '/patient/overview',
  doctor: '/doctor/overview',
  staff: '/staff/overview',
  'hospital-super-admin': '/hospital/admin/overview',
  'hospital-operations': '/hospital/operations/overview',
  receptionist: '/reception/patients',
  'platform-admin': '/platform/overview',
}
