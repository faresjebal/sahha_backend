import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { doctors as seedDoctors, patients as seedPatients } from '../../data'
import type {
  AppointmentCommand,
  AppointmentResource,
  ClinicalOrderCommand,
  ClinicalOrderResource,
  DepartmentCommand,
  DepartmentResource,
  Doctor,
  DoctorCommand,
  Patient,
  PatientRegistrationCommand,
} from '../../models/healthcare'
import { services } from '../../services'

export interface LocalAuditEvent {
  id: string
  time: string
  user: string
  action: string
  target: string
  area: string
  result: string
}

interface DemoDataContextValue {
  patients: Patient[]
  doctors: Doctor[]
  departments: DepartmentResource[]
  appointments: AppointmentResource[]
  orders: ClinicalOrderResource[]
  auditEvents: LocalAuditEvent[]
  messages: Record<number, Array<{ from: string; text: string; time: string }>>
  loading: boolean
  error: string
  createPatient(command: PatientRegistrationCommand): Promise<Patient>
  createDoctor(command: DoctorCommand): Promise<Doctor>
  createDepartment(command: DepartmentCommand): Promise<DepartmentResource>
  createAppointment(command: AppointmentCommand): Promise<AppointmentResource>
  cancelAppointment(id: string, reason: string): Promise<void>
  rescheduleAppointment(id: string, startsAt: string, version: number): Promise<void>
  updateAppointmentStatus(id: string, status: AppointmentResource['status'], version: number): Promise<void>
  createOrder(command: ClinicalOrderCommand): Promise<ClinicalOrderResource>
  sendMessage(conversationId: number, text: string): void
  recordAudit(event: Omit<LocalAuditEvent, 'id' | 'time'>): void
}

const DemoDataContext = createContext<DemoDataContextValue | null>(null)
const MESSAGE_KEY = 'aegis-demo-messages-v1'
const AUDIT_KEY = 'aegis-demo-local-audit-v1'

const readLocal = <T,>(key: string, fallback: T): T => {
  try { return JSON.parse(localStorage.getItem(key) || 'null') || fallback }
  catch { return fallback }
}

const defaultAudit: LocalAuditEvent[] = [
  { id:'LOG-8841', time:'14:42:11', user:'Leila Mansour', action:'Changed user permissions', target:'Sofia Alvarez', area:'Access', result:'Logged' },
  { id:'LOG-8837', time:'14:18:04', user:'Omar Haddad', action:'Reviewed capacity plan', target:'Cardiology ward', area:'Operations', result:'Complete' },
  { id:'LOG-8832', time:'13:46:29', user:'Dr. Mara Voss', action:'Opened patient record', target:'PT-1176', area:'Patient data', result:'Logged' },
]

