import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Building2, CalendarDays, Check, Stethoscope, UserPlus, X } from 'lucide-react'
import { useDemoData } from '../../app/data/DemoDataProvider'

interface DrawerProps {
  open: boolean
  onClose(): void
  title: string
  eyebrow: string
  copy: string
  icon: ReactNode
  children: ReactNode
}

function DrawerFrame({ open, onClose, title, eyebrow, copy, icon, children }: DrawerProps) {
  const ref = useRef<HTMLElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    if (!open) return
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const node = ref.current
    const controls = () => Array.from(node?.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])') || [])
    window.requestAnimationFrame(() => controls()[0]?.focus())
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); closeRef.current(); return }
      if (event.key !== 'Tab') return
      const items = controls(); if (!items.length) return
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1)?.focus() }
      else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0].focus() }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => { document.removeEventListener('keydown', onKeyDown); document.body.style.overflow = oldOverflow; previous?.focus() }
  }, [open])
  if (!open) return null
  return <div className="management-drawer"><button className="management-drawer__scrim" onClick={onClose} aria-label={`Close ${title}`}/><aside ref={ref} role="dialog" aria-modal="true" aria-labelledby="management-drawer-title"><header><span>{icon}</span><div><p className="eyebrow">{eyebrow}</p><h2 id="management-drawer-title">{title}</h2><p>{copy}</p></div><button type="button" className="icon-button" onClick={onClose} aria-label={`Close ${title}`}><X/></button></header>{children}</aside></div>
}

function FormMessage({ error, success }: { error: string; success: string }) {
  if (error) return <p className="form-message form-message--error" role="alert">{error}</p>
  if (success) return <p className="form-message form-message--success" role="status"><Check/>{success}</p>
  return null
}

function dateInputValue(daysFromToday = 0) {
  const date = new Date()
  date.setDate(date.getDate() + daysFromToday)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function AddDoctorDrawer({ open, onClose }: { open: boolean; onClose(): void }) {
  const { createDoctor, departments } = useDemoData()
  const [form, setForm] = useState({ name:'', specialty:'', department:'Cardiology', email:'', phone:'', experience:'', languages:'English', doctorType:'HOSPITAL', licenseNumber:'', consultationFee:'', bio:'' })
  const [error, setError] = useState(''); const [success, setSuccess] = useState(''); const [saving, setSaving] = useState(false)
  useEffect(()=>{if(open&&departments.length&&!departments.some(item=>item.name===form.department))setForm(current=>({...current,department:departments[0].name}))},[departments,form.department,open])
  const update = (key: keyof typeof form, value: string) => setForm(current => ({ ...current, [key]:value }))
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setSuccess('')
    if (form.name.trim().length < 3 || !form.specialty || !form.department || form.licenseNumber.trim().length < 4) { setError('Add the doctor’s name, specialty, department, and license number.'); return }
    if (!/^\S+@\S+\.\S+$/.test(form.email)) { setError('Enter a valid work email address.'); return }
    setSaving(true)
    try { const doctor = await createDoctor({ ...form, doctorType:form.doctorType as 'PRIVATE'|'HOSPITAL', experience:Number(form.experience || 0), consultationFee:Number(form.consultationFee || 0), languages:form.languages.split(',').map(value=>value.trim()).filter(Boolean) }); setSuccess(`${doctor.name} was added to the clinical directory.`); setForm(current=>({...current,name:'',email:'',phone:'',licenseNumber:'',bio:''})) }
    catch (saveError) { setError(saveError instanceof Error ? saveError.message : 'The doctor could not be added.') }
    finally { setSaving(false) }
  }
  return <DrawerFrame open={open} onClose={onClose} title="Add a doctor" eyebrow="Hospital administration" copy="Create the clinical identity, verification record, practice type, and first department assignment." icon={<Stethoscope/>}><form className="management-form" onSubmit={submit}><div className="management-fields"><label className="wide"><span>Full name</span><input value={form.name} onChange={event=>update('name',event.target.value)} placeholder="e.g. Amira Rahal" autoComplete="name"/></label><label><span>Specialty</span><input value={form.specialty} onChange={event=>update('specialty',event.target.value)} placeholder="e.g. Cardiology"/></label><label><span>Doctor setting</span><select value={form.doctorType} onChange={event=>update('doctorType',event.target.value)}><option value="HOSPITAL">Hospital doctor</option><option value="PRIVATE">Independent practice</option></select></label><label><span>Department</span><select value={form.department} onChange={event=>update('department',event.target.value)}>{departments.map(item=><option key={item.id}>{item.name}</option>)}</select></label><label><span>License number</span><input value={form.licenseNumber} onChange={event=>update('licenseNumber',event.target.value)} placeholder="MED-20481"/></label><label><span>Work email</span><input type="email" value={form.email} onChange={event=>update('email',event.target.value)} placeholder="doctor@hospital.org"/></label><label><span>Phone</span><input value={form.phone} onChange={event=>update('phone',event.target.value)} placeholder="+1 312 555 0100"/></label><label><span>Years of experience</span><input min="0" max="60" type="number" value={form.experience} onChange={event=>update('experience',event.target.value)}/></label><label><span>Consultation fee (USD)</span><input min="0" step="1" type="number" value={form.consultationFee} onChange={event=>update('consultationFee',event.target.value)}/></label><label><span>Languages</span><input value={form.languages} onChange={event=>update('languages',event.target.value)} placeholder="English, French"/></label><label className="wide"><span>Professional biography</span><textarea rows={4} value={form.bio} onChange={event=>update('bio',event.target.value)} placeholder="Clinical focus, approach to care, and relevant experience"/></label></div><FormMessage error={error} success={success}/><footer><button type="button" className="secondary" onClick={onClose}>Cancel</button><button className="primary" disabled={saving}>{saving?'Adding doctor…':<><UserPlus/>Add doctor</>}</button></footer></form></DrawerFrame>
}

