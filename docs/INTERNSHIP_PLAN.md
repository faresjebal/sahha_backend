# Sahha internship implementation plan

Last updated: 2026-08-05

Status values: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `COMPLETE`

## 1. Working agreement

This is the living implementation tracker for Sahha Version 1.

It must be updated after every code, configuration, schema, infrastructure,
test, or material documentation change. An item is marked complete only after
its exit criteria are verified.

No application code was generated during the initial context and planning
task.

## 2. Progress dashboard

| Phase | Outcome | Status |
| --- | --- | --- |
| 0 | Context, scope, baseline audit, and living plan | COMPLETE |
| 1 | Repository, frontend migration, local infrastructure, and service foundation | IN PROGRESS |
| 2 | Authentication, active organisation, departments, memberships, and roles | IN PROGRESS |
| 3 | Administrative patient registry and duplicate detection | IN PROGRESS |
| 4 | Doctor availability, appointments, check-in, and real-time appointment notifications | NOT STARTED |
| 5 | Consultations, diagnoses, medication, finalisation/corrections, and protected files | NOT STARTED |
| 6 | Doctor messaging, referrals, selected-data sharing, revocation, and expiry | NOT STARTED |
| 7 | Limited patient portal, audit completion, security hardening, and observability | NOT STARTED |
| 8 | Full end-to-end verification, CI/CD foundation, and internship demonstration | NOT STARTED |

Current phase: `Phase 3 — Administrative patient registry`

Current next task: start Auth, Organisation, Patient, Gateway, and the React
frontend, then verify the live receptionist registration, duplicate review,
directory, tenant isolation, and clinical-field exclusion workflow through the
browser and Gateway.

## 3. Phase 0 — Context and baseline

Status: `COMPLETE`

Completed:

- [x] Capture the internship product definition, users, workflows, constraints,
      technologies, boundaries, and success criteria.
- [x] Inspect the Sahha backend scaffold.
- [x] Inspect the existing frontend source and its integration contract.
- [x] Identify the mismatch between the broad frontend and focused internship
      scope.
- [x] Establish the existing frontend as the approved UX baseline.
- [x] Create durable repository guidance and a living implementation plan.
- [x] Record the planned service boundaries and cross-service rules.

Validation evidence:

- Sahha contains only four generated Spring modules and no functional domain
  implementation.
- The external frontend contains role-aware routes, mock workflows, partial
  REST adapters, typed contracts, and tests.
- No application source was changed.

## 4. Phase 1 — Repository and platform foundation

Status: `IN PROGRESS`

Goal: produce a reproducible local platform on which vertical slices can be
built safely.

Tasks:

- [x] Initialise or confirm the Sahha Git repository and add an appropriate
      `.gitignore`.
- [x] Flatten or standardise the generated nested module layout.
- [x] Decide and document Maven structure: aggregator parent plus independently
      deployable services.
- [x] Define and create the standard source, resource, and test package
      structure for all current services.
- [x] Verify Java 21 and the selected Spring Boot/Spring Cloud versions by
      resolving dependencies and running the current module context tests.
- [x] Migrate the frontend from `C:\Users\LENOVO\Desktop\codex` into
      `sahha/frontend`, excluding generated/dependency/repository files.
- [x] Preserve a working mock-mode frontend before any API integration.
- [ ] Rebrand runtime names and visible Aegis identity to Sahha.
- [ ] Feature-flag or remove deferred modules from V1 navigation without
      discarding reusable UI assets.
- [x] Add Config Server as an infrastructure component.
- [x] Enable and configure Eureka Server.
- [x] Configure Gateway discovery, Auth/JWKS routing, request IDs, safe CORS,
      stateless browser-cookie forwarding, baseline security headers, and
      RS256 access-cookie validation at the edge.
- [ ] Add the remaining `/api/v1` domain-service routes as their vertical
      slices are implemented.
- [x] Create service skeletons for Organisation, Scheduling, Clinical,
      Communication, Notification, File, and Audit.
- [x] Add one shared IntelliJ run configuration per application plus
      infrastructure and all-service compound configurations.
- [ ] Add standard health, readiness, logging, Problem Details, OpenAPI, and
      resource-server conventions. Auth now has verified Problem Details and
      OpenAPI/Swagger; the cross-service convention remains pending.
- [ ] Create Docker Compose for PostgreSQL, Kafka, Redis, MinIO, Eureka, Config,
      gateway, and the initial services.
- [x] Provision separate local PostgreSQL databases, restricted login owners,
      and credentials for every stateful internship service.
- [ ] Configure each stateful service to use its owned database and validate
      its schema through Flyway.
- [x] Connect Auth Service to `sahha_auth`, validate its schema through Flyway,
      and give automated tests an isolated guarded `sahha_auth_test` database.
- [x] Establish the initial Flyway convention in Auth Service.
- [x] Establish the initial transactional-outbox template in Auth Service.
- [ ] Add root developer commands/documentation for build, test, start, stop,
      reset synthetic data, and inspect services.

Exit criteria:

- [ ] A new developer can start the infrastructure from documented commands.
- [ ] Gateway, Eureka, Config Server, Auth, Organisation, and Patient services
      report healthy.
- [ ] Services register with Eureka and receive externalised configuration.
- [x] The migrated frontend runs in mock mode from `sahha/frontend`.
- [x] Baseline backend tests and frontend typecheck/tests/build/browser checks
      pass.
- [ ] Docker health checks pass.

Validation evidence:

- Git repository initialised on `main`; no commit was created.
- Four nested generated modules were flattened without discarding their files.
- Root Maven parent/aggregator and Maven Wrapper cover 12 independently
  runnable applications: Discovery, Config, Gateway, Auth, Organisation,
  Patient, Scheduling, Clinical, Communication, Notification, File, and Audit.
- The generated Windows Maven Wrapper was patched to handle a normal
  non-symbolic-link Maven home path in Windows PowerShell.
- Java `21.0.12`, Maven Wrapper `3.9.16`, Spring Boot `4.1.0`, and Spring Cloud
  `2025.1.2` resolved successfully.
- `.\mvnw.cmd test`: `BUILD SUCCESS`; all 13 reactor projects succeeded and
  12 application-context tests passed.
- `.\mvnw.cmd package -DskipTests`: `BUILD SUCCESS`; all 12 applications were
  repackaged as executable Spring Boot JARs.
- Startup smoke checks: all 12 packaged applications reached their Spring
  Boot `Started` state and all temporary test ports were released.
- IntelliJ now imports the root `pom.xml` instead of the removed nested Auth
  POM and targets Java language level 21.
- Fourteen valid shared run configuration files exist: 12 individual
  applications and two compound configurations.
- PostgreSQL `18.1` is running locally on port `5432`.
- Nine service-owned databases and nine restricted login owners were created
  for Auth, Organisation, Patient, Scheduling, Clinical, Communication,
  Notification, File, and Audit.
- All nine roles connected successfully to their own databases; all nine
  cross-service connection checks were denied.
- All service roles are non-superusers without database, role, or replication
  creation privileges, and public database access is revoked.
- Generated local database credentials are stored only in the gitignored
  `.env.database.local`; the PostgreSQL administrator credential is not stored
  in the repository.
- Auth Service uses environment-backed local/test datasource configuration,
  Flyway migrations, `ddl-auto=validate`, UTC JDBC timestamps, and disabled
  Open EntityManager in View.
- `sahha_auth_test` and its restricted login are isolated from
  `sahha_auth`; its test suite refuses to clean any other database.
- Auth Flyway migrations `V1__create_user_accounts.sql`,
  `V2__create_platform_roles.sql`, and
  `V3__create_user_sessions_and_refresh_tokens.sql`, and
  `V4__create_verification_tokens.sql`, and
  `V5__create_security_events_and_auth_outbox.sql`, and
  `V6__index_session_retention.sql`, and
  `V7__add_active_organisation_context.sql` applied successfully; the test
  schema reports version `7` and development advances on the next Auth start.
- Auth now contains `UserAccount`, `AccountStatus`,
  `UserAccountRepository`, and `EmailNormalizer`, with UUID identity,
  normalized-email uniqueness, state/lock/credential fields, UTC timestamps,
  database checks, indexes, and optimistic locking.
- Auth also contains `PlatformRole`, `UserPlatformRole`, and their
  repositories. Flyway seeds only `PLATFORM_ADMIN`; assignment uniqueness,
  lifecycle consistency, optional assigner attribution, and optimistic locking
  are enforced without storing organisation roles in Auth.
- Auth now contains `UserSession`, `SessionStatus`, `RefreshToken`, and their
  repositories. Session IDs are token-family IDs; raw tokens are absent;
  same-user/same-family lineage, unique hashes, one active token per family,
  expiration/revocation consistency, and optimistic/pessimistic concurrency
  foundations are enforced.
- Auth now contains `VerificationToken`, purpose-scoped issuance/consumption,
  secure token generation and hash-only persistence, configurable BCrypt and
  password policy, registration/verification/reset services, account-state
  enforcement, failed-login counting, and timed locking.
- Auth now applies Redis-backed, SHA-256-address-keyed throttling to sensitive
  public authentication flows. A bounded local fallback protects a single
  instance during Redis outages while account lock state remains authoritative
  in PostgreSQL.
- Authenticated accounts can list safe active-session metadata, revoke one
  owned session, change their password after proving the current password, and
  revoke every device session through that change.
- Persisted `PLATFORM_ADMIN` authority protects suspension, reactivation, and
  disablement hooks. The service rechecks the database role, denies
  self-targeting, advances credential state, and immediately revokes target
  sessions for suspension or disablement.
- Authentication activity is stored in append-only `security_event` rows with
  one same-transaction `auth_outbox_event`. The conditional Kafka publisher
  marks rows only after broker acknowledgement and retains failed rows with
  bounded retry metadata; event/outbox payloads exclude passwords and raw
  tokens.
- Auth account, verification-token, and email services are grouped under
  `useraccountservice`, `verificationtokenservice`, and `emailservice`.
- Public `/api/v1/auth` endpoints now cover registration, email confirmation
  and resend, password-reset request and confirmation, validated DTOs, safe
  Problem Details, request IDs, generic accepted responses, and two-minute
  request cooldowns.
- Spring Mail provides a provider-independent asynchronous adapter configured
  for Brevo SMTP with plain-text/HTML authentication templates. The ignored
  local configuration now contains the supplied SMTP settings, enabled
  configuration fails fast when either credential is absent, and one live
  registration verification email was confirmed in the user's inbox.
- A controlled local-account reset proved that dependent verification/session
  data can be removed without changing the seeded `PLATFORM_ADMIN` definition,
  schema, Flyway history, or application code.
- Auth-focused verification: 115 tests passed with no failures, errors, or
  skips.
- Full `.\mvnw.cmd test`: all 13 reactor projects succeeded; 138 tests passed
  with no failures, errors, or skips.
- Gateway-focused verification: 19 tests cover cookie-only token resolution,
  JWT/JWKS validation, global-role conversion, discovery-aware routing,
  request-context sanitisation, CORS, safe errors, response cookies, and
  downstream cookie isolation.
- A real three-process smoke test routed Auth and its public JWKS through
  Eureka and Gateway, preserved exactly one canonical request ID, kept two
  independent browser cookie jars isolated, and rejected an unauthenticated
  protected request at the edge.
