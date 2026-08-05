const REFRESH_LOCK_NAME = 'sahha-auth-refresh-v1'

export const withBrowserRefreshLock = async <T>(
  operation: () => Promise<T>,
): Promise<T> => {
  if (!navigator.locks?.request) return operation()
  return navigator.locks.request(
    REFRESH_LOCK_NAME,
    { mode:'exclusive' },
    operation,
  )
}

export { REFRESH_LOCK_NAME }
