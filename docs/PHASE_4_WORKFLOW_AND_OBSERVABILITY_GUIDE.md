# Sahha Phase 4 workflow, performance, and observability guide

Last updated: 2026-08-24

Status: the live Phase 4 role workflow and event pipeline are verified; response
and PostgreSQL measurement tables have not yet been completed.

Use only synthetic internship data. Never paste cookies, JWTs, refresh tokens,
passwords, SMTP credentials, complete Redis values, or patient payloads into a
report, screenshot, issue, or Git commit.

## 1. What this guide proves

This guide teaches how to test the implemented Phase 4 journey and how to
observe each layer without confusing one measurement with another:

```text
React action
  -> API Gateway response time
  -> owning service and PostgreSQL query time
  -> transactional outbox row
  -> Kafka appointment record
  -> Notification consumer/inbox row
  -> authenticated WebSocket message
  -> REST inbox recovery
```

A successful HTTP response proves only the synchronous command. It does not by
itself prove that Kafka published, Notification consumed, WebSocket delivered,
or Redis cached anything. Each boundary is checked separately below.

## 2. The frontend main-chunk warning

### What was measured

The verified production build currently reports:

| Asset | Minified size | Gzip transfer estimate |
| --- | ---: | ---: |
| Main application chunk | 503.73 kB | 120.18 kB |
| React vendor chunk | 252.21 kB | 81.66 kB |
| Motion chunk | 113.43 kB | 44.64 kB |
| Icons chunk | 28.70 kB | 10.10 kB |

Vite warns because the main minified file is just over its 500 kB warning
threshold. This is a warning, not a failed build and not proof that 504 kB is
transferred over the network. Compression reduces that particular file to
about 120 kB, but the browser must still download, decompress, parse, compile,
and execute the JavaScript needed by the first route.

### Why Sahha currently produces one large main chunk

- `frontend/src/App.tsx` is approximately 207 kB and 786 lines.
- Login/registration pages are lazy-loaded, but most organisation, patient,
  scheduling, staff, and broad prototype workflow pages are static imports.
- The Vite chunk policy separates React, GSAP, and Lucide dependencies, but it
  does not split application code by route or role.
- A patient may therefore download code for hospital/platform/prototype screens
  they never open. A change to one of those screens can also invalidate the
  shared main chunk in the browser cache.

The correct future fix is route/feature lazy loading and incremental extraction
of `App.tsx`. Merely increasing `chunkSizeWarningLimit`, minifying harder, or
moving the same code into a manually named chunk hides or relocates the signal;
it does not prove a faster first visit.

### How to see the impact yourself

Build and serve production output; do not use Vite development mode for bundle
performance conclusions:

```powershell
Set-Location C:\Users\LENOVO\Desktop\sahha\frontend
npm.cmd run build
npm.cmd run preview -- --host 127.0.0.1 --port 4173
```

In Chrome DevTools:

1. Open **Network**, enable **Disable cache**, select a realistic network
   throttle such as Fast 4G, and reload the page.
2. Filter by **JS** and inspect transferred size, resource size, duration, and
   the waterfall. Record the initial route separately for patient, doctor,
   receptionist, organisation administrator, and platform administrator.
3. Repeat with cache enabled. A second navigation should reuse unchanged
   chunks.
4. Open **More tools -> Coverage**, reload, and inspect unused bytes in the
   main chunk. High unused code on a role's first page is evidence for route
   splitting.
5. Run a Lighthouse mobile lab audit and record Total Blocking Time. Also test
   actual clicks in the Performance panel; bundle size alone does not prove
   poor interaction latency.

Do not optimise until the initial-route waterfall and Coverage results are
saved. The first candidate budget for Sahha is a main application entry chunk
below 350 kB minified, with role pages loaded on demand. This is an internal
engineering target, not an industry standard; the real exit condition is a
measurable improvement in transferred bytes and browser work without breaking
route transitions.

## 3. Response-time guidance and Sahha budgets

There is no universal healthcare-platform rule saying every API must respond
within one exact number. Security checks, password hashing, file transfer,
external email, dataset size, network distance, and clinical safety all change
the expected latency. Compliance requirements must not be traded for speed.

The external user-experience references used here are:

