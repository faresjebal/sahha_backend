import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  CalendarDays,
  Check,
  ChevronRight,
  CircleCheck,
  IdCard,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
  Search,
  ShieldCheck,
  UserPlus,
  Users,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import { ManagementDrawer } from '../../components/forms/ManagementDrawer'
import type {
  CreatePatientRegistrationCommand,
  DuplicateCandidateResource,
  DuplicateDecisionReason,
  PatientAdministrativeResource,
  PatientAdministrativeSummaryResource,
  PatientDuplicateCheckResource,
  PatientSex,
} from '../../models/patient'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { patientRegistryRestService } from '../../services/api/patientRegistryRestService'

const optionalEmail = z.string().trim().max(254).refine(
  value => !value || /^\S+@\S+\.\S+$/.test(value),
  'Enter a valid email address or leave it empty.',
)

const registrationSchema = z.object({
  firstName:z.string().trim().min(1, 'Enter the legal first name.').max(80),
  lastName:z.string().trim().min(1, 'Enter the legal last name.').max(80),
  dateOfBirth:z.string().min(1, 'Enter the date of birth.'),
  sex:z.enum(['FEMALE', 'MALE', 'INTERSEX', 'UNDISCLOSED']),
  identifierType:z.enum(['NONE', 'NATIONAL_ID', 'PASSPORT']),
  identifierValue:z.string().trim().max(60),
  identifierCountryCode:z.string().trim().regex(/^[A-Za-z]{2}$/, 'Use a two-letter country code.'),
  phoneNumber:z.string().trim().min(7, 'Enter a reachable phone number.').max(32),
  email:optionalEmail,
  address:z.string().trim().min(3, 'Enter the home address.').max(300),
  city:z.string().trim().max(100),
  region:z.string().trim().max(100),
  postalCode:z.string().trim().max(20),
  countryCode:z.string().trim().regex(/^[A-Za-z]{2}$/, 'Use a two-letter country code.'),
  emergencyContactName:z.string().trim().max(160),
  emergencyContactPhone:z.string().trim().max(32),
  emergencyContactRelationship:z.string().trim().max(80),
  preferredLanguage:z.string().trim().max(64),
  accessibilityNeeds:z.string().trim().max(500),
  privacyNoticeAcknowledged:z.boolean().refine(Boolean, 'Confirm the privacy notice was provided.'),
}).superRefine((values, context) => {
  if (values.dateOfBirth && new Date(`${values.dateOfBirth}T00:00:00`) > new Date()) {
    context.addIssue({ code:'custom', path:['dateOfBirth'], message:'Date of birth cannot be in the future.' })
  }
  if (values.identifierType !== 'NONE' && values.identifierValue.length < 4) {
    context.addIssue({ code:'custom', path:['identifierValue'], message:'Enter at least four identifier characters.' })
  }
  if (Boolean(values.emergencyContactName) !== Boolean(values.emergencyContactPhone)) {
    context.addIssue({ code:'custom', path:['emergencyContactPhone'], message:'Add both emergency contact name and phone.' })
  }
})

type RegistrationValues = z.infer<typeof registrationSchema>

const defaults: RegistrationValues = {
  firstName:'',
  lastName:'',
  dateOfBirth:'',
  sex:'UNDISCLOSED',
  identifierType:'NATIONAL_ID',
  identifierValue:'',
  identifierCountryCode:'TN',
  phoneNumber:'',
  email:'',
  address:'',
  city:'',
  region:'',
  postalCode:'',
  countryCode:'TN',
  emergencyContactName:'',
  emergencyContactPhone:'',
  emergencyContactRelationship:'',
  preferredLanguage:'Arabic',
  accessibilityNeeds:'',
  privacyNoticeAcknowledged:false,
}

const emptyToNull = (value: string) => value.trim() || null

