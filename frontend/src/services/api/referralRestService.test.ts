import { afterEach, expect, it, vi } from 'vitest'
import { httpClient } from './httpClient'
import { referralRestService } from './referralRestService'
import { consultationRestService } from './consultationRestService'

afterEach(() => vi.restoreAllMocks())
it.each(['SECOND_OPINION', 'SHARED_TREATMENT'] as const)('uses only Gateway source-discovery and idempotent %s creation contracts', async referralType => {
  const request = vi.spyOn(httpClient, 'request').mockResolvedValue({})
  await consultationRestService.listReferralSources(2,20)
  expect(request).toHaveBeenLastCalledWith('/consultations/referral-sources?page=2&size=20')
  const command = { referralRequestId:crypto.randomUUID(), recipientUserId:'recipient', patientRegistrationId:'registration',
    referralType,
    reason:'Synthetic review', priority:'ROUTINE' as const, clinicalSummary:null, purpose:'Second opinion', consentType:'RECORDED_WRITTEN' as const,
    consentEvidenceReference:'synthetic-consent', consentRecordedAt:'2026-09-01T00:00:00Z', accessExpiresAt:'2026-09-12T00:00:00Z',
    selectedItems:referralType === 'SECOND_OPINION' ? [{ resourceType:'DIAGNOSIS' as const, resourceId:'diagnosis' }] : [], sendImmediately:true }
  await referralRestService.create(command)
  expect(request).toHaveBeenLastCalledWith('/referrals', { method:'POST', body:command })
})
it('uses the Gateway referral list and exact versioned command contracts', async () => {
  const request = vi.spyOn(httpClient,'request').mockResolvedValue({})
  await referralRestService.list('RECEIVED',2,20)
  expect(request).toHaveBeenLastCalledWith('/referrals?direction=RECEIVED&page=2&size=20')
  await referralRestService.get('opaque/id')
  expect(request).toHaveBeenLastCalledWith('/referrals/opaque%2Fid')
  await referralRestService.command('referral-id',{ action:'accept', expectedVersion:4 })
  expect(request).toHaveBeenLastCalledWith('/referrals/referral-id/accept',{ method:'POST', body:{ expectedVersion:4 } })
  await referralRestService.command('referral-id',{ action:'revoke', expectedVersion:5, reason:'Consent withdrawn' })
  expect(request).toHaveBeenLastCalledWith('/referrals/referral-id/revoke',{ method:'POST', body:{ expectedVersion:5, reason:'Consent withdrawn' } })
})
