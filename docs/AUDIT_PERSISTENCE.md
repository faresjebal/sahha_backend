# Audit persistence foundation

Last updated: 2026-09-13

This is the Phase 1 owned-database foundation, not Phase 7 central audit delivery.
No Kafka consumer, producer adapter, repository API, controller, query permission
or Gateway route is introduced. Existing services continue writing only their
own local audit/outbox tables. No existing audit history is copied or backfilled.

## Owned storage

Audit uses PostgreSQL `sahha_audit` with restricted login `sahha_audit_app`.
`AUDIT_DB_URL`, `AUDIT_DB_USERNAME` and `AUDIT_DB_PASSWORD` are required external
settings. Only the `local` profile imports the ignored root `.env.database.local`
(also when launched from the module directory). Azure must supply settings
externally and must not activate `local`. No Docker or Azure resources are needed
for this slice.

Flyway owns schema creation and checksum validation; clean is disabled. V1 creates
`audit_event`, indexes and an append-only trigger. JDBC is sufficient for this
schema-only slice: there is no Hibernate schema generation, entity or generic CRUD
repository to imply that central ingestion/query is already implemented.

The record stores only bounded metadata:

- Central UUID plus source service/event UUID; the pair is unique across retries.
- Explicit `GLOBAL` or `ORGANISATION` scope; organisation scope requires an
  organisation UUID, and global scope cannot carry organisation/patient UUIDs.
- Optional actor/resource/patient UUID references, coded action/resource/result,
  optional coded access reason and bounded request correlation identifier.
- Source occurrence time and database-assigned receipt time. Clock skew between
  services is allowed; receipt time need not follow the source timestamp.

There are no cross-service foreign keys, arbitrary JSON, clinical payloads,
free-text explanations, identity/contact snapshots, credentials or token columns.
Nullable actor/resource references support pre-authentication denials and system
activity; they do not confer anonymous record access. Codes and request IDs still
need producer-specific validation before future ingestion. Regex checks are not
content sanitisation or proof that an incoming event is trustworthy.

The database rejects UPDATE, DELETE and TRUNCATE, including no-op statements.
Corrections must append new attributable records; central correction handling is
not yet implemented. Uniqueness is the persistence prerequisite for idempotency,
not a completed Kafka replay/acknowledgement or conflicting-payload policy.

The development login owns its database to run migrations. As with other local
services, an owner can alter/drop objects or disable triggers using DDL. This is
application-level append-only protection, not tamper-proof storage against a
database administrator. Separate migration/runtime privileges, producer trust,
retention/backup and stronger operational controls remain hardening work.

## Closed HTTP surface and health

Only GET `/actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness` and `/actuator/info` are public. Health components and
details are never exposed. Readiness includes `db`; liveness does not require the
database. All other requests are denied, including Audit business/internal URLs,
login, Swagger and other Actuator endpoints. There is no generated local login,
HTTP Basic or browser session. Even an already authenticated administrator or
doctor is denied by the foundation policy.
CSRF is disabled only while every mutation is unconditionally denied and there
is no cookie-authenticated API; the default CSRF session repository must not
create sessions for rejected POST/PUT/PATCH/DELETE requests.

Before exposing anything in Phase 7, implement the normal authoritative-session,
CSRF, service-owned permission, organisation and resource checks; do not turn the
foundation policy into a blanket role-based Audit reader.

## Validation and native use

Run from the root with native PostgreSQL available:

```powershell
.\mvnw.cmd -pl audit-service -am verify
node --test scripts/sahha.test.mjs
node scripts/sahha.mjs start discovery-server config-server audit-service
node scripts/sahha.mjs status discovery-server config-server audit-service
node scripts/sahha.mjs stop audit-service config-server discovery-server
```

The runner now treats Audit as PostgreSQL-dependent and starts it with
`local,platform`. The IntelliJ `local` profile and standalone local Maven run
continue working without Config Server.

Tests use only `audit_test` inside the owned database, never clean any schema, and
roll back synthetic event inserts. A pre-migration guard rejects the wrong
database/login, a non-test default/search schema, extra Flyway schemas, or enabled
clean. A previously absent `audit_test` is created by Flyway; it and its migration
history remain available for repeat validation. No development data is reset.

Evidence: 50 Audit tests passed (real PostgreSQL, constraint/immutability checks,
unsafe-target guard, real random-port HTTP and simulated database-health failure),
plus 12 native-runner tests. The packaged native service applies and revalidates
V1, registers with Eureka, consumes Config `native-v1`, reports healthy and
denies business requests without session cookies. See the dated living-plan
entry for final native startup and cleanup evidence.
