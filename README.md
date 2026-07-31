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
infrastructure/        Local platform configuration (planned)
scripts/               Root developer automation (planned)
```

All backend applications are independent Spring Boot Maven modules aggregated
by the root `pom.xml`.

## Current commands

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
calls Auth through Gateway on port `8080`; the start order, routes, cookies,
CSRF flow, security boundaries, and verified commands are in
[`docs/GATEWAY_AUTH_GUIDE.md`](docs/GATEWAY_AUTH_GUIDE.md).
The registration, email-verification, login, refresh, and logout walkthrough
is in [`docs/FRONTEND_AUTH_GUIDE.md`](docs/FRONTEND_AUTH_GUIDE.md).

Auth uses the native Memurai Windows service as its disposable local
Redis-compatible session cache, so Docker Desktop and WSL do not need to run.
Verification, optional Redis Insight setup, environment variables, and
Docker/WSL alternatives are in
[`docs/LOCAL_REDIS.md`](docs/LOCAL_REDIS.md). Auth continues to use PostgreSQL
if the cache is unavailable.

Package all executable backend JARs:

```powershell
.\mvnw.cmd package -DskipTests
```

In IntelliJ, reload the root Maven project once after opening the repository.
The Run dropdown then contains one shared configuration for every application,
plus `Sahha - Infrastructure` and `Sahha - All Services`. Auth uses its
PostgreSQL-backed `local` profile; Patient keeps its temporary database-free
local profile until its vertical slice begins.

Default local ports:

| Application | Port |
| --- | ---: |
| API Gateway | 8080 |
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
schema. Other service databases remain empty until their owning services
introduce Flyway migrations.

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

Portable infrastructure start/stop commands will be added when Docker Compose
is introduced later in Phase 1.
