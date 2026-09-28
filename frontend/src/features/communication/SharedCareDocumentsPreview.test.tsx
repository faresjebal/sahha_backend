import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { SharedCareDocumentsPreview } from './SharedCareDocumentsPreview'
import { sharedCareFileRestService } from '../../services/api/sharedCareFileRestService'
import { careReferral } from '../../test/fixtures/sharedCare'
import { careFile, careFiles } from '../../test/fixtures/sharedCareFile'
import type { SharedCareFilePage } from '../../models/sharedCareFile'

const props = { referral:careReferral, consultationId:'consultation-1', now:Date.now() }
const open = () => fireEvent.click(screen.getByRole('button', { name:'Open encounter documents' }))
const download = () => fireEvent.click(screen.getByRole('button', { name:'Download document synthetic-care.pdf' }))
beforeEach(() => {
  vi.spyOn(sharedCareFileRestService, 'list').mockResolvedValue(careFiles)
  vi.spyOn(sharedCareFileRestService, 'download').mockResolvedValue(new Blob(['synthetic']))
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:synthetic-care')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })
it('loads documents only after explicit opening and downloads through a live guard without caching', async () => {
  render(<SharedCareDocumentsPreview {...props}/>)
  expect(sharedCareFileRestService.list).not.toHaveBeenCalled(); open()
  await screen.findByRole('button', { name:'Download document synthetic-care.pdf' }); download()
  expect(await screen.findByText('The authorised document download has started.')).toBeInTheDocument()
  expect(sharedCareFileRestService.download).toHaveBeenCalledWith(careReferral, careFile, expect.any(Function))
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1)
  await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic-care'))
  expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0)
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})
it('does not release a late download after closing the documents', async () => {
  let resolveBlob!:(blob:Blob) => void
  vi.mocked(sharedCareFileRestService.download).mockImplementationOnce(() => new Promise(resolve => { resolveBlob = resolve }))
  render(<SharedCareDocumentsPreview {...props}/>); open()
  await screen.findByRole('button', { name:'Download document synthetic-care.pdf' }); download()
  fireEvent.click(screen.getByRole('button', { name:'Close encounter documents' }))
  await act(async () => resolveBlob(new Blob(['synthetic'])))
  expect(URL.createObjectURL).not.toHaveBeenCalled()
})
it('ignores a late document list after switching patient context', async () => {
  let resolveList!:(value:SharedCareFilePage) => void
  vi.mocked(sharedCareFileRestService.list).mockImplementationOnce(() => new Promise(resolve => { resolveList = resolve }))
    .mockResolvedValue({ ...careFiles, content:[] })
  const view = render(<SharedCareDocumentsPreview {...props}/>); open()
  view.rerender(<SharedCareDocumentsPreview {...props} referral={{ ...careReferral, patientRegistrationId:'other-patient' }}/>)
  await act(async () => resolveList(careFiles))
  expect(screen.queryByRole('button', { name:'Download document synthetic-care.pdf' })).not.toBeInTheDocument()
})
it.each(['REVOKED', 'COMPLETED', 'EXPIRED'] as const)('clears documents and blocks an in-flight download when referral becomes %s', async status => {
  let resolveBlob!:(blob:Blob) => void
  vi.mocked(sharedCareFileRestService.download).mockImplementationOnce(() => new Promise(resolve => { resolveBlob = resolve }))
  const view = render(<SharedCareDocumentsPreview {...props}/>); open()
  await screen.findByRole('button', { name:'Download document synthetic-care.pdf' }); download()
  view.rerender(<SharedCareDocumentsPreview {...props} referral={{ ...careReferral, status }}/>)
  await act(async () => resolveBlob(new Blob(['synthetic'])))
  expect(screen.queryByRole('button', { name:'Download document synthetic-care.pdf' })).not.toBeInTheDocument()
  expect(URL.createObjectURL).not.toHaveBeenCalled()
})
it('clears filenames on a denied poll and discards an in-flight download until explicit retry', async () => {
  let poll!:() => void
  const original = window.setTimeout.bind(window)
  vi.spyOn(window, 'setTimeout').mockImplementation(((callback:TimerHandler, delay?:number, ...args:unknown[]) => {
    if (delay === 5_000 && typeof callback === 'function') { poll = callback as () => void; return 0 }
    return original(callback, delay, ...args)
  }) as typeof window.setTimeout)
  let resolveBlob!:(blob:Blob) => void
  vi.mocked(sharedCareFileRestService.download).mockImplementationOnce(() => new Promise(resolve => { resolveBlob = resolve }))
  render(<SharedCareDocumentsPreview {...props}/>); open()
  await screen.findByRole('button', { name:'Download document synthetic-care.pdf' }); download()
  vi.mocked(sharedCareFileRestService.list).mockRejectedValueOnce(new Error('Revoked'))
  await act(async () => poll())
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be verified')
  expect(screen.queryByRole('button', { name:'Download document synthetic-care.pdf' })).not.toBeInTheDocument()
  await act(async () => resolveBlob(new Blob(['synthetic'])))
  expect(URL.createObjectURL).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name:'Retry encounter documents' }))
  expect(await screen.findByRole('button', { name:'Download document synthetic-care.pdf' })).toBeEnabled()
})
it('clears metadata on a failed download and re-enables a successful retry', async () => {
  vi.mocked(sharedCareFileRestService.download).mockRejectedValueOnce(new Error('Dependency unavailable'))
  render(<SharedCareDocumentsPreview {...props}/>); open()
  await screen.findByRole('button', { name:'Download document synthetic-care.pdf' }); download()
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be verified')
  fireEvent.click(screen.getByRole('button', { name:'Retry encounter documents' }))
  expect(await screen.findByRole('button', { name:'Download document synthetic-care.pdf' })).toBeEnabled()
  download(); expect(await screen.findByText('The authorised document download has started.')).toBeInTheDocument()
})
it('honours an earlier server expiry without waiting for a poll', async () => {
  vi.mocked(sharedCareFileRestService.list).mockResolvedValue({ ...careFiles, validUntil:new Date(props.now + 1_000).toISOString() })
  const view = render(<SharedCareDocumentsPreview {...props}/>); open()
  await screen.findByRole('button', { name:'Download document synthetic-care.pdf' })
  view.rerender(<SharedCareDocumentsPreview {...props} now={props.now + 1_000}/>)
  expect(screen.getByRole('alert')).toHaveTextContent('expired')
  expect(screen.queryByRole('button', { name:'Download document synthetic-care.pdf' })).not.toBeInTheDocument()
})
it('pages documents and shows a real empty state', async () => {
  vi.mocked(sharedCareFileRestService.list).mockResolvedValueOnce({ ...careFiles, totalElements:21, totalPages:2 })
    .mockResolvedValueOnce({ ...careFiles, content:[], page:1, totalElements:21, totalPages:2 })
  render(<SharedCareDocumentsPreview {...props}/>); open()
  fireEvent.click(await screen.findByRole('button', { name:'Next page' }))
  expect(await screen.findByText('No clean documents are available for this encounter.')).toBeInTheDocument()
  expect(sharedCareFileRestService.list).toHaveBeenLastCalledWith(careReferral, 'consultation-1', 1)
})
