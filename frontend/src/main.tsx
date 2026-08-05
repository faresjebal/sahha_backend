import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { NavigationEffects } from './app/NavigationEffects'
import { AuthProvider } from './app/auth/AuthProvider'
import { DemoDataProvider } from './app/data/DemoDataProvider'
import { WorkflowProvider } from './app/data/WorkflowProvider'
import { AppErrorBoundary } from './components/feedback/AppErrorBoundary'
import { queryClient } from './app/queryClient'
import App from './App'
import './styles.css'
import './styles/quality-pass.css'
import './styles/workflows.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode><AppErrorBoundary><QueryClientProvider client={queryClient}><BrowserRouter><NavigationEffects/><AuthProvider><DemoDataProvider><WorkflowProvider><App /></WorkflowProvider></DemoDataProvider></AuthProvider></BrowserRouter></QueryClientProvider></AppErrorBoundary></StrictMode>,
)
