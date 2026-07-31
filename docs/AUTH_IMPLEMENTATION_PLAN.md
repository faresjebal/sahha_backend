# Auth Service implementation plan

Last updated: 2026-07-30

## 1. Objective

Implement authentication, account security, platform-level roles, rotating
refresh tokens, user sessions, verification/reset tokens, and security-event
publication without allowing Auth Service to become the owner of organisation
memberships or clinical authorisation.

PostgreSQL is the source of truth. Redis is a disposable acceleration layer
for minimal session state and must never become the only record of a session or
revocation.

## Progress

| Milestone | Status |
| --- | --- |
| A1. User-account persistence foundation | COMPLETE |
| A2. Platform-role persistence foundation | COMPLETE |
| A3. Session and refresh-token persistence foundation | COMPLETE |
| B. Credential and account security | COMPLETE |
| C. Session and rotation core | COMPLETE |
| D. Redis session cache | COMPLETE |
| E. Tokens, cookies, gateway, and organisation context | IN PROGRESS |
| F. Security events and audit delivery | COMPLETE |
| G. Frontend integration | COMPLETE |

Completed foundation:

- Auth local and test profiles connect to separately credentialed PostgreSQL
  databases.
- `V1__create_user_accounts.sql` through
  `V6__index_session_retention.sql` are applied and validated.
- `UserAccount`, `AccountStatus`, repository, and email normalization are
  implemented.
- `UserAccount` uses Lombok for read-only getters, its protected JPA
  constructor, and an explicitly limited `toString`; state changes remain
  controlled by domain methods rather than generated setters.
- `PlatformRole`, `UserPlatformRole`, their repositories, and the immutable
  `PLATFORM_ADMIN` reference role are implemented. Both entities follow the
  same Lombok read-only/protected-constructor/restricted-string convention as
  `UserAccount`.
- `UserSession` is the persistent per-device refresh-token family aggregate;
  `RefreshToken` stores only lowercase SHA-256 hexadecimal hashes and explicit
  same-family parent/replacement lineage.
- Session/token repositories include pessimistic-lock lookups for later atomic
  rotation. PostgreSQL enforces session/user consistency, one active token per
  family, unique token hashes, expiry/revocation state, and one-to-one lineage.
- `VerificationToken` persists only a SHA-256 token hash, is purpose-scoped,
  single-use, expiring, replaceable, and protected by pessimistic locking and
  a database-enforced single-active-token rule.
- Registration, verification, password reset, and credential authentication
  services use configurable BCrypt hashing, secure random tokens, a bounded
  password policy, account-state checks, failed-login counting, and timed
  account locking.
- Account services are grouped under `service/useraccountservice`, verification
  token behavior under `service/verificationtokenservice`, and mail delivery
  under `service/emailservice`.
- Validated public registration, verification, resend, password-reset request,
  and password-reset confirmation endpoints use safe Problem Details,
  request IDs, generic accepted responses, and configurable request cooldowns.
- A provider-independent Spring Mail adapter is configured for Brevo SMTP.
  Authentication emails are rendered as plain text and escaped HTML, queued
  asynchronously after the database transaction, and never expose their raw
  bearer token through an API response or string representation.
- Brevo SMTP was enabled through the ignored local configuration, and a live
  registration verification message was confirmed in the intended inbox.
- Transactional login now creates one device-scoped `UserSession` and initial
  hash-only refresh token. Rotation consumes and links exactly one replacement,
  replay commits a family compromise before returning a generic rejection,
  and current/global logout preserve device isolation.
- A protected non-rotating current-session read restores browser metadata from
  a valid access cookie without creating credentials or refresh-token history.
- Refresh-token lineage is retained through the replay-detection window and
  purged in bounded batches only after absolute session expiry plus a
  configurable retention period. Audit-linked session rows remain intact.
- Password reset now revokes every active session family after advancing the
  account credential version.
