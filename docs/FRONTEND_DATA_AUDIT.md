# Frontend data integration audit

Reviewed: 2026-09-08. Scope: the active React routes and their data sources,
against the internship V1 in `PROJECT_CONTEXT.md`. This is a source audit, not
a claim that a live multi-service browser acceptance journey passed. No
frontend implementation or approved visual design was changed in that audit task.
The dated repair record below supersedes the baseline findings where indicated.

## Repair record — 2026-09-09

- Auth boundaries now replace the query client, clear sensitive component state,
  abort in-flight requests and reject late results. Account/organisation switches,
  refresh/logout races, late mutations, CSRF and private blobs have regressions.
- Active routes no longer mount demo/workflow providers. Real doctor and patient
  appointment data, colleague discovery, patient-safe doctor availability,
  organisation counters, account lookup and device sessions replace sample cards.
- Reception check-in uses the selected day's real confirmed/checked-in appointments
  and versioned server transition. Legacy encounter routes enter Clinical without
  treating a patient ID as a consultation ID.
- Shell identity is loaded from Auth and Organisation. Own-account and organisation
  profile forms now save through new validated, versioned, audited APIs. Backend
  tests cover fresh reads, CSRF, stale edits, role and organisation isolation.
- Organisation-member notification controls use the real private inbox; admin
  notifications do not navigate to doctor routes. Patient notifications still need
  Phase 7 recipient policy/projections, so no fake bell or unread dot is shown.
- Deferred healthcare, operational and fake audit screens are removed from active
  navigation and guarded deep links show an explicit unavailable state. Prototype
  assets remain in source, unmounted, to preserve the approved design reference.
- Phase 6E now has a real participant inbox/detail and versioned
  send/accept/reject/revoke/complete actions. Creation from explicit selections
  and protected content previews are the next part, not sample UI controls.
  Central audit queries and patient notifications are not claimed complete.

Validation through the first referral slice: 126 frontend tests; 210 backend
tests (Auth 119, Organisation 40, Gateway 35, Communication 16); production build
and executable packaging pass. Twenty-four intercepted browser contract checks
cover twelve routes at 1440/375 px. A 2026-09-10 screenshot review also identified
and fixed page-animation clipping of full-screen workflow drawers; viewport
coverage and internal-overflow assertions were added. These checks do not
constitute the live two-doctor/Gateway/Kafka acceptance journey.

## Referral creation repair record — 2026-09-10

The sender-creation part of Phase 6E is now integrated. `/doctor/referrals`
offers **New referral** with a real, paginated list of consultations finalised
under the current doctor's original active membership. The form loads only the
chosen author-owned consultation and its owned files, starts with no selections,
and requires purpose, recorded consent evidence, expiry and review before
creating a draft or sending. Only stored, clean, available files are selectable.

Final confirmation rechecks source membership/ownership, consultation version,
recipient eligibility and file availability. Retries after uncertain delivery
reuse the same frozen request ID/body within the form. Backend recipient queries
now hide unsent drafts, including withdrawn drafts; unsent withdrawals produce
no recipient notification event. Protected reads still rely on the owning
Clinical/File service's resource-level grant checks, not frontend validation.

Verification: 144 frontend tests; Clinical 32, Communication 19 and Gateway 36
backend tests; production frontend build and Clinical/Communication packages;
28 intercepted desktop/mobile browser checks including creation and readback.
The checks use synthetic data and do not claim live two-doctor/Gateway/Kafka
acceptance. Exact selected-content previews remain the next task; Phase 6 is
not complete. The baseline findings below are retained as historical evidence.

## Main finding

The frontend contains real backend-integrated vertical slices alongside the
original prototype. The surrounding dashboards and several duplicate routes
still use hardcoded values, mock services, or browser-only state. A component
named `Connected` is not necessarily connected to a backend.

The inspected local configuration has `VITE_USE_MOCKS=true` and
`VITE_USE_AUTH_MOCKS=false`: real authentication with legacy demo data. This
does **not** make every page mock-backed: newer feature pages import dedicated
REST adapters directly.

