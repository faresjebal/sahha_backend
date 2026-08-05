import { AlertTriangle, ArrowLeft, HeartPulse, RefreshCw } from 'lucide-react'

export function SessionLoading() {
  return <main className="session-loading" aria-live="polite" aria-busy="true"><span><HeartPulse/></span><h1>Preparing your workspace</h1><p>Restoring your secure demo session.</p><div><i/><i/><i/></div></main>
}

export function ErrorState({ title = 'This view could not load.', detail, retry }: { title?: string; detail: string; retry?: () => void }) {
  return <section className="app-error" role="alert"><AlertTriangle/><h2>{title}</h2><p>{detail}</p>{retry&&<button className="secondary soft" onClick={retry}><RefreshCw/>Try again</button>}</section>
}

export function ForbiddenState({ back }: { back: () => void }) {
  return <main className="forbidden-page"><span><AlertTriangle/></span><p className="eyebrow">Access restricted</p><h1>Your current role cannot open this workspace.</h1><p>The backend must make the final authorization decision. If you need access, contact your organization administrator.</p><button className="primary soft" onClick={back}><ArrowLeft/>Return to your workspace</button></main>
}
