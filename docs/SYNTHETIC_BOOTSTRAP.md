# Isolated synthetic development bootstrap

Updated: 2026-09-24. Windows-native, Docker-free, development only.
Database, identity/staff and native dependency foundations are verified. This is
**not the complete internship demonstration**: all application smoke batches and
the patient/clinical seed checks pass. Primary protected-file recovery/repeat,
second-file fresh upload/repeat and messaging REST/Kafka recovery/repeat checks pass.
Selected-referral Clinical/File access, revocation and expiry pass 81 live checks.
Accepted shared-treatment history/private documents and termination pass 112
live Gateway assertions (2026-09-21). Referral notifications passed 103 real
React/Gateway/Kafka assertions across six types (2026-09-24), with nine observed
WebSocket deliveries and one explicitly classified interrupted-run REST recovery.
Real browser messaging passes 48 delivery and 45 restart-recovery assertions.
The ordered installed-machine retained-data demo passes all 16 stages. The full
referral/shared-care browser journey, fresh-checkout/empty-generation acceptance
and clean-machine acceptance remain separately tracked gates.

## Safety boundary

The tool uses only port `15432`, loopback `127.0.0.1`, and a new UUID directory
under `infrastructure/.state/synthetic-postgres`. It never reads or changes
`.env.database.local`, connects to shared PostgreSQL on `5432`, adopts an existing
database, registers a Windows service or stops a process by name.

Each generation contains nine `sahha_demo_<service>` databases with independently
generated restricted logins. Only the matching database/role pair authenticates;
the bootstrap operator has a separate credential. Passwords use SCRAM, sockets
and non-loopback access are disabled, SQL/error-detail logging is disabled, and
the instance uses 32 MB shared buffers and at most 40 connections. It is not a
production PostgreSQL configuration or a load-tested capacity claim.

Generation directories have current-user/SYSTEM-only Windows ACLs before any
secret is written. Manifests/settings/logs are private, ignored runtime state.
The tool never prints passwords or raw subprocess errors. Treat the entire
directory as sensitive; do not upload it or paste its files into reports.

Exact paths reject symlinks/junctions and unsafe state files. Operations lock
against concurrent init/start/stop/reset. Shutdown checks the exact executable,
PGDATA and PID/port record. SQL operations verify the actual PostgreSQL cluster
identity and service-specific operator marker, not just an open TCP port.

## Prerequisites and commands

Install PostgreSQL 18 binaries and Node 22. This slice is verified on Windows;
other operating systems are explicitly refused until process guards are ported.
The default binary location is `C:\Program Files\PostgreSQL\18\bin`. If needed,
set `SAHHA_PG_BIN` to the actual installed binary directory, not a data directory.
No administrator database password or change to the shared Windows service is
needed. The tool runs PostgreSQL as the current operating-system user.

From the repository root, with Sahha applications/frontend stopped:

```powershell
node scripts/synthetic-db.mjs init
node scripts/synthetic-db.mjs status
node scripts/synthetic-db.mjs verify
```

`init` creates a new cluster only when no active generation exists, provisions
nine databases and runs the real access matrix. It stops its server afterward.
`verify` performs 101 checks: cluster identity, nine owner/privilege decisions,
nine markers, 72 cross-database denials, nine denied marker writes and a rejected
incorrect operator password. If the instance was already deliberately running,
verification leaves it running; otherwise it stops what it started.

Migration uses Java 21 and the already packaged Auth JAR solely for pinned
Flyway/JDBC libraries. It does not launch Auth, share Auth's database or use its
development credentials:

```powershell
.\mvnw.cmd -pl auth-service -am package -DskipTests
node scripts/synthetic-db.mjs migrate
```

The non-secret runtime cache under `synthetic-postgres/migration-runtime` is
keyed by the packaged JAR's SHA-256 and reused across generations. A changed
dependency set cannot mix with stale extracted libraries from an older package.

Each service migrates with its own restricted login and its existing
`src/main/resources/db/migration` scripts. Cleaning, baselining and repair are
disabled. Repeating `migrate` validates the existing history; it does not reset
data. The offline Java source is operator tooling, not a production application
bean/endpoint. This command leaves an initially stopped database stopped.

For deliberate local use:

```powershell
node scripts/synthetic-db.mjs start
# Perform the explicitly configured synthetic work.
node scripts/synthetic-db.mjs stop
```

`status` prints the generation ID and private directory, never credentials.
Each service's `<service>.properties` contains only its own connection settings.
These are inputs for the isolated application/seed launcher below. The
existing `sahha.mjs start` still uses normal development settings and must not be
assumed to use this new database. Do not overwrite your development environment
file or globally export one service's datasource settings to every service.

## Recoverable reset

Stop the application stack/frontend and all owned synthetic dependencies, including
PostgreSQL, Redis, Kafka and SeaweedFS.
Copy the current generation ID from `status`:

```powershell
node scripts/synthetic-db.mjs reset --confirm CURRENT-GENERATION-ID
node scripts/synthetic-db.mjs migrate
```

