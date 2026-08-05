export const ACTIVE_ORGANISATION_CHANGED_KEY =
  'sahha-active-organisation-changed-v1'

export const broadcastActiveOrganisationChange = () => {
  try {
    localStorage.setItem(
      ACTIVE_ORGANISATION_CHANGED_KEY,
      `${Date.now()}:${Math.random()}`,
    )
  } catch {
    // Browser storage can be disabled. The current tab still receives the
    // selected session and backend cookie enforcement remains authoritative.
  }
}