export function AddDepartmentDrawer({ open, onClose }: { open: boolean; onClose(): void }) {
  const { createDepartment } = useDemoData()
  const [form, setForm] = useState({ name:'', lead:'', capacity:'24', location:'' })
  const [error, setError] = useState(''); const [success, setSuccess] = useState(''); const [saving, setSaving] = useState(false)
  const update = (key: keyof typeof form, value: string) => setForm(current=>({...current,[key]:value}))
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); setSuccess(''); if(form.name.trim().length<3||!form.location.trim()){setError('Add a department name and location.');return} setSaving(true); try{const department=await createDepartment({...form,capacity:Number(form.capacity)});setSuccess(`${department.name} is ready for staffing and capacity setup.`);setForm({name:'',lead:'',capacity:'24',location:''})}catch(saveError){setError(saveError instanceof Error?saveError.message:'The department could not be created.')}finally{setSaving(false)} }
  return <DrawerFrame open={open} onClose={onClose} title="Create a department" eyebrow="Hospital structure" copy="Start the service line with its location, capacity, and accountable clinical lead." icon={<Building2/>}><form className="management-form" onSubmit={submit}><div className="management-fields"><label className="wide"><span>Department name</span><input value={form.name} onChange={event=>update('name',event.target.value)} placeholder="e.g. Oncology"/></label><label><span>Clinical lead</span><input value={form.lead} onChange={event=>update('lead',event.target.value)} placeholder="e.g. Dr. Salma Ben Ali"/></label><label><span>Planned capacity</span><input type="number" min="1" max="500" value={form.capacity} onChange={event=>update('capacity',event.target.value)}/></label><label className="wide"><span>Location</span><input value={form.location} onChange={event=>update('location',event.target.value)} placeholder="Building and floor"/></label></div><FormMessage error={error} success={success}/><footer><button type="button" className="secondary" onClick={onClose}>Cancel</button><button className="primary" disabled={saving}>{saving?'Creating department…':<><Building2/>Create department</>}</button></footer></form></DrawerFrame>
}