Reset creates and verifies a **new** independent cluster before changing the
active pointer. The previous generation directory, data and credentials remain
in place; nothing is dropped, truncated or recursively deleted. The old pointer
stays active if creation fails. Wrong/stale confirmation, running applications
or an occupied port stop the operation. Partial failed generations are retained
for private inspection rather than recursively removed.

Old generations consume disk space. There is intentionally no automatic purge.
Recovery requires stopping the active generation and using the exact retained
PGDATA/binary and its private credentials; both use 15432, so never run two at
once. The native gate proves retained data can be reopened after reset. Reset
does not migrate automatically. Run migrations and `synthetic-demo.mjs seed` on
the new generation to establish new credentials and staff; old data is retained.

## Repeatable synthetic identities and staff

Package Discovery, Auth, Organisation and Gateway with their existing tests/build
before seeding. The seed needs Java 21 and their executable JARs:

```powershell
node scripts/synthetic-demo.mjs seed
```

It starts only four temporary applications plus the isolated database, verifies
actual routing readiness, performs Gateway-only API setup, logs out its sessions
and stops what it started. Apps use the `synthetic` profile, clean child settings,
generation-specific datasource files, distinct demo cookie names, loopback binding
and at most 256 MB Java heap each. Edited datasource settings are refused. SMTP,
Kafka publishing and normal local secret-file imports are not enabled.

The Auth-owned offline source is outside production sources/JARs. It can connect
only to the exact demo Auth database/login with the matching operator-owned
generation marker. It creates eight fixed reserved-domain identities with random
BCrypt-protected passwords and attributable metadata-only audit/outbox intents.
It refuses mismatched existing credentials or lifecycle state; there is no force
overwrite or password-reset option. Only one identity has `PLATFORM_ADMIN`.

Through real APIs, the platform identity creates Clinic A/B and assigns separate
administrators. Each admin creates a department and invites staff; recipients
explicitly accept. Clinic A has Doctor A, Doctor B, an unrelated doctor and a
receptionist. Clinic B has Doctor A as a receptionist and Doctor B as a doctor.
Doctor profiles are explicitly synthetic, not verified professional licences.
The patient identity has no staff memberships; it is not a Patient Service record.

Credentials are generated once in private `demo-accounts.json` in the active
generation. The command prints only its path, never credentials. Do not commit,
upload or paste this file. `demo-seed-report.json` contains non-secret IDs/counts.
Repeating setup preserves identities, credentials, organisations, memberships,
invitations, placements and profiles. It does not claim clinical/Kafka acceptance.

```powershell
$env:SAHHA_SYNTHETIC_DEMO_TEST = 'true'
node --test scripts/synthetic-demo.integration.test.mjs
```

Verified 2026-09-15: 57 first-run and 51 repeat real Gateway assertions; the full
288-second gate verifies exact row counts, in-memory preservation fingerprints,
logged-out sessions, five wrong database/owner/generation/password refusals and
edited datasource refusal before launch. All temporary ports stop. Auth/shared
session regression passes 201 tests. No development database is changed/reset.

## Temporary isolated Redis

Use the installed native Memurai executable; override `SAHHA_REDIS_EXE` if it is
not at `C:\Program Files\Memurai\memurai.exe`. No installer, service registration,
service startup-mode change or licence management is performed:

```powershell
node scripts/synthetic-infra.mjs verify redis
```

The generation receives its own private random credential/configuration. The
helper binds only `127.0.0.1:16379`, requires authentication, uses at most 64 MB,
and has snapshots/AOF disabled. Credentials are never command-line arguments.
The probe checks anonymous denial, authenticated write/read/TTL and removes only
its unique expiring test key. Shutdown requires the exact executable, private
unique per-launch config argument and PID; it performs `SHUTDOWN NOSAVE`. An occupied port or changed
config is refused, never adopted. Do not inspect/publish Memurai logs, which may
contain vendor licence information. Native verification passes on 2026-09-15;
the stricter repeat/listener/refusal gate is recorded below.

## Native Kafka and private file storage

Install/extract the approved native prerequisites before running these commands:
Java 21, Node 22, PostgreSQL 18 binaries, Memurai under its applicable licence,
Kafka `4.3.1` and SeaweedFS `4.41`. Use official distributions and verify their
published checksums/signatures. This tooling does not download software, accept
licences, register Windows services, edit existing broker settings, or provision
Azure. Kafka's directory must directly contain `libs`, not its parent archive
directory. Paths below are examples; select your actual installation paths:

```powershell
$env:SAHHA_KAFKA_HOME = 'C:\tools\kafka_2.13-4.3.1'
$env:SAHHA_SEAWEED_EXE = 'C:\tools\seaweedfs\4.41\weed.exe'
node scripts/synthetic-infra.mjs verify kafka
node scripts/synthetic-infra.mjs verify storage
```

Each command starts only its own temporary helper and stops it in `finally`.
All data/configuration is inside the private current synthetic generation.
Installed binaries and the existing workstation infrastructure remain untouched.
No `K:` drive mapping, shell wrapper, Docker or inherited broker environment is
required. The helper inspects the owning PID/executable/unique launch arguments
and **every actual listening socket** before the probe. Unexpected/non-loopback
listeners fail the check and trigger owned shutdown.