Do not simply change the mock flag to false. The old adapters still call
`/doctors`, `/clinical-orders`, appointment `/cancellation`,
and `/status`, and list appointments without the required date range. They
also use legacy numeric doctor IDs and combined administrative/clinical
patient models. The legacy `/departments` path exists, but its assumed collection
shape differs from the owned API's paginated DTO. These adapters do not match the
implemented scoped contracts as a whole.

Sources: [service selection](../frontend/src/services/index.ts),
[legacy adapters](../frontend/src/services/api/restServices.ts),
[environment defaults](../frontend/src/config/env.ts).

## Cross-cutting fixes

| Priority | Finding and evidence | Required fix / acceptance check |
| --- | --- | --- |
| P0 | `AuthProvider` updates the session on logout, login, refresh failure, and organisation changes without clearing the singleton query cache. Messenger and notification keys include organisation but not the signed-in user; global query freshness is 30 seconds. | Partition user-scoped keys by identity and organisation, cancel requests and clear protected cached data at auth boundaries, and reset sensitive component/provider state. Prove Doctor A -> logout -> Doctor B in the same organisation never renders A's cached conversations or notifications, including late request completions. This is a code-supported risk, not a reproduced live disclosure. |
| P1 | `DemoDataProvider` seeds patients/doctors immediately, loads five legacy collections in one `Promise.all`, and runs only once before/independently of auth. One failure leaves seeded data visible; auth/context changes do not reload it. | Keep demo providers behind an explicit demo boundary. Active V1 pages must use auth-enabled, role-specific queries with loading, empty, and error states, never silently fall back to sample patients. Test login and organisation switching. |
| P1 | `WorkflowProvider`, demo messages, encounter drafts, and local audit use shared browser storage, not service-owned persistence. | Remove these providers from real workflows; reserve them for a clearly separated prototype. Do not store clinical records in global demo storage. Server mutations must drive success, invalidation, and durable state. |
| P1 | Patient/organisation/platform shells use Nora Bennett, Leila Mansour, Sofia Alvarez, St. Helena, or Avery Kim as fixed identities. Static badges and a permanent notification dot imply real state. | Render the authenticated identity and selected organisation. Use permitted unread/count queries; omit unsupported badges. Check each role rather than only the doctor shell. |
| P2 | Quick find, generic portal notification bell, profile overflow, and several settings/profile controls have no action; some saves only set local success state. | Wire supported actions with keyboard/loading/error behaviour, or hide/disable with an honest explanation. Do not present a fake success toast. |

Sources: [auth lifecycle](../frontend/src/app/auth/AuthProvider.tsx),
[query defaults](../frontend/src/app/queryClient.ts),
[messenger query keys](../frontend/src/features/communication/DoctorMessengerPage.tsx),
[notification query keys](../frontend/src/features/notifications/DoctorNotificationCenter.tsx),
[global provider composition](../frontend/src/main.tsx),
[demo provider](../frontend/src/app/data/DemoDataProvider.tsx),
[workflow provider](../frontend/src/app/data/WorkflowProvider.tsx).

P0 means fix before a shared-browser multi-account demonstration. P1 means an
active V1 workflow is misleading or incomplete. P2 means a secondary
integration/polish gap. Deferred modules should be hidden from V1, not built
just to populate their cards.

## Active route inventory: what needs fixing

The component references below are in [App.tsx](../frontend/src/App.tsx) unless
a separate file is linked. Line numbers are navigation hints for this audit
snapshot, not permanent identifiers.

