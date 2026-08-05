import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Building2,
  Check,
  CircleCheck,
  Hospital,
  LoaderCircle,
  Pencil,
  Plus,
  Power,
  RefreshCw,
  Search,
  ShieldCheck,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import type {
  CreateDepartmentCommand,
  DepartmentPageResource,
  DepartmentResource,
  UpdateDepartmentCommand,
} from '../../models/organisation'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { departmentRestService } from '../../services/api/departmentRestService'

const departmentFormSchema = z.object({
  name:z.string().trim().min(2, 'Enter at least 2 characters.').max(120),
  code:z.string().trim()
    .regex(/^[A-Za-z][A-Za-z0-9_-]{1,31}$/, 'Use 2–32 letters, numbers, _ or -.'),
  description:z.string().trim().max(500, 'Use 500 characters or fewer.'),
})

type DepartmentFormValues = z.infer<typeof departmentFormSchema>

const defaultValues: DepartmentFormValues = {
  name:'',
  code:'',
  description:'',
}

const statusLabel = (department: DepartmentResource) =>
  department.status === 'ACTIVE' ? 'Active' : 'Inactive'

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'Select an active organisation where you are an Organisation Administrator.'
    if (error.problem.status === 404) return 'This department is not available in your active organisation.'
    if (error.problem.status === 409) return error.problem.type.includes('concurrent')
      ? 'This department changed in another request. Refresh and try again.'
      : 'A department with this name or code already exists.'
  }
  return apiErrorMessage(error, fallback)
}

function FieldError({ message }: { message?: string }) {
  return message
    ? <small className="login-field-error" role="alert">{message}</small>
    : null
}

function StatusBadge({ department }: { department: DepartmentResource }) {
  const label = statusLabel(department)
  return <span className={`status status--${label.toLowerCase()}`}><i/>{label}</span>
}

function replaceDepartment(
  current: DepartmentPageResource | undefined,
  department: DepartmentResource,
): DepartmentPageResource {
  if (!current) {
    return {
      items:[department],
      page:0,
      size:100,
      totalElements:1,
      totalPages:1,
    }
  }
  const existed = current.items.some(item => item.id === department.id)
  return {
    ...current,
    items:[department, ...current.items.filter(item => item.id !== department.id)]
      .sort((left, right) => left.name.localeCompare(right.name)),
    totalElements:existed ? current.totalElements : current.totalElements + 1,
  }
}

