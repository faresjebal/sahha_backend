import { Navigate, useLocation } from 'react-router-dom'
import type { AppRole, Permission } from '../../models/auth'
import { useAuth } from './AuthProvider'
import { SessionLoading } from '../../components/feedback/AsyncState'

export function ProtectedRoute({ children, roles, permission }: { children: React.ReactNode; roles?: AppRole[]; permission?: Permission }) {
  const auth = useAuth()
  const location = useLocation()
  if (auth.status === 'loading') return <SessionLoading />
  if (!auth.session) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if ((roles && !auth.hasRole(roles)) || (permission && !auth.hasPermission(permission))) return <Navigate to="/forbidden" replace />
  return children
}