- Redis-backed IP throttling now protects login, registration,
  verification/reset request and confirmation, and refresh operations. Keys
  contain only a SHA-256 address hash; a bounded single-instance fallback
  remains active when Redis is unavailable.
- Authenticated users can list safe active-device metadata, revoke one owned
  session, change their password after proving the current password, and
  revoke every existing session through that change.
- Persisted `PLATFORM_ADMIN` authority now protects account suspension,
  reactivation, and disablement. Administrative actions are rechecked against
  the database, self-targeting is denied, and suspension/disablement revoke all
  target sessions.
- A minimal versioned Redis projection caches session identity, state,
  credential version, optional active organisation, expirations, and database
  version only. Active decisions are capped at 30 seconds; revocation,
  compromise, and expiry tombstones remain until absolute session expiration.
- Redis writes occur only after PostgreSQL commit and use an atomic
  version-aware Lua update. Cache misses, malformed values, disabled caching,
  and Redis outages fall back to PostgreSQL.
- Auth OpenAPI/Swagger documents all fifteen implemented account and browser-session
  HTTP operations, including their CSRF requirements, provides synthetic
  examples and safe error contracts, and can be disabled with
  `AUTH_OPENAPI_ENABLED=false`.
- `V5__create_security_events_and_auth_outbox.sql` adds database-enforced
  append-only authentication events and one same-transaction outbox row per
  event. The conditional Kafka publisher marks rows published only after
  acknowledgement and retains failed rows with bounded retry backoff.
- A guarded integration suite rebuilds only `sahha_auth_test`.
- One hundred twelve Auth tests and the 138-test complete backend reactor pass with no
  failures, errors, or skips.

Current next task: model Organisation Service memberships and roles plus the
active-organisation selection contract, without moving resource-level
authorisation into Auth or Gateway.

## 2. Service boundary

Auth Service owns:

- User accounts and credentials.
- Account status, email verification, lock state, and credential version.
- Platform-level roles such as `PLATFORM_ADMIN`.
- Login sessions and refresh-token rotation.
- Email-verification and password-reset tokens.
- Authentication security events and their outbox publication.

Organisation Service owns:

- Organisations and organisation type.
- Invitations and memberships.
- Organisation-scoped roles such as `ORGANIZATION_ADMIN`, `DOCTOR`, and
  `RECEPTIONIST`.
- Membership activation, suspension, removal, and active-organisation
  validation.

Patient Service owns the relationship between a patient administrative record
and an optional Sahha login identity. A registered patient record does not
automatically create an Auth account.

Domain services remain responsible for resource-level decisions. A role or
permission claim alone never grants access to a particular patient, clinical
record, referral, or file.

## 3. Auth persistence model

### `UserAccount`

Required fields:

