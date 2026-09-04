# Sahha internship implementation plan

Last updated: 2026-09-04

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
| 3 | Administrative patient registry and duplicate detection | COMPLETE |
| 4 | Doctor availability, appointments, check-in, and real-time appointment notifications | COMPLETE |
| 5 | Consultations, diagnoses, medication, finalisation/corrections, and protected files | COMPLETE |
| 6 | Doctor messaging, referrals, selected-data sharing, revocation, and expiry | IN PROGRESS |
| 7 | Limited patient portal, audit completion, security hardening, and observability | NOT STARTED |
| 8 | Full end-to-end verification, CI/CD foundation, and internship demonstration | NOT STARTED |

Current phase: `Phase 6 — Messaging, referral, and selected sharing`

Current next task: restart Communication and Notification services and run the
Phase 6B live two-doctor acceptance path, verifying direct message delivery,
the persistent notification inbox, Kafka records, and recipient isolation.

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

Status: `COMPLETE`

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
- [ ] Create Docker Compose for PostgreSQL, Kafka, Redis, SeaweedFS, Eureka, Config,
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

Status: `COMPLETE`

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

Status: `COMPLETE`

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
- [x] Add synthetic patient fixtures only.

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

Status: `IN PROGRESS`

Goal: complete availability, appointment, confirmation, and check-in across
administrator/receptionist, doctor, and patient-facing projections.

Tasks:

- [x] Model organisation-specific doctor availability, breaks, locations,
      absences, holidays, slot duration, and timezone.
- [x] Implement deterministic slot calculation.
- [x] Model appointment status and an explicit transition matrix.
- [x] Enforce actor-specific transition permissions.
- [x] Prevent overlaps with database constraints, transactions, and optimistic
      locking.
- [x] Implement create, confirm, reject, reschedule, and cancel commands.
- [x] Implement check-in, start, complete, and no-show commands.
- [x] Require rejection, cancellation, and reschedule reasons.
- [x] Publish minimal appointment domain events through the outbox.
- [x] Consume appointment events in Notification Service.
- [x] Expose authenticated, active-organisation-scoped REST inbox recovery and
      read-state commands through Gateway.
- [x] Deliver authenticated in-app WebSocket notifications.
- [x] Integrate doctor availability and receptionist scheduling UI.
- [x] Integrate receptionist check-in UI.
- [x] Integrate the doctor appointment board.
- [x] Integrate doctor notifications.
- [x] Integrate the limited patient appointment request/status view.
- [x] Add concurrency tests for two users attempting the same slot.

Current Phase 4A implementation state:

- Scheduling Service now owns the doctor availability aggregate, weekly
  windows, breaks, dated time off, location, timezone, appointment duration,
  minimum lead time, booking horizon, audit metadata, and optimistic version.
- Flyway version 1 creates only Scheduling-owned tables and constraints;
  external organisation, membership, and user identifiers remain references,
  not cross-service foreign keys.
- Authenticated doctors can read and replace only their own schedule. Doctors,
  receptionists, and Organisation Administrators can read slots according to
  their live active Organisation membership; a doctor cannot browse another
  doctor's slots.
- Slots are calculated deterministically from weekly rules and subtract
  breaks and time off while enforcing lead time, timezone, booking horizon,
  and a bounded query range. Empty slots are not persisted.
- Gateway routing and OpenAPI descriptions expose the availability contracts
  through the browser entry point.
- [x] The approved React frontend uses the real Gateway API for the doctor
  availability editor and a read-only receptionist slot browser. The focused
  service/page tests, all 56 frontend tests, TypeScript check, and production
  build pass.
- [x] The fresh unrestricted Maven reactor run passed all 7 Scheduling tests
  and all 24 Gateway tests with no failures, errors, or skips. This verifies
  Flyway and Hibernate schema validation, persistence, application startup,
  slot rules, doctor/receptionist role boundaries, CSRF, stale-version
  conflicts, Organisation-context checks, and Gateway forwarding.

Current Phase 4B implementation state:

- Flyway version 2 adds the Scheduling-owned appointment aggregate, immutable
  booking request identifier, appointment status, doctor/patient references,
  availability/location/timezone snapshots, audit attribution, optimistic
  version, and append-only local booking audit event.
- PostgreSQL is the final double-booking authority through a GiST exclusion
  constraint over organisation, doctor, and the half-open appointment time
  range. A pre-check provides a friendly conflict, while the constraint still
  resolves simultaneous transactions safely.
- `POST /api/v1/appointments` derives the appointment end, timezone, and
  location from an exact currently-calculated slot. A repeated identical
  booking request is idempotent; reuse for different details is rejected.
- Receptionists and Organisation Administrators are the only initial booking
  actors. Scheduling revalidates their live context, resolves a minimal active
  doctor through Organisation Service, and resolves an active tenant patient
  registration through Patient Service before persistence. Doctor and patient
  booking remain deferred until their own minimum-necessary patient reference
  contracts exist.
- Booked intervals are subtracted from later available-slot responses. No
  Kafka event is emitted yet; the transactional outbox follows only after the
  booking command passes its backend gate.
- [x] The receptionist slot browser now selects an active patient and submits
  the real booking command through Gateway. All 21 frontend test files and 58
  tests, TypeScript checking, and the production build pass.
- [x] The fresh unrestricted Maven run passed all 38 Organisation, 10
  Scheduling, and 25 Gateway tests with no failures, errors, or skips. This
  verifies Flyway version 2 and Hibernate validation, the minimum doctor
  directory, role/CSRF boundaries, idempotent booking, local audit, occupied
  slot subtraction, and the real two-transaction exclusion-constraint race.

Current Phase 4C implementation state:

- Flyway version 3 adds the current transition reason plus append-only command
  identifiers, prior/new appointment times, transition reasons, and event
  types for confirmation, rejection, rescheduling, and cancellation. The
  organisation-scoped command identifier is unique and existing booking audit
  rows are backfilled from their booking request identifiers.
- The explicit matrix permits requested/rescheduled appointments to be
  confirmed, rejected, rescheduled, or cancelled and confirmed appointments
  to be rescheduled or cancelled. Rejected and cancelled states are terminal
  in this slice; check-in and consultation states remain the next lifecycle
  extension.
- Only the appointment's active doctor can confirm or reject it. The owning
  doctor, receptionist, or Organisation Administrator may reschedule or
  cancel. Organisation Service permits a doctor to resolve only their own
  active scheduling directory record while operational roles retain their
  bounded directory access.
- `GET /api/v1/appointments` accepts a positive range of at most 31 days.
  Operational roles receive the tenant schedule; doctors receive only their
  own appointments. The resource query and every command remain scoped by the
  active organisation.
- Confirm, reject, reschedule, and cancel use an explicit optimistic version
  and idempotent command UUID. Reject, reschedule, and cancel require a bounded
  reason. Rescheduling accepts only an exact currently published slot,
  excludes the current appointment from its own overlap check, and still
  relies on PostgreSQL's exclusion constraint as the final race authority.
- The approved doctor and receptionist appointment routes now use the real
  Gateway contracts. Reception can book, reschedule, or cancel but cannot see
  confirm/reject controls; doctors receive only the actions allowed by their
  current appointment state. Reschedule choices come from live published
  availability rather than an arbitrary timestamp input.
- [x] Frontend TypeScript checking, all 61 tests in 22 files, and the Vite
  production build pass. `git diff --check` also passes.
- [x] The fresh unrestricted Maven run produced 38 passing Organisation tests,
  15 passing Scheduling tests, and 25 passing Gateway tests with zero failures,
  errors, or skips. Flyway version 3, Hibernate schema validation, lifecycle
  policy/security, scoped reads, command idempotency, optimistic versions,
  mandatory reasons, exact-slot rescheduling, audit history, and routing are
  covered.

Current Phase 4D implementation state:

- Flyway version 4 adds a Scheduling-owned appointment outbox linked one-to-one
  with each append-only appointment audit event. It tracks stable event IDs,
  aggregate versions, publication attempts, bounded retry state, and expiring
  publisher claims. Historical audit rows are deliberately not backfilled;
  events are produced for commands committed after this migration.
- Booking and confirm/reject/reschedule/cancel persistence now create their
  audit row and outbox row in the same database transaction. Idempotent command
  replay returns the existing result without creating a duplicate audit or
  event; rolled-back commands cannot leave a publishable event behind.
- Schema-version-1 payloads contain only the identifiers and operational
  appointment snapshot required by Notification Service. Registration and
  membership identifiers, reasons, patient contact details, and clinical data
  are excluded.
- The publisher sends to `sahha.scheduling.appointments.v1`, keyed by
  appointment ID to preserve per-appointment partition ordering. Kafka producer
  acknowledgements and idempotence are enabled.
- Ready rows are claimed in short PostgreSQL transactions with
  `FOR UPDATE SKIP LOCKED`; Kafka I/O occurs after the claim transaction closes.
  Claims expire for crash recovery, retry delay is exponentially bounded, and
  publication acknowledgement requires the current claim token.
- Delivery is intentionally at least once. A failure between Kafka acknowledgement
  and the database publication update can resend the same stable event ID, so
  the future Notification consumer must persistently deduplicate by event ID.
- The publisher remains disabled by default until Kafka and the Notification
  consumer are started deliberately. No Notification Service consumer or
  WebSocket behavior is claimed in this slice.
- Added mapper minimisation, outbox lifecycle/configuration, publisher success
  and failure, transactional HTTP persistence, idempotency, and real
  PostgreSQL lease-expiry recovery coverage. The Scheduling source now contains
  22 test methods across 12 test classes.
- [x] The fresh unrestricted 2026-08-10 run passed all 22 Scheduling tests with
  zero failures, errors, or skips. It verifies Flyway version 4, Hibernate
  validation, same-transaction event persistence, minimized payloads, command
  idempotency, leased claim recovery, and publisher success/retry behavior.

Current Phase 4E implementation state:

- Notification Service now has its first owned PostgreSQL/Flyway model:
  consumed appointment-event receipts, rejected-message receipts, a
  per-appointment resource-version cursor, and doctor in-app notifications.
  No service reads or writes the Scheduling database.
- A conditional Kafka consumer reads
  `sahha.scheduling.appointments.v1` with a stable consumer group, record-level
  acknowledgement, disabled auto-commit, and `read_committed` isolation. It is
  disabled by default until local Kafka is deliberately enabled.
- The schema-version-1 decoder validates every required identifier, event/status
  pairing, appointment period, timezone, resource version, transition snapshot,
  and the Kafka appointment key before persistence.
- Event IDs are durably unique. Duplicate delivery returns without creating a
  second inbox row, while the per-appointment cursor records the highest
  resource version and safely consumes older events as stale.
- Invalid records do not block their Kafka partition or enter application logs
  with their payload. Notification stores only topic/partition/offset, a safe
  reason code, and a lowercase SHA-256 payload fingerprint. The exact Kafka
  coordinate remains available for the later replay procedure.
- The doctor receives an inbox projection for externally authored requested,
  rescheduled, and cancelled appointments. The projection contains the doctor,
  organisation, appointment, status, schedule/location, event, and version
  identifiers required for navigation, but no patient ID, actor ID, request ID,
  reason, contact information, or clinical content.
- Doctor-authored decisions are consumed without notifying the same doctor.
  Confirm/reject currently have no eligible patient-account recipient because
  the Scheduling `patientId` is not an Auth user ID; no unsafe identity linkage
  is inferred.
- REST inbox recovery, read/unread commands, authenticated WebSocket delivery,
  frontend notification integration, and patient-account notification routing
  remain explicitly outside this slice.
- Added 11 Notification test methods covering application startup, Flyway
  version 1, event decoding/rejection, Kafka key/source forwarding, persistent
  event-ID deduplication, stale-version suppression, minimum projection data,
  no invented recipient, and fingerprint-only rejection receipts.
- Static checks pass: the POM is valid XML, Scheduling/Notification source has
  no trailing whitespace, and the inbox entity has no prohibited patient,
  actor, request, reason, contact, or clinical field.
- The first unrestricted Notification run reached all 11 tests: ten passed and
  the stale-version test exposed a fixture error, not a production error. Its
  two events accidentally used different random organisation IDs, correctly
  triggering the tenant immutability guard. All event helper calls now pass an
  explicit organisation ID in the correct position.
- [x] The fresh unrestricted `clean verify` run on 2026-08-11 passed all 22
  Scheduling and 11 Notification tests with zero failures, errors, or skips.
  Both executable JARs were packaged, Flyway versions 4 and 1 were present in
  their compiled resources, and the appointment consumer milestone is now
  verified.

Current Phase 4F implementation state:

- Notification Service now validates the signed access cookie independently,
  requires a paired active-organisation context and organisation role, applies
  cookie/header CSRF protection to read-state commands, and revalidates the
  live membership through Organisation Service before every inbox operation.
- `GET /api/v1/notifications` exposes newest-first bounded pagination and
  `GET /api/v1/notifications/unread-count` exposes the current user's unread
  total. Neither request accepts a browser-supplied user or organisation ID.
- `POST /api/v1/notifications/{notificationId}/read` is owner/tenant scoped,
  hidden as `404` outside that scope, row-locked, and idempotent.
  `POST /api/v1/notifications/read-all` performs one scoped monotonic update
  and increments optimistic versions only for previously unread rows.
- REST DTOs omit source event, organisation, and recipient identifiers as well
  as all patient, actor, request, reason, contact, and clinical fields. Every
  response is non-cacheable and failures use request-correlated safe Problem
  Details.
- Springdoc documents cookie and CSRF security. Gateway now has an
  authenticated load-balanced Notification route; the browser still never
  calls the internal service or Kafka directly.
- Added seven focused Notification test methods and one Gateway routing test,
  bringing the source totals to 18 Notification and 26 Gateway test methods.
  Coverage includes authentication, live membership failure, pagination,
  recipient/tenant hiding, response minimisation, CSRF, idempotent mark-one,
  scoped mark-all, access-token claim validation, OpenAPI, and forwarding.
- Static validation passes: `git diff --check`, Notification POM XML parsing,
  route discovery, and source test discovery. The managed Maven run still
  cannot access the external Maven cache, so unrestricted result inspection is
  used for the backend gate.
- The first unrestricted gate compiled both modules and passed all 26 Gateway
  tests. Its one Notification test-only Mockito restubbing error was corrected
  with non-invoking `doThrow(...).when(...)` exception stubs.
- [x] The fresh unrestricted Notification `clean verify` run on 2026-08-11 at
  19:56 passed all 18 tests with zero failures, errors, or skips. The packaged
  executable JAR contains the Notification controller, resource-server
  configuration, OpenAPI configuration, and inbox service, so Phase 4F is now
  verified.
- No WebSocket/STOMP endpoint, frontend notification adapter, patient-account
  routing, email/SMS delivery, or new Notification database migration was
  added in this slice.

Current Phase 4G implementation state (verified):

- Notification Service now exposes native STOMP at
  `/api/v1/notifications/ws`; clients may subscribe only to
  `/user/queue/notifications`. It does not expose SockJS or a client-send
  application destination.
- The HTTP WebSocket upgrade reuses the signed access cookie and revalidates
  active membership through Organisation Service. STOMP `CONNECT` also
  requires the cookie-bound `X-XSRF-TOKEN`, and exact frontend origins are
  configured rather than wildcard credentialed origins.
- A WebSocket principal combines the authenticated user ID and active
  organisation ID. Two tabs for the same user in different organisations are
  therefore separate realtime recipients rather than sharing a user-only
  queue.
- Notification creation raises an in-process event inside the Kafka-consumer
  database transaction. Delivery runs only after commit, reloads the row by
  notification, organisation, and recipient, and sends the same minimum REST
  DTO through the organisation-bound user destination.
- Realtime delivery is deliberately best effort: a WebSocket failure cannot
  roll back or retry already-committed Kafka processing. The authenticated
  REST inbox and unread-count endpoints remain the recovery source after
  reconnect.
- Gateway was migrated from Server MVC to Server WebFlux because the installed
  MVC Gateway runtime has no WebSocket proxy handler. Existing REST route,
  cookie authentication, platform-role, CORS, safe Problem Details, timeout,
  request-ID, and edge-header contracts were preserved, and an authenticated
  `lb:ws://notification-service` route was added before the Notification HTTP
  route.
- Existing Gateway tests were migrated from MockMvc to WebTestClient. New
  focused tests cover organisation-bound principals, active/inactive
  handshake membership, post-commit delivery targeting/failure isolation,
  required STOMP CSRF, same-user cross-organisation isolation, and an actual
  WebSocket upgrade through Gateway.
- Static `git diff --check` passes. The managed Maven environment cannot access
  the Spring Boot 4.1 parent POM or external Maven network, so backend evidence
  comes from the captured unrestricted clean verification gate.
- The first unrestricted gate stopped in Gateway production compilation
  because Spring 7 removed the `HttpHeaders.FORWARDED` Java constant. The
  sanitised standard header remains `Forwarded`; using that literal restored
  source compatibility without changing edge-header behavior.
- The next unrestricted gate compiled, packaged, and passed all 27 Gateway
  tests, then reached all 28 Notification tests with one active STOMP
  connection error. Spring Security 7.1 installed its XOR-masked messaging
  CSRF interceptor while Sahha deliberately exposes and echoes the raw
  cookie-backed token used by every REST client.
- Notification messaging security now supplies the plain constant-time CSRF
  channel interceptor under Spring Security's expected bean name. The
  duplicate manually registered handshake interceptor was removed because
  `@EnableWebSocketSecurity` already prepends the framework handshake bridge.
  The raw-token success path and missing-token rejection are covered by the
  passing real WebSocket integration tests.
- The following gate proved that raw-token STOMP `CONNECT` now succeeds. Its
  only failure moved to a five-second wait for a client-requested SUBSCRIBE
  receipt, which is not the delivery contract and was not returned by the
  in-memory simple broker. The integration test now waits on
  `SimpUserRegistry` until the exact organisation-bound principal and
  `/user/queue/notifications` subscription are registered, directly verifying
  the required server state without relying on optional receipt behavior.
- The fresh unrestricted clean gate completed successfully on 2026-08-12:
  all 27 Gateway tests and all 28 Notification tests passed with zero failures,
  errors, or skips, and both executable JARs were packaged. This verifies the
  real Gateway WebSocket upgrade, authenticated CONNECT, raw CSRF acceptance,
  missing-CSRF rejection, live membership checks, post-commit delivery,
  failure isolation, and same-user cross-organisation recipient isolation.

Current Phase 4H implementation state (verified frontend boundary):

- The approved doctor header now contains a real private notification inbox.
  Typed Gateway adapters cover newest-first recovery, unread count, mark-one
  read, and mark-all read; unsafe commands reuse the shared cookie-bound CSRF
  client and never accept a browser-supplied user or organisation ID.
- A focused native STOMP-over-WebSocket adapter connects only through
  `/api/v1/notifications/ws`, sends the shared `X-XSRF-TOKEN` on `CONNECT`,
  subscribes only to `/user/queue/notifications`, validates the minimum
  realtime envelope before exposing it to React, and does not add a new
  external frontend dependency.
- The realtime adapter implements negotiated heartbeats, exponential bounded
  reconnect, malformed-message isolation, and clean `DISCONNECT` teardown.
  Every successful initial connection or reconnect invalidates the REST inbox
  and unread-count queries, so persisted REST state remains authoritative over
  best-effort WebSocket delivery.
- The component lifecycle is keyed by authenticated user and active
  organisation. Switching organisation stops the old socket, uses separate
  TanStack Query keys, and opens a new connection only after the new session
  context is visible. The static role-preview mode performs no private REST or
  WebSocket calls.
