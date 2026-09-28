import { zodResolver } from '@hookform/resolvers/zod'
import {
  ArrowLeft, ArrowUpRight, BedDouble, Building2, CircleCheck, FlaskConical,
  HeartPulse, Hospital, KeyRound, LoaderCircle, Mail, Pill, RotateCcw,
  ShieldCheck, Stethoscope, UserRound,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import { sessionDestination } from '../../app/auth/roleRoutes'
import { env } from '../../config/env'
import type { AppRole, DoctorType, StaffRole } from '../../models/auth'
import { apiErrorMessage } from '../../services/api/ApiError'

const roleOptions: Array<{ value: AppRole; label: string; copy: string; icon: typeof UserRound }> = [
  { value:'patient', label:'Patient', copy:'Appointments, health history, results, and care team.', icon:UserRound },
  { value:'doctor', label:'Independent doctor', copy:'Patients, clinical work, schedule, and practice.', icon:Stethoscope },
  { value:'staff', label:'Hospital staff', copy:'Schedule, assigned work, handover, and a role-specific queue.', icon:Hospital },
  { value:'hospital-operations', label:'Hospital operations', copy:'Departments, flow, beds, staffing, and handover.', icon:Hospital },
  { value:'hospital-super-admin', label:'Hospital super admin', copy:'Finance, statistics, access, and audit.', icon:Building2 },
  { value:'receptionist', label:'Receptionist', copy:'Restricted registration and patient-service tools.', icon:KeyRound },
  { value:'platform-admin', label:'Platform administrator', copy:'Organisations, verification, trust, and platform health.', icon:HeartPulse },
]

const staffOptions: Array<{value:StaffRole;label:string;icon:typeof Hospital}> = [
  {value:'NURSE',label:'Nurse',icon:HeartPulse},
  {value:'LABORATORY',label:'Laboratory',icon:FlaskConical},
  {value:'PHARMACY',label:'Pharmacy',icon:Pill},
  {value:'BED_COORDINATOR',label:'Bed coordinator',icon:BedDouble},
]

const loginSchema = z.object({
  email:z.string().trim().min(1, 'Email is required.').email('Enter a valid email address.').max(320),
  password:z.string().min(1, 'Password is required.').max(128),
})

const registrationSchema = z.object({
  firstName:z.string().trim().min(1, 'First name is required.').max(100),
  lastName:z.string().trim().min(1, 'Last name is required.').max(100),
  email:z.string().trim().min(1, 'Email is required.').email('Enter a valid email address.').max(320),
  phoneNumber:z.string().trim().refine(
    value => !value || /^\+[1-9][0-9]{7,14}$/.test(value),
    'Use international format, for example +21620123456.',
  ),
  password:z.string().min(12, 'Use at least 12 characters.').max(128),
  confirmPassword:z.string().min(1, 'Confirm your password.'),
}).refine(value => value.password === value.confirmPassword, {
  path:['confirmPassword'],
  message:'The passwords do not match.',
})

type LoginValues = z.infer<typeof loginSchema>
type RegistrationValues = z.infer<typeof registrationSchema>

function AuthenticationLayout({
  children,
  icon:Icon,
  contextTitle,
  contextCopy,
}: {
  children:React.ReactNode
  icon:typeof UserRound
  contextTitle:string
  contextCopy:string
}) {
  const navigate = useNavigate()
  return <main className="login-page">
    <button className="login-back" onClick={()=>navigate('/')}><ArrowLeft/>Back to Sahha</button>
    <section className="login-intro">
      <span className="aegis-wordmark"><i><HeartPulse/></i>SAHHA</span>
      <div>
        <p>Secure Sahha identity</p>
        <h1>Care starts with a trusted account.</h1>
        <span>Registration, verification, sign-in, session renewal, and logout now run through Sahha API Gateway. Authentication cookies remain HttpOnly and are never stored by this interface.</span>
      </div>
      <aside><Icon/><strong>{contextTitle}</strong><p>{contextCopy}</p></aside>
    </section>
    {children}
  </main>
}

function FieldError({ message }: { message?:string }) {
  return message ? <small className="login-field-error" role="alert">{message}</small> : null
}

function AuthenticationTabs({ active }: { active:'login' | 'register' }) {
  const navigate = useNavigate()
  return <div className="login-tabs" aria-label="Account access">
    <button type="button" aria-current={active === 'login' ? 'page' : undefined} className={active === 'login' ? 'active' : ''} onClick={()=>navigate('/login')}>Sign in</button>
    <button type="button" aria-current={active === 'register' ? 'page' : undefined} className={active === 'register' ? 'active' : ''} onClick={()=>navigate('/register')}>Create account</button>
  </div>
}

export function LoginPage() {
  const [params] = useSearchParams()
  const requested = params.get('role') as AppRole | null
  const [role, setRole] = useState<AppRole>(roleOptions.some(option => option.value === requested) ? requested! : 'patient')
  const [doctorType,setDoctorType]=useState<DoctorType>('PRIVATE')
  const [staffRole,setStaffRole]=useState<StaffRole>('NURSE')
  const [serverError, setServerError] = useState('')
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const selected = useMemo(
    () => env.useAuthMocks
      ? roleOptions.find(option => option.value === role)!
      : roleOptions[0],
    [role],
  )
  const {
    register,
    handleSubmit,
    formState:{ errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver:zodResolver(loginSchema),
    defaultValues:{
      email:env.useAuthMocks
        ? 'demo@sahha.health'
        : ((location.state as { registeredEmail?:string } | null)?.registeredEmail || ''),
      password:env.useAuthMocks ? 'sahha-demo' : '',
    },
  })

  const submit = handleSubmit(async values => {
    setServerError('')
    try {
      const session = await auth.login({
        ...values,
        ...(env.useAuthMocks ? {
          demoRole:role,
          demoDoctorType:doctorType,
          demoStaffRole:staffRole,
        } : {}),
      })
      const from = (location.state as { from?: string } | null)?.from
      const requiresOrganisationSelection = !env.useAuthMocks
        && session.user.role === 'patient'
        && !session.user.organizationId
      const safeDestination = requiresOrganisationSelection
        ? '/organisations/select'
        : sessionDestination(session, from)
      navigate(safeDestination, { replace:true })
    } catch (loginError) {
      setServerError(apiErrorMessage(loginError, 'Sign in failed. Check your details and try again.'))
    }
  })

  return <AuthenticationLayout icon={selected.icon} contextTitle={selected.label} contextCopy={selected.copy}>
    <section className="login-panel">
      {!env.useAuthMocks&&<AuthenticationTabs active="login"/>}
      <header>
        <p className="eyebrow">{env.useAuthMocks ? 'Role-aware demonstration' : 'Secure access'}</p>
        <h2>Sign in to continue</h2>
        <p>{env.useAuthMocks ? 'Choose a demo identity, then use the prepared credentials.' : 'Use the verified email and password for your Sahha account.'}</p>
      </header>
      <form onSubmit={submit} noValidate>
        {env.useAuthMocks&&<fieldset>
          <legend>Demo identity</legend>
          <div className="login-roles">{roleOptions.map(option=><button type="button" className={role===option.value?'active':''} onClick={()=>setRole(option.value)} key={option.value}><option.icon/><span><strong>{option.label}</strong><small>{option.copy}</small></span><i/></button>)}</div>
        </fieldset>}
        {env.useAuthMocks&&role==='doctor'&&<fieldset className="login-subroles">
          <legend>Doctor setting</legend>
          <div>{(['PRIVATE','HOSPITAL'] as DoctorType[]).map(value=><button type="button" aria-pressed={doctorType===value} className={doctorType===value?'active':''} onClick={()=>setDoctorType(value)} key={value}>{value==='PRIVATE'?'Independent practice':'Hospital doctor'}</button>)}</div>
        </fieldset>}
        {env.useAuthMocks&&role==='staff'&&<fieldset className="login-subroles">
          <legend>Staff profile</legend>
          <div>{staffOptions.map(option=><button type="button" aria-pressed={staffRole===option.value} className={staffRole===option.value?'active':''} onClick={()=>setStaffRole(option.value)} key={option.value}><option.icon/>{option.label}</button>)}</div>
        </fieldset>}
        <label>
          <span>Email</span>
          <input autoComplete="username" type="email" aria-invalid={Boolean(errors.email)} {...register('email')}/>
          <FieldError message={errors.email?.message}/>
        </label>
        <label>
          <span>Password</span>
          <input autoComplete="current-password" type="password" aria-invalid={Boolean(errors.password)} {...register('password')}/>
          <FieldError message={errors.password?.message}/>
        </label>
        {serverError&&<p className="login-error" role="alert">{serverError}</p>}
        <button className="primary login-submit" disabled={isSubmitting}>
          {isSubmitting?<><LoaderCircle className="spin"/>Signing in securely</>:<>Enter workspace <ArrowUpRight/></>}
        </button>
      </form>
      <footer>
        <KeyRound/>
        <span>{env.useAuthMocks ? 'Demo authentication is active. No backend credentials are used.' : 'Access and refresh tokens stay inside scoped HttpOnly cookies. The interface never receives their values.'}</span>
      </footer>
    </section>
  </AuthenticationLayout>
}

export function RegisterPage() {
  const auth = useAuth()
  const [registeredEmail, setRegisteredEmail] = useState('')
  const [serverError, setServerError] = useState('')
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    formState:{ errors, isSubmitting },
  } = useForm<RegistrationValues>({
    resolver:zodResolver(registrationSchema),
    defaultValues:{ firstName:'', lastName:'', email:'', phoneNumber:'', password:'', confirmPassword:'' },
  })

  const submit = handleSubmit(async values => {
    setServerError('')
    try {
      await auth.register({
        firstName:values.firstName,
        lastName:values.lastName,
        email:values.email,
        phoneNumber:values.phoneNumber || undefined,
        password:values.password,
      })
      setRegisteredEmail(values.email.trim().toLowerCase())
    } catch (registrationError) {
      setServerError(apiErrorMessage(registrationError, 'Registration failed. Please review your details and try again.'))
    }
  })

  return <AuthenticationLayout icon={UserRound} contextTitle="Patient registration" contextCopy="Create a patient identity now. Clinical and organisation roles are assigned later by authorised staff.">
    <section className="login-panel register-panel">
      <AuthenticationTabs active="register"/>
      {registeredEmail ? <div className="registration-success">
        <span><Mail/></span>
        <p className="eyebrow">Registration accepted</p>
        <h2>Verify your email</h2>
        <p>We sent a secure verification link to <strong>{registeredEmail}</strong>. Open it before signing in.</p>
        <button className="primary login-submit" onClick={()=>navigate('/login', { state:{ registeredEmail } })}>Return to sign in <ArrowUpRight/></button>
      </div> : <>
        <header>
          <p className="eyebrow">Patient account</p>
          <h2>Create your account</h2>
          <p>Use synthetic information while Sahha remains an internship environment.</p>
        </header>
        <form onSubmit={submit} noValidate>
          <div className="register-grid">
            <label>
              <span>First name</span>
              <input autoComplete="given-name" aria-invalid={Boolean(errors.firstName)} {...register('firstName')}/>
              <FieldError message={errors.firstName?.message}/>
            </label>
            <label>
              <span>Last name</span>
              <input autoComplete="family-name" aria-invalid={Boolean(errors.lastName)} {...register('lastName')}/>
              <FieldError message={errors.lastName?.message}/>
            </label>
            <label className="wide">
              <span>Email</span>
              <input type="email" autoComplete="email" aria-invalid={Boolean(errors.email)} {...register('email')}/>
              <FieldError message={errors.email?.message}/>
            </label>
            <label className="wide">
              <span>Phone <small>optional, international format</small></span>
              <input type="tel" autoComplete="tel" placeholder="+21620123456" aria-invalid={Boolean(errors.phoneNumber)} {...register('phoneNumber')}/>
              <FieldError message={errors.phoneNumber?.message}/>
            </label>
            <label>
              <span>Password</span>
              <input type="password" autoComplete="new-password" aria-invalid={Boolean(errors.password)} {...register('password')}/>
              <FieldError message={errors.password?.message}/>
            </label>
            <label>
              <span>Confirm password</span>
              <input type="password" autoComplete="new-password" aria-invalid={Boolean(errors.confirmPassword)} {...register('confirmPassword')}/>
              <FieldError message={errors.confirmPassword?.message}/>
            </label>
          </div>
          {serverError&&<p className="login-error" role="alert">{serverError}</p>}
          <button className="primary login-submit" disabled={isSubmitting}>
            {isSubmitting?<><LoaderCircle className="spin"/>Creating account</>:<>Create patient account <ArrowUpRight/></>}
          </button>
        </form>
      </>}
      <footer><ShieldCheck/><span>Registration creates a pending patient account. The email verification token is single-use and expires automatically.</span></footer>
    </section>
  </AuthenticationLayout>
}

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')?.trim() || ''
  const auth = useAuth()
  const navigate = useNavigate()
  const started = useRef(false)
  const [state, setState] = useState<'checking' | 'success' | 'error'>(token ? 'checking' : 'error')
  const [message, setMessage] = useState(token ? '' : 'The verification link does not contain a token.')

  useEffect(() => {
    if (!token || started.current) return
    started.current = true
    navigate('/verify-email', { replace:true })
    auth.confirmEmail(token)
      .then(() => setState('success'))
      .catch(error => {
        setMessage(apiErrorMessage(error, 'This verification link is invalid or has expired.'))
        setState('error')
      })
  }, [auth, token])

  return <AuthenticationLayout icon={Mail} contextTitle="Email verification" contextCopy="A verified email is required before Sahha creates a browser session.">
    <section className="login-panel verification-panel">
      <div className={`verification-state verification-state--${state}`}>
        <span>{state === 'checking' ? <LoaderCircle className="spin"/> : state === 'success' ? <CircleCheck/> : <KeyRound/>}</span>
        <p className="eyebrow">{state === 'checking' ? 'Checking secure link' : state === 'success' ? 'Email verified' : 'Verification unavailable'}</p>
        <h2>{state === 'checking' ? 'Verifying your account…' : state === 'success' ? 'Your account is ready' : 'We could not verify this link'}</h2>
        <p>{state === 'checking' ? 'Keep this page open for a moment.' : state === 'success' ? 'You can now sign in with the password used during registration.' : message}</p>
        {state !== 'checking'&&<button className="primary login-submit" onClick={()=>navigate(state === 'success' ? '/login' : '/register')}>
          {state === 'success' ? <>Continue to sign in <ArrowUpRight/></> : <>Create or verify an account <ArrowUpRight/></>}
        </button>}
      </div>
    </section>
  </AuthenticationLayout>
}
