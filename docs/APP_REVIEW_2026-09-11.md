# Sahha application review and reconciled backlog

Reviewed: 2026-09-10 to 2026-09-11; multi-role/permission follow-ups 2026-09-12;
Audit persistence and health/readiness follow-ups 2026-09-13; final native health,
API conventions and selected referral previews 2026-09-14; isolated database
bootstrap/reset and identity/staff seed verified 2026-09-15; native dependency
restart/refusal gate verified 2026-09-16; clinical/file/messaging repeats and the
Notification cookie repair verified 2026-09-18; live browser messaging verified
2026-09-19; ordered retained-data setup and Windows PID-reuse cleanup verified
2026-09-20. Scope: internship V1, approved frontend,
implemented service contracts, organisation/resource boundaries, events, tests
and local operations. No Docker, cloud provisioning or deferred clinical modules.

This is an app-wide source/contract/test review, not a production security
certification or a claim that the complete multi-doctor journey has passed.
Historical live acceptance is distinguished from this task's new checks.

## Findings and repairs

| Priority | Finding | Evidence and disposition |
| --- | --- | --- |
| Medium - fixed | Windows recycled parent PIDs could incorrectly block owned Java shutdown. | An unrelated browser helper predated the synthetic JVM by two days but retained its now-reused parent PID. Creation-time-aware child selection excludes that older process; exact JAR/run-marker checks and refusals for unknown contemporary children remain. Three new regressions, real ownership-checked cleanup and the subsequent complete 16-stage run pass. No unrelated process is stopped. |
| High - fixed | Communication's real browser stream could not complete its handshake. | Its origin was hard-coded, the generic conversation-ID REST handler shadowed /conversations/ws, and the raw CSRF override had the wrong Spring bean name. Exact configurable origins, explicit WebSocket mapping priority and the correctly named cookie-bound CSRF interceptor fix these defects without relaxing role, membership, origin or subscription checks. Full-context routing and five actual socket regressions pass; Communication/shared-session package passes 113 tests. |
| Medium - fixed | The messenger could retain stale history after a socket-only interruption while HTTP stayed online. | onConnected now invalidates the active organisation's conversation list and selected history. The added component regression, all 226 frontend tests and production build pass. The live browser gate verifies a genuine socket close, new subscription and REST-history refresh without taking HTTP offline. |
| Medium - fixed | Notification ignored Auth's configured access-cookie name and secure-cookie setting. | Its shipped properties now prefer AUTH_ACCESS_COOKIE_NAME and AUTH_COOKIE_SECURE, retaining legacy aliases as fallbacks. Verified with five new binding/cookie regressions, all 114 Notification/shared-session tests, a rebuilt JAR and 51/51 real Gateway/Kafka messaging checks. A tooling contract guards all nine browser-facing APIs. |
| High | Some Clinical author routes trusted historical ownership and a signed doctor claim without checking current membership. | `ConsultationService.find/updateDraft`, `ClinicalRecordService` and `ConsultationAttachmentAccessService` lacked a live author gate. Fixed by `ConsultationAuthorAccessService`, invoked for both public author endpoints and the internal File attachment-context endpoint. Checks original membership identity, fails closed on authority outages and audits denied decisions in a separate transaction. Six new HTTP regression tests pass. |
| High — fixed | Domain-wide session/account revocation was not consistently enforced. | Auth now reads its own current session/account/platform-role state rather than trusting positive Redis projections. Gateway and all seven implemented resource services consult its uncached internal decision after local JWT checks. Captured-token, stale-cache, direct-service and real WebSocket transport regressions pass; resource authorization remains separate. See `SESSION_SECURITY.md` for outage and in-flight-request boundaries. |
| Medium | A fresh frontend checkout could silently select mocks and an unversioned API path. | `frontend/src/config/env.ts` used opt-out mock flags and `/api`; the example still named Aegis. Fixed: real adapters by default, `/api/v1`, explicit mock opt-in, Sahha example and a Vite Gateway-only HTTP/WebSocket proxy. Two new configuration tests and the full frontend suite pass. |
| Medium | Config Server ran with an empty repository and clients did not consume it. | Added public operational defaults, ten Gateway/domain Config Clients and a required `platform` import. Tests remain independent with Config Client disabled. Six foundation processes report ready; all four foundation domain/Gateway clients register `config=native-v1` in Eureka. Missing required Config Server fails startup. |
| Medium | Root lifecycle automation was absent and manual Java processes were difficult to manage safely. | Added `scripts/sahha.mjs` build/test/check/start/status/stop. Preflight refuses unmanaged ports, process control is locked, and shutdown checks exact JAR plus unique launch marker. Live testing exposed Oracle's Java shim and Windows console hosts; runtime resolution and constrained child handling now cover these. No database reset or infrastructure installation is implied. |
| Medium — fixed | Backend permissions were not defined independently from roles. | Nine service/edge catalogues now define 47 operation authorities, derived from explicit global or active-organisation roles. HTTP and WebSocket entry points enforce permissions; live membership/resource/consent checks remain required. No custom permission editor, database or trusted client permission claim was introduced. See `BACKEND_PERMISSIONS.md` and the full native verification below. |
| Medium — fixed | Multi-role users could be restricted to one frontend workspace. | REST sessions, route guards and the workspace selector now use all explicitly assigned active-organisation roles plus global platform roles. The administrative default landing page no longer blocks an assigned Doctor role; administrator-only users remain denied. Doctor overview/patients/schedule/clinical-queue observers select only their own appointments without narrowing the shared administrative response. Login deep links, organisation changes, role removal, cache/draft disposal and mobile navigation have regression coverage. |
| Medium — installed-machine sequence fixed, fresh setup open | Repeatable synthetic seed/reset and fresh-machine provisioning were missing. | Nine restricted databases, recoverable generation reset, native dependencies and all twelve application smoke checks pass. The ordered 16-stage retained-data demo now passes, covering identity/staff, scheduling, Clinical, files, messaging, referral denials and browser recovery while preserving 58 identifiers. Fresh-checkout/empty-generation and clean-machine acceptance remain open. See `SYNTHETIC_BOOTSTRAP.md`. |
| Medium — persistence fixed, central delivery open | Audit lacked owned persistence. | Audit now has metadata-only Flyway V1, append-only and source-event uniqueness constraints, guarded isolated-schema tests and a closed HTTP policy (50 Audit tests, 12 runner tests and native startup verified). No central consumer/query API or Gateway route; existing domain audits/outboxes are not central Audit completion. Central ingestion/query/security remains Phase 7. See `AUDIT_PERSISTENCE.md`. |
| Medium — fixed | Readiness policies were incomplete and probe security depended on business authentication. | All twelve applications ship explicit local policies and operational-only access rules, with 154 policy tests and 108 packaged native assertions passing. Required dependencies affect readiness, never liveness; optional aggregate failure no longer blocks the native runner. The final Gateway/Audit checks passed individually on 2026-09-14 and helpers were stopped. See `HEALTH_READINESS.md`. |
| Medium — fixed | Framework errors and generated API security requirements were inconsistent. | Eight APIs now preserve protocol-error status with safe correlated Problem Details; Gateway handles its own discovery/connection/timeouts without rewriting downstream errors. Generated OpenAPI requires the declared cookie/CSRF/file credentials together, excludes internal endpoints and documents correlation/errors. Imported defaults suppress request/SQL/error-detail logging. All 177 new API tests and full regression pass; comprehensive Phase 7 observability/privacy acceptance remains open. See `API_CONVENTIONS.md`. |
| Medium | Agreed treatment-referral access is not implemented. | Current `CreateReferralRequest` and grant contracts cover explicitly selected resources. They do not model separate care participation or same-organisation shared treatment. Do not broaden recipient reads until acceptance, consent, independent participation, expiry/revocation and immutable originals are enforced server-side. |
| Medium — preview fixed, workflows open | Several Phase 6 screens/actions remain incomplete integrations. | Recipients now explicitly open selected finalised Clinical content and protected File downloads through existing grant-checked APIs. Payloads are memory-only and clear on denial, expiry and context/lifecycle changes. Frontend/API-contract regressions pass; the full live two-doctor journey is still open. Referral notification consumption/projection and protected message attachments remain unimplemented. |
| Low | Tracker and context contained obsolete completion statements. | Reconciled verified branding/deferred navigation/domain routing/token checks. Labelled the detailed early context as historical. Removed Docker instructions from the current Redis guide and replaced active container requirements with native/Azure delivery requirements. |