- Valid realtime appointment messages are deduplicated in the inbox cache,
  update the unread badge, and invalidate only the active organisation's
  appointment query. Selecting one marks it read and navigates to the real
  doctor appointment board; mark-all-read updates the scoped cache
  monotonically.
- Added eight focused tests across the REST adapter, STOMP protocol client,
  and React component. They cover CSRF, private subscription, payload
  validation, reconnect, teardown, REST recovery, active-organisation change,
  unread merging, read-state mutation, and appointment navigation.
- Frontend validation passes: all 69 tests in 25 files, TypeScript and the Vite
  production build, `git diff --check`, and the complete responsive browser
  regression. The browser check now opens the notification panel at 375 px,
  verifies its bounds, and captures it without horizontal overflow.
- The full live-stack browser path remains an exit-level manual integration
  check: Gateway, Auth, Organisation, Scheduling, Notification, PostgreSQL,
  Kafka, and the real frontend must be running together before claiming that a
  human doctor received a newly booked appointment in real time.

Current Phase 4I implementation state (verified):

- Scheduling's explicit transition matrix now permits only
  `CONFIRMED -> CHECKED_IN -> IN_PROGRESS -> COMPLETED` and
  `CONFIRMED -> NO_SHOW`. Completed and no-show appointments are terminal;
  requested or merely rescheduled appointments must still be confirmed first.
- Receptionists and Organisation Administrators may check in a patient. Only
  the appointment's owning active doctor may start or complete it. The owning
  doctor, receptionist, or Organisation Administrator may record no-show, but
  never before the scheduled start instant. These checks occur after live
  active-organisation resolution and remain resource scoped.
- Four CSRF-protected Gateway-routed Scheduling commands are available:
  `POST /api/v1/appointments/{id}/check-in`, `/start`, `/complete`, and
  `/no-show`. Each command requires a UUID command identifier and the current
  optimistic version, returns safe `403`/`409` failures, and is documented by
  Springdoc through the controller contract.
- Every successful transition updates the aggregate and writes one append-only
  actor/membership/request-attributed audit event plus one minimized outbox
  event in the same transaction. Command replay creates no duplicate audit or
  outbox row; stale versions and command-ID reuse remain conflicts.
- Scheduling Flyway version 5 expands only owned audit/outbox event-type
  constraints. The existing appointment status and status-reason constraints
  already supported the four states, so no unrelated schema or cross-service
  ownership was introduced.
- Notification Service accepts the four new schema-version-1 events through
  Flyway version 2 and exact event/status validation. A receptionist-authored
  check-in creates one minimum `PATIENT_CHECKED_IN` doctor notification;
  doctor-authored start/complete events are consumed without self-notifying,
  and no patient recipient is inferred from the Scheduling patient ID.
- The doctor notification frontend contract now validates and renders the
  `PATIENT_CHECKED_IN` projection. No receptionist/doctor lifecycle controls
  were added to React in this backend slice.
- The first clean gate compiled successfully and applied Scheduling migration
  5, then exposed a test-fixture-only PostgreSQL precision problem: storing
  `LocalTime.MAX` rounded the synthetic window into equal start/end values.
  Restoring the established 09:00-11:00 fixture window corrected the test
  without changing production behavior.
- The fresh clean reactor verification passed all 27 Scheduling tests and all
  31 Notification tests with zero failures, errors, or skips. It validated
  both migrations against PostgreSQL, Hibernate schema validation, role and
  resource boundaries, the no-show time guard, idempotency, optimistic
  versions, audit/outbox atomicity, event decoding/projection, REST inbox,
  WebSocket delivery, security, and packaged both executable JARs.
- All 69 frontend tests in 25 files, TypeScript checking, the Vite production
  build, POM XML parsing, and whitespace validation pass.

Current Phase 4J implementation state (verified frontend boundary):

- The typed appointment Gateway adapter now exposes check-in, start, complete,
  and no-show commands. Every request uses the shared cookie/CSRF client and
  carries only a generated command UUID plus the appointment's current
  optimistic version.
- The real appointment board derives controls from both role scope and current
  state. Reception may check in a confirmed patient; the doctor may start a
  checked-in appointment and complete one in progress. Reception and doctor
  no-show controls are offered only after the scheduled start, while the
  backend remains the final clock and authorisation authority.
- Reception now has a non-clinical waiting projection containing only the
  organisation-scoped registration reference, owning doctor reference,
  location, and check-in update time. It does not resolve a patient clinical
  record or grant reception any clinical action.
- Successful commands replace only the matching versioned appointment in the
  active organisation's TanStack Query cache and invalidate relevant slot
  queries. Existing realtime check-in notification handling invalidates the
  same organisation-scoped appointment query for the doctor.
- Added three focused component tests and expanded the REST adapter test. They
  cover reception check-in/waiting, doctor start/complete, no-show temporal
  visibility, command paths, CSRF reuse, UUID commands, and optimistic
  versions.
- Added a reduced-motion-safe shared hover treatment to primary, secondary,
  icon, text, and back-button primitives. This supplies a visible interaction
  response to existing shared controls without replacing the approved visual
  language or changing disabled behavior.
- The complete frontend gate passes: all 72 tests in 25 files, TypeScript and
  the Vite production build, plus 11 phone/tablet/desktop browser captures
  with no document-level horizontal overflow. The browser gallery covers the
  established role surfaces; the real live lifecycle route still requires the
  full-stack Phase 4 acceptance run.
- The frontend consistency audit found three competing form systems: inline
  `order-composer` cards (including Add organization), right-side
  `management-form` drawers, and `workflow-form` drawers/settings forms. They
  differ in label/input sizing, spacing, footer placement, focus behavior, and
  whether opening the form shifts the page. It also found remaining Aegis
  branding, mixed organization/organisation terminology, very small 7-9 px
  support text in older surfaces, and domain-specific unclassed buttons that
  do not all have an explicit hover state. These require a focused shared-form
  and interaction consolidation, not a design regeneration.
- No backend, schema, Kafka, Redis, or database behavior changed in this
  frontend slice. Live role enforcement and event delivery remain covered by
  the previously verified backend tests until the complete stack is exercised.

Current Phase 4K implementation state (verified patient boundary):

- Patient Service Flyway version 3 adds a durable one-to-one account link for
  an Auth user and stable patient identity. Linking requires an authenticated
  unscoped account, authoritative active and email-verified Auth state, an
  active organisation registration, matching organisation/medical-record
  number/date of birth, and an exact match between the registration email and
  authoritative account email. An identical retry is idempotent; conflicting
  links are rejected.
- The account-link audit and outbox record is committed with the link and
  excludes email, date of birth, medical-record number, contact data, and
  clinical data. Patient self-service reads resolve registrations from the
  durable link on every request rather than accepting an arbitrary patient ID
  or inferring ownership from email alone.
- Patient and Scheduling token validators now accept only internally
  consistent identity modes: patient accounts may use a genuinely unscoped
  token with neither active organisation nor organisation roles, while staff
  requests retain paired organisation and role claims. Mixed or partial claim
  sets remain invalid.
- Organisation Service exposes a minimum authenticated patient scheduling
  doctor projection only for an active organisation and an active doctor
  membership. Its existing staff directory endpoint keeps the original live
  membership and tenant restrictions.
- Scheduling Flyway version 6 records whether a booking actor is `STAFF` or
  `PATIENT` and permits a null membership reference only for a patient actor.
  Patient-owned endpoints list real doctors and slots, create idempotent
  requests, and list status history after resolving the selected registration
  through Patient Service. The patient response excludes patient, membership,
  booking-request, actor, and audit identifiers.
- The approved patient visits route now uses these Gateway APIs. It provides a
  one-time verified record link, registration/doctor/date selectors, published
  slots, transactional appointment requests, and a 31-day status view. It
  does not invent a clinical reason field or expose the old mock booking
  drawer. Service outages are not presented as a request to relink an account.
- Added focused Patient account-link/negative-ownership/token tests,
  Organisation minimum-directory tests, Scheduling patient booking/query/token
  tests, frontend REST contract tests, and patient-page behavior/error tests.
  The full affected backend reactor passed 38 Organisation, 12 Patient, and 29
  Scheduling tests with zero failures, errors, or skips. The complete frontend
  suite passed all 77 tests in 26 files and the TypeScript/Vite production
  build succeeded. `git diff --check` passes.
- The production build reports a non-blocking existing optimisation signal:
  the main JavaScript chunk is approximately 504 kB after minification. Route
  code splitting must be measured in the pre-Phase-5 performance pass rather
  than changed speculatively.
- Live cross-service browser acceptance, patient-side realtime status delivery,
  operational measurements, and the consolidated test guide are not claimed
  by this automated slice. Patient status currently recovers through its real
  REST query/refresh path.

Current Phase 4L documentation state (verified guide, live evidence pending):

- Added `docs/PHASE_4_WORKFLOW_AND_OBSERVABILITY_GUIDE.md` as the consolidated
  Phase 4 learning and evidence guide. It maps the actual React-to-Gateway,
  service/database, outbox/Kafka, Notification/WebSocket, and REST-recovery
  workflow without treating Kafka as a browser-facing integration.
- Explained the measured Vite optimisation warning: the main application
  chunk is 503.73 kB minified and 120.18 kB gzip because `App.tsx` statically
  imports most role pages. The guide distinguishes transfer, parse/execution,
  cache, and unused-role-code costs and proposes measured route-level lazy
  loading instead of merely raising the warning limit.
- Defined provisional Sahha local percentile budgets separately from public
  browser responsiveness guidance. They are engineering investigation
  thresholds, not healthcare regulations, production SLAs, or claims about
  current Sahha performance.
- Documented the complete synthetic Phase 4 role workflow, expected state and
  denial checks, browser and PowerShell Gateway timing, Kafka topic and
  consumer-lag inspection, safe Memurai key/TTL/latency inspection,
  PostgreSQL `pg_stat_statements` and `EXPLAIN (ANALYZE, BUFFERS)` use, and a
  single correlation/evidence worksheet.
- The observed local baseline is recorded honestly: PostgreSQL 18.1 and
  Memurai respond locally, all Sahha application ports were stopped, Kafka is
  not installed/listening on port 9092, and `pg_stat_statements` is not yet
  installed in the inspected service databases. No live event or response
  percentile is therefore claimed.
- The subsequent native Windows setup is user-verified: Apache Kafka 4.3.1
  KRaft storage was formatted successfully and the broker is running on local
  port 9092. A short `SUBST` drive avoids the Windows batch classpath limit,
  explicit Log4j paths avoid malformed drive-letter URLs, and an explicit
  256-512 MiB development heap bypasses Kafka's obsolete `wmic` probe. Topic,
  producer/consumer, and end-to-end event evidence remain pending.
- Scheduling and Notification now use Spring Boot 4's
  `spring-boot-starter-kafka` rather than only the Spring Kafka library, so
  Boot creates the required `KafkaTemplate` and listener-container beans.
  Their local IntelliJ configurations explicitly enable the outbox publisher
  and appointment consumer against `localhost:9092`.
- Live verification confirms both `/actuator/health` endpoints are `UP`, topic
  `sahha.scheduling.appointments.v1` has one partition with broker 1 as leader
  and in-sync replica, and consumer group
  `sahha-notification-appointments-v1` is actively assigned. Its log end is
  zero, so no application event or offset has yet been claimed.
- The affected Maven reactor passes all 29 Scheduling tests and all 31
  Notification tests with zero failures, errors, or skips. The runtime topic
  and consumer evidence plus `git diff --check` also pass.
- A fresh 2026-08-16 preflight confirms PostgreSQL accepts connections,
  Memurai returns `PONG`, and the Kafka appointment topic still has broker 1 as
  its leader and in-sync replica. All required Sahha application ports,
  Eureka, and the frontend were stopped during the check, so readiness is not
  yet claimed. Kafka reports no durable Notification consumer group while the
  consumer is stopped and no offset has been committed; this is an expected
  empty-topic state, not evidence of a broker failure.
- The 2026-08-17 backend readiness rerun passes: Auth, Organisation, Patient,
  Scheduling, Notification, and Gateway report `UP`; Eureka reports all six as
  `UP`; and the Notification consumer is actively assigned to partition 0 of
  the appointment topic. Its current offset remains unset because the topic
  log end is zero. The React frontend is not listening on port 5173, so the
  complete readiness gate remains open and no appointment test data was
  created.
- The frontend subsequently passed with HTTP 200 at
  `http://localhost:5173`. Vite is bound to IPv6 loopback (`::1`) in this run,
  so `127.0.0.1:5173` is not an equivalent reachability check. The full Phase
  4 infrastructure/application readiness gate now passes.
- Read-only fixture inspection confirms five active, verified Auth accounts,
  one active synthetic clinic, active Organisation Administrator/doctor/
  receptionist memberships, two active departments, and a completed doctor
  profile. Scheduling contains no availability, appointment, or outbox rows,
  providing a clean baseline for the correlated event test.
- The existing unlinked patient registration has a different email from the
  verified patient Auth account. The test must therefore create a fresh
  synthetic registration using the exact verified patient email before linking;
  it must not bypass Patient Service ownership or edit the database directly.
- The doctor published the first live schedule through the React application.
  Read-only Scheduling verification confirms the expected clinic, doctor and
  membership ownership, `Africa/Tunis`, 30-minute duration, zero lead time,
  30-day horizon, location `Cardiology Room A`, and Tuesday/Wednesday
  `08:00–11:00` weekly windows. There are still zero appointments and zero
  appointment outbox events.
- The receptionist created a fresh active synthetic registration through the
  React application with the exact verified patient Auth email. Read-only
  Patient Service verification confirms its clinic ownership, generated
  registration and identity IDs, medical-record number, date of birth, and
  expected unlinked state. The previous differently addressed registration
  remains untouched.
- The patient completed the one-time link through the React application.
  Read-only Patient Service verification confirms the link belongs to the
  expected patient identity and the authenticated verified patient user; the
  link actor equals the linked user. Scheduling remains at zero appointments
  and zero outbox events immediately before the receptionist booking.
- The receptionist created appointment
  `ae686935-5244-4854-bc38-0e38a0d28bf6`; the doctor received its realtime
  request notification and then confirmed it. Scheduling records the correct
  receptionist booking attribution, doctor confirmation attribution, final
  `CONFIRMED` status, and version 1.
- The requested and confirmed outbox rows each published exactly once without
  error. Kafka partition 0 contains them at offsets 0 and 1 with the appointment
  ID as the key, and the Notification group has committed offset 2 with zero
  lag. Notification recorded both source offsets, created exactly one doctor
  inbox row for the request, advanced its cursor to resource version 1, and has
  zero rejected events. Confirmation has `NO_ELIGIBLE_RECIPIENT`, as expected
  because the confirming doctor is not sent another notification.
- Observed pipeline timestamps, distinct from HTTP response time, were about
  2.45 seconds from requested-event creation to outbox publication and 0.26
  seconds from publication to Notification processing; confirmation measured
  about 0.64 seconds and 0.01 seconds respectively. Browser delivery was
  manually confirmed, but an instrumented WebSocket arrival timestamp is not
  claimed.
- Manual reload verification confirms the previously delivered doctor
  notification remains available through the authenticated REST inbox after
  realtime delivery. The linked patient page also recovers the same appointment
  as `CONFIRMED`, proving the minimum patient-owned status projection without
  an organisation membership.
- The linked patient requested a second slot without an organisation membership.
  The doctor received the request in realtime, reception recovered it, and the
  doctor rejected it with a required reason. Scheduling attributes booking to
  the patient user with no staff membership, attributes rejection to the doctor
  membership, persists the same nonblank reason in current state and append-only
  audit, and ends at `REJECTED` version 1.
- The second request/rejection outbox rows each published once without error at
  Kafka partition 0 offsets 2 and 3. Notification consumed both, created exactly
  one unread doctor request notification, advanced the appointment cursor to
  version 1, rejected no events, and reports group offset 4/log end 4/lag 0.
  Rejection has no eligible in-app recipient because the patient projection
  currently uses authenticated REST recovery.
- Observed second-path pipeline timestamps were about 0.34 seconds from request
  event creation to publication and 0.01 seconds from publication to consumer
  persistence; rejection measured about 0.71 seconds and 0.01 seconds
  respectively. These are event-pipeline observations, not HTTP percentiles.
- Manual REST recovery confirms both the linked patient and receptionist views
  expose the second appointment as `REJECTED` with its recorded reason. The
  first appointment remains `CONFIRMED` for the time-gated check-in path.
- A linked-patient request using another real but unlinked registration ID is
  concealed as `404` and returns no foreign patient or appointment data. After
  returning to the portal, only the correct linked patient projection is shown.
- Two receptionist attempts against the doctor-only confirmation command were
  denied with `403`: one omitted CSRF and one supplied a valid CSRF token but
  lacked doctor authority. Read-only verification confirms the protected
  appointment remains `CONFIRMED` version 1 and the aggregate audit/outbox
  counts remain unchanged at four each.
- The owning doctor's tampered start request against the terminal rejected
  appointment returned the expected `409`. Read-only verification confirms it
  remains `REJECTED` version 1 with the original reason, while total audit and
  outbox counts remain four. At 00:01 Africa/Tunis, the first appointment's
  09:30 positive check-in path is still correctly time-gated.
- The owning doctor's pre-start no-show request also returned the expected
  `409`. The first appointment remains `CONFIRMED` version 1 with its original
  update timestamp, and aggregate audit/outbox counts remain four, proving the
  temporal denial is side-effect free.
- On 2026-08-19 the receptionist created a third disposable appointment,
  `0c225e46-8208-45f9-9213-e176eb22da57`, for 2026-08-25 10:30
  Africa/Tunis. Scheduling persists it as `REQUESTED` version 0 with `STAFF`
  booking attribution, and the doctor manually confirmed realtime receipt.
- Its request outbox row published once, Notification persisted
  `NOTIFICATION_CREATED` from Kafka partition 0 offset 4, and the consumer
  group committed offset 5 with log end 5 and zero lag.
- Read-only verification confirms all three live-test appointments still exist.
  The receptionist board displays only two because its rolling lower bound is
  the exact current instant minus one day; the 2026-08-18 09:30 confirmed
  appointment is older than that cutoff. This is a frontend history/filter
  limitation, not data loss, and remains to be resolved explicitly.
- The receptionist rescheduled the third appointment from 2026-08-25 10:30 to
  2026-08-26 09:00 Africa/Tunis with the required reason. Scheduling records
  `RESCHEDULED` version 1, while the append-only audit preserves both times,
  `REQUESTED -> RESCHEDULED`, the reason, and `STAFF` attribution.
- The reschedule outbox row published exactly once. Notification consumed it
  from Kafka partition 0 offset 5, created exactly one
  `APPOINTMENT_RESCHEDULED` inbox row, and the consumer group committed offset
  6 with log end 6 and zero lag. The doctor manually confirmed that this
  reschedule notification arrived without a page refresh.
- The receptionist then cancelled the rescheduled appointment with a required
  reason. Scheduling records `CANCELLED` version 2 and an append-only
  `RESCHEDULED -> CANCELLED` staff-authored audit row without changing the
  rescheduled appointment time.
- Its cancellation outbox row published once at Kafka partition 0 offset 6.
  Notification consumed it, created exactly one cancellation inbox row, and
  reports no rejected events; the consumer group committed offset 7 with log
  end 7 and zero lag. The doctor manually confirmed that the cancellation
  notification also arrived without a page refresh.
- All 22 PowerShell examples in the guide pass PowerShell parser validation,
  Markdown fences are balanced, and `git diff --check` passes. No application
  code, runtime configuration, database schema, or credentials changed in
  this documentation slice.

Current Phase 4M frontend refinement state (verified automated boundary):