Kafka uses loopback `19092`/`19093`, 256 MB maximum heap and one broker/controller.
It formats only a new, empty directory, once; a matching existing cluster marker
is required on restart. Partial/unmarked/nonmatching data is retained and refused,
never reformatted. The probe verifies the actual broker cluster ID, creates nine
explicit one-partition/one-replica topics idempotently, and performs an acknowledged
producer/consumer roundtrip on the synthetic probe topic. No domain event is
fabricated. Auto-topic creation is disabled. Existing messages remain on stop.
This local-only plaintext broker is not an Azure security design. The existing
Windows file-lock workaround disables compaction; monitor local disk growth and
do not reuse that setting in production. Native process termination on Windows
is not host-managed graceful production shutdown; repeat recovery is tested.

SeaweedFS uses these loopback ports, separate from the normal development stack:

| Component | HTTP | gRPC |
| --- | --- | --- |
| Private S3 | 18333 | 28333 |
| Master | 19333 | 29333 |
| Volume | 18081 | 28081 |
| Filer | 18889 | 28889 |

Object and filer data have explicit generation-local paths/configuration. Random
S3 credentials are private files, never printed or passed as CLI arguments.
Embedded IAM, telemetry and the unused default Iceberg catalog are disabled.
The pinned File Service SDK verifies signed byte reads/writes, anonymous and
wrong-key rejection, deletion of only its unique probe object, and a retained
generation sentinel across restart. That small sentinel is intentionally kept.
The bucket is `sahha-synthetic-files`. The browser must still use File Service
through Gateway; it must never receive these storage endpoints or credentials.
Raw local filer/master endpoints are trusted-host development interfaces, not
public production APIs. Azure needs separate private networking/authentication.