| Priority | Screen / component | Actual data today | What must change |
| --- | --- | --- | --- |
| P1 | `/reception/checkin` (`ReceptionCheckIn`, line 762) | Three hardcoded patients; clicking Check in only changes `useState`; sidebar badge is always 3. | Reuse the real reception appointment lifecycle query and check-in mutation; filter the selected day's eligible arrivals. Refresh must retain the server transition and the doctor queue must observe it. |
| P1 | `/doctor/encounters/:patientId` (`DoctorEncounterConnected`, line 857) | Browser-local draft and local “signed/locked” state; no Clinical mutation. The demo patient record links here. | Remove/reroute this duplicate path into the real appointment-backed Clinical workspace. Finalisation must be server-confirmed and immutable; do not treat a local sign button as a medical-record write. |
| P1 | `/doctor/patients` and `/doctor/patients/:id` (`PatientList`, `PatientRecord`, lines 252/262) | Demo patient list and combined sample vitals/history/medications/notes. New receptionist registrations and real consultations do not populate it. | Use an authorised doctor care-roster/projection and Clinical reads. If a roster endpoint is missing, add it with clinical scope. Do not reuse an administrative DTO containing prohibited fields or grant doctors/admins additional access just to fill this screen. |
| P1 | `/doctor/overview` (`Overview`, line 187) | Demo appointments/patients, fixed greeting/day text, selected priority patient/vitals, 128 active patients, 02 threads, 24-minute average; timeline is just the first four demo appointments. | Use the authenticated profile, selected date's appointments, actual conversations/notifications, and permitted clinical summaries. Hide unsupported metrics/tasks. Empty lists must be safe; remove positional `patients[2]` assumptions. |
| P1 | `/doctor/orders` (`DoctorOrdersConnected`, line 782) | Generic demo order ledger with laboratory/imaging/prescription/referral choices. No real referral lifecycle. | Add the Phase 6 referral workspace with explicit selected items, purpose/consent, recipient acceptance/rejection, revocation/expiry, and protected read preview. Basic medication belongs in Clinical; hide deferred lab/imaging/pharmacy operations. |
| P1 | `/patient/overview` (`PatientHomeConnected`, line 840) | Assumes `patients[0]` is the signed-in patient; demo appointments and local care-plan completion. | Resolve the authenticated patient's registrations and appointments using patient-owned APIs. Reflect bookings made on the real appointments page. Remove unsupported clinical/task cards. |
| P1 | `/patient/profile` (`PatientProfile`, line 579) | Hardcoded identity/contact fields; Save only flips a boolean. Insurance, caregiver, and privacy access-history sections are invented data. | Implement the limited patient-owned profile contract and persisted edit/read flow; hide deferred insurance/caregiver sections and do not fabricate access logs. |
| P1 | `/patient/doctors` and doctor detail (`PatientDoctorsConnected`, `DoctorProfile`, lines 768/434) | Demo doctors with numeric IDs, invented review totals, reviews, location and availability copy. | Reuse patient-safe doctor/slot queries already used by patient booking; add only the missing permitted profile/search contract. Do not expose the organisation-admin staff directory to patients. |
| P1 | Patient/reception/organisation notifications (`PortalShell`, line 395) | Static bell/dot with no inbox handler, unlike the real doctor notification component. | Implement only the role-scoped notification flows required by V1 and supported by recipient policy; backend projection/authorisation may need extending. No reuse of doctor-only permissions by implication. |
| P2 | `/doctor/doctors` (`DoctorDirectory`, line 234) | Demo doctors and legacy booking drawer. | Use the doctor collaboration directory for colleague discovery. Any booking/availability action must use the appropriate scoped Scheduling contract and UUIDs. |
| P2 | `/hospital/admin/overview` (`OrganizationCommandConnected`, line 788) | Demo departments/doctors/patients, estimated bed/occupancy/wait metrics and fixed facility/transfer text. | Render actual organisation identity and permitted administrative counts. Hide bed/transfer operations and derived fake metrics, which are outside V1. |
| P2 | `/hospital/admin/settings` (`HospitalSettingsPage`) | `aegis-hospital-settings` localStorage and seeded facility details. | Persist supported organisation profile settings through the owning service. If an update API is absent, implement it with active-admin authorisation; do not report local storage as a saved organisation change. |
| P1 | `/hospital/admin/logs` (`ActivityLogsConnected`, line 825) | Demo/local audit events, including exported JSON. Not the backend audit trail. | Integrate authorised Audit queries in Phase 7. Until then label unavailable or hide; never present editable browser events as security evidence. |
| P2 | `/hospital/admin/security` and `/statistics` (`HospitalSecurity`, `HospitalStatisticsConnected`, lines 722/831) | Local policy controls/sample security records and demo-derived charts. | Connect only supported account/session security and safe administrative metrics. Disable unsupported policy writes; advanced analytics remain deferred. |
| P2 | `/platform/overview`, `/users`, `/appointments` (`AdminOverview`, `PeopleAdminConnected`, `AppointmentAdminConnected`, lines 466/838/836) | Hardcoded metrics/charts; demo patients/doctors relabelled as accounts; demo appointment table. | Use actual platform account/organisation/security projections where in scope. A global appointment table is not permission to reveal clinical/patient information to platform administrators. Hide unsupported data. |
| P1/P2 | Other platform controls: audit, settings, flags, integrations, support/incidents, verification | Sample arrays, localStorage state, local replay/approval actions, or no-op buttons. | Real audit/security operations need authoritative APIs and permission checks; disable fake operational claims. External licence/document verification is explicitly deferred, not an unfinished V1 backend to build. |

