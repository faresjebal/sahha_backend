import { Component, type ErrorInfo, type ReactNode } from 'react'
import { HeartPulse, RefreshCw } from 'lucide-react'

type Props = { children: ReactNode }
type State = { failed: boolean; reference: string }

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { failed: false, reference: '' }

  static getDerivedStateFromError(): State {
    return { failed: true, reference: `UI-${Date.now().toString(36).toUpperCase()}` }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Replace this with your production observability client. Never send PHI.
    console.error('Aegis interface error', { error, componentStack: info.componentStack })
  }

  private retry = () => this.setState({ failed: false, reference: '' })

  render() {
    if (!this.state.failed) return this.props.children

    return <main className="app-error" role="alert">
      <span className="app-error__mark"><HeartPulse /></span>
      <p className="eyebrow">Interface recovery</p>
      <h1>This workspace hit an unexpected error.</h1>
      <p>No action was submitted from this screen. Try restoring the interface; if the problem returns, share the reference with your support team.</p>
      <small>Reference {this.state.reference}</small>
      <button className="primary soft" onClick={this.retry}><RefreshCw />Restore workspace</button>
    </main>
  }
}
