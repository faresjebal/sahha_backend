import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Activity,
  AlertTriangle,
  Building2,
  Check,
  ChevronRight,
  CircleCheck,
  Hospital,
  LoaderCircle,
  Plus,
  Search,
  ShieldCheck,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ManagementDrawer } from '../../components/forms/ManagementDrawer'
import type { CreateOrganisationCommand, OrganisationPageResource, OrganisationResource, OrganisationType } from '../../models/organisation'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { organisationRestService } from '../../services/api/organisationRestService'
import { OrganisationAdministratorsPanel } from './OrganisationAdministratorsPanel'

const organisationQueryKey = ['platform', 'organisations'] as const

const organisationFormSchema = z.object({
  name:z.string().trim().min(2, 'Enter at least 2 characters.').max(160),
  legalName:z.string().trim().max(200),
  type:z.enum(['HOSPITAL', 'CLINIC', 'PRIVATE_PRACTICE']),
  contactEmail:z.string().trim().email('Enter a valid contact email.').max(254),
  phoneNumber:z.string().trim().min(6, 'Enter a valid phone number.').max(32),
  address:z.string().trim().min(4, 'Enter the organisation address.').max(300),
  city:z.string().trim().min(2, 'Enter the city.').max(100),
  region:z.string().trim().min(2, 'Enter the region.').max(100),
  postalCode:z.string().trim().max(20),
  countryCode:z.string().trim().regex(/^[A-Za-z]{2}$/, 'Use a two-letter country code.'),
  timeZone:z.string().trim().min(1, 'Enter an IANA time zone.').max(64),
})

type OrganisationFormValues = z.infer<typeof organisationFormSchema>

const defaultValues: OrganisationFormValues = {
  name:'',
  legalName:'',
  type:'CLINIC',
  contactEmail:'',
  phoneNumber:'',
  address:'',
  city:'',
  region:'',
  postalCode:'',
  countryCode:'TN',
  timeZone:'Africa/Tunis',
}

const typeLabel: Record<OrganisationType, string> = {
  HOSPITAL:'Hospital',
  CLINIC:'Clinic',
  PRIVATE_PRACTICE:'Independent practice',
}

const statusLabel = (value: OrganisationResource['status']) =>
  value === 'ACTIVE' ? 'Active' : 'Suspended'

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'This action requires the Platform Administrator role.'
    if (error.problem.status === 409) return 'An organisation with this name already exists.'
  }
  return apiErrorMessage(error, fallback)
}

function StatusBadge({ value }: { value: string }) {
  return <span className={`status status--${value.toLowerCase().replace(/\s/g, '-')}`}><i/>{value}</span>
}

function FieldError({ message }: { message?: string }) {
  return message ? <small className="login-field-error" role="alert">{message}</small> : null
}

