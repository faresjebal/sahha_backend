# Native development and Azure preparation

Last updated: 2026-09-19

Docker, Compose and Kubernetes are not V1 requirements. The native runner starts
the existing Java applications directly. It does not install infrastructure,
provision cloud resources, reset databases or change Windows services.

## Prerequisites

- Java 21 on `PATH`; the Maven Wrapper downloads its pinned Maven version.
- Node 22 and `npm ci` in `frontend`. The runner uses only Node built-ins.
- Native PostgreSQL on `localhost:5432`, with the separate databases/restricted
  owners in the root README. Credentials belong in ignored `.env.database.local`,
  never in Config Server or frontend variables. Fresh-machine database/credential
  provisioning is still manual; never use an administrator login for an app.
- Native [Memurai/Redis](LOCAL_REDIS.md). Auth uses `AUTH_REDIS_PORT` and
  Communication uses `REDIS_PORT`; both default to 6379.
- Native Kafka for events: see [the Phase 4 guide](PHASE_4_WORKFLOW_AND_OBSERVABILITY_GUIDE.md#4-current-local-prerequisites).
  Its workstation-specific paths are not a portable installer. Never reformat
  an existing broker directory.
- Native [SeaweedFS](LOCAL_SEAWEEDFS.md) for files: private S3 gateway on 8333,
  filer on 18888 (not Config Server's 8888). No permanent public file URLs.

For an independent database environment, the new
[synthetic database bootstrap](SYNTHETIC_BOOTSTRAP.md) uses a separate PostgreSQL
instance on `15432`, nine owned databases, private generated credentials and
recoverable generation reset. It does not adopt the databases above or modify
their credentials. The original application launcher still uses development
settings; use `node scripts/synthetic-demo.mjs seed` for the separate temporary
Gateway/Auth/Organisation identity/staff setup. Its repeat/refusal gate is verified.

Identity/staff seeding and private Redis/Kafka/storage bootstrap commands are
available in the synthetic guide. Its strict infrastructure repeat/refusal gate
and four small isolated application batches covering all twelve services pass.
Patient/appointment, clinical recovery/repeat, primary-file recovery/repeat and
messaging REST/Kafka recovery/repeat checks also pass. Second-file fresh upload/
repeat and original-file preservation pass. Selected-sharing/source handoff now
passes 81 real Gateway/Clinical/File assertions, including expiry, revocation and
an outstanding download-token denial. See the serial synthetic-referrals commands
in the bootstrap guide. Real browser messaging delivery/restart now passes 48/45
assertions. Full referral/shared-care and clean-machine verification remain
outstanding. There is deliberately no destructive database reset/purge
command; the independent synthetic reset retains prior generations.

For a single ordered, temporary installed-machine demo, use
`node scripts/synthetic-setup.mjs check` followed by
`node scripts/synthetic-setup.mjs run`, with the native paths configured in
[the ordered setup guide](SYNTHETIC_BOOTSTRAP.md#ordered-setup-from-installed-prerequisites).
It rejects stale JARs/missing dependencies or occupied ports, reuses retained
synthetic data, holds one operation lock across all 16 stages and stops each
owned batch before continuing. It does not use `.env.database.local` or the
normal all-service launcher. Reports are private and do not certify Azure or a
clean machine. The 2026-09-20 retained-data run passes all 16 stages in 43 minutes,
including both 45-assertion browser replays, stable source/artifact fingerprints,
58 retained identifiers and zero remaining project listeners/owned runtimes.
Fresh-checkout/empty-generation acceptance is the next foundation task.

Windows process ownership also checks creation times when enumerating Java
children. An older unrelated process can retain a parent PID later recycled for
Sahha; it is ignored, never stopped. Unknown contemporary children or missing
ownership/creation metadata still refuse cleanup. The 2026-09-20 regression and
real leftover-process cleanup pass. The first failed report stays retained; the
subsequent complete 16-stage run provides separate success evidence.

## Root commands

Run from the repository root:

```powershell
node scripts/sahha.mjs check
node scripts/sahha.mjs test tools
node scripts/sahha.mjs build backend
node scripts/sahha.mjs test backend
node scripts/sahha.mjs test frontend
node scripts/sahha.mjs build frontend
node scripts/sahha.mjs start foundation
node scripts/sahha.mjs status foundation
node scripts/sahha.mjs stop foundation
```

`foundation` starts Discovery, Config Server, Auth, Organisation, Patient and
Gateway in order. `start all` includes all twelve applications; Audit now requires
its owned PostgreSQL database, but exposes no business API. See
[Audit persistence](AUDIT_PERSISTENCE.md). Explicit service names are accepted
in dependency order; start Discovery/Config before starting a client alone.
Build/test default to `all`; stop/status default to all known applications.
Startup never rebuilds automatically. `build backend` skips tests when packaging
JARs, so `test backend` is a separate required gate.

The reactor also contains the non-deployable `session-security` library. For a
single-service Maven command use `-pl <service> -am` to build its dependencies.
Gateway and resource services require Auth for every protected request; when
changing Auth's port/address also set `AUTH_SESSION_CHECK_URI`. See
[session security](SESSION_SECURITY.md) for the internal contract and outage behavior.

Managed services bind to `127.0.0.1`, with a 256 MB maximum Java heap each. The
runner waits for liveness AND readiness. Optional aggregate-health failures do
not withdraw local serving capacity. Each stateful service requires its own
database; Config requires its repository. See [health/readiness](HEALTH_READINESS.md)
for the dependency matrix, failure behavior and public-probe boundary.
Run `node scripts/health-smoke.mjs all` against the packaged services for redacted
probes, stale-credential isolation and denied non-public Actuator checks.
All twelve packaged applications have passed these checks (108 assertions on
2026-09-14, including the final individual Gateway/Audit checks). Start only the
needed small batch and stop owned processes afterward on resource-limited machines.
Eureka registration can take
longer; `status` also displays discovery state and `config=native-v1` for clients
that consumed remote configuration.

Logs and ownership records are under ignored `infrastructure/.state/native`.
The runner never prints environment values or parses secret files. Application
secret loading stays in each application. Treat logs as private diagnostics;
do not paste them wholesale into tickets.

Startup refuses occupied unmanaged ports, ambiguous JARs and invalid arguments.
Shutdown requires the exact service JAR and a unique launch marker in the running
process command line; it never kills Java/Node by name. A lock prevents concurrent
start/stop. After a crashed launcher, inspect the PID in `process-control.lock`
before manually removing only that stale lock, never the whole state directory.

The runner resolves the real Java 21 runtime instead of Oracle's Windows PATH
shim. Matching legacy shim/JVM descendants are ownership-checked before stop;
the exact Windows system console host is left to OS lifetime management. Unknown
application children still prevent automatic shutdown.

Stop retains all logs, databases, objects, Kafka data and infrastructure services.
Windows process termination is not production graceful shutdown; Azure needs
host-managed shutdown and recovery. Live validation is on this Windows machine;
other operating systems and clean-machine reproducibility remain delivery gates.

## Native test integrations

For a separately running Redis test helper:

```powershell
$env:AUTH_TEST_REDIS_PORT = '16379'
node scripts/sahha.mjs test backend
```

The runner also supplies that numeric port to Communication's
`sahha.test.redis.port` property. It does not start Redis or flush arbitrary keys.
Auth tests use guarded `sahha_auth_test`; other implemented domain tests use
isolated service-owned schemas. Never point tests at production.

The full 2026-09-14 API-convention regression passed with Maven capped at 256 MB
and each test JVM at 512 MB; 888 tests passed and one opt-in storage test skipped.
To use these bounds in a dedicated PowerShell test session:

```powershell
$env:AUTH_TEST_REDIS_PORT = '16379'
$env:MAVEN_OPTS = '-Xmx256m'
.\mvnw.cmd verify "-Dsahha.test.redis.port=16379" "-DargLine=-Xmx512m"
```

Start/stop your owned disposable Redis helper separately; these commands never
manage Windows services or shared PostgreSQL. Do not stop unrelated processes.
Frontend tests can use `npm.cmd test -- --maxWorkers=1` from `frontend` to keep
worker count bounded. [API conventions](API_CONVENTIONS.md) records the error,
documentation, session/resource authority and safe-logging boundaries.

The opt-in storage check writes then removes its own unique synthetic `live-test/`
object on the local S3 gateway:

```powershell
$env:SEAWEEDFS_LIVE_TEST = 'true'
node scripts/sahha.mjs test backend
```

A passing TCP `check` is not proof of authentication, migrations, event delivery
or an end-to-end clinical workflow.

## Frontend and events

Start the frontend separately with `npm run dev` in `frontend`. Without a local
environment file it uses real adapters, Sahha branding and `/api/v1`. Vite
forwards this prefix (including WebSockets) to Gateway on 8079. An explicit
Gateway origin can be set using `frontend/.env.example` as a reference.
Production must route `/api/v1` to Gateway or supply its approved HTTPS origin
at build time. The browser must never address internal services.

Startup preserves configured Kafka switches; it does not silently enable event
side effects. For live event workflows, start Kafka and explicitly set the
implemented switches before starting the relevant applications:

```powershell
$env:AUTH_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:ORGANISATION_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:PATIENT_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:SCHEDULING_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:CLINICAL_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:COMMUNICATION_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:FILE_OUTBOX_PUBLISHER_ENABLED = 'true'
$env:SCHEDULING_CLINICAL_CONSUMER_ENABLED = 'true'
$env:NOTIFICATION_APPOINTMENT_CONSUMER_ENABLED = 'true'
$env:NOTIFICATION_COMMUNICATION_CONSUMER_ENABLED = 'true'
$env:NOTIFICATION_REFERRAL_CONSUMER_ENABLED = 'true'
```

Referral notifications consume the separate COMMUNICATION_REFERRAL_TOPIC (default
sahha.communication.referrals.v1) with NOTIFICATION_REFERRAL_GROUP_ID (default
sahha-notification-referrals-v1). The new consumer validates routing-only lifecycle
events, deduplicates transactionally, ignores older versions, and uses the existing
private inbox/after-commit WebSocket channel. Central Audit ingestion is deferred.
Keep the synthetic clean-file shortcut disabled outside
controlled synthetic checks; production needs real malware-scanning decisions.

Use AUTH_ACCESS_COOKIE_NAME and AUTH_COOKIE_SECURE consistently across Auth,
Gateway and resource services. Notification now honours these canonical names;
its older AUTH_ACCESS_TOKEN_COOKIE_NAME and AUTH_SECURE_COOKIES are fallback-only
aliases. A custom cookie namespace must also reach internal session/membership
clients. The live synthetic namespace exposed this mismatch; the repaired package
passes 114 Notification/shared-session tests and both 51-check messaging runs.

## External configuration and Azure boundary

Communication's WebSocket endpoint now honours FRONTEND_ALLOWED_ORIGINS as an
exact HTTP(S) origin list, matching the browser deployment origin; wildcard,
credential-bearing and path origins are rejected. Its reserved /conversations/ws
handler takes priority over the conversation-ID REST route. The STOMP raw CSRF
interceptor is registered using Spring's required bean name, preserving the same
cookie-bound token contract as Notification. Validated origins do not bypass
session, role, membership, private subscription or participant authorisation.
The messenger refetches REST conversation/history data when its stream reconnects.
See [the browser acceptance commands](SYNTHETIC_BOOTSTRAP.md#real-browser-messaging-acceptance)
for isolated real-browser verification with automatic shutdown.

Gateway/domain applications opt in through `platform`. Native startup uses
`local,platform` for every Gateway/domain client; standalone `local` remains usable
without Config Server. With `platform`, the Config Server import is REQUIRED.
Tests disable Config Client to stay independent of running local configuration.

`CONFIG_SERVER_URL` selects the server; `CONFIG_SEARCH_LOCATIONS` selects its
native public-settings repository. Only reviewed operational defaults belong
there, never credentials, signing keys, database URLs, SMTP secrets or medical
data. Config Server binds to loopback by default. An Azure override needs a
private-connectivity/access design; do not expose Config Server through Gateway.
Import behaviour follows [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html).

Azure delivery uses frontend assets and executable JARs, not project-managed
Docker images. Never activate `local` there: configure stable signing keys,
secure cookies, HTTPS origins and private service/data/event/storage connections
through the chosen host's secure settings. No hosting SKU or resource has been
selected/provisioned by this task. Changing storage/event providers requires
compatibility testing, not just renaming environment variables.