## Active frontend coverage

| Area | Current integration | Remaining work |
| --- | --- | --- |
| Authentication/profile/devices | Real account APIs, validated saves, session restoration, CSRF, per-device controls, request/cache isolation and authoritative cross-service session invalidation. | Production load/network hardening remains later work. |
| Organisation/platform administration | Real organisation/departments/memberships/invitations/profiles; administrative DTOs, service-owned operation permissions and server checks. Synthetic identity/staff bootstrap passes real Gateway/repeat/denial checks. | Remaining full-platform/clean-machine operational gates. |
| Reception/patient registry | Real search, registration, duplicates, schedules, bookings and check-in. | End-to-end regression/clean-machine demonstration remains a delivery gate. |
| Doctor workspace | Real appointment-linked patient summaries, availability, clinical records, finalisation/corrections and protected files; explicitly assigned multi-role navigation and doctor-owned appointment views. | Broader shared care and final live Phase 6 acceptance. |
| Messaging | Real same-organisation conversations, participant-scoped immutable messages and recoverable notifications. | Protected message attachments and final live Phase 6 acceptance. |
| Referrals | Real author-owned finalised source discovery, consent-aware creation, participant lifecycle and exact selected Clinical/File recipient previews. | Second-opinion/shared-treatment distinction, referral notifications and live two-doctor/Gateway/Kafka acceptance. |
| Patient portal | Real authentication, profile, doctor search and appointment request/history/status. | Patient notification integration; optional prescription/document visibility still requires a scope decision. |
| Deferred Aegis assets/routes | Preserved as reference/reusable UI; unavailable routes are not enabled V1 features. | No laboratory, pharmacy, beds, billing or advanced hospital-module implementation. |

