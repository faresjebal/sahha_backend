import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { SharedCareHistoryPage, SharedCareRecord } from '../../models/sharedCare'
import { careHistory, careRecord, careReferral } from '../../test/fixtures/sharedCare'
import { sharedCareRestService } from '../../services/api/sharedCareRestService'
import { SharedCareHistoryPreview } from './SharedCareHistoryPreview'

const props = { referral:careReferral, userId:'recipient', organisationId:'org-1', now:Date.now() }
const open = () => fireEvent.click(screen.getByRole('button', { name:'Open shared-care history' }))
const encounter = () => screen.getByRole('button', { name:'Open encounter consultation-1' })
beforeEach(() => {
  vi.spyOn(sharedCareRestService, 'list').mockResolvedValue(careHistory)
  vi.spyOn(sharedCareRestService, 'record').mockResolvedValue(careRecord)
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers() })

it.each(['sender', 'recipient'])('loads only on request for %s and renders immutable corrections without edit controls', async userId => {
  render(<SharedCareHistoryPreview {...props} userId={userId}/>)
  expect(sharedCareRestService.list).not.toHaveBeenCalled()
  open()
  fireEvent.click(await screen.findByRole('button', { name:'Open encounter consultation-1' }))
  expect(await screen.findByText('Synthetic shared-care encounter')).toBeInTheDocument()
  expect(screen.getByText('Synthetic correction reason')).toBeInTheDocument()
  expect(screen.getByText('correction-author')).toBeInTheDocument()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name:/save|finalise|append correction|download/i })).not.toBeInTheDocument()
  expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0)
  fireEvent.click(encounter()) // Same selection must re-read, not leave an endless loading state.
  expect(await screen.findByText('Synthetic shared-care encounter')).toBeInTheDocument()
  expect(sharedCareRestService.record).toHaveBeenCalledTimes(2)
})
it.each([
  { userId:'unrelated' }, { organisationId:'other-org' },
  { referral:{ ...careReferral, referralType:'SECOND_OPINION' as const } },
  { referral:{ ...careReferral, status:'SENT' as const } }, { referral:{ ...careReferral, status:'REVOKED' as const } },
  { referral:{ ...careReferral, sharingGrantId:null } }, { now:Date.parse(careReferral.accessExpiresAt) },
])('never queries for an ineligible context', change => {
  render(<SharedCareHistoryPreview {...props} {...change}/>)
  expect(screen.queryByRole('button', { name:'Open shared-care history' })).not.toBeInTheDocument()
  expect(sharedCareRestService.list).not.toHaveBeenCalled(); expect(sharedCareRestService.record).not.toHaveBeenCalled()
})
it('discards late history after closing and late record after a context switch', async () => {
  let resolveHistory!:(value:SharedCareHistoryPage) => void
  vi.mocked(sharedCareRestService.list).mockImplementationOnce(() => new Promise(resolve => { resolveHistory = resolve }))
  const view = render(<SharedCareHistoryPreview {...props}/>); open()
  fireEvent.click(screen.getByRole('button', { name:'Close shared-care history' }))
  await act(async () => resolveHistory(careHistory))
  expect(screen.queryByRole('button', { name:'Open encounter consultation-1' })).not.toBeInTheDocument()
  let resolveRecord!:(value:SharedCareRecord) => void
  vi.mocked(sharedCareRestService.record).mockImplementationOnce(() => new Promise(resolve => { resolveRecord = resolve }))
  open(); fireEvent.click(await screen.findByRole('button', { name:'Open encounter consultation-1' }))
  await waitFor(() => expect(sharedCareRestService.record).toHaveBeenCalled())
  view.rerender(<SharedCareHistoryPreview {...props} organisationId="other-org"/>)
  await act(async () => resolveRecord(careRecord))
  expect(screen.queryByText('Synthetic shared-care encounter')).not.toBeInTheDocument()
})
it('removes history on a denied recheck and only recovers after an explicit retry', async () => {
  render(<SharedCareHistoryPreview {...props}/>); open()
  fireEvent.click(await screen.findByRole('button', { name:'Open encounter consultation-1' }))
  expect(await screen.findByText('Synthetic shared-care encounter')).toBeInTheDocument()
  // Keep the scheduled timer test real and bounded: mock scheduling before forcing a re-read.
  let recheck:(() => void) | undefined
  const originalTimeout = window.setTimeout.bind(window)
  vi.spyOn(window, 'setTimeout').mockImplementation(((callback:TimerHandler, delay?:number, ...args:unknown[]) => {
    if (delay === 5_000 && typeof callback === 'function') { recheck = callback as () => void; return 0 }
    return originalTimeout(callback, delay, ...args)
  }) as typeof window.setTimeout)
  fireEvent.click(encounter()); await screen.findByText('Synthetic shared-care encounter')
  vi.mocked(sharedCareRestService.list).mockRejectedValueOnce(new Error('Grant revoked'))
  await act(async () => { recheck!() })
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be verified')
  expect(screen.queryByText('Synthetic shared-care encounter')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name:'Open encounter consultation-1' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name:'Retry shared-care history' }))
  expect(await screen.findByText('Synthetic shared-care encounter')).toBeInTheDocument()
})
it.each(['REVOKED', 'COMPLETED', 'EXPIRED'] as const)('clears visible records when authoritative referral becomes %s', async status => {
  const view = render(<SharedCareHistoryPreview {...props}/>); open()
  fireEvent.click(await screen.findByRole('button', { name:'Open encounter consultation-1' }))
  await screen.findByText('Synthetic shared-care encounter')
  view.rerender(<SharedCareHistoryPreview {...props} referral={{ ...careReferral, status }}/>)
  expect(screen.queryByText('Synthetic shared-care encounter')).not.toBeInTheDocument()
})
it('honours the earlier server expiry without waiting for the next poll', async () => {
  const validUntil = new Date(props.now + 10_000).toISOString()
  vi.mocked(sharedCareRestService.list).mockResolvedValue({ ...careHistory, validUntil })
  const view = render(<SharedCareHistoryPreview {...props}/>); open()
  await screen.findByRole('button', { name:'Open encounter consultation-1' })
  view.rerender(<SharedCareHistoryPreview {...props} now={props.now + 10_000}/>)
  expect(screen.queryByRole('button', { name:'Open encounter consultation-1' })).not.toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent('expired')
})
it('pages through authorised metadata and shows an empty finalised history without mock data', async () => {
  vi.mocked(sharedCareRestService.list).mockResolvedValueOnce({ ...careHistory, totalElements:21, totalPages:2 })
    .mockResolvedValueOnce({ ...careHistory, page:1, content:[], totalElements:21, totalPages:2 })
  render(<SharedCareHistoryPreview {...props}/>); open()
  fireEvent.click(await screen.findByRole('button', { name:'Next page' }))
  expect(await screen.findByText('No finalised encounters are available in this organisation.')).toBeInTheDocument()
  expect(sharedCareRestService.list).toHaveBeenLastCalledWith(careReferral, 1)
})
