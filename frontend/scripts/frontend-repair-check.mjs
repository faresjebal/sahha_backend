// Deterministic browser contract/layout regression; not a live-backend acceptance run.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { chromium } from 'playwright-core'

const base = process.env.SAHHA_FRONTEND_URL || 'http://127.0.0.1:5173'
const output = resolve(tmpdir(), 'sahha-frontend-repair-check')
await mkdir(output, { recursive:true })
const browser = await chromium.launch({ headless:true, executablePath:process.env.SAHHA_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' })
const orgId = '10000000-0000-4000-8000-000000000001'
const userId = '20000000-0000-4000-8000-000000000001'
const conversationSource = '30000000-0000-4000-8000-000000000001'
const conversationPatient = '30000000-0000-4000-8000-000000000002'
const conversationRecipient = '30000000-0000-4000-8000-000000000003'
const report = []
async function verifyDialogViewport(page) {
  // A React rerender may cancel an animation. Wait for settlement, then measure
  // the current dialog rather than treating a cancelled animation as a UI error.
  await page.getByRole('dialog').evaluate(async element => { await Promise.allSettled(element.getAnimations({ subtree:true }).map(animation => animation.finished)) })
  const bounds = await page.locator('.workflow-drawer').evaluate(element => { const rect=element.getBoundingClientRect(); return { top:rect.top,left:rect.left,width:rect.width,height:rect.height,viewportWidth:innerWidth,viewportHeight:innerHeight } })
  assert.equal(bounds.top,0,'Dialog must not be positioned relative to an animated page')
  assert.equal(bounds.left,0)
  assert.equal(bounds.width,bounds.viewportWidth)
  assert.equal(bounds.height,bounds.viewportHeight)
  assert.equal(await page.getByRole('dialog').evaluate(element => element.scrollWidth > element.clientWidth+1),false,'Dialog content must fit its viewport')
}
const scenarios = [
  { role:'DOCTOR', path:'/doctor/overview', text:'Welcome, Synthetic.' },
  { role:'DOCTOR', path:'/doctor/patients', text:'Your patient workspace.' },
  { role:'DOCTOR', path:'/doctor/doctors', text:'Find a colleague.' },
  { role:'DOCTOR', path:'/doctor/messages', text:'Private clinical conversations.', messaging:true },
  { role:'DOCTOR', path:'/doctor/results', text:'Not available in this version' },
  { role:'DOCTOR', path:'/doctor/referrals', text:'Referrals & selected sharing.', referral:true },
  { role:'DOCTOR', path:'/doctor/referrals?referral=referral-1', text:'Referrals & selected sharing.', sharedCare:true },
  { role:'DOCTOR', path:'/doctor/orders', text:'Referrals & selected sharing.' },
  { role:'DOCTOR', path:'/doctor/referrals?compose=new', text:'Referrals & selected sharing.', creation:'send' },
  { role:'DOCTOR', path:'/doctor/referrals?compose=new', text:'Referrals & selected sharing.', creation:'draft' },
  { role:'DOCTOR', path:'/doctor/referrals?compose=new', text:'Referrals & selected sharing.', creation:'send', createSharedCare:true },
  { role:'DOCTOR', path:'/doctor/referrals?compose=new', text:'Referrals & selected sharing.', creation:'draft', createSharedCare:true },
  { role:'PATIENT', path:'/patient/overview', text:'Your upcoming care.' },
  { role:'PATIENT', path:'/patient/profile', text:'Your profile.', edit:'account' },
  { role:'ORGANIZATION_ADMIN', path:'/hospital/admin/overview', text:'Your organisation at a glance.' },
  { role:'ORGANIZATION_ADMIN', path:'/hospital/admin/settings', text:'Organisation settings.', edit:'organisation' },
  { role:'RECEPTIONIST', path:'/reception/checkin', text:'Check-in queue.' },
  { role:'PLATFORM_ADMIN', path:'/platform/overview', text:'Your platform workspace.' },
  { role:'ORGANIZATION_ADMIN', roles:['ORGANIZATION_ADMIN','DOCTOR','RECEPTIONIST'], path:'/hospital/admin/overview', text:'Your organisation at a glance.', multiRole:true },
  { role:'ORGANIZATION_ADMIN', path:'/doctor/clinical', text:'Your current role cannot open this workspace.', clinicalDenied:true },
]
try {
  for (const scenario of scenarios.filter(item => !process.env.SAHHA_CHECK_PATH || item.path === process.env.SAHHA_CHECK_PATH)) for (const width of [1440, 375]) {
    const context = await browser.newContext({ viewport:{ width, height:960 } })
    // This is a contract/layout suite. Never let test sockets contact a live API.
    await context.routeWebSocket('**/api/**', socket => socket.close({ code:1008, reason:'Synthetic browser contract test' }))
    const page = await context.newPage()
    const unexpected = [], errors = [], requests = []
    const organisationRoles = scenario.roles || [scenario.role]
    const appointments = [userId, 'other-doctor'].map((doctorUserId, index) => ({
      id:'synthetic-appointment-' + index, organisationId:orgId, doctorUserId, doctorMembershipId:'membership-' + index,
      patientRegistrationId:index ? 'other-registration' : 'own-registration', patientId:'synthetic-patient-' + index,
      startsAt:new Date().toISOString(), endsAt:new Date(Date.now()+1800000).toISOString(), timeZone:'Africa/Tunis',
      locationLabel:index ? 'Other doctor room' : 'Own doctor room', status:'CHECKED_IN', statusReason:null,
      bookingRequestId:'synthetic-booking-' + index, bookedByUserId:userId, bookedByActorType:'STAFF', bookedByMembershipId:'member',
      bookedAt:new Date().toISOString(), createdAt:new Date().toISOString(), updatedAt:new Date().toISOString(), version:0,
    }))
    let identity = { id:userId, email:'synthetic@example.test', firstName:'Synthetic', lastName:'Browser', phoneNumber:null, status:'ACTIVE', emailVerified:true, version:0 }
    let organisation = { id:orgId, name:'Synthetic Clinic', legalName:null, type:'CLINIC', status:'ACTIVE', contactEmail:'clinic@example.test', phoneNumber:'+21670000000', address:'12 Synthetic Street', city:'Tunis', region:'Tunis', postalCode:'1000', countryCode:'TN', timeZone:'Africa/Tunis', createdBy:userId, createdAt:'2026-08-01T00:00:00Z', updatedAt:'2026-08-01T00:00:00Z', version:0 }
    let referral = { id:'referral-1', organisationId:orgId, patientRegistrationId:'registration-1', senderUserId:'sender-1', senderDisplayName:'Synthetic Sender', recipientUserId:userId, recipientDisplayName:'Synthetic Browser', reason:'Synthetic second opinion', priority:'ROUTINE', clinicalSummary:'Selected diagnosis only.', purpose:'Second opinion', consentType:'RECORDED_WRITTEN', consentEvidenceReference:'synthetic-consent', consentRecordedAt:'2026-09-01T09:00:00Z', accessExpiresAt:'2099-09-09T09:00:00Z', status:'SENT', sharingGrantId:null, createdAt:'2026-09-01T09:00:00Z', version:2, selectedItems:[{ id:'item-1', resourceType:'DIAGNOSIS', resourceId:'diagnosis-1' }] }
    if (scenario.referral) referral.selectedItems.push({ id:'item-2', resourceType:'MEDICAL_DOCUMENT', resourceId:'file-1' })
    referral.referralType = scenario.sharedCare ? 'SHARED_TREATMENT' : 'SECOND_OPINION'
    if (scenario.sharedCare) referral = { ...referral, status:'ACTIVE', sharingGrantId:'care-grant', version:3, selectedItems:[], reason:'Synthetic shared treatment' }
    let denySelected = false
    let denyCareFiles = false
    let conversation = null
    page.on('pageerror', error => errors.push(error.message))
    await context.route('**/api/**', async route => {
      const request = route.request(), url = new URL(request.url())
      if (!url.pathname.startsWith('/api/')) { await route.continue(); return }
      if (request.method() === 'OPTIONS') { await route.fulfill({ status:204, headers:{ 'access-control-allow-origin':base, 'access-control-allow-credentials':'true', 'access-control-allow-headers':'Content-Type,X-XSRF-TOKEN', 'access-control-allow-methods':'GET,POST,PUT,DELETE,OPTIONS' } }); return }
      const path = url.pathname.replace(/^\/api(?:\/v1)?/, '')
      requests.push(request.method() + ' ' + path)
      let data
      if (path === '/auth/session') data = { userId, sessionId:'session', accessTokenExpiresAt:new Date(Date.now()+600000).toISOString(), platformRoles:scenario.role === 'PLATFORM_ADMIN' ? ['PLATFORM_ADMIN'] : [], activeOrganisationId:['PATIENT','PLATFORM_ADMIN'].includes(scenario.role) ? null : orgId, organisationRoles:['PATIENT','PLATFORM_ADMIN'].includes(scenario.role) ? [] : organisationRoles }
      else if (path === '/auth/account') data = identity
      else if (path === '/auth/csrf') data = { token:'synthetic-csrf', headerName:'X-XSRF-TOKEN', parameterName:'_csrf' }
      else if (path === '/auth/account/profile' && request.method() === 'PUT') {
        assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
        const command = request.postDataJSON()
        assert.equal(command.version, identity.version)
        identity = { ...identity, ...command, version:identity.version + 1 }; data = identity
      }
      else if (path === '/organisations/current/profile') {
        if (request.method() === 'PUT') {
          assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
          const command = request.postDataJSON()
          assert.equal(command.version, organisation.version)
          organisation = { ...organisation, ...command, version:organisation.version + 1 }
        }
        data = organisation
      }
      else if (path === '/organisations/memberships') data = [{ organisationId:orgId, organisationName:organisation.name, organisationType:'CLINIC', membershipId:'member', membershipVersion:0, roles:organisationRoles }]
      else if (path === '/appointments') data = scenario.multiRole ? appointments : []
      else if (path === '/patients') data = { items:[], page:0, size:50, totalElements:0, totalPages:0 }
      else if (path === '/conversations' && request.method() === 'POST') {
        assert.equal(request.headers()['x-xsrf-token'],'synthetic-csrf')
        const command=request.postDataJSON()
        assert.equal(command.sourceConsultationId,conversationSource);assert.equal(command.patientRegistrationId,conversationPatient)
        assert.equal(command.recipientUserId,conversationRecipient);assert.match(command.conversationRequestId,/^[0-9a-f-]{36}$/)
        conversation={id:'conversation-1',organisationId:orgId,subject:command.subject,patientRegistrationId:conversationPatient,
          patientAccessGranted:false,createdByUserId:userId,unreadCount:0,version:0,createdAt:'2026-09-18T10:00:00Z',lastMessageAt:'2026-09-18T10:00:00Z',
          participants:[{userId:conversationRecipient,displayName:'Synthetic Colleague',membershipId:'member-recipient',joinedAt:'2026-09-18T10:00:00Z',lastReadAt:null}]}
        data=conversation
      }
      else if (path === '/conversations') data = { content:conversation?[conversation]:[], page:0, size:50, totalElements:conversation?1:0, totalPages:conversation?1:0 }
      else if (path === '/conversations/conversation-1/messages') data = { content:[], page:0, size:50, totalElements:0, totalPages:0 }
      else if (path.endsWith('/collaboration-doctors')) data = { content:scenario.creation || scenario.messaging ? [{ membershipId:'member-recipient', organisationId:orgId, userId:scenario.messaging?conversationRecipient:'recipient-1', displayName:'Synthetic Colleague', membershipVersion:0 }] : [], page:0, size:100, totalElements:scenario.creation || scenario.messaging ? 1 : 0, totalPages:scenario.creation || scenario.messaging ? 1 : 0 }
      else if (path === '/consultations/referral-sources') data = { content:[{ consultationId:scenario.messaging?conversationSource:'consultation-1', patientRegistrationId:scenario.messaging?conversationPatient:'registration-1', appointmentId:'appointment-1', finalizedAt:'2026-09-01T09:00:00Z', version:4 }], page:0, size:20, totalElements:1, totalPages:1 }
      else if (path === '/consultations/consultation-1/record') data = { id:'consultation-1', organisationId:orgId, patientRegistrationId:'registration-1', appointmentId:'appointment-1', patientId:'private-patient', doctorUserId:userId, doctorMembershipId:'member-sender', status:'FINALIZED', version:4,
        reasonForConsultation:'Synthetic consultation', clinicalAssessment:'Private assessment', treatmentPlan:'Private treatment', additionalNotes:'Private note', followUpInstructions:null,
        symptoms:[], examinationFindings:[], vitalSigns:null, corrections:[], medications:[], history:[{ id:'allergy-1', category:'ALLERGY', description:'Synthetic allergy', notes:null }],
        diagnoses:[{ id:'diagnosis-1', label:'Synthetic diagnosis', type:'PRIMARY', status:'CONFIRMED', code:null, codeSystem:null, notes:null }], finalizedAt:'2026-09-01T09:00:00Z', createdAt:'2026-09-01T09:00:00Z', updatedAt:'2026-09-01T09:00:00Z' }
      else if (path === '/files' && url.searchParams.get('consultationId') === 'consultation-1') data = [
        { fileId:'file-1', consultationId:'consultation-1', originalFilename:'synthetic-report.pdf', contentType:'application/pdf', size:123, uploadStatus:'STORED', scanStatus:'CLEAN', downloadAvailable:true },
        { fileId:'pending-1', consultationId:'consultation-1', originalFilename:'quarantined.pdf', contentType:'application/pdf', size:123, uploadStatus:'STORED', scanStatus:'PENDING', downloadAvailable:false },
      ]
      else if (path === '/notifications') data = { items:[], page:0, size:20, totalElements:0, totalPages:0, first:true, last:true }
      else if (path === '/notifications/unread-count') data = { unreadCount:0 }
      else if (path === '/patients/me/registrations') data = []
      else if (path === '/referrals' && request.method() === 'POST') {
        assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
        const command = request.postDataJSON()
        assert.match(command.referralRequestId, /^[0-9a-f-]{36}$/)
        assert.equal(command.recipientUserId, 'recipient-1')
        assert.equal(command.patientRegistrationId, 'registration-1')
        assert.equal(command.sourceConsultationId, 'consultation-1')
        assert.equal(command.sendImmediately, scenario.creation === 'send')
        assert.equal(command.referralType, scenario.createSharedCare ? 'SHARED_TREATMENT' : 'SECOND_OPINION')
        assert.deepEqual(command.selectedItems, scenario.createSharedCare ? [] : [{ resourceType:'DIAGNOSIS', resourceId:'diagnosis-1' }, { resourceType:'MEDICAL_DOCUMENT', resourceId:'file-1' }])
        assert.equal(command.clinicalSummary, null)
        const {sourceConsultationId,...storedCommand}=command
        referral = { ...referral, ...storedCommand, senderUserId:userId, senderDisplayName:'Synthetic Browser', recipientDisplayName:'Synthetic Colleague', status:command.sendImmediately ? 'SENT' : 'DRAFT', sharingGrantId:null, version:0,
          selectedItems:command.selectedItems.map((item, index) => ({ ...item, id:'item-' + index })) }
        data = referral
      }
      else if (path === '/referrals') data = { content:[referral], page:0, size:20, totalElements:1, totalPages:1 }
      else if (path === '/referrals/referral-1') data = referral
      else if (path === '/clinical/shared-care/registration-1/consultations'
        || path === '/clinical/shared-care/registration-1/consultations/care-consultation-1') {
        assert.equal(request.method(), 'GET')
        assert.equal(referral.referralType, 'SHARED_TREATMENT')
        if (denySelected || referral.status !== 'ACTIVE') {
          await route.fulfill({ status:404, contentType:'application/problem+json', body:JSON.stringify({ title:'Care unavailable', status:404 }) }); return
        }
        const envelope = { organisationId:orgId, patientRegistrationId:'registration-1', validUntil:referral.accessExpiresAt }
        data = path.endsWith('/consultations') ? { ...envelope, page:0, size:20, totalElements:1, totalPages:1,
          content:[{ consultationId:'care-consultation-1', appointmentId:'care-appointment-1', doctorUserId:'original-care-author', finalizedAt:'2026-09-01T09:00:00Z', version:2 }] }
          : { ...envelope, consultation:{ id:'care-consultation-1', organisationId:orgId, patientRegistrationId:'registration-1', appointmentId:'care-appointment-1', patientId:'synthetic-care-patient', doctorUserId:'original-care-author', doctorMembershipId:'care-author-member',
            status:'FINALIZED', version:2, reasonForConsultation:'Protected shared-care encounter', clinicalAssessment:'Corrected shared-care assessment', treatmentPlan:'Synthetic plan', followUpInstructions:null, additionalNotes:null,
            symptoms:[], history:[], examinationFindings:[], vitalSigns:null, diagnoses:[], medications:[],
            corrections:[{ id:'care-correction', targetType:'CONSULTATION', targetId:null, fieldName:'clinicalAssessment', oldValue:'Original assessment', newValue:'Corrected shared-care assessment', reason:'Synthetic shared-care correction', actorUserId:'original-care-author', consultationVersion:2, correctedAt:'2026-09-01T09:10:00Z' }],
            finalizedAt:'2026-09-01T09:00:00Z', finalizedByUserId:'original-care-author', createdAt:'2026-09-01T08:00:00Z', updatedAt:'2026-09-01T09:10:00Z' } }
      }
      else if (path.startsWith('/files/shared-care/registration-1/')) {
        assert.equal(referral.referralType, 'SHARED_TREATMENT')
        if (denyCareFiles || referral.status !== 'ACTIVE') {
          await route.fulfill({ status:404, contentType:'application/problem+json', body:JSON.stringify({ title:'Care documents unavailable', status:404 }) }); return
        }
        const envelope = { organisationId:orgId, patientRegistrationId:'registration-1', validUntil:referral.accessExpiresAt }
        const file = { fileId:'care-file-1', consultationId:'care-consultation-1', originalFilename:'synthetic-care.pdf',
          contentType:'application/pdf', size:18, uploadStatus:'STORED', scanStatus:'CLEAN', downloadAvailable:true,
          createdAt:'2026-09-01T09:00:00Z', uploadedAt:'2026-09-01T09:00:00Z', availableAt:'2026-09-01T09:00:00Z', rejectedAt:null }
        if (path.endsWith('/download-grants')) {
          assert.equal(request.method(), 'POST')
          assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
          data = { grantId:'care-download-grant', fileId:file.fileId, downloadToken:'synthetic-care-download',
            downloadPath:'/api/v1/files/shared-care/registration-1/care-file-1/content', expiresAt:referral.accessExpiresAt }
        } else if (path.endsWith('/content')) {
          assert.equal(request.method(), 'GET'); assert.equal(request.headers()['x-download-token'], 'synthetic-care-download')
          await route.fulfill({ status:200, contentType:'application/pdf', headers:{ 'Cache-Control':'no-store' }, body:'synthetic document' }); return
        } else if (path.endsWith('/consultations/care-consultation-1')) {
          assert.equal(request.method(), 'GET')
          data = { ...envelope, consultationId:file.consultationId, content:[file], page:0, size:20, totalElements:1, totalPages:1 }
        } else {
          assert.equal(request.method(), 'GET'); assert.equal(path, '/files/shared-care/registration-1/care-file-1')
          data = { ...envelope, file }
        }
      }
      else if (path === '/referrals/referral-1/accept' && request.method() === 'POST') {
        assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
        assert.deepEqual(request.postDataJSON(), { expectedVersion:2 })
        referral = { ...referral, status:'ACTIVE', sharingGrantId:'selected-grant', version:3 }; data = referral
      }
      else if (path === '/referrals/referral-1/complete' && request.method() === 'POST') {
        assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
        assert.deepEqual(request.postDataJSON(), { expectedVersion:3 })
        referral = { ...referral, status:'COMPLETED', sharingGrantId:null, version:4 }; data = referral
      }
      else if (path === '/clinical/shared/registration-1/DIAGNOSIS/diagnosis-1') {
        if (denySelected || referral.status !== 'ACTIVE') {
          await route.fulfill({ status:404, contentType:'application/problem+json', body:JSON.stringify({ title:'Selected resource unavailable', status:404 }) })
          return
        }
        data = { resourceType:'DIAGNOSIS', resourceId:'diagnosis-1', patientRegistrationId:'registration-1', validUntil:referral.accessExpiresAt,
          diagnosis:{ id:'diagnosis-1', label:'Protected selected diagnosis', code:'SYNTHETIC', codeSystem:'DEMO', type:'PRIMARY', status:'SUSPECTED', notes:'Synthetic selected note' } }
      }
      else if (path === '/files/shared/registration-1/file-1') data = {
        fileId:'file-1', consultationId:'consultation-1', originalFilename:'synthetic-selected.pdf', contentType:'application/pdf', size:18,
        uploadStatus:'STORED', scanStatus:'CLEAN', downloadAvailable:true,
      }
      else if (path === '/files/shared/registration-1/file-1/download-grants' && request.method() === 'POST') {
        assert.equal(request.headers()['x-xsrf-token'], 'synthetic-csrf')
        assert.equal(referral.status, 'ACTIVE')
        data = { grantId:'one-time-grant', fileId:'file-1', downloadPath:'/api/v1/files/shared/registration-1/file-1/content', downloadToken:'synthetic-download-token', expiresAt:new Date(Date.now()+60_000).toISOString() }
      }
      else if (path === '/files/shared/registration-1/file-1/content') {
        assert.equal(referral.status, 'ACTIVE')
        assert.equal(request.headers()['x-download-token'], 'synthetic-download-token')
        assert.equal(url.searchParams.size, 0)
        await route.fulfill({ status:200, contentType:'application/pdf', headers:{ 'Cache-Control':'no-store' }, body:'%PDF-1.4 synthetic' })
        return
      }
      else if (['/departments','/staff','/platform/organisations'].includes(path)) data = { items:[], page:0, size:1, totalElements:0, totalPages:0 }
      else {
        unexpected.push(request.method() + ' ' + path)
        await route.fulfill({ status:500, contentType:'application/problem+json', body:JSON.stringify({ title:'Unexpected test request', status:500 }) })
        return
      }
      await route.fulfill({ status:200, headers:{ 'access-control-allow-origin':base, 'access-control-allow-credentials':'true', 'access-control-allow-headers':'Content-Type,X-XSRF-TOKEN', 'access-control-allow-methods':'GET,POST,PUT,DELETE,OPTIONS' }, contentType:'application/json', body:JSON.stringify(data) })
    })
    await page.goto(base + scenario.path)
    await page.getByRole('heading', { name:scenario.text, exact:true }).waitFor().catch(async error => { console.error(JSON.stringify({ scenario, requests, unexpected, errors, page:await page.locator('body').innerText() })); throw error })
    await page.getByText('Loading current data…', { exact:true }).first().waitFor({ state:'hidden' })
    assert.equal(await page.getByRole('alert').count(), 0, JSON.stringify(unexpected))
    if (scenario.clinicalDenied) {
      assert.equal(new URL(page.url()).pathname, '/forbidden')
      assert.equal(await page.getByLabel('Switch workspace').count(), 0)
      assert.deepEqual([...new Set(requests)], ['GET /auth/session'], 'Denied routes must not mount clinical consumers; StrictMode may repeat restoration')
    }
    if (scenario.multiRole) {
      async function openNavigation() {
        if (width < 800) await page.getByRole('button', { name:'Open navigation', exact:true }).click()
      }
      async function switchWorkspace(role) {
        await openNavigation()
        const selector = page.getByLabel('Switch workspace', { exact:true })
        await selector.waitFor({ state:'visible' })
        await selector.evaluate(async element => { await Promise.all(element.closest('aside').getAnimations().map(animation => animation.finished)) })
        const box = await selector.boundingBox()
        assert.ok(box && box.x >= 0 && box.x + box.width <= width + 1 && box.y + box.height <= 960, 'Workspace selector must fit the open navigation')
        assert.deepEqual(await selector.locator('option').evaluateAll(options => options.map(option => option.value)), ['hospital-super-admin','doctor','receptionist'])
        await page.screenshot({ path:resolve(output, 'workspace-selector-' + role + '-' + width + '.png'), fullPage:true })
        await selector.selectOption(role)
        if (width < 800) await page.locator('.sidebar--open').waitFor({ state:'hidden' })
      }
      await switchWorkspace('doctor')
      await page.getByRole('heading', { name:'Welcome, Synthetic.', exact:true }).waitFor()
      await page.getByText('Own doctor room', { exact:true }).waitFor()
      assert.equal(await page.getByText('Other doctor room', { exact:true }).count(), 0)
      await page.reload()
      await page.getByRole('heading', { name:'Welcome, Synthetic.', exact:true }).waitFor()
      assert.equal(await page.getByLabel('Switch workspace').inputValue(), 'doctor')
      for (const [button, heading] of [['Patients','Your patient workspace.'], ['Appointments','Appointments with accountable states.'], ['Clinical workspace','Consultations with a traceable record.']]) {
        await openNavigation()
        await page.getByRole('navigation', { name:'Primary navigation' }).getByRole('button', { name:button, exact:true }).click()
        await page.getByRole('heading', { name:heading, exact:true }).waitFor()
        if (button === 'Patients') {
          await page.getByRole('link', { name:'Registration own-registration', exact:true }).waitFor()
          assert.equal(await page.getByRole('link', { name:'Registration other-registration', exact:true }).count(), 0)
        } else {
          await page.getByText(/Own doctor room/).waitFor()
          assert.equal(await page.getByText(/Other doctor room/).count(), 0)
        }
      }
      await switchWorkspace('receptionist')
      await page.waitForURL('**/reception/patients')
      await openNavigation()
      await page.getByRole('navigation', { name:'Receptionist navigation' }).getByRole('button', { name:'Check-in queue', exact:true }).click()
      await page.getByRole('heading', { name:'Check-in queue.', exact:true }).waitFor()
      await page.getByText('Other doctor room', { exact:true }).first().waitFor()
      await switchWorkspace('hospital-super-admin')
      await page.getByRole('heading', { name:'Your organisation at a glance.', exact:true }).waitFor()
      assert.ok(requests.every(request => request.startsWith('GET ')), 'Workspace navigation must not change server authority')
    }
    if (scenario.edit) {
      const accountEdit = scenario.edit === 'account'
      await page.getByRole('button', { name:accountEdit ? 'Edit profile' : 'Edit organisation profile', exact:true }).click()
      await verifyDialogViewport(page)
      await page.getByLabel(accountEdit ? 'First name' : 'Organisation name', { exact:true }).fill(accountEdit ? 'Updated' : 'Updated Clinic')
      await page.getByRole('button', { name:accountEdit ? 'Save profile' : 'Save organisation profile', exact:true }).click()
      await page.getByRole('dialog').waitFor({ state:'hidden' })
      await page.reload()
      await page.getByRole('heading', { name:accountEdit ? 'Updated Browser' : 'Updated Clinic', exact:true }).waitFor()
    }
    if (scenario.messaging) {
      await page.getByRole('button',{name:'New conversation',exact:true}).click()
      await page.getByLabel('Clinical colleague').selectOption(conversationRecipient)
      await page.getByLabel('Conversation subject').fill('Synthetic finalised-source discussion')
      assert.equal(requests.includes('GET /consultations/referral-sources'),false)
      await page.getByLabel('Patient context',{exact:true}).selectOption('finalised')
      await page.getByLabel('Finalised consultation',{exact:true}).selectOption(conversationSource)
      assert.equal(await page.getByLabel(/Patient registration UUID/).inputValue(),conversationPatient)
      await verifyDialogViewport(page)
      await page.screenshot({path:resolve(output,'conversation-source-'+width+'.png'),fullPage:true})
      await page.getByRole('button',{name:'Create conversation',exact:true}).click()
      await page.getByRole('dialog').waitFor({state:'hidden'})
      await page.getByRole('heading',{name:'Synthetic finalised-source discussion',exact:true}).waitFor()
      assert.equal(requests.filter(item=>item==='POST /conversations').length,1)
      assert.equal(requests.some(item=>item.endsWith('/record')),false)
      await page.getByText('Only the selected doctors can open a thread. A patient reference supplies context, but never grants access to the medical record.',{exact:true}).waitFor()
      assert.equal(conversation.patientAccessGranted,false)
    }
    if (scenario.referral) {
      await page.getByRole('button', { name:'Review referral', exact:true }).click()
      await page.getByRole('button', { name:'Accept referral', exact:true }).click()
      await page.getByRole('button', { name:'Confirm accept', exact:true }).click()
      await page.getByRole('button', { name:'Complete referral', exact:true }).waitFor()
      await verifyDialogViewport(page)
      assert.equal(await page.getByRole('link', { name:/patient record/i }).count(), 0)
      await page.reload()
      await page.getByRole('button', { name:'Complete referral', exact:true }).waitFor()
      await verifyDialogViewport(page)
      await page.getByRole('button', { name:'Open selected diagnosis', exact:true }).click()
      await page.getByText('Protected selected diagnosis', { exact:true }).waitFor()
      assert.equal(await page.getByText('Private assessment', { exact:true }).count(), 0)
      assert.equal(requests.includes('GET /consultations/consultation-1/record'), false)
      await verifyDialogViewport(page)
      await page.screenshot({ path:resolve(output, 'selected-diagnosis-' + width + '.png'), fullPage:true })
      await page.getByRole('button', { name:'Close preview', exact:true }).click()
      await page.getByRole('button', { name:'Open selected medical document', exact:true }).click()
      const downloadPromise = page.waitForEvent('download')
      await page.getByRole('button', { name:'Download selected file', exact:true }).click()
      const download = await downloadPromise
      assert.equal(download.suggestedFilename(), 'synthetic-selected.pdf')
      await page.getByRole('button', { name:'Close preview', exact:true }).click()
      await page.getByRole('button', { name:'Open selected diagnosis', exact:true }).click()
      await page.getByText('Protected selected diagnosis', { exact:true }).waitFor()
      denySelected = true
      await page.getByText('Access could not be verified. The preview has been cleared. Refresh the referral before retrying.', { exact:true }).waitFor()
      assert.equal(await page.getByText('Protected selected diagnosis', { exact:true }).count(), 0)
      denySelected = false
      await page.getByRole('button', { name:'Retry selected access', exact:true }).click()
      await page.getByText('Protected selected diagnosis', { exact:true }).waitFor()
      await page.getByRole('button', { name:'Complete referral', exact:true }).click()
      await page.getByRole('button', { name:'Confirm complete', exact:true }).click()
      await page.getByRole('button', { name:'Open selected diagnosis', exact:true }).waitFor({ state:'hidden' })
      assert.equal(await page.getByText('Protected selected diagnosis', { exact:true }).count(), 0)
      await verifyDialogViewport(page)
    }
    if (scenario.sharedCare) {
      await page.getByRole('button', { name:'Open shared-care history', exact:true }).waitFor()
      assert.equal(requests.some(item => item.includes('/clinical/shared-care/')), false)
      await page.getByRole('button', { name:'Open shared-care history', exact:true }).click()
      await page.getByRole('button', { name:'Open encounter care-consultation-1', exact:true }).click()
      await page.getByText('Protected shared-care encounter', { exact:true }).waitFor()
      await page.getByText('Synthetic shared-care correction', { exact:true }).waitFor()
      await verifyDialogViewport(page)
      assert.equal(await page.getByRole('textbox').count(), 0)
      assert.equal(requests.some(item => item.startsWith('GET /files') || item.includes('/consultations/care-consultation-1/record')), false)
      await page.getByRole('button', { name:'Open encounter documents', exact:true }).click()
      await page.getByRole('button', { name:'Download document synthetic-care.pdf', exact:true }).waitFor()
      await verifyDialogViewport(page)
      const downloaded = page.waitForEvent('download')
      await page.getByRole('button', { name:'Download document synthetic-care.pdf', exact:true }).click()
      const document = await downloaded
      assert.equal(document.suggestedFilename(), 'synthetic-care.pdf')
      await document.delete()
      assert.equal(requests.includes('POST /files/shared-care/registration-1/care-file-1/download-grants'), true)
      assert.equal(requests.includes('GET /files/shared-care/registration-1/care-file-1/content'), true)
      assert.equal(requests.some(item => item.startsWith('GET /files/') && !item.includes('/files/shared-care/')), false)
      denyCareFiles = true
      await page.getByText('Shared-care documents could not be verified. The document list has been cleared.', { exact:true }).waitFor()
      assert.equal(await page.getByRole('button', { name:'Download document synthetic-care.pdf', exact:true }).count(), 0)
      denyCareFiles = false
      await page.getByRole('button', { name:'Retry encounter documents', exact:true }).click()
      await page.getByRole('button', { name:'Download document synthetic-care.pdf', exact:true }).waitFor()
      await page.screenshot({ path:resolve(output, 'shared-care-history-' + width + '.png'), fullPage:true })
      denySelected = true
      await page.getByText('Shared-care history could not be verified. Access may have ended or a service is unavailable.', { exact:true }).waitFor()
      assert.equal(await page.getByText('Protected shared-care encounter', { exact:true }).count(), 0)
      denySelected = false
      await page.getByRole('button', { name:'Retry shared-care history', exact:true }).click()
      await page.getByText('Protected shared-care encounter', { exact:true }).waitFor()
      await page.getByRole('button', { name:'Complete referral', exact:true }).click()
      await page.getByRole('button', { name:'Confirm complete', exact:true }).click()
      await page.getByText('Referral updated by Communication Service.', { exact:true }).waitFor()
      assert.equal(await page.getByText('Protected shared-care encounter', { exact:true }).count(), 0)
      assert.equal(await page.getByRole('button', { name:'Open shared-care history', exact:true }).count(), 0)
      assert.equal(await page.getByRole('button', { name:'Download document synthetic-care.pdf', exact:true }).count(), 0)
      await verifyDialogViewport(page)
    }
    if (scenario.creation) {
      await page.getByRole('button', { name:'Use consultation', exact:true }).click()
      await page.getByLabel('Referral reason', { exact:true }).waitFor()
      await verifyDialogViewport(page)
      assert.equal(await page.getByRole('checkbox', { checked:true }).count(), 0)
      assert.equal(await page.getByLabel('Recorded consent or legal basis').inputValue(), '')
      assert.equal(await page.getByLabel('File: quarantined.pdf', { exact:true }).count(), 0)
      assert.equal(await page.getByLabel('Referral type', { exact:true }).inputValue(), 'SECOND_OPINION')
      if (scenario.createSharedCare) {
        await page.getByLabel('Referral type', { exact:true }).selectOption('SHARED_TREATMENT')
        assert.equal(await page.getByLabel('Diagnosis: Synthetic diagnosis', { exact:true }).count(), 0)
        assert.equal(await page.getByRole('checkbox', { checked:true }).count(), 0)
        await page.getByText(/After acceptance, both doctors are responsible for treatment/).waitFor()
      }
      await page.getByLabel('Recipient colleague').selectOption('recipient-1')
      await page.getByLabel('Referral reason', { exact:true }).fill('Synthetic specialist review')
      await page.getByLabel('Sharing purpose', { exact:true }).fill(scenario.createSharedCare ? 'Synthetic joint treatment' : 'Synthetic second opinion')
      await page.getByLabel('Recorded consent or legal basis').selectOption('RECORDED_WRITTEN')
      await page.getByLabel('Consent evidence reference', { exact:true }).fill('synthetic-consent-reference')
      const times = await page.evaluate(() => [-3600000, 86400000].map(offset => { const date = new Date(Date.now()+offset); date.setMinutes(date.getMinutes()-date.getTimezoneOffset()); return date.toISOString().slice(0,16) }))
      await page.getByLabel('Consent recorded at', { exact:true }).fill(times[0])
      await page.getByLabel('Access expires at', { exact:true }).fill(times[1])
      if (scenario.createSharedCare) await page.getByLabel(/I acknowledge that both doctors/).check()
      else {
        await page.getByLabel('Diagnosis: Synthetic diagnosis', { exact:true }).check()
        await page.getByLabel('File: synthetic-report.pdf', { exact:true }).check()
      }
      await page.getByLabel(/I confirm the/).check()
      await page.screenshot({ path:resolve(output, 'composer-form-' + scenario.creation + '-' + width + '.png'), fullPage:true })
      await page.getByRole('button', { name:scenario.creation === 'send' ? 'Review and send' : 'Review draft', exact:true }).click()
      await page.getByRole('button', { name:scenario.creation === 'send' ? 'Confirm and send' : 'Confirm draft', exact:true }).waitFor()
      assert.equal(requests.includes('POST /referrals'), false)
      await page.getByRole('heading', { name:'Referral type: ' + (scenario.createSharedCare ? 'Shared treatment' : 'Second opinion'), exact:true }).waitFor()
      if (scenario.createSharedCare) await page.getByText(/Shared-treatment scope: both doctors treat/).waitFor()
      await verifyDialogViewport(page)
      await page.getByRole('button', { name:scenario.creation === 'send' ? 'Confirm and send' : 'Confirm draft', exact:true }).click()
      await page.getByRole('dialog', { name:'Review referral', exact:true }).waitFor()
      assert.equal(requests.filter(item => item === 'POST /referrals').length, 1)
      assert.equal(requests.some(item => item.includes('/clinical/shared-care/')), false, 'Creation does not activate shared access')
      await page.reload()
      await page.getByRole('dialog', { name:'Review referral', exact:true }).waitFor()
      await page.getByRole('button', { name:scenario.creation === 'send' ? 'Revoke referral' : 'Send referral', exact:true }).waitFor()
      await verifyDialogViewport(page)
    }
    const layout = await page.evaluate(() => ({ viewport:innerWidth, scroll:document.documentElement.scrollWidth }))
    assert.ok(layout.scroll <= layout.viewport + 1, scenario.path + ' overflows: ' + JSON.stringify(layout))
    assert.deepEqual(unexpected, [], scenario.path)
    assert.deepEqual(errors, [], scenario.path)
    await page.screenshot({ path:resolve(output, scenario.role + '-' + scenario.path.split('/').at(-1).replaceAll('?', '-') + '-' + (scenario.creation || (scenario.multiRole ? 'multi-role' : 'view')) + '-' + width + '.png'), fullPage:true })
    report.push({ ...scenario, width, requests, layout })
    console.log(scenario.path + ' ' + width + ' passed')
    await context.close()
  }
  await writeFile(resolve(output, 'report.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ checks:report.length, output, mode:'synthetic intercepted Gateway contracts; not live backend' }))
} finally {
  await browser.close()
}