- The doctor and receptionist appointment board now exposes explicit
  `Upcoming` and `History` controls. Each query is exactly 31 days or less,
  the windows meet at the local start of today without overlap, and the older
  confirmed appointment is no longer silently inaccessible.
- Opening an unread appointment notification now updates its cached read state
  and decrements the bell badge immediately. A failed read command restores
  both the inbox and unread-count snapshots; the successful server response
  remains authoritative.
- Added one reusable accessible management drawer with unique labelling,
  focus trapping, focus restoration, Escape/scrim close, scroll locking, and
  responsive standard/wide variants. Platform organisation creation,
  department create/edit, staff invitations, patient registration, and the
  existing management drawers now share this frame and field system.
- Preserved the existing approved typography, palette, responsive layout, and
  shared reduced-motion-safe button hover/press behavior. Added consistent
  keyboard focus visibility for selects and invalid-field styling without a
  new frontend dependency.
- Focused coverage passed 25 tests across seven changed files. The complete
  frontend gate passed 81 tests in 27 files, TypeScript checking, the Vite
  production build, and `git diff --check`.
- The responsive browser regression passed all 11 existing phone, tablet, and
  desktop captures with no document-level horizontal overflow; the private
  notification panel remains bounded at 375 px. The main chunk warning remains
  non-blocking at 504.90 kB minified and 120.85 kB gzip and is still reserved
  for the measured optimisation pass.
- Live authenticated verification passed. History exposed appointment
  `ae686935-5244-4854-bc38-0e38a0d28bf6`; the receptionist checked it in, the
  doctor received `PATIENT_CHECKED_IN` in realtime, and opening the notification
  decremented the badge and persisted its `read_at` value.
- The doctor then started and completed the appointment. Scheduling persisted
  the exact `CONFIRMED -> CHECKED_IN -> IN_PROGRESS -> COMPLETED` sequence at
  versions 1 through 4 with append-only audit records and one-attempt outbox
  publication for each transition.
- Notification consumed Kafka partition 0 offsets 7, 8, and 9. Check-in created
  the intended doctor notification; doctor-authored start and completion were
  recorded as `NO_ELIGIBLE_RECIPIENT` to avoid self-notification. The consumer
  group reached offset 10 of 10 with zero lag and the rejected-event table
  remains empty.

Current Phase 4N performance-baseline tooling state (verified boundary):

- Added `scripts/performance/Measure-SahhaGateway.ps1`, a Windows PowerShell
  5.1-compatible sequential Gateway sampler for the five implemented roles.
- The sampler accepts passwords only through a masked secure prompt, keeps
  authentication cookies in memory, logs out the temporary session, and writes
  a sanitized JSON report under the user's temporary directory without email,
  password, cookies, CSRF values, or JWTs.
- It records a first request separately, performs one unrecorded warm-up, then
  calculates nearest-rank p50, p95, p99, maximum, errors, and target result over
  30 warm requests by default. A first request is labelled cold only through an
  explicit switch after a deliberate service restart.
- The live unauthenticated probe passed through Gateway in 60.39 ms without
  creating a login session. PowerShell AST parsing also passed. This probe is a
  tool validation result, not an authenticated Phase 4 performance baseline.
- The guide now uses the compatible `New-Object` web-session construction and
  documents the preferred secure sampler command.
- The authenticated receptionist sampler subsequently completed at 16:20
  Africa/Tunis against the real local Gateway and active synthetic organisation.
  Auth session, organisation memberships, patient directory, available-doctor
  directory, and appointment history each received 30 sequential warm samples.
- All 150 recorded requests and all five first observations returned without an
  error. Warm p95 values were 52.65, 66.43, 104.82, 65.07, and 80.75 ms
  respectively; every result passed its 250 or 500 ms local target.
- Services were already warm, so the separately recorded first observations are
  not claimed as cold measurements.
- The authenticated doctor sampler then completed at 16:31 Africa/Tunis using
  the same local Gateway, organisation, and 30-sample method. Session,
  memberships, doctor availability, appointment history, and notification inbox
  produced warm p95 values of 56.44, 42.35, 89.87, 61.72, and 79.06 ms.
- All five doctor first observations returned `200`, all 150 measured requests
  completed without errors, and every p95 passed its local 250 or 500 ms target.
  That initial doctor run did not claim cold evidence because the services were
  already warm.
- A controlled restart then produced the explicit cold-stack first pass at
  16:40 Africa/Tunis. All five first observations returned `200`; Auth session,
  memberships, availability, appointment history, and notification inbox took
  49.97, 53.91, 793.09, 96.40, and 842.09 ms respectively.
- The 150 following warm samples again had zero errors and p95 values of 63.17,
  69.88, 103.49, 76.27, and 87.62 ms, all within their local targets. The larger
  first Scheduling and Notification readings are recorded as first-use
  initialization evidence, not production cold-start SLAs.
- The Gateway safe-read warm/cold baseline is recorded without an identified
  optimisation candidate.
- The 2026-08-21 Memurai baseline passed. A five-second native sample reported
  0/2/0.48 ms minimum/maximum/average over 322 samples; 500 persistent-connection
  PINGs produced p50 0.0242 ms, p95 0.1133 ms, p99 0.1449 ms, and maximum 0.2496
  ms against the 5 ms p95 target.
- Memurai used 1.41 MB of its 256 MB limit with fragmentation ratio 1.00,
  `allkeys-lru`, zero evictions, zero rejected connections, zero blocked clients,
  and an empty slow log. The cumulative hit ratio was 67.92% from 1,122 hits and
  530 misses and is recorded as context rather than a target.
- Safe metadata-only scans found 19 expiring string session entries using 10,296
  bytes, all long-TTL tombstone candidates after sampler logout; no active
  30-second projection or rate-limit key existed at that instant. No cached
  session value was read or recorded.
- The latency monitor remains deliberately disabled, so `LATENCY DOCTOR` had no
  diagnosis. The Memurai inspection and measurement gate is complete without an
  identified optimisation candidate.
- The 2026-08-21 read-only PostgreSQL preflight verified PostgreSQL 18.1 on port
  5432 and located its active configuration. `shared_preload_libraries` is
  empty, query IDs are `auto`, I/O timing is off, and no setting has a pending
  restart.
- `pg_stat_statements` 1.12 is available but not installed in any of the ten
  `sahha_*` databases. No configuration, extension, statistics, or service state
  changed during the preflight.
- All active service databases are approximately 8–10 MB with cumulative
  buffer-hit ratios of at least 99.92%, zero temporary files, and zero deadlocks.
  Reported I/O times are zero because tracking is disabled.
- Small-table estimates and scan counters were recorded without interpreting
  them as bottlenecks. Scheduling outbox polling is visible through a high
  cumulative index-scan count, but no query, index, or polling change is justified
  until statement-level timing is enabled and measured.
- On 2026-08-22 `ALTER SYSTEM` wrote the statement preload, explicit query IDs,
  and query I/O timing. Configuration reload activated `compute_query_id=on`
  and `track_io_timing=on`; the preload is valid and correctly awaits restart.
- The attempted Windows service restart was rejected because the process did
  not have Service Control elevation. PostgreSQL remained running on port 5432,
  no application interruption occurred, and no extension was created at that
  point.
- On 2026-08-24 the Administrator restart completed. The statement preload,
  explicit query IDs, and query I/O timing are all active with no pending
  restart; the default statement capacity is 5,000 with top-level tracking and
  persistence enabled.
- `pg_stat_statements` 1.12 is installed and queryable only in Auth,
  Organisation, Patient, Scheduling, and Notification. Future-service and test
  databases remain untouched, and no service-account role grants changed.
- The shared collector reset at 19:39:43 Africa/Tunis. No application database
  connections were active during verification, so controlled Gateway workload
  generation and statement/plan capture are the exact next measurement task.
- On 2026-08-24 the native Windows Kafka broker was recovered without
  reformatting its KRaft storage. A hidden newline in `KAFKA_LOG4J_OPTS` caused
  the initial batch syntax error; 158 already-tombstoned files and 51 retained
  checkpoints had a Windows read-only attribute that blocked KRaft recovery.
  Only those attributes were cleared and Kafka performed its own cleanup.
- Kafka subsequently exposed a Windows file-move conflict while compacting
  `__consumer_offsets`. For this synthetic local run only, the broker was
  started with `--override log.cleaner.enable=false`; port 9092 is verified.
  This bounded workaround is not a production or Azure configuration.
- The same readiness check found Memurai stopped and Auth plus Organisation
  absent from Eureka. Gateway, Audit, Patient, Scheduling, Notification,
  Discovery, PostgreSQL, and Kafka are available, so workload capture remains
  pending until the three missing local components are started.
- The subsequent 2026-08-24 readiness check passed: PostgreSQL, Memurai,
  Gateway, Auth, Organisation, Patient, Scheduling, Notification, Audit,
  Discovery, and Kafka all accepted connections; every application health
  endpoint returned `UP`, and all seven application services registered `UP`
  in Eureka.
- A call-count snapshot was captured across 112 existing query IDs before 30
  warm samples for each receptionist and doctor endpoint. All 300 measured
  Gateway requests completed without errors. Doctor p95 values ranged from
  50.25 to 59.20 ms; receptionist p95 values ranged from 50.44 to 67.33 ms.
- Forty-eight PostgreSQL statements changed during the controlled interval.
  The frequent Scheduling outbox poll ran 463 times at a cumulative mean of
  0.0298 ms; representative patient-directory, appointment, availability,
  membership, and notification reads averaged 0.0233-0.0565 ms. No statement
  used temporary blocks.
- Representative `EXPLAIN (ANALYZE, BUFFERS)` reads executed in 0.056-0.206 ms
  across Organisation, Patient, Scheduling, and Notification. The Auth plan
  took 1.594 ms after one physical page read. Existing indexes supported the
  key lookups, including `ix_user_platform_role_active_user`,
  `ix_membership_user_status`,
  `uq_patient_registration_organisation_patient`,
  `ix_appointment_outbox_ready`, and
  `ix_in_app_notification_recipient_history`. No index or query change is
  justified by this small synthetic baseline.
- Post-test verification kept every application health endpoint `UP`, Memurai
  returned `PONG`, and Kafka remained reachable. The appointment topic has one
  partition with broker 1 as leader and in-sync replica; Notification is at
  offset 10 of 10 with zero consumer lag.

Current Phase 4O frontend consistency state (verified boundary):

- Replaced remaining visible Aegis identity in the landing, authentication,
  shared portal, staff, legal, browser-title, metadata, and error-boundary
  surfaces with Sahha. Legacy CSS selectors, asset paths, and browser storage
  keys remain unchanged intentionally so the approved design and saved local
  demo state stay compatible.
- Normalised the real Platform Administrator organisation directory and form to
  the project's `organisation` terminology, including headings, labels, states,
  errors, accessible names, and component tests. The platform navigation and
  organisation portal label now use the same terminology.
- Added Sahha identity and document-title assertions to the prepared 11-route
  browser gallery and retained its keyboard, drawer, notification-panel, and
  document-level overflow checks.
- Added `frontend/scripts/role-live-smoke.mjs` plus
  `scripts/performance/Test-SahhaFrontendRoles.ps1`. The secure wrapper prompts
  for synthetic doctor and receptionist passwords, stores no credentials,
  checks six real Gateway-backed routes across phone/tablet/desktop widths,
  detects failed API responses, verifies the notification panel, writes only
  sanitized screenshots, and logs both temporary sessions out.
- `npm.cmd run typecheck`, 9 focused tests, all 27 frontend test files and 81
  tests, the production build, and all 11 prepared browser checks passed. The
  existing approximately 505 kB legacy main-chunk advisory remains recorded and
  is not a regression from these changes.
- PowerShell AST parsing and Node syntax checks pass. After the backend stack
  became available, Vite was started on port 5173 and `npm.cmd run smoke:auth`
  passed against the real Gateway: real-auth mode was active, CSRF was issued,
  an unknown user returned `401` with a request ID and no access cookie, and the
  375 px registration route had no horizontal overflow.
- The developer then ran the secure credentialed role gate. All six real
  Gateway-backed scenarios passed: doctor appointments at 1440 px, doctor
  availability at 1024 px, doctor overview and notification panel at 375 px,
  receptionist patients at 1440 and 375 px, and receptionist appointments at
  1024 px. Every document/body width matched its viewport, every title ended in
  `| Sahha`, all six exposed Sahha branding, and the aggregate API-error count
  was zero. The wrapper logged both sessions out and printed no credential.

Mandatory Phase 4-to-Phase 5 acceptance and optimisation gate:

- [x] Complete the limited patient appointment request/status projection with
      explicit account-to-patient linkage and negative authorisation tests.
- [x] Run the entire live Phase 4 journey through Gateway with Platform
      Administrator, Organisation Administrator, receptionist, doctor, and
      patient roles, including the principal denial cases.
- [x] Produce one step-by-step guide for starting the local stack and testing
      every implemented application part, including exact accounts, expected
      HTTP states, UI evidence, Swagger checks, and recovery checks.
- [x] Include a repeatable Gateway response-time baseline in that guide using
      browser Network timing and command-line samples, then record warm/cold
      results and percentile targets before attempting optimisation.
- [x] Include Kafka inspection instructions for topic records, keys, headers,
      partitions, consumer-group lag, outbox publication, duplicate handling,
      and verification that event payloads remain minimal.
- [x] Include Memurai/Redis inspection instructions for safe key discovery,
      types, TTLs, session projections, revocation tombstones, and rate-limit
      counters without exposing reusable credentials.
- [x] Include PostgreSQL query-time instructions using synthetic data,
      `pg_stat_statements`, and `EXPLAIN (ANALYZE, BUFFERS)` for selected slow
      requests, with before/after index evidence and no speculative tuning.
- [x] Consolidate V1 creation/edit forms around one shared form/drawer system,
      finish explicit hover/focus states, normalize visible Sahha terminology,
      and extend browser coverage to the real Phase 4 role workflow.
- [x] Do not start Phase 5 until the guide, measurements, live acceptance,
      security denials, and targeted UI consistency pass are recorded and
      verified.

Exit criteria:

- [x] The doctor configures availability.
- [x] The receptionist books an available slot and cannot double book it.
- [x] The doctor receives a real-time notification and confirms/reschedules.
- [x] The receptionist checks the patient in.
- [x] Invalid state transitions and stale versions return safe conflicts.

## 8. Phase 5 — Clinical record and protected files

Status: `COMPLETE`

Goal: conduct, finalise, and safely correct a basic consultation with protected
medical documents.

Milestone sequence:

### Phase 5A — Authorised draft consultation foundation

Status: `COMPLETE`

- Connect Clinical Service to its restricted `sahha_clinical` database and an
  isolated test database using Flyway, JPA schema validation, and the existing
  local secret-loading convention.
- Add resource-server JWT validation, cookie/CSRF enforcement, request IDs,
  safe Problem Details, OpenAPI, Gateway routing, and Eureka-aware internal
  clients using the established service patterns.
- Add a minimal Scheduling clinical-context decision endpoint. Scheduling
  remains authoritative for appointment state and returns only appointment,
  organisation, patient-registration/patient, doctor, status, time, and version
  data after validating the active organisation and treating doctor.
- Create a consultation only from the authenticated doctor's own
  `IN_PROGRESS` appointment. The browser supplies only the appointment ID;
  Clinical copies all other ownership identifiers from Scheduling's decision.
- Make draft creation retry-safe with one consultation per appointment. Support
  authorised consultation read and versioned draft update with stale-write
  `409` handling.
- Persist append-only Clinical audit and minimal transactional outbox rows in
  the same transaction as each draft command.
- Prove receptionist, unrelated-doctor, wrong-organisation, wrong-status,
  malformed-token, stale-version, duplicate-request, and unavailable-
  Scheduling behavior before expanding the aggregate.

Phase 5A proposed decisions for review:

- The appointment is the initial explicit care relationship. A separate broad
  doctor-patient grant is not invented in Phase 5; referrals and selected
  sharing remain owned by Communication Service in Phase 6.
- Starting an appointment remains a Scheduling command. Consultation creation
  is a retry-safe Clinical command immediately afterward, avoiding a distributed
  transaction while preventing forged clinical ownership.
- An authoring doctor may read their own consultation in the same active
  organisation. Access to another doctor's same-organisation history requires
  a current confirmed/checked-in/in-progress appointment decision and returns a
  minimum-necessary summary rather than raw consultation aggregates.
- The first migration contains the consultation root, local audit, and outbox.
  Structured symptoms, vitals, diagnoses, medication, and prescription child
  tables are added in Phase 5B so the foundation stays reviewable.
- Clinical events contain identifiers, event type, occurred-at time, actor,
  organisation, appointment/consultation IDs, and resource version only. Notes,
  diagnoses, medication, and other clinical content never enter Kafka or audit
  payloads.

Phase 5A exit criteria:

- [x] A doctor can create, read, and version-update one draft consultation from
      their own in-progress appointment through Gateway.
- [x] Retrying creation returns the same consultation and creates no duplicate.
- [x] Receptionist, unrelated doctor, cross-organisation context, and invalid
      appointment states receive safe denials without clinical serialization.
- [x] Concurrent draft updates preserve one winner and return a safe conflict
      for stale versions.
- [x] PostgreSQL integration, HTTP security, client-contract, audit/outbox,
      Gateway routing, and frontend service-adapter tests pass.

Verified Phase 5A evidence:

- Clinical owns `sahha_clinical`; Flyway created and Hibernate validated the
  isolated `clinical_test` schema. The first migration contains only the draft
  consultation root, append-only local audit, and transactional outbox.
- Scheduling exposes a doctor-only internal clinical-context decision that
  validates the live organisation membership and exact appointment doctor,
  then returns only the minimum appointment identifiers, status, times, and
  version. Its internal route is not exposed by Gateway.
- The public create contract accepts only `appointmentId`. Clinical forwards
  the signed access cookie, derives organisation/patient/doctor ownership from
  Scheduling, and requires `IN_PROGRESS`; browser-supplied ownership fields do
  not exist in the request model.
- PostgreSQL `ON CONFLICT` creation enforces one consultation per appointment
  and returns the existing aggregate on retry without a second audit/outbox
  command. Draft updates take a short pessimistic row lock, compare the
  explicit version, and return `409` for a stale writer.
- Clinical uses cookie JWT validation, doctor role enforcement, double-submit
  CSRF for commands, request IDs, no-store responses, safe Problem Details,
  OpenAPI, Eureka-aware Scheduling discovery, and the Gateway consultation
  route. The frontend now has typed consultation contracts and a tested REST
  adapter; no Phase 5 UI was claimed complete.
- `scheduling-service` passed 31 tests, including minimal-context and negative
  role/ownership cases. `clinical-service` passed 6 tests covering client
  forwarding/mapping, migration/context startup, retry-safe create, authorised
  read, draft update, stale version, CSRF, receptionist/unrelated-doctor,
  wrong organisation/status, and unavailable Scheduling.
- `api-gateway` passed 28 routing/security/WebSocket tests. The complete
  frontend passed 82 tests in 28 files and its TypeScript/Vite production build.
  The existing approximately 505 kB main-chunk advisory remains non-blocking.

### Phase 5B — Structured clinical content and record integrity

Status: `COMPLETE`

- Add relational symptoms, history, vitals, examination findings, diagnoses,
  medication/basic prescription items, assessment, treatment, follow-up, and
  notes with JSONB only for genuinely variable specialty measurements.
- Finalise/sign a complete draft using an optimistic version and make the
  original record immutable.
- Store corrections as append-only field-level records with server-derived old
  value, new value, author, timestamp, reason, and consultation version. Build
  the effective view without overwriting the signed source value.