export function DemoDataProvider({ children }: { children: ReactNode }) {
  const [patients, setPatients] = useState<Patient[]>(seedPatients)
  const [doctors, setDoctors] = useState<Doctor[]>(seedDoctors)
  const [departments, setDepartments] = useState<DepartmentResource[]>([])
  const [appointments, setAppointments] = useState<AppointmentResource[]>([])
  const [orders, setOrders] = useState<ClinicalOrderResource[]>([])
  const [messages, setMessages] = useState<Record<number, Array<{ from: string; text: string; time: string }>>>(() => readLocal(MESSAGE_KEY, {}))
  const [auditEvents, setAuditEvents] = useState<LocalAuditEvent[]>(() => readLocal(AUDIT_KEY, defaultAudit))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([
      services.patients.list({ page:0, size:250 }),
      services.doctors.list(),
      services.departments.list(),
      services.appointments.list(),
      services.orders.list(),
    ]).then(([patientPage, doctorList, departmentList, appointmentList, orderList]) => {
      if (!active) return
      setPatients(patientPage.content)
      setDoctors(doctorList)
      setDepartments(departmentList)
      setAppointments(appointmentList)
      setOrders(orderList)
    }).catch(loadError => {
      if (active) setError(loadError instanceof Error ? loadError.message : 'Demo data could not be loaded.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const recordAudit = useCallback((event: Omit<LocalAuditEvent, 'id' | 'time'>) => {
    setAuditEvents(current => {
      const next = [{ ...event, id:`LOG-${Date.now()}`, time:new Intl.DateTimeFormat('en-GB', { hour:'2-digit', minute:'2-digit', second:'2-digit', hour12:false }).format(new Date()) }, ...current]
      localStorage.setItem(AUDIT_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const createPatient = useCallback(async (command: PatientRegistrationCommand) => {
    const patient = await services.patients.create(command)
    setPatients(current => [...current, patient])
    recordAudit({ user:'Sofia Alvarez', action:'Registered patient', target:patient.id, area:'Patient data', result:'Logged' })
    return patient
  }, [recordAudit])

  const createDoctor = useCallback(async (command: DoctorCommand) => {
    const doctor = await services.doctors.create(command)
    setDoctors(current => [...current, doctor])
    recordAudit({ user:'Leila Mansour', action:'Added doctor', target:doctor.name, area:'Access', result:'Logged' })
    return doctor
  }, [recordAudit])

  const createDepartment = useCallback(async (command: DepartmentCommand) => {
    const department = await services.departments.create(command)
    setDepartments(current => [...current, department])
    recordAudit({ user:'Leila Mansour', action:'Created department', target:department.name, area:'Operations', result:'Logged' })
    return department
  }, [recordAudit])

  const createAppointment = useCallback(async (command: AppointmentCommand) => {
    const appointment = await services.appointments.create(command)
    setAppointments(current => [appointment, ...current])
    recordAudit({ user:'Nora Bennett', action:'Requested appointment', target:appointment.id, area:'Operations', result:'Logged' })
    return appointment
  }, [recordAudit])

  const cancelAppointment = useCallback(async (id: string, reason: string) => {
    const appointment = await services.appointments.cancel(id, reason)
    setAppointments(current => current.map(item => item.id === id ? appointment : item))
    recordAudit({ user:'Nora Bennett', action:'Cancelled appointment', target:id, area:'Operations', result:'Logged' })
  }, [recordAudit])

  const rescheduleAppointment = useCallback(async (id: string, startsAt: string, version: number) => {
    const appointment = await services.appointments.reschedule(id, startsAt, version)
    setAppointments(current => current.map(item => item.id === id ? appointment : item))
    recordAudit({ user:'Sofia Alvarez', action:'Rescheduled appointment', target:id, area:'Operations', result:'Logged' })
  }, [recordAudit])

  const updateAppointmentStatus = useCallback(async (id: string, status: AppointmentResource['status'], version: number) => {
    const appointment = await services.appointments.updateStatus(id, status, version)
    setAppointments(current => current.map(item => item.id === id ? appointment : item))
    recordAudit({ user:'Current demo user', action:`Changed appointment to ${status}`, target:id, area:'Operations', result:'Logged' })
  }, [recordAudit])

  const createOrder = useCallback(async (command: ClinicalOrderCommand) => {
    const order = await services.orders.create(command)
    setOrders(current => [order, ...current])
    recordAudit({ user:'Dr. Mara Voss', action:'Created clinical order', target:order.id, area:'Clinical', result:'Logged' })
    return order
  }, [recordAudit])

  const sendMessage = useCallback((conversationId: number, text: string) => {
    setMessages(current => {
      const next = { ...current, [conversationId]:[...(current[conversationId] || []), { from:'me', text:text.trim(), time:'Now' }] }
      localStorage.setItem(MESSAGE_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const value = useMemo<DemoDataContextValue>(() => ({ patients, doctors, departments, appointments, orders, auditEvents, messages, loading, error, createPatient, createDoctor, createDepartment, createAppointment, cancelAppointment, rescheduleAppointment, updateAppointmentStatus, createOrder, sendMessage, recordAudit }), [patients, doctors, departments, appointments, orders, auditEvents, messages, loading, error, createPatient, createDoctor, createDepartment, createAppointment, cancelAppointment, rescheduleAppointment, updateAppointmentStatus, createOrder, sendMessage, recordAudit])
  return <DemoDataContext.Provider value={value}>{children}</DemoDataContext.Provider>
}

export function useDemoData() {
  const context = useContext(DemoDataContext)
  if (!context) throw new Error('useDemoData must be used inside DemoDataProvider')
  return context
}