export function PlatformOrganizationsPage() {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState('')
  const [query, setQuery] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const organisationsQuery = useQuery({
    queryKey:organisationQueryKey,
    queryFn:() => organisationRestService.list(),
  })
  const {
    register,
    handleSubmit,
    reset,
    formState:{ errors },
  } = useForm<OrganisationFormValues>({
    resolver:zodResolver(organisationFormSchema),
    defaultValues,
  })

  const createOrganisation = useMutation({
    mutationFn:(command: CreateOrganisationCommand) => organisationRestService.create(command),
    onSuccess:created => {
      queryClient.setQueryData<OrganisationPageResource>(organisationQueryKey, current => {
        if (!current) {
          return { items:[created], page:0, size:100, totalElements:1, totalPages:1 }
        }
        const items = [created, ...current.items.filter(item => item.id !== created.id)]
          .sort((left, right) => left.name.localeCompare(right.name))
        return {
          ...current,
          items,
          totalElements:current.items.some(item => item.id === created.id)
            ? current.totalElements
            : current.totalElements + 1,
        }
      })
      setSelectedId(created.id)
      setNotice(`${created.name} is active and ready for its next configuration step.`)
      setFormOpen(false)
      reset(defaultValues)
      void queryClient.invalidateQueries({ queryKey:organisationQueryKey })
    },
  })

  const organisations = organisationsQuery.data?.items || []
  const visibleOrganisations = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return organisations
    return organisations.filter(item =>
      `${item.name} ${item.legalName || ''} ${item.city} ${item.region} ${typeLabel[item.type]}`
        .toLowerCase()
        .includes(normalized),
    )
  }, [organisations, query])
  const selectedFromDirectory = organisations.find(item => item.id === selectedId)
    || visibleOrganisations[0]
    || organisations[0]
  const selectedOrganisationId = selectedFromDirectory?.id || ''
  const organisationDetailQuery = useQuery({
    queryKey:[...organisationQueryKey, selectedOrganisationId],
    queryFn:() => organisationRestService.get(selectedOrganisationId),
    enabled:Boolean(selectedOrganisationId),
  })
  const selected = organisationDetailQuery.data || selectedFromDirectory
  const activeCount = organisations.filter(item => item.status === 'ACTIVE').length
  const hospitalCount = organisations.filter(item => item.type === 'HOSPITAL').length
  const practiceCount = organisations.filter(item => item.type !== 'HOSPITAL').length

  const submit = handleSubmit(async values => {
    setNotice('')
    const command: CreateOrganisationCommand = {
      ...values,
      legalName:values.legalName || null,
      postalCode:values.postalCode || null,
      countryCode:values.countryCode.toUpperCase(),
    }
    try {
      await createOrganisation.mutateAsync(command)
    } catch {
      // The mutation state renders the safe API error next to the form.
    }
  })

  return <div className="page platform-depth-page">
    <div className="page-intro"><div><p className="eyebrow">Tenant operations · Real service data</p><h1>Clinical organisations.</h1><p>Create and inspect healthcare tenants while the backend enforces platform authority, auditability, and tenant isolation.</p></div><button className="primary soft" onClick={()=>{createOrganisation.reset();setFormOpen(true)}}><Plus/>Add organisation</button></div>

    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}

    <ManagementDrawer
      open={formOpen}
      onClose={()=>{setFormOpen(false);createOrganisation.reset()}}
      title="Create an active organisation"
      eyebrow="Platform-controlled onboarding"
      copy="Create the tenant identity and operational contact details used across Sahha."
      icon={<Hospital/>}
      wide
    ><form className="management-form" onSubmit={submit} noValidate>
      <p className="tenant-create__notice"><ShieldCheck/>Document verification and applicant review are deferred. This internship action creates the organisation as active immediately.</p>
      <div className="management-fields">
        <label><span>Organisation name</span><input {...register('name')} aria-invalid={Boolean(errors.name)}/><FieldError message={errors.name?.message}/></label>
        <label><span>Legal name <small>optional</small></span><input {...register('legalName')} aria-invalid={Boolean(errors.legalName)}/><FieldError message={errors.legalName?.message}/></label>
        <label><span>Organisation type</span><select {...register('type')}><option value="HOSPITAL">Hospital</option><option value="CLINIC">Clinic</option><option value="PRIVATE_PRACTICE">Independent practice</option></select></label>
        <label><span>Contact email</span><input type="email" {...register('contactEmail')} aria-invalid={Boolean(errors.contactEmail)}/><FieldError message={errors.contactEmail?.message}/></label>
        <label><span>Phone number</span><input {...register('phoneNumber')} aria-invalid={Boolean(errors.phoneNumber)}/><FieldError message={errors.phoneNumber?.message}/></label>
        <label><span>Street address</span><input {...register('address')} aria-invalid={Boolean(errors.address)}/><FieldError message={errors.address?.message}/></label>
        <label><span>City</span><input {...register('city')} aria-invalid={Boolean(errors.city)}/><FieldError message={errors.city?.message}/></label>
        <label><span>Region</span><input {...register('region')} aria-invalid={Boolean(errors.region)}/><FieldError message={errors.region?.message}/></label>
        <label><span>Postal code <small>optional</small></span><input {...register('postalCode')} aria-invalid={Boolean(errors.postalCode)}/><FieldError message={errors.postalCode?.message}/></label>
        <label><span>Country code</span><input maxLength={2} {...register('countryCode')} aria-invalid={Boolean(errors.countryCode)}/><FieldError message={errors.countryCode?.message}/></label>
        <label className="wide"><span>Time zone</span><input {...register('timeZone')} aria-invalid={Boolean(errors.timeZone)} placeholder="Africa/Tunis"/><FieldError message={errors.timeZone?.message}/></label>
      </div>
      {createOrganisation.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(createOrganisation.error, 'The organisation could not be created.')}</p>}
      <footer><button type="button" className="secondary" onClick={()=>setFormOpen(false)}>Cancel</button><button className="primary" disabled={createOrganisation.isPending}>{createOrganisation.isPending?<><LoaderCircle className="spin"/>Creating…</>:<><CircleCheck/>Create organisation</>}</button></footer>
    </form></ManagementDrawer>

    <section className="tenant-metrics" aria-label="Organisation summary">
      <article><Building2/><span><strong>{organisationsQuery.data?.totalElements ?? '—'}</strong><small>Total organisations</small></span></article>
      <article><Activity/><span><strong>{organisationsQuery.isPending?'—':activeCount}</strong><small>Active workspaces</small></span></article>
      <article><Hospital/><span><strong>{organisationsQuery.isPending?'—':hospitalCount}</strong><small>Hospitals</small></span></article>
      <article><ShieldCheck/><span><strong>{organisationsQuery.isPending?'—':practiceCount}</strong><small>Clinics and practices</small></span></article>
    </section>

    <div className="tenant-layout">
      <section className="tenant-directory" aria-label="Healthcare organisations">
        <div className="tenant-tools"><label className="search-field"><Search/><span className="sr-only">Search organisations</span><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Search name, type, city, or region"/></label></div>
        {organisationsQuery.isPending&&<div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>Loading organisations</strong><span>Reading the Platform Administrator directory.</span></div>}
        {organisationsQuery.isError&&<div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Organisations unavailable</strong><span>{errorMessage(organisationsQuery.error, 'The organisation directory could not be loaded.')}</span><button className="secondary" onClick={()=>void organisationsQuery.refetch()}>Try again</button></div>}
        {!organisationsQuery.isPending&&!organisationsQuery.isError&&visibleOrganisations.map(item=><button className={selected?.id===item.id?'active':''} onClick={()=>setSelectedId(item.id)} key={item.id}><span className="tenant-mark"><Building2/></span><span><strong>{item.name}</strong><small>{typeLabel[item.type]} · {item.city}, {item.region}</small></span><StatusBadge value={statusLabel(item.status)}/><ChevronRight/></button>)}
        {!organisationsQuery.isPending&&!organisationsQuery.isError&&!visibleOrganisations.length&&<div className="tenant-state"><Building2/><strong>{organisations.length?'No matching organisations':'No organisations yet'}</strong><span>{organisations.length?'Try another search.':'Create the first healthcare tenant from this page.'}</span></div>}
      </section>

      {selected?<aside className="tenant-profile" aria-busy={organisationDetailQuery.isPending}><header><span className="tenant-mark large"><Building2/></span><StatusBadge value={statusLabel(selected.status)}/></header>{organisationDetailQuery.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(organisationDetailQuery.error, 'The latest organisation details could not be loaded.')}</p>}<p className="eyebrow">{selected.id} · {selected.countryCode}</p><h2>{selected.name}</h2><p>{selected.legalName || typeLabel[selected.type]}. This profile is supplied by Organisation Service and remains isolated from every other tenant.</p><dl><div><dt>Type</dt><dd>{typeLabel[selected.type]}</dd></div><div><dt>Workspace state</dt><dd><StatusBadge value={statusLabel(selected.status)}/></dd></div><div><dt>Contact</dt><dd>{selected.contactEmail}<br/>{selected.phoneNumber}</dd></div><div><dt>Address</dt><dd>{selected.address}<br/>{selected.postalCode&&`${selected.postalCode} `}{selected.city}, {selected.region}</dd></div><div><dt>Time zone</dt><dd>{selected.timeZone}</dd></div><div><dt>Created</dt><dd>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(selected.createdAt))}</dd></div></dl><OrganisationAdministratorsPanel organisationId={selected.id}/><div className="tenant-scope"><ShieldCheck/><span><strong>Next configuration boundary</strong><small>Active-organisation selection and department management come next.</small></span></div></aside>:<aside className="tenant-profile tenant-profile--empty"><Building2/><h2>Select an organisation</h2><p>Organisation details will appear here after the directory loads.</p></aside>}
    </div>
  </div>
}
