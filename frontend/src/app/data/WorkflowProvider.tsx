import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { useDemoData } from './DemoDataProvider'
import type {
  AdmissionResource,
  AttachmentResource,
  BedResource,
  BudgetResource,
  ConversationResource,
  DiagnosisResource,
  DoctorAssignmentResource,
  EncounterResource,
  ExpenseResource,
  HandoverResource,
  IncidentResource,
  InvoiceResource,
  MessageResource,
  MedicationAdministrationResource,
  ObservationResource,
  PaymentResource,
  PrescriptionResource,
  ReviewResource,
  StaffMemberResource,
  StaffTaskResource,
  TestResource,
} from '../../models/workflows'

export interface WorkflowState {
  staff: StaffMemberResource[]
  tasks: StaffTaskResource[]
  observations: ObservationResource[]
  medicationAdministrations: MedicationAdministrationResource[]
  beds: BedResource[]
  admissions: AdmissionResource[]
  encounters: EncounterResource[]
  diagnoses: DiagnosisResource[]
  prescriptions: PrescriptionResource[]
  tests: TestResource[]
  attachments: AttachmentResource[]
  reviews: ReviewResource[]
  conversations: ConversationResource[]
  workflowMessages: MessageResource[]
  incidents: IncidentResource[]
  handovers: HandoverResource[]
  invoices: InvoiceResource[]
  payments: PaymentResource[]
  budgets: BudgetResource[]
  expenses: ExpenseResource[]
  doctorAssignments: DoctorAssignmentResource[]
}

interface WorkflowContextValue extends WorkflowState {
  addStaff(command: Omit<StaffMemberResource, 'id' | 'initials' | 'status'>): StaffMemberResource
  updateTask(id: string, status: StaffTaskResource['status']): void
  addTask(command: Omit<StaffTaskResource, 'id' | 'status'>): StaffTaskResource
  addObservation(command: Omit<ObservationResource, 'id' | 'recordedAt'>): ObservationResource
  addMedicationAdministration(command: Omit<MedicationAdministrationResource, 'id' | 'recordedAt'>): MedicationAdministrationResource
  updateBed(id: string, status: BedResource['status'], patientId?: string): void
  addAdmission(command: Omit<AdmissionResource, 'id' | 'status' | 'admittedAt'>): AdmissionResource
  updateAdmission(id: string, status: AdmissionResource['status'], departmentId?: string, bedId?: string): void
  saveEncounter(command: Omit<EncounterResource, 'id' | 'createdAt' | 'status'> & { id?: string; status?: EncounterResource['status'] }): EncounterResource
  signEncounter(id: string): void
  addDiagnosis(command: Omit<DiagnosisResource, 'id' | 'diagnosedAt'>): DiagnosisResource
  addPrescription(command: Omit<PrescriptionResource, 'id' | 'status'>): PrescriptionResource
  updatePrescription(id: string, status: PrescriptionResource['status']): void
  addTest(command: Omit<TestResource, 'id' | 'status' | 'orderedAt'>): TestResource
  updateTest(id: string, status: TestResource['status'], result?: string): void
  addAttachment(command: Omit<AttachmentResource, 'id' | 'uploadedAt'>): AttachmentResource
  submitReview(command: Omit<ReviewResource, 'id' | 'status' | 'createdAt'>): ReviewResource
  moderateReview(id: string, status: ReviewResource['status']): void
  addConversation(command: Omit<ConversationResource, 'id' | 'updatedAt'>): ConversationResource
  sendWorkflowMessage(conversationId: string, body: string, attachmentIds?: string[]): MessageResource
  addIncident(command: Omit<IncidentResource, 'id' | 'status' | 'createdAt'>): IncidentResource
  updateIncident(id: string, status: IncidentResource['status'], assignedTo?: string): void
  addHandover(command: Omit<HandoverResource, 'id' | 'acknowledgedBy' | 'createdAt'>): HandoverResource
  acknowledgeHandover(id: string): void
  addInvoice(command: Omit<InvoiceResource, 'id' | 'paidAmount' | 'status' | 'issuedAt'>): InvoiceResource
  recordPayment(command: Omit<PaymentResource, 'id' | 'paidAt'>): PaymentResource
  addBudget(command: Omit<BudgetResource, 'id' | 'spent'>): BudgetResource
  addExpense(command: Omit<ExpenseResource, 'id' | 'status' | 'createdAt'>): ExpenseResource
  updateExpense(id: string, status: ExpenseResource['status']): void
  addDoctorAssignment(command: Omit<DoctorAssignmentResource, 'id'>): DoctorAssignmentResource
  resetDemo(): void
}