const commandFrom = (values: RegistrationValues): CreatePatientRegistrationCommand => ({
  firstName:values.firstName.trim(),
  lastName:values.lastName.trim(),
  dateOfBirth:values.dateOfBirth,
  sex:values.sex as PatientSex,
  identifier:values.identifierType === 'NONE' ? null : {
    type:values.identifierType,
    value:values.identifierValue.trim(),
    countryCode:values.identifierCountryCode.toUpperCase(),
  },
  phoneNumber:values.phoneNumber.trim(),
  email:emptyToNull(values.email),
  address:values.address.trim(),
  city:emptyToNull(values.city),
  region:emptyToNull(values.region),
  postalCode:emptyToNull(values.postalCode),
  countryCode:values.countryCode.toUpperCase(),
  emergencyContactName:emptyToNull(values.emergencyContactName),
  emergencyContactPhone:emptyToNull(values.emergencyContactPhone),
  emergencyContactRelationship:emptyToNull(values.emergencyContactRelationship),
  preferredLanguage:emptyToNull(values.preferredLanguage),
  accessibilityNeeds:emptyToNull(values.accessibilityNeeds),
  privacyNoticeAcknowledged:values.privacyNoticeAcknowledged,
})

const fullName = (patient: PatientAdministrativeSummaryResource) =>
  `${patient.firstName} ${patient.lastName}`

const initials = (patient: PatientAdministrativeSummaryResource) =>
  `${patient.firstName[0] || ''}${patient.lastName[0] || ''}`.toUpperCase()

const age = (dateOfBirth: string) => {
  const born = new Date(`${dateOfBirth}T00:00:00`)
  const today = new Date()
  let years = today.getFullYear() - born.getFullYear()
  if (today.getMonth() < born.getMonth()
    || (today.getMonth() === born.getMonth() && today.getDate() < born.getDate())) years -= 1
  return Math.max(0, years)
}

const sexLabel = (sex: PatientSex) => ({
  FEMALE:'Female',
  MALE:'Male',
  INTERSEX:'Intersex',
  UNDISCLOSED:'Not disclosed',
})[sex]

const matchLabel = (value: string) => ({
  STRONG_IDENTIFIER:'Identity document',
  NAME_AND_DATE_OF_BIRTH:'Name and date of birth',
  PHONE_NUMBER:'Phone number',
  EMAIL:'Email address',
})[value] || value

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'Select an organisation where you are a receptionist or Organisation Administrator.'
    if (error.problem.status === 404) return 'This patient is not available in the active organisation.'
    if (error.problem.status === 409 && error.problem.type.includes('concurrent')) return 'This record changed elsewhere. Refresh it before editing.'
  }
  return apiErrorMessage(error, fallback)
}

function FieldError({ message }: { message?: string }) {
  return message ? <small className="login-field-error" role="alert">{message}</small> : null
}

function PatientAvatar({ patient }: { patient: PatientAdministrativeSummaryResource }) {
  return <span className="avatar avatar--small">{initials(patient)}</span>
}

interface RegistryProps {
  mode?: 'directory' | 'registration'
}