- Local Auth smoke verification returned `UP`, published one RS256 public JWK
  with no private key component, issued the `XSRF-TOKEN` cookie, and returned a
  CSRF response over HTTP `200`.
- A real local-profile request to
  `POST /api/v1/auth/password-resets/request` returned generic `202 Accepted`,
  preserved `X-Request-ID`, and left the development database unchanged.
- Auth started successfully with the real `local` profile after migrating its
  owned development database.
- 55 source-controlled package placeholders created across Gateway, Discovery,
  Auth, and Patient, with responsibilities documented in
  `docs/BACKEND_PACKAGE_STRUCTURE.md`.
- The empty misspelled Auth `sevice` directory was replaced by the tracked
  `service` package.
- 49 approved frontend files matched the original source by SHA-256 after
  migration; excluded dependency, build, log, generated, and repository files
  were absent.
- `npm ci`: dependencies installed from the lockfile.
- `npm run typecheck`: passed after the real Auth integration.
- `npm test`: 4 test files and 16 tests passed, including 11 focused real-Auth
  service/page tests.
- `npm run build`: passed; production assets emitted to ignored `frontend/dist`.
- `npm run validate:ui`: passed 11 prepared role/viewport checks, including
  mobile patient registration, with no
  document-level horizontal overflow and with keyboard/drawer checks.

Known foundation limitations:

- Patient still uses a temporary database-free local/test profile until its
  vertical slice begins.
- Native Memurai is the selected local Redis-compatible server so Docker
  Desktop and WSL do not consume development RAM. Redis tests use this server
  and clean only the Sahha Auth key prefix; PostgreSQL tests use the separately
  credentialed `sahha_auth_test` database. Container-backed CI remains a later
  portability gate.
- Gateway now routes Auth and its public JWKS through Eureka, validates RS256
  access cookies, forwards browser cookies without retaining shared client
  state, strips untrusted identity/forwarding headers, and applies safe CORS,
  request-ID, trusted client-IP, error, and security-header policies. Auth
  endpoint throttling is complete; general edge throttling, remaining domain
  routes, and each domain service's own resource-server checks are pending.
- The new domain-service modules are executable foundations only. Persistence,
  security, Kafka, OpenAPI, and their business APIs are added in their owning
  vertical slices.
- Other stateful service databases are provisioned but are not connected until
  their owning vertical slices begin.
- The migrated frontend intentionally remains Aegis-branded until the next
  isolated task.
- Frontend Auth is real while unfinished domain data remains mocked. Until
  Organisation Service supplies scoped memberships and profiles, a signed
  `PLATFORM_ADMIN` claim maps to the platform workspace and every other
  verified Auth account maps to the patient workspace. Display name/email
  metadata is a non-authoritative UI convenience; backend permissions never
  depend on it.
- The latest stable React Router is retained. The remaining npm audit advisory
  applies to React Server Components action handling, which this Vite
  `BrowserRouter` SPA does not use; PostCSS is overridden to its patched
  release.

## 5. Phase 2 — Identity, organisations, and authorisation

Status: `IN PROGRESS`

Goal: authenticate users and enforce role/membership rules in an active
organisation.

Tasks:

- [x] Define the Auth Service ownership boundary, persistence model,
      `UserSession` token-family design, Redis cache contract, security
      workflows, implementation milestones, and test gates in
      `docs/AUTH_IMPLEMENTATION_PLAN.md`.
- [x] Define canonical roles: `PLATFORM_ADMIN`, `ORGANIZATION_ADMIN`, `DOCTOR`,
      `RECEPTIONIST`, and `PATIENT`.
- [ ] Define permissions separately from roles.
- [x] Model global users, account status, credentials, and verified contact
      fields in Auth Service.
- [x] Persist the global `PLATFORM_ADMIN` role and user-platform-role
      assignments in Auth without introducing organisation-scoped roles.
- [x] Establish PostgreSQL persistence for per-device `UserSession`
      token-family aggregates and same-family hashed refresh-token lineage.
- [x] Establish BCrypt credentials, purpose-scoped single-use verification and
      reset tokens, account-state checks, failed-login counting, and timed
      account locking.
- [x] Add validated registration, email-verification/resend, and
      password-reset request/confirm HTTP contracts.
- [x] Add safe Problem Details, request IDs, secret-free responses, generic
      enumeration-safe acceptance, and per-account request cooldowns.
- [x] Add provider-independent Spring Mail delivery configured for Brevo SMTP
      with asynchronous plain-text/HTML authentication emails.
- [x] Model the core organisation profile, type, active/suspended status,
      contact/address metadata, audit metadata, and optimistic locking in
      Organisation Service.
- [x] Allow only a global `PLATFORM_ADMIN` to create an immediately active
      organisation and to list or read organisations.
- [x] Model organisation memberships, membership status, independently
      assignable organisation roles, identity snapshots, and uniqueness rules.
- [x] Model departments with organisation-scoped identity, status, audit
      metadata, uniqueness, and optimistic locking.
- [x] Model invitations and doctor/receptionist affiliation.
- [x] Model authoritative active-organisation selection and scoped role
      snapshots without copying organisation membership ownership into Auth.
- [x] Allow only a global `PLATFORM_ADMIN` to resolve an exact Auth identity
      and assign an active verified user as `ORGANIZATION_ADMIN` for one
      organisation.
- [ ] Implement applicant registration, documentary verification, and
      approve/reject processing after the internship's direct platform-create
      workflow is complete; this is currently deferred.
- [x] Allow an organisation administrator to invite, assign, suspend, or
      remove doctors and receptionists only inside an organisation where that
      administrator has an active admin membership.
- [x] Complete IP-aware authentication throttling and
      account-administration workflows.
- [x] Add owned-session listing/revocation and authenticated password change.
- [x] Add non-rotating current-session restoration and refresh only after
      access rejection or near access-token expiry.
- [x] Coordinate refresh across browser tabs with an exclusive Web Lock and
      an after-lock current-session recheck.
- [x] Purge expired refresh-token lineage in bounded batches after absolute
      session expiry plus retention while preserving audit-linked sessions.
- [x] Implement short-lived RS256 JWT access tokens and rotating refresh-token
      families in scoped HttpOnly cookies, with Redis/PostgreSQL-bound access
      validation and a public-only JWKS document.
- [x] Persist one `UserSession` per login/device and use its ID as the
      refresh-token family identifier.
- [x] Implement PostgreSQL-authoritative initial refresh-token issuance,
      atomic rotation, reuse compromise, current/global logout, device
      isolation, expiration, and password-reset session revocation.
- [x] Cache only a minimal, versioned session projection in Redis with bounded
      TTL, revocation tombstones, and PostgreSQL fallback.
- [x] Add SPA CSRF cookie/header protection and rotate the CSRF token after
      login, refresh, and logout.
- [x] Route Auth and its public JWKS through Eureka-aware API Gateway routing;
      validate RS256 access cookies at the edge with issuer, audience,
      token-type, session-ID, credential-version, and global-role checks.
- [x] Route Platform Administrator organisation APIs through Gateway and
      enforce `PLATFORM_ADMIN` at both Gateway and Organisation Service.
- [x] Validate Auth JWT issuer, audience, access-token claims, and global role
      again inside Organisation Service, with explicit CSRF enforcement for
      unsafe cookie-authenticated requests.
- [x] Implement active-organisation selection and renew the authenticated
      token/session context after a selection change.
- [ ] Validate tokens again in every domain service before resource-level
      authorisation.
- [x] Prevent arbitrary organisation IDs from overriding the authenticated
      token/session context; Organisation Service resolves membership from the
      authenticated subject before Auth can persist or sign the selection.
- [x] Implement platform-controlled Organisation Administrator assignment and
      membership list/read APIs.
- [x] Implement organisation-scoped department create, list, read, update,
      activate, and deactivate APIs for an active `ORGANIZATION_ADMIN`.
- [x] Complete and verify invitation-driven doctor/receptionist membership
      onboarding APIs.
- [x] Implement doctor professional profiles and doctor/receptionist
      membership suspension/removal management APIs.
- [x] Map the implemented frontend organisation roles/routes to canonical V1
      `ORGANIZATION_ADMIN`, `DOCTOR`, and `RECEPTIONIST` roles.
- [x] Integrate patient registration, email verification, login, session-first
      startup restoration, cross-tab renewal, logout, safe errors, and
      cookie/CSRF handling through Gateway in the React application.
- [x] Integrate the Platform Administrator organisation directory, details,
      and creation screen with the real Organisation Service contracts through
      Gateway.
- [x] Integrate Organisation Administrator assignment/list/read into the
      Platform Administrator organisation screen.
- [x] Integrate active-organisation discovery and selection into the React
      login/session flow, with cross-tab context restoration.
- [x] Integrate the Organisation Administrator department screen with the real
      Organisation Service through Gateway and organisation-keyed TanStack
      Query caching.
- [x] Integrate organisation-admin invitation management and invited-user
      accept/reject onboarding with the real Gateway contracts.
- [x] Integrate the Organisation Administrator staff/clinician directory,
      department placement, suspension/reactivation/removal controls, and the
      doctor-owned professional-profile form with the real Gateway contracts.
- [ ] Seed synthetic platform admin, organisation admin, doctors, and
      receptionists for demonstrations.
- [x] Emit and record Auth security events through an append-only local record
      and transactional Kafka outbox.
- [x] Persist organisation creation, its append-only audit record, and a
      minimal secret-free Kafka outbox intent in one transaction.
- [x] Document the Organisation Service organisation, administrator
      membership, and department contracts with OpenAPI/Swagger and validate
      them with database, HTTP, security, client, Gateway, and frontend tests.
- [x] Document the staff lifecycle and doctor-profile contracts in
      OpenAPI/Swagger and cover them with database, HTTP, security, Gateway,
      and frontend tests.

Security tests:

- [x] A non-platform user cannot create, list, or read platform-managed
      organisations.
- [x] A non-platform user cannot assign an Organisation Administrator, and an
      inactive or unverified account is rejected before persistence.
- [x] A Platform Administrator does not receive organisation staff-management
      or clinical access merely from the global platform role.
- [x] An Organisation Administrator cannot add or modify staff in another
      organisation.
- [x] Cross-organisation department access is hidden and denied.
- [x] Suspended memberships are denied even when an older access token still
      carries the organisation-administrator role.
- [ ] An organisation administrator cannot read clinical records by role alone.
- [ ] A user with multiple memberships receives the permissions of only the
      active context.
- [x] Refresh-token reuse revokes the affected token family.
- [x] Clearing or disabling Redis does not lose authoritative session or
      revocation state.
- [x] A token issued before an active-organisation change is rejected after
      the session context changes, and selection does not rotate the refresh
      token family.
- [x] Revoking one device session does not revoke an unrelated device session.

Current milestone validation state:

- The fresh 2026-08-05 unrestricted Maven run passed all 22 Gateway tests and
  all 36 Organisation tests with zero failures, errors, or skips.
- The passing Organisation suite covers Flyway version 6 and all seven
  migration resources, OpenAPI, staff invitations, doctor/receptionist
  affiliation, tenant hiding, department placement, membership lifecycle,
  doctor-profile ownership, CSRF, optimistic versions, audit, and outbox data
  minimisation.
- Frontend typecheck, all 45 Vitest tests, and the Vite production build pass.
- All 119 Organisation production sources compile successfully.