export function AddPatientDrawer({ open, onClose, actor = 'Reception' }: { open: boolean; onClose(): void; actor?: string }) {
  const { createPatient } = useDemoData()
  const [form,setForm]=useState({firstName:'',lastName:'',dateOfBirth:'',sex:'Female',phone:'',email:'',address:'',emergencyContactName:'',emergencyContactPhone:'',emergencyContactRelationship:'',preferredLanguage:'English',accessibilityNeeds:''})
  const [consent,setConsent]=useState(false);const [error,setError]=useState('');const [success,setSuccess]=useState('');const [saving,setSaving]=useState(false)
  const update=(key:keyof typeof form,value:string)=>setForm(current=>({...current,[key]:value}))
  const submit=async(event:FormEvent)=>{event.preventDefault();setError('');setSuccess('');if(!form.firstName||!form.lastName||!form.dateOfBirth||!form.phone||!form.address){setError('Complete identity, date of birth, phone, and address fields.');return}if(!/^\S+@\S+\.\S+$/.test(form.email)){setError('Enter a valid email address.');return}if(!consent){setError('Confirm that the patient received the privacy and consent information.');return}setSaving(true);try{const patient=await createPatient({...form,consentConfirmed:consent});setSuccess(`${patient.name} was registered as ${patient.id}.`);setForm({firstName:'',lastName:'',dateOfBirth:'',sex:'Female',phone:'',email:'',address:'',emergencyContactName:'',emergencyContactPhone:'',emergencyContactRelationship:'',preferredLanguage:'English',accessibilityNeeds:''});setConsent(false)}catch(saveError){setError(saveError instanceof Error?saveError.message:'The patient could not be registered.')}finally{setSaving(false)}}
  return <DrawerFrame open={open} onClose={onClose} title="Register a patient" eyebrow={`${actor} workflow`} copy="Create an administrative identity with contact, emergency, language, accessibility, and consent information." icon={<UserPlus/>}><form className="management-form" onSubmit={submit}><div className="management-fields"><label><span>First name</span><input value={form.firstName} onChange={event=>update('firstName',event.target.value)} autoComplete="given-name"/></label><label><span>Last name</span><input value={form.lastName} onChange={event=>update('lastName',event.target.value)} autoComplete="family-name"/></label><label><span>Date of birth</span><input type="date" max={dateInputValue()} value={form.dateOfBirth} onChange={event=>update('dateOfBirth',event.target.value)}/></label><label><span>Sex</span><select value={form.sex} onChange={event=>update('sex',event.target.value)}><option>Female</option><option>Male</option><option>Intersex</option><option>Prefer not to say</option></select></label><label><span>Mobile phone</span><input value={form.phone} onChange={event=>update('phone',event.target.value)} autoComplete="tel"/></label><label><span>Email</span><input type="email" value={form.email} onChange={event=>update('email',event.target.value)} autoComplete="email"/></label><label className="wide"><span>Home address</span><input value={form.address} onChange={event=>update('address',event.target.value)} autoComplete="street-address"/></label><label><span>Emergency contact</span><input value={form.emergencyContactName} onChange={event=>update('emergencyContactName',event.target.value)}/></label><label><span>Emergency phone</span><input value={form.emergencyContactPhone} onChange={event=>update('emergencyContactPhone',event.target.value)}/></label><label><span>Relationship</span><input value={form.emergencyContactRelationship} onChange={event=>update('emergencyContactRelationship',event.target.value)}/></label><label><span>Preferred language</span><select value={form.preferredLanguage} onChange={event=>update('preferredLanguage',event.target.value)}><option>English</option><option>Arabic</option><option>French</option><option>Spanish</option></select></label><label className="wide"><span>Accessibility or communication needs</span><textarea rows={3} value={form.accessibilityNeeds} onChange={event=>update('accessibilityNeeds',event.target.value)} placeholder="Mobility, hearing, visual, interpreter, or other support needs"/></label><label className="wide consent-field"><input type="checkbox" checked={consent} onChange={event=>setConsent(event.target.checked)}/><span>The patient received the privacy notice and registration consent information.</span></label></div><FormMessage error={error} success={success}/><footer><button type="button" className="secondary" onClick={onClose}>Cancel</button><button className="primary" disabled={saving}>{saving?'Registering patient…':<><UserPlus/>Register patient</>}</button></footer></form></DrawerFrame>
}

