import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { NavigationEffects } from './app/NavigationEffects'
import { AuthProvider } from './app/auth/AuthProvider'
import { AppErrorBoundary } from './components/feedback/AppErrorBoundary'
import App from './App'
import './styles.css'
import './styles/quality-pass.css'
import './styles/workflows.css'
import './styles/clinical.css'
import './styles/communication.css'
import './styles/live-workspace.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode><AppErrorBoundary><BrowserRouter><NavigationEffects/><AuthProvider><App /></AuthProvider></BrowserRouter></AppErrorBoundary></StrictMode>,
)