Patient-portal work already delivered is not marked missing merely because Phase
7 has not started. Conversely, a static placeholder or a synthetic browser
intercept is never counted as a completed backend feature.

## Validation evidence

2026-09-20 ordered setup follow-up: all 16 stages pass in 43 minutes. The report
verifies retained generation/58 identifiers, consistent source/artifact inputs,
per-stage cleanup and both 45-assertion browser replays. All 136 default tooling
tests pass (one opt-in native test skipped by default); the separate native
refusal/failure-cleanup test passes. The initial run's messaging cleanup failure
remains recorded; its repaired rerun is separate evidence. Final audit finds no
project listeners, owned runtimes, application ownership records or operation lock;
shared PostgreSQL is retained. Fresh empty-generation and clean-machine acceptance,
shared care, central Audit/hardening and CI still prevent cloud readiness.

2026-09-19 browser messaging follow-up: the real Chromium/Gateway/Kafka gate
passes 48 assertions with separate participant/unrelated-doctor sessions and
desktop/mobile viewports. Actual recipient message and metadata-only notification
frames, durable history/inbox, socket-only and page recovery, unrelated-user
denials and organisation-switch cleanup are verified. No response/frame is
mocked. A prior interrupted, uncommitted command was reconciled only after fresh
authorised history proved empty; its ID remains journalled and no message was
replaced. All 119 tooling tests pass. The separate full process-restart recovery
passes 45 assertions against the same message/notification, with no duplicate.
Final audit: zero project listeners and owned synthetic runtimes; shared PostgreSQL
5432 and unrelated IDE tooling are retained. This does not complete shared care,
referral notifications, attachments, full demo or cloud-readiness gates.

