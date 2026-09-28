import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../services/api/ApiError'

export const createQueryClient = () => new QueryClient({
  defaultOptions:{
    queries:{
      staleTime:30_000,
      retry:(failureCount, error) => {
        if (error instanceof ApiError && error.problem.status < 500) return false
        return failureCount < 1
      },
      refetchOnWindowFocus:false,
    },
    mutations:{ retry:false },
  },
})

export const queryClient = createQueryClient()