Exit criteria:

- [x] Only a Platform Administrator can directly create an active
      organisation, and the creation is audited without exposing contact data
      in the outbox event.
- [x] Only a Platform Administrator can assign an eligible existing user as
      that organisation's administrator, and the target-aware audit/outbox
      records exclude identity display data.
- [x] The designated Organisation Administrator can configure that
      organisation and its departments and add a doctor and receptionist.
- [ ] The doctor and receptionist can authenticate in the correct organisation.
- [x] Role and tenant denial tests pass at controller, service, and integration
      levels.

## 6. Phase 3 — Administrative patient registry

Status: `IN PROGRESS`

Goal: allow authorised administrative registration and search without exposing
clinical data.

Tasks:

- [x] Model the global patient identity and organisation-specific registration
      or medical-record number.
- [x] Define administrative patient create, update, list, detail, and search
      DTOs.
- [x] Store national identifier/passport safely and define masking rules.
- [x] Implement exact duplicate checks for strong identifiers.
- [x] Implement candidate duplicate scoring for phone, email, and
      name/date-of-birth combinations.
- [x] Require an authorised decision when a possible duplicate is found.
- [x] Add optimistic locking and audit history for administrative edits.
- [x] Ensure receptionist responses contain no diagnoses, notes, allergies,
      prescriptions, or other clinical fields.
- [x] Split the frontend's current combined `Patient` model into role-safe
      administrative and clinical projections.
- [x] Integrate receptionist patient registration, directory, search, and
      administrative profile screens.
- [ ] Add synthetic patient fixtures only.

Verified implementation:

- Patient Service Flyway version 1 defines global stable identity,
  organisation-specific administrative registration/contact data,
  organisation-local medical-record numbers, append-only audit, and outbox
  tables in the service-owned database.
- Cookie JWT validation, CSRF, role checks, active organisation extraction, and
  live Organisation Service context revalidation protect every Patient API.
- Create, duplicate-check, list/search, detail, update, and administrative
  history contracts are implemented under `/api/v1/patients` and documented by
  Springdoc.
- Strong identifiers use deterministic HMAC-SHA-256 fingerprints scoped by
  identifier type and issuing country; only the last four characters are
  retained for masking. Phone, email, and name/date-of-birth matches receive
  deterministic weighted reasons.
- Possible duplicates return masked candidates and require `LINK_EXISTING` or
  an audited `CREATE_NEW` reason. Exact strong-identifier matches cannot be
  overridden into a new identity.
- Gateway routing and negative tenant/role/CSRF/clinical-field tests pass. The
  fresh local Maven run verified 10 Patient Service tests and 23 Gateway tests
  with zero failures, errors, or skips.

Verified frontend evidence:

- Added a separate administrative patient contract and real Gateway REST
  adapter; the legacy clinical/mock presentation type is not used by the
  receptionist screens.
- Replaced the receptionist directory and registration routes with TanStack
  Query, React Hook Form, and Zod screens covering search, detail, masked
  duplicate review, explicit linking, and reviewed distinct-patient creation.
- `npm.cmd run typecheck`, the full 50-test Vitest suite, and the Vite production
  build pass. The five new patient REST/component tests are included in those
  totals.

Exit criteria:

- [x] A receptionist can register and find a patient.
- [x] Duplicate candidates are presented safely and do not create silent
      duplicates.
- [x] Receptionist clinical-data denial and response-serialization tests pass.
- [x] A different organisation cannot browse the patient registration.

## 7. Phase 4 — Scheduling and appointment notifications

Status: `NOT STARTED`

Goal: complete availability, appointment, confirmation, and check-in across
administrator/receptionist, doctor, and patient-facing projections.

Tasks:

- [ ] Model organisation-specific doctor availability, breaks, locations,
      absences, holidays, slot duration, and timezone.
- [ ] Implement deterministic slot calculation.
- [ ] Model appointment status and an explicit transition matrix.
- [ ] Enforce actor-specific transition permissions.
- [ ] Prevent overlaps with database constraints, transactions, and optimistic
      locking.
- [ ] Implement create, confirm, reject/reschedule, cancel, check-in, start,
      complete, and no-show commands.
- [ ] Require cancellation/reschedule reasons where appropriate.
- [ ] Publish minimal appointment domain events through the outbox.
- [ ] Consume appointment events in Notification Service.
- [ ] Deliver authenticated in-app/WebSocket notifications with REST recovery.
- [ ] Integrate doctor availability and receptionist scheduling/check-in UI.
- [ ] Integrate the doctor appointment board and notifications.
- [ ] Integrate the limited patient appointment request/status view.
- [ ] Add concurrency tests for two users attempting the same slot.

Exit criteria:

- [ ] The doctor configures availability.
- [ ] The receptionist books an available slot and cannot double book it.
- [ ] The doctor receives a real-time notification and confirms/reschedules.
- [ ] The receptionist checks the patient in.
- [ ] Invalid state transitions and stale versions return safe conflicts.

## 8. Phase 5 — Clinical record and protected files

Status: `NOT STARTED`

Goal: conduct, finalise, and safely correct a basic consultation with protected
medical documents.

Tasks:

- [ ] Define the care relationship that authorises consultation access.
- [ ] Model consultation drafts, history, symptoms, vitals, examination,
      diagnoses, assessment, treatment, medication, follow-up, and notes.
- [ ] Keep basic prescription items inside Clinical Service for V1.
- [ ] Start consultations only from an authorised appointment or explicit care
      relationship.
- [ ] Implement draft autosave with explicit versions.
- [ ] Implement finalisation/signing and immutable final records.
- [ ] Implement append-only corrections with old/new values, author, time, and
      reason.
- [ ] Create role-safe patient medical-summary projections.
- [ ] Model file metadata and consultation/patient/organisation links.
- [ ] Implement authorised upload negotiation, size/type/checksum validation,
      MinIO storage, scan-status hooks, and short-lived download access.
- [ ] Audit clinical reads, writes, finalisation, correction, upload, and
      download.
- [ ] Integrate the doctor patient summary, consultation workspace, diagnosis,
      medication, follow-up, and file UI.
- [ ] Keep receptionist endpoints unable to resolve clinical or file content.

Exit criteria:

- [ ] The checked-in appointment can become an in-progress consultation.
- [ ] The doctor records and finalises the consultation.
- [ ] The final record cannot be overwritten.
- [ ] A correction preserves both values and its reason.
- [ ] An authorised doctor can access a short-lived file URL.
- [ ] A receptionist and unrelated doctor cannot access clinical data or files.

## 9. Phase 6 — Messaging, referral, and selected sharing

Status: `NOT STARTED`

Goal: collaborate with another doctor without granting blanket patient access.

Tasks:

- [ ] Model authorised doctor conversations, participants, messages, and
      attachments.
- [ ] Ensure a patient mention stores context but grants no clinical access.
- [ ] Model referral draft, sent, accepted, rejected, active, completed,
      revoked, and expired states.
- [ ] Model selected share items rather than a whole-record boolean.
- [ ] Record consent type, evidence/reference, purpose, and access duration.
- [ ] Implement recipient validation and minimum-necessary sharing.
- [ ] Expose a share-access decision API owned by Communication Service.
- [ ] Require Clinical and File services to validate grants before returning
      externally shared resources.
- [ ] Use only short bounded Redis caching for decisions and invalidate it on
      grant changes.
- [ ] Publish referral/share events and generate real-time notifications.
- [ ] Implement scheduled expiry and immediate revocation.
- [ ] Integrate doctor messages, referral creation, incoming requests, selected
      data preview, acceptance/rejection, and revocation UI.
- [ ] Audit message attachments, referrals, decisions, grant use, revocation,
      expiry, and denied access.

Exit criteria:

- [ ] Doctor A sends a message mentioning a patient without granting access.
- [ ] Doctor A shares selected resources with Doctor B.
- [ ] Doctor B sees only those resources after acceptance.
- [ ] Revocation and expiry prevent later access.
- [ ] An unrelated doctor remains denied.

## 10. Phase 7 — Patient portal, audit, and hardening

Status: `NOT STARTED`

Goal: complete the limited patient experience and harden the whole platform.

Tasks:

- [ ] Integrate patient registration/login if retained for the internship demo.
- [ ] Integrate patient profile, doctor search, appointment request/history,
      appointment status, and notifications.
- [ ] Decide which finalised prescriptions/documents are patient-visible in V1.
- [ ] Complete central append-only audit ingestion and authorised queries.
- [ ] Add idempotency, retry, dead-letter, and replay procedures for consumers.
- [ ] Add structured logs, metrics, traces/request correlation, and health
      dashboards without sensitive data.
- [ ] Add rate limits for login, search, downloads, messages, and invitations.
- [ ] Add content-security policy and frontend security headers.
- [ ] Add dependency, container, secret, and upload security checks.
- [ ] Review retention, synthetic-data reset, backups, and recovery procedures.
- [ ] Perform an OWASP-focused review of authentication, authorisation,
      injection, CSRF, XSS, SSRF, file upload, and sensitive logging.
- [ ] Perform accessibility and responsive checks for active V1 routes.

Exit criteria:

- [ ] The limited patient portal works through real APIs.
- [ ] Audit history covers the required sensitive success and denial cases.
- [ ] No secrets, tokens, patient payloads, or clinical notes appear in logs or
      browser telemetry.
- [ ] Security, accessibility, and recovery checks have recorded evidence.

## 11. Phase 8 — End-to-end delivery and internship demonstration

Status: `NOT STARTED`

Goal: prove the complete workflow reliably in a reproducible environment.

Tasks:

- [ ] Automate the full 29-step internship journey with Playwright/API support.
- [ ] Add negative journeys for receptionist clinical access, unrelated doctor
      access, expired/revoked sharing, stale appointment updates, and invalid
      file access.
- [ ] Run unit, repository, service integration, contract, Kafka, WebSocket,
      frontend component, and browser tests in CI.
- [ ] Add GitHub Actions for build, test, security scans, and Docker image
      creation.
- [ ] Pin reproducible dependency and container versions.
- [ ] Create a deterministic synthetic demonstration dataset and reset process.
- [ ] Document architecture, API usage, security decisions, local deployment,
      testing, and the demonstration script.
- [ ] Prepare the internship report evidence: requirements traceability,
      diagrams, tests, screenshots, limitations, and future work.
- [ ] Evaluate Kubernetes/K3s deployment only after Docker Compose is stable.

Exit criteria:

- [ ] A clean machine can run the documented setup and demonstration.
- [ ] The full positive workflow and principal denial cases pass.
- [ ] CI is green and produces deployable frontend/backend artifacts.
- [ ] Known limitations and deferred modules are clearly documented.

## 12. Testing strategy applied in every phase

| Layer | Required focus |
| --- | --- |
| Unit | State machines, permission decisions, duplicate scoring, slot calculation, correction rules |
| Repository | Organisation scoping, unique constraints, optimistic locking, Flyway migrations |
| Service integration | PostgreSQL, Kafka, Redis, and MinIO through Testcontainers |
| Security integration | Role, active organisation, ownership, care relationship, sharing grant, denied-field serialization |
| Contract | OpenAPI/DTO compatibility between frontend and gateway APIs |
| Event | Outbox publication, idempotent consumers, duplicate/out-of-order events, dead-letter handling |
| WebSocket | Authentication, user isolation, reconnect, REST recovery |
| Frontend | Forms, validation, loading/empty/error/stale states, route UX |
| Browser | Complete role journeys at phone, tablet, and desktop widths |

