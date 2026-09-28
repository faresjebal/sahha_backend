import type { AppointmentResource } from '../../models/scheduling'

// An explicitly assigned administrative role can legitimately list the whole
// organisation. Select per observer: never narrow the shared administrative cache.
export const doctorAppointments = (appointments: AppointmentResource[], userId: string | undefined) =>
  userId ? appointments.filter(appointment => appointment.doctorUserId === userId) : []
