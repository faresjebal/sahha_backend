import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, IdCard, LoaderCircle, RefreshCw, ShieldCheck, Stethoscope } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../app/auth/AuthProvider'
import type { DoctorProfileResource } from '../../models/organisation'
import { ApiError, apiErrorMessage } from '../../services/api/ApiError'
import { staffDirectoryRestService } from '../../services/api/staffDirectoryRestService'

const profileSchema = z.object({
  specialty:z.string().trim().min(2, 'Enter your specialty.').max(120),
  professionalTitle:z.string().trim().min(2, 'Enter your professional title.').max(120),
  licenceNumber:z.string().trim().min(2, 'Enter your licence or registration number.').max(80),
  registrationAuthority:z.string().trim().min(2, 'Enter the issuing authority.').max(160),
  biography:z.string().trim().max(1000, 'Use 1,000 characters or fewer.'),
})

type ProfileValues = z.infer<typeof profileSchema>

const emptyValues: ProfileValues = {
  specialty:'',
  professionalTitle:'',
  licenceNumber:'',
  registrationAuthority:'',
  biography:'',
}

const loadProfile = async () => {
  try {
    return await staffDirectoryRestService.getMyDoctorProfile()
  } catch (error) {
    if (error instanceof ApiError && error.problem.status === 404) return null
    throw error
  }
}

const errorMessage = (error: unknown) => {
  if (error instanceof ApiError) {
    if (error.problem.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.problem.status === 403) return 'Select an active organisation where you have a doctor membership.'
    if (error.problem.status === 409) return error.problem.type.includes('concurrent')
      ? 'Your profile changed in another request. Reload it before saving.'
      : 'This licence number conflicts with another doctor profile in the organisation.'
  }
  return apiErrorMessage(error, 'The professional profile could not be saved.')
}

export function DoctorProfessionalProfilePage() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const organisationId = auth.session?.user.organizationId || 'no-active-organisation'
  const queryKey = ['organisation', organisationId, 'my-doctor-profile'] as const
  const [notice, setNotice] = useState('')
  const profileQuery = useQuery({ queryKey, queryFn:loadProfile })
  const { register, handleSubmit, reset, formState:{ errors } } = useForm<ProfileValues>({
    resolver:zodResolver(profileSchema),
    defaultValues:emptyValues,
  })

  useEffect(() => {
    if (profileQuery.data === undefined) return
    reset(profileQuery.data ? {
      specialty:profileQuery.data.specialty,
      professionalTitle:profileQuery.data.professionalTitle,
      licenceNumber:profileQuery.data.licenceNumber,
      registrationAuthority:profileQuery.data.registrationAuthority,
      biography:profileQuery.data.biography || '',
    } : emptyValues)
  }, [profileQuery.data, reset])

  const saveProfile = useMutation({
    mutationFn:(values: ProfileValues) => staffDirectoryRestService.upsertMyDoctorProfile({
      specialty:values.specialty.trim(),
      professionalTitle:values.professionalTitle.trim(),
      licenceNumber:values.licenceNumber.trim(),
      registrationAuthority:values.registrationAuthority.trim(),
      biography:values.biography.trim() || null,
      version:profileQuery.data?.version ?? null,
    }),
    onSuccess:profile => {
      queryClient.setQueryData<DoctorProfileResource>(queryKey, profile)
      setNotice('Your professional profile was saved for this organisation.')
      void queryClient.invalidateQueries({ queryKey:['organisation', organisationId, 'staff'] })
    },
  })

  const submit = handleSubmit(async values => {
    setNotice('')
    try {
      await saveProfile.mutateAsync(values)
    } catch {
      // The mutation state renders the safe API problem.
    }
  })

  if (profileQuery.isPending) return <div className="page doctor-work-page"><div className="tenant-state" role="status"><LoaderCircle className="spin"/><strong>Loading professional profile</strong></div></div>

  if (profileQuery.isError) return <div className="page doctor-work-page"><div className="tenant-state tenant-state--error" role="alert"><AlertTriangle/><strong>Profile unavailable</strong><span>{errorMessage(profileQuery.error)}</span><button className="secondary" onClick={()=>void profileQuery.refetch()}><RefreshCw/>Try again</button></div></div>

  return <div className="page doctor-work-page doctor-professional-profile-page">
    <div className="page-intro"><div><p className="eyebrow">Active organisation · Professional identity</p><h1>Your doctor profile.</h1><p>Maintain the professional information your organisation administrator sees. External licence verification remains outside the internship scope.</p></div></div>
    {notice&&<div className="inline-success" role="status"><Check/>{notice}</div>}
    <section className="doctor-profile-boundary"><ShieldCheck/><span><strong>Organisation-specific profile</strong><small>Updating this profile does not change your global Auth identity or profiles in another organisation.</small></span></section>
    <form className="doctor-professional-form" onSubmit={submit} noValidate>
      <header><Stethoscope/><div><p className="eyebrow">Professional details</p><h2>{profileQuery.data?'Update profile':'Complete your profile'}</h2></div></header>
      <div className="order-fields">
        <label><span>Specialty</span><input {...register('specialty')} placeholder="e.g. Cardiology"/>{errors.specialty&&<small className="login-field-error">{errors.specialty.message}</small>}</label>
        <label><span>Professional title</span><input {...register('professionalTitle')} placeholder="e.g. Consultant cardiologist"/>{errors.professionalTitle&&<small className="login-field-error">{errors.professionalTitle.message}</small>}</label>
        <label><span>Licence / registration number</span><input {...register('licenceNumber')} autoComplete="off"/>{errors.licenceNumber&&<small className="login-field-error">{errors.licenceNumber.message}</small>}</label>
        <label><span>Registration authority</span><input {...register('registrationAuthority')} placeholder="Issuing medical council"/>{errors.registrationAuthority&&<small className="login-field-error">{errors.registrationAuthority.message}</small>}</label>
        <label className="wide"><span>Professional biography <small>optional</small></span><textarea rows={6} {...register('biography')} placeholder="Clinical focus, experience, and approach to care"/>{errors.biography&&<small className="login-field-error">{errors.biography.message}</small>}</label>
      </div>
      {saveProfile.isError&&<p className="form-message form-message--error" role="alert">{errorMessage(saveProfile.error)}</p>}
      <footer><span><IdCard/>Version {profileQuery.data?.version ?? 'new'}</span><button className="primary" disabled={saveProfile.isPending}>{saveProfile.isPending?<><LoaderCircle className="spin"/>Saving…</>:<><Check/>Save professional profile</>}</button></footer>
    </form>
  </div>
}
