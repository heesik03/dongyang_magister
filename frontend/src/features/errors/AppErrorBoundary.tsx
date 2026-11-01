import { Component, type ReactNode } from 'react'
import { ErrorPage } from './ErrorPage'
export class AppErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  render() {
    return this.state.failed ? <ErrorPage kind="server" /> : this.props.children
  }
}