The initial storage socket check caught the default Iceberg listener on 8181;
`-s3.port.iceberg=0` removes it. This is verified against the installed 4.41 help
and the upstream [SeaweedFS server flags](https://github.com/seaweedfs/seaweedfs/blob/master/weed/command/server.go).
Kafka formatting follows the official [native quick start](https://kafka.apache.org/43/getting-started/quickstart/);
storage uses the documented [S3 interface](https://github.com/seaweedfs/seaweedfs/wiki/Amazon-S3-API).

## Ordered setup from installed prerequisites

From a new checkout, install Java/Node/native binaries as above, restore frontend
dependencies with `npm ci` in `frontend`, then package the current backend JARs.
Packaging alone is not a test gate:

```powershell
.\mvnw.cmd package -DskipTests
$env:SAHHA_KAFKA_HOME = 'C:\tools\kafka_2.13-4.3.1'
$env:SAHHA_SEAWEED_EXE = 'C:\tools\seaweedfs\4.41\weed.exe'
node scripts/synthetic-setup.mjs check
node scripts/synthetic-setup.mjs run
```

Use your installed paths. `check` is read-only and starts no application/helper
services: it validates supported versions, installed Memurai/Chrome/Playwright,
frontend manifest/lock/dependency consistency, twelve packaged JARs, newer source
or configuration, and every required application/isolated-helper port. Shared
PostgreSQL 5432 is never probed or adopted. Missing tools, stale builds, injected
Java/Node runtime flags or occupied ports fail before workflow startup.

`run` repeats preflight and holds one exclusive synthetic-operation lock across
all 16 stages. Existing commands called inside it retain that lock; other CLIs
cannot reset/start/modify the generation between stages. The sequence is:

1. Retain the current database generation, or initialise a new one only if no
   active pointer exists; validate all service migrations and database isolation.
2. Verify Redis, Kafka and storage; seed/revalidate the eight identities and staff.
3. Revalidate scheduling, finalised Clinical history, both protected files and
   durable messaging through the real Gateway and service-owned APIs.
4. Verify selected Clinical/File sharing, expiry, revocation and a fresh Clinical
   denial, then run browser messaging live/recovery.

Each stage shuts down its own bounded helpers. The sequence checks free ports
before and after every stage, stops on the first failure, and compares retained
resource/command identities after every success. Newly created identities also
become protected by that comparison. It never resets a generation, installs
software, accepts a licence, deletes a committed record, extends an expired share,
automatically reconciles a browser command, or provisions cloud resources.
Repeating `run` re-executes every check; an old success report never skips work.
On retained data, revoked/expired referrals stay closed. Earlier positive-read
and live-message checkpoints remain evidence of their original run; the replay
checks retained denials/history, not a renewed grant or duplicate message. A
fresh empty-generation journey is a separate acceptance result.

Private `setup-runs/<run-id>.json` reports contain stage status, completed steps,
cleanup status, approved tool versions, identifier counts and a source/artifact
fingerprint. They contain no credentials, clinical content or raw failure bodies.
Success is recorded only after every stage and the final input-fingerprint check.
After a failure, inspect the last named stage and its private existing journal;
correct the cause, then rerun. For interrupted browser writes, use only the
separately documented explicit reconciliation rule, never delete its journal.
Do not terminate the command midway: machine/process crashes may bypass normal
finally cleanup and leave an ownership lock requiring manual PID verification.

Verified 2026-09-20: read-only preflight, 136 default tooling tests, the separate
native refusal/failure-cleanup test and the complete 16-stage retained replay pass.
The ordered run took 43 minutes, retained 58 identifiers and the active generation,
and passed its final source/artifact fingerprint and cleanup checks. Clinical/File/
restart referral repeats pass 24/15/24 assertions with the grant still revoked;
browser live/recovery replays pass 45/45 using the existing message/notification.
The first attempt exposed a Windows recycled-parent-PID shutdown defect; the
creation-time/ownership repair passes regressions and real cleanup. Its failed
report remains retained, separate from the later complete report.
This is installed-machine acceptance, not a clean Windows installation, fresh
empty-generation journey, complete Phase 6/7 workflow or CI
certification. Do not substitute `sahha.mjs start all`: it uses normal development
settings and intentionally leaves applications running.

Tooling and opt-in native orchestration safety checks:

```powershell
node scripts/sahha.mjs test tools
$env:SAHHA_SYNTHETIC_SETUP_TEST = 'true'
node --test scripts/synthetic-setup.integration.test.mjs
```

The opt-in test retains a deliberately occupied listener, refuses a competing
synthetic CLI, injects failure inside owned Redis, verifies its shutdown/restart
and checks unchanged generation metadata. It never rotates the active generation.

## Application batch verification

After migrations, dependency checks and identity/staff seeding, verify each
temporary application batch separately:

```powershell
node scripts/synthetic-platform.mjs smoke foundation
node scripts/synthetic-platform.mjs smoke clinical
node scripts/synthetic-platform.mjs smoke collaboration
node scripts/synthetic-platform.mjs smoke audit
```

Each batch includes Discovery, Config Server, Auth, Organisation and Gateway.
Foundation adds Patient/Scheduling; Clinical adds Clinical/File; collaboration
adds Communication/Notification; Audit adds Audit. No batch runs more than seven
application JVMs. The services use `synthetic` (Config uses `native`), import only
their verified generation datasource and explicit public policy/Config resources,
and never load `.env.database.local`. Every client must consume `native-v1` Config
settings. Actual listening sockets are checked against the exact process and
loopback port. Native Redis is authenticated; only Auth/Communication receive its
credential. Only File receives S3 credentials, and only Patient receives the
generation's HMAC key. No SMTP is enabled.

The collaboration batch explicitly enables existing outbox/consumer switches and
starts the isolated broker; other smoke batches leave event delivery off. The
synthetic clean-file shortcut stays disabled in smoke checks. Each batch verifies
health/Actuator isolation and live Gateway role boundaries, logs out its clients,
stops apps in reverse order, then stops storage/Kafka/Redis and the database it
started. Reports contain non-secret counts under the private generation.

These are automated smoke checks, not a long-running development server or proof
of the clinical/referral/notification journey. Full workflow verification remains
a separate gate. All four batches passed on 2026-09-16/17: Foundation, Clinical and
collaboration each passed 63 operational assertions; Audit passed 54. This is 243
assertions across 27 service starts covering all twelve applications, plus Config
markers and Gateway checks. Kafka-enabled startup exposed missing Boot Kafka
starters in Auth/Organisation/Patient, which are now corrected; three targeted
application contexts pass eight assertions including producer auto-wiring.

The stronger opt-in infrastructure gate exercises all three helpers twice,
checks real protocols and exact loopback sockets, proves stable identities and
S3 sentinel recovery, refuses occupied ports/changed configs without adoption,
and verifies automatic cleanup after an injected callback failure:

```powershell
$env:SAHHA_SYNTHETIC_INFRA_TEST = 'true'
node --test scripts/synthetic-infra.integration.test.mjs
```

Run native gates serially. They share the generation's operation lock and retain
existing data. Check the living plan for the most recent completed gate result.

Verified 2026-09-16: the infrastructure gate passed in 147.7 seconds, including
both storage restart preservation and all listener/denial/cleanup assertions.
All eleven helper ports were released. Default tooling passes 53 tests. Only
the probe's own temporary Redis keys/S3 objects are removed; persistent synthetic
objects, Kafka records, identities and prior generations remain recoverable.

If an interrupted command leaves `operation.lock`, inspect its recorded PID
before removing only that stale lock. Do not delete the state root or an entire
generation as a way to unlock it.

## Real browser messaging acceptance

With the identity seed, packaged collaboration services and native Kafka path
already prepared, run these commands serially from the repository root:

```powershell
node scripts/synthetic-browser.mjs live
node scripts/synthetic-browser.mjs recover
```

This Windows-only runner uses frontend-installed Playwright and Chrome at
`C:\Program Files\Google\Chrome\Application\chrome.exe`. Port 5173 must be free;
the runner refuses to adopt a running frontend. It starts the seven-application
collaboration batch with isolated PostgreSQL, authenticated Redis and Kafka.
Its private Vite configuration ignores developer env files, disables mocks and
proxies HTTP/WebSockets only through Gateway. External design fonts stay blocked;
other off-origin traffic fails the gate. This is not a font/visual-baseline test.

Three separate real browser sessions use the two participant doctors and an
unrelated doctor, with desktop/mobile viewports. A separately journalled synthetic
conversation preserves older demo data. Live acceptance requires actual recipient
message and Kafka-notification WebSocket frames, persisted REST recovery, a real
socket-only reconnect, page reload, unrelated-user denials and organisation-switch
cleanup. No HTTP response or WebSocket message is mocked. Closing a native socket
for recovery testing does not take HTTP offline or inject protocol frames.

The private `workflow-browser-messages.json` records exact command/resource IDs
and observed delivery checkpoints, not tokens, credentials or clinical content.
Recovery refuses to run without completed live evidence. Repeats never replace a
committed message to manufacture another delivery result; an ambiguous write
without recorded positive evidence fails closed for review. Reports are written
only after browser logout and shutdown of Chromium, frontend and owned services
and helpers. Shared PostgreSQL on 5432 is never adopted or stopped.

If an interrupted browser send has only a pending command ID, an operator can
explicitly run `node scripts/synthetic-browser.mjs live --reconcile-empty`.
It starts fresh services, requires authorised history to be empty and refuses
any recorded message or positive delivery evidence. The previous command ID is
retained in reconciliation history before a new real UI command is allowed.
It does not erase, resend or replace a committed message. Normal live runs never
silently change a pending command. The observer allows the production HTTP
client's single CSRF-refresh retry and records only numeric failure statuses.

Verified 2026-09-19: 48 real delivery assertions and 45 fresh-process recovery
assertions pass, retaining the same message/notification with no duplicate. The
first interrupted send was proven uncommitted before explicit reconciliation;
its command ID is retained. All 119 tooling, 113 Communication/shared-session and
226 frontend tests pass, as do package/production builds. Final audit: zero project
listeners/owned synthetic runtimes; shared PostgreSQL retained. This does not close
the complete referral/shared-treatment browser journey or clean-machine acceptance.
The separate ordered retained-data setup gate is verified above.

## Verification

The patient/appointment workflow seed runs after identity/staff seeding and
packaging the foundation services. Its first and fresh-process repeat gates passed
on 2026-09-17 (40/32 Gateway assertions):

```powershell
node scripts/synthetic-workflow.mjs seed scheduling
```

It uses seven temporary applications plus authenticated Redis, isolated Kafka
and PostgreSQL. Through Gateway, it performs duplicate checks, registers one
synthetic patient, links the verified synthetic patient account, publishes two
doctors' availability and books/confirms/checks in/starts one appointment. Existing
matching rows are reused, edited/ambiguous data is refused, and the exact booking
command is journalled privately before submission. Repeating the command must
reuse the appointment, including after a lost committed response. It also checks
patient appointment visibility and unrelated-doctor, wrong-organisation and role
denials. This does not seed finalised clinical content or test notification delivery.
No real patient data, mail, Docker or cloud resources are involved.

The clinical gate passed 31 real Gateway/Kafka assertions on 2026-09-17, recovering
the record created during earlier interrupted validation attempts. Its next-day
fresh-process repeat passed the same 31 checks, retaining that record/correction:

```powershell
node scripts/synthetic-workflow.mjs seed clinical
```

It replaces Patient with Clinical in the seven-app foundation batch, reuses the
verified appointment, and records only clearly labelled synthetic clinical data.
It checks finalisation, immutable-write denial, one append-only correction,
role/resource isolation, the doctor's active-care patient summary and actual Kafka-driven
appointment completion. It does not mark success merely because an event was
queued. Repeats read the same signed record/correction instead of adding another.
After completion, patient-wide summary access is denied because the active
appointment care relationship has ended; the author can still read their own
signed record. No permanent patient-wide access is inferred from past treatment.
The later collaboration command can now explicitly reference that author's own
finalised consultation. Clinical rechecks its original live Doctor membership;
this is not continuing care, a new appointment or implicit recipient access.

The protected-file recovery and fresh-process repeat passed on 2026-09-18 (22/20
Gateway/storage checks), retaining the original PDF. The initial attempt committed
the upload before a strict binary-cookie assertion failed. Only the configured
CSRF cookie is now permitted; authentication/session cookies remain rejected:

```powershell
node scripts/synthetic-workflow.mjs seed files
```

This uses the seven-app Clinical/File batch plus isolated storage, Redis, Kafka
and PostgreSQL. It uploads one deterministic synthetic PDF through Gateway,
checks CSRF and quarantine denials, explicitly applies a development-only
synthetic scan decision, and tests author-only short-lived one-time download,
byte preservation and role/stolen-grant/replay denials. Upload tickets stay in
the private journal until committed metadata is read; a missing/expired ticket
fails closed without replacing the file. **This does not verify malware scanning.**

For the later exact-sharing denial journey, a separate fixture is implemented:

```powershell
node scripts/synthetic-workflow.mjs seed files-unselected
```

It uses a different synthetic filename, private journal and report, preserving the
primary document. Only these two known fixtures are accepted; duplicate names or
changed metadata fail closed. It does not create any sharing grant. Its fresh
upload passed 24 Gateway/storage checks on 2026-09-18, including eight allowlisted
CSRF-cookie updates without exposing values. The primary preservation repeat
passed another 20 checks after this upload; the second-file repeat also passed 20.
The historical first-upload cookie name remains unknown, but a genuinely fresh
upload now passes the strict transport checks. Default tooling passes 101 tests.

The following seven-app collaboration gate passed recovery and fresh-process
repeat verification on 2026-09-18 (51 Gateway/Kafka checks each):

```powershell
node scripts/synthetic-workflow.mjs seed messages
```

It creates one explicitly synthetic doctor-to-doctor conversation/message using
privately journalled recovery commands. It requires the real Kafka-delivered
recipient notification, metadata-only inbox fields, participant/role/tenant
denials and idempotent read markers. Repeats must retain all three resource IDs.
No patient is attached to the conversation, no patient access is granted, and
WebSocket delivery/patient-context referral checks remain separate acceptance
gates. No frontend mock or direct domain-database write supplies these results.
All helpers stop after each command, including failure paths. Run commands serially.

Live messaging exposed Notification's access-cookie setting mismatch. Its shipped
configuration now prefers the canonical AUTH_ACCESS_COOKIE_NAME and
AUTH_COOKIE_SECURE shared with Auth and other APIs, with legacy Notification names
as fallback aliases. The packaged repair passes 114 Notification/shared-session
tests, and a tooling regression guards the shared property contract across nine
browser-facing APIs. Messaging requires exact patient-without-context 401,
administrative-role 403 and unrelated-doctor 404 denials, not an interchangeable
list of failure statuses. Thirteen focused frontend messaging/notification tests
also pass; these are not the pending live browser/WebSocket acceptance.

Patient-linked messaging and selected-sharing acceptance now passes through real
Gateway APIs (2026-09-18/19), with no SQL seeding or replacement resources:

```powershell
# Apply the additive Communication V3 after packaging the updated service.
node scripts/synthetic-db.mjs migrate
node scripts/synthetic-referrals.mjs seed clinical
node scripts/synthetic-referrals.mjs seed files
node scripts/synthetic-referrals.mjs seed clinical
```

Run serially with the native Kafka/SeaweedFS paths configured as above. Each batch
uses seven applications and stops its owned processes/helpers, even on failure.
The first Clinical batch passed 28 assertions: a source-authorised patient mention
without access, pre-acceptance denial, selected diagnosis content, an independent
short medication expiry, role/resource denials and unchanged originals. The File
batch passed 29 assertions: exact selected bytes, unselected/wrong-role/tenant
denials, single-use grants and revocation including an outstanding download token.
The final fresh Clinical batch passed 24 assertions against the same revoked grant.

The private workflow-referrals.json journal preserves request IDs, selected
resources and evidence checkpoints. Repeats never extend expired grants, recreate
the signed record or replace a revoked referral; they check retained denials.
An interrupted short-lived command without positive-read evidence fails closed
for manual review rather than silently creating replacement evidence. The source
reference remains internal and immutable. Clinical content still requires an
explicit, accepted, consent-aware, current resource grant.

Communication/Clinical/shared-session regressions pass 186 tests, tooling 108,
and all 38 service migrations revalidate with 101 database isolation assertions.
This is API/storage acceptance, not live browser/WebSocket or malware-scanner
verification. Shared-treatment participation remains separate Phase 6 work.
All 225 frontend tests, the production build and 34 intercepted desktop/mobile
browser checks pass; those browser checks are not a live-Gateway delivery result.
The final project listener audit is empty; shared PostgreSQL 5432 is retained.


## Message attachments only (2026-09-26)

This focused gate does not start full-application acceptance or Kafka. It uses
the retained synthetic accounts/organisations, the existing real React messenger,
Gateway, Communication, File and private SeaweedFS storage. Package Communication
and File with their shared dependencies, then apply retained migrations before
running it:

```powershell
$env:SAHHA_SEAWEED_EXE = 'C:\Users\LENOVO\seaweedfs\4.41\weed.exe'
node scripts/synthetic-db.mjs migrate
node --test scripts/synthetic-message-attachments.test.mjs
node scripts/synthetic-message-attachments.mjs verify
```

The gate creates/reuses one participant-only conversation and one synthetic PDF.
It checks browser upload, pending-scan send gating, unsent-file denial, immutable
send retry, recipient reload/download and byte equality, mobile fit, role/org/
unrelated-user denial, actor-bound tokens and replay denial. API responses are
not mocked. A private generation journal retains exact commands before writes;
interrupted-run recovery reuses the retained IDs, validates every synthetic
payload field and preserves positive browser evidence. Tokens are never journalled.

Clean status is supplied only through the explicitly enabled synthetic scan
hook; this is **not malware-scanner validation** and that hook must remain off
in production. Existing clinical files cannot be attached through this flow.
Production scan integration and abandoned-upload cleanup remain deployment work.

Verified: 29 native assertions, four runner contract tests, 26 focused frontend
tests/build and 239 affected backend tests/packages. One optional legacy storage
test was skipped; this gate independently verifies real private-storage bytes.
All 45 retained migrations applied, zero pending; 101 migration-prerequisite
database-isolation checks passed. Browser, frontend, apps, Redis, storage and
isolated PostgreSQL stopped afterward; shared PostgreSQL 5432 is retained.

## Patient appointment notifications

Package Notification and Gateway, then migrate the retained isolated generation:

    .\mvnw.cmd -pl notification-service,api-gateway -am package
    node scripts/synthetic-db.mjs migrate
    $env:SAHHA_KAFKA_HOME = 'C:\Users\LENOVO\kafka\kafka_2.13-4.3.1\kafka_2.13-4.3.1'
    node scripts/synthetic-patient-notifications.mjs verify

This gate requires the existing synthetic patient-account link and scheduling
seed. It starts the seven-service foundation batch plus Notification, isolated
PostgreSQL/Redis/Kafka, Vite and headless Chromium. It creates two synthetic
appointments via Gateway, exercises request/confirm/reschedule/cancel/reject,
checks live delivery, offline cancellation recovery, read state, account/resource
denials, CSRF and desktop/mobile layouts. No API responses or frames are mocked.
All owned apps/helpers stop in finally blocks; shared PostgreSQL stays untouched.

The private workflow-patient-notifications.json stores exact booking/transition
commands before POSTs and records LIVE versus RECOVERED evidence. Repeats retain
appointments and notification IDs; they never reset data or invent live evidence.
Interrupted commands remain recoverable with their original idempotency keys.
Known progressed appointments are recovered with GET by their retained ID rather
than replaying the initial booking: Scheduling currently compares the booking
time against the mutable rescheduled time. That original-booking retry edge is
tracked for full-app acceptance; the runner never rewrites the saved command.
The report is private to the retained generation. There is no reset/reconcile flag.

Verified 2026-09-26: 49 assertions, six durable alerts across five lifecycle
types, five journalled live deliveries and one offline REST recovery. Repeats
reuse the original live evidence, not fabricated new frames. A soft-shell stacking
bug found by the gate is protected by four standalone real-Chromium CSS cases:

    npm.cmd --prefix frontend run test:notification-layer

The layout command uses installed Chrome (or SAHHA_CHROME_PATH), synthetic markup
and offline font CSS. It is a pointer/hit-testing regression, not backend evidence.
The native gate above uses actual React and unmocked Gateway/Kafka delivery.

Patient REST uses /api/v1/notifications/patient/registrations/{registrationId};
patient WebSocket uses /api/v1/notifications/patient/ws?registrationId=... through
Gateway. Notification verifies the current own-active-registration context through
Patient on every REST operation and incoming/outgoing live frame. Staff org/user
inboxes and patient/org inboxes have separate persistence/principals.
Session revalidation, cookie CSRF and private subscription restrictions remain
mandatory. Appointment alerts grant no clinical access. Historical appointments
remain in Scheduling; previously consumed events are not retroactively re-notified.

## Referral lifecycle notifications

After the retained shared-care gate, package Notification Service and migrate the
isolated generation. With all project apps/frontend stopped, run:

    $env:SAHHA_KAFKA_HOME = 'C:\Users\LENOVO\kafka\kafka_2.13-4.3.1\kafka_2.13-4.3.1'
    node scripts/synthetic-db.mjs migrate
    node scripts/synthetic-referral-notifications.mjs verify

The gate starts eight owned applications (the collaboration batch plus Clinical),
isolated PostgreSQL/Redis/Kafka, Vite and real Chromium. It creates four synthetic
consent-labelled referrals and checks six lifecycle notification types over the
real Gateway/outbox/Kafka/Notification path. Three independent browser sessions
cover both doctors and an unrelated doctor, including 1440/375-pixel layouts.
No HTTP responses or WebSocket frames are mocked. File/storage are not started.

The private workflow-referral-notifications.json journal pins immutable commands,
expiry and retained source IDs, and records observed notification IDs. Repeats use
the same referrals and verify stable durable delivery evidence; they do not
manufacture fresh WebSocket evidence for already-finished commands. An interrupted
command recovered from the REST inbox is explicitly classified separately from
live delivery. Completion still requires observed WebSocket evidence covering all
six lifecycle types; a REST-only run cannot pass that gate.
There is no reset, expiry extension or journal-reconciliation flag.

Malformed events are recorded by hash/reason/source position only. Valid event
consumption, the referral-version cursor and inbox inserts share one transaction;
duplicate IDs/positions and older/equal versions cannot create additional alerts.
WebSocket delivery is after commit; REST inbox/read state is the recovery path.
Notifications contain no patient, consent, reason, selected-file or clinical data.
Opening a notification grants no referral or record authority.

The runner verifies ten private deliveries, lifecycle UI labels, foreign-user and
organisation inbox/read denial, read-state persistence and browser reload recovery.
It logs out browser/API sessions and closes Chromium, Vite, apps and owned native
helpers in finally. The report contains counts/verification metadata only. Actual
run results are recorded in the living plan; this command is not a completion claim.
It does not complete the new treatment appointment/consultation acceptance journey
or the full application release gate.

Verified 2026-09-24: 103 assertions passed, covering all six lifecycle types, nine
observed private WebSocket deliveries and one REST-recovered alert from the first
interrupted run. All ten durable alerts were unique; read state survived reload.
User/organisation and foreign-read isolation, UI labels and desktop/mobile fit
passed. Retry logs contained no referral rejections or broker storage failures.
All owned runtimes stopped; twelve apps DOWN, zero project listeners/owned
processes, shared PostgreSQL retained. All 42 migrations and 101 database-isolation
checks passed; tooling passed 147 tests with one optional infrastructure skip.

Retained native Kafka uses a separate server-retained.properties file with
log.retention.ms=-1. This preserves the original configuration/data and avoids the
Windows mapped-index retention deletion that shut down the first notification run.
This is for small synthetic demonstrations only: disk retention is now operator-
managed, not automatically bounded by age. No topic/log data is reset or removed.
Azure/production requires a separately chosen bounded retention policy. See
[Kafka retention configuration](https://kafka.apache.org/41/generated/topic_config.html)
and the matching [Windows file-lock report](https://issues.apache.org/jira/browse/KAFKA-19438).

## Accepted shared-treatment history and documents

After the retained clinical/two-file setup and fresh Communication, Clinical and
File packaging, run serially with all project apps/frontend stopped:

    $env:SAHHA_SEAWEED_EXE = 'C:\Users\LENOVO\seaweedfs\4.41\weed.exe'
    node scripts/synthetic-db.mjs migrate
    node scripts/synthetic-shared-care.mjs verify

The runner creates four explicitly synthetic, consent-labelled treatment referrals
through Gateway only. It never resets records/files, extends recorded expiry, or
replaces a completed/revoked command. It checks pending denial, both participants'
history/corrections and two private documents, roles/tenants/patients, author-write
denial, an outstanding-token revocation, independent care after one completion,
and expiry. Access denial is immediate from timestamps; the persisted EXPIRED
state is checked separately after its scheduled transition.

Only eight required applications plus isolated PostgreSQL, Redis and storage are
started, with ownership-checked finally cleanup. Kafka and Notification are not
needed for this synchronous gate. Existing outbox/audit writes stay enabled;
delivery/notifications and each doctor's new appointment/consultation workflow
remain separately tracked acceptance work.

The private workflow-shared-care.json journal preserves command IDs and evidence.
An outstanding download token may exist briefly in this ACL-restricted recovery
journal until its denial is verified; never print or commit it. The separate
workflow-shared-care-report.json contains only verification metadata/counts, not
credentials, tokens or clinical content. Interrupted positive expiry evidence
fails closed rather than manufacturing a replacement. See the living plan for
actual run results; this command is not itself a completion claim.

Verified 2026-09-21: the native run passed all 112 Gateway assertions. The retained
generation applied all 41 service-owned migrations with zero pending and passed
101 database-isolation checks. All owned applications/helpers then stopped;
project listener and owned-runtime counts were zero, shared PostgreSQL untouched.
This is synchronous API/private-storage acceptance with synthetic scan status;
it does not verify Kafka notifications, live referral browser interactions or a
new treatment appointment/consultation. The complete tooling suite passed 142
tests with one optional infrastructure test skipped.

```powershell
node scripts/sahha.mjs test tools
$env:SAHHA_SYNTHETIC_DB_TEST = 'true'
node --test scripts/synthetic-db.integration.test.mjs
```

The opt-in native test creates/uses only this synthetic instance, applies and
revalidates all nine migrations, checks occupied-port/reset guards, creates a
synthetic sentinel, rotates generations, proves the new data is clean and the
old sentinel survives, migrates the active generation, then stops helpers.
It retains both generations. Never run it concurrently with synthetic app work.
The default test invocation skips this infrastructure-mutating gate.

For a refusal-only test that does **not** rotate the active generation:

```powershell
$env:SAHHA_SYNTHETIC_DB_RESET_REFUSAL_TEST = 'true'
node --test scripts/synthetic-db.integration.test.mjs
```

Leave `SAHHA_SYNTHETIC_DB_TEST` unset for this test. It occupies only the isolated
Redis port temporarily, verifies reset is refused and the active generation is
unchanged, then closes that listener. Init/reset refuse all eleven isolated
dependency ports in addition to application/frontend/database listeners.

See the [living plan](INTERNSHIP_PLAN.md) for actual validation evidence. A passing
database gate does not establish seeded login/roles, Kafka/file/notification
workflows, clean-machine installation or cloud readiness.

Verified 2026-09-15: all 34 tooling tests pass; the opt-in native gate passes
45 lifecycle assertions in 157 seconds, repeatedly running the 101-check access
matrix and all nine real migrations. The old sentinel was recovered and the
fresh generation has no old sentinel. All temporary database processes stop.
Three generated directories remain from validation (active, retained old and an
initial partial generation), approximately 705 MiB including extracted migration
libraries and the fingerprinted cache. Its final native rerun validates all
37 migrations across nine services with zero pending. Existing development/shared
databases were not changed.

Native command behavior was checked against installed PostgreSQL 18.1 help and
the official [initdb](https://www.postgresql.org/docs/18/app-initdb.html),
[pg_ctl](https://www.postgresql.org/docs/18/app-pg-ctl.html) and
[host authentication](https://www.postgresql.org/docs/18/auth-pg-hba-conf.html)
documentation.