2026-09-18/19 selected-sharing follow-up: fixed the post-finalisation handoff gap.
Conversation/referral commands can explicitly reference an owned finalised source;
Clinical rechecks the original live author membership and exact patient/tenant.
Source references are immutable, idempotency-bound and private; no patient-wide
care or recipient grant is inferred. The existing referral form sends the source,
and the messaging form adds an optional paginated metadata-only selector with
context disposal and safe command retries. Regression passes 186 backend tests,
225 frontend tests, the production build and 108 tooling tests. Native acceptance
passes 81 Gateway assertions in three serial batches (28/29/24), including selected
Clinical/File reads, unselected denials, expiry, revocation and an already-issued
download token. The final restart proves Clinical still denies the revoked grant.
All 34 desktop/mobile browser-contract scenarios pass, including the source picker;
the final port audit finds zero project listeners and retains shared PostgreSQL.
This does not close live browser/WebSocket, shared treatment,
referral notifications, attachments, central Audit, hardening or delivery gates.

2026-09-15/16 follow-up: the 288-second identity/staff native gate passes 57 initial
and 51 repeat Gateway assertions, exact row counts, credential/aggregate
preservation, logout and unsafe-input refusals. Auth/session regression passes
201 tests. Phase 2 V1 is complete; public applicant onboarding remains deferred.
The 148-second native dependency gate passes Redis/Kafka/SeaweedFS protocol and
restart checks, retained identities/sentinel, occupied-port/edited-config refusal,
exact PID-owned loopback socket checks and cleanup after injected failure. The
unused SeaweedFS Iceberg listener is disabled. All eleven helper ports stop.
2026-09-17 follow-up: all four isolated application batches pass (243 operational
assertions, twelve distinct services), including Kafka-enabled collaboration.
Missing Boot Kafka starters in Auth/Organisation/Patient were fixed; the affected
modules/shared session pass 346 regression tests and package successfully.
Patient/appointment seed passes 40 initial and 32 fresh-process repeat Gateway
assertions. Clinical recovery passes 31 Gateway/Kafka assertions, including real
appointment completion, immutable-write denial and author-only record access.
All 94 tooling tests pass. Protected-file first/repeat and real messaging-inbox
acceptance remain in progress/pending; no cloud-readiness claim follows.

2026-09-18 follow-up supersedes those pending file/messaging checks: clinical
fresh-process repeat passes 31 assertions. Primary-file recovery/repeat passes
22/20; a fresh second-document upload/repeat passes 24/20 and the original file's
post-upload preservation repeat passes another 20. Real messaging recovery/repeat
passes 51/51, preserving the original conversation/message/private notification.
The live custom cookie namespace exposed the Notification configuration defect
above. Full Notification/shared-session verification passes 114 tests and packages
Notification; 101 tooling and 13 focused frontend messaging/notification tests pass.
No frontend source/design or clinical access policy changed. Synthetic scan only,
not malware-scanner validation. Live browser/WebSocket, patient-context/referral,
shared care, central Audit, remaining hardening and CI/clean-machine acceptance
still prevent cloud readiness. All owned helpers stop; shared PostgreSQL remains.

2026-09-15 database follow-up: all 34 tooling tests and the opt-in real native
migration/reset/recovery gate pass. All nine service migrations apply and validate
again using individual database-owner logins. Each access matrix verifies 101
decisions, including 72 cross-database denials, marker-write denials and an invalid
password. Forty-five lifecycle assertions prove occupied-port/running-reset
refusal, fresh identity, clean new data and recovery of a retained old sentinel.
Temporary databases stop automatically. Three synthetic directories remain,
about 705 MiB including pinned migration libraries/cache; no data was deleted.
The final artifact-fingerprinted runtime rerun validates all 37 migrations across
nine services, with zero pending. The cache cannot mix different packaged
dependency sets and is shared across generations without sharing credentials.