export function AdministrativePatientRegistryPage({ mode = 'directory' }: RegistryProps) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'no-active-organisation'
  const [searchInput, setSearchInput] = useState('')
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(mode === 'registration')
  const [notice, setNotice] = useState('')
  const [pendingCommand, setPendingCommand] = useState<CreatePatientRegistrationCommand | null>(null)
  const [duplicateReview, setDuplicateReview] = useState<PatientDuplicateCheckResource | null>(null)
  const [separateReason, setSeparateReason] = useState<DuplicateDecisionReason>('CONTACT_INFORMATION_SHARED')
  const queryKey = ['organisation', organisationId, 'administrative-patients', query] as const
  const directoryQuery = useQuery({
    queryKey,
    queryFn:() => patientRegistryRestService.list(query),
    enabled:mode === 'directory',
  })
  const detailQuery = useQuery({
    queryKey:['organisation', organisationId, 'administrative-patient', selectedId],
    queryFn:() => patientRegistryRestService.get(selectedId!),
    enabled:Boolean(selectedId),
  })
  const {
    register,
    watch,
    handleSubmit,
    reset,
    formState:{ errors },
  } = useForm<RegistrationValues>({
    resolver:zodResolver(registrationSchema),
    defaultValues:defaults,
  })
  const identifierType = watch('identifierType')

  const createMutation = useMutation({
    mutationFn:(command: CreatePatientRegistrationCommand) =>
      patientRegistryRestService.create(command),
    onSuccess:created => {
      setNotice(`${created.firstName} ${created.lastName} was registered as ${created.medicalRecordNumber}.`)
      setSelectedId(created.registrationId)
      setPendingCommand(null)
      setDuplicateReview(null)
      setFormOpen(false)
      reset(defaults)
      void queryClient.invalidateQueries({
        queryKey:['organisation', organisationId, 'administrative-patients'],
      })
    },
  })

  const duplicateMutation = useMutation({
    mutationFn:(command: CreatePatientRegistrationCommand) =>
      patientRegistryRestService.checkDuplicates({
        firstName:command.firstName,
        lastName:command.lastName,
        dateOfBirth:command.dateOfBirth,
        sex:command.sex,
        phoneNumber:command.phoneNumber,
        email:command.email,
        identifier:command.identifier,
      }),
  })

  const patients = directoryQuery.data?.items || []
  const selected = detailQuery.data
  const openRegistration = () => {
    setNotice('')
    setPendingCommand(null)
    setDuplicateReview(null)
    createMutation.reset()
    duplicateMutation.reset()
    reset(defaults)
    setFormOpen(true)
  }

  const submit = handleSubmit(async values => {
    setNotice('')
    setDuplicateReview(null)
    createMutation.reset()
    const command = commandFrom(values)
    try {
      const duplicateResult = await duplicateMutation.mutateAsync(command)
      if (duplicateResult.reviewRequired) {
        setPendingCommand(command)
        setDuplicateReview(duplicateResult)
        return
      }
      await createMutation.mutateAsync(command)
    } catch (error) {
      if (error instanceof ApiError) {
        const problem = error.problem as typeof error.problem & {
          duplicateCheck?: PatientDuplicateCheckResource
        }
        if (problem.duplicateCheck) {
          setPendingCommand(command)
          setDuplicateReview(problem.duplicateCheck)
        }
      }
    }
  })

  const linkCandidate = async (candidate: DuplicateCandidateResource) => {
    if (!pendingCommand) return
    if (candidate.registeredInActiveOrganisation && candidate.activeOrganisationRegistrationId) {
      setSelectedId(candidate.activeOrganisationRegistrationId)
      setFormOpen(false)
      setDuplicateReview(null)
      return
    }
    try {
      await createMutation.mutateAsync({
        ...pendingCommand,
        duplicateDecision:'LINK_EXISTING',
        selectedPatientId:candidate.patientId,
        duplicateDecisionReason:'SAME_PERSON_CONFIRMED',
      })
    } catch {
      // Mutation state renders the safe backend error.
    }
  }

  const createSeparate = async () => {
    if (!pendingCommand) return
    try {
      await createMutation.mutateAsync({
        ...pendingCommand,
        duplicateDecision:'CREATE_NEW',
        duplicateDecisionReason:separateReason,
      })
    } catch {
      // Mutation state renders the safe backend error.
    }
  }

  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(undefined, { dateStyle:'medium' }),
    [],
  )

  return <div className="page organization-page reception-page patient-registry-page">
    <div className="page-intro"><div><p className="eyebrow">Patient services · Real tenant data</p><h1>{mode === 'registration' ? 'Register a patient safely.' : 'Patient directory.'}</h1><p>{mode === 'registration' ? 'Check for existing identities before creating an organisation medical-record number.' : 'Search identity and contact information without receiving diagnoses, notes, medication, or other clinical data.'}</p></div><button className="primary soft" onClick={openRegistration}><UserPlus/>{mode === 'registration' ? 'Start registration' : 'Register patient'}</button></div>

    <div className="reception-notice"><LockKeyhole/><span><strong>Administrative projection only</strong><small>The active organisation comes from the signed session. Record views and duplicate checks are audited.</small></span><span className="status status--restricted"><i/>Restricted</span></div>

    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}

    {mode === 'registration'&&!formOpen&&<section className="registration-guide"><article><i>1</i><h2>Confirm identity</h2><p>Capture only the administrative identifiers required for safe matching.</p></article><article><i>2</i><h2>Review candidates</h2><p>Strong and probable matches must receive an explicit decision.</p></article><article><i>3</i><h2>Create local record</h2><p>Sahha assigns the active organisation’s medical-record number.</p></article></section>}

    <ManagementDrawer
      open={formOpen}
      onClose={()=>{setFormOpen(false);setDuplicateReview(null)}}
      title="New patient registration"
      eyebrow="Administrative identity · Synthetic data only"
      copy="Capture the minimum administrative identity, then review duplicate candidates before creating a record."
      icon={<UserPlus/>}
      wide
    ><form className="management-form patient-registration-form" onSubmit={submit} noValidate>
      <p className="tenant-create__notice"><ShieldCheck/>National ID or passport values are fingerprinted for matching and returned only in masked form.</p>
      <div className="management-fields patient-registration-fields">
        <label><span>Legal first name</span><input {...register('firstName')} aria-invalid={Boolean(errors.firstName)} autoComplete="given-name"/><FieldError message={errors.firstName?.message}/></label>
        <label><span>Legal last name</span><input {...register('lastName')} aria-invalid={Boolean(errors.lastName)} autoComplete="family-name"/><FieldError message={errors.lastName?.message}/></label>
        <label><span>Date of birth</span><input type="date" max={new Date().toISOString().slice(0,10)} {...register('dateOfBirth')} aria-invalid={Boolean(errors.dateOfBirth)}/><FieldError message={errors.dateOfBirth?.message}/></label>
        <label><span>Sex</span><select {...register('sex')}><option value="UNDISCLOSED">Not disclosed</option><option value="FEMALE">Female</option><option value="MALE">Male</option><option value="INTERSEX">Intersex</option></select></label>
        <label><span>Identity document</span><select {...register('identifierType')}><option value="NATIONAL_ID">National identity card</option><option value="PASSPORT">Passport</option><option value="NONE">No document available</option></select></label>
        {identifierType!=='NONE'&&<><label><span>Document value</span><input {...register('identifierValue')} aria-invalid={Boolean(errors.identifierValue)} autoComplete="off"/><FieldError message={errors.identifierValue?.message}/></label><label><span>Issuing country</span><input {...register('identifierCountryCode')} maxLength={2} aria-invalid={Boolean(errors.identifierCountryCode)}/><FieldError message={errors.identifierCountryCode?.message}/></label></>}
        <label><span>Mobile phone</span><input {...register('phoneNumber')} aria-invalid={Boolean(errors.phoneNumber)} autoComplete="tel"/><FieldError message={errors.phoneNumber?.message}/></label>
        <label><span>Email <small>optional</small></span><input type="email" {...register('email')} aria-invalid={Boolean(errors.email)} autoComplete="email"/><FieldError message={errors.email?.message}/></label>
        <label className="wide"><span>Home address</span><input {...register('address')} aria-invalid={Boolean(errors.address)} autoComplete="street-address"/><FieldError message={errors.address?.message}/></label>
        <label><span>City <small>optional</small></span><input {...register('city')} autoComplete="address-level2"/></label>
        <label><span>Region <small>optional</small></span><input {...register('region')} autoComplete="address-level1"/></label>
        <label><span>Postal code <small>optional</small></span><input {...register('postalCode')} autoComplete="postal-code"/></label>
        <label><span>Country</span><input {...register('countryCode')} maxLength={2} aria-invalid={Boolean(errors.countryCode)}/><FieldError message={errors.countryCode?.message}/></label>
        <label><span>Emergency contact <small>optional</small></span><input {...register('emergencyContactName')}/></label>
        <label><span>Emergency phone</span><input {...register('emergencyContactPhone')} aria-invalid={Boolean(errors.emergencyContactPhone)}/><FieldError message={errors.emergencyContactPhone?.message}/></label>
        <label><span>Relationship</span><input {...register('emergencyContactRelationship')}/></label>
        <label><span>Preferred language</span><input {...register('preferredLanguage')}/></label>
        <label className="wide"><span>Accessibility or communication needs <small>optional</small></span><textarea rows={3} {...register('accessibilityNeeds')}/></label>
        <label className="wide consent-field"><input type="checkbox" {...register('privacyNoticeAcknowledged')}/><span>The patient received the privacy notice and registration information.</span><FieldError message={errors.privacyNoticeAcknowledged?.message}/></label>
      </div>

      {duplicateReview&&<section className="duplicate-review" aria-labelledby="duplicate-review-title">
        <header><Search/><div><p className="eyebrow">Decision required</p><h3 id="duplicate-review-title">Review possible existing patients.</h3><p>Only masked, minimum-necessary fields are shown. Choose the same person or confirm a distinct identity.</p></div></header>
        <div className="duplicate-candidates">{duplicateReview.candidates.map(candidate=><article key={candidate.patientId}>
          <div><IdCard/><span><strong>{candidate.maskedDisplayName}</strong><small>Born {candidate.birthYear} · {sexLabel(candidate.sex)}{candidate.maskedIdentifier?` · ${candidate.maskedIdentifier}`:''}</small></span></div>
          <div className="duplicate-reasons">{candidate.matchReasons.map(reason=><span key={reason}>{matchLabel(reason)}</span>)}</div>
          <strong className="duplicate-score">{candidate.exactStrongIdentifierMatch?'Exact ID match':`${candidate.score}% match`}</strong>
          <button type="button" className="secondary" disabled={createMutation.isPending} onClick={()=>void linkCandidate(candidate)}>{candidate.registeredInActiveOrganisation?'Open existing registration':'Use this patient identity'}</button>
        </article>)}</div>
        {!duplicateReview.exactStrongIdentifierMatch&&<footer className="duplicate-separate"><label><span>Why is this a different patient?</span><select value={separateReason} onChange={event=>setSeparateReason(event.target.value as DuplicateDecisionReason)}><option value="CONTACT_INFORMATION_SHARED">Contact information is shared</option><option value="DEMOGRAPHIC_MATCH_CONFIRMED_DISTINCT">Demographic match confirmed distinct</option><option value="OTHER_REVIEWED">Other reviewed reason</option></select></label><button type="button" className="secondary" disabled={createMutation.isPending} onClick={()=>void createSeparate()}>Create a separate identity</button></footer>}
      </section>}

      {(duplicateMutation.isError||createMutation.isError)&&!duplicateReview&&<p className="form-message form-message--error" role="alert">{errorMessage(createMutation.error||duplicateMutation.error, 'The patient registration could not be completed.')}</p>}
      {createMutation.isError&&duplicateReview&&<p className="form-message form-message--error" role="alert">{errorMessage(createMutation.error, 'The duplicate decision could not be completed.')}</p>}
      <footer><button type="button" className="secondary" onClick={()=>setFormOpen(false)}>Cancel</button>{!duplicateReview&&<button className="primary" disabled={duplicateMutation.isPending||createMutation.isPending}>{duplicateMutation.isPending?<><LoaderCircle className="spin"/>Checking duplicates…</>:createMutation.isPending?<><LoaderCircle className="spin"/>Registering…</>:<><ShieldCheck/>Check and register</>}</button>}</footer>
    </form></ManagementDrawer>

    {mode === 'directory'&&<>
      <form className="patient-directory-search" onSubmit={event=>{event.preventDefault();setQuery(searchInput.trim())}}><label className="search-field reception-search"><Search/><span className="sr-only">Search patients</span><input value={searchInput} onChange={event=>setSearchInput(event.target.value)} placeholder="Search name, medical-record number, phone, or email"/></label><button className="secondary">Search directory</button>{query&&<button type="button" className="text-button" onClick={()=>{setSearchInput('');setQuery('')}}>Clear</button>}</form>

      {directoryQuery.isPending&&<div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>Loading patient directory</strong><span>Reading administrative registrations in the active organisation.</span></div>}
      {directoryQuery.isError&&<div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Patient directory unavailable</strong><span>{errorMessage(directoryQuery.error, 'The patient directory could not be loaded.')}</span><button className="secondary" onClick={()=>void directoryQuery.refetch()}><RefreshCw/>Try again</button></div>}
      {!directoryQuery.isPending&&!directoryQuery.isError&&<section className="reception-table patient-directory-table" aria-label="Patient directory"><header><span>Patient</span><span>Contact</span><span>Registered</span><span>Status</span><span/></header>{patients.map(patient=><button onClick={()=>setSelectedId(patient.registrationId)} key={patient.registrationId}><span className="patient-identity"><PatientAvatar patient={patient}/><span><strong>{fullName(patient)}</strong><small>{patient.medicalRecordNumber} · {age(patient.dateOfBirth)} years</small></span></span><span><strong>{patient.phoneNumber}</strong><small>{patient.email||'No email recorded'}</small></span><span>{dateFormat.format(new Date(patient.registeredAt))}</span><span className={`status status--${patient.registrationStatus.toLowerCase()}`}><i/>{patient.registrationStatus==='ACTIVE'?'Active':'Inactive'}</span><ChevronRight/></button>)}</section>}
      {!directoryQuery.isPending&&!directoryQuery.isError&&!patients.length&&<div className="empty-state"><Users/><h2>{query?'No patient found':'No patients registered'}</h2><p>{query?'Check the search value and try again.':'Create the first synthetic administrative registration for this organisation.'}</p>{!query&&<button className="primary soft" onClick={openRegistration}><UserPlus/>Register patient</button>}</div>}
    </>}

    {selectedId&&<div className="reception-drawer"><button className="nav-scrim" aria-label="Close patient profile" onClick={()=>setSelectedId(null)}/><aside role="dialog" aria-modal="true" aria-labelledby="administrative-patient-title"><header><div><p className="eyebrow">Administrative patient profile</p><h2 id="administrative-patient-title">{selected?`${selected.firstName} ${selected.lastName}`:'Loading patient…'}</h2></div><button className="icon-button" aria-label="Close patient profile" onClick={()=>setSelectedId(null)}><X/></button></header>
      {detailQuery.isPending&&<div className="tenant-state"><LoaderCircle className="spin"/><strong>Loading profile</strong></div>}
      {detailQuery.isError&&<div className="tenant-state tenant-state--error"><AlertTriangle/><strong>Profile unavailable</strong><span>{errorMessage(detailQuery.error, 'The profile could not be loaded.')}</span></div>}
      {selected&&<PatientAdministrativeDetail patient={selected}/>}</aside></div>}
  </div>
}