- Publish minimal `consultation.finalised.v1` and
  `consultation.corrected.v1` events through the outbox.

Phase 5B exit criteria:

- [x] The owning doctor can replace one complete structured draft under an
      explicit optimistic version.
- [x] Finalisation rejects incomplete records and makes the persisted source
      root and children immutable at both service and database levels.
- [x] Corrections are append-only, attributable, reasoned, versioned, and
      produce an effective view without changing the signed source value.
- [x] Clinical audit/outbox rows contain no notes, diagnoses, symptoms, vital
      values, medication, or other clinical payload.
- [x] The Clinical schema, HTTP/security behavior, event publication boundary,
      frontend TypeScript contract, automated tests, and production build pass.

Verified Phase 5B evidence:

- Flyway V2 added relational symptoms, history, vital signs, examination
  findings, diagnoses, medication/basic prescription items, corrections, and
  finalisation fields. JSONB is limited to bounded primitive specialty
  measurements. Flyway V3 added expiring outbox claims and tightened the signed
  root trigger to protect its identifier and creation timestamp as well.
- Whole-draft replacement locks the owned consultation briefly, validates the
  supplied version, replaces the child aggregate, and returns the new effective
  record. Finalisation requires a reason, assessment, follow-up, symptom,
  examination finding, active diagnosis, and treatment or complete prescribed
  medication instructions.
- PostgreSQL triggers reject silent changes to a finalised root or child row and
  reject updates/deletes to correction rows. A correction stores the
  server-derived previous effective value, new value, field/target, actor,
  reason, timestamp, and consultation version; reads overlay corrections while
  leaving the signed source untouched.
- The Clinical event mapper emits schema-versioned identifier-only envelopes.
  The outbox publisher claims rows in a short transaction, waits for Kafka
  outside that transaction, acknowledges by claim token, and releases failures
  with bounded exponential backoff. Finalisation and correction wire names are
  `consultation.finalised.v1` and `consultation.corrected.v1` on
  `sahha.clinical.consultations.v1`.
- Clinical Service passed all 11 tests with zero failures or errors, including
  Flyway V3/Hibernate validation, HTTP authorization, completeness, optimistic
  conflicts, source-tamper rejection, payload minimisation, and Kafka
  acknowledgement/failure behavior. The frontend passed all 83 tests in 28
  files and its TypeScript/Vite production build; the existing approximately
  505 kB main-chunk advisory remains non-blocking.

### Phase 5C — Clinical summary and appointment completion

Status: `COMPLETE`

- Expose a role-safe, minimum-necessary same-organisation patient summary only
  after a live Clinical/Scheduling access decision.
- Allow the author to read their own finalised consultation while preventing a
  receptionist or unrelated doctor from resolving its existence.
- Consume consultation-finalised events idempotently in Scheduling to move the
  matching `IN_PROGRESS` appointment to `COMPLETED`, with REST recovery and
  conflict evidence rather than a cross-database transaction.
- Complete read/write/finalisation/correction audit coverage and notification
  projections without clinical payloads in events.

Phase 5C exit criteria:

- [x] A doctor with a live confirmed, checked-in, or in-progress appointment can
      read only the bounded finalised summary for that organisation and patient.
- [x] An unrelated doctor receives resource hiding and a receptionist receives
      role denial; granted and care-relationship-denied summary reads are
      append-only audited.
- [x] A finalisation event completes its matching in-progress appointment once,
      and duplicate delivery is idempotent.
- [x] Completion mismatches create durable conflict evidence, while an owning
      doctor can invoke the version-checked REST recovery path.
- [x] The completion projection remains compatible with Notification Service
      and contains no consultation content.
- [x] Gateway routing, PostgreSQL migrations, backend tests, frontend contracts,
      the frontend test suite, and the production build pass.

Verified Phase 5C evidence:

- Scheduling owns the live patient-care decision and exposes it only on an
  internal doctor-only route. Clinical rechecks exact organisation, actor,
  patient registration, and patient identifiers before assembling at most 20
  finalised encounters containing only reason, diagnoses, medication summary,
  and allergy history.
- Clinical Flyway V4 added append-only access audit rows. Scheduling Flyway V7
  added the idempotent consumed-event ledger with Kafka/REST source metadata,
  applied/idempotent/conflict outcomes, appointment versions, and safe conflict
  codes—never clinical content.
- `consultation.finalised.v1` is decoded with a bounded schema and key check.
  Scheduling locks the appointment, changes only `IN_PROGRESS` to `COMPLETED`,
  emits the existing `APPOINTMENT_COMPLETED` outbox projection, and records
  duplicates or conflicts without a cross-service database transaction.
- The public Clinical recovery endpoint reuses the stored finalisation outbox
  identity and version; its internal Scheduling call forwards the authenticated
  cookie and already validated double-submit CSRF context. Internal recovery is
  not routed through Gateway.
- IntelliJ local profiles now enable the Clinical outbox publisher and
  Scheduling clinical consumer against `localhost:9092`; test profiles keep
  external Kafka listeners disabled.
- Gateway, Scheduling, and Clinical passed 29, 36, and 14 tests respectively.
  The frontend passed all 84 tests in 28 files and its TypeScript/Vite build.
  `git diff --check` passed apart from the pre-existing line-ending advisory;
  the approximately 505 kB main-chunk advisory remains non-blocking.

### Phase 5D — Protected file storage

Status: `COMPLETE`

Phase 5D delivery slices:

- [x] File Service persistence and private object-storage foundation.
- [x] Clinical attachment decision, security, upload negotiation, and streaming.
- [x] Pending-scan workflow, explicit synthetic local clean hook, and rejection.
- [x] Short-lived authorised download grants, download streaming, audit/outbox,
      Gateway/frontend contracts, and acceptance tests.

- Connect File Service to `sahha_file` and SeaweedFS with independently owned
  metadata, upload state, checksum, content type/size, scan status, and external
  consultation/patient/organisation references.
- Negotiate a short-lived upload ticket, then stream bytes through Gateway and
  File Service to SeaweedFS with bounded size/type/checksum validation. The
  browser does not connect directly to SeaweedFS and file bytes never pass
  through Kafka.
- Keep new objects unavailable while scan status is pending; expose a scanner
  hook and an explicitly synthetic local clean-path, not a production malware-
  scanning claim.
- Ask Clinical Service for a live minimum-necessary attachment access decision.
  Issue short-lived opaque Gateway download grants and never permanent public
  object URLs.
- Audit upload, availability, download-grant, download, denial, and expiry, and
  publish only minimal file-availability metadata through an outbox.

Verified Phase 5D upload-slice evidence:

- SeaweedFS `4.41` was downloaded from its official release, MD5-verified, and
  installed natively at `C:\Users\LENOVO\seaweedfs\4.41\weed.exe`. The local
  master, filer, S3 gateway, and volume endpoints run on loopback only; local
  setup and lifecycle commands are documented in `docs/LOCAL_SEAWEEDFS.md`.
- File Service now uses the AWS S3-compatible client with endpoint override and
  path-style access for SeaweedFS. The bucket is private and lazily created;
  PostgreSQL remains authoritative for ownership, lifecycle, digests, audit,
  and later access grants.
- Clinical exposes one doctor-only minimum attachment decision. It derives the
  actor and active organisation from the access token, uses an exact scoped
  consultation query, returns no notes or clinical content, hides resources
  from unrelated doctors, and rejects receptionists.
- File Service validates the cookie JWT and organisation role, requires
  double-submit CSRF for commands, revalidates exact consultation ownership
  through Clinical, persists only the SHA-256 digest of a 256-bit one-time
  upload ticket, and streams bounded bytes through File Service. The browser
  receives no SeaweedFS address, credentials, bucket, or storage key.
- Upload declaration and streaming enforce the type allowlist, exact content
  length, maximum size, optional expected SHA-256, server-computed SHA-256,
  single ticket use, random identifier-only storage keys, failure cleanup, and
  `PENDING` scan state. Stored bytes remain unavailable until the next scan
  slice marks them clean.
- Gateway now routes `/api/v1/files/**` to File Service and allows the
  `X-Upload-Token` CORS header while retaining credentialed-origin policy.
- File Service passed 11 ordinary tests with zero failures/errors and its
  separately enabled real SeaweedFS adapter round trip passed 1/1. The targeted
  Gateway routing/CORS suite passed 19/19 and the Clinical consultation/access
  suite passed 4/4. These tests cover role denial, resource hiding, CSRF,
  mismatched downstream identity, one-time use, MIME canonicalisation,
  checksum failure cleanup, append-only audit counts, and private storage.
- The follow-on scan slice adds a local-profile-only explicit synthetic
  `CLEAN`/`REJECTED` decision. It is disabled by default outside the local
  profile and is explicitly documented as a development aid, not a malware
  scanner or production security control.
- Clean decisions first verify that private bytes still exist and then commit
  the state change, append-only audit, and minimal `medical-file.available.v1`
  outbox row atomically. Rejected decisions commit an unavailable state plus
  audit/minimal `medical-file.rejected.v1` outbox row before removing bytes;
  an idempotent retry can repeat cleanup without duplicating evidence.
- File Service passed 15 ordinary tests with zero failures/errors after the
  scan slice; its one additional live SeaweedFS adapter test remains explicitly
  opt-in. Coverage proves pending files are unavailable, clean decisions retain
  bytes, rejected decisions remove bytes, repeats are idempotent, pre-upload
  decisions conflict, receptionists are denied, and the synthetic controller
  is absent when its production-default feature flag is false.
- The final slice exposes only bounded consultation-file metadata after a live
  Clinical ownership decision. Grant issuance revalidates the exact doctor,
  active organisation, consultation, patient registration, and patient before
  creating a 256-bit two-minute grant whose digest—not bearer value—is stored.
- Downloads use the Gateway path plus `X-Download-Token`, keeping the bearer
  secret out of URLs, browser history, and ordinary proxy logs. The exact
  doctor/organisation/file binding is consumed once under a pessimistic lock;
  only `STORED` + `CLEAN` files stream, and responses use `no-store`, bounded
  content metadata, and an attachment disposition without exposing SeaweedFS.
- Grant issuance, successful stream authorization, replay, invalid token,
  expiry, revoked clinical relationship, unavailable state, and object-store
  failure produce append-only audit evidence. Object I/O remains outside the
  database transaction; a missing object consumes the fail-safe one-time grant
  and adds a failure audit rather than weakening access.
- The File outbox now claims availability/rejection rows with `SKIP LOCKED`,
  publishes identifier-only events to `sahha.file.medical-files.v1` keyed by
  medical-file ID, waits for Kafka acknowledgement outside the database
  transaction, and records publication or bounded retry state by claim token.
  Filename, checksum, storage key, token, and clinical content remain absent.
- Gateway forwards the one-time download header and permits it only through the
  configured credentialed CORS origin. The frontend has typed listing,
  negotiation, raw streaming, grant, and Blob-download contracts; returned
  versioned Gateway paths are normalized without allowing direct storage calls.
- Final verification passed 33/33 Gateway tests and 24/24 ordinary File tests
  (plus one intentionally skipped opt-in live test), packaged both services,
  and passed the explicit live SeaweedFS round trip 1/1. The frontend passed
  87/87 tests with four workers and its TypeScript/Vite production build; the
  existing approximately 506 kB main-chunk advisory remains non-blocking.

### Phase 5E — React integration and Phase 5 acceptance

Status: `COMPLETE`

- Replace the doctor consultation/summary/file mocks with typed Gateway REST
  services, TanStack Query state, React Hook Form/Zod validation, explicit
  optimistic versions, and the approved responsive UI components.
- Run the complete checked-in → in-progress appointment → draft consultation →
  clinical content → file upload → finalisation → appointment completion →
  correction journey with receptionist/unrelated-doctor denial cases.
- Record automated, browser, PostgreSQL, SeaweedFS, Kafka/outbox, audit, security,
  and response-time evidence before Phase 6.

Verified frontend integration slice:

- The doctor appointment lifecycle no longer offers direct manual completion
  for an in-progress visit. Starting a checked-in appointment now creates the
  retry-safe Clinical consultation and opens it; an already in-progress row
  exposes the same safe recovery entry point. Consultation finalisation remains
  the source of appointment completion through the existing Kafka/recovery
  workflow.
- The former doctor clinical mock route now loads a real Gateway-backed work
  queue and consultation workspace. TanStack Query owns appointment,
  consultation, minimum-necessary patient-summary, and protected-file server
  state; the browser never calls an internal service or object-storage URL.
- React Hook Form and Zod cover narrative fields, symptoms, history/allergies,
  vitals, examination, diagnoses, medication, treatment, and follow-up. Valid
  drafts autosave after a bounded pause, manual save remains available, every
  command carries the latest server version, and finalisation applies the
  stricter backend-aligned completeness policy.
- Finalised records render read-only and the workspace exposes append-only
  consultation-field corrections with the required new value and reason. The
  correction history retains old/new values, actor-backed server evidence,
  time, and consultation version.
- The protected-file panel negotiates and streams uploads through Gateway,
  lists safe metadata, exposes the local synthetic clean decision only in the
  Vite development build, and downloads clean files through a one-time opaque
  header-bound grant. No SeaweedFS endpoint, bucket, key, or credential enters
  the UI contract.
- The clinical route is lazy loaded as a separate approximately 39.03 kB
  minified chunk. This reduced the main JavaScript chunk from the initial
  post-integration 534.58 kB result to 496.09 kB and removed the build's
  greater-than-500-kB warning.
- Focused clinical/scheduling/file tests pass 17/17. The complete frontend
  suite passes 95/95, TypeScript compilation passes, the production build
  succeeds, and targeted diff checks report no whitespace errors. Live
  multi-service/browser/storage/event/audit/performance acceptance is not yet
  claimed because the local stack was not running during this slice.

Tasks:

- [x] Define the care relationship that authorises consultation access.
- [x] Model consultation drafts, history, symptoms, vitals, examination,
      diagnoses, assessment, treatment, medication, follow-up, and notes.
- [x] Keep basic prescription items inside Clinical Service for V1.
- [x] Start consultations only from an authorised appointment or explicit care
      relationship.
- [x] Implement draft autosave with explicit versions.
- [x] Implement finalisation/signing and immutable final records.
- [x] Implement append-only corrections with old/new values, author, time, and
      reason.
- [x] Create role-safe patient medical-summary projections.
- [x] Model file metadata and consultation/patient/organisation links.
- [x] Implement authorised upload negotiation, size/type/checksum validation,
      SeaweedFS storage, scan-status hooks, and short-lived download access.
      Upload, scan decisions, rejection, and one-time downloads are verified.
- [x] Audit clinical reads, writes, finalisation, correction, upload, and
      download.
- [x] Integrate the doctor patient summary, consultation workspace, diagnosis,
      medication, follow-up, and file UI.
- [x] Keep receptionist endpoints unable to resolve clinical or file content.

Exit criteria:

- [x] The checked-in appointment can become an in-progress consultation.
- [x] The doctor records and finalises the consultation.
- [x] The final record cannot be overwritten.
- [x] A correction preserves both values and its reason.
- [x] An authorised doctor can access a short-lived Gateway file path with an
      opaque header-bound grant.
- [x] A receptionist and unrelated doctor cannot access clinical data or files.

## 9. Phase 6 — Messaging, referral, and selected sharing

Status: `IN PROGRESS`

Goal: collaborate with another doctor without granting blanket patient access.

Tasks:

- [x] Model authorised direct-doctor conversations, participants, and immutable
      messages.
- [ ] Add protected message attachments through File Service.
- [x] Ensure a patient mention stores context but grants no clinical access.
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

### Phase 6A - secure doctor conversations

Status: `COMPLETE` for the implemented API/UI slice; the Phase 6 live
two-doctor exit journey remains pending.

Implemented and verified:

- [x] Added Communication-owned PostgreSQL entities and Flyway migration for
      organisation-scoped threads, active participants, append-only messages,
      read markers, audit events, and transactional outbox events.
- [x] Added database triggers that reject message and communication-audit
      updates or deletes.
- [x] Added retry-safe conversation and message request IDs. Reusing an ID with
      different content returns a conflict instead of duplicating a write.
- [x] Added doctor-only conversation list/read/create, message list/send, and
      read-marker endpoints with cookie JWT, CSRF, active-organisation, live
      membership, and participant checks.
- [x] Added a purpose-specific Organisation Service collaboration directory.
      It returns only active doctors, requires a live doctor membership, and
      requires the path organisation to equal the JWT active organisation.
- [x] Revalidate the sender and recipient memberships before message creation;
      a suspended or removed recipient cannot receive new messages.
- [x] Validate an optional patient-registration mention through Scheduling
      Service using the sender's existing care relationship. The stored API
      response explicitly reports `patientAccessGranted: false`, and no grant
      is created.
- [x] Store audit and privacy-minimised outbox rows in the same transaction as
      conversation/message writes. Kafka payloads contain routing identifiers
      only and exclude message text and patient identifiers.
- [x] Route `/api/v1/conversations` through Gateway and add Communication
      Service Swagger/OpenAPI configuration.
- [x] Replace the doctor mock messages route with a Gateway-backed TanStack
      Query/React Hook Form/Zod workspace, live unread badge, participant
      directory, retry-safe commands, explicit no-access boundary, and
      responsive reuse of the approved Sahha design.
- [x] Lazy-load the 8.22 kB messenger route so the main minified bundle remains
      below the 500 kB warning threshold at 497.24 kB.

Decisions and boundaries:

- Phase 6A supports one direct conversation between two doctors. Group threads,
  attachments, referrals, selected sharing, consent, grants, revocation, and
  expiry remain later Phase 6 slices.
- A patient mention is an opaque registration identifier visible only to thread
  participants. It is never accepted by Clinical or File Service as an access
  credential.
- Conversation enumeration is participant-scoped and unauthorised resource
  access is hidden as `404`; receptionist access is rejected as `403` before
  business logic.
- Message and conversation events use topic
  `sahha.communication.messages.v1`, keyed by conversation ID. Recipient
  notification consumption and WebSocket delivery are Phase 6B.
- The local IntelliJ Communication Service configuration enables its outbox
  publisher against `localhost:9092`; tests keep external publication disabled.

### Phase 6B progress - authenticated real-time message delivery

Implemented in the first Phase 6B slice:

- [x] Added an authenticated doctor-only STOMP endpoint at
      `/api/v1/conversations/ws`, with CSRF checked on the STOMP `CONNECT` and
      participant-scoped `/user/queue/messages` subscriptions.
- [x] Added Gateway WebSocket routing for the Communication endpoint.
- [x] Added an after-commit Communication event carrying the message body only
      to the already-authorised conversation participants. The Kafka outbox
      payload remains privacy-minimised and contains routing identifiers only.
- [x] Added the frontend STOMP client with CSRF handshake, reconnect, payload
      validation, and TanStack Query history recovery in the doctor messenger.
- [x] Added a Notification Service consumer for `message.sent.v1` events on
      `sahha.communication.messages.v1`, keyed by conversation ID. Known
      `conversation.created.v1` records are safely ignored.
- [x] Validate the event schema, required routing identifiers, recipient list,
      sender exclusion, duplicate recipients, and Kafka key before projection.
- [x] Persist consumed and rejected Communication event positions so valid
      retries are idempotent and rejected payloads retain only a SHA-256
      fingerprint and safe reason code.
- [x] Generalised the existing notification inbox with a constrained
      `MESSAGE_RECEIVED` / `CONVERSATION` shape. Message alerts contain no body,
      patient identifier, or clinical data; Communication Service remains the
      source for authorised message content.
- [x] Deliver the committed alert through Notification Service's existing
      private WebSocket queue and retain it in PostgreSQL for REST recovery.