All project application/infrastructure listeners are closed. Memurai had
auto-started; it had no clients and was gracefully stopped with SAVE after Windows
denied service-manager access. Shared PostgreSQL and unrelated processes remain.
The database-bootstrap slice changed no service production source, schema or
frontend. The full-application regression evidence below remains dated, not a new
run; the 2026-09-18 Notification repair has its own targeted verification above.

2026-09-14 follow-up: full native Maven `verify` succeeded across all 14 reactor
projects and packaged all twelve applications. Of 889 discovered tests in 168
suites, 888 passed, none failed/errored, and one existing opt-in SeaweedFS test was
skipped. The 177 new API tests cover protocol status, safe errors/logging,
correlation, actual generated OpenAPI and random-port Gateway errors. One new Auth
HTTP regression switches between Doctor and Receptionist in two organisations,
checking exact current JWT/session roles and rejection of pre-switch tokens;
Organisation's authoritative directory is mocked, not a seeded live-role journey.
Existing Clinical administrative-role and selected-sharing denials pass.

Frontend: 216 tests in 43 files, TypeScript and production build pass. The 35 new
preview/adapter/recovery tests cover exact content, unselected/malformed responses,
file-token/path boundaries, read-only corrections, denied recovery, expiry and
late responses. All 32 synthetic desktop/mobile browser scenarios pass, including
the new protected preview/download/denial/retry/completion assertions. Intercepts
test the real React UI against synthetic Gateway contracts, not live services.
Screenshots/report remain in `%TEMP%/sahha-frontend-repair-check`.

Native operations: all 108 packaged health assertions across twelve applications
and all 16 tooling tests pass. Temporary Redis/Vite/browser helpers are stopped,
all twelve application ports and Redis/Kafka/storage/frontend ports are clear;
shared PostgreSQL is retained for unrelated databases. Existing data is untouched.
`git diff --check` passes. Cloud readiness is not achieved by this repair batch.

2026-09-12 backend permission follow-up: full native reactor `verify` succeeded
across all 14 projects and packaged all twelve applications plus the shared
technical library. Of 508 tests, 507 passed, zero failed/errored and one opt-in
live SeaweedFS adapter test was skipped (storage code unchanged; no storage
server started). There are 89 new regressions: 86 mapping/mechanics tests and
three HTTP boundary/multi-role cases. Existing session, live resource-membership,
CSRF, clinical integrity, sharing/revocation, file protection and real WebSocket
transport tests pass. This is not a full live multi-doctor/Kafka acceptance run.
The disposable Redis helper was stopped; project ports/processes are clear and
the shared PostgreSQL server was preserved. `git diff --check` passes.

2026-09-12 frontend follow-up: 181 tests in 41 files pass with `--maxWorkers=2`;
TypeScript/production build pass. Coverage includes real REST-to-provider/route
integration, admin-only clinical denial, explicit role unions, safe login return
paths, organisation switching, role removal on renewal/cross-tab restoration,
clinical cache/draft disposal and preservation of administrative appointment
responses. No backend code or dependency was changed; the backend evidence below
is historical, not a new full live acceptance run.

All 32 synthetic browser contract/layout checks pass at 1440/375 widths,
including the 28 retained checks, multi-role admin/doctor/reception navigation
and administrator-only clinical denials. Report/screenshots:
`%TEMP%/sahha-frontend-repair-check`. These are intercepted Gateway responses,
not live backend/Kafka acceptance. Temporary Vite, browser and test helpers were
stopped afterwards; all project services/infrastructure remain stopped except
the deliberately preserved shared PostgreSQL server. No existing data removed.