## 13. Decision log

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-07-25 | Internship V1 is the active scope. | Reliability and security of the complete workflow matter more than feature count. |
| 2026-07-25 | Reuse the frontend in `C:\Users\LENOVO\Desktop\codex`. | It already provides the approved visual system, role portals, mock workflows, and partial integration layer. |
| 2026-07-25 | Existing frontend extras do not expand V1 scope. | The Aegis prototype is broader than the internship requirements. |
| 2026-07-25 | Build vertical slices, not isolated services to apparent completion. | Each milestone must be demonstrable through the React application and tested across boundaries. |
| 2026-07-25 | Keep basic prescriptions in Clinical Service. | A separate prescription service is deferred until advanced medication workflows exist. |
| 2026-07-25 | Use a dedicated append-only Audit Service for the unified audit view. | Sensitive activity originates in every service and the frontend already expects an audit endpoint. |
| 2026-07-25 | Use one local PostgreSQL server with separate service-owned databases and credentials. | This preserves ownership while keeping internship development manageable. |
| 2026-07-25 | Use transactional outbox and idempotent Kafka consumers. | Core events must not diverge from committed domain state. |
| 2026-07-25 | Keep authentication cookies inaccessible to JavaScript. | The frontend already uses credentialed requests and health data requires a safer browser session model. |
| 2026-07-25 | Use one root Maven parent/aggregator with flat service directories. | It centralises versions and enables one reactor build while keeping every service independently deployable. |
| 2026-07-25 | Migrate the frontend byte-for-byte before rebranding or scope trimming. | A verified baseline makes later visual or behavioural regressions attributable. |
| 2026-07-25 | Temporarily exclude database auto-configuration only in empty scaffold context tests. | PostgreSQL/Testcontainers are not configured yet; real database tests are mandatory when domain persistence begins. |
| 2026-07-25 | Use a pragmatic layered package structure inside domain services. | Explicit HTTP, DTO, persistence, business, security, client, event, and outbox boundaries are understandable for the internship and prevent premature shared abstractions. |
| 2026-07-25 | Keep new domain services lean and executable until their vertical slice begins. | Adding database, Kafka, or security dependencies before their infrastructure and rules exist would make empty services fail locally without proving business behavior. |
| 2026-07-25 | Use shared IntelliJ Spring Boot configurations backed by the root Maven reactor. | Every application remains visible and reproducibly runnable with the intended local profile; generated temporary configurations are redundant. |
| 2026-07-25 | Use one local PostgreSQL server with a separate database and restricted login owner for each stateful service. | This preserves microservice data ownership and prevents cross-service database access while remaining manageable for internship development. |
| 2026-07-25 | Keep generated local database credentials in `.env.database.local`. | The file is ignored by Git, gives later datasource configuration one local source, and prevents credentials from entering committed configuration. |
| 2026-07-25 | Use `UserSession.id` as the refresh-token family identifier. | One persistent aggregate then owns device/session lifetime, token rotation lineage, compromise, and per-device revocation without duplicating a separate family concept. |
| 2026-07-25 | Cache only a minimal versioned session projection in Redis while keeping PostgreSQL authoritative. | Redis accelerates session checks, but clearing it or losing it cannot erase session, token, or revocation state. |
| 2026-07-25 | Use a dedicated restricted `sahha_auth_test` database while local Docker/Testcontainers are unavailable. | Automated Flyway clean/rebuild tests must never target the development `sahha_auth` database; container-backed PostgreSQL can replace this fallback in CI. |
| 2026-07-25 | Use Flyway as schema authority and Hibernate `ddl-auto=validate`. | Database changes remain explicit, reviewable, reproducible, and protected from silent ORM schema mutation. |
| 2026-07-26 | Store only the global `PLATFORM_ADMIN` role in Auth Service and keep organisation roles in Organisation Service. | Platform administration is identity-wide, while organisation authority must remain scoped to a validated membership and active organisation. |
| 2026-07-26 | Enforce refresh-token family ownership with composite session/user and self-lineage foreign keys plus a partial unique active-token index. | The database rejects cross-user/cross-family tokens and prevents two unused, unrevoked tokens from being authoritative for one session even under concurrent writes. |
| 2026-07-26 | Use configurable BCrypt with cost 12 by default and a 12–128 character password boundary without arbitrary composition rules. | BCrypt protects stored credentials while the policy supports long passphrases; tests use cost 4 only to keep the suite fast. |
| 2026-07-26 | Generate 256-bit verification/reset secrets and persist only their lowercase SHA-256 hashes. | A database disclosure cannot directly reveal usable bearer tokens; the raw value exists only in the issuance result needed for later delivery. |
| 2026-07-26 | Group Auth use cases into `useraccountservice`, `verificationtokenservice`, and `emailservice`. | Entity/responsibility grouping keeps a growing service layer navigable while preserving separate controller, DTO, repository, exception, and configuration boundaries. |
| 2026-07-26 | Use Brevo through a provider-independent Spring Mail adapter. | Brevo fits the internship's low-volume real transactional-email needs, while standard SMTP properties allow the provider to change without rewriting account workflows. |
| 2026-07-26 | Queue authentication email in memory after the account/token transaction and retain a resend path. | Raw bearer tokens remain unpersisted and API timing does not wait on SMTP; durable Notification/outbox delivery will replace this transitional adapter without placing a raw token in an outbox. |
| 2026-07-26 | Return the same `202 Accepted` body for public resend and password-reset requests and enforce a two-minute per-account token cooldown. | The API does not disclose account existence and repeated requests cannot continuously replace active bearer tokens or flood one known account. |
| 2026-07-27 | Commit refresh-token replay compromise before returning a generic rejection. | Throwing the denial must not roll back the family revocation; PostgreSQL remains authoritative and the caller learns no token-state detail. |
| 2026-07-27 | Cap active Redis session decisions at 30 seconds and retain revocation tombstones until absolute session expiry. | A failed invalidation can permit only a short stale-active window, while version-aware tombstones prevent an older active write from restoring revoked state. |
| 2026-07-28 | Use short-lived RS256 access JWTs and rotating opaque refresh credentials in scoped HttpOnly cookies, with SPA CSRF protection. | The browser does not expose bearer credentials to JavaScript, downstream validators receive signed minimal claims, and cookie-authenticated state changes resist cross-site requests. |
| 2026-07-28 | Use native Memurai for Windows development and keep PostgreSQL authoritative. | It provides the Redis protocol at `localhost:6379` without Docker Desktop/WSL RAM overhead, while cache loss never loses the session record. |
| 2026-07-29 | Hash client addresses in Auth throttling keys and use a bounded local fallback when Redis is unavailable. | Redis provides shared short-lived counters without storing a readable address; the fallback preserves single-instance protection without making the disposable cache an authentication authority. |
| 2026-07-29 | Persist Auth security activity and its Kafka intent in the same PostgreSQL transaction. | Append-only events remain locally auditable and the acknowledged, retryable outbox prevents committed authentication changes from silently losing their audit event. |
| 2026-07-30 | Restore the frontend from a validated access cookie before considering refresh, and serialise real refresh through a browser-wide Web Lock. | Page reloads no longer create token history; simultaneous tabs cannot present the same rotating refresh credential, and a waiting tab reuses cookies renewed by the lock holder. |
| 2026-07-30 | Purge refresh-token lineage only after absolute session expiry plus seven-day retention and preserve the session row. | Expired secret hashes no longer grow indefinitely, replay detection remains available throughout the useful window, and append-only security-event foreign keys keep their audit anchor. |
| 2026-07-30 | Keep any future platform approval authority separate from organisation staff administration. | If the deferred registration/review workflow is added, only the Platform Administrator approves it, while the Organisation Administrator remains scoped to staff and departments; direct platform creation supersedes approval for the current slice. |
| 2026-07-30 | Use direct Platform Administrator organisation creation for the current internship slice. | It delivers the first controlled organisation workflow now; applicant registration, regulatory documents, authenticity checks, and approve/reject processing remain an explicitly deferred production workflow. |
| 2026-07-30 | Require an explicit CSRF cookie/header match for unsafe cookie-bearer requests in resource services. | Spring Security's Resource Server defaults exempt bearer-token requests from CSRF because header bearers are normally not ambient credentials; Sahha's bearer is an ambient cookie and therefore needs additional double-submit enforcement. |
| 2026-08-05 | Separate global stable patient identity from organisation-owned registration and contact data. | Exact identity matching can link one person safely while each organisation keeps an isolated medical-record number and local administrative projection. |
| 2026-08-05 | Persist only a type/country-scoped HMAC fingerprint and last-four mask for national IDs and passports. | Exact matching remains deterministic without storing or returning the submitted raw strong identifier. |
| 2026-08-05 | Revalidate receptionist/administrator membership live through Organisation Service for every Patient API use case. | Signed organisation claims provide coarse policy, while suspension and role changes must take effect from the authoritative membership owner. |

## 14. Risks and controls

| Risk | Control |
| --- | --- |
| Too many microservices for an internship | Deliver in vertical slices, standardise service templates, and defer non-core modules. |
| Frontend drives accidental scope expansion | Keep a V1 route/feature matrix and hide deferred modules. |
| Receptionist receives clinical fields | Separate DTOs and add serialization/security tests. |
| Cross-organisation data leakage | Active-context validation plus organisation-scoped repositories and negative integration tests. |
| Double booking | Database constraints, transactional checks, and optimistic locking; not Redis alone. |
| Kafka/database inconsistency | Transactional outbox and idempotent consumers. |
| Stale sharing permission after revocation | Authoritative grant decision with short cache and invalidation. |
| Silent clinical record changes | Finalisation lock and append-only corrections. |
| Public or guessable medical files | Private object storage and short-lived authorised access. |
| Dependency/version mismatch | Verify the Java/Spring compatibility matrix in Phase 1 before expanding modules. |
| Sensitive data in logs/events | Minimal event schemas, structured redaction rules, and automated checks. |

## 15. Plan update template

Use this checklist after every task:

1. Update the phase and task status.
2. Record files/components/services changed.
3. Record migrations, API, event, permission, or architecture decisions.
4. Record commands/tests run and their results.
5. Record blockers or unverified behavior.
6. Set exactly one current next task.
7. Add a dated change-log entry below.

## 16. Change log

### 2026-08-05 — Patient and Gateway backend validation completed

- Inspected the fresh Surefire reports after applying Patient Flyway version
  2. All 10 Patient Service tests and all 23 Gateway tests passed with zero
  failures, errors, or skips.
- Verified migration and Hibernate schema validation, Patient application
  startup, HMAC identifier protection, registration/search/detail, duplicate
  decisions, cross-organisation hiding, receptionist projection boundaries,
  CSRF/role/version denial, OpenAPI, and Gateway routing.
- Marked the now-verified Phase 3 implementation and security tasks complete.
  Phase 3 itself remains in progress until the real services and frontend pass
  the live receptionist workflow through Gateway.
- Next: start the required local services and perform the live Phase 3 browser
  workflow with synthetic patient data.

### 2026-08-05 — Administrative patient registry implementation started