- [x] Extend the doctor notification UI so message alerts increment the unread
      badge, use a message icon, become read through the existing endpoint, and
      open `/doctor/messages`.
- [x] Enable the Communication consumer in the shared IntelliJ Notification
      Service local run configuration.

Validation evidence for this slice:

- `mvn -pl communication-service -Dtest=ConversationHttpIntegrationTests test`:
  context and conversation integration test passed after explicit constructor
  injection was added for the new event publisher dependency.
- `mvn -pl communication-service -DskipTests package` and
  `mvn -pl api-gateway -DskipTests package`: package builds passed.
- `npm.cmd run typecheck`: passed.
- `npm.cmd test`: 32/32 files and 97/97 tests passed.
- `mvn -pl notification-service test`: all 37 tests passed, including the V3
  Flyway migration, message decoder/consumer validation, idempotent projection,
  existing appointment regressions, REST inbox, and WebSocket isolation.
- `mvn -pl notification-service -DskipTests package`: passed.
- `npm.cmd run typecheck`: passed after the generic notification contract was
  extended with nullable appointment-only fields.
- `npm.cmd test`: 32/32 files and 98/98 tests passed, including message-alert
  merge, unread count, mark-read, and messenger navigation.
- `npm.cmd run build`: passed; the main chunk remains below the configured
  500 kB warning boundary at 497.81 kB.

Not yet claimed complete: the two-doctor live browser/Kafka verification and
message recovery across a real service restart. Referral, sharing, revocation,
and expiry remain later Phase 6 slices. Redis was deliberately not added to
this low-frequency durable inbox path; PostgreSQL and Kafka already provide the
required persistence and replay behavior.

Validation evidence:

- `mvn -pl communication-service test`: 5/5 tests passed, including Flyway/JPA
  validation against PostgreSQL `communication_test` and the HTTP security,
  CSRF, idempotency, participant-isolation, audit, and outbox integration path.
- `mvn -pl organisation-service test`: 38/38 existing regression tests passed
  before the focused collaboration cases were added; the final focused
  `SchedulingDirectoryHttpIntegrationTests` run passed 1/1 with doctor-only,
  active-organisation, and receptionist-denial assertions.
- `mvn -pl api-gateway -Dtest=GatewaySecurityRoutingIntegrationTests test`:
  22/22 routing and edge-security tests passed, including Communication routing.
- `npm.cmd test`: 32/32 files and 97/97 frontend tests passed; the new REST
  contract tests passed 2/2.
- `npm.cmd run build`: TypeScript and Vite production build passed with a
  separate 8.22 kB Communication chunk and no large-chunk warning.
- Changed backend modules compiled and completed the package lifecycle. Because
  the user's running Gateway and Organisation processes held their executable
  JARs on Windows, the final package verification used
  `-Dspring-boot.repackage.skip=true` rather than stopping those processes.
- A live two-doctor browser/Kafka check is not claimed yet; it requires the
  changed services to be restarted and two active synthetic doctor accounts.

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
| Service integration | PostgreSQL, Kafka, Redis, and SeaweedFS through Testcontainers |
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
| 2026-08-05 | Store one full-replacement availability aggregate per organisation and doctor, protected by an optimistic version. | The schedule, breaks, and time off form one consistency boundary; stale browser edits must conflict instead of silently replacing newer rules. |
| 2026-08-05 | Calculate availability slots from persisted rules instead of storing empty slot rows. | Rules are authoritative, deterministic generation avoids unbounded slot data, and appointment occupancy can be subtracted when the appointment slice is added. |
| 2026-08-05 | Revalidate Scheduling requesters through Organisation Service while retaining only external membership/user identifiers in Scheduling. | Membership status and roles remain authoritative in their owning service without cross-service database access or trusting stale token claims alone. |
| 2026-08-05 | Make PostgreSQL's half-open time-range exclusion constraint the final double-booking authority. | Application pre-checks improve errors but cannot close a race between concurrent booking transactions; Redis is not an integrity boundary. |
| 2026-08-05 | Require a client-generated booking request UUID and return the original result for an identical retry. | Network retries must not create duplicate appointments, while reuse for different patient/doctor/time details remains a safe conflict. |
| 2026-08-05 | Validate booking actors, doctors, and patients live through their owning services and persist only minimum appointment references/snapshots. | A stale availability row must not schedule a suspended doctor, and Scheduling must not copy the full patient or staff directory. |
| 2026-08-05 | Limit the first booking command to receptionists and Organisation Administrators. | Existing Patient administrative access supports these actors safely; doctor and patient booking require narrower purpose-built patient-reference contracts before they can be enabled. |
| 2026-08-14 | Require a measured Phase 4 acceptance gate before beginning Phase 5. | The complete scheduling workflow must be exercised live and documented with Gateway response timing, Kafka/Redis state, PostgreSQL query evidence, security denials, and targeted frontend consistency work before clinical scope increases risk. |
| 2026-08-14 | Treat response-time values as provisional percentile-based Sahha engineering budgets, not universal healthcare standards. | Browser guidance provides useful user-experience thresholds, but backend, database, cache, and event targets depend on workload and environment and must be replaced or confirmed by measured live evidence before optimisation. |
| 2026-08-29 | Use SeaweedFS's private S3-compatible gateway for local medical-file bytes and keep all browser transfer through Gateway and File Service. | It provides native Windows development without Docker Desktop while preserving the provider-neutral private-object-storage boundary; SeaweedFS addresses and credentials never become browser contracts. |
| 2026-08-29 | Permit attachments on an owned draft or finalised consultation as append-only File Service resources. | Adding a separate attachment does not overwrite the signed Clinical aggregate; ownership is still revalidated live and the attachment remains unavailable until its scan state is clean. |
| 2026-09-03 | Deliver direct-message WebSocket payloads only after the Communication transaction commits and only to active conversation participants. | The browser gets messenger-style low-latency delivery without exposing uncommitted messages; REST remains the recovery source and Kafka remains privacy-minimised for backend consumers. |
| 2026-09-04 | Persist message alerts as minimal conversation-routing notifications and keep message content in Communication Service. | Notification Service can recover unread alerts without copying clinical conversation text; the authorised messenger REST API remains the source of message content. |
| 2026-08-29 | Expose synthetic file scan decisions only when an explicit local-development flag is enabled. | The internship can demonstrate clean/rejected lifecycles without misrepresenting a mock decision as production malware scanning; the controller is absent under the production default. |
| 2026-09-01 | Run the local SeaweedFS filer on port `18888` while retaining its S3 gateway on `8333`. | Spring Cloud Config owns `8888`; assigning an explicit filer port lets both required local dependencies run without weakening either service boundary. |

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

### 2026-09-04 - Phase 6B recoverable message notifications implemented

- Added Notification Service consumption of privacy-minimised
  `message.sent.v1` records, with schema validation, conversation-key
  validation, idempotent consumed positions, and fingerprint-only rejection
  records.
- Added Flyway V3 to generalise the existing inbox for constrained
  `MESSAGE_RECEIVED` conversation alerts while preserving all appointment
  notification shapes and behavior.
- Kept message bodies and patient identifiers out of Notification Service;
  persisted alerts contain only the recipient and conversation routing data.
- Reused the existing after-commit private Notification WebSocket and REST
  inbox recovery path. Extended the doctor UI with unread-count updates,
  message presentation, mark-read behavior, and `/doctor/messages` navigation.
- Enabled the consumer in the shared IntelliJ Notification Service local run
  configuration. Deliberately added no Redis cache to this infrequent durable
  inbox workflow.
- Excluded the local SeaweedFS S3 identity file from Git before the milestone
  push; local object-storage credentials remain workstation-only.
- Verified Notification Service 37/37 tests and package build, frontend 98/98
  tests, TypeScript typecheck, production build, and whitespace checks.
- Next: restart Communication and Notification services and run the live
  two-doctor Phase 6B acceptance path with Kafka, PostgreSQL, WebSocket, and
  recipient-isolation evidence.

### 2026-09-02 - Phase 6A secure doctor conversations implemented

- Started Phase 6 with a complete direct doctor-conversation vertical slice in
  Communication Service, including its owned schema, API, security, audit, and
  transactional outbox boundaries.
- Added a minimal doctor-only Organisation collaboration directory without
  weakening the stricter scheduling directory, and enforced equality with the
  JWT active organisation.
- Made patient context consent-safe by validating the sender's existing care
  relationship while explicitly creating no recipient clinical access. Message
  bodies and patient identifiers are excluded from Kafka metadata.
- Routed conversations through Gateway and replaced the doctor mock messenger
  with the real API workflow. Split the page into an 8.22 kB lazy chunk and kept
  the main bundle at 497.24 kB.
- Verified Communication 5/5, the focused Organisation collaboration suite
  1/1 after the complete 38/38 regression run, Gateway 22/22, frontend 97/97,
  TypeScript/Vite build, and changed-module packaging without interrupting the
  user's running services.
- Next: implement Phase 6B recipient notifications from Communication Kafka
  events through the recoverable Notification/WebSocket workflow.

### 2026-09-01 — Phase 5 accepted and closed

- Completed the live receptionist-to-doctor journey through booking,
  confirmation, check-in, retry-safe consultation creation, structured clinical
  entry, medication, finalisation, Kafka-driven appointment completion,
  append-only correction, private upload, local clean decision, one-time
  download, and receptionist Clinical/File denial.
- Correlated the browser results with service-owned PostgreSQL state, audit and
  outbox rows, Kafka partitions/offsets, Notification consumption, and the
  private SeaweedFS object without recording tokens, credentials, storage keys,
  or clinical payloads in the plan.
- The user accepted the Phase 5 manual journey and explicitly deferred creating
  a second live doctor account. Unrelated-doctor, cross-organisation, immutable
  record, stale-version, invalid-grant, and replay denials remain supported by
  the passing automated security/integration suites; the deferred browser check
  is not represented as live evidence.
- Retained autosave write-count tuning as measured optimisation debt rather than
  weakening optimistic versions or append-only audit. Phase 5 and Phase 5E are
  now complete.
- Next: define Phase 6A's conversation/participant ownership, patient mention
  boundary, APIs, events, permission matrix, migrations, and acceptance tests.

### 2026-09-01 — Phase 5E live environment made ready

- Corrected the missing MockMvc `header` matcher import that prevented Auth test
  sources from compiling. `BrowserSessionHttpIntegrationTests` now passes
  11/11, and all ten services required by the Phase 5 path package successfully.
- Moved the local SeaweedFS filer endpoint from its conflicting default `8888`
  to `18888`, retained the private S3 adapter on `8333`, updated the local guide,
  and passed the explicit live object-storage adapter round trip 1/1.
- Recovered native Kafka from Windows retention-file locking without deleting or
  reformatting its data. For this acceptance run only, compaction is disabled,
  retention deletion is disabled, and the retention scan interval is extended;
  both Clinical and Scheduling topics and their two expected consumer groups are
  visible.
- Started Discovery, Config, Auth, Organisation, Patient, Scheduling, Clinical,
  Notification, File, and Gateway from the current packaged sources. All report
  `UP`, and Eureka contains the nine expected registered applications in addition
  to the registry itself.
- Preserved the existing mock-enabled frontend on `5173` and started a separate
  live frontend on `5174`; its served environment proves both application and
  authentication mocks are disabled. The pre-journey databases contain no
  consultation or medical-file rows, providing a clean evidence baseline.
- The headless live-auth smoke passed against `5174`/Gateway at a 375 px
  viewport: real Auth mode was active, an unknown account returned safe `401`,
  CSRF and request-ID contracts held, no access cookie was issued, registration
  fields were present, and neither the document nor body overflowed.
- Live doctor and receptionist sessions restored the same active organisation.
  Reception booked a published slot for an existing active patient: PostgreSQL
  holds the new appointment as `REQUESTED` version 0, its outbox row published
  once, Kafka partition 0 offset 10 was consumed as `NOTIFICATION_CREATED`, and
  the doctor received and read the corresponding in-app notification.
- The doctor confirmed the same request through the live UI. Scheduling records
  `REQUESTED` to `CONFIRMED` at version 1 with append-only audit evidence; the
  confirmation outbox published once and Kafka partition 0 offset 11 was handled
  as `NO_ELIGIBLE_RECIPIENT`, which is expected because this synthetic patient
  has no portal account. No service logged a runtime error.
- Reception checked in the confirmed patient and the waiting list reflected the
  change. Scheduling records `CONFIRMED` to `CHECKED_IN` at version 2; its outbox
  published once, Kafka partition 0 offset 12 produced `NOTIFICATION_CREATED`,
  and the doctor's `PATIENT_CHECKED_IN` notification is present and unread.
- The doctor marked the visit started and the UI navigated through the retry-safe
  Clinical creation call. Scheduling is `IN_PROGRESS` version 3 with a
  `CHECKED_IN` to `IN_PROGRESS` audit transition; Clinical contains exactly one
  matching `DRAFT` version 0 and one successful creation audit row. No Gateway,
  Scheduling, or Clinical runtime error was logged.
- Structured synthetic clinical entry produced 18 attributable full-draft
  updates before finalisation, exposing version 19 after the finalisation write.
  All 20 Clinical outbox rows published once. Scheduling consumed the
  `consultation.finalised.v1` event from partition 0 offset 19 as `APPLIED`, moved
  the appointment from `IN_PROGRESS` to `COMPLETED` at version 4, and retained
  its completion audit. The observed autosave write count is a post-gate tuning
  candidate; optimistic versioning and audit history remain required.
- The doctor attached a 1,749,783-byte synthetic PNG to the finalised record
  through Gateway. File metadata is `STORED/PENDING`, declared and streamed byte
  counts match, the computed checksum matches, and append-only negotiation and
  storage audits succeeded. SeaweedFS returns the same object length from its
  private filer while File has correctly emitted no availability outbox event
  before a clean scan decision.
- The explicit local scan hook moved the file to `STORED/CLEAN` version 3 and
  retained its availability timestamp and clean-scan audit. The
  `medical-file.available.v1` outbox row published once to the one-partition
  `sahha.file.medical-files.v1` topic; its sole replica is in sync and the local
  acceptance broker retains the topic without time-based deletion.
- The doctor downloaded the clean object through Gateway. File Service issued
  one bounded grant, its expiry is after issuance, and that one grant is marked
  consumed. `DOWNLOAD_GRANT_ISSUED` and `DOWNLOADED` audits each occur once, and
  Gateway, Clinical, and File logs contain no runtime errors. Neither the token
  nor private object key was inspected or recorded as acceptance evidence.
- The doctor appended a consultation-level clinical-assessment correction. The
  record remains `FINALIZED`, advances to version 20, and the root assessment
  still equals the correction's old value rather than its new value. The
  separate row retains both values, the actor, reason, time, and version; its
  successful audit and `consultation.corrected.v1` outbox event each exist and
  the event published once.
- A live receptionist session requested the exact final clinical-record resource
  through Gateway and received `403 Forbidden` without clinical content.
  Gateway and Clinical recorded no runtime error, proving this was an enforced
  role denial rather than a service failure.
- The same live receptionist session requested File Service's exact
  consultation listing through Gateway and received `403 Forbidden` without a
  filename, checksum, grant, object key, or storage endpoint.
- Next: complete the checked-in appointment through finalisation and correction
  in the live browser, then record persistence, object, event, audit, denial, and
  latency evidence before closing Phase 5.

### 2026-08-30 — Phase 5E clinical frontend integration implemented

- Replaced the doctor clinical mock route with a lazy-loaded, responsive,
  Gateway-backed clinical queue and consultation workspace while preserving the
  approved Sahha visual system.
- Connected checked-in appointment start to retry-safe consultation creation,
  removed direct in-progress appointment completion from the scheduling UI,
  and added safe re-entry for existing in-progress consultations.
- Added React Hook Form/Zod structured clinical drafting, bounded autosave,
  explicit optimistic versions, completeness-checked finalisation, immutable
  final rendering, and append-only correction UI.
- Integrated the minimum-necessary patient summary and private file listing,
  upload, development-only synthetic clean decision, one-time-grant download,
  and lifecycle feedback without exposing object-storage infrastructure.
- Added form-contract, workspace, scheduling-transition, and File REST coverage.
  Focused tests passed 17/17; the complete frontend suite passed 95/95;
  TypeScript and the Vite production build passed. Lazy loading reduced the
  main minified chunk to 496.09 kB and produced a separate 39.03 kB clinical
  chunk with no chunk-size warning.
- Kept Phase 5E in progress because no local service, PostgreSQL, Memurai,
  SeaweedFS, or Kafka listener was running for the live acceptance gate.
- Next: start the local stack and run the complete Phase 5 browser, persistence,
  object-storage, Kafka/outbox, audit, denial, and response-time acceptance
  path before Phase 6.

### 2026-08-30 — Phase 5D protected-file milestone completed

- Added live Clinical-revalidated file discovery and two-minute, 256-bit,
  digest-only download grants bound to one doctor, organisation, and file.
- Added one-time clean-file streaming through Gateway with the bearer grant in
  `X-Download-Token`, safe response metadata, `no-store`, resource hiding, and
  no direct SeaweedFS URL or storage identifier.
- Added append-only evidence for grant issuance, authorization, replay, invalid
  grants, expiry, revoked clinical access, unavailable state, and private-object
  failure. Missing storage fails closed and leaves the grant consumed.
- Completed the File transactional-outbox publisher for minimal availability
  and rejection events on `sahha.file.medical-files.v1`, including short claim
  transactions, acknowledgement-aware completion, lease recovery, and bounded
  retry backoff. File Service's IntelliJ local configuration enables it against
  native Kafka while automated tests keep external publication disabled.
- Added Gateway forwarding/CORS coverage and typed frontend metadata, upload,
  grant, and Blob-download contracts. The shared HTTP client now supports raw
  bodies and versioned returned Gateway paths without bypassing the Gateway.
- Verified the combined Gateway/File reactor with 33 and 25 tests respectively
  (the File count includes one intentionally skipped opt-in live test), both
  packaged successfully. The separately enabled SeaweedFS test passed 1/1.
  Frontend medical-file/HTTP tests passed 6/6, the bounded-worker complete suite
  passed 87/87, and the production build succeeded. The first unconstrained
  frontend run had two unrelated loading-state timeouts; both passed 9/9 in
  isolation before the complete bounded-worker rerun passed.
- Next: implement Phase 5E's backend-integrated doctor consultation,
  patient-summary, and protected-file workspace and run the complete Phase 5
  browser/database/SeaweedFS/Kafka/audit/security/performance acceptance path.

### 2026-08-29 — Phase 5D pending-scan slice completed

- Added an explicit local-only synthetic `CLEAN`/`REJECTED` endpoint for the
  owning doctor, protected by the existing cookie JWT, active organisation,
  doctor role, resource scope, and double-submit CSRF boundaries.
- Kept object-store existence checks and rejected-byte removal outside database
  transactions. Each state decision, audit event, and minimal availability or
  rejection outbox row commits atomically under a scoped pessimistic lock.
- Made same-decision retries idempotent without duplicate audit/outbox rows;
  opposite or pre-storage decisions return a safe conflict. A rejected retry
  can repeat physical cleanup if the first object-store removal failed.
- Preserved pending/rejected unavailability, retained clean bytes, removed
  rejected bytes, and excluded filename, checksum, storage key, and clinical
  content from file events.
- Added a production-default-disabled context test plus HTTP integration tests
  for clean, rejection, idempotency, conflict, role denial, audit/outbox, and
  storage effects. The complete File Service suite passed 15 ordinary tests
  with zero failures/errors; the live SeaweedFS adapter remains a separate
  opt-in test already verified 1/1 in this milestone.