function PatientAdministrativeDetail({ patient }: { patient: PatientAdministrativeResource }) {
  return <>
    <div className="reception-person"><span className="avatar">{`${patient.firstName[0]}${patient.lastName[0]}`}</span><span><strong>{patient.medicalRecordNumber}</strong><small>{age(patient.dateOfBirth)} years · {sexLabel(patient.sex)} · {patient.registrationStatus.toLowerCase()}</small></span></div>
    <dl><div><dt>Phone</dt><dd>{patient.phoneNumber}</dd></div><div><dt>Email</dt><dd>{patient.email||'Not recorded'}</dd></div><div><dt>Address</dt><dd>{[patient.address,patient.city,patient.region,patient.postalCode,patient.countryCode].filter(Boolean).join(', ')}</dd></div><div><dt>Identity document</dt><dd>{patient.maskedIdentifier ? `${patient.identifierType?.replace('_',' ')} · ${patient.maskedIdentifier}` : 'Not recorded'}</dd></div><div><dt>Emergency contact</dt><dd>{patient.emergencyContactName ? `${patient.emergencyContactName} · ${patient.emergencyContactPhone}` : 'Not recorded'}</dd></div><div><dt>Preferred language</dt><dd>{patient.preferredLanguage||'Not recorded'}</dd></div><div><dt>Privacy notice</dt><dd><CircleCheck/> Acknowledged</dd></div></dl>
    <div className="restricted-panel"><LockKeyhole/><span><strong>Clinical information is not part of this response</strong><small>Diagnoses, notes, allergies, medication, results, and documents use separate authorised clinical APIs.</small></span></div>
    <button className="primary soft" disabled title="Scheduling is implemented in the next milestone"><CalendarDays/>Schedule appointment next</button>
  </>
}