- Corrected the first local backend validation failure with forward-only
  Patient Flyway version 2. PostgreSQL exposed the fixed-length identifier and
  country columns as `bpchar`, while their JPA mappings require `VARCHAR`;
  version 2 aligns all three affected columns without changing the already
  applied version 1 migration. The persistence test now expects schema version
  2. Gateway tests had passed; the apparent Patient-wide failure was one
  shared application-context startup error followed by Spring's context
  failure-threshold skips.
- Replaced the database-free Patient scaffold with a service-owned PostgreSQL
  configuration, Hibernate validation, and Flyway version 1 for stable patient
  identity, tenant registration/contact records, append-only audit, and a
  transactional outbox.
- Added keyed HMAC identifier protection and last-four masking; neither API
  responses, local audit metadata, nor outbox payloads contain a submitted
  national ID/passport, patient name, address, phone, or email.
- Added weighted duplicate detection for strong identifier,
  name/date-of-birth, phone, and email matches. Masked candidates require an
  explicit same-person link or enumerated distinct-person reason; an exact
  strong-identifier match cannot be overridden.
- Added tenant-scoped create, duplicate-check, list/search, detail, optimistic
  update, and administrative-history endpoints with safe Problem Details and
  OpenAPI documentation.
- Added Patient resource-server validation, cookie CSRF enforcement, coarse
  receptionist/Organisation Administrator policy, live Organisation Service
  membership revalidation, and cross-organisation `404` resource hiding.
- Added acknowledgement-aware Patient outbox publication code and a minimal
  PII-free event mapper. Publication remains disabled by default until Kafka is
  intentionally started.
- Routed `/api/v1/patients` through Gateway and added a routing integration
  test.
- Added backend integration tests for registration/search/detail, masked exact
  matching and cross-organisation linking, reviewed possible duplicates,
  clinical-field serialization boundaries, CSRF/role/version denial, Flyway,
  append-only audit, identifier protection, and OpenAPI.
- Added the frontend administrative patient model and REST adapter separately
  from the legacy clinical/mock `Patient`, then connected the receptionist
  directory/detail and registration/duplicate-review screens with TanStack
  Query, React Hook Form, and Zod.
- Verified `npm.cmd run typecheck`, all 50 frontend tests, the Vite production
  build, and `git diff --check`. The offline Maven check also confirmed the
  blocker is environmental: Spring Boot parent `4.1.0` is absent from the
  local cache and Maven Central is unavailable inside the restricted sandbox.
  No Patient or updated Gateway backend test is marked complete yet.
- Next: run the Patient Service and Gateway Maven suites against local
  PostgreSQL and fix any reported failures before live browser validation.

### 2026-08-05 — Phase 2 backend validation fixes

- Inspected the fresh unrestricted Surefire reports instead of relying on the
  earlier sandbox compilation attempt. All 22 Gateway tests passed.
- Traced all 30 initial Organisation context errors to one missing test-only
  configuration value: `StaffInvitationProperties.validity` was `null`
  because the test `application.properties` overrides the main resource.
- Added `sahha.organisation.staff-invitation.validity=P7D` explicitly to the
  Organisation test configuration. The next Organisation run started Flyway
  version 6, validated seven migration resources, and passed 35 of 36 tests.
- Traced the sole remaining `401`/`404` mismatch to a malformed four-segment
  mocked JWT in `StaffInvitationHttpIntegrationTests`; the production resolver
  correctly requires three segments. Corrected only the fixture and preserved
  the expected cross-organisation `404` hiding behavior.
- The fresh unrestricted rerun completed at 00:39 on 2026-08-05: all 22
  Gateway tests and all 36 Organisation tests passed with zero failures,
  errors, or skips. The organisation staff lifecycle slice is verified.
- Next: inspect the Patient Service skeleton and existing frontend registration
  contracts, then define the tenant-scoped administrative patient-registry
  contract before implementing its first migration.

### 2026-08-04 — Organisation staff directory and membership lifecycle

- Added Organisation Service Flyway version 6 with tenant-constrained staff
  department assignments and organisation-specific doctor profiles. Composite
  foreign keys prevent cross-organisation membership/department references;
  partial unique indexes allow only one active placement per department and
  one active primary placement per staff membership.
- Added active-organisation administrator APIs to list/read accepted doctors
  and receptionists, assign/end department placement, suspend/reactivate
  access, and permanently remove a membership. Removal deactivates the staff
  role and ends active assignments; a removed membership cannot be restored.
- Added doctor-owned create/read/update professional-profile APIs. The
  administrator can read a doctor's organisation profile but cannot edit it;
  receptionists cannot use the doctor endpoint. External documentary licence
  verification remains deferred and examples use synthetic data.
- Enforced controller roles plus live membership/role/organisation checks,
  tenant resource hiding, CSRF on mutations, optimistic versions, safe Problem
  Details, append-only audit records, and minimal outbox payloads that omit
  professional and identity details.
- Added Gateway routes and OpenAPI coverage, expanded the Organisation Swagger
  guide, and added migration, HTTP lifecycle, role-denial, cross-tenant,
  audit/outbox, Gateway, frontend REST-adapter, and page tests.
- Replaced the Organisation Administrator's mocked Clinical teams and Staffing
  surfaces with the real staff directory while leaving non-admin Operations
  views unchanged. Added the doctor Professional profile route and preserved
  organisation-keyed TanStack Query caches.
- Verification passed for all 45 frontend tests, frontend TypeScript, the Vite
  production build, and Organisation Service production compilation of all
  119 sources. Backend test compilation remains unverified
  because this restricted Java process cannot access the external Maven cache;
  the requested elevated read was denied. No backend test is marked passed.
- Next: run the Organisation and Gateway staff-directory, invitation,
  migration, OpenAPI, and security suites in an unrestricted local terminal,
  resolve any failures, and only then mark the Phase 2 staff lifecycle slice
  complete.

### 2026-08-04 — Staff invitation and membership onboarding implementation

- Added Organisation Service Flyway version 5 and a tenant-owned staff
  invitation lifecycle for `DOCTOR` and `RECEPTIONIST`, including normalized
  email targeting, seven-day expiry, renew/revoke/accept/reject actions,
  optimistic locking, and one unresolved invitation per organisation/email.
- Added active-organisation-scoped administrator APIs plus caller-owned
  invitation APIs. The invited account is resolved through Auth's new
  caller-only `/api/v1/auth/account` projection; administrators cannot browse
  arbitrary Auth identities through this workflow.
- Kept the Auth network lookup outside the Organisation database transaction.
  Acceptance then creates the membership, assigns exactly the invited role,
  resolves the invitation, and stores append-only audit/outbox records in one
  Organisation transaction.
- Enforced live organisation-administrator membership checks, exact invited
  email binding, resource hiding across organisations/accounts, CSRF on
  mutations, safe Problem Details, and minimal secret-free outbox payloads.
- Added Gateway routes, OpenAPI assertions, Auth client coverage, and
  Organisation lifecycle/security integration tests.
- Replaced the mocked organisation access screen with real invitation
  management and added invitation accept/reject to organisation selection so a
  new staff member can join before an active organisation exists. Invitation
  responses include the authoritative organisation display name so the user
  knows which organisation they are joining before deciding.
- Verification passed for frontend TypeScript, all 38 frontend tests, Vite
  production build, and `git diff --check`. Backend production compilation
  produced the affected classes and reported `BUILD SUCCESS`, but backend test
  compilation could not run because the restricted Java process receives
  `AccessDeniedException` for the external Maven cache. No backend test is
  marked verified from that attempt.
- Next: run the Auth, Organisation, and Gateway invitation test suites in an
  unrestricted local terminal, resolve any failures, and mark the onboarding
  slice complete only after they pass.

### 2026-08-01 — Organisation-scoped department management

- Added Organisation Service Flyway version 4 with tenant-owned departments,
  organisation-local normalized-name/code uniqueness, active/inactive state,
  audit metadata, indexes, constraints, and optimistic locking.
- Added create, paginated list, read, update, and status-change APIs under
  `/api/v1/departments`. The organisation ID comes only from the signed
  `org_id` claim; it is absent from request forms and resource paths.
- Enforced both `ORGANIZATION_ADMIN` authority and a live database check for
  the caller's active membership, active role, and active organisation.
  Cross-organisation resource IDs return `404`; global `PLATFORM_ADMIN` or
  doctor roles alone return `403`; suspended memberships remain denied even
  when presented with a previously issued role-bearing token.
- Made every department mutation atomic with an append-only audit event and a
  minimal transactional outbox intent containing identifiers, status, and
  event metadata but no department description or organisation contact data.
- Added duplicate conflict and stale-version Problem Details, CSRF enforcement,
  OpenAPI operations, Gateway discovery routing, and full lifecycle/security
  integration coverage.
- Replaced the mocked Organisation Administrator department page with a real
  Gateway-backed React screen using TanStack Query cache keys scoped by active
  organisation, React Hook Form/Zod validation, create/edit/status workflows,
  safe error states, search, and responsive styling.
- Verification passed: Organisation Service 27 tests, Gateway 20 tests,
  frontend 32 tests, TypeScript typecheck, and Vite production build, with no
  failures, errors, or skips.
- Next: model the staff-invitation lifecycle and implement active-organisation
  doctor/receptionist invitation and membership onboarding with tenant-denial
  coverage.

### 2026-08-01 — Eureka client isolation and Gateway port alignment

- Diagnosed live healthy Auth and Organisation processes that created Eureka
  clients but never opened a connection or registered, while Gateway registered
  normally on port `8079`.
- Separated a primary plain `RestClient.Builder`, used by Eureka's bootstrap
  HTTP transport, from each explicitly qualified load-balanced builder used for
  Auth-to-Organisation and Organisation-to-Auth calls. This prevents service
  discovery from depending circularly on an initially empty service registry.
- Aligned the frontend local/example environment, smoke script, README, and
  current Auth/Gateway guides with the user's Gateway port `8079`.
- Live verification registered Auth on `8081`; an isolated Organisation
  instance registered on `9082` and was then stopped. Both application-context
  tests, frontend typecheck, and all 27 frontend tests passed. The existing
  IntelliJ Organisation process requires one restart to load the compiled fix.
- Next remains: model departments and implement active-organisation-scoped
  department management with cross-organisation denial tests.

### 2026-08-01 — Active organisation and scoped session context

- Added authenticated Organisation Service context APIs that list and resolve
  only the caller's active memberships in active organisations with active
  organisation roles; the user ID always comes from the verified JWT subject.
- Added Auth Service Flyway version 7 and persisted a validated active
  organisation plus canonical role snapshot on each `UserSession`, while
  retaining PostgreSQL as authority and upgrading the Redis projection schema.
- Added `POST /api/v1/auth/active-organisation`. Auth validates the requested
  context through the Eureka-aware Organisation Service client, locks the
  caller-owned session, updates Redis only after commit, records an append-only
  security event/outbox intent, and issues a new access cookie without creating
  or rotating a refresh token.
- Added `org_id` and `org_roles` access-token claims and context-bound Auth
  validation. A token carrying the previous context is rejected as soon as the
  session selection changes; Gateway and Organisation Service also reject
  malformed or unpaired scoped claims.
- Added Gateway routing for authenticated organisation APIs and kept
  Organisation Service's resource-server and membership checks authoritative.