const STORAGE_KEY = 'aegis-workflow-state-v1'
const WorkflowContext = createContext<WorkflowContextValue | null>(null)
const iso = (days = 0, hour = 10) => { const value = new Date(); value.setDate(value.getDate() + days); value.setHours(hour, 0, 0, 0); return value.toISOString() }
const date = (days = 0) => iso(days).slice(0, 10)

const seedState = (): WorkflowState => ({
  staff: [
    {id:'STF-2001',name:'Elena Petrov',initials:'EP',email:'elena.p@sthelena.health',phone:'+1 312 555 0192',role:'NURSE',departmentId:'DEP-CARD',shift:'07:00–15:00',status:'ACTIVE',hireDate:'2022-04-11'},
    {id:'STF-2002',name:'Mateo Silva',initials:'MS',email:'mateo.s@sthelena.health',phone:'+1 312 555 0181',role:'LABORATORY',departmentId:'DEP-LAB',shift:'08:00–16:00',status:'ACTIVE',hireDate:'2021-09-18'},
    {id:'STF-2003',name:'Amina Diallo',initials:'AD',email:'amina.d@sthelena.health',phone:'+1 312 555 0177',role:'PHARMACY',departmentId:'DEP-PHARM',shift:'09:00–17:00',status:'ACTIVE',hireDate:'2023-02-06'},
    {id:'STF-2004',name:'Jonas Berg',initials:'JB',email:'jonas.b@sthelena.health',phone:'+1 312 555 0142',role:'BED_COORDINATOR',departmentId:'DEP-EM',shift:'07:00–19:00',status:'ACTIVE',hireDate:'2020-11-23'},
  ],
  tasks: [
    {id:'TSK-401',staffRole:'NURSE',title:'Record post-round observations',detail:'Nora Bennett · blood pressure and symptom check',patientId:'PT-1048',departmentId:'DEP-CARD',priority:'HIGH',status:'TODO',dueAt:iso(0,11)},
    {id:'TSK-402',staffRole:'LABORATORY',title:'Collect metabolic panel',detail:'Routine monitoring order OR-4821',patientId:'PT-1048',departmentId:'DEP-LAB',priority:'ROUTINE',status:'IN_PROGRESS',dueAt:iso(0,12)},
    {id:'TSK-403',staffRole:'PHARMACY',title:'Validate refill request',detail:'Amlodipine 5 mg · 30-day supply',patientId:'PT-1048',departmentId:'DEP-PHARM',priority:'ROUTINE',status:'TODO',dueAt:iso(0,14)},
    {id:'TSK-404',staffRole:'BED_COORDINATOR',title:'Prepare monitored bed',detail:'Cardiology admission expected at 13:30',patientId:'PT-1176',departmentId:'DEP-CARD',priority:'URGENT',status:'TODO',dueAt:iso(0,13)},
  ],
  observations: [{id:'OBS-301',patientId:'PT-1048',staffId:'STF-2001',type:'Blood pressure',value:'142/89',unit:'mmHg',note:'Seated after five minutes of rest.',recordedAt:iso(-1,18)}],
  medicationAdministrations: [],
  beds: [
    {id:'BED-C201',label:'C-201',departmentId:'DEP-CARD',type:'Monitored',status:'OCCUPIED',patientId:'PT-1176',updatedAt:iso(-1)},
    {id:'BED-C202',label:'C-202',departmentId:'DEP-CARD',type:'Monitored',status:'AVAILABLE',updatedAt:iso(0)},
    {id:'BED-C203',label:'C-203',departmentId:'DEP-CARD',type:'Standard',status:'CLEANING',updatedAt:iso(0,9)},
    {id:'BED-E014',label:'E-014',departmentId:'DEP-EM',type:'Observation',status:'RESERVED',patientId:'PT-0884',updatedAt:iso(0,8)},
    {id:'BED-P108',label:'P-108',departmentId:'DEP-PED',type:'Pediatric',status:'AVAILABLE',updatedAt:iso(0)},
    {id:'BED-N305',label:'N-305',departmentId:'DEP-NEUR',type:'High dependency',status:'MAINTENANCE',updatedAt:iso(-1)},
  ],
  admissions: [
    {id:'ADM-701',patientId:'PT-1176',departmentId:'DEP-CARD',bedId:'BED-C201',status:'ADMITTED',admittedAt:iso(-1,16)},
    {id:'ADM-702',patientId:'PT-0884',departmentId:'DEP-EM',bedId:'BED-E014',status:'PENDING',admittedAt:iso(0,13)},
  ],
  encounters: [
    {id:'ENC-3301',patientId:'PT-1048',doctorId:1,appointmentId:'APT-2398',status:'SIGNED',subjective:'Home readings remain elevated in the evening.',objective:'Blood pressure 142/89; no acute symptoms.',assessment:'Hypertension remains above the agreed target.',plan:'Continue monitoring and review medication response in four weeks.',createdAt:iso(-32),signedAt:iso(-32,14)},
  ],
  diagnoses: [
    {id:'DX-8201',encounterId:'ENC-3301',patientId:'PT-1048',doctorId:1,code:'I10',description:'Essential hypertension',diagnosedAt:iso(-32)},
  ],
  prescriptions: [
    {id:'RX-6101',encounterId:'ENC-3301',patientId:'PT-1048',doctorId:1,medication:'Amlodipine',dosage:'5 mg',frequency:'Once daily',instructions:'Take at the same time each morning.',startDate:date(-32),status:'ACTIVE'},
    {id:'RX-6102',patientId:'PT-0921',doctorId:3,medication:'Metformin',dosage:'500 mg',frequency:'Twice daily',instructions:'Take with food.',startDate:date(-90),status:'DISPENSED'},
  ],
  tests: [
    {id:'TST-4821',patientId:'PT-1048',doctorId:1,name:'Comprehensive metabolic panel',type:'LABORATORY',indication:'Medication monitoring',priority:'ROUTINE',status:'ORDERED',orderedAt:iso(-1)},
    {id:'TST-4816',patientId:'PT-1176',doctorId:1,name:'Cardiac MRI',type:'IMAGING',indication:'Myocarditis assessment',priority:'URGENT',status:'RESULTED',result:'Findings are consistent with resolving inflammation.',attachmentId:'ATT-9002',orderedAt:iso(-3)},
  ],
  attachments: [
    {id:'ATT-9001',ownerType:'PATIENT',ownerId:'PT-1048',fileName:'Cardiology visit summary.pdf',fileType:'application/pdf',sizeLabel:'184 KB',uploadedBy:'Dr. Mara Voss',uploadedAt:iso(-32)},
    {id:'ATT-9002',ownerType:'TEST',ownerId:'TST-4816',fileName:'Cardiac MRI report.pdf',fileType:'application/pdf',sizeLabel:'1.2 MB',uploadedBy:'Imaging services',uploadedAt:iso(-1)},
  ],
  reviews: [
    {id:'REV-3001',appointmentId:'APT-2201',patientId:'PT-0921',doctorId:3,rating:5,comment:'The plan was explained clearly and the follow-up was well organized.',status:'PUBLISHED',createdAt:iso(-18)},
  ],
  conversations: [
    {id:'CONV-101',type:'DIRECT',title:'Cardiology care team',participantIds:['USR-P-2401','USR-D-1042'],patientId:'PT-1048',updatedAt:iso(-1)},
    {id:'CONV-201',type:'CASE_DISCUSSION',title:'PT-1176 cardiac review',participantIds:['USR-D-1042','USR-D-1043'],patientId:'PT-1176',updatedAt:iso(0)},
  ],
  workflowMessages: [
    {id:'MSG-8001',conversationId:'CONV-101',senderId:'USR-D-1042',senderName:'Dr. Mara Voss',body:'Please continue recording your evening readings through Tuesday.',sentAt:iso(-1,14),readBy:['USR-D-1042','USR-P-2401'],attachmentIds:[]},
    {id:'MSG-8002',conversationId:'CONV-201',senderId:'USR-D-1043',senderName:'Dr. Elias Chen',body:'I reviewed the imaging report and added a neurology note for the team.',sentAt:iso(0,9),readBy:['USR-D-1043'],attachmentIds:[]},
  ],
  incidents: [
    {id:'INC-501',title:'MRI suite cooling inspection',description:'Imaging capacity is reduced while facilities completes the inspection.',departmentId:'DEP-RAD',severity:'HIGH',status:'IN_PROGRESS',reportedBy:'Omar Haddad',assignedTo:'Facilities on-call',createdAt:iso(-1,17)},
    {id:'INC-502',title:'Pediatrics evening coverage gap',description:'Resident coverage needs confirmation from 18:00 to 21:00.',departmentId:'DEP-PED',severity:'MEDIUM',status:'OPEN',reportedBy:'Elena Petrov',createdAt:iso(0,8)},
  ],
  handovers: [
    {id:'HND-301',departmentId:'DEP-CARD',title:'Monitor evening blood-pressure cases',detail:'Two patients need repeat observations before the medication round.',priority:'HIGH',owner:'Elena Petrov',acknowledgedBy:[],createdAt:iso(0,14)},
    {id:'HND-302',departmentId:'DEP-EM',title:'Observation beds near threshold',detail:'Confirm discharge readiness before accepting the 18:00 transfer.',priority:'URGENT',owner:'Jonas Berg',acknowledgedBy:['Omar Haddad'],createdAt:iso(0,13)},
  ],
  invoices: [
    {id:'INV-7001',patientId:'PT-1048',appointmentId:'APT-2398',doctorId:1,description:'Cardiology consultation',totalAmount:184,paidAmount:0,status:'ISSUED',issuedAt:iso(-32),dueAt:date(7)},
    {id:'INV-6994',patientId:'PT-0921',appointmentId:'APT-2401',doctorId:3,description:'Diabetes review',totalAmount:126,paidAmount:126,status:'PAID',issuedAt:iso(-4),dueAt:date(10)},
  ],
  payments: [{id:'PAY-4011',invoiceId:'INV-6994',amount:126,method:'CARD',reference:'CARD-4418',paidAt:iso(-3)}],
  budgets: [
    {id:'BUD-101',departmentId:'DEP-CARD',year:new Date().getFullYear(),allocated:1280000,spent:824600,description:'Cardiology operating budget'},
    {id:'BUD-102',departmentId:'DEP-EM',year:new Date().getFullYear(),allocated:1840000,spent:1397700,description:'Emergency medicine operating budget'},
    {id:'BUD-103',departmentId:'DEP-PED',year:new Date().getFullYear(),allocated:960000,spent:601400,description:'Pediatrics operating budget'},
  ],
  expenses: [
    {id:'EXP-501',budgetId:'BUD-101',category:'Clinical supplies',description:'Ambulatory monitoring devices',amount:18240,status:'APPROVED',createdAt:iso(-8)},
    {id:'EXP-502',budgetId:'BUD-102',category:'Facilities',description:'Observation bay equipment service',amount:7860,status:'SUBMITTED',createdAt:iso(-2)},
  ],
  doctorAssignments: [
    {id:'ASN-101',doctorId:1,departmentId:'DEP-CARD',position:'Attending cardiologist',startDate:'2022-03-01'},
    {id:'ASN-102',doctorId:2,departmentId:'DEP-NEUR',position:'Consultant neurologist',startDate:'2023-08-14'},
  ],
})

