import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../app/auth/AuthProvider'
import type { AuthSession } from '../../models/auth'
import { LoginPage, RegisterPage, VerifyEmailPage } from './LoginPage'

const authService = vi.hoisted(() => ({
  restoreSession:vi.fn(),
  refreshSession:vi.fn(),
  login:vi.fn(),
  register:vi.fn(),
  confirmEmail:vi.fn(),
  logout:vi.fn(),
}))

vi.mock('../../services', () => ({ services:{ auth:authService } }))
vi.mock('../../config/env', () => ({
  env:{
    apiBaseUrl:'http://localhost:8079/api/v1',
    appName:'Sahha',
    useMocks:true,
    useAuthMocks:false,
    requestTimeoutMs:12_000,
  },
}))
vi.mock('../../app/data/WorkflowProvider', () => ({
  useWorkflow:() => ({ resetDemo:vi.fn() }),
}))

const patientSession: AuthSession = {
  user:{
    id:'10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98',
    displayName:'Amal Mansour',
    email:'amal@example.com',
    initials:'AM',
    role:'patient',
    permissions:['patient:read:self', 'appointment:manage:self'],
  },
  expiresAt:new Date(Date.now() + 10 * 60_000).toISOString(),
}

describe('real frontend authentication pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authService.restoreSession.mockResolvedValue(null)
    authService.refreshSession.mockResolvedValue(patientSession)
    authService.login.mockResolvedValue(patientSession)
    authService.register.mockResolvedValue(undefined)
    authService.confirmEmail.mockResolvedValue(undefined)
    authService.logout.mockResolvedValue(undefined)
  })

  it('submits validated patient registration details and shows verification guidance', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider><RegisterPage/></AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('First name'), { target:{ value:'Amal' } })
    fireEvent.change(screen.getByLabelText('Last name'), { target:{ value:'Mansour' } })
    fireEvent.change(screen.getByLabelText('Email'), { target:{ value:'amal@example.com' } })
    fireEvent.change(screen.getByLabelText(/Phone/), { target:{ value:'+21620123456' } })
    fireEvent.change(screen.getByLabelText('Password'), { target:{ value:'Synthetic passphrase 2026!' } })
    fireEvent.change(screen.getByLabelText('Confirm password'), { target:{ value:'Synthetic passphrase 2026!' } })
    fireEvent.click(screen.getByRole('button', { name:/Create patient account/i }))

    await waitFor(() => expect(authService.register).toHaveBeenCalledWith({
      firstName:'Amal',
      lastName:'Mansour',
      email:'amal@example.com',
      phoneNumber:'+21620123456',
      password:'Synthetic passphrase 2026!',
    }))
    expect(await screen.findByText('Verify your email')).toBeInTheDocument()
    expect(screen.getByText('amal@example.com')).toBeInTheDocument()
  })

  it('signs in without a client role selector and requests organisation context', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage/>}/>
            <Route path="/organisations/select" element={<p>Organisation selection</p>}/>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(screen.queryByText('Demo identity')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Email'), { target:{ value:'amal@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target:{ value:'Synthetic passphrase 2026!' } })
    fireEvent.click(screen.getByRole('button', { name:/Enter workspace/i }))

    await waitFor(() => expect(authService.login).toHaveBeenCalledWith({
      email:'amal@example.com',
      password:'Synthetic passphrase 2026!',
    }))
    expect(await screen.findByText('Organisation selection')).toBeInTheDocument()
  })

  it('consumes the token from the email verification route', async () => {
    render(
      <MemoryRouter initialEntries={['/verify-email?token=single-use-token']}>
        <AuthProvider><VerifyEmailPage/></AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(authService.confirmEmail).toHaveBeenCalledWith('single-use-token'))
    expect(await screen.findByText('Your account is ready')).toBeInTheDocument()
    expect(screen.getByRole('button', { name:/Continue to sign in/i })).toBeInTheDocument()
  })

  it.each([true, false])('restores a Doctor return route only with an explicit Doctor assignment (%s)', async isDoctor => {
    authService.login.mockResolvedValue({ ...patientSession,
      user:{ ...patientSession.user, role:'hospital-super-admin', organizationId:'org-a' },
      platformRoles:[], organisationRoles:isDoctor ? ['ORGANIZATION_ADMIN','DOCTOR'] : ['ORGANIZATION_ADMIN'],
    })
    render(<MemoryRouter initialEntries={[{ pathname:'/login', state:{ from:'/doctor/clinical/synthetic-record' } }]}><AuthProvider><Routes>
      <Route path="/login" element={<LoginPage/>}/>
      <Route path="/doctor/clinical/synthetic-record" element={<p>Returned to clinical record</p>}/>
      <Route path="/hospital/admin/overview" element={<p>Returned to administration</p>}/>
    </Routes></AuthProvider></MemoryRouter>)
    fireEvent.change(screen.getByLabelText('Email'), { target:{ value:'amal@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target:{ value:'Synthetic passphrase 2026!' } })
    fireEvent.click(screen.getByRole('button', { name:/Enter workspace/i }))
    await screen.findByText(isDoctor ? 'Returned to clinical record' : 'Returned to administration')
  })
})