- [web.dev TTFB guidance](https://web.dev/articles/optimize-ttfb): a rough
  guideline is TTFB at or below 800 ms; it is not itself a Core Web Vital.
- [web.dev INP guidance](https://web.dev/articles/optimize-inp): good
  interaction responsiveness is at or below 200 ms at the 75th percentile.

Those browser thresholds are not backend SLAs. Until realistic production load
tests exist, use these Sahha local-development budgets with synthetic data:

| Measurement | Initial Sahha target | Investigate when |
| --- | ---: | ---: |
| Browser navigation TTFB, p75 | <= 800 ms | > 800 ms |
| Browser INP, p75 | <= 200 ms | > 200 ms |
| Warm Gateway simple read, p95 | <= 250 ms | > 500 ms |
| Warm Gateway cross-service read, p95 | <= 500 ms | > 1,000 ms |
| Gateway state-changing command, p95 | <= 750 ms | > 1,500 ms |
| Login with BCrypt, p95 | <= 1,500 ms | > 2,500 ms |
| Indexed PostgreSQL lookup on local data | <= 20 ms | > 50 ms |
| More complex local PostgreSQL list query | <= 50 ms | > 100 ms |
| Local Memurai command, p95 | <= 5 ms | > 10 ms |
| Outbox occurrence to Notification persistence, p95 | <= 2,000 ms | > 3,000 ms |
| Notification commit to WebSocket receipt, p95 | <= 500 ms | > 1,000 ms |
| Appointment command to visible notification, p95 | <= 3,000 ms | > 5,000 ms |

The one-second Scheduling outbox polling interval is included in the event
budget. Email delivery and application startup are measured separately and
must not be mixed into warm API percentiles.

Always record p50, p95, p99, maximum, error count, request count, payload size,
machine/profile, and whether the request was cold or warm. An average alone can
hide the slow requests users notice.

## 4. Current local prerequisites

Observed through 2026-08-24:

- PostgreSQL 18.1 service `postgresql-x64-18` is running on `localhost:5432`.
- Memurai is running on `127.0.0.1:6379` and returns `PONG`.
- Native Kafka 4.3.1 is running on `127.0.0.1:9092`. Its Windows scripts are
  available through the local `K:` substituted drive for the current setup.
- `pg_stat_statements` 1.12 is active in Auth, Organisation, Patient,
  Scheduling, and Notification; query IDs and I/O timing are enabled.
- Discovery, Gateway, Auth, Organisation, Patient, Scheduling, Notification,
  and the React frontend were running when the live workflow was verified.

If Kafka must be installed on another workstation, follow the
[official Apache Kafka local-files quick start](https://kafka.apache.org/43/getting-started/quickstart/)
with Java 17 or later. This project uses Java 21. On Windows, use the equivalent
scripts under the downloaded distribution's `bin\windows` directory when they
are present. Never reformat an existing Kafka log directory; formatting is a
one-time operation for a new empty local broker.

After Kafka is installed, set its location for the current PowerShell session:

```powershell
$SahhaKafkaHome = 'K:\'
$SahhaKafkaBin = Join-Path $SahhaKafkaHome 'bin\windows'
Test-Path (Join-Path $SahhaKafkaBin 'kafka-topics.bat')
Test-NetConnection localhost -Port 9092
```

Both results must be `True` after the broker is running.

### Windows restart recovery used by this workstation

The `K:` mapping and Kafka environment values belong to the local Windows
session. Build the Log4j option from short values so copied commands cannot
insert a hidden newline:

```powershell
$logPrefix = '-Dlog4j2.configurationFile='
$logFile = 'file:///K:/config/log4j2.yaml'
$env:KAFKA_LOG4J_OPTS = $logPrefix + $logFile
$env:KAFKA_HEAP_OPTS = '-Xms256M -Xmx512M'
$env:LOG_DIR = 'C:/Users/LENOVO/kafka-logs'
```

Verify `$env:KAFKA_LOG4J_OPTS -match "[`r`n]"` returns `False`. On 2026-08-24,
KRaft recovery also found old `*.deleted` and checkpoint files marked read-only.
With the broker stopped, only their read-only attributes were cleared; no Kafka
file was manually deleted and storage was not reformatted.

Kafka 4.3.1 later encountered a Windows file-sharing conflict while its log
cleaner moved a compacted `__consumer_offsets` time index. The current small,
synthetic local environment starts the broker with this runtime-only override:

```powershell
$server = 'K:\bin\windows\kafka-server-start.bat'
$config = 'K:\config\server.properties'
& $server $config --override 'log.cleaner.enable=false'
```

Disabling compaction can grow local consumer-offset storage, so monitor the
directory and keep this workaround local. Do not copy it to Azure or production;
use a supported managed or Linux broker configuration there.

Create the local appointment topic idempotently for a single development
broker:

```powershell
& (Join-Path $SahhaKafkaBin 'kafka-topics.bat') `
  --bootstrap-server localhost:9092 `
  --create `
  --if-not-exists `
  --topic sahha.scheduling.appointments.v1 `
  --partitions 1 `
  --replication-factor 1
```

One partition and replication factor 1 match the current disposable local
environment. Production partition and replication counts must be selected from
measured throughput, ordering, and availability requirements rather than copied
from this workstation.

## 5. Synthetic accounts and evidence sheet

There are no committed demo passwords or complete seeded role accounts yet.
Do not invent credentials in source control. Use one controlled mailbox with
plus aliases if the provider supports them, for example:

| Role | Synthetic alias pattern | Active organisation required? |
| --- | --- | --- |
| Platform Administrator | `your.address+sahha-platform@gmail.com` | No |
| Organisation Administrator | `your.address+sahha-org-admin@gmail.com` | Yes |
| Doctor | `your.address+sahha-doctor@gmail.com` | Yes |
| Receptionist | `your.address+sahha-reception@gmail.com` | Yes |
| Patient | `your.address+sahha-patient@gmail.com` | No |

Store the exact emails/passwords only in a local password manager or temporary
untracked worksheet. The patient's Auth email must exactly match the email in
their Patient Service registration before account linking can succeed.

The first local Platform Administrator assignment remains the documented
trusted bootstrap in `docs/ORGANISATION_SWAGGER_GUIDE.md`. Log out and log in
again after assignment so a new access token contains the persisted role.

Record these non-secret identifiers during the run:

| Name | Value |
| --- | --- |
| Organisation ID | |
| Department ID | |
| Doctor user/membership IDs | |
| Receptionist user/membership IDs | |
| Patient registration ID | |
| Appointment A ID: receptionist booking | |
| Appointment B ID: patient request | |
| Request IDs from response headers | |
| Kafka partition/offsets | |

## 6. Start the Phase 4 stack

### 6.1 Infrastructure checks

```powershell
Get-Service postgresql-x64-18,Memurai
& 'C:\Program Files\Memurai\memurai-cli.exe' PING
Test-NetConnection localhost -Port 5432
Test-NetConnection localhost -Port 6379
Test-NetConnection localhost -Port 9092
```

Expect both Windows services to be `Running`, Redis to return `PONG`, and all
three TCP tests to succeed.

### 6.2 Start services in separate terminals

Start Discovery first:

```powershell
.\mvnw.cmd -pl discovery-server spring-boot:run
```

Then start Auth, Organisation, and Patient with the local profile:

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run '-Dspring-boot.run.profiles=local'
```

```powershell
.\mvnw.cmd -pl organisation-service spring-boot:run '-Dspring-boot.run.profiles=local'
```

```powershell
.\mvnw.cmd -pl patient-service spring-boot:run '-Dspring-boot.run.profiles=local'
```

Enable the appointment publisher before starting Scheduling:

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:9092'
$env:SCHEDULING_OUTBOX_PUBLISHER_ENABLED = 'true'
.\mvnw.cmd -pl scheduling-service spring-boot:run '-Dspring-boot.run.profiles=local'
```

Enable the appointment consumer before starting Notification:

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:9092'
$env:NOTIFICATION_APPOINTMENT_CONSUMER_ENABLED = 'true'
.\mvnw.cmd -pl notification-service spring-boot:run '-Dspring-boot.run.profiles=local'
```

Start Gateway last:

```powershell
.\mvnw.cmd -pl api-gateway spring-boot:run '-Dspring-boot.run.profiles=local'
```

The flags apply only to the terminal where they were set. If Scheduling or
Notification was already running, restart that service after setting the flag.

Start the real-auth frontend:

```powershell
Set-Location C:\Users\LENOVO\Desktop\sahha\frontend
npm.cmd run dev
```

The frontend calls `http://localhost:8079/api/v1`; it must never be pointed
directly at an internal service.

In a separate PowerShell window at the repository root, run the repeatable
doctor/receptionist browser gate:

```powershell
Set-Location C:\Users\LENOVO\Desktop\sahha
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\Test-SahhaFrontendRoles.ps1
```

Enter the two synthetic passwords only at the masked prompts. The wrapper does
not store or print them. It verifies the real Gateway-backed doctor and
receptionist routes at phone, tablet, and desktop widths, checks Sahha identity,
document titles, keyboard entry, the doctor notification panel, API failures,
and document-level overflow, then logs both sessions out. Sanitized screenshots
are written to `%TEMP%\sahha-role-live` by default.

Verified role-browser result on 2026-08-24:

| Scenario | Viewport | Width result | Sahha title/brand | API errors |
| --- | --- | --- | --- | --- |
| Doctor appointments | 1440 x 1000 | document/body 1440 | PASS | 0 |
| Doctor availability | 1024 x 900 | document/body 1024 | PASS | 0 |
| Doctor overview + notifications | 375 x 900 | document/body 375 | PASS | 0 |
| Reception patient directory | 1440 x 1000 | document/body 1440 | PASS | 0 |
| Reception appointments | 1024 x 900 | document/body 1024 | PASS | 0 |
| Reception patient directory | 375 x 900 | document/body 375 | PASS | 0 |

### 6.3 Verify discovery and health

Open `http://localhost:8761` and verify these applications appear:

- `API-GATEWAY`
- `AUTH-SERVICE`
- `ORGANISATION-SERVICE`
- `PATIENT-SERVICE`
- `SCHEDULING-SERVICE`
- `NOTIFICATION-SERVICE`

For setup diagnostics only, check each local health endpoint:

```powershell
$SahhaPorts = 8079,8081,8082,8083,8084,8087,8761
foreach ($SahhaPort in $SahhaPorts) {
  try {
    $SahhaHealth = Invoke-RestMethod "http://localhost:$SahhaPort/actuator/health"
    "port=$SahhaPort status=$($SahhaHealth.status)"
  } catch {
    "port=$SahhaPort unavailable"
  }
}
```

The application workflow must still be tested through Gateway, not these
direct diagnostic URLs.

## 7. Complete Phase 4 workflow

Use the React application for the acceptance journey. Use Swagger only to
isolate a failing contract. Keep Chrome DevTools **Network** and the doctor
notification panel open.

### 7.1 Platform and organisation setup

1. Register, verify, and log in as the synthetic Platform Administrator.
2. Create an active synthetic clinic/hospital/private practice.
3. Register and verify the Organisation Administrator account.
4. Assign it as the organisation's administrator by exact email.
5. Log in as that administrator and select the new active organisation.
6. Create a synthetic department.
7. Invite the doctor and receptionist using their exact verified Auth emails.
8. Log in as each invited user, accept the invitation, and select the
   organisation.
9. Return as Organisation Administrator and verify both memberships and roles.

Expected failures:

- A non-platform account creating an organisation returns `403`.
- A Platform Administrator without an active organisation administrator
  membership cannot manage departments/staff.
- A resource UUID from another organisation is hidden as `404`.
- A state-changing request without the CSRF header returns `403`.

### 7.2 Doctor setup

1. Log in as the doctor and select the organisation.
2. Complete the professional profile with synthetic licence data.
3. Configure timezone, location, appointment duration, weekly hours, breaks,
   and at least two future open slots.
4. Read the schedule again and retain its version.

Expected failures:

- A doctor cannot read another doctor's private schedule controls.
- A stale schedule version returns `409` instead of overwriting the newer
  schedule.
- A slot outside hours, inside a break/absence, or already occupied is absent.

### 7.3 Patient registration and account link

1. Register and verify the synthetic Patient Auth account. Do not select an
   active organisation for this account.
2. Log in as the receptionist with the organisation selected.
3. Run duplicate detection and create the administrative patient registration.
   Its email must be the exact verified Patient Auth email.
4. Record the generated medical-record number and date of birth.
5. Log in as the patient and open the appointments page.
6. Link the account using organisation ID, medical-record number, and date of
   birth. The backend also compares the authoritative verified email.

Expected failures:

- Wrong birth date, MRN, organisation, or email combination does not link.
- The patient cannot supply another patient's registration ID to list or book
  appointments; it is hidden as `404`.
- A patient account does not receive an organisation membership merely because
  the link succeeds.

### 7.4 Appointment A: receptionist path

1. Log in as the receptionist.
2. Select the patient, doctor, and first published slot.
3. Book the appointment and record its ID, response status, `X-Request-Id`,
   version, and duration. Expect `REQUESTED`.
4. Verify the doctor receives an unread realtime notification. Open that
   notification and verify the bell count decrements immediately; a failed
   read command must restore the previous count.
5. Log in as the doctor, open the appointment board, and confirm it. Use
   **Upcoming** for today plus the next 30 days or **History** for the previous
   31 days; the two explicit windows replace the old hidden rolling cutoff.
6. Log in as the receptionist, select the range containing the confirmed
   appointment, and check the patient in.
7. Verify the doctor receives `PATIENT_CHECKED_IN`.
8. As the doctor, start and then complete the appointment.

Expected failures:

- Booking the same occupied doctor/time returns `409`.
- A receptionist cannot confirm/reject or start/complete: expect `403`.
- Start before check-in and complete before start return `409`.
- Reusing an old optimistic version returns `409`.

### 7.5 Appointment B: patient self-service path

1. Keep the doctor notification WebSocket connected in one browser/profile.
2. Log in as the linked patient in a separate browser/profile.
3. Select the linked registration, real doctor, and second published slot.
4. Request the appointment. Expect `REQUESTED` and one appointment only even
   if the same browser command is safely retried with its idempotency UUID.
5. Verify the doctor receives one notification and confirm the appointment.
6. Refresh the patient's status history and verify `CONFIRMED`.

The current patient projection recovers status through REST refresh. Realtime
patient status delivery has not yet been implemented and must not be claimed.

### 7.6 Realtime recovery

1. In DevTools **Network -> WS**, select
   `ws://localhost:8079/api/v1/notifications/ws`.
2. Verify `CONNECTED`, the private `/user/queue/notifications` subscription,
   heartbeats, and later `MESSAGE` frames.
3. Disconnect the network or stop Notification briefly, then restore it.
4. Verify the client moves through reconnecting/connected states.
5. Verify the persisted REST inbox and unread count recover any message missed
   while the socket was unavailable.

Never copy the STOMP `CONNECT` CSRF value or cookie values into evidence.

## 8. Measure Gateway response time

### 8.1 Browser method

In DevTools **Network**:

1. Filter to **Fetch/XHR**.
2. Enable **Preserve log** but do not export a HAR containing credentials.
3. Record URL path, method, status, duration, waiting/TTFB, transferred bytes,
   response bytes, and `X-Request-Id`.
4. Record one first request after service restart as `cold`.
5. Execute one unrecorded warm-up, then at least 30 sequential requests for a
   warm baseline. Do not run unsafe create/update commands repeatedly.
6. Measure patient list, doctor appointments, slots, notifications, session,
   and one representative state-changing command separately.

### 8.2 Repeatable PowerShell sampling

Use the repository sampler from the repository root. It supports Windows
PowerShell 5.1, prompts for the password with a masked secure input, keeps
cookies only in memory, logs out the temporary session, and writes a sanitized
JSON report without the email, password, cookies, CSRF token, or JWT:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\performance\Measure-SahhaGateway.ps1 `
  -Role Receptionist `
  -WarmSamples 30
```

Allowed roles are `PlatformAdministrator`, `OrganisationAdministrator`,
`Doctor`, `Receptionist`, and `Patient`. Organisation-scoped accounts reuse
their current active organisation, accept `-OrganisationId` when an explicit
context is required, or select their first active membership when no context is
already selected. The default report path is printed at the end and is under
the current user's temporary directory.

The script records the first request separately, performs one unrecorded
warm-up, and then makes sequential warm requests. Pass `-FirstRequestIsCold`
only when the services were deliberately restarted immediately before the run;
the switch labels evidence and does not restart services itself.

The following commands show the equivalent manual method. This also keeps
authentication cookies in memory and does not write a cookie jar. Use a
synthetic account and close the PowerShell session afterward.

```powershell
$SahhaBase = 'http://localhost:8079/api/v1'
$SahhaWebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$SahhaEmail = Read-Host 'Synthetic Sahha email'
$SahhaSecurePassword = Read-Host 'Synthetic Sahha password' -AsSecureString
$SahhaPlainPassword = [Net.NetworkCredential]::new('', $SahhaSecurePassword).Password

$SahhaCsrf = Invoke-RestMethod `
  -Uri "$SahhaBase/auth/csrf" `
  -WebSession $SahhaWebSession
$SahhaHeaders = @{ $SahhaCsrf.headerName = $SahhaCsrf.token }
$SahhaLoginBody = @{
  email = $SahhaEmail
  password = $SahhaPlainPassword
  deviceName = 'Sahha local timing terminal'
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "$SahhaBase/auth/login" `
  -WebSession $SahhaWebSession `
  -Headers $SahhaHeaders `
  -ContentType 'application/json' `
  -Body $SahhaLoginBody | Out-Null

Remove-Variable SahhaPlainPassword,SahhaLoginBody
```

Define a nearest-rank percentile helper and measure a safe GET:

```powershell
function Get-SahhaPercentile {
  param([double[]]$Values, [double]$Percentile)
  $SahhaOrdered = @($Values | Sort-Object)
  $SahhaIndex = [Math]::Max(0, [Math]::Ceiling($Percentile * $SahhaOrdered.Count) - 1)
  return $SahhaOrdered[$SahhaIndex]
}

$SahhaMeasuredUri = "$SahhaBase/auth/session"
Invoke-WebRequest -Uri $SahhaMeasuredUri -WebSession $SahhaWebSession | Out-Null

$SahhaSamples = 1..30 | ForEach-Object {
  $SahhaTimer = [Diagnostics.Stopwatch]::StartNew()
  $SahhaResponse = Invoke-WebRequest `
    -Uri $SahhaMeasuredUri `
    -WebSession $SahhaWebSession `
    -SkipHttpErrorCheck
  $SahhaTimer.Stop()
  [pscustomobject]@{
    Status = [int]$SahhaResponse.StatusCode
    Milliseconds = $SahhaTimer.Elapsed.TotalMilliseconds
    RequestId = $SahhaResponse.Headers['X-Request-Id']
  }
}

$SahhaDurations = [double[]]$SahhaSamples.Milliseconds
[pscustomobject]@{
  Count = $SahhaDurations.Count
  Errors = @($SahhaSamples | Where-Object Status -ge 400).Count
  P50ms = [Math]::Round((Get-SahhaPercentile $SahhaDurations 0.50), 2)
  P95ms = [Math]::Round((Get-SahhaPercentile $SahhaDurations 0.95), 2)
  P99ms = [Math]::Round((Get-SahhaPercentile $SahhaDurations 0.99), 2)
  Maxms = [Math]::Round(($SahhaDurations | Measure-Object -Maximum).Maximum, 2)
}
```

After selecting an active organisation, replace `$SahhaMeasuredUri` with a
role-appropriate safe Gateway GET. Do not compare a direct service request to
a Gateway request as if they were the same measurement.

## 9. Inspect Kafka appointment events

Apache's console consumer and consumer-group tooling are documented in
[Basic Kafka Operations](https://kafka.apache.org/43/operations/basic-kafka-operations/).

### 9.1 Verify topic and consumer group

```powershell
& (Join-Path $SahhaKafkaBin 'kafka-topics.bat') `
  --bootstrap-server localhost:9092 `
  --describe `
  --topic sahha.scheduling.appointments.v1

& (Join-Path $SahhaKafkaBin 'kafka-consumer-groups.bat') `
  --bootstrap-server localhost:9092 `
  --describe `
  --group sahha-notification-appointments-v1
```

Expected while Notification is running:

- The group exists and is stable.
- Every partition has an assigned consumer or is visible in the group output.
- `LAG` returns to `0` after each appointment event is processed.

### 9.2 Watch new events

Start this before performing an appointment command:

```powershell
& (Join-Path $SahhaKafkaBin 'kafka-console-consumer.bat') `
  --bootstrap-server localhost:9092 `
  --topic sahha.scheduling.appointments.v1 `
  --property print.timestamp=true `
  --property print.partition=true `
  --property print.offset=true `
  --property print.key=true `
  --property print.headers=true
```

For each command verify:

- The Kafka key equals `appointmentId`, preserving per-appointment ordering.
- `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `appointmentId`,
  `organisationId`, `patientId`, `doctorUserId`, `actorUserId`, `requestId`,
  `status`, period, timezone, location, and `resourceVersion` are coherent.
- Transition events may include `previousStatus` and `previousStartsAt`.
- The value does not contain email, telephone, address, date of birth,
  medical-record number, membership ID, diagnosis, clinical note, medication,
  password, cookie, token, or rejection/cancellation reason.

The payload is minimal for Notification processing, not anonymous: stable
patient/doctor/actor identifiers are intentionally present. Do not paste the
payload into public evidence.

### 9.3 Prove outbox publication and durable consumption

In the Scheduling database, the newest outbox rows should progress from
`published_at IS NULL` to a non-null timestamp only after Kafka acknowledges:

```sql
SELECT
    id,
    appointment_id,
    event_type,
    aggregate_version,
    occurred_at,
    published_at,
    publication_attempts,
    last_error_code
FROM appointment_outbox_event
ORDER BY occurred_at DESC
LIMIT 20;
```

Inspect payload keys without printing values:

```sql
SELECT
    event_type,
    jsonb_object_keys(payload) AS payload_key
FROM appointment_outbox_event
WHERE id = 'replace-with-synthetic-outbox-event-id'
ORDER BY payload_key;
```

In the Notification database:

```sql
SELECT
    event_id,
    event_type,
    appointment_id,
    resource_version,
    source_partition,
    source_offset,
    outcome,
    event_occurred_at,
    processed_at,
    ROUND(EXTRACT(EPOCH FROM (processed_at - event_occurred_at)) * 1000, 2)
        AS processing_lag_ms
FROM consumed_appointment_event
ORDER BY processed_at DESC
LIMIT 20;
```

Duplicate delivery is safe when the same `event_id` produces only one consumed
receipt and at most one recipient notification. Do not reset the real
Notification group offsets merely to test duplicates; the automated tests
already exercise stable-event deduplication.

## 10. Inspect Memurai/Redis safely

Redis is a disposable technical accelerator; PostgreSQL remains authoritative.
Redis's [`SCAN`](https://redis.io/docs/latest/commands/scan/) incrementally
iterates keys, while [`TTL`](https://redis.io/docs/latest/commands/ttl/) reports
remaining expiry (`-1` means no expiry and `-2` means missing).

Set the CLI path once:

```powershell
$SahhaRedisCli = 'C:\Program Files\Memurai\memurai-cli.exe'
& $SahhaRedisCli PING
```

### 10.1 Session projections

After login and `GET /api/v1/auth/session`:

```powershell
& $SahhaRedisCli --scan --pattern 'sahha:auth:session:v1:*'
```

Select one synthetic key locally, then inspect metadata before its value:

```powershell
$SahhaSessionKey = & $SahhaRedisCli --scan `
  --pattern 'sahha:auth:session:v1:*' | Select-Object -First 1
& $SahhaRedisCli TYPE $SahhaSessionKey
& $SahhaRedisCli PTTL $SahhaSessionKey
& $SahhaRedisCli MEMORY USAGE $SahhaSessionKey
```

An active projection should be a string and normally have no more than 30
seconds remaining. If it expires, the next protected Auth check reloads the
authoritative PostgreSQL session and repopulates the cache. This is cache-aside
behavior, not a database poll every 30 seconds.

`GET` is permitted only for local learning because the value contains internal
user/session/organisation identifiers. It must contain no JWT, refresh token,
password, IP address, email, or device hash. Never paste or screenshot it.

After logout or global revocation, the same key may become a non-`ACTIVE`
versioned tombstone whose TTL is bounded by absolute session expiry. It must
not become an older `ACTIVE` projection again.

### 10.2 Rate-limit counters

Generate one synthetic login attempt, then inspect only key metadata:

```powershell
& $SahhaRedisCli --scan --pattern 'sahha:auth:rate-limit:v1:*'
$SahhaRateKey = & $SahhaRedisCli --scan `
  --pattern 'sahha:auth:rate-limit:v1:*' | Select-Object -First 1
& $SahhaRedisCli TYPE $SahhaRateKey
& $SahhaRedisCli PTTL $SahhaRateKey
```

The key suffix is a one-way address hash. Do not repeatedly call public Auth
endpoints just to increase counters; that can correctly trigger `429`.

### 10.3 Redis performance and health

```powershell
& $SahhaRedisCli INFO stats
& $SahhaRedisCli INFO memory
& $SahhaRedisCli INFO keyspace
& $SahhaRedisCli SLOWLOG LEN
& $SahhaRedisCli --latency --raw -i 5
& $SahhaRedisCli LATENCY DOCTOR
```

Record keyspace hits/misses, evictions, expired keys, used memory, slow commands,
and observed latency spikes. `LATENCY DOCTOR` may report that latency monitoring
is disabled; do not change production-like settings merely to create output.
The command's purpose is described in the
[Redis latency documentation](https://redis.io/docs/latest/commands/latency-doctor/).

The raw CLI latency output is `minimum maximum average sample-count` in
milliseconds. It is useful as a quick independent check but does not provide a
percentile. For the recorded p95 baseline, use one persistent TCP connection,
discard initial warm-up commands, and time sequential `PING` round trips; do not
launch a new CLI process for each sample because process startup would dominate
the Redis measurement.

Verified local baseline on 2026-08-21:

- Memurai 4.2.3 exposes Redis protocol 7.4.9 and had been running for nine days.
- The five-second native CLI sample reported minimum 0 ms, maximum 2 ms, average
  0.48 ms over 322 samples.
- A persistent local TCP sample discarded 20 warm-ups and recorded 500
  sequential `PING` commands: p50 0.0242 ms, p95 0.1133 ms, p99 0.1449 ms, and
  maximum 0.2496 ms. The p95 passes the 5 ms local target.
- Memory was 1.41 MB out of a 256 MB limit (about 0.55%), peak 1.41 MB, with
  fragmentation ratio 1.00 and `allkeys-lru`. There were zero evictions, zero
  rejected connections, zero blocked clients, and an empty slow log.
- The cumulative cache snapshot contained 1,122 hits and 530 misses, a 67.92%
  hit ratio. This includes deliberate cache-aside misses and diagnostic access,
  so it is a baseline—not an optimisation target by itself.
- Database 0 contained 19 keys and every key had an expiry. Safe prefix scans
  found 19 string session entries using 10,296 bytes in total, all with TTLs
  longer than 30 seconds and therefore consistent with logged-out/revoked
  tombstone candidates. No active 30-second projection or rate-limit counter
  was present at the snapshot time. No cached value was read for this evidence.
- The server latency monitor remained disabled (`latency-monitor-threshold=0`),
  so `LATENCY DOCTOR` correctly returned no diagnosis. It was not enabled merely
  to manufacture data.

Never use `KEYS *`, `FLUSHDB`, or `FLUSHALL` during the workflow.

## 11. Measure PostgreSQL query time

### 11.1 Enable `pg_stat_statements` once

The extension is active in the five measured databases. PostgreSQL requires it
in `shared_preload_libraries`; see the official
[`pg_stat_statements` documentation](https://www.postgresql.org/docs/current/pgstatstatements.html).

Read-only preflight verified on 2026-08-21:

- PostgreSQL 18.1 is running from `C:\Program Files\PostgreSQL\18`, accepts
  local connections on port 5432, and uses
  `C:\Program Files\PostgreSQL\18\data\postgresql.conf`.
- `shared_preload_libraries` is empty, `compute_query_id` is `auto`, and both
  `track_io_timing` and `track_wal_io_timing` are off. All values come from
  defaults and none currently has a pending restart.
- `pg_stat_statements` version 1.12 is available but not installed in any of the
  ten `sahha_*` databases. This preflight did not change configuration, create
  an extension, reset statistics, or restart PostgreSQL.
- The active service databases are approximately 8–10 MB. Their cumulative
  buffer-hit ratios are at least 99.92%, with zero temporary files and zero
  deadlocks. I/O time is reported as zero because timing is disabled, not
  because physical reads can never occur.
- The five implemented data services currently expose small synthetic table
  estimates: Auth 877 live/70 dead tuples, Organisation 57/88, Patient 85/99,
  Scheduling 66/161, and Notification 24/119. These estimates are context for
  later plans, not evidence to vacuum or index speculatively.
- Scheduling's outbox table has a high cumulative index-scan count relative to
  its ten rows, consistent with periodic ready-event polling. Exact statement
  cost must be verified with `pg_stat_statements` before changing the polling
  query, indexes, or interval.

Activation state on 2026-08-22:

- `ALTER SYSTEM` safely wrote `shared_preload_libraries=pg_stat_statements`,
  `compute_query_id=on`, and `track_io_timing=on`, and the configuration reload
  parsed all three entries.
- Query IDs and I/O timing are active immediately. The preload entry is present
  in `postgresql.auto.conf` but cannot apply until PostgreSQL restarts, which is
  expected for this setting.
- The automated service restart was rejected by Windows Service Control because
  the terminal was not elevated. PostgreSQL remained running on port 5432 and
  no extension was created, so application availability was not interrupted.
- Complete the one remaining restart from an Administrator PowerShell with
  `Restart-Service postgresql-x64-18`, then verify the three settings before
  creating the extensions.

Activation completed on 2026-08-24:

- The Administrator restart completed and PostgreSQL returned to `Running`.
  `shared_preload_libraries=pg_stat_statements`, `compute_query_id=on`, and
  `track_io_timing=on` are all active from the configuration file with no
  pending restart.
- The runtime keeps conservative defaults: 5,000 tracked statements,
  top-level statement tracking, statistics persistence enabled, utility
  tracking enabled, and planning-time tracking disabled.
- `pg_stat_statements` 1.12 was installed and queried successfully only in
  `sahha_auth`, `sahha_organisation`, `sahha_patient`, `sahha_scheduling`, and
  `sahha_notification`. No role grants were changed.
- `sahha_audit`, `sahha_auth_test`, `sahha_clinical`,
  `sahha_communication`, and `sahha_file` remain without the extension because
  their internship workflows are not part of the current measurement slice.
- The shared statement subsystem reset timestamp is 2026-08-24 19:39:43
  Africa/Tunis. Only verification queries existed immediately after setup; the
  controlled workload and plan evidence are recorded below.

As a PostgreSQL administrator:

1. In `postgresql.conf`, add `pg_stat_statements` to the existing
   `shared_preload_libraries` list without removing other libraries. For this
   local installation the equivalent setting has already been written through
   `ALTER SYSTEM`, so do not add a duplicate entry.
2. Set `compute_query_id = on` and, for useful I/O evidence,
   `track_io_timing = on`.
3. Restart only the PostgreSQL 18 service during a planned local interruption.
4. In pgAdmin Query Tool, connect to each synthetic service database as an
   administrator and run:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

At minimum enable it in `sahha_auth`, `sahha_organisation`, `sahha_patient`,
`sahha_scheduling`, and `sahha_notification`. Service accounts remain
least-privileged; do not grant them superuser or cross-database access.

### 11.2 Find expensive/frequent statements

Run the workflow, then execute this inside one service database:

```sql
SELECT
    calls,
    ROUND(total_exec_time::numeric, 2) AS total_exec_ms,
    ROUND(mean_exec_time::numeric, 2) AS mean_exec_ms,
    ROUND(min_exec_time::numeric, 2) AS min_exec_ms,
    ROUND(max_exec_time::numeric, 2) AS max_exec_ms,
    rows,
    shared_blks_hit,
    shared_blks_read,
    temp_blks_read,
    temp_blks_written,
    LEFT(query, 220) AS query_sample
FROM pg_stat_statements
WHERE dbid = (
    SELECT oid FROM pg_database WHERE datname = current_database()
)
  AND query NOT ILIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC
LIMIT 20;
```

`pg_stat_statements` provides aggregate mean/min/max and standard deviation,
not an end-to-end API p95. Use the browser/PowerShell samples for request p95
and this view to identify which SQL statements contribute to it.

### 11.3 Inspect an actual plan

Copy a normalized slow query and replace parameters only with synthetic IDs.
For the patient appointment history query:

```sql
EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT TEXT)
SELECT
    id,
    status,
    starts_at,
    ends_at,
    doctor_user_id,
    location_label,
    version
FROM scheduled_appointment
WHERE organisation_id = 'replace-with-synthetic-organisation-uuid'
  AND patient_registration_id = 'replace-with-synthetic-registration-uuid'
  AND starts_at < TIMESTAMPTZ '2026-09-15T00:00:00Z'
  AND ends_at > TIMESTAMPTZ '2026-08-15T00:00:00Z'
ORDER BY starts_at, id;
```

Record:

- Planning and execution time.
- Estimated rows compared with actual rows.
- Sequential, index, bitmap, or GiST scan choice.
- Shared buffer hits versus reads.
- Temporary reads/writes, sorts, and repeated loops.
- The exact index used.

The existing patient-history index starts with
`(organisation_id, patient_registration_id, starts_at)`, so this query should
have an index-supported plan once there is enough representative data for the
planner to prefer it. A sequential scan on a tiny synthetic table can be
correct and is not proof that an index is missing.

`EXPLAIN ANALYZE` actually executes its statement and adds measurement overhead,
as the [PostgreSQL EXPLAIN documentation](https://www.postgresql.org/docs/current/sql-explain.html)
warns. Prefer `SELECT`. If a mutation must be investigated, use only synthetic
data and protect it with rollback:

```sql
BEGIN;
EXPLAIN (ANALYZE, BUFFERS, WAL) UPDATE ...;
ROLLBACK;
```

Never add an index from one slow result. Reproduce it, confirm representative
data/statistics, capture the before plan, add only a service-owned migration,
rerun tests, then capture the after plan and API percentiles.

### 11.4 Verified controlled workload and plans

Recorded on 2026-08-24 with synthetic data after the statement subsystem was
activated:

- A baseline captured calls for 112 query IDs before the two role samplers.
  The after snapshot found 48 changed statements. Because the baseline retained
  call counts rather than cumulative execution totals, interval totals are
  estimated as changed calls multiplied by the current cumulative mean; the
  per-call means and actual plans are authoritative observations.
- Scheduling's empty ready-outbox poll changed by 463 calls and averaged
  0.0298 ms per call. Its plan used `ix_appointment_outbox_ready` and executed
  in 0.056 ms with no matching row.
- The empty-query patient directory averaged 0.0565 ms. Appointment range
  reads averaged 0.0287-0.0389 ms, doctor availability reads 0.0186-0.0720 ms,
  the Notification inbox 0.0368 ms, and the organisation membership lookup
  0.0233 ms.
- The largest individual means were expected low-frequency Auth login/logout
  writes: session insertion 4.265 ms and refresh-token insertion 2.427 ms.
  Patient audit insertion averaged 0.382 ms. These are not repeated session-read
  costs and were not executed again merely to obtain plans.
- No changed statement used temporary blocks. Physical reads were small and
  the endpoint reads were primarily buffer hits.

Representative actual read-plan results:

| Service/query | Execution ms | Important plan evidence |
| --- | ---: | --- |
| Auth active platform roles | 1.594 | `ix_user_platform_role_active_user`; one physical page read |
| Organisation membership | 0.206 | `ix_membership_user_status`; shared-buffer hits only |
| Patient directory | 0.190 | registration unique index plus patient primary-key lookup |
| Scheduling appointment range | 0.136 | organisation index lookup; three synthetic rows inspected |
| Scheduling ready outbox | 0.056 | `ix_appointment_outbox_ready`; zero ready rows |
| Notification inbox | 0.095 | `ix_in_app_notification_recipient_history`; six rows |

The ad hoc plans included parameter-selection CTEs and are evidence about scan
shape and execution, not a comparison with Hibernate planning latency. No query,
index, cache, or polling change is justified by this dataset.

## 12. Correlate one appointment across all layers

For one synthetic appointment, capture this sequence without secret values:

| Layer | Evidence | Timestamp/latency |
| --- | --- | --- |
| Browser | method/path/status/duration/request ID | |
| Scheduling DB | appointment ID/version/status | |
| Scheduling outbox | event ID/type/occurred/published/attempts | |
| Kafka | key/partition/offset/timestamp/schema/version | |
| Notification receipt | event ID/outcome/processed time | |
| Notification inbox | notification ID/created/read state | |
| WebSocket | connected/subscribed/message received | |
| REST recovery | inbox/unread endpoint status and duration | |

Verified reference correlation on 2026-08-19:

| Layer | Evidence | Timestamp/latency |
| --- | --- | --- |
| Scheduling DB | `ae686935-5244-4854-bc38-0e38a0d28bf6`, `COMPLETED`, version 4 | completed 15:33:40.608 Africa/Tunis |
| Scheduling audit | booked, confirmed, checked in, started, completed | append-only sequence verified |
| Scheduling outbox | check-in, start, and complete rows published once | all attempts = 1 |
| Kafka/consumer | partition 0, offsets 7-9; group 10/10 | lag = 0 |
| Notification receipt | check-in created; start/complete avoided self-notification | zero rejected events |
| Notification inbox | `PATIENT_CHECKED_IN` persisted and read | read 15:34:23.488 Africa/Tunis |
| Browser/WebSocket | doctor received check-in without refresh; bell decremented on open | manually verified |

HTTP and database timing cells remain intentionally unpopulated until the
repeatable sampling procedure is run; event timestamps are not HTTP latency.

Calculate database-visible event lag:

```sql
SELECT
    event_id,
    ROUND(EXTRACT(EPOCH FROM (processed_at - event_occurred_at)) * 1000, 2)
        AS outbox_to_consumer_ms
FROM consumed_appointment_event
WHERE appointment_id = 'replace-with-synthetic-appointment-uuid'
ORDER BY resource_version;
```

For a WebSocket `MESSAGE`, compare the contained `eventOccurredAt` and
`createdAt` timestamps with local receipt time. Treat clocks on different
machines as unreliable unless they are synchronized.

## 13. Evidence table to complete before optimisation

| Scenario | Count | Cold ms | p50 ms | p95 ms | p99 ms | Max ms | Errors | Result |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Auth session — Receptionist | 30 | 46.95 (warm) | 41.78 | 52.65 | 54.33 | 54.33 | 0 | PASS |
| Auth session — Doctor | 30 | 49.97 | 41.01 | 63.17 | 67.81 | 67.81 | 0 | PASS |
| Organisation memberships — Receptionist | 30 | 40.68 (warm) | 48.00 | 66.43 | 75.52 | 75.52 | 0 | PASS |
| Organisation memberships — Doctor | 30 | 53.91 | 46.24 | 69.88 | 71.41 | 71.41 | 0 | PASS |
| Patient directory/list — Receptionist | 30 | 107.38 (warm) | 63.51 | 104.82 | 123.44 | 123.44 | 0 | PASS |
| Available-doctor directory — Receptionist | 30 | 92.95 (warm) | 47.46 | 65.07 | 71.01 | 71.01 | 0 | PASS |
| Doctor availability — Doctor | 30 | 793.09 | 66.15 | 103.49 | 116.38 | 116.38 | 0 | PASS |
| Appointment history — Receptionist | 30 | 52.14 (warm) | 50.98 | 80.75 | 87.75 | 87.75 | 0 | PASS |
| Appointment history — Doctor | 30 | 96.40 | 55.81 | 76.27 | 116.41 | 116.41 | 0 | PASS |
| Notification inbox — Doctor | 30 | 842.09 | 61.96 | 87.62 | 111.29 | 111.29 | 0 | PASS |
| Local Memurai persistent PING | 500 | n/a | 0.0242 | 0.1133 | 0.1449 | 0.2496 | 0 | PASS |
| Appointment booking command | controlled | | | | | | | |
| Outbox to Notification persistence | >= 20 events | | | | | | | |
| Notification commit to WS receipt | >= 20 events | | | | | | | |

Also record:

- Main/route JavaScript resource and transferred sizes.
- Lighthouse mobile TBT and browser interaction evidence.
- Kafka consumer lag before/after a command.
- Redis hits, misses, evictions, memory, and session TTL behavior.
- Top PostgreSQL statements and before/after plans for any actual bottleneck.

Receptionist baseline evidence recorded on 2026-08-19 at 16:20 Africa/Tunis:

- Windows PowerShell 5.1 on `DESKTOP-MPGID83`, local profile, Gateway
  `http://localhost:8079/api/v1`, active synthetic organisation
  `bb5ac622-c39d-4099-951e-738ad0b80931`.
- Each scenario used one first observation, one discarded warm-up, and 30
  sequential warm samples. All first requests returned `200`; all 150 recorded
  warm requests completed without an HTTP or transport error.
- The services were already warm, so the first-request values above are marked
  `(warm)` and must not be presented as cold-start evidence.
- The slowest receptionist p95 was the Patient directory at 104.82 ms, below
  the 500 ms local cross-service-read target. No optimisation is justified by
  this small synthetic-data receptionist sample alone.

Doctor baseline evidence recorded on 2026-08-19 at 16:31 Africa/Tunis:

- The same local machine, Gateway, active synthetic organisation, sampling
  method, and already-warm service state were used for a role-comparable run.
- Auth session, organisation memberships, doctor availability, appointment
  history, and notification inbox each used 30 sequential warm samples. All
  five first observations returned `200`; all 150 measured requests completed
  without an HTTP or transport error.
- Warm p95 values were 56.44, 42.35, 89.87, 61.72, and 79.06 ms respectively.
  Every result passed its 250 or 500 ms target. Doctor availability had the
  highest doctor p95, while its 120.75 ms maximum remains below the threshold.
- These results are a local sequential baseline, not concurrent load evidence.
  The first observations remain marked `(warm)` because no service restart
  preceded this run.

Controlled doctor cold-stack first pass recorded at 16:40 Africa/Tunis:

- Auth, Organisation, Scheduling, Notification, and Gateway were restarted
  while PostgreSQL, Memurai, Kafka, and Discovery remained available. The
  sampler explicitly labelled its separately recorded first observations cold.
- All five first observations returned `200`. Auth session and memberships took
  49.97 and 53.91 ms; the first Scheduling availability and Notification inbox
  reads took 793.09 and 842.09 ms. Appointment history took 96.40 ms after the
  earlier availability request had already initialized Scheduling's request
  path.
- The following 150 warm samples completed without errors. Their p95 values
  were 63.17, 69.88, 103.49, 76.27, and 87.62 ms, all inside the applicable 250
  or 500 ms targets.
- This is a cold-stack first pass, not pure process-startup latency: sampler
  authentication necessarily touches Auth and Gateway before measurement, and
  the sequential scenarios can warm a service used by a later scenario. The
  result is sufficient to expose first-use overhead without misrepresenting it
  as a production cold-start SLA.

PostgreSQL-instrumented warm repeat recorded on 2026-08-24:

| Scenario | Count | First ms | p50 ms | p95 ms | p99 ms | Max ms | Errors | Result |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Auth session — Doctor | 30 | 37.81 | 36.11 | 58.32 | 59.74 | 59.74 | 0 | PASS |
| Organisation memberships — Doctor | 30 | 52.24 | 39.97 | 50.25 | 56.57 | 56.57 | 0 | PASS |
| Doctor availability | 30 | 83.83 | 44.42 | 58.35 | 58.39 | 58.39 | 0 | PASS |
| Appointment history — Doctor | 30 | 59.77 | 43.14 | 57.82 | 65.12 | 65.12 | 0 | PASS |
| Notification inbox — Doctor | 30 | 422.89 | 46.61 | 59.20 | 60.09 | 60.09 | 0 | PASS |
| Auth session — Receptionist | 30 | 44.85 | 38.64 | 50.44 | 52.95 | 52.95 | 0 | PASS |
| Organisation memberships — Receptionist | 30 | 46.03 | 42.68 | 55.62 | 69.20 | 69.20 | 0 | PASS |
| Patient directory | 30 | 557.70 | 50.40 | 63.52 | 80.72 | 80.72 | 0 | PASS |
| Available doctors | 30 | 463.18 | 46.91 | 56.20 | 64.96 | 64.96 | 0 | PASS |
| Appointment history — Receptionist | 30 | 83.60 | 49.25 | 67.33 | 67.97 | 67.97 | 0 | PASS |

All 300 measured requests completed without errors. The first-use Patient,
available-doctor, and Notification values are retained separately and do not
affect warm percentiles. Post-test checks kept every application health endpoint
`UP`; Memurai returned `PONG`; Kafka partition 0 had broker 1 as leader and ISR;
and Notification was at offset 10 of 10 with lag zero.

## 14. Completion rule

This document is complete as an executable guide only after:

- [x] Kafka is installed and port 9092 is verified.
- [x] All role accounts and synthetic IDs are recorded locally.
- [x] Both appointment workflows and denial cases pass through Gateway.
- [x] Kafka, outbox, Notification persistence, WebSocket, and REST recovery evidence
  agree for the same appointment/event IDs.
- [x] Response and query measurements populate the evidence table.
- [x] No optimisation was applied because the measured evidence did not identify
  a bottleneck; any future optimisation still requires before/after evidence
  and a green automated-test gate.

Until then, Phase 4 remains in progress and Phase 5 must not start.