export function AppointmentDrawer({ open, onClose, defaultDoctorId, defaultPatientId }: { open: boolean; onClose(): void; defaultDoctorId?: number; defaultPatientId?: string }) {
  const { createAppointment, doctors, patients } = useDemoData()
  const [form,setForm]=useState({doctorId:String(defaultDoctorId||doctors[0]?.id||1),patientId:defaultPatientId||patients[0]?.id||'',date:dateInputValue(1),time:'11:30',duration:'30',mode:'VIDEO',reason:'',notes:''})
  const [error,setError]=useState('');const [success,setSuccess]=useState('');const [saving,setSaving]=useState(false)
  useEffect(()=>{if(!open)return;setForm(current=>({...current,doctorId:String(defaultDoctorId||doctors[0]?.id||1),patientId:defaultPatientId||patients[0]?.id||''}));setError('');setSuccess('')},[defaultDoctorId,defaultPatientId,doctors,open,patients])
  const update=(key:keyof typeof form,value:string)=>setForm(current=>({...current,[key]:value}))
  const submit=async(event:FormEvent)=>{event.preventDefault();setError('');setSuccess('');if(!form.patientId||!form.doctorId||!form.date||!form.time){setError('Choose the patient, doctor, date, and time.');return}if(form.reason.trim().length<8){setError('Add a short reason for the appointment.');return}setSaving(true);try{const appointment=await createAppointment({doctorId:Number(form.doctorId),patientId:form.patientId,startsAt:new Date(`${form.date}T${form.time}:00`).toISOString(),durationMinutes:Number(form.duration),mode:form.mode as 'VIDEO'|'IN_CLINIC',reason:form.reason.trim(),notes:form.notes.trim()||undefined,reminderChannels:['EMAIL','SMS']});setSuccess(`Appointment ${appointment.id} was requested.`);setForm(current=>({...current,reason:'',notes:''}))}catch(saveError){setError(saveError instanceof Error?saveError.message:'The appointment could not be created.')}finally{setSaving(false)}}
  return <DrawerFrame open={open} onClose={onClose} title="Create an appointment" eyebrow="Shared schedule" copy="The new visit will appear in the patient, doctor, and reception schedules." icon={<CalendarDays/>}><form className="management-form" onSubmit={submit}><div className="management-fields"><label><span>Patient</span><select value={form.patientId} onChange={event=>update('patientId',event.target.value)}>{patients.map(patient=><option value={patient.id} key={patient.id}>{patient.name} · {patient.id}</option>)}</select></label><label><span>Doctor</span><select value={form.doctorId} onChange={event=>update('doctorId',event.target.value)}>{doctors.map(doctor=><option value={doctor.id} key={doctor.id}>{doctor.name} · {doctor.specialty}</option>)}</select></label><label><span>Date</span><input type="date" min={dateInputValue()} value={form.date} onChange={event=>update('date',event.target.value)}/></label><label><span>Time</span><input type="time" value={form.time} onChange={event=>update('time',event.target.value)}/></label><label><span>Duration</span><select value={form.duration} onChange={event=>update('duration',event.target.value)}><option value="15">15 minutes</option><option value="30">30 minutes</option><option value="45">45 minutes</option><option value="60">60 minutes</option></select></label><label><span>Visit type</span><select value={form.mode} onChange={event=>update('mode',event.target.value)}><option value="VIDEO">Video consultation</option><option value="IN_CLINIC">In clinic</option></select></label><label className="wide"><span>Reason for visit</span><textarea rows={4} value={form.reason} onChange={event=>update('reason',event.target.value)} placeholder="Describe what the visit should address"/></label><label className="wide"><span>Administrative notes</span><textarea rows={2} value={form.notes} onChange={event=>update('notes',event.target.value)} placeholder="Optional preparation or accessibility note"/></label></div><FormMessage error={error} success={success}/><footer><button type="button" className="secondary" onClick={onClose}>Cancel</button><button className="primary" disabled={saving}>{saving?'Creating appointment…':<><CalendarDays/>Create appointment</>}</button></footer></form></DrawerFrame>
}