- Added the React organisation selector, TanStack Query context discovery,
  canonical role-to-workspace routing, no-membership handling, safe error
  states, and a storage event that makes other tabs restore the changed
  server-side context without placing an organisation ID in browser storage.
- Verification passed: Auth Service 115 tests, Organisation Service 22 tests,
  Gateway 19 tests, frontend 27 tests, TypeScript typecheck, and Vite production
  build. The final Auth constructor refinement also compiled successfully.
- Next: model departments and implement active-organisation-scoped department
  management with cross-organisation denial tests.

### 2026-07-31 — Organisation Administrator membership assignment

- Added an exact-email Platform Account directory endpoint in Auth Service
  that returns only the identity fields required for membership eligibility;
  password, credential-version, session, and token data are never returned.
- Added Organisation Service Flyway version 3 with organisation memberships,
  separate multi-role assignments, lifecycle constraints, organisation/user
  uniqueness, lookup indexes, and target/resource-aware audit metadata.
- Added an Eureka-aware Auth account client that forwards only the current
  access credential, never a refresh or CSRF cookie, and keeps Organisation
  Service out of the Auth database.
- Added platform-protected assign/list/read administrator APIs beneath
  `/api/v1/platform/organisations/{organisationId}/administrators`, including
  exact account resolution, active/verified eligibility, duplicate denial,
  CSRF enforcement, safe Problem Details, and OpenAPI documentation.
- Made administrator assignment atomic across the membership, active
  `ORGANIZATION_ADMIN` role, append-only audit event, and secret-free Kafka
  outbox event; email and display-name snapshots are excluded from the event.
- Added the real Platform Admin assignment panel with TanStack Query,
  React Hook Form/Zod validation, identity and eligibility error states,
  membership list/read details, and cache invalidation after assignment.
- Verification passed: Auth Service 114 tests, Organisation Service 21 tests,
  Gateway 18 tests, frontend 25 tests, TypeScript typecheck, and Vite production
  build, with no failures, errors, or skips.
- Next: implement validated active-organisation selection and renew the
  authenticated session/token context with its scoped membership roles.

### 2026-07-31 — Platform organisation frontend integration

- Assigned the existing verified local development user a global
  `PLATFORM_ADMIN` role through a trusted manual database bootstrap; no
  password was created, recovered, or stored in plaintext.
- Added typed Organisation Service create/list/read frontend contracts and a
  Gateway-only REST adapter that inherits cookie authentication and CSRF
  protection from the shared HTTP client.
- Added TanStack Query at the application root with bounded staleness and
  conservative retries, then replaced the fake Platform Organizations screen
  with a searchable real directory, selected-resource details, organisation
  metrics, and a validated creation workflow.
- Added safe expired-session, missing-role, duplicate-name, loading, empty, and
  service-unavailable states without exposing backend internals.
- Kept the Gateway address environment-driven through `VITE_API_BASE_URL`, so
  the user's local Gateway port can change without editing feature code.
- Added REST and component coverage for Gateway URLs, credentialed requests,
  CSRF headers, create/list/read behaviour, real-data rendering, form mapping,
  and 403 guidance.
- Verification passed: TypeScript typecheck, six focused tests, 22 complete
  frontend tests, and the Vite production build.
- Next: implement platform-controlled Organisation Administrator membership
  assignment and persistence in Organisation Service.

### 2026-07-30 — Platform-created organisation vertical slice

- Replaced the immediate organisation-registration/review milestone with the
  user's narrower internship workflow: a global `PLATFORM_ADMIN` directly
  creates an active hospital, clinic, or private practice.
- Added Organisation Service Flyway migrations for the organisation aggregate,
  append-only audit events, and retryable outbox events. Hibernate validates
  the schema and tests use the isolated `organisation_test` schema.
- Added validated create, paginated list, and read APIs at
  `/api/v1/platform/organisations`, with safe Problem Details, duplicate-name
  protection, request IDs, no-store responses, and OpenAPI/Swagger examples.
- Added Organisation Service JWT validation against Auth issuer, audience,
  access-token claims, and global roles. Non-platform users are denied.
- Added explicit double-submit CSRF enforcement for unsafe
  cookie-authenticated requests in Organisation and Auth. This closes the
  Resource Server bearer exemption for Sahha's cookie-carried bearer token.
- Added Gateway routing and coarse `PLATFORM_ADMIN` enforcement for the
  organisation APIs.
- Added a manual Swagger guide and updated the durable project context.
- Verification passed: Organisation Service 14 tests, Gateway 17 tests, and
  Auth Service 112 tests, with no failures, errors, or skips.
- Deferred public organisation applications, regulatory-document submission,
  licence authenticity checks, and approve/reject processing.
- Next: connect the Platform Administrator organisations screen to the real
  create/list/read Organisation Service APIs.

### 2026-07-30 — Organisation approval and staff-ownership clarification

- Confirmed that an organisation registration is submitted for platform
  review; the `PLATFORM_ADMIN` is the only role that approves or rejects it.
- Confirmed that approval activates the designated Organisation
  Administrator, who then manages departments, doctors, receptionists, and
  other staff only within that approved organisation.
- Kept platform administration separate from organisation membership and from
  clinical access.
- No implementation code changed.
- This earlier proposed workflow is superseded for the current internship
  slice by direct Platform Administrator creation; it remains historical
  context for the deferred production review workflow.

### 2026-07-30 — Session-first restoration and cross-tab refresh coordination

- Added protected `GET /api/v1/auth/session`, which returns safe metadata from
  the already validated access cookie without setting authentication cookies,
  changing `UserSession`, or inserting/consuming a refresh-token row.
- Changed React startup restoration to call the non-rotating endpoint first.
  `/refresh` is now used only after access rejection or inside the 30-second
  access-renewal window.
- Added the browser-wide `sahha-auth-refresh-v1` Web Lock. A waiting tab
  rechecks `/session` after acquiring the lock and skips refresh when another
  tab already replaced the shared cookies.
- Added configurable, hourly, bounded refresh-token-family cleanup with a
  default seven-day retention after absolute session expiry. Audit-linked
  `UserSession` rows remain; only expired token lineage is removed.
- Added Flyway `V6__index_session_retention.sql` and applied migration version
  6 to both Auth development and test databases.
- Corrected the ignored frontend local API base URL from Organisation port
  `8082` to Gateway port `8080`.
- Verification passed: Auth 112 tests; full 13-project backend reactor 138
  tests; frontend 4 files/16 tests, typecheck, and production build. The
  packaged Auth application also started successfully against migration
  version 6 on an isolated local port. No test failures, errors, or skips were
  reported.
- Next: model organisations, departments, invitations, memberships,
  organisation roles, and the active-organisation selection contract in
  Organisation Service.

### 2026-07-30 — Local Auth development-account reset

- Removed all three local development accounts from `sahha_auth` in one
  PostgreSQL transaction after confirming that none had an active
  `PLATFORM_ADMIN` assignment.
- Removed four dependent verification tokens; no database sessions, refresh
  tokens, security events, or pending outbox events existed.
- Preserved the seeded `PLATFORM_ADMIN` role definition, the complete Flyway
  schema/migration history, and all application code.
- Confirmed zero remaining `user_account`, `verification_token`,
  `user_session`, and `refresh_token` rows and zero
  `sahha:auth:session:v1:*` Memurai keys.
- Next: model organisations, departments, invitations, memberships,
  organisation roles, and the active-organisation selection contract in
  Organisation Service.

### 2026-07-29 — Auth Service completion before Organisation

- Completed IP-aware throttling for login, registration, email
  verification/reset requests and confirmations, and refresh. Redis keys
  contain only a SHA-256 client-address hash; an in-process fallback remains
  available during Redis outages, and excessive requests receive safe
  `429` Problem Details with `Retry-After`.
- Added a trusted `X-Sahha-Client-IP` hop from Gateway after stripping any
  client-supplied value, so Auth limits the real edge client rather than the
  Gateway instance.
- Added authenticated active-session listing with safe device metadata,
  owned-session revocation, current-password-protected password change, and
  immediate all-device revocation after a credential change.
- Added `PLATFORM_ADMIN`-protected suspension, reactivation, and disablement
  hooks. Auth rechecks the persisted role, denies self-targeting, advances
  credential state, and revokes target sessions for suspension/disablement.
- Added Flyway `V5__create_security_events_and_auth_outbox.sql`, append-only
  `SecurityEvent`, same-transaction `AuthOutboxEvent`, secret-free mapping,
  and a conditional Kafka publisher that marks delivery only after
  acknowledgement and schedules bounded retries on failure.
- Expanded Swagger from 10 to 14 operations and added integration coverage for
  ownership denial, password/session invalidation, persisted platform-role
  enforcement, append-only database protection, secret-free outbox payloads,
  Kafka acknowledgement/retry behavior, and hashed rate-limit keys.
- Applied migration version 5 to `sahha_auth` and `sahha_auth_test`; a temporary
  local-profile startup reached Auth health `UP` and was then stopped.
- Verification passed: Auth 110 tests, Gateway 16 tests, and the complete
  13-project reactor 136 tests, with no failures, errors, or skips.
- Auth-owned internship work is complete. Active-organisation selection
  remains intentionally deferred until Organisation Service can authoritatively
  validate memberships.
- Next: model organisations, departments, invitations, memberships,
  organisation roles, and the active-organisation selection contract in
  Organisation Service.

### 2026-07-29 — React registration and Gateway authentication

- Added a hybrid frontend mode: Auth uses real Gateway APIs at
  `http://localhost:8080/api/v1` while unfinished patient/organisation/
  clinical data services continue using their mock implementations.
- Replaced the production login role selector with server-derived routing.
  Signed `PLATFORM_ADMIN` metadata opens the platform workspace; other current
  accounts open the patient workspace until Organisation Service supplies
  validated organisation memberships and roles.
- Added React Hook Form and Zod login/registration validation, a real patient
  registration page, accepted-registration guidance, and the `/verify-email`
  route used by Auth's Brevo email link. The single-use token is removed from
  the browser address bar before confirmation.
- Added automatic Gateway CSRF bootstrap/header attachment, credentialed
  requests, stale-CSRF retry, HttpOnly-cookie session handling, deduplicated
  startup refresh, access-expiry renewal, logout cleanup, and safe
  Problem-Details/network errors.
- Added a minimal session-storage, non-authoritative UI identity projection
  for display only
  because Auth's session response currently exposes IDs/expiry/roles but not
  the user's profile. It never stores access or refresh credentials and never
  grants backend authority.
- Added 6 Auth REST-adapter tests and 3 authentication-page tests. The complete
  frontend verification passes 4 files/14 tests, TypeScript typecheck, the
  production build, and 11 responsive browser checks including registration
  at 375 px without horizontal overflow.
- Patched PostCSS through an override and moved React Router to the latest
  stable `7.18.2`. npm's remaining advisory chain is specific to React Server
  Components action handling, which this client-only `BrowserRouter` SPA does
  not enable.
- Added `docs/FRONTEND_AUTH_GUIDE.md` and a reusable
  `npm run smoke:auth` browser check. The ignored `frontend/.env.local`
  enables real Auth locally without disabling domain mocks.
- Next: model the Organisation Service domain and active-organisation
  selection contract.

### 2026-07-29 — Gateway Auth routing and RS256 edge validation

- Added explicit Eureka load-balanced routes for `/api/v1/auth/**` and Auth's
  public `/.well-known/jwks.json`; all other unknown routes remain denied.