export function OrganisationDepartmentsPage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'no-active-organisation'
  const queryKey = ['organisation', organisationId, 'departments'] as const
  const [query, setQuery] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState<DepartmentResource | null>(null)
  const [notice, setNotice] = useState('')
  const departmentsQuery = useQuery({
    queryKey,
    queryFn:() => departmentRestService.list(),
  })
  const {
    register,
    handleSubmit,
    reset,
    formState:{ errors },
  } = useForm<DepartmentFormValues>({
    resolver:zodResolver(departmentFormSchema),
    defaultValues,
  })

  const saveDepartment = useMutation({
    mutationFn:async (values: DepartmentFormValues) => {
      const command: CreateDepartmentCommand = {
        name:values.name.trim(),
        code:values.code.trim().toUpperCase(),
        description:values.description.trim() || null,
      }
      if (!editing) return departmentRestService.create(command)
      const update: UpdateDepartmentCommand = {
        ...command,
        version:editing.version,
      }
      return departmentRestService.update(editing.id, update)
    },
    onSuccess:saved => {
      queryClient.setQueryData<DepartmentPageResource>(
        queryKey,
        current => replaceDepartment(current, saved),
      )
      setNotice(`${saved.name} was ${editing ? 'updated' : 'created'} successfully.`)
      setEditorOpen(false)
      setEditing(null)
      reset(defaultValues)
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const statusMutation = useMutation({
    mutationFn:(department: DepartmentResource) =>
      departmentRestService.changeStatus(department.id, {
        status:department.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
        version:department.version,
      }),
    onSuccess:saved => {
      queryClient.setQueryData<DepartmentPageResource>(
        queryKey,
        current => replaceDepartment(current, saved),
      )
      setNotice(`${saved.name} is now ${statusLabel(saved).toLowerCase()}.`)
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const departments = departmentsQuery.data?.items || []
  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return departments
    return departments.filter(department =>
      `${department.name} ${department.code} ${department.description || ''}`
        .toLowerCase()
        .includes(normalized),
    )
  }, [departments, query])

  const openCreate = () => {
    setEditing(null)
    setNotice('')
    saveDepartment.reset()
    reset(defaultValues)
    setEditorOpen(true)
  }

  const openEdit = (department: DepartmentResource) => {
    setEditing(department)
    setNotice('')
    saveDepartment.reset()
    reset({
      name:department.name,
      code:department.code,
      description:department.description || '',
    })
    setEditorOpen(true)
  }

  const submit = handleSubmit(async values => {
    setNotice('')
    try {
      await saveDepartment.mutateAsync(values)
    } catch {
      // The mutation state renders a safe, actionable API error.
    }
  })

  return <div className="page organization-page department-management-page">
    <div className="page-intro"><div><p className="eyebrow">Active organisation · Real service data</p><h1>Departments and service lines.</h1><p>Create the organisation structure used by future staff assignments, scheduling, and clinical access rules.</p></div><button className="primary soft" onClick={openCreate}><Plus/>Add department</button></div>

    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}

    {editorOpen&&<form className="order-composer tenant-create department-editor" onSubmit={submit} noValidate>
      <header><div><p className="eyebrow">Organisation-controlled structure</p><h2>{editing ? 'Edit department' : 'Create a department'}</h2></div><button type="button" className="icon-button" aria-label="Close department form" onClick={()=>{setEditorOpen(false);saveDepartment.reset()}}><X/></button></header>
      <p className="tenant-create__notice"><ShieldCheck/>The active organisation comes from your signed access token. It cannot be chosen or changed in this form.</p>
      <div className="order-fields">
        <label><span>Department name</span><input {...register('name')} aria-invalid={Boolean(errors.name)} placeholder="e.g. Cardiology"/><FieldError message={errors.name?.message}/></label>
        <label><span>Department code</span><input {...register('code')} aria-invalid={Boolean(errors.code)} placeholder="e.g. CARD"/><FieldError message={errors.code?.message}/></label>
        <label className="wide"><span>Description <small>optional</small></span><textarea rows={4} {...register('description')} aria-invalid={Boolean(errors.description)} placeholder="Administrative purpose and service scope"/><FieldError message={errors.description?.message}/></label>
      </div>
      {saveDepartment.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(saveDepartment.error, 'The department could not be saved.')}</p>}
      <footer><button type="button" className="secondary" onClick={()=>setEditorOpen(false)}>Cancel</button><button className="primary" disabled={saveDepartment.isPending}>{saveDepartment.isPending?<><LoaderCircle className="spin"/>Saving…</>:<><CircleCheck/>{editing ? 'Save changes' : 'Create department'}</>}</button></footer>
    </form>}

    <section className="department-summary" aria-label="Department summary">
      <article><Building2/><span><strong>{departmentsQuery.data?.totalElements ?? '—'}</strong><small>Total departments</small></span></article>
      <article><Power/><span><strong>{departmentsQuery.isPending?'—':departments.filter(item=>item.status==='ACTIVE').length}</strong><small>Active service lines</small></span></article>
    </section>

    <label className="search-field soft-field"><Search/><span className="sr-only">Search departments</span><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Search name, code, or description"/></label>

    {departmentsQuery.isPending&&<div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>Loading departments</strong><span>Reading the structure of your active organisation.</span></div>}
    {departmentsQuery.isError&&<div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Departments unavailable</strong><span>{errorMessage(departmentsQuery.error, 'The department directory could not be loaded.')}</span><button className="secondary" onClick={()=>void departmentsQuery.refetch()}><RefreshCw/>Try again</button></div>}
    {statusMutation.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(statusMutation.error, 'The department status could not be changed.')}</p>}

    {!departmentsQuery.isPending&&!departmentsQuery.isError&&<div className="department-grid">{visible.map((department,index)=><article className={department.status==='INACTIVE'?'department-card--inactive':''} key={department.id}>
      <header><span>{String(index+1).padStart(2,'0')} · {department.code}</span><StatusBadge department={department}/></header>
      <Hospital/>
      <h2>{department.name}</h2>
      <p>{department.description || 'No department description has been added.'}</p>
      <dl><div><dt>Code</dt><dd>{department.code}</dd></div><div><dt>State</dt><dd>{statusLabel(department)}</dd></div><div><dt>Version</dt><dd>v{department.version}</dd></div></dl>
      <small className="department-updated">Updated {new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(department.updatedAt))}</small>
      <div className="department-actions"><button className="secondary" onClick={()=>openEdit(department)}><Pencil/>Edit</button><button className="text-button" disabled={statusMutation.isPending} onClick={()=>statusMutation.mutate(department)}><Power/>{department.status==='ACTIVE'?'Deactivate':'Activate'}</button></div>
    </article>)}</div>}

    {!departmentsQuery.isPending&&!departmentsQuery.isError&&!visible.length&&<div className="empty-state"><Building2/><h2>{departments.length?'No department found':'No departments yet'}</h2><p>{departments.length?'Try another name or code.':'Create the first service line for this organisation.'}</p>{!departments.length&&<button className="primary soft" onClick={openCreate}><Plus/>Add department</button>}</div>}
  </div>
}
