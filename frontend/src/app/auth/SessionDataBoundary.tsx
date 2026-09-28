import { QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { createQueryClient } from '../queryClient'

/** Remounted at identity/permission boundaries. Late callbacks retain only the
 * discarded client; they cannot populate the next person's cache. */
export function SessionDataBoundary({ children }: { children: ReactNode }) {
  const [client] = useState(createQueryClient)
  useEffect(() => () => {
    void client.cancelQueries()
    client.clear()
  }, [client])
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