- Added cookie-only edge authentication using Auth's RS256 JWKS, issuer,
  audience, access-token type, UUID subject/session, credential version, and
  global-role validation. Public Auth operations ignore stale access cookies
  so login, refresh, and recovery remain reachable.
- Added canonical request IDs, removal of untrusted identity and forwarding
  headers, credentialed frontend CORS, safe Problem Details for `401`/`403`,
  and baseline browser security headers.
- Replaced the Gateway downstream client with a no-cookie-store JDK client
  after live verification exposed cross-request cookie retention. Independent
  browser sessions now keep separate Auth CSRF and session cookies.
- Added 16 Gateway tests covering the JWT decoder with real RSA keys and a
  local JWKS server, claim and role rules, routing through service discovery,
  CORS, safe failures, header sanitisation, cookie forwarding, and cookie
  isolation.
- Added `docs/GATEWAY_AUTH_GUIDE.md` and linked it from the repository README
  and Auth Swagger guide so the local start order, browser cookie/CSRF flow,
  configuration, boundaries, and verification commands stay reproducible.
- Full verification passed all 125 tests across the 13 Maven projects with no
  failures, errors, or skips. Memurai returned `PONG`, zero Auth session keys
  remained, and the source credential-pattern scan was clean.
- A real Eureka/Auth/Gateway smoke test proved health, discovery routing,
  public-key exposure without a private component, independent CSRF cookies,
  Auth login reachability, protected-route rejection, and a single canonical
  response request ID. All temporary smoke-test processes were stopped.
- Next: model the Organisation Service domain and active-organisation
  selection contract.

### 2026-07-29 — CSRF response/header contract correction

- Reproduced Swagger's `403` against the running Auth service and proved that
  Spring's 96-character masked request attribute differed from the raw
  36-character `XSRF-TOKEN` cookie value expected in the header flow.
- Changed `GET /api/v1/auth/csrf` to expose the raw token from Spring's
  request-bound `DeferredCsrfToken`, avoiding a second token generation and
  duplicate `Set-Cookie` headers.
- Strengthened the browser-session integration test so it asserts the JSON
  token equals the single cookie value and then uses the JSON token in the
  following protected POST.
- Focused browser/OpenAPI verification passed 8 tests; the complete Auth suite
  passed 99 tests with no failures, errors, or skips.
- Full reactor verification passed all 110 tests across the 13 Maven projects
  with no failures, errors, or skips.
- A corrected local-profile instance on port `18081` issued exactly one cookie,
  returned a matching JSON token, and accepted that response token through the
  CSRF layer; the synthetic unknown login then reached its expected `401`.
- Next remains: route Auth through API Gateway and validate its access cookie
  using Auth's public JWKS.

### 2026-07-28 — Auth secure browser-session HTTP

- Added RS256 access-token signing, public-only JWKS publication, issuer,
  audience, expiry, token-type, session ID, credential-version, and
  database-backed global-role claims.
- Added Redis/PostgreSQL-bound JWT validation so logout, replay compromise,
  credential changes, and session expiry invalidate access decisions.
- Added scoped HttpOnly access, refresh, and device cookies with production
  `Secure` defaults and an explicit local HTTP override.
- Added SPA CSRF issuance and rotation plus header enforcement on every
  state-changing account/session operation.
- Added login, refresh, current logout, and global logout HTTP contracts; token
  values never appear in response bodies or Problem Details.
- Extended OpenAPI to all ten account/session operations and documented both
  cookie authentication and CSRF-header requirements; JWKS remains hidden from
  Swagger but publicly reachable for validators.
- Replaced Docker-dependent Redis integration tests with native Memurai
  verification and an unused-port outage test that does not stop the service.
- Verified Memurai `PONG`, 256 MB `maxmemory`, `allkeys-lru`, and empty Sahha
  session-key cleanup after testing.
- Full verification: 99 Auth tests and 110 tests across all 13 Maven reactor
  projects passed with no failures, errors, or skips.
- Live local smoke test: Auth health was `UP`, JWKS exposed one RSA public key
  with no private component, and CSRF issuance returned HTTP `200` with the
  `XSRF-TOKEN` cookie.
- Next: route Auth through API Gateway and validate its access cookie using the
  Auth JWKS.

### 2026-07-27 — Auth Redis session cache

- Added Spring Data Redis with environment-backed local settings and disabled
  Redis health coupling because PostgreSQL remains the session authority.
- Added a minimal versioned JSON session projection containing no refresh
  token, email, phone, IP address, device name, or user-agent data.
- Added cache-aside loading, after-commit writes, at-most-30-second active
  entries, and revocation tombstones retained until absolute session expiry.
- Added an atomic Lua write guard so an older active cache write cannot
  overwrite a newer revocation decision.
- Integrated cache synchronisation with login, refresh rotation, replay
  compromise, expiry, current/global logout, and password-reset revocation.
- Proved that malformed, missing, cleared, and unavailable Redis state falls
  back to PostgreSQL without losing authoritative session or token state.
- Added Docker Desktop and WSL2 local Redis instructions in
  `docs/LOCAL_REDIS.md`; no persistent Sahha Redis container was created
  automatically.
- Complete Auth verification: 86 tests passed with no failures, errors, or
  skips.
- Clean full backend reactor: all 13 projects succeeded; 97 tests passed with
  no failures, errors, or skips.
- Live fallback smoke test: Auth health returned HTTP `200` while local Redis
  port `6379` had no listener.
- Next: implement the Auth login, refresh, and logout HTTP contracts with
  short-lived JWT access tokens, secure HttpOnly cookies, and CSRF protection.

### 2026-07-27 — Auth OpenAPI and Swagger UI

- Added Springdoc OpenAPI `3.0.3`, the Spring Boot 4 compatible line, to Auth.
- Added Auth API title/version metadata, synthetic request examples, endpoint
  summaries, operation IDs, success responses, and safe Problem Details
  contracts.
- Exposed `/v3/api-docs`, `/swagger-ui.html`, and Swagger UI assets through the
  Auth security filter without changing protection for other routes.
- Added `AUTH_OPENAPI_ENABLED` so both documentation surfaces can be disabled
  together outside the internship development environment.
- Added integration tests proving the OpenAPI JSON documents exactly the five
  implemented HTTP operations and the unauthenticated Swagger page is
  reachable.
- Added `docs/AUTH_SWAGGER_GUIDE.md` with startup, happy-path, denial-path,
  email-token, and automated-test instructions.
- Live local verification: health, OpenAPI JSON, and Swagger UI each returned
  HTTP `200`; the document contained exactly five paths.
- Complete Auth verification: 70 tests passed with no failures, errors, or
  skips.
- Clean full backend reactor: all 13 projects succeeded; 81 tests passed with
  no failures, errors, or skips.
- No login, refresh, logout, or session-management HTTP contract is claimed;
  those remain later milestones.
- Next: implement the minimal Redis session projection with bounded TTL,
  version-aware writes, revocation tombstones, and PostgreSQL fallback.

### 2026-07-27 — Auth session and refresh-rotation core

- Added configurable session idle and absolute lifetimes, defaulting to seven
  and 30 days while preventing idle lifetime from exceeding absolute lifetime.
- Added `usersessionservice` for credential-backed device-session creation,
  current-device logout, global logout, and security-change revocation.
- Added `refreshtokenservice` for 256-bit refresh issuance, SHA-256 hash-only
  persistence, atomic parent/replacement rotation, family revocation, and
  generic presented-token lookup.
- Kept raw refresh values only in `@JsonIgnore`, restricted-`toString`
  in-memory issuance results; hashes, device identifiers, and network metadata
  remain excluded from API serialization and string output.
- Added version-4-compatible pessimistic family queries without changing the
  existing Flyway schema.
- Added replay handling that commits `COMPROMISED` session state and revokes
  the replacement token before returning a generic invalid-token failure.
- Connected password reset to immediate revocation of all active session
  families after the credential version advances.
- Added nine PostgreSQL integration tests covering initial issuance,
  unknown/unverified denial, lineage, replay, two-thread concurrent refresh,
  device isolation, global logout, password-reset revocation, and idle expiry.
- Focused session integration: nine tests passed.
- Complete Auth verification: 68 tests passed with no failures, errors, or
  skips.
- Clean full backend reactor: all 13 projects succeeded; 79 tests passed with
  no failures, errors, or skips.
- No login, refresh, or logout HTTP endpoint, JWT, cookie, CSRF, gateway, or
  Redis behavior was claimed in this milestone; those remain later layers.
- Next: implement the minimal Redis session projection with bounded TTL,
  version-aware writes, revocation tombstones, and PostgreSQL fallback.

### 2026-07-26 — Brevo SMTP local configuration and live submission

- Enabled Auth mail in the gitignored `.env.mail.local` with the supplied Brevo
  SMTP server, port, login, SMTP key, sender name, and verified sender address.
- Confirmed `.env.mail.local` remains excluded by `.gitignore`; no SMTP secret
  was added to source-controlled application configuration or documentation.
- Rebuilt the current Auth executable JAR successfully and started it with its
  real `local` profile against the owned PostgreSQL database.
- Confirmed `GET /actuator/health` returned `200 OK`. Eureka was not running,
  so Auth logged non-blocking discovery-registration warnings during this
  isolated test.
- Submitted one synthetic registration for the configured recipient through
  `POST /api/v1/auth/registrations`; it returned `202 Accepted` with request ID
  `brevo-smoke-20260726`.
- Observed the asynchronous delivery window through the configured SMTP
  timeouts and found no authentication, preparation, send, or dispatch failure;
  the user then confirmed that the verification email reached the intended
  inbox.
- Re-ran `.\mvnw.cmd -pl auth-service test`: all 59 Auth tests passed with no
  failures, errors, or skips.
- The supplied SMTP key appeared in conversation history and should be revoked
  after this smoke test; its replacement must be written directly to the
  ignored local file rather than sent through chat.
- Next: implement transactional login/session issuance and refresh-token
  rotation/reuse detection.

### 2026-07-26 — Auth public account HTTP and Brevo SMTP milestone

- Reorganised Auth services into `useraccountservice`,
  `verificationtokenservice`, and `emailservice`; tests now mirror those
  responsibility packages.
- Added validated endpoints for registration, email verification, verification
  resend, password-reset request, and password-reset confirmation under
  `/api/v1/auth`.
- Added request IDs and safe `application/problem+json` responses without
  echoing passwords, email addresses, or presented tokens.
- Added generic `202 Accepted` responses for enumeration-sensitive email
  operations and two-minute configurable verification/reset request cooldowns.
- Added Spring Mail, provider-independent email delivery, asynchronous
  dispatch, escaped Sahha HTML and plain-text templates, and Brevo SMTP
  configuration.
- Added fail-fast credential validation when mail is enabled, a
  source-controlled `.env.mail.example`, and an ignored `.env.mail.local`
  configured with the user's sender address but no SMTP secret.
- Verified 59 Auth tests and 70 complete-reactor tests with no failures,
  errors, or skips.
- Started Auth with its real local profile and verified that an unknown-email
  password-reset request returns generic `202 Accepted`, preserves its request
  ID, and creates no development data.
- Confirmed both local environment files are ignored, the administrator
  password is absent from repository files, and port `8081` is released.
