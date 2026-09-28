# Sahha

Sahha is a secure healthcare management and collaboration platform. This
repository is focused on the internship Version 1 and its complete patient
journey.

Read before implementing:

- `docs/PROJECT_CONTEXT.md`
- `docs/INTERNSHIP_PLAN.md`
- `docs/AUTH_IMPLEMENTATION_PLAN.md`
- `docs/GATEWAY_AUTH_GUIDE.md`
- `docs/FRONTEND_AUTH_GUIDE.md`
- `docs/BACKEND_PACKAGE_STRUCTURE.md`
- `AGENTS.md`

## Repository structure

```text
discovery-server/      Eureka service discovery
config-server/         Externalised configuration
api-gateway/           Public backend entry point
auth-service/          Accounts and authentication
organisation-service/ Organisations, departments, and memberships
patient-service/       Administrative patient registry
scheduling-service/    Availability and appointments
clinical-service/      Consultations and clinical records
communication-service/ Messages, referrals, and sharing
notification-service/ In-app and real-time notifications
file-service/          Protected medical files
audit-service/         Append-only audit foundation
frontend/              React and TypeScript application
docs/                  Product context and living implementation plan
infrastructure/        Ignored native runtime state and local configuration
scripts/               Native developer commands and performance checks
```

All backend applications are independent Spring Boot Maven modules aggregated
by the root `pom.xml`.

## Current commands

The workflow is Docker-free. Read
[`docs/NATIVE_DEVELOPMENT.md`](docs/NATIVE_DEVELOPMENT.md) for prerequisites,
safe process ownership, event switches and Azure preparation.

```powershell
node scripts/sahha.mjs check
node scripts/sahha.mjs build backend
node scripts/sahha.mjs start foundation
node scripts/sahha.mjs status
node scripts/sahha.mjs stop
```

Use `start all` for all backend applications or explicit service names. Root
`test backend`, `test frontend`, `test tools` and `build frontend` are also
available. Only launcher-owned processes are controlled; data is never reset.

The separate [synthetic development bootstrap](docs/SYNTHETIC_BOOTSTRAP.md) creates
nine isolated PostgreSQL databases on loopback port `15432` and validates their
service-owned migrations. Its explicitly confirmed reset creates a new generation
and retains the old data; it never resets development databases on `5432`.
Repeatable synthetic account/staff, patient/appointment, clinical and messaging
REST/Kafka checks, protected-file recovery/fresh upload, isolated Redis/Kafka/
SeaweedFS restart checks and all twelve application smoke checks now pass.
Temporary helpers shut down automatically. The full referral/browser demonstration
and clean-machine acceptance remain tracked work; see the guide for per-gate evidence.

