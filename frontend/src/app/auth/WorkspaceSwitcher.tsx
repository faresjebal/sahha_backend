import { useLocation, useNavigate } from 'react-router-dom'
import type { AppRole } from '../../models/auth'
import { useAuth } from './AuthProvider'
import { roleHome, workspaceRoleForPath } from './roleRoutes'
import { sessionRoles } from './sessionAccess'

const labels: Record<AppRole, string> = {
  doctor:'Doctor', 'hospital-super-admin':'Organisation administrator',
  receptionist:'Receptionist', 'platform-admin':'Platform administrator',
  patient:'Patient', staff:'Staff', 'hospital-operations':'Hospital operations',
}

// Navigation changes the visible workspace, never the server's organisation or roles.
export function WorkspaceSwitcher({ onNavigate }: { onNavigate?: () => void }) {
  const { session } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const roles = sessionRoles(session)
  if (roles.length < 2) return null
  const current = workspaceRoleForPath(pathname)
  return <label className="workspace-switcher">
    <span>Switch workspace</span>
    <select aria-label="Switch workspace" value={current && roles.includes(current) ? current : roles[0]} onChange={event => {
      const role = event.target.value as AppRole
      if (!roles.includes(role)) return
      navigate(roleHome[role])
      onNavigate?.()
    }}>
      {roles.map(role => <option key={role} value={role}>{labels[role]}</option>)}
    </select>
  </label>
}