2026-09-11 session-security follow-up: full native reactor `verify` passed 418 tests
and packaged all twelve apps plus the technical library. A final Notification
rerun passed all 39 tests (including one new real WebSocket revocation regression)
and all 22 shared-library tests: 419 distinct current backend tests verified,
zero failures/errors/skips. Auth's 130 include stale Redis and persisted-state
revocation; production decoder wiring is asserted in every client context.
Frontend: 146 tests pass with `--maxWorkers=2`, typecheck/build pass. Native runner:
11 tests pass. An earlier unrestricted concurrent frontend run had four failures;
no frontend source change was needed for the passing capped-worker rerun.

Requested shutdown after this follow-up: both frontend servers, the Redis helper
and native SeaweedFS are stopped. All backend/discovery/config and Kafka ports
are closed; the Memurai Windows service is stopped. Shared PostgreSQL remains
running because it also hosts `userdb` and `mydb`; broader shutdown was not
confirmed. Unrelated Java processes and all existing data were preserved.

Earlier review-batch evidence (historical):

- Baseline full Maven reactor: 366 passed, one explicitly opt-in storage test
  skipped. After repairs: 374 tests pass, zero failures/errors/skips, across the
  resumed/split reactor verification. The six new Clinical tests cover suspended
  membership, replacement membership, unavailable authority, read audits, denied
  mutations and organisation/administrative-role boundaries.
- Live native SeaweedFS adapter round trip passes. Its own unique synthetic test
  object was removed afterwards; existing files were not deleted.
- Frontend: 146 tests in 39 files, typecheck and production build pass.
- Native runner: 11 unit/contract checks pass. Root backend build packages all
  twelve executable JARs. Six foundation services reached aggregate/readiness
  UP and registered clients consumed remote configuration. Shutdown retained all
  data and released the six managed service ports. Corrected-runtime restart
  passes: each recorded Java PID is exactly the PID listening on its service
  port, and all six applications are ready with the expected configuration.
- Live frontend proxy: `/api/v1/auth/csrf` through Vite returns HTTP 200, JSON and
  the expected CSRF contract; tokens were not copied to the report.
- Existing synthetic browser repair checks: 28 pass across 1440/375 widths,
  including profile saves, referral acceptance and creation review/send/draft.
  These intercept Gateway contracts; they do NOT replace live backend/Kafka
  end-to-end acceptance. Existing frontend processes were not stopped.
- `git diff --check` passes. Native tests use isolated service test databases/
  schemas and namespaced/unique synthetic state. No Docker engine or cloud
  resource was started for these checks.
- Cleanup: temporary validation JVMs and Redis helper are stopped, with their
  ports released. Existing frontend, PostgreSQL and SeaweedFS processes and all
  application data are preserved.

## Work order after this repair batch

1. Complete live browser/WebSocket verification and the full demo.
   Isolated application batches, patient registration/appointment and clinical
   recovery checks now pass. Database
   provisioning, migrations, recoverable reset, identity/staff seed and native
   Redis/Kafka/storage restart/refusal checks now pass.
   Protected-file/messaging repeats and the selected-sharing/source/expiry/
   revocation API journey now pass; do not repeat them as if still missing.
   Preserve existing data and stop helpers after small verification batches.
2. Implement the agreed same-organisation second-opinion/shared-treatment model:
   A remains treating in a second opinion; both treat after accepted treatment
   referral. Track each doctor's involvement separately and preserve independent
   legitimate access when another involvement ends.
3. Complete message attachments, referral notifications and live two-doctor
   Phase 6 acceptance/denials, including the implemented recipient previews.
4. Continue Phase 7 hardening/patient notifications/central Audit, then Phase 8
   CI, full journey automation and explicitly authorised non-Docker Azure rollout.

No older phase or Phase 6 is declared complete solely because this review batch
passes its checks. The living tracker remains the completion authority.