For the ordered temporary demo, configure the installed native paths in
[the setup guide](docs/SYNTHETIC_BOOTSTRAP.md#ordered-setup-from-installed-prerequisites),
then run `node scripts/synthetic-setup.mjs check` and
`node scripts/synthetic-setup.mjs run`. Preflight is read-only; the fixed sequence
reuses or initialises only isolated synthetic data, checks retained identities and
stops owned helpers between stages. It never installs software or resets data.
The retained-data sequence passed all 16 stages on 2026-09-20, with all temporary
services stopped. Fresh-checkout/empty-generation and clean-machine acceptance
remain open; this does not certify Azure readiness.

Run all backend module tests:

```powershell
.\mvnw.cmd test
```

Run Auth with its local PostgreSQL and SMTP configuration:

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

Then open `http://localhost:8081/swagger-ui.html`. The complete manual endpoint
sequence and expected responses are in
[`docs/AUTH_SWAGGER_GUIDE.md`](docs/AUTH_SWAGGER_GUIDE.md).

Run Organisation Service against its local PostgreSQL database:

```powershell
.\mvnw.cmd -pl organisation-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

Then open `http://localhost:8082/swagger-ui.html`. The current
Platform-Administrator create/list/read flow, role bootstrap note, CSRF steps,
and expected denials are in
[`docs/ORGANISATION_SWAGGER_GUIDE.md`](docs/ORGANISATION_SWAGGER_GUIDE.md).

For the browser-facing flow, also start Discovery and Gateway. The frontend
calls Auth through Gateway on port `8079`; the start order, routes, cookies,
CSRF flow, security boundaries, and verified commands are in
[`docs/GATEWAY_AUTH_GUIDE.md`](docs/GATEWAY_AUTH_GUIDE.md).
The registration, email-verification, login, refresh, and logout walkthrough
is in [`docs/FRONTEND_AUTH_GUIDE.md`](docs/FRONTEND_AUTH_GUIDE.md).

Auth uses the native Memurai Windows service as its disposable local
Redis-compatible session cache, so Docker Desktop and WSL do not need to run.
Verification, optional Redis Insight setup and environment variables are in
[`docs/LOCAL_REDIS.md`](docs/LOCAL_REDIS.md). Auth continues to use PostgreSQL
if the cache is unavailable.

Gateway and implemented resource services now require a fresh Auth-owned session
decision after local JWT validation, including direct-service requests. This
uses the non-deployable `session-security` module. Internal address configuration,
outage behavior and WebSocket enforcement are documented in
[`docs/SESSION_SECURITY.md`](docs/SESSION_SECURITY.md). Single-service Maven
commands for clients should include `-am` to build the shared dependency.

Backend operation permissions and their explicit role bundles are documented in
[`docs/BACKEND_PERMISSIONS.md`](docs/BACKEND_PERMISSIONS.md). Each service owns
its catalogue; the shared library supplies only immutable claim conversion.
Auth also uses this converter, so include `-am` when building/testing Auth alone.
Permissions never replace live membership, resource ownership or sharing checks.

Local operational probes have a separate, credential-independent policy. The
native launcher requires liveness and readiness, not aggregate diagnostic health.
See [health/readiness](docs/HEALTH_READINESS.md) for the dependency matrix and
`node scripts/health-smoke.mjs all` for the packaged-service smoke checks.

Package all executable backend JARs:

```powershell
.\mvnw.cmd package -DskipTests
```

In IntelliJ, reload the root Maven project once after opening the repository.
The Run dropdown then contains one shared configuration for every application,
plus `Sahha - Infrastructure` and `Sahha - All Services`. Implemented domain
services use their owned PostgreSQL databases. Audit now has its owned Flyway
foundation; central ingestion and authorised queries remain Phase 7 work, with
all business endpoints denied. See [Audit persistence](docs/AUDIT_PERSISTENCE.md).

Default local ports:

| Application | Port |
| --- | ---: |
| API Gateway | 8079 |
| Auth | 8081 |
| Organisation | 8082 |
| Patient | 8083 |
| Scheduling | 8084 |
| Clinical | 8085 |
| Communication | 8086 |
| Notification | 8087 |
| File | 8088 |
| Audit | 8089 |
| Config Server | 8888 |
| Discovery Server | 8761 |

## Local PostgreSQL

PostgreSQL runs locally on `localhost:5432`. Each stateful service owns a
separate database and restricted login:

| Service | Database | Login |
| --- | --- | --- |
| Auth | `sahha_auth` | `sahha_auth_app` |
| Organisation | `sahha_organisation` | `sahha_organisation_app` |
| Patient | `sahha_patient` | `sahha_patient_app` |
| Scheduling | `sahha_scheduling` | `sahha_scheduling_app` |
| Clinical | `sahha_clinical` | `sahha_clinical_app` |
| Communication | `sahha_communication` | `sahha_communication_app` |
| Notification | `sahha_notification` | `sahha_notification_app` |
| File | `sahha_file` | `sahha_file_app` |
| Audit | `sahha_audit` | `sahha_audit_app` |

Generated development connection settings are in the gitignored
`.env.database.local`. Auth also has a separately credentialed
`sahha_auth_test` database that its guarded integration tests may clean and
rebuild. Organisation owns Flyway version 2 in `sahha_organisation`; its tests
use the separate `organisation_test` schema and never clean the development
schema. Patient, Scheduling, Clinical, Communication, Notification and File also
own Flyway-backed slices and isolated test schemas. Audit V1 uses its own database
and guarded `audit_test` schema, with no central ingestion/API yet. See the living
plan for current migration/validation evidence.

Run the migrated frontend:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Validate the frontend:

```powershell
npm run typecheck
npm test
npm run build
```

Native service automation, isolated database generation/reset, identity/staff,
patient/appointment, clinical, file and messaging seeds and dependency probes are
available now. Native prerequisite installation is explicit/manual; full demo and
clean-machine verification remain tracked work. Azure
deployment does not require adding Docker to this project.
