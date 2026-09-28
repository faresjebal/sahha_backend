# Health and readiness policy

Last updated: 2026-09-13

This Phase 1 slice standardises local operational probes, not end-to-end journey
availability or deployment provisioning. Every application ships and imports its
own `health-policy.properties`. Local defaults work without Config Server, and
tests load that same shipped policy rather than a divergent test-only copy.

## Probe contract

- GET `/actuator/health/liveness`: application liveness only. `BROKEN` maps to
  HTTP 503/DOWN. A database, broker or remote-service outage is not a restart cue.
- GET `/actuator/health/readiness`: application acceptance state plus the required
  local dependencies below. Refusing traffic maps to 503/OUT_OF_SERVICE; a failed
  required dependency maps to 503/DOWN. Recovery restores HTTP 200/UP.
- GET `/actuator/health`: redacted aggregate diagnostic health. An optional
  contributor may make this DOWN while the application remains locally ready.
  It is not the routing/startup gate and is not a complete dependency inventory.
- GET `/actuator/info`: reviewed operational metadata only, such as Config's
  `native-v1` marker. No credentials or clinical data belong here.

No components, connection strings, exception details or stack traces are exposed
by public probes, including on failure. Component/group-child paths and every
other Actuator URL/method are denied. Dedicated Actuator security chains bypass
browser JWT/session authentication only for these operational routes; stale
cookies or bearer headers cannot trigger an Auth call or create a session. Normal
business API authentication, permissions, CSRF and resource checks are unchanged.
Audit retains its closed business surface. Discovery/Config apply an
Actuator-specific filter without changing their existing infrastructure protocols.

## Required dependencies and partial operation

| Application | Readiness requirement beyond accepting traffic | Not part of the local readiness gate |
| --- | --- | --- |
| Discovery | None; serves its local registry | Peer/downstream application availability |
| Config | Config repository health (`configServer`) | Eureka/client availability |
| Gateway | None; edge can receive requests | Auth, Eureka and individual routed services |
| Auth | Owned PostgreSQL (`db`) | Redis fallback; SMTP/onboarding; Organisation context lookup; Kafka publication |
| Organisation | Owned PostgreSQL | Auth/other synchronous clients; Kafka publication |
| Patient | Owned PostgreSQL | Auth/Organisation decisions; Kafka publication |
| Scheduling | Owned PostgreSQL | Auth/Organisation/Patient decisions; Kafka delivery |
| Clinical | Owned PostgreSQL | Auth/Organisation/Scheduling/Communication decisions; Kafka delivery |
| Communication | Owned PostgreSQL | Auth/Organisation/Scheduling decisions; optional Redis selection cache; Kafka delivery |
| Notification | Owned PostgreSQL | Auth/Organisation decisions; Kafka consumption and delivery freshness |
| File | Owned PostgreSQL | Auth/Clinical/Communication decisions; S3 byte operations; Kafka publication |
| Audit | Owned PostgreSQL | No central ingestion/query API exists yet |

“Not part of readiness” does **not** mean “safe to ignore.” Protected requests
still fail closed when Auth or a resource authority is unavailable. An unavailable
S3 store prevents byte operations, but does not justify withdrawing all metadata
capacity. Missing SMTP affects email workflows. Kafka outages delay projections
and notifications; persistent outboxes/replay support eventual recovery, not a
claim that notifications are current. Redis remains disposable where PostgreSQL
fallback is implemented. The probes do not send email, publish Kafka events,
create buckets, inspect patient data or fan out to clinical services.

This local-capacity policy avoids making a shared dependency outage withdraw every
application at once. Workflow smoke checks, per-route error rates, outbox backlog,
consumer lag, storage/mail availability and alerting remain separate operational
gates; production hardening/observability is not complete with this slice.

Config Client import is still mandatory at startup for the `platform` profile;
readiness does not relax missing-config startup failure. Runtime Config/Eureka
availability is not recursively probed. Missing required health contributor names
fail startup validation instead of silently dropping the dependency check.

Hikari pool acquisition is capped at three seconds and pool validation at one
second for stateful services. These are not a total network black-hole deadline
for every JDBC statement: driver/network timeouts and production pool sizing still
need deployment-specific testing. The native runner has its own bounded HTTP
probe timeout and fails closed on malformed, non-200 or unreachable probes.

## Native workflow and verification

The native runner now requires **liveness AND readiness** to be UP; optional
aggregate-health failures no longer block startup. It still reports Eureka/Config
metadata separately. No Docker, Azure provisioning, reset command or broad
process shutdown is introduced.

```powershell
node --test scripts/sahha.test.mjs
.\mvnw.cmd verify "-Dsahha.test.redis.port=16379"
node scripts/sahha.mjs start all
node scripts/sahha.mjs status
node scripts/health-smoke.mjs all
node scripts/sahha.mjs stop all
```

The full backend suite also needs the isolated Auth Redis test helper and
`AUTH_TEST_REDIS_PORT=16379`; see the native guide. Never stop shared PostgreSQL
to test failure. Policy tests replace only an in-context health contributor,
exercise 503/redaction/liveness isolation and restore it afterward. Availability
tests likewise restore accepting/correct state. Existing owned database tests
retain their schema/database isolation. Gateway tests exercise real random-port
HTTP; a native packaged-JAR smoke check verifies real deployed policy loading.
The smoke command checks all twelve applications (or supplied service names),
prints only safe status summaries and never issues business/API data requests.

Validation (2026-09-13): all 154 new policy tests pass; full backend `verify`
passes with 710 tests passed, no failures/errors and one opt-in live-storage test
skipped. All twelve JARs package successfully. Fourteen tooling tests pass.
Ninety native smoke assertions passed across ten applications, in small batches
with background processing disabled. The final packaged Gateway/Audit rechecks
were not started after the user requested immediate shutdown for resource use.

Follow-up (2026-09-14): the user's pre-cloud completion request reopened the
pending verification. Audit and Gateway were run one at a time with only
Discovery/Config. Both passed all nine packaged assertions (108 total across
all twelve applications), then all owned processes were stopped in finally
cleanup. Status confirmed all twelve DOWN/unmanaged. Shared PostgreSQL remains
for its other databases. The native health gate is complete; this does not close
the remaining functional, hardening, CI or cloud-deployment gates.
Simulated contributor failures verify policy transitions, not physical
broker/storage/driver failure recovery or the complete Phase 6 workflow.
