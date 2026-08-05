import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, LoaderCircle, Mail, Plus, ShieldCheck, UserRoundCheck, X } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import type { OrganisationMembershipPageResource } from '../../models/organisation'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { organisationRestService } from '../../services/api/organisationRestService'

const formSchema = z.object({
  email:z.string().trim().email('Enter the exact email of a registered Sahha user.').max(254),
})

type FormValues = z.infer<typeof formSchema>

const membershipError = (error: unknown) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'Only a Platform Administrator can assign this role.'
    if (error.problem.status === 404) return 'No Sahha account was found for that exact email.'
    if (error.problem.status === 409) return 'The account is unverified, inactive, or already belongs to this organization.'
    if (error.problem.status === 503) return 'Auth Service could not verify the account. Try again shortly.'
  }
  return apiErrorMessage(error, 'The administrator membership could not be processed.')
}

export function OrganisationAdministratorsPanel({ organisationId }: { organisationId: string }) {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [selectedId, setSelectedId] = useState('')
  const [notice, setNotice] = useState('')
  const queryKey = ['platform', 'organisations', organisationId, 'administrators'] as const
  const administratorsQuery = useQuery({
    queryKey,
    queryFn:() => organisationRestService.listAdministrators(organisationId),
  })
  const administrators = administratorsQuery.data?.items || []
  const selectedFromList = administrators.find(item => item.id === selectedId) || administrators[0]
  const detailQuery = useQuery({
    queryKey:[...queryKey, selectedFromList?.id || ''],
    queryFn:() => organisationRestService.getAdministrator(organisationId, selectedFromList!.id),
    enabled:Boolean(selectedFromList),
  })
  const selected = detailQuery.data || selectedFromList
  const {
    register,
    handleSubmit,
    reset,
    formState:{ errors },
  } = useForm<FormValues>({ resolver:zodResolver(formSchema), defaultValues:{ email:'' } })

  const assignment = useMutation({
    mutationFn:(values: FormValues) => organisationRestService.assignAdministrator(
      organisationId,
      { email:values.email.trim().toLowerCase() },
    ),
    onSuccess:created => {
      queryClient.setQueryData<OrganisationMembershipPageResource>(queryKey, current => {
        if (!current) return { items:[created], page:0, size:20, totalElements:1, totalPages:1 }
        return {
          ...current,
          items:[created, ...current.items.filter(item => item.id !== created.id)]
            .sort((left, right) => left.displayName.localeCompare(right.displayName)),
          totalElements:current.items.some(item => item.id === created.id)
            ? current.totalElements
            : current.totalElements + 1,
        }
      })
      setSelectedId(created.id)
      setNotice(`${created.displayName} is now an Organisation Administrator.`)
      setFormOpen(false)
      reset()
      void queryClient.invalidateQueries({ queryKey })
    },
  })

  const submit = handleSubmit(async values => {
    setNotice('')
    try {
      await assignment.mutateAsync(values)
    } catch {
      // Mutation state renders the safe backend error.
    }
  })

  return <section className="tenant-admins" aria-label="Organisation administrators">
    <header><div><p className="eyebrow">Scoped authority</p><h3>Organisation administrators</h3></div><button className="secondary" onClick={()=>{assignment.reset();setFormOpen(true)}}><Plus/>Assign</button></header>
    <p className="tenant-admins__boundary"><ShieldCheck/>This role applies only inside this organization and does not grant clinical access.</p>
    {notice&&<p className="tenant-admins__success" role="status"><UserRoundCheck/>{notice}</p>}

    {formOpen&&<form className="tenant-admin-form" onSubmit={submit} noValidate>
      <header><strong>Assign an existing Sahha user</strong><button type="button" className="icon-button" aria-label="Close administrator form" onClick={()=>setFormOpen(false)}><X/></button></header>
      <label><span>Account email</span><div><Mail/><input type="email" {...register('email')} aria-invalid={Boolean(errors.email)} placeholder="admin@example.com"/></div>{errors.email&&<small className="login-field-error" role="alert">{errors.email.message}</small>}</label>
      {assignment.isError&&<p className="form-message form-message--error" role="alert">{membershipError(assignment.error)}</p>}
      <button className="primary" disabled={assignment.isPending}>{assignment.isPending?<><LoaderCircle className="spin"/>Verifying account…</>:<><UserRoundCheck/>Assign administrator</>}</button>
    </form>}

    {administratorsQuery.isPending&&<div className="tenant-admins__state" role="status"><LoaderCircle className="spin"/>Loading administrators</div>}
    {administratorsQuery.isError&&<div className="tenant-admins__state tenant-admins__state--error" role="alert"><AlertTriangle/>{membershipError(administratorsQuery.error)}</div>}
    {!administratorsQuery.isPending&&!administratorsQuery.isError&&!administrators.length&&<div className="tenant-admins__state"><UserRoundCheck/>No administrator assigned yet.</div>}
    {administrators.length>0&&<div className="tenant-admins__layout">
      <div className="tenant-admins__list">{administrators.map(item=><button className={selected?.id===item.id?'active':''} key={item.id} onClick={()=>setSelectedId(item.id)}><span>{item.displayName.split(/\s+/).map(part=>part[0]).join('').slice(0,2)}</span><span><strong>{item.displayName}</strong><small>{item.email}</small></span></button>)}</div>
      {selected&&<dl className="tenant-admins__detail" aria-busy={detailQuery.isPending}><div><dt>Membership</dt><dd>{selected.status}</dd></div><div><dt>Role</dt><dd>Organisation Administrator</dd></div><div><dt>Joined</dt><dd>{new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(selected.joinedAt))}</dd></div></dl>}
    </div>}
  </section>
}