const loadState = (): WorkflowState => {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null') || seedState() }
  catch { return seedState() }
}

const nextId = (prefix: string) => `${prefix}-${Date.now().toString().slice(-7)}`

export function WorkflowProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<WorkflowState>(loadState)
  const { recordAudit, appointments, updateAppointmentStatus } = useDemoData()
  const auth = useAuth()
  const actor = auth.session?.user.displayName || 'Demo user'
  useEffect(() => { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)) }, [state])
  const audit = useCallback((action: string, target: string, area = 'Operations') => recordAudit({ user:actor, action, target, area, result:'Logged' }), [actor, recordAudit])

  const addStaff = useCallback((command: Omit<StaffMemberResource, 'id' | 'initials' | 'status'>) => {
    const item: StaffMemberResource = { ...command, id:nextId('STF'), initials:command.name.split(' ').map(part=>part[0]).join('').slice(0,2).toUpperCase(), status:'INVITED' }
    setState(current=>({...current,staff:[item,...current.staff]})); audit('Invited staff member',item.name,'Access'); return item
  },[audit])
  const updateTask = useCallback((id:string,status:StaffTaskResource['status'])=>{setState(current=>({...current,tasks:current.tasks.map(item=>item.id===id?{...item,status}:item)}));audit(`Changed task to ${status}`,id)},[audit])
  const addTask = useCallback((command:Omit<StaffTaskResource,'id'|'status'>)=>{const item={...command,id:nextId('TSK'),status:'TODO' as const};setState(current=>({...current,tasks:[item,...current.tasks]}));audit('Created staff task',item.id);return item},[audit])
  const addObservation = useCallback((command:Omit<ObservationResource,'id'|'recordedAt'>)=>{const item={...command,id:nextId('OBS'),recordedAt:new Date().toISOString()};setState(current=>({...current,observations:[item,...current.observations]}));audit('Recorded patient observation',item.patientId,'Clinical');return item},[audit])
  const addMedicationAdministration = useCallback((command:Omit<MedicationAdministrationResource,'id'|'recordedAt'>)=>{const item={...command,id:nextId('MAR'),recordedAt:new Date().toISOString()};setState(current=>({...current,medicationAdministrations:[item,...current.medicationAdministrations]}));audit(`Medication ${item.status.toLowerCase()}`,item.patientId,'Clinical');return item},[audit])
  const updateBed = useCallback((id:string,status:BedResource['status'],patientId?:string)=>{setState(current=>({...current,beds:current.beds.map(item=>item.id===id?{...item,status,patientId:status==='AVAILABLE'||status==='CLEANING'?undefined:patientId||item.patientId,updatedAt:new Date().toISOString()}:item)}));audit(`Changed bed to ${status}`,id)},[audit])
  const addAdmission = useCallback((command:Omit<AdmissionResource,'id'|'status'|'admittedAt'>)=>{const item={...command,id:nextId('ADM'),status:'PENDING' as const,admittedAt:new Date().toISOString()};setState(current=>({...current,admissions:[item,...current.admissions],beds:current.beds.map(bed=>bed.id===item.bedId?{...bed,status:'RESERVED',patientId:item.patientId,updatedAt:new Date().toISOString()}:bed)}));audit('Created admission',item.id,'Patient data');return item},[audit])
  const updateAdmission = useCallback((id:string,status:AdmissionResource['status'],departmentId?:string,bedId?:string)=>{setState(current=>{const existing=current.admissions.find(item=>item.id===id);const nextBedId=bedId||existing?.bedId;return{...current,admissions:current.admissions.map(item=>item.id===id?{...item,status,departmentId:departmentId||item.departmentId,bedId:nextBedId}:item),beds:current.beds.map(bed=>{if(existing?.bedId&&existing.bedId!==nextBedId&&bed.id===existing.bedId)return{...bed,status:'CLEANING',patientId:undefined,updatedAt:new Date().toISOString()};if(bed.id!==nextBedId)return bed;if(status==='DISCHARGED')return{...bed,status:'CLEANING',patientId:undefined,updatedAt:new Date().toISOString()};return{...bed,status:status==='ADMITTED'?'OCCUPIED':'RESERVED',patientId:existing?.patientId,updatedAt:new Date().toISOString()}})}});audit(`Changed admission to ${status}`,id,'Patient data')},[audit])
  const saveEncounter = useCallback((command:Omit<EncounterResource,'id'|'createdAt'|'status'>&{id?:string;status?:EncounterResource['status']})=>{let result:EncounterResource;setState(current=>{const existing=command.id?current.encounters.find(item=>item.id===command.id):undefined;result={...command,id:command.id||nextId('ENC'),createdAt:existing?.createdAt||new Date().toISOString(),status:command.status||'IN_PROGRESS'};return{...current,encounters:existing?current.encounters.map(item=>item.id===result.id?result:item):[result,...current.encounters]}});audit('Saved encounter draft',command.patientId,'Clinical');return result!},[audit])
  const signEncounter = useCallback((id:string)=>{setState(current=>({...current,encounters:current.encounters.map(item=>item.id===id?{...item,status:'SIGNED',signedAt:new Date().toISOString()}:item)}));const encounter=state.encounters.find(item=>item.id===id);if(encounter?.appointmentId){const appointment=appointments.find(item=>item.id===encounter.appointmentId);if(appointment)void updateAppointmentStatus(appointment.id,'COMPLETED',appointment.version)}audit('Signed encounter',id,'Clinical')},[appointments,audit,state.encounters,updateAppointmentStatus])
  const addDiagnosis = useCallback((command:Omit<DiagnosisResource,'id'|'diagnosedAt'>)=>{const item={...command,id:nextId('DX'),diagnosedAt:new Date().toISOString()};setState(current=>({...current,diagnoses:[item,...current.diagnoses]}));audit('Added diagnosis',item.patientId,'Clinical');return item},[audit])
  const addPrescription = useCallback((command:Omit<PrescriptionResource,'id'|'status'>)=>{const item={...command,id:nextId('RX'),status:'ACTIVE' as const};setState(current=>({...current,prescriptions:[item,...current.prescriptions]}));audit('Created prescription',item.patientId,'Clinical');return item},[audit])
  const updatePrescription = useCallback((id:string,status:PrescriptionResource['status'])=>{setState(current=>({...current,prescriptions:current.prescriptions.map(item=>item.id===id?{...item,status}:item)}));audit(`Changed prescription to ${status}`,id,'Clinical')},[audit])
  const addTest = useCallback((command:Omit<TestResource,'id'|'status'|'orderedAt'>)=>{const item={...command,id:nextId('TST'),status:'ORDERED' as const,orderedAt:new Date().toISOString()};setState(current=>({...current,tests:[item,...current.tests]}));audit('Ordered test',item.patientId,'Clinical');return item},[audit])
  const updateTest = useCallback((id:string,status:TestResource['status'],result?:string)=>{setState(current=>({...current,tests:current.tests.map(item=>item.id===id?{...item,status,result:result||item.result}:item)}));audit(`Changed test to ${status}`,id,'Clinical')},[audit])
  const addAttachment = useCallback((command:Omit<AttachmentResource,'id'|'uploadedAt'>)=>{const item={...command,id:nextId('ATT'),uploadedAt:new Date().toISOString()};setState(current=>({...current,attachments:[item,...current.attachments]}));audit('Added attachment',item.fileName,'Clinical');return item},[audit])
  const submitReview = useCallback((command:Omit<ReviewResource,'id'|'status'|'createdAt'>)=>{const appointment=appointments.find(item=>item.id===command.appointmentId);if(!appointment||appointment.status!=='COMPLETED')throw new Error('Reviews are available after a completed appointment.');const item={...command,id:nextId('REV'),status:'PENDING_MODERATION' as const,createdAt:new Date().toISOString()};setState(current=>({...current,reviews:[item,...current.reviews]}));audit('Submitted verified review',item.id,'Patient data');return item},[appointments,audit])
  const moderateReview = useCallback((id:string,status:ReviewResource['status'])=>{setState(current=>({...current,reviews:current.reviews.map(item=>item.id===id?{...item,status}:item)}));audit(`Changed review to ${status}`,id,'Trust')},[audit])
  const addConversation = useCallback((command:Omit<ConversationResource,'id'|'updatedAt'>)=>{const item={...command,id:nextId('CONV'),updatedAt:new Date().toISOString()};setState(current=>({...current,conversations:[item,...current.conversations]}));audit('Created conversation',item.title,'Clinical');return item},[audit])
  const sendWorkflowMessage = useCallback((conversationId:string,body:string,attachmentIds:string[]=[] )=>{const item:MessageResource={id:nextId('MSG'),conversationId,senderId:auth.session?.user.id||'DEMO',senderName:actor,body:body.trim(),sentAt:new Date().toISOString(),readBy:[auth.session?.user.id||'DEMO'],attachmentIds};setState(current=>({...current,workflowMessages:[...current.workflowMessages,item],conversations:current.conversations.map(conversation=>conversation.id===conversationId?{...conversation,updatedAt:item.sentAt}:conversation)}));return item},[actor,auth.session?.user.id])
  const addIncident = useCallback((command:Omit<IncidentResource,'id'|'status'|'createdAt'>)=>{const item={...command,id:nextId('INC'),status:'OPEN' as const,createdAt:new Date().toISOString()};setState(current=>({...current,incidents:[item,...current.incidents]}));audit('Reported incident',item.id,'Operations');return item},[audit])
  const updateIncident = useCallback((id:string,status:IncidentResource['status'],assignedTo?:string)=>{setState(current=>({...current,incidents:current.incidents.map(item=>item.id===id?{...item,status,assignedTo:assignedTo||item.assignedTo,resolvedAt:status==='RESOLVED'||status==='CLOSED'?new Date().toISOString():item.resolvedAt}:item)}));audit(`Changed incident to ${status}`,id,'Operations')},[audit])
  const addHandover = useCallback((command:Omit<HandoverResource,'id'|'acknowledgedBy'|'createdAt'>)=>{const item={...command,id:nextId('HND'),acknowledgedBy:[],createdAt:new Date().toISOString()};setState(current=>({...current,handovers:[item,...current.handovers]}));audit('Created handover item',item.id,'Operations');return item},[audit])
  const acknowledgeHandover = useCallback((id:string)=>{setState(current=>({...current,handovers:current.handovers.map(item=>item.id===id&&!item.acknowledgedBy.includes(actor)?{...item,acknowledgedBy:[...item.acknowledgedBy,actor]}:item)}));audit('Acknowledged handover',id,'Operations')},[actor,audit])
  const addInvoice = useCallback((command:Omit<InvoiceResource,'id'|'paidAmount'|'status'|'issuedAt'>)=>{const item={...command,id:nextId('INV'),paidAmount:0,status:'ISSUED' as const,issuedAt:new Date().toISOString()};setState(current=>({...current,invoices:[item,...current.invoices]}));audit('Issued invoice',item.id,'Finance');return item},[audit])
  const recordPayment = useCallback((command:Omit<PaymentResource,'id'|'paidAt'>)=>{const item={...command,id:nextId('PAY'),paidAt:new Date().toISOString()};setState(current=>({...current,payments:[item,...current.payments],invoices:current.invoices.map(invoice=>{if(invoice.id!==command.invoiceId)return invoice;const paid=Math.min(invoice.totalAmount,invoice.paidAmount+command.amount);return{...invoice,paidAmount:paid,status:paid>=invoice.totalAmount?'PAID':'PARTIALLY_PAID'}})}));audit('Recorded payment',item.invoiceId,'Finance');return item},[audit])
  const addBudget = useCallback((command:Omit<BudgetResource,'id'|'spent'>)=>{const item={...command,id:nextId('BUD'),spent:0};setState(current=>({...current,budgets:[item,...current.budgets]}));audit('Created budget',item.id,'Finance');return item},[audit])
  const addExpense = useCallback((command:Omit<ExpenseResource,'id'|'status'|'createdAt'>)=>{const item={...command,id:nextId('EXP'),status:'SUBMITTED' as const,createdAt:new Date().toISOString()};setState(current=>({...current,expenses:[item,...current.expenses]}));audit('Submitted expense',item.id,'Finance');return item},[audit])
  const updateExpense = useCallback((id:string,status:ExpenseResource['status'])=>{setState(current=>{const expense=current.expenses.find(item=>item.id===id);const delta=expense?(status==='APPROVED'?expense.amount:0)-(expense.status==='APPROVED'?expense.amount:0):0;return{...current,expenses:current.expenses.map(item=>item.id===id?{...item,status}:item),budgets:delta?current.budgets.map(budget=>expense?.budgetId===budget.id?{...budget,spent:budget.spent+delta}:budget):current.budgets}});audit(`Changed expense to ${status}`,id,'Finance')},[audit])
  const addDoctorAssignment = useCallback((command:Omit<DoctorAssignmentResource,'id'>)=>{const item={...command,id:nextId('ASN')};setState(current=>({...current,doctorAssignments:[item,...current.doctorAssignments]}));audit('Assigned doctor to department',item.id,'Access');return item},[audit])
  const resetDemo = useCallback(()=>{for(let index=localStorage.length-1;index>=0;index-=1){const key=localStorage.key(index);if(key?.startsWith('aegis-')&&key!=='aegis-theme')localStorage.removeItem(key)}window.location.assign('/login')},[])

  const value=useMemo<WorkflowContextValue>(()=>({...state,addStaff,updateTask,addTask,addObservation,addMedicationAdministration,updateBed,addAdmission,updateAdmission,saveEncounter,signEncounter,addDiagnosis,addPrescription,updatePrescription,addTest,updateTest,addAttachment,submitReview,moderateReview,addConversation,sendWorkflowMessage,addIncident,updateIncident,addHandover,acknowledgeHandover,addInvoice,recordPayment,addBudget,addExpense,updateExpense,addDoctorAssignment,resetDemo}),[state,addStaff,updateTask,addTask,addObservation,addMedicationAdministration,updateBed,addAdmission,updateAdmission,saveEncounter,signEncounter,addDiagnosis,addPrescription,updatePrescription,addTest,updateTest,addAttachment,submitReview,moderateReview,addConversation,sendWorkflowMessage,addIncident,updateIncident,addHandover,acknowledgeHandover,addInvoice,recordPayment,addBudget,addExpense,updateExpense,addDoctorAssignment,resetDemo])
  return <WorkflowContext.Provider value={value}>{children}</WorkflowContext.Provider>
}

export function useWorkflow() {
  const context=useContext(WorkflowContext)
  if(!context)throw new Error('useWorkflow must be used inside WorkflowProvider')
  return context
}