Additional source: [hospital settings and operational prototypes](../frontend/src/features/workflows/HospitalWorkflowPages.tsx).

## Already connected: preserve these implementations

These visible routes use dedicated real adapters. Their presence does not
make the surrounding demo dashboards real, nor does this audit substitute for
their live acceptance tests.

- Authentication: registration, verification, login, session restoration and
  active-organisation selection.
- Platform organisation creation/listing and administrator assignment.
- Organisation departments, staff invitations/access, staff directory,
  administrator clinician directory, and doctor professional profile.
- Reception administrative patient directory/registration, duplicate checks,
  and patient-account linking.
- Doctor availability and real doctor/reception appointment lifecycle pages.
- `/patient/appointments`: patient registration resolution, safe doctor/slot
  lookup, own booking/history/status. The old `PatientVisitsConnected` is not
  the component mounted on this route.
- `/doctor/clinical`: drafts, finalisation, append-only corrections and
  protected medical-file upload/download through `MedicalFilePanel`.
- `/doctor/messages`: REST/WebSocket doctor conversations; the doctor
  notification centre also has REST recovery and live unread updates.

Do not mistake unused legacy functions such as `DoctorAvailabilityConnected`,
`AccessControlConnected`, `ReceptionAppointmentsConnected`, or the old
`Appointments`/`Messenger` components for the active real routes. In
particular, `/hospital/admin/access` mounts `OrganisationStaffInvitationsPage`,
not the local permissions prototype.

## Deferred prototype surfaces

Hide/feature-flag these route families for internship V1, retaining reusable
design assets: laboratory/radiology results and orders, pharmacy/refill
operations, patient billing/reviews/full care messaging, hospital beds,
admissions/transfers/discharges, nursing/staff operational workspaces, claims,
budgets/finance, advanced analytics, and external licence verification.

Patient prescriptions/documents are optional only after the core limited
portal is stable. A static “Results & documents” page does not prove private
File Service integration. The real doctor's file panel is a separate feature.

## Recommended repair sequence

1. Fix the auth/cache/provider boundary and add account/organisation-switch
   regressions before demonstrating real sensitive data.
2. Close the misleading duplicate workflows: real reception check-in and
   real Clinical encounter navigation. Keep the approved design.
3. Build the Phase 6 referral workspace on the existing Communication
   lifecycle plus the new Clinical/File shared-read APIs. Acceptance must show
   one selected item only, followed by denial after revocation/expiry.
4. Replace active V1 dashboard, patient identity/profile, doctor search and
   notification placeholders using role-safe APIs; add missing scoped DTOs
   deliberately, not broad record access.
5. Hide deferred navigation and finish authoritative audit/security surfaces
   in Phase 7. Every active action must have a server mutation or an honest
   unavailable state.

Each repaired screen should have loading/empty/error coverage, a successful
mutation followed by a fresh read, wrong-role/wrong-organisation denial tests,
and a browser check against Gateway. Preserve the existing visual language;
this is data integration work, not a redesign.
