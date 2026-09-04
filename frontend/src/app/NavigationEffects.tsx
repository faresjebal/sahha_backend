import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const labelForPath = (pathname: string) => {
  const segments = pathname.split('/').filter(Boolean)
  if (!segments.length) return 'Medical care platform'
  return segments
    .map(segment => segment.replaceAll('-', ' '))
    .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' · ')
}

export function NavigationEffects() {
  const location = useLocation()
  const label = labelForPath(location.pathname)

  useEffect(() => {
    document.title = `${label} | Sahha`
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [label])

  return <div className="sr-only" aria-live="polite" aria-atomic="true">{label} loaded</div>
}
