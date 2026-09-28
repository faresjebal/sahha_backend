import { describe, expect, it } from 'vitest'
import { isRealtimeNotificationMessage } from './notification'

const referral = {
  id:'notification', notificationType:'REFERRAL_RECEIVED', resourceType:'REFERRAL',
  resourceId:'referral', resourceVersion:0, eventOccurredAt:'2026-09-24T00:00:00Z',
  createdAt:'2026-09-24T00:00:01Z', read:false, readAt:null,
  appointmentStatus:null, appointmentStartsAt:null, appointmentEndsAt:null,
  appointmentTimeZone:null, appointmentLocationLabel:null,
}
const valid = (changes = {}) => isRealtimeNotificationMessage({
  messageType:'NOTIFICATION_CREATED', notification:{ ...referral, ...changes },
})
describe('Referral notification transport contract', () => {
  it.each(['RECEIVED', 'ACCEPTED', 'REJECTED', 'REVOKED', 'COMPLETED', 'EXPIRED'])('accepts %s routing-only alerts', type => {
    expect(valid({ notificationType:'REFERRAL_' + type })).toBe(true)
  })
  it.each([
    { notificationType:'MESSAGE_RECEIVED' }, { notificationType:'REFERRAL_DRAFTED' },
    { resourceType:'CONVERSATION' }, { resourceType:'PATIENT' },
    { appointmentStatus:'REQUESTED' }, { appointmentStartsAt:'2026-09-24T00:00:00Z' },
    { appointmentLocationLabel:'Unexpected clinical content' },
    { resourceType:'APPOINTMENT', appointmentStatus:'REQUESTED',
      appointmentStartsAt:'2026-09-24T00:00:00Z', appointmentEndsAt:'2026-09-24T00:01:00Z',
      appointmentTimeZone:'UTC', appointmentLocationLabel:'Synthetic location' },
  ])('rejects incompatible resource/type or appointment content %j', changes => {
    expect(valid(changes)).toBe(false)
  })
})