- Next: implement short-lived authorised download grants and one-time download
  streaming through Gateway, with clean-state enforcement, expiry, resource
  hiding, and audit evidence.

### 2026-08-29 — Phase 5D authorised upload slice completed with SeaweedFS

- Replaced the unverified local MinIO adapter with the provider-neutral
  SeaweedFS S3 adapter, AWS SDK client configuration, synthetic loopback-only
  credentials, and a native Windows setup guide.
- Downloaded and MD5-verified SeaweedFS `4.41`, started its loopback master,
  filer, S3 gateway, and volume server, and passed a real private bucket
  create/put/get/existence/delete integration test.
- Added Clinical's minimum doctor-owned consultation attachment decision and
  File Service's JWT/role/active-organisation, double-submit CSRF, request ID,
  Problem Details, OpenAPI, and Eureka-aware Clinical client boundaries.
- Added one-time digest-only upload tickets, exact scoped pessimistic claims,
  bounded streaming through File Service, type/size/checksum checks, random
  storage keys, failure cleanup, append-only upload audit, and pending scan
  state. No storage endpoint, credential, bucket, key, or permanent URL is
  returned to the browser.
- Routed the API through Gateway and enabled only the required upload-ticket
  CORS header. Added negative tests for receptionist, missing CSRF, unrelated
  consultation, downstream identity mismatch, unsafe type, ticket replay, and
  checksum mismatch.
- Verified 11 ordinary File Service tests plus 1/1 explicit live SeaweedFS
  test, 19/19 targeted Gateway routing/security tests, 4/4 targeted Clinical
  tests, and compilation of the affected four-project reactor.
- Marked only the Clinical decision/security/upload slice complete. Scan,
  rejection, download grants/streaming, frontend integration, and live browser
  acceptance remain open.
- Next: implement the pending-scan workflow, explicit synthetic local clean
  hook, and rejection path without claiming production malware scanning.

### 2026-08-24 — Phase 5D private file foundation started

- Upgraded the empty File Service scaffold with JPA, Flyway, PostgreSQL,
  security/resource-server, Kafka, OpenAPI, Lombok, and the official MinIO Java
  SDK 9.0.1 dependency.
- Added service-owned medical-file, one-time download-grant, append-only audit,
  and minimal outbox tables with immutable ownership, bounded states, digest,
  size, scan, expiry, claim, and payload constraints.
- Added the typed metadata lifecycle plus private object-storage port and
  conditional MinIO/in-memory adapters. MinIO bucket access remains lazy and no
  object URL is exposed to the browser.
- The first validation correctly found PostgreSQL `char(64)`/Hibernate
  `varchar(64)` drift. Preserved applied migration history and normalized all
  digest columns through forward-only Flyway V2.
- File Service passed all 4 tests against `sahha_file.file_test`, covering both
  migrations, Hibernate validation, append-only audit, immutable ownership,
  private storage round-trip, one-time grants, unsafe names, and integrity
  mismatches. Live MinIO is not yet running and is not claimed verified.
- Next: add Clinical's attachment decision and the authenticated, bounded upload
  negotiation/streaming path through Gateway.

### 2026-08-24 — Phase 5C clinical summary and completion completed

- Added Scheduling's live minimum-necessary patient-care decision and
  Clinical's bounded finalised patient-summary projection with explicit
  organisation, actor, registration, and patient matching.
- Added append-only clinical read auditing and verified granted and denied
  access evidence without storing clinical values.
- Added bounded Clinical finalisation event decoding, Scheduling's idempotent
  consumed-event ledger, appointment completion, durable conflict evidence, and
  the version-checked REST recovery path.
- Preserved the existing payload-free appointment notification projection and
  enabled the Clinical publisher/Scheduling consumer in local IntelliJ profiles.
- Added typed frontend summary/recovery contracts. Verified Gateway 29/29,
  Scheduling 36/36, Clinical 14/14, frontend 84/84, the production build, and
  clean diff checks; live native-Kafka delivery remains part of the Phase 5
  end-to-end acceptance gate.
- Marked Phase 5C complete and Phase 5D in progress.
- Next: build File Service's independently owned metadata/audit persistence and
  private object-storage foundation before exposing upload negotiation.

### 2026-08-24 — Phase 5B structured record integrity completed

- Added the relational structured consultation aggregate, typed whole-draft
  replacement, completeness validation, finalisation, and effective record API.
- Enforced finalised-source immutability and append-only attributable
  corrections in both the service model and PostgreSQL triggers.
- Added minimal schema-versioned Clinical events and a leased transactional
  outbox publisher that does not hold database transactions open while waiting
  for Kafka.
- Extended the frontend model and Gateway REST adapter for record read, draft
  replacement, finalisation, and correction without claiming the Phase 5 UI
  complete.
- Verified all 11 Clinical tests, all 83 frontend tests, the frontend production
  build, and clean targeted diff checks.
- Marked Phase 5B complete and Phase 5C in progress.
- Next: implement the live minimum-necessary clinical-summary access decision,
  then idempotent Scheduling completion from the finalisation event with REST
  recovery.

### 2026-08-24 — Phase 5A authorised consultation foundation completed

- Connected Clinical Service to its independently credentialed PostgreSQL
  database and isolated test schema with Flyway and Hibernate validation.
- Added the consultation draft root, append-only Clinical audit, transactional
  outbox, retry-safe appointment uniqueness, version-protected draft commands,
  cookie JWT/CSRF security, request IDs, Problem Details, OpenAPI, and discovery.
- Added Scheduling's minimum-necessary treating-doctor appointment decision;
  Clinical accepts only an appointment ID and derives every ownership field
  server-side from the signed caller context.
- Routed only the public consultation API through Gateway and added a typed
  frontend consultation REST adapter without claiming the clinical UI complete.
- Verified 31 Scheduling tests, 6 Clinical tests, 28 Gateway tests, all 82
  frontend tests, and the frontend production build. The first Clinical client
  test run exposed only mock-expectation ordering; registering both expected
  requests before execution fixed it and the rerun passed.
- Marked Phase 5A complete and Phase 5B in progress.
- Next: implement the Phase 5B relational content model and whole-draft
  replacement command before finalisation and append-only corrections.

### 2026-08-24 — Phase 5 implementation boundary designed

- Inspected the empty Clinical/File executable scaffolds and the completed
  Scheduling appointment controller, response, status, security, and event
  contracts before defining the next slice.
- Split Phase 5 into five bounded milestones: authorised draft foundation,
  structured immutable clinical records, summary/appointment completion,
  protected file storage, and React/end-to-end acceptance.
- Selected Scheduling's live appointment decision as the initial care-
  relationship authority; Clinical will derive ownership server-side and will
  not trust browser-supplied patient, doctor, or organisation identifiers.
- Kept file bytes out of Kafka and direct browser-to-MinIO access out of V1;
  File Service will stream through Gateway and use short-lived opaque download
  grants after Clinical authorization.
- No schema, API, configuration, service code, or Phase 5 status changed during
  this design task.
- Next: review and confirm Phase 5A, then implement only its Clinical Service
  foundation and test gates.

### 2026-08-24 — Phase 4 acceptance gate closed

- Recorded the developer-run credentialed browser gate after both passwords
  were entered only through masked prompts.
- Verified six real doctor/receptionist routes through Gateway across 1440,
  1024, and 375 px viewports. All widths matched, Sahha branding and titles were
  correct, the doctor notification panel passed, and no API error was observed.
- Marked the combined UI consistency and pre-Phase-5 acceptance checks complete.
- Marked Phase 4 complete; no Phase 5 implementation was started by this
  documentation update.
- Next: define and review the bounded Phase 5A care-relationship, consultation,
  immutability/correction, authorisation, persistence/API/event, and test design
  before changing Clinical Service code.

### 2026-08-24 — Sahha frontend identity and live-role gate prepared

- Completed the focused product-identity pass without replacing the approved
  frontend design or renaming compatibility-sensitive CSS/storage identifiers.
- Normalised the live platform organisation workflow and its accessible form
  terminology, then updated affected tests.
- Added repeatable Sahha identity checks to the 11 prepared browser scenarios
  and added a credential-safe six-scenario doctor/receptionist live browser
  harness with automatic logout.
- Verified TypeScript, 9 focused tests, all 81 frontend tests, the production
  build, and all 11 prepared responsive browser scenarios.
- Subsequently verified the real non-mutating Auth browser smoke through
  Gateway after starting Vite on port 5173. The safe unknown-login and mobile
  registration checks passed without issuing an access cookie.
- Did not mark the combined Phase 4 UI gate complete because the credentialed
  doctor/receptionist browser scenarios still require the developer's two
  masked password entries.
- Next: start Gateway and the real-auth frontend, run
  `scripts/performance/Test-SahhaFrontendRoles.ps1`, record the sanitized
  results, and close Phase 4 only if all six live scenarios pass.

### 2026-08-24 — Instrumented Gateway and PostgreSQL baseline verified

- Verified the complete implemented backend stack, both native dependencies,
  all direct health endpoints, and seven Eureka application registrations.
- Captured a five-database statement call-count baseline, then compared it with
  30 warm samples for every receptionist and doctor scenario. All 300 measured
  requests passed with zero errors and warm p95 values below 68 ms.
- Classified 48 changed statements into endpoint reads, expected Auth
  login/logout writes, audit writes, transaction control, and the Scheduling
  outbox poll. No endpoint read averaged more than 0.057 ms and no statement
  used temporary blocks.
- Captured non-mutating actual plans for one representative read in each active
  database plus the outbox poll. The plans used the expected lookup indexes and
  executed in at most 0.206 ms except for one 1.594 ms Auth cold-page plan.
- Applied no migration, index, cache, or query change because the evidence does
  not identify a database bottleneck.
- Reverified all service health, Memurai `PONG`, the one-partition appointment
  topic, leader/ISR, and Notification consumer offset 10/10 with lag zero.
- Next: finish the remaining Phase 4 frontend terminology and live browser
  coverage consistency gate, then reassess Phase 4 closure.

### 2026-08-24 — Native Windows Kafka recovery verified

- Diagnosed and removed an embedded newline from the current PowerShell
  `KAFKA_LOG4J_OPTS`, allowing the Kafka launcher to reach Java and KRaft.
- Confirmed no competing listener on ports 9092 or 9093, identified read-only
  attributes on 158 stale tombstones and 51 KRaft checkpoints, and cleared the
  attributes without deleting or reformatting Kafka data.
- Diagnosed the later broker shutdown as the Windows log cleaner failing to
  move an in-use `__consumer_offsets` time index, rather than a topic, network,
  or application error.
- Verified the local-only `--override log.cleaner.enable=false` recovery with
  Kafka listening on port 9092. This workaround must not be copied to Azure or
  another production broker.
- The full readiness check still reports Memurai stopped and Auth plus
  Organisation unavailable; the other implemented services are healthy and
  registered in Eureka.
- Next: start Memurai, Auth, and Organisation, verify complete Eureka readiness,
  then run the controlled receptionist and doctor Gateway workload.

### 2026-08-24 — PostgreSQL statement observability activated

- Verified the Administrator restart loaded the statement extension, explicit
  query IDs, and query I/O timing with no pending restart.
- Retained PostgreSQL's conservative local defaults: 5,000 statements,
  top-level tracking, persistence and utility tracking enabled, and planning
  tracking disabled.
- Installed and queried `pg_stat_statements` 1.12 only in Auth, Organisation,
  Patient, Scheduling, and Notification; no future-service/test database or
  service-account grants were changed.
- Recorded the collector reset timestamp and confirmed no application database
  connections were active during verification, preventing setup SQL from being
  mistaken for workload evidence.
- Next: start the backend stack, run controlled receptionist and doctor reads
  through Gateway, and capture top statements plus selected execution plans.

### 2026-08-22 — PostgreSQL observability settings staged

- Used PostgreSQL `ALTER SYSTEM` to stage `pg_stat_statements` preload and enable
  explicit query IDs plus query I/O timing without editing protected files.
- Reloaded and verified query IDs and I/O timing are active; validated the
  preload entry is syntactically valid and waiting for its required restart.
- Attempted the planned service restart, but Windows Service Control rejected
  it because the tool process was not elevated. PostgreSQL stayed running on
  port 5432 and no extension was installed.
- Next: run `Restart-Service postgresql-x64-18` once from an Administrator
  PowerShell, then verify preload and create the extension in the five active
  service databases.

### 2026-08-21 — PostgreSQL observability preflight recorded

- Verified PostgreSQL 18.1 service/port health and queried active configuration
  safely through `pg_settings` without reading protected configuration files.
- Confirmed `pg_stat_statements` 1.12 is available but not installed in any
  Sahha database; preload libraries are empty, query IDs are automatic, and I/O
  timing is disabled.
- Recorded database sizes, connections, transactions, buffer-cache ratios,
  temporary-file/deadlock counters, and aggregate table statistics for the five
  implemented service databases.
- Confirmed no temporary files or deadlocks and at least 99.92% cumulative
  buffer hits on active service databases, while keeping I/O timing explicitly
  unknown until tracking is enabled.
- Made no PostgreSQL configuration, extension, statistics, schema, or service
  change in this preflight.
- Next: enable query IDs, I/O timing, and the statement preload through one
  planned restart, then install the extension in the five implemented databases.

### 2026-08-21 — Memurai performance and health baseline recorded

- Verified Memurai 4.2.3/Redis protocol 7.4.9 health, configuration, cumulative
  statistics, memory, keyspace, safe TTL metadata, slow log, and latency state.
- Recorded a native 322-sample latency check and 500 persistent PING round trips;
  p95 was 0.1133 ms against the 5 ms target with a 0.2496 ms maximum.
- Confirmed 1.41 MB of 256 MB used, fragmentation 1.00, zero evictions/rejected
  connections/blocked clients, and an empty slow log.
- Recorded a 67.92% cumulative hit ratio and 19 expiring session tombstone
  candidates using 10,296 bytes without reading or publishing their values.
- Marked the Memurai inspection gate complete. No Redis optimisation is
  justified by this local synthetic-data snapshot.
- Next: run the read-only PostgreSQL observability preflight before deciding
  whether `pg_stat_statements` requires a planned configuration restart.

### 2026-08-19 — Doctor cold-stack first pass recorded

- Restarted the relevant application services and recorded the explicit doctor
  first-request pass separately from 30 following warm samples per scenario.
- Verified all five first observations returned `200`; Scheduling availability
  and Notification inbox exposed expected first-use costs of 793.09 and 842.09
  ms while Auth, membership, and later appointment reads remained below 100 ms.
- Verified the following 150 warm requests completed without errors and every
  p95 remained within target, with the highest at 103.49 ms.
- Documented the authentication and sequential-scenario limitations so the
  first observations are not mislabeled as pure JVM or production startup SLAs.
- Next: collect safe Memurai latency, cache-efficiency, memory, keyspace, and
  slow-log evidence without reading or publishing cached session values.

### 2026-08-19 — Doctor Gateway warm baseline recorded

- Ran the authenticated doctor sampler through Gateway with 30 warm samples
  each for session, memberships, availability, appointment history, and the
  notification inbox.
- Recorded five successful first observations, 150 successful measured
  requests, and zero HTTP or transport errors.
- Verified every warm p95 passes its local target; doctor availability was the
  highest at 89.87 ms and the notification inbox was 79.06 ms against its
  stricter 250 ms target.
- Kept cold evidence open because the services were not restarted before this
  run and retained the results as a sequential synthetic-data baseline only.
- Next: restart the local stack once and run the doctor sampler with the explicit
  cold-first label before investigating any endpoint.

### 2026-08-19 — Receptionist Gateway warm baseline recorded

- Ran the authenticated receptionist sampler through Gateway with 30 warm
  samples each for session, memberships, patient directory, available doctors,
  and appointment history.
- Recorded p50/p95/p99/maximum values with five successful first observations,
  150 successful measured requests, and zero HTTP or transport errors.
- Verified every p95 passes its local target; the highest was the cross-service
  Patient directory at 104.82 ms against the 500 ms target.
- Kept cold evidence open because the services were not restarted before this
  run and avoided speculative optimisation from a small synthetic dataset.
- Next: run and record the 30-sample doctor Gateway warm baseline.

### 2026-08-19 — Secure Gateway baseline sampler added

- Added a role-aware, sequential PowerShell 5.1 Gateway sampler for safe Phase
  4 reads with masked credentials and memory-only cookies.
- Added sanitized temporary JSON output, automatic temporary-session logout,
  first-request separation, one warm-up, nearest-rank percentiles, error counts,
  and explicit local p95 target evaluation.
- Corrected the observability guide's unsupported web-session constructor and
  documented the preferred command and cold-label rule.
- Verified the script with PowerShell AST parsing and a live 60.39 ms Gateway
  CSRF probe that did not authenticate or create a session.
- Next: run the sampler with the synthetic receptionist account and record its
  30-request p50/p95 results.

### 2026-08-19 — Live Phase 4 care lifecycle accepted

- Verified the History view exposes the preserved confirmed appointment and
  the receptionist can check it in through the real Gateway-backed UI.
- Verified realtime `PATIENT_CHECKED_IN` delivery, immediate bell-count
  decrement, and persisted notification `read_at` state.
- Verified the doctor started and completed the same appointment, producing
  versions 2 through 4 with attributable audit and single-attempt outbox rows.
- Correlated Kafka partition 0 offsets 7 through 9 with Notification outcomes;
  the consumer group is at 10 of 10 with zero lag and zero rejected events.
- Marked the live multi-role Phase 4 journey and remaining Phase 4 exit criteria
  complete. Phase 4 remains in progress only for its documentation,
  measurement, and targeted UI-consistency gate.
- Next: record the repeatable cold/warm Gateway response-time baseline with p50
  and p95 results before making any performance changes.

### 2026-08-19 — Appointment history, notification badge, and form consistency

- Replaced the implicit rolling appointment cutoff with explicit bounded
  Upcoming and History views.
- Made single-notification reads decrement the bell badge optimistically with
  safe cache rollback on failure.
- Consolidated active V1 creation/edit forms and existing management drawers
  on one accessible, responsive frame while retaining the approved design.
- Passed 25 focused tests, all 81 frontend tests in 27 files, TypeScript, the
  production build, whitespace validation, and 11 responsive browser checks
  without document overflow.
- Next: verify History, check-in notification delivery, and badge decrement in
  the live authenticated receptionist/doctor workflow.

### 2026-08-19 — Disposable appointment cancellation correlated

- Recorded manual confirmation that the preceding reschedule notification
  arrived in the doctor's open browser without a refresh.
- Verified the receptionist-authored `RESCHEDULED -> CANCELLED` transition,
  version 2, required reason, and append-only audit entry.
- Verified single-attempt outbox publication at Kafka partition 0 offset 6,
  exactly one persisted cancellation notification, no rejected events, and
  zero consumer lag at committed/log-end offset 7.
- Recorded manual confirmation that the cancellation notification arrived in
  the doctor's open browser without a refresh.
- Next: add explicit upcoming/history date-range controls so the preserved
  confirmed appointment can be checked in through the React workflow.

### 2026-08-19 — Third appointment reschedule correlated

- Verified the receptionist-authored transition from `REQUESTED` version 0 at
  2026-08-25 10:30 to `RESCHEDULED` version 1 at 2026-08-26 09:00, including
  the required reason and append-only old/new schedule audit.
- Verified single-attempt outbox publication, Notification consumption from
  Kafka partition 0 offset 5, exactly one persisted reschedule notification,
  and zero consumer lag at committed/log-end offset 6.
- Realtime browser delivery remains a manual observation and is not marked as
  verified from persistence alone.