- Live Brevo inbox delivery is pending the user's SMTP login/key and sender
  verification.
- Next: complete the real Brevo inbox smoke test, then implement login/session
  issuance and refresh-token rotation/reuse detection.

### 2026-07-26 — Auth verification-token and credential-security foundation

- Added `V4__create_verification_tokens.sql` with purpose, expiry, single-use,
  replacement/revocation, lifecycle checks, indexes, and one-active-token per
  user and purpose.
- Added `VerificationToken`, its purpose model and locking repository, secure
  256-bit token generation, SHA-256 hashing, configurable BCrypt, and a
  bounded password policy.
- Added transactional registration, email-verification, resend,
  password-reset issuance/consumption, and credential-authentication services.
- Added controlled `UserAccount` methods for verification, password changes,
  successful/failed logins, timed lock release, suspension, and disablement.
- Ensured raw verification/reset tokens are neither persisted nor included in
  JSON or string output, and malformed/unknown tokens return one generic
  service error.
- Applied Flyway version 4 incrementally to `sahha_auth`; confirmed all six
  Auth domain tables are owned by `sahha_auth_app`, with no development users,
  assignments, sessions, refresh tokens, or verification tokens.
- Verified 44 Auth tests and 55 complete-reactor tests with no failures,
  errors, or skips.
- Verified the PostgreSQL administrator password remains absent from repository
  files, `.env.database.local` remains ignored, and the temporary Auth process
  released port `8081`.
- Next: add the validated, enumeration-safe public HTTP contracts for
  registration, email verification, and password reset.

### 2026-07-26 — Auth session and refresh-token persistence foundation

- Added `V3__create_user_sessions_and_refresh_tokens.sql`.
- Added `user_session` with per-device/token-family identity, activity and
  idle/absolute expiry, active-organisation snapshot, credential-version
  snapshot, revocation/compromise state, audit metadata, indexes, and
  optimistic locking.
- Added `refresh_token` with lowercase SHA-256 hash-only persistence,
  same-user/same-session composite foreign keys, parent/replacement lineage,
  one-child/one-parent constraints, global hash uniqueness, one active token
  per session, expiry/revocation checks, and optimistic locking.
- Added `SessionStatus`, `UserSession`, `RefreshToken`,
  `UserSessionRepository`, and `RefreshTokenRepository` using the established
  Lombok no-setter/protected-constructor/safe-string convention.
- Added explicit session activity, revocation, compromise, expiration, token
  issuance, rotation consumption, replacement linking, revocation, and
  active-state behavior.
- Added pessimistic-lock session/token repository queries for the later
  transactional rotation service.
- Added migration, multiple-device, lifecycle, expiry, revocation, unique
  hash, single-active-token, rotation-lineage, cross-user, cross-family,
  JSON-exclusion, safe-string, and hash-format tests.
- Applied Flyway version 3 incrementally to `sahha_auth`; confirmed all five
  Auth domain tables are owned by `sahha_auth_app`, with zero development
  users, assignments, sessions, and tokens.
- Verified 27 Auth tests and 38 complete-reactor tests with no failures,
  errors, or skips.
- Verified the PostgreSQL administrator password remains absent from repository
  files and `.env.database.local` remains ignored.
- Next: add verification-token persistence and credential/account security
  with `V4__create_verification_tokens.sql`.

### 2026-07-26 — Platform-role entity Lombok refactor

- Replaced manual getters and protected constructors in `PlatformRole` and
  `UserPlatformRole` with Lombok `@Getter` and
  `@NoArgsConstructor(access = PROTECTED)`.
- Replaced the manual assignment string output with explicitly included
  Lombok `@ToString` fields.
- Limited `PlatformRole` string output to ID and role code.
- Limited `UserPlatformRole` string output to ID and active state so logging
  does not traverse lazy user/role relationships or expose user information.
- Preserved `@JsonIgnore` on all three Lombok-generated relationship getters.
- Kept setters absent and retained explicit assignment/deactivation behavior.
- Added coverage for the safe string boundaries and relationship getter
  annotations; all 13 Auth tests pass.
- Next remains: add `UserSession` and refresh-token persistence in
  `V3__create_user_sessions_and_refresh_tokens.sql`.

### 2026-07-26 — UserAccount Lombok boilerplate refactor

- Replaced manual `UserAccount` getters, the protected no-argument constructor,
  and `toString()` with Lombok `@Getter`,
  `@NoArgsConstructor(access = PROTECTED)`, and an explicitly included
  `@ToString`.
- Kept setters absent so future credential, lock, verification, and status
  changes remain controlled by domain methods.
- Preserved `@JsonIgnore` on the Lombok-generated password-hash getter.
- Limited string output to account ID and status; email and password hash are
  excluded and covered by a focused security test.
- Verified all 12 Auth tests and the additional focused security-test rerun.
- Next remains: add `UserSession` and refresh-token persistence in
  `V3__create_user_sessions_and_refresh_tokens.sql`.

### 2026-07-26 — Auth platform-role persistence foundation

- Added `V2__create_platform_roles.sql` with global role and user-role
  assignment tables, foreign keys, lifecycle checks, uniqueness, indexes, and
  optimistic-lock versions.
- Seeded the deterministic immutable `PLATFORM_ADMIN` reference role; no
  organisation-scoped role is present in Auth.
- Added `PlatformRole`, `PlatformRoleCode`, `UserPlatformRole`, and their
  Spring Data repositories.
- Added integration coverage for clean migration, the seed boundary,
  assignment/deactivation, duplicate assignment rejection, and lifecycle
  consistency.
- Applied Flyway version 2 to `sahha_auth`, confirmed all Auth tables are owned
  by `sahha_auth_app`, and confirmed the development database has no users or
  assignments.
- Verified 12 Auth tests and 23 complete-reactor tests with no failures,
  errors, or skips.
- Verified the PostgreSQL administrator password is absent from repository
  files and `.env.database.local` remains ignored.
- Next: add `UserSession` and refresh-token persistence in
  `V3__create_user_sessions_and_refresh_tokens.sql`.

### 2026-07-25 — Auth user-account persistence foundation

- Created restricted local `sahha_auth_test` database credentials after
  confirming Docker/Testcontainers were unavailable.
- Connected Auth local and test profiles to their owned databases through the
  gitignored environment file.
- Enabled Flyway validation, Hibernate schema validation, UTC JDBC timestamps,
  and disabled Open EntityManager in View.
- Added `V1__create_user_accounts.sql` with UUID identity, normalized-email
  uniqueness, account status, verification, lock, login, credential-version,
  timestamp, optimistic-lock, check-constraint, and index support.
- Added `UserAccount`, `AccountStatus`, `UserAccountRepository`, and
  `EmailNormalizer`.
- Added a database guard that refuses to clean anything except
  `sahha_auth_test`.
- Added migration, repository, database-constraint, email-normalization, and
  password-hash exposure tests.
- Applied migration version 1 successfully to empty development and test
  schemas and confirmed both remain free of account data.
- Verified eight Auth tests and 19 complete-reactor tests with no failures or
  errors.
- Smoke-started Auth successfully using the real local PostgreSQL profile.
- Next: add platform-role persistence, seed `PLATFORM_ADMIN`, and verify global
  role assignment constraints.

### 2026-07-25 — Auth session and Redis implementation plan

- Defined the final Auth/Organisation/Patient ownership boundary.
- Defined `UserSession` as the persistent refresh-token family aggregate.
- Defined account, platform-role, refresh-token, verification-token,
  security-event, and outbox responsibilities.
- Defined five ordered Auth Flyway migrations.
- Defined a minimal Redis session projection, bounded TTL, cache-aside loading,
  version checks, revocation tombstones, and PostgreSQL fallback.
- Defined registration, login, rotation, reuse compromise, per-device logout,
  global logout, password-reset, and account-suspension workflows.
- Defined the API order and unit, PostgreSQL, Redis, and security test gates.
- Added `docs/AUTH_IMPLEMENTATION_PLAN.md`.
- Next: connect Auth Service to `sahha_auth` and add the first Flyway migration
  and PostgreSQL integration test.

### 2026-07-25 — Local PostgreSQL database foundation

- Detected PostgreSQL `18.1` running on `localhost:5432`.
- Created nine service-owned databases and nine independently credentialed
  restricted login roles.
- Revoked public access to every service database.
- Verified every service role can connect to its own database.
- Verified every service role is denied when connecting to another service's
  database.
- Verified all service roles lack superuser, database creation, role creation,
  and replication privileges.
- Stored generated service connection settings in the gitignored
  `.env.database.local`; no administrator credential was persisted.
- Next: wire Auth Service to `sahha_auth` with Flyway and a PostgreSQL
  integration test.

### 2026-07-25 — Runnable microservice foundation and IntelliJ integration

- Added Config Server plus Organisation, Scheduling, Clinical, Communication,
  Notification, File, and Audit as real Spring Boot Maven modules.
- Expanded the root reactor from four to 12 executable applications.
- Added the agreed layered production, migration, and test package structure
  to every new domain service.
- Assigned non-conflicting local ports, Eureka client configuration, and
  Actuator health/info exposure.
- Added temporary database-free `local` startup profiles for the empty Auth
  and Patient scaffolds.
- Corrected IntelliJ to import the root Maven reactor and removed the stale
  generated Auth run entry that duplicated the canonical shared entry.
- Added 12 individual shared Spring Boot run configurations plus
  `Sahha - Infrastructure` and `Sahha - All Services`.
- Verified 12 context tests, 12 executable JAR packages, and successful startup
  of all 12 applications.
- Next: rebrand the migrated frontend and enforce a clear V1 route/feature
  boundary.

### 2026-07-25 — Current microservice package scaffolds

- Documented the standard domain-service, gateway, discovery, resource, and
  test package structures.
- Added 55 tracked `.gitkeep` placeholders across the four current services.
- Added request/response DTO separation and explicit security, client, event,
  and outbox boundaries to Auth and Patient.
- Added edge-specific config, filters, routing, security, and exception
  boundaries to Gateway.
- Kept Discovery infrastructure-only.
- Removed the empty misspelled Auth `sevice` directory after creating the
  correct tracked `service` directory.
- Verified the Maven reactor: four suites and four tests passed with no
  failures or errors.
- Next: rebrand the migrated frontend and enforce a clear V1 route/feature
  boundary.

### 2026-07-25 — Phase 1 repository baseline and frontend migration

- Initialised the Sahha Git repository on `main`.
- Added root ignore, attributes, editor, README, Maven parent/aggregator, and
  Maven Wrapper files.
- Flattened Discovery, Gateway, Auth, and Patient module paths.
- Migrated 49 approved frontend files into `frontend` and excluded generated
  and repository artifacts.
- Enabled and configured the Eureka discovery server.
- Added temporary external-service-free context-test configuration for the
  empty Auth and Patient scaffolds.
- Verified the backend reactor, frontend types, component tests, production
  build, and prepared browser checks.
- Next: rebrand the migrated frontend and enforce a clear V1 route/feature
  boundary.

### 2026-07-25 — Initial planning baseline

- Added durable Sahha project context.
- Added the phased internship implementation plan.
- Added repository guidance requiring plan maintenance.
- Recorded the current backend and frontend baselines.
- Confirmed that no application code was generated or modified.
- Next: begin Phase 1 with repository normalisation and controlled frontend
  migration when implementation is authorised.