- `id`: UUID.
- `email` and `normalized_email`; normalized email is unique.
- `password_hash`.
- `first_name`, `last_name`, and optional E.164 phone number.
- `status`: `PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, `SUSPENDED`, or
  `DISABLED`.
- `email_verified_at`.
- `failed_login_attempts` and `locked_until`.
- `last_login_at` and `password_changed_at`.
- `credential_version`, incremented when credentials or security state change.
- `created_at`, `updated_at`, and optimistic-lock `version`.

The Java entity may be named `UserAccount`; the PostgreSQL table must not be
named `user`.

### `PlatformRole` and `UserPlatformRole`

These tables contain only global platform roles. Organisation-scoped role
assignments are not stored in Auth.

For the internship, platform roles and their codes are seeded through Flyway
and are not freely editable through an administrator endpoint.

### `UserSession`

`UserSession.id` is the refresh-token family identifier. Every refresh token
created by one login session references this same session ID.

Required fields:

- `id`: UUID and token-family identifier.
- `user_id`.
- `status`: `ACTIVE`, `REVOKED`, `EXPIRED`, or `COMPROMISED`.
- Optional `active_organisation_id` as a validated context snapshot; no
  cross-database foreign key.
- `device_id_hash` and optional untrusted display `device_name`.
- Bounded `user_agent`, `initial_ip`, and `last_ip`.
- `created_at`, `last_activity_at`, `idle_expires_at`, and
  `absolute_expires_at`.
- `revoked_at`, `revoked_by_user_id`, and `revocation_reason`.
- `credential_version_at_creation`.
- Optimistic-lock `version`.

The absolute expiration can never be extended. Idle expiration may move
forward only up to the absolute expiration.

### `RefreshToken`

Required fields:

- `id`: UUID.
- `session_id`: foreign key to `UserSession`.
- `user_id`: retained for indexed user-wide revocation and consistency checks.
- Unique `token_hash`; the raw token is never persisted.
- `parent_token_id` and `replaced_by_token_id`.
- `created_at`, `expires_at`, and `used_at`.
- `revoked_at` and `revocation_reason`.
- Optional bounded request metadata needed for security investigation.

At most one unused, unrevoked refresh token may be active for a session.
Rotation updates the old token and creates its replacement in one database
transaction.

### `VerificationToken`

Required fields:

- `id`, `user_id`, and unique `token_hash`.
- `purpose`: `EMAIL_VERIFICATION`, `PASSWORD_RESET`, or `EMAIL_CHANGE`.
- `expires_at`, `used_at`, and `created_at`.

Tokens are single-use. Issuing a replacement invalidates older active tokens
for the same user and purpose.

### `SecurityEvent`

Append-only local record of authentication activity:

- Nullable `user_id` and normalized attempted email.
- `event_type`, result, and a safe reason code.
- `session_id`, request ID, IP address, bounded user agent, and timestamp.

Unknown-account login failures require a nullable user ID. Raw exceptions,
passwords, tokens, and clinical payloads are forbidden.

### `AuthOutboxEvent`

Stores minimal security events for reliable publication to Audit Service.
Outbox events are idempotent and are marked published only after successful
delivery.

## 4. PostgreSQL migration sequence

1. `V1__create_user_accounts.sql`
2. `V2__create_platform_roles.sql`
3. `V3__create_user_sessions_and_refresh_tokens.sql`
4. `V4__create_verification_tokens.sql`
5. `V5__create_security_events_and_auth_outbox.sql`
6. `V6__index_session_retention.sql`

Every migration includes explicit foreign keys, unique constraints, check
constraints, indexes for expiry/revocation jobs, and UTC timestamps. Hibernate
uses `ddl-auto=validate`; it never creates or updates the schema.

Auth Service now connects to `sahha_auth` using environment-backed datasource
settings. Migration version 6 is applied successfully and Hibernate validates
the schema without creating or updating it.

## 5. Redis session contract

### Key

```text
sahha:auth:session:v1:{sessionId}
```

### Cached value

Only the minimum session projection is cached:

- `sessionId`
- `userId`
- `status`
- `credentialVersion`
- Optional validated `activeOrganisationId`
- `idleExpiresAt`
- `absoluteExpiresAt`
- `version`

Do not cache:

- Password hashes.
- Raw or hashed refresh/verification tokens.
- Email, phone, names, IP addresses, or user agents.
- Organisation memberships or clinical permissions.

### TTL

An active Redis entry uses the shortest of the remaining idle duration,
remaining absolute duration, and a configurable 30-second maximum active
decision TTL. A revoked, expired, or compromised session is stored as a minimal
revocation tombstone until its original absolute expiration, preventing stale
concurrent writes from reactivating the cache entry.

### Consistency rules

- Cache-aside read: Redis first; on a miss, read PostgreSQL and repopulate.
- PostgreSQL commits before Redis is updated.
- A Redis write failure does not undo a committed database security change.
- Redis unavailability falls back to PostgreSQL and never causes a session to
  be accepted without an authoritative result.
- Cache values carry the database version; older versions cannot replace newer
  active or revoked values.
- Refresh-token rotation always locks and verifies PostgreSQL token state.
  Redis alone never authorises a refresh.
- Last-activity persistence is throttled rather than writing PostgreSQL on
  every request.

## 6. Authentication workflows

### Registration and verification

1. Normalize and validate the email.
2. Create a pending account with a strong password hash.
3. Create a hashed email-verification token.
4. Queue an in-memory SMTP delivery after commit. This is a transitional
   internship adapter until durable Notification Service/outbox delivery is
   available; failed delivery is recovered through the resend endpoint.
5. Mark the account active only after successful single-use verification.
6. Return enumeration-safe responses.

### Login

1. Apply IP and account-aware throttling.
2. Find the account by normalized email.
3. verify the password using a constant-time password encoder.
4. Enforce account, verification, and lock status.
5. Create `UserSession` and the first `RefreshToken` in one transaction.
6. Issue a short-lived access token containing `sub`, `sid`, credential
   version, and global platform roles.
7. Return refresh/access credentials through secure, HttpOnly cookies.
8. Populate the Redis session projection after commit.
9. Record a security event without logging sensitive values.

### Refresh-token rotation

1. Hash the presented refresh token.
2. Lock its PostgreSQL row.
3. Validate the token, session, account status, credential version, and
   expirations.
4. Mark the current token used/revoked with reason `ROTATED`.
5. Insert the replacement token with the same `session_id`.
6. Set `replaced_by_token_id`.
7. Commit atomically and then update the session cache.
8. Issue a new short-lived access token and raw refresh token.

Only one concurrent refresh succeeds. The frontend must use a single-flight
refresh operation; reuse of an already consumed token is treated as a possible
compromise.

### Refresh-token reuse

1. Detect presentation of a used or rotated token.
2. Mark the session `COMPROMISED`.
3. Revoke every active refresh token in that session family.
4. Write a Redis compromise/revocation tombstone after commit.
5. Publish a high-priority security event.
6. Require a new login.

Another device uses a different `UserSession`, so it remains active unless the
user chooses global logout or the account itself is compromised.

### Logout and security changes

- Logout current device: revoke one session family.
- Logout everywhere: revoke every active session for the user.
- Password change/reset: increment `credential_version` and revoke existing
  sessions according to the selected security policy.
- Account suspension/disablement: revoke all sessions immediately.
- Active-organisation change: Organisation Service validates membership before
  Auth updates the session context and issues a replacement access token.

## 7. Initial configurable security values

Proposed internship defaults:

- Access-token lifetime: 10 minutes.
- Session idle lifetime: 7 days.
- Session absolute lifetime: 30 days.
- Password-reset token lifetime: 30 minutes.
- Email-verification token lifetime: 24 hours.
- Email-verification resend cooldown: 2 minutes.
- Password-reset request cooldown: 2 minutes.

These values remain configuration, not constants embedded in business code.
Production values require a formal security review.

## 8. API sequence

Implement in this order:

1. Registration and email-verification request/consume. `COMPLETE`
2. Login. `COMPLETE`
3. Refresh with rotation. `COMPLETE`
4. Logout current session. `COMPLETE`
5. List the current user's sessions without exposing sensitive metadata.
   `COMPLETE`
6. Revoke one owned session. `COMPLETE`
7. Logout everywhere. `COMPLETE`
8. Password-reset request/consume. `COMPLETE`
9. Password change. `COMPLETE`
10. Account suspension hooks for authorised platform administration.
    `COMPLETE`

All errors use safe Problem Details responses and must not reveal whether an
email address exists.

## 9. Test plan

### Unit tests

- Email normalization and account-state decisions.
- Lock and expiration calculations.
- Session idle/absolute expiration.
- Token hashing and rotation decisions.
- Token-reuse compromise decision.
- Credential-version invalidation.
- Redis TTL calculation and cache serialization.

### PostgreSQL integration tests

- Flyway applies cleanly to the guarded local `sahha_auth_test` database.
- The suite refuses to clean any database except `sahha_auth_test`.
- Container-backed PostgreSQL replaces this local fallback in CI when a
  container runtime is available.
- Normalized email and token hashes are unique.
- At most one active refresh token exists per session.
- Rotation is atomic under concurrency.
- Revocation and expiry queries use the intended indexes.
- Final role and table ownership remains inside `sahha_auth`.

### Redis integration tests

- Cache hit and miss behavior.
- Correct TTL bounded by absolute expiration.
- PostgreSQL fallback when Redis is unavailable.
- Revocation tombstones replace active entries.
- An older version cannot overwrite a revoked session.
- No prohibited sensitive fields appear in cached values.

### Security integration tests

- Unknown email and wrong password return indistinguishable responses.
- Suspended, disabled, locked, and unverified accounts are denied.
- One refresh succeeds; replaying the previous token compromises the session.
- Revoking laptop session A does not revoke phone session B.
- Logout everywhere revokes both sessions.
- Password reset consumes one token once and invalidates sessions.
- A user cannot list or revoke another user's sessions.
- Organisation roles cannot be invented through Auth requests or token claims.

## 10. Implementation milestones

### A1. User-account persistence foundation — `COMPLETE`

- [x] Connect `auth-service` to `sahha_auth`.
- [x] Add the user-account Flyway migration and JPA mapping.
- [x] Add guarded PostgreSQL integration validation using
      `sahha_auth_test`.

Exit verified: migrations apply from empty state, Hibernate validation passes,
the development runtime starts, and the full Maven reactor remains green.

### A2. Platform-role persistence foundation — `COMPLETE`

- [x] Add `V2__create_platform_roles.sql`.
- [x] Add `PlatformRole` and `UserPlatformRole` mappings and repositories.
- [x] Seed the immutable `PLATFORM_ADMIN` reference role.
- [x] Enforce uniqueness and valid assignment/assigned-by relationships.

Exit verified: migration version 2 applies both incrementally and from empty
state, global role assignments are constrained correctly, Hibernate validates
the mappings, and organisation-scoped roles remain absent from Auth
persistence.

### A3. Session and refresh-token persistence foundation — `COMPLETE`

- [x] Add `V3__create_user_sessions_and_refresh_tokens.sql`.
- [x] Model one `UserSession` per device with its ID as the token-family ID.
- [x] Persist hashed refresh tokens with parent/replacement lineage.
- [x] Enforce session/user consistency and one active token per family.
- [x] Add pessimistic-lock repository lookups for later atomic rotation.
- [x] Exclude hashes, device identifiers, network metadata, and relationships
      from JSON and string output.

Exit verified: version 3 applies incrementally and from empty state, Hibernate
validates both entities, positive lifecycle/rotation-lineage persistence
passes, invalid expiry/revocation/cross-user/cross-family states are rejected,
the development runtime starts, and the full Maven reactor remains green.

### B. Credential and account security — `COMPLETE`

- [x] Add `V4__create_verification_tokens.sql`.
- [x] Persist purpose-scoped, hash-only, expiring, single-use verification
      tokens with atomic replacement.
- [x] Add configurable BCrypt hashing and a bounded password policy.
- [x] Implement registration, email verification, password-reset issuance and
      consumption, account-state enforcement, failed-login counting, and timed
      account locking.
- [x] Return indistinguishable authentication denials for unknown and
      ineligible accounts at the service boundary.
- [x] Add validated public HTTP DTOs and safe Problem Details responses.
- [x] Return enumeration-safe accepted responses for email-verification and
      password-reset requests.
- [x] Add configurable per-account verification/reset request cooldowns.
- [x] Add provider-independent, asynchronous Brevo SMTP delivery with
      plain-text/HTML templates and fail-fast enabled configuration.
- [x] Add IP-aware throttling.
- [x] Connect password/security changes to session revocation after the
      session core exists.

Exit verified: positive registration/verification/reset behavior,
single-use and replacement rules, BCrypt persistence, account locking,
unknown-account denial, suspended/disabled denial, schema constraints, and
secret-exclusion rules pass. Public HTTP validation, generic accepted
responses, safe Problem Details, request IDs, cooldowns, SMTP MIME construction,
HTML escaping, local endpoint startup, one confirmed Brevo inbox delivery, and
password-reset-triggered session revocation, Redis/local fallback IP
throttling, authenticated password change, session management, and
platform-administrator lifecycle controls all pass.

### C. Session and rotation core — `COMPLETE`

- [x] Add configurable seven-day idle and 30-day absolute session lifetimes.
- [x] Create one persisted device session and initial hash-only refresh token
      in the successful credential transaction.
- [x] Keep raw refresh values only in secret-excluded in-memory issuance
      results.
- [x] Rotate under PostgreSQL pessimistic locks, consume the parent before
      inserting one replacement, and persist explicit parent/replacement
      lineage.
- [x] Commit `COMPROMISED` state and revoke the active replacement when a
      rotated token is replayed, while returning one generic rejection.
- [x] Implement current-device logout, global logout, session expiration, and
      account/credential-version rejection.
- [x] Revoke existing session families after password reset.
- [x] Prove that concurrent presentation permits one rotation and then
      compromises the family, without affecting a separate device family.

Exit verified: focused PostgreSQL integration tests cover initial issuance,
hash-only storage, rotation lineage, replay persistence, real two-thread
concurrency, current/global logout, device isolation, idle expiration, and
password-reset revocation. Bounded retention tests prove expired lineage is
purged without deleting current tokens or audit-linked sessions. All 112 Auth
tests and all 138 reactor tests pass with no failures, errors, or skips.

### D. Redis session cache — `COMPLETE`

- [x] Add Spring Data Redis with environment-backed local connection settings.
- [x] Cache only the versioned minimal session projection under
      `sahha:auth:session:v1:{sessionId}` using explicit JSON rather than Java
      native serialization.
- [x] Cap active decision entries at 30 seconds and bound every entry by the
      relevant session expiration.
- [x] Retain revoked, expired, and compromised tombstones until absolute
      expiration.
- [x] Apply atomic version-aware writes that prevent an older active
      projection from replacing a newer tombstone.
- [x] Update login, rotation, replay compromise, expiry, current/global
      logout, and password-reset revocation only after PostgreSQL commit.
- [x] Use cache-aside PostgreSQL loading for misses, invalid schema, malformed
      JSON, disabled caching, and connection failures.
- [x] Keep refresh-token rotation PostgreSQL-only and exclude credentials,
      tokens, PII, device data, and network metadata from Redis.
- [x] Keep Auth health independent of optional Redis availability.
- [x] Document and verify native Memurai on Windows, with Docker Desktop and
      WSL2 retained only as alternatives.

Exit verified: 12 focused cache tests and four real Redis/Memurai tests
cover TTL, serialization boundaries, cache hit/miss, after-commit behavior,
atomic version ordering, tombstones, logout invalidation, clearing Redis, and
forced-outage PostgreSQL fallback without stopping the local service. The
complete 112-test Auth suite and 138-test backend reactor pass with no failures,
errors, or skips. Memurai returns `PONG`, is capped at 256 MB with
`allkeys-lru`, and PostgreSQL fallback is exercised through an unused port.

### E. Tokens, cookies, gateway, and organisation context — `IN PROGRESS`

- [x] Define environment-backed JWT and browser-cookie properties with a
      ten-minute maximum access-token lifetime and production-secure defaults.
- [x] Add RS256 key loading with local/test-only ephemeral key generation and
      a session-bound JWT validator that checks Redis/PostgreSQL authority.
- [x] Issue signed access tokens with minimal `sub`, `sid`, credential-version,
      token-type, audience, and global-platform-role claims, bounded by both
      the ten-minute token lifetime and the session expirations.
- [x] Deliver access and rotating refresh credentials through scoped HttpOnly
      cookies with production-secure defaults and local HTTP overrides.
- [x] Add SPA CSRF issuance, readable XSRF cookie, header enforcement, and
      rotation after login, refresh, and logout. The public CSRF response now
      exposes the same raw value saved in the cookie rather than Spring's
      masked request attribute.
- [x] Expose login, refresh, current logout, and global logout HTTP contracts,
      plus a public-only RS256 JWKS document.
- [x] Expose a protected non-rotating current-session read for browser
      restoration without issuing credentials or adding refresh-token rows.
- [x] Validate the complete browser flow, refresh replay compromise,
      database-sourced global roles, session-bound access validation, cookie
      clearing, and secret-exclusion rules in 20 focused tests.
- [x] Route Auth and JWKS through discovery-aware Gateway routes and validate
      RS256 access cookies there with issuer, audience, access-token type,
      session ID, credential version, and global-role checks.
- [ ] Add active-organisation selection and repeat authentication validation
      inside every domain resource service.

Current exit evidence: Auth health, public-only JWKS, and CSRF issuance passed
a real local-profile HTTP smoke test. A real Eureka/Auth/Gateway smoke test
also proved discovery routing, separate client cookie jars, public Auth
reachability, protected-route rejection, and one canonical request ID. The
Gateway suite passes 16 tests, the full Auth suite passes 112 tests, and the
full reactor passes 138 tests with no failures, errors, or skips. Active
organisation context and domain-service validation remain unfinished.

### F. Security events and audit delivery — `COMPLETE`

- [x] Add `V5__create_security_events_and_auth_outbox.sql`.
- [x] Persist append-only login, refresh/reuse, verification, password,
      session-revocation, and account-administration events.
- [x] Store one secret-free, idempotent outbox payload in the same transaction
      as each security event.
- [x] Add conditional Kafka publication with acknowledgement-before-marking,
      stable event IDs, retry metadata, and bounded exponential backoff.
- [x] Keep passwords and raw refresh/verification/access tokens outside event
      rows and outbox payloads.

Exit verified: PostgreSQL rejects event mutation, success and denial events
produce same-transaction outbox rows, acknowledged publication is marked once,
failed publication remains pending for retry, and automated tests prove
payloads exclude presented passwords and raw tokens.

### G. Frontend integration

- [x] Use Gateway `/api/v1/auth/**` routes with credentials enabled and keep
      access/refresh tokens outside JavaScript.
- [x] Bootstrap CSRF automatically, attach the server-selected header to
      unsafe requests, invalidate it after token rotation, and retry one stale
      CSRF decision.
- [x] Add React Hook Form/Zod patient registration and accepted-email
      guidance.
- [x] Consume Auth's emailed `/verify-email?token=...` link and remove the
      bearer token from the visible URL before confirmation.
- [x] Implement login, non-rotating session-first startup restoration,
      scheduled near-expiry renewal, logout cleanup, and safe Problem
      Details/network errors.
- [x] Serialise refresh across browser tabs with an exclusive Web Lock and
      recheck the shared access cookie after acquiring it so a waiting tab
      skips unnecessary rotation.
- [x] Route only from signed server metadata: `PLATFORM_ADMIN` maps to the
      platform workspace and accounts without a global platform role map to
      patient until Organisation Service provides scoped roles.
- [x] Keep unfinished domain services mocked independently from real Auth.
- [x] Verify the REST adapter and visible journeys in 11 focused tests plus the
      complete frontend typecheck, build, and 11-view browser layout suite.

Exit verified: the relevant React journeys use the real Gateway APIs; 4 test
files/16 tests, TypeScript typecheck, production build, and all 11 responsive
browser checks pass. The local ignored environment enables real Auth while
domain mocks remain available.

## 11. Definition of done

Auth is complete for the internship only when:

- PostgreSQL remains authoritative and Redis can be cleared without losing
  security state.
- Raw passwords and tokens are never persisted, cached, logged, or audited.
- Refresh rotation and replay detection are atomic.
- Per-device and global revocation work.
- Organisation membership remains owned and validated by Organisation
  Service.
- Every sensitive success and denial has automated test coverage.
- The React application authenticates through the gateway using secure browser
  session handling.