- Next: confirm exactly one reschedule notification arrived in the open doctor
  browser without a page refresh.

### 2026-08-19 — Third appointment request and rolling-range finding

- Verified the receptionist created a third future appointment and the doctor
  received its request notification in realtime.
- Verified Scheduling state, single-attempt outbox publication, Notification
  consumption at partition 0 offset 4, and consumer-group lag returning to
  zero.
- Confirmed PostgreSQL retains all three appointments. The board shows two
  because its rolling query begins at the exact current time minus one day,
  excluding the older confirmed appointment; no record was deleted.
- Next: reschedule the third appointment to another published slot with a
  required reason and correlate the resulting event and notification.

### 2026-08-18 — Terminal appointment transition denied

- Verified the owning doctor receives `409` when attempting to start an
  appointment already in terminal `REJECTED` state.
- Verified the denied command is side-effect free: status, version, reason, and
  total audit/outbox counts are unchanged.
- Confirmed the remaining positive appointment begins at 09:30 Africa/Tunis;
  the local clock was 00:01, so check-in remains intentionally unavailable.
- Verified the owning doctor's pre-start no-show attempt returns `409
  Appointment has not started`; appointment status/version/timestamp and the
  total audit/outbox counts remain unchanged.
- Next: exercise receptionist reschedule and cancellation with reasons on a
  separate disposable appointment while retaining the confirmed appointment.

### 2026-08-17 — Patient-originated request and doctor rejection verified

- Verified a linked patient without an organisation membership booked a
  different published slot, while doctor realtime delivery and receptionist
  recovery both exposed the new request.
- Verified patient booking attribution has no fabricated staff membership and
  doctor rejection attribution uses the correct doctor membership. The required
  rejection reason is identical in appointment state and append-only audit.
- Correlated the request and rejection outbox IDs to Kafka partition 0 offsets
  2 and 3 and Notification's consumed-event rows. Both published once, consumer
  lag is zero, and no event was rejected or duplicated.
- Confirmed the request creates one doctor inbox notification; the doctor-authored
  rejection intentionally has no eligible in-app recipient, while the patient
  uses REST status recovery.
- Verified patient and receptionist REST recovery both show the rejected state
  and reason after refresh.
- Next: execute resource-hiding, role-denial, CSRF, and invalid-transition
  checks while retaining the confirmed appointment for time-gated check-in.

### 2026-08-17 — Patient resource-hiding denial verified

- From the authenticated patient session, queried the appointment projection
  with another existing but unlinked synthetic registration ID through Gateway.
- Verified the backend returned non-disclosing `404`, exposed no foreign
  patient fields or appointments, and the portal recovered only the correct
  linked patient's data after navigation.
- Next: distinguish CSRF rejection from backend role rejection by attempting a
  doctor-only confirmation command as the receptionist without and then with a
  valid CSRF token.

### 2026-08-17 — CSRF and receptionist role denials verified

- Verified the receptionist's confirmation request returns `403` without a
  CSRF header and still returns `403` with a valid CSRF token because only the
  owning doctor may confirm.
- Verified both denied commands are side-effect free: the first appointment is
  still `CONFIRMED` version 1, with no additional audit or outbox row.
- Next: attempt to start the terminal rejected appointment as the authorised
  doctor and require a side-effect-free `409` invalid-transition response.

### 2026-08-17 — First appointment correlated through Kafka and Notification

- Verified the receptionist-created appointment, its append-only booking audit,
  and the doctor's subsequent confirmation audit with distinct actors and
  request IDs.
- Verified both Scheduling outbox records published once without an error and
  matched Kafka partition 0 offsets 0 and 1 by event ID, appointment key, event
  type, and resource version.
- Verified Notification consumed both offsets with zero group lag, created one
  doctor inbox notification for the request, advanced its per-appointment
  cursor through confirmation, and rejected no records. The confirmation's
  `NO_ELIGIBLE_RECIPIENT` result is intentional for the doctor-authored action.
- Recorded the user's successful realtime browser observation, subsequent
  doctor inbox recovery after a full reload, and patient-side recovery of the
  confirmed appointment status.
- Next: request a second appointment from the linked patient portal using a
  different published slot, then verify doctor and receptionist projections.

### 2026-08-17 — Patient-owned registration link verified

- Verified the browser-created account link joins the matching patient identity
  to the expected verified Auth user and records that same user as the linking
  actor.
- Confirmed the appointment and outbox baselines remain zero, preserving a
  clean correlation point for the first receptionist booking.
- Clarified during acceptance that the portal/account link is optional and must
  never block receptionist registration, appointments, or care. The current
  manual claim is an internship mechanism; an opt-in, expiring invitation flow
  is the preferred future UX.
- Next: create the first receptionist appointment while the doctor's realtime
  notification client is connected, then correlate the resulting evidence.

### 2026-08-17 — Matching patient registration verified

- Verified the receptionist-created synthetic patient registration is active,
  belongs to the selected clinic, and uses the exact normalized email of the
  verified patient Auth account.
- Captured its generated registration ID and medical-record number plus its
  date of birth for the one-time patient-owned linking operation.
- Confirmed no `patient_account_link` exists yet, which is the expected state
  before patient authentication. No diagnostic query mutated service data.
- Next: log in as the patient, link the registration through the React page,
  and verify the private doctor/slot projection.

### 2026-08-17 — First doctor availability persisted

- Verified the browser-published doctor schedule exists in Scheduling Service's
  database under the active synthetic clinic and correct doctor membership.
- Verified its timezone, duration, lead time, horizon, location, and both
  weekly windows; no break or time-off rows were created.
- Confirmed the clean appointment baseline remains zero appointments and zero
  outbox events. No database row was created or edited through diagnostics.
- Next: create a fresh receptionist-owned synthetic patient registration using
  the exact verified patient Auth email, then record its linkage inputs.

### 2026-08-17 — Phase 4 readiness completed and fixtures audited

- Verified the React application returns HTTP 200 on `localhost:5173`; Vite is
  listening on IPv6 loopback in this run, explaining the earlier IPv4-only
  false negative.
- Completed the infrastructure/application readiness gate with all required
  services, Gateway, discovery registrations, Kafka consumer assignment, and
  frontend reachability verified.
- Used read-only service-owned database queries to confirm the existing active
  verified role accounts, clinic, memberships, departments, and doctor profile.
  No password hashes, tokens, cookie values, or database credentials were read
  into evidence.
- Confirmed Scheduling starts clean with zero schedules, appointments, and
  outbox events. Found that the existing unlinked patient registration email
  does not match the verified patient Auth account, so a new matching synthetic
  registration is required through the application before patient linking.
- Next: publish the doctor's first organisation-scoped availability through the
  React application and verify it was persisted.

### 2026-08-17 — Backend readiness and Kafka assignment verified

- Verified Auth, Organisation, Patient, Scheduling, Notification, and Gateway
  health endpoints all report `UP`.
- Verified Eureka lists all six applications as `UP` and Kafka reports an
  active Notification consumer assigned to appointment-topic partition 0.
- Confirmed the appointment topic remains empty, so the unset committed offset
  is expected and no delivery claim is made yet.
- The frontend is not listening on port 5173. No appointment or other test data
  was created, and the live acceptance test remains paused at readiness.
- Next: start the React frontend, verify port 5173, then prepare the synthetic
  receptionist and doctor sessions for the first correlated appointment.

### 2026-08-16 — Live Phase 4 preflight executed

- Verified PostgreSQL on port 5432, Memurai on port 6379, and Kafka on port
  9092; PostgreSQL accepts connections and Memurai returns `PONG`.
- Verified `sahha.scheduling.appointments.v1` still has one healthy partition
  with broker 1 as leader and in-sync replica.
- Confirmed all required Sahha application ports, Eureka, Gateway, and the
  frontend were stopped, so no application readiness or end-to-end delivery is
  claimed from this check.
- Confirmed the absent `sahha-notification-appointments-v1` group is consistent
  with Notification being stopped before its first committed event. No data or
  runtime configuration was changed.
- Next: start the required applications and frontend in dependency order, then
  repeat health, discovery, Gateway, and consumer-group checks.

### 2026-08-14 — Live Kafka producer and consumer infrastructure verified

- Corrected the user-applied Spring Boot 4 dependency coordinates in both
  Scheduling and Notification to use `spring-boot-starter-kafka`, restoring
  Boot's `KafkaTemplate` and listener-container auto-configuration.
- Enabled only Scheduling's outbox publisher and Notification's appointment
  consumer in their local IntelliJ configurations; both target the local
  broker without placing environment-specific behavior in base properties.
- Verified both service health endpoints, the one-partition appointment topic
  leader/ISR, and the active Notification consumer-group assignment. The topic
  currently has zero records, so delivery is not yet claimed.
- Verified the affected Maven reactor: all 29 Scheduling and 31 Notification
  tests pass with zero failures, errors, or skips; `git diff --check` passes.
- Next: create one authenticated appointment through Gateway and correlate its
  outbox row, Kafka record, Notification inbox row, and realtime or REST
  recovery result.

### 2026-08-14 — Native local Kafka broker started

- User-verified Apache Kafka 4.3.1 running locally in KRaft mode on port 9092
  without Docker, WSL, or ZooKeeper.
- Recorded the Windows launcher accommodations: a short `SUBST` path for the
  expanded classpath, explicit Log4j configuration URLs, and a bounded
  256-512 MiB local heap that bypasses the removed `wmic` command.
- No Sahha topic, application producer/consumer, or event-delivery claim is
  complete yet.
- Next: create and verify `sahha.scheduling.appointments.v1`, enable both Kafka
  application paths, and capture one correlated appointment event.

### 2026-08-14 — Phase 4 workflow and observability guide drafted

- Added the consolidated Phase 4 workflow, performance, and observability
  guide with the actual service boundaries, synthetic role journey, expected
  appointment states, authorisation denials, realtime path, and REST recovery.
- Explained the approximately 504 kB minified frontend main-chunk warning and
  recorded route-level lazy loading as a measured future optimisation rather
  than hiding the Vite warning.
- Added repeatable browser and PowerShell response timing, Kafka topic/event
  and consumer-lag inspection, safe Memurai key/TTL/latency inspection, and
  PostgreSQL statement and execution-plan timing instructions.
- Separated official browser responsiveness guidance from provisional Sahha
  local p75/p95 budgets so no target is represented as a healthcare regulation
  or measured production result.
- Verified all 22 PowerShell examples parse and `git diff --check` passes. The
  live acceptance gate remains open because Kafka is not installed/running,
  application services were stopped during inspection, and no live percentile
  or end-to-end event evidence has been captured.
- Next: install and start a native Kafka broker, enable the Scheduling and
  Notification Kafka paths, and capture one correlated appointment event
  through PostgreSQL, Kafka, Notification, and WebSocket using the guide.

### 2026-08-14 — Limited patient appointment boundary verified

- Added Patient Service's durable verified Auth-account link, minimum
  self-registration and scheduling-context projections, transactional
  audit/outbox record, Flyway version 3, safe Problem Details, and focused
  ownership/security coverage.
- Added Organisation's minimum patient-visible active-doctor directory and
  Scheduling's patient-owned doctor/slot, booking, and appointment-status
  contracts. Scheduling Flyway version 6 distinguishes patient actors without
  fabricating an organisation membership and preserves staff attribution.
- Connected the approved patient visits route to the real Gateway APIs with a
  one-time ownership link, published-slot selection, idempotent appointment
  request, status history, safe error/retry behavior, responsive styling, and
  no clinical or mock-only fields.
- Corrected the new TanStack Query test to assert mutation variables without
  depending on its internal callback context. Hardened the existing staff
  directory async test after the full parallel frontend suite exposed a
  one-second timing flake; isolated behavior was already correct.
- Verified the full affected backend reactor: 38 Organisation, 12 Patient, and
  29 Scheduling tests passed with no failures, errors, or skips. Verified all
  77 frontend tests in 26 files, the TypeScript/Vite production build, and
  `git diff --check`. The build's approximately 504 kB main-chunk warning is
  recorded for measured optimisation.
- Next: execute and document the complete live Phase 4 Gateway workflow for
  all five roles, including realtime/recovery and denial checks plus the
  required response-time, Kafka, Redis, and PostgreSQL evidence.

### 2026-08-14 — Appointment care-flow frontend and UI audit verified

- Connected the approved appointment board to the verified check-in, start,
  complete, and no-show Gateway commands with state-, role-, time-, and
  optimistic-version-aware controls.
- Added the receptionist's non-clinical waiting list and preserved the owning
  doctor's exclusive start/complete boundary. No receptionist clinical fields
  or actions were introduced.
- Added three lifecycle component tests, expanded the REST adapter contract
  test, and verified all 72 frontend tests in 25 files plus TypeScript and the
  production Vite build.
- Ran the full mock-role browser regression across 11 phone, tablet, and
  desktop captures. Every viewport matched its document/body width and the
  tested drawers and mobile notification panel behaved correctly.
- Audited all 40 frontend form call sites, 402 button call sites, the three
  global style layers, shared management drawers, inline composers, and
  workflow drawers. Added a safe shared hover response for the main button
  primitives and recorded the remaining form, branding, typography,
  interaction, and focus inconsistencies for targeted consolidation.
- Recorded a mandatory pre-Phase-5 gate requiring the full application test
  guide, live role workflow, response-time baseline, Kafka/Redis inspection,
  PostgreSQL query timing, security denials, and targeted UI consistency pass.
  These items are deliberately not marked complete from mock browser evidence.
- Next: integrate the limited patient appointment request/status view through
  Gateway with explicit patient-account identity linkage and focused
  authorisation tests.

### 2026-08-12 — Care-delivery appointment lifecycle verified

- Added the check-in, start, complete, and no-show Scheduling commands with an
  explicit state matrix, active-organisation/resource authorization, owning
  doctor rules, operational check-in authority, and a scheduled-start guard
  for no-show.
- Extended aggregate mutation, safe Problem Details, append-only audit event
  types, minimized transactional outbox events, and Scheduling Flyway version
  5. Replayed commands remain single-write and stale versions remain safe
  conflicts.
- Extended Notification's version-1 event decoder and Flyway version 2 so the
  new events are consumed rather than rejected. Receptionist check-in creates
  one minimal doctor notification; doctor care actions do not self-notify or
  invent a patient account recipient. Updated the React notification contract
  and title for `PATIENT_CHECKED_IN`.
- Added policy, HTTP lifecycle, temporal eligibility, mapper, decoder,
  projection, and migration coverage. The first gate found only a synthetic
  `LocalTime.MAX` PostgreSQL rounding issue; restoring the stable fixture
  window resolved it.
- Fresh `clean verify` passed 27 Scheduling and 31 Notification tests and
  packaged both JARs. All 69 frontend tests, TypeScript, Vite build, XML, and
  whitespace checks also pass.
- The Phase 4 UI/live exit criteria remain open because reception and doctor
  controls are not yet connected to these new commands in the browser.
- Next: integrate receptionist check-in/waiting and doctor start/complete/
  no-show controls into the real React appointment board with focused tests.

### 2026-08-12 — Doctor notification frontend integrated

- Added the real doctor notification inbox, typed REST contracts, shared CSRF
  access for STOMP `CONNECT`, and a dependency-free native STOMP WebSocket
  adapter with heartbeats, bounded reconnect, validation, and teardown.
- Made persisted REST recovery authoritative after initial connection and
  every reconnect. Realtime messages update unread state, invalidate the
  active organisation's appointment queries, and navigate to the real doctor
  appointment board without exposing patient or clinical data.
- Bound the connection and TanStack Query caches to the current user and
  active organisation. Organisation switching closes the previous socket;
  mock role previews deliberately perform no private network calls.
- Added eight focused tests. All 69 frontend tests, the TypeScript/Vite build,
  `git diff --check`, and the responsive browser suite pass. The 375 px browser
  check also opens and bounds-checks the notification panel.
- Kept the live full-stack doctor-receives-notification exit criterion open;
  it requires a manual run with all infrastructure and participating services.
- Next: implement the check-in, start, complete, and no-show backend lifecycle
  with actor permissions, idempotent audit, outbox events, and tests.

### 2026-08-12 — Organisation-bound WebSocket backend gate completed

- Inspected the fresh captured `clean verify` results: all 27 API Gateway and
  all 28 Notification Service tests passed with zero failures, errors, or
  skips.
- Confirmed fresh executable packages at 00:43: the 67,264,413-byte Gateway
  JAR and 110,185,134-byte Notification JAR.
- Marked authenticated in-app WebSocket delivery complete. The verified path
  covers Gateway proxy upgrade, signed-cookie authentication, active
  membership revalidation, raw CONNECT CSRF, subscription authorisation,
  post-commit minimum-data delivery, reconnect-safe REST persistence, and
  same-user organisation isolation.
- Phase 4 remains in progress: the browser notification experience,
  receptionist check-in, start/complete/no-show lifecycle, and limited patient
  projection are not yet complete.
- Next: integrate doctor notifications in the approved React frontend with
  Gateway STOMP, REST recovery, reconnect handling, organisation switching,
  and focused automated tests.

### 2026-08-12 — WebSocket subscription readiness check corrected

- Inspected the post-CSRF-fix gate. All 27 Gateway tests still passed and the
  Notification WebSocket client successfully reached STOMP `CONNECTED`,
  proving authentication and raw CSRF validation now work.
- The remaining error was a test-only timeout at the SUBSCRIBE receipt wait;
  the Spring simple broker registered the subscription but did not return the
  optional receipt assumed by the test.
- Replaced that receipt assumption with a bounded `SimpUserRegistry` wait for
  the exact user-plus-organisation principal and notification destination.
  Notification publication starts only after the authorised subscription is
  visible on the server.
- Static validation passes. Phase 4G remains pending until the full fresh gate
  verifies delivery and same-user cross-organisation isolation.
- Next: rerun the captured Phase 4G clean verification command and inspect all
  fresh reports before starting frontend notification integration.

### 2026-08-12 — STOMP CSRF format mismatch corrected

- Inspected the fresh reports: all 27 Gateway tests passed, while Notification
  ran all 28 tests with one `ConnectionLostException` before STOMP
  `CONNECTED`; all other Notification tests passed.
- Traced Spring Security 7.1 configuration and confirmed that
  `@EnableWebSocketSecurity` defaults to `XorCsrfChannelInterceptor`. Sahha's
  CSRF endpoint and frontend intentionally echo the raw token from the readable
  cookie, so the valid CONNECT header was decoded as an XOR value and rejected.
- Registered the plain constant-time `CsrfChannelInterceptor` using the
  framework's override bean name and removed the duplicate manual
  `CsrfTokenHandshakeInterceptor`; the framework still bridges the HTTP token
  into the WebSocket session automatically.
- Static validation remains clean. Phase 4G is still pending until the focused
  raw-token success, missing-token rejection, tenant isolation, and full
  Notification/Gateway gates pass unrestricted.
- Next: rerun the captured Phase 4G clean verification command and inspect all
  fresh reports before starting frontend notification integration.

### 2026-08-11 — Spring 7 forwarded-header compile break corrected

- Inspected the captured unrestricted build log. Gateway stopped before test
  compilation with one error: Spring 7 no longer provides
  `HttpHeaders.FORWARDED`.
- Replaced only the removed constant with the standard `Forwarded` header
  literal. The Gateway continues stripping that untrusted browser-supplied
  forwarding header before proxying.
- `git diff --check` passes; Phase 4G remains unverified until a fresh clean
  Notification/Gateway gate completes.
- Next: rerun the captured Phase 4G clean verification command and inspect the
  new log for any later compilation, startup, or assertion failures.

### 2026-08-11 — Organisation-bound WebSocket delivery added; gate pending

- Added authenticated native STOMP delivery, exact-origin handshake policy,
  live Organisation membership validation, cookie-bound CONNECT CSRF, and a
  user-plus-active-organisation principal namespace in Notification Service.
- Added post-commit, minimum-DTO user delivery that cannot affect committed
  Kafka processing; REST history and unread count remain the explicit
  reconnect recovery contract.
- Audited the installed Gateway MVC implementation and confirmed it has no
  WebSocket proxy handler. Migrated the small edge module to Gateway Server
  WebFlux while preserving its existing security and REST behavior, then
  added the load-balanced Notification WebSocket route.
- Migrated the 26 existing Gateway checks to reactive test contracts and added
  focused Notification/Gateway tests for CSRF, live membership, post-commit
  failure isolation, same-user tenant isolation, and real proxy upgrade.
- `git diff --check` passes. Managed compilation remains unavailable because
  the sandbox cannot resolve Spring Boot 4.1 and elevated Maven access was
  rejected; all new work remains explicitly unverified.
- Next: run
  `.\mvnw.cmd -pl notification-service,api-gateway -am clean verify` in the
  unrestricted project terminal, inspect every fresh result, and fix all
  failures before starting frontend notification integration.

### 2026-08-11 — Notification REST recovery gate completed

- Inspected the fresh unrestricted Notification reports generated at 19:56:
  all 18 tests passed with zero failures, errors, or skips, including all five
  inbox HTTP integration tests.
- Confirmed the 109,681,855-byte executable Notification JAR was freshly
  packaged and contains the REST controller, resource-server configuration,
  OpenAPI configuration, and inbox service classes.
- Marked authenticated REST history, unread count, idempotent mark-one,
  tenant/recipient-scoped mark-all, live membership revalidation, CSRF,
  Problem Details, OpenAPI, and Gateway forwarding complete.
- Phase 4 remains in progress because authenticated WebSocket delivery,
  frontend notification integration, patient routing, check-in, and the
  remaining appointment lifecycle commands are separate slices.
- Next: implement authenticated, active-organisation-scoped WebSocket/STOMP
  delivery through Gateway with post-commit publication, reconnect-safe REST
  recovery, and focused isolation tests, without frontend integration yet.

### 2026-08-11 — Notification access-denial test restubbing corrected

- Inspected the fresh unrestricted reports: all 26 Gateway tests passed, while
  Notification compiled and ran all 18 tests with 17 passes and one error in
  `authenticationPaginationAndLiveMembershipFailuresAreSafe`.
- The production exception mapping was not the failure. The test first stubbed
  the Organisation context mock to throw access denied, then attempted to
  replace it with `when(mock.resolve(...))`; that restubbing call triggered the
  already-installed exception before Mockito could replace it.
- Changed both failure-path stubs to `doThrow(...).when(...)`, which replaces
  behavior without invoking the already-throwing method. `git diff --check`
  passes; the corrected Notification test still requires an unrestricted
  rerun.
- Next: run
  `.\mvnw.cmd -pl notification-service -am clean verify`, then inspect all 18
  fresh Notification results before marking Phase 4F complete.

### 2026-08-11 — Notification REST recovery implementation added; gate pending

- Added independent Notification resource-server validation, active
  organisation extraction, live Organisation membership revalidation,
  cookie/header CSRF enforcement, request IDs, safe Problem Details, and
  non-cacheable responses.
- Added bounded inbox history, unread count, idempotent owner-scoped mark-one,
  and tenant/recipient-scoped mark-all APIs. Browser input cannot select a user
  or organisation, and response DTOs exclude internal ownership/source IDs and
  sensitive appointment-event fields.
- Added Springdoc security documentation and the authenticated load-balanced
  Gateway route for `/api/v1/notifications/**`.
- Added seven Notification and one Gateway test methods for claim validation,
  authentication, CSRF, live membership, tenant/recipient isolation,
  pagination, response minimisation, read idempotency/scoping, OpenAPI, and
  forwarding.
- `git diff --check` and POM XML/static route/test discovery pass. Maven could
  not resolve Spring Boot `4.1.0` inside the restricted workspace, and elevated
  access to the external Maven cache/network was rejected, so the new slice is
  deliberately not marked complete.
- Next: run
  `.\mvnw.cmd -pl notification-service,api-gateway -am clean verify` in the
  unrestricted project terminal, then inspect and resolve all fresh failures
  before starting WebSocket delivery.

### 2026-08-11 — Appointment Notification consumer gate completed

- Inspected the fresh unrestricted `clean verify` outputs produced between
  05:05 and 05:06: all 22 Scheduling and 11 Notification tests passed with
  zero failures, errors, or skips.
- Confirmed the packaged Scheduling and Notification executable JARs and their
  compiled Flyway version 4 and version 1 migration resources are present.
- Marked appointment outbox publication and Notification Service appointment
  consumption complete. Durable event-ID deduplication, stale-version
  suppression, safe invalid-message receipts, minimum doctor projections, and
  recipient-boundary behavior are now covered by the passing gate.
- Phase 4 remains in progress because authenticated REST inbox recovery,
  read/unread commands, WebSocket delivery, frontend notification integration,
  patient routing, and the remaining appointment lifecycle commands are
  separate slices.
- Next: implement the authenticated, active-organisation-scoped Notification
  REST API for inbox history, unread count, and read-state changes through
  Gateway without starting WebSocket delivery yet.

### 2026-08-10 — Scheduling gate passed; Notification fixture corrected

- Inspected fresh unrestricted reports: all 22 Scheduling tests passed with
  zero failures, errors, or skips, so the Scheduling appointment outbox is now
  marked verified.
- Notification reached all 11 tests with ten passes and one error in the
  stale-version scenario: the test created one appointment under two random
  organisation IDs, and the production cursor correctly rejected that tenant
  change before evaluating event age.
- Corrected only the test data so every related event shares one explicit
  organisation ID. Also corrected the other helper calls so organisation,
  patient, and actor UUIDs follow the declared parameter order. The production
  tenant guard was preserved.
- The corrected fixture passes whitespace/static inspection. The agent-side
  clean Maven run remains prohibited from accessing the external Maven cache
  and local PostgreSQL test schema, so no new Notification result or package
  build is claimed.
- Next: rerun
  `.\mvnw.cmd -pl scheduling-service,notification-service -am clean test` in
  the unrestricted project terminal, then inspect the fresh 22 Scheduling and
  11 Notification results before packaging.

### 2026-08-10 — Appointment Notification consumer implemented; joint gate pending

- Connected Notification Service to its owned PostgreSQL database with JPA,
  Flyway, Hibernate schema validation, UTC timestamps, and Kafka consumer
  configuration.
- Added Flyway version 1 for consumed-event deduplication, rejected-message
  receipts, aggregate-version cursors, and minimum doctor inbox projections.
- Added strict Scheduling appointment-event decoding and Kafka-key validation,
  transactional consumption, persistent event-ID idempotency, stale-version
  suppression, and safe no-recipient outcomes.
- Limited doctor notifications to externally authored request, reschedule, and
  cancellation events. Patient delivery is not inferred from a Patient Service
  identity that is not linked to an Auth account.
- Added safe poison-message handling that stores only Kafka coordinates, a
  reason code, and the payload SHA-256 fingerprint; raw event content is not
  persisted or logged.
- Added 11 Notification tests for migration, contract validation, consumer
  delegation/rejection, idempotency, stale events, data minimisation, recipient
  rules, and rejection fingerprints.
- Verified POM parsing, whitespace, test discovery, and the production inbox
  field boundary. The focused Maven run could not resolve Spring Boot parent
  `4.1.0` because the restricted workspace cannot access Maven Central/cache;
  the elevated run was denied. No backend gate is marked complete.
- No REST notification API, WebSocket/STOMP delivery, frontend change, or
  patient notification routing was added.
- Next: run
  `.\mvnw.cmd -pl scheduling-service,notification-service -am clean test`,
  verify 22 Scheduling and 11 Notification tests with zero failures, and fix
  any compilation, Flyway, Hibernate-validation, or behavior failure before
  starting notification delivery APIs.

### 2026-08-06 — Appointment outbox implementation added; backend gate pending

- Added Scheduling Flyway version 4 and the appointment outbox persistence
  model with one event per appointment audit row, stable IDs, aggregate
  versions, attempt state, and expiring claims.
- Coupled appointment booking and lifecycle event recording to their existing
  local transactions while preserving command idempotency and rollback
  behavior.
- Added a minimized schema-version-1 event mapper. It excludes registration and
  membership identifiers, transition reasons, patient contact information,
  and clinical content.
- Added the keyed Kafka publisher, idempotent producer configuration,
  `FOR UPDATE SKIP LOCKED` claim batches, bounded acknowledgement waits,
  expiring-lease recovery, exponential retry, and claim-token ownership checks.
- Added seven focused test methods, bringing Scheduling to 22 source test
  methods, including PostgreSQL claim recovery and HTTP outbox persistence.
- Verified `git diff --check` and the sensitive-field boundary scan. Maven could
  not begin dependency resolution in the restricted workspace, so Flyway
  version 4 and the new tests are not yet marked verified.
- No Notification consumer, WebSocket delivery, or patient projection was
  added.
- Next: run `.\mvnw.cmd -pl scheduling-service -am clean test`, inspect the
  fresh reports for all 22 Scheduling tests with zero failures, and resolve any
  compilation, migration, or test failure before consuming the events.

### 2026-08-06 — Appointment lifecycle backend gate completed

- Verified fresh Surefire reports generated by the unrestricted clean Maven
  run: all 38 Organisation, 15 Scheduling, and 25 Gateway tests passed with
  zero failures, errors, or skips.
- Confirmed the compiled Scheduling output contains Flyway version 3 plus the
  new lifecycle HTTP integration and transition-policy test classes.
- Marked the explicit transition matrix, actor-specific permissions,
  confirm/reject/reschedule/cancel commands, mandatory reasons, real
  receptionist scheduling UI, real doctor appointment board, and safe
  transition/version conflicts complete.
- Phase 4 remains in progress because check-in/start/complete/no-show,
  transactional appointment events, Notification consumption, WebSocket
  delivery, and the limited patient projection are separate remaining slices.
- Next: implement the Scheduling transactional outbox for minimal appointment
  lifecycle events, an idempotent Kafka publisher with retry-safe claiming,
  and focused persistence/publishing tests without starting the Notification
  consumer yet.

### 2026-08-05 — Appointment lifecycle implementation added; backend gate pending

- Added Scheduling Flyway version 3, current status reasons, unique
  organisation-scoped lifecycle command identifiers, prior/new schedule
  snapshots, and append-only confirm/reject/reschedule/cancel audit events.
- Added the explicit transition matrix, doctor-ownership and operational-role
  policy, tenant-scoped appointment queries, optimistic versions, idempotent
  command replay, mandatory reasons, exact-slot rescheduling, and safe `404`,
  `409`, validation, and authorisation Problem Details mappings.
- Extended Organisation's minimum scheduling directory so a doctor can
  revalidate their own active doctor membership but cannot resolve another
  doctor; receptionist and Organisation Administrator behavior is unchanged.
- Replaced the doctor and receptionist mock appointment routes with the real
  Scheduling API lifecycle page, preserved the existing design language, and
  sourced reschedule choices from live availability. Reception has no
  confirm/reject controls.
- Added focused matrix, Organisation directory, Scheduling HTTP/security,
  audit/idempotency/version, frontend REST, and role-aware component tests.
- Verified frontend TypeScript, 61 Vitest tests across 22 files, the Vite
  production build, and `git diff --check`.
- Maven validation could not start in the restricted sandbox because the
  Spring Boot parent was unavailable and external/cache access was denied;
  elevated execution was also rejected. Backend completion remains
  deliberately unclaimed.
- Next: run the unrestricted Scheduling, Organisation, and Gateway Maven suites
  against local PostgreSQL and resolve any compilation, migration, or test
  failure before starting the appointment outbox/Kafka slice.

### 2026-08-05 — Appointment booking backend validation completed

- Inspected the fresh unrestricted Surefire reports produced between 04:07
  and 04:09. All 38 Organisation, 10 Scheduling, and 25 Gateway tests passed
  with zero failures, errors, or skips.
- Verified Organisation's minimum scheduling-doctor contract, Scheduling
  Flyway version 2 and Hibernate schema validation, availability-backed and
  idempotent booking, patient/doctor/actor revalidation, CSRF and role denial,
  append-only booking audit, occupied-slot subtraction, Gateway forwarding,
  and the concurrent PostgreSQL exclusion-constraint race.
- Marked overlap prevention, the double-booking concurrency test, and the
  receptionist booking outcome complete. Phase 4 remains in progress because
  lifecycle transitions, check-in, outbox/Kafka, notifications, and patient
  projections are still separate milestones.
- Next: define and implement the explicit appointment transition matrix plus
  versioned confirm, reject, reschedule, and cancel commands with actor-specific
  permissions and mandatory reasons where applicable.

### 2026-08-05 — Appointment booking foundation implemented

- Added Scheduling Flyway version 2, the appointment aggregate and status
  vocabulary, booking request idempotency, local append-only booking audit,
  relevant organisation/patient/doctor indexes, and a PostgreSQL GiST
  exclusion constraint that rejects overlapping doctor time ranges.
- Added the availability-backed booking orchestrator and transactional
  persistence boundary. It accepts only an exact calculated slot, checks the
  current booking horizon/lead time/breaks/time off, revalidates the actor,
  doctor, and patient through their owning services, and maps stale or
  concurrent bookings to safe conflicts.
- Added a minimum Organisation scheduling-doctor endpoint that returns only
  active membership ID, organisation/user IDs, display name, and membership
  version to authorised receptionists/administrators; the full staff record is
  not exposed or copied.
- Routed appointment commands through Gateway and removed booked intervals
  from subsequent availability results.
- Integrated active-patient search, slot selection, idempotent booking, error
  states, and successful slot refresh into the receptionist React workflow.
- Added persistence/Flyway, HTTP/security, idempotency, local-audit,
  occupied-slot, Organisation-directory, Gateway-routing, REST-adapter, UI,
  and true two-transaction database exclusion tests.
- Verified all 21 frontend test files and 58 tests, TypeScript checking, the
  Vite production build, Maven XML parsing, and `git diff --check`.
- Backend verification remains open because the restricted environment cannot
  resolve the Spring parent or access the user's Maven cache. No backend task
  is marked complete until the unrestricted test command passes.
- Next: run
  `.\mvnw.cmd -pl organisation-service,scheduling-service,api-gateway -am test`
  from unrestricted local PowerShell and resolve any reported failure.

### 2026-08-05 — Doctor availability backend validation completed

- Inspected the fresh unrestricted Surefire reports produced at 03:36. All 7
  Scheduling Service tests and all 24 Gateway tests passed with zero failures,
  errors, or skips.
- Verified Scheduling application startup, Flyway version 1, Hibernate schema
  validation, availability persistence, deterministic slot subtraction,
  doctor ownership, receptionist read access, cookie CSRF rejection,
  optimistic-version conflicts, and Gateway forwarding.
- Marked the Phase 4A availability model, slot calculation, real frontend
  integration, and doctor-configuration outcome complete. Phase 4 remains in
  progress because appointments, transitions, check-in, events, and real-time
  notifications are separate remaining slices.
- Next: implement the Scheduling-owned appointment aggregate, Flyway version
  2, initial booking command, and transaction-safe prevention of overlapping
  appointments for one doctor and slot.

### 2026-08-05 — Doctor availability vertical slice implemented

- Replaced the empty Scheduling Service scaffold with PostgreSQL/JPA/Flyway,
  resource-server and cookie-CSRF security, live Organisation-context checks,
  safe Problem Details, request IDs, OpenAPI, and an organisation-scoped
  availability aggregate with weekly windows, breaks, time off, location,
  timezone, duration, lead time, horizon, audit metadata, and optimistic
  locking.
- Added doctor-owned `GET/PUT /api/v1/availability/me`, authorised published
  doctor discovery, and deterministic slot calculation endpoints. The slot
  algorithm removes breaks and full/partial time off and rejects past,
  excessive, reversed, and out-of-horizon ranges.
- Routed Scheduling through Eureka-aware Gateway rules and added Gateway,
  HTTP/security, persistence/migration, and slot-calculation tests.
- Replaced the doctor's browser-local availability form with a real TanStack
  Query integration and added a receptionist read-only doctor/slot browser.
  Availability and slot caches are keyed by active organisation and discard a
  doctor selection that is not published in the newly selected organisation;
  appointment creation intentionally remains the next bounded slice.
- Verified all 19 frontend test files and 56 tests, `npm.cmd run typecheck`,
  `npm.cmd run build`, Maven XML parsing, and `git diff --check`.
- Backend verification remains open because the restricted execution
  environment cannot read the user's Maven dependency cache or test
  PostgreSQL. The attempted reactor compile reached compilation before that
  policy denial and produced no Java diagnostic.
- Next: run
  `.\mvnw.cmd -pl scheduling-service,api-gateway -am test` from unrestricted
  local PowerShell and resolve any reported failure before live browser smoke
  testing.

### 2026-08-05 — Phase 3 live validation and Scheduling handoff

- Recorded the user's successful live browser verification after the shared
  CSRF recovery correction; repeated department creation now completes through
  Gateway with the active Organisation Administrator context.
- Closed Phase 3 because its patient registration, search, duplicate handling,
  administrative projection, cross-organisation denial, automated backend and
  frontend checks, synthetic test fixtures, and live workflow evidence are all
  present.
- Selected doctor availability and deterministic slot calculation as the first
  bounded Phase 4 milestone. Appointment persistence, lifecycle transitions,
  Kafka events, and real-time notifications follow after this foundation.
- Next: define the Scheduling Service availability model, API/security
  contract, and Flyway migration before implementation.

### 2026-08-05 — Shared frontend CSRF recovery

- Confirmed from the authoritative local state that the tested Organisation
  Administrator membership is active, the Auth session contains the same
  active organisation and `ORGANIZATION_ADMIN` role, and the first department
  was persisted. The misleading second-create `403` was therefore not an
  organisation-authority failure.
- Moved stale-CSRF recovery from Auth-only mutations into the shared Gateway
  HTTP client. Every unsafe Auth, Organisation, and Patient request now clears
  its cached token, obtains the current CSRF cookie/header value, and retries
  exactly once after a `403`; safe requests are never retried and a repeated
  denial is returned normally.
- Removed the redundant Auth-specific retry and added focused tests for a
  successful refreshed-token retry, the one-retry limit, and safe-request
  behavior.
- Verified all 53 frontend tests, TypeScript checking, the Vite production
  build, and the earlier focused 14-test Auth/Department/HTTP-client suite.
- Next: reload the frontend and retry adding the second department through the
  running Gateway to confirm the live browser workflow.

### 2026-08-05 — Gmail plus-alias identity lookup correction

- Reproduced the live Organisation Administrator assignment failure against
  the authoritative local databases: the target plus-alias account was active
  and verified, and the actor retained the persisted `PLATFORM_ADMIN` role.
- Corrected Organisation Service's Auth directory request to expand the email
  as an encoded URI-template value. This preserves `+` as `%2B` instead of
  allowing query parsing to interpret it as a space and return a misleading
  account-not-found response.
- Added a focused client regression test using
  `ffaresjebali+sahha-org-admin@gmail.com`. `git diff --check` passes. The
  focused Maven run reached Java compilation but the restricted sandbox denied
  the compiler access to the user's external Maven cache, so backend validation
  and the live assignment retry require the updated Organisation Service to be
  rebuilt and restarted locally.
- Next: rerun Organisation Service tests, restart the service, and retry the
  live administrator assignment before continuing the Phase 3 browser flow.

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
