# Sahha frontend integration contract

This React application now integrates its account/session journey with Sahha
Auth through API Gateway. The remaining domain workflows retain typed mock
implementations until their owning Spring services are implemented.

## Local hybrid mode

The ignored `.env.local` is already configured with:

```properties
VITE_USE_MOCKS=true
VITE_USE_AUTH_MOCKS=false
VITE_API_BASE_URL=http://localhost:8079/api/v1
```

This selects real Auth and mock domain services independently. Set
`VITE_USE_AUTH_MOCKS=true` only when exercising the prepared multi-role demo
without the backend. Set `VITE_USE_MOCKS=false` later as real domain service
adapters replace their mocks.

The frontend sends requests with `credentials: include`, obtains the CSRF
cookie/header value through Gateway, and keeps access/refresh values in scoped
HttpOnly cookies. It never persists either credential in browser storage.

## Required session response

`POST /auth/login` and `POST /auth/refresh` return Auth's non-secret session
metadata:

```json
{
  "userId": "10b7c8b9-bf5e-4f40-a99a-a32a2cad3b98",
  "sessionId": "a8c42991-ce66-4f66-842f-262390359515",
  "accessTokenExpiresAt": "2026-07-29T22:10:00Z",
  "refreshTokenExpiresAt": "2026-07-30T22:00:00Z",
  "sessionIdleExpiresAt": "2026-08-05T22:00:00Z",
  "sessionAbsoluteExpiresAt": "2026-08-28T22:00:00Z",
  "platformRoles": []
}
```

Auth currently owns only the global `PLATFORM_ADMIN` role. Therefore the real
adapter maps `PLATFORM_ADMIN` to `platform-admin` and other verified accounts
to `patient`. Doctor, receptionist, staff, and organisation-administrator
routes remain demo-only until Organisation Service supplies validated scoped
memberships.

The adapter stores only a small display-only email/name projection in
`sessionStorage` because the current Auth response does not contain profile
fields. It disappears with the browser session and is not trusted for any
backend decision. Every Spring endpoint must independently enforce tenant
membership, role, permission, resource ownership, and minimum-necessary
access.

## Role-aware frontend routes

| Role | Route family | Primary frontend workflows |
| --- | --- | --- |
| Patient | `/patient/*` | Find doctors, book visits, longitudinal history, results, medication, billing, verified reviews, messages, profile |
| Private doctor | `/doctor/*` | Patient chart, appointment lifecycle, clinical documentation, results, orders, messages, availability, private-practice view |
| Hospital doctor | `/doctor/*` | Patient chart, appointment lifecycle, clinical documentation, results, orders, care-team assignments, case discussions, handover |
| Receptionist | `/reception/*` | Administrative patient registration and directory, appointment lifecycle, check-in; no clinical or finance workspace |
| Hospital staff | `/staff/*` | Personal schedule and tasks plus a nurse, laboratory, pharmacy, or bed-coordination work queue |
| Hospital operations | `/hospital/operations/*` | Admissions, transfers, discharges, beds, staffing, departments, incidents, handover, patient flow |
| Hospital super admin | `/hospital/admin/*` | Operations plus finance, budgets, claims, statistics, access control, security, logs, and hospital settings |
| Platform admin | `/platform/*` | Organizations, clinician verification, global appointment oversight, review moderation, support, incidents, feature controls, and audit |

The demonstration state in `WorkflowProvider` is versioned and browser-persistent. It intentionally models connected projections: an appointment change appears across role views, a payment updates its invoice, an admission reserves a bed, and a signed encounter becomes a shared clinical artifact. Use the reset action on the login screen to restore the prepared scenario.

## Initial REST endpoints

| Method | Path | Frontend purpose |
| --- | --- | --- |
| GET | `/auth/csrf` | Bootstrap the readable cookie/header CSRF pair |
| POST | `/auth/registrations` | Create a pending patient account |
| POST | `/auth/email-verifications/confirm` | Consume the emailed single-use token |
| POST | `/auth/login` | Authenticate and create a cookie session |
| POST | `/auth/refresh` | Restore or renew the cookie session |
| POST | `/auth/logout` | Revoke the browser session and clear cookies |
| GET | `/patients?page=&size=&query=` | Paginated permitted patient directory |
| GET | `/patients/{patientId}` | Permitted patient resource |
| POST | `/patients` | Register an administrative patient record |
| GET | `/doctors` | Permitted doctor directory |
| POST | `/doctors` | Add a hospital doctor record |
| GET | `/departments` | Hospital department directory |
| POST | `/departments` | Create a hospital department |
| GET | `/appointments` | Role-scoped appointment tracker |
| POST | `/appointments` | Create an appointment request |
| POST | `/appointments/{id}/cancellation` | Cancel with a reason |
| POST | `/appointments/{id}/reschedule` | Reschedule with an optimistic-lock version |
| POST | `/appointments/{id}/status` | Confirm, check in, begin, complete, or mark no-show with optimistic locking |
| GET/POST | `/encounters` | Role-scoped structured consultation records |
| POST | `/encounters/{id}/signature` | Sign and lock a completed encounter |
| GET/POST | `/diagnoses` | Diagnoses connected to a patient and encounter |
| GET/POST | `/prescriptions` | Prescribing and patient medication projection |
| POST | `/prescriptions/{id}/status` | Refill, dispense, hold, cancel, or complete medication workflow |
| GET/POST | `/tests` | Laboratory and imaging orders |
| POST | `/tests/{id}/status` | Collection, processing, result, and clinical-review lifecycle |
| POST | `/attachments` | Upload metadata after secure object-storage negotiation |
| GET/POST | `/conversations` | Direct, group, and patient-linked case discussions |
| POST | `/conversations/{id}/messages` | Send a message to an authorized thread |
| GET/POST | `/reviews` | Completed-appointment review submission and patient history |
| POST | `/reviews/{id}/moderation` | Platform moderation decision with reason |
| GET/POST | `/staff` | Hospital staff directory and invitations |
| GET/POST | `/staff-tasks` | Role-scoped task assignment |
| POST | `/staff-tasks/{id}/status` | Start, complete, or reopen an assigned task |
| GET/POST | `/observations` | Nursing observation documentation |
| GET/POST | `/medication-administrations` | Nursing medication administration record |
| GET/POST | `/beds` | Bed inventory and live capacity state |
| POST | `/beds/{id}/status` | Reserve, occupy, clean, maintain, or release a bed |
| GET/POST | `/admissions` | Admission, placement, transfer, and discharge coordination |
| GET/POST | `/incidents` | Operational incident capture and ownership |
| POST | `/incidents/{id}/status` | Incident progress, resolution, and closure |
| GET/POST | `/handovers` | Department handover items and acknowledgements |
| GET/POST | `/invoices` | Patient invoices and hospital receivables |
| POST | `/invoices/{id}/payments` | Idempotent payment recording |
| GET/POST | `/budgets` | Hospital budget allocations |
| GET/POST | `/expenses` | Expense request, approval, rejection, and posting |
| GET | `/clinical-orders` | Role-scoped clinical order ledger |
| POST | `/clinical-orders` | Create a laboratory, imaging, medication, or referral order |
| GET | `/notifications` | Current user's notifications |
| POST | `/notifications/{id}/read` | Mark one notification read |
| GET | `/audit-events?page=&size=&query=` | Authorized paginated audit view |

The exact TypeScript inputs and outputs live in `src/services/contracts.ts`, `src/models/auth.ts`, `src/models/api.ts`, and `src/models/healthcare.ts`. Extend these contracts by domain as each UI workflow is connected.

## Response and error conventions

Successful resources may be returned directly or wrapped in `{ "data": ..., "meta": ... }`. The client supports both. Pagination should match `PageResponse<T>` with `content`, `page`, `size`, `totalElements`, and `totalPages`.

Return errors as `application/problem+json`:

```json
{
  "type": "https://api.example.com/problems/validation",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid.",
  "requestId": "REQ-7F2A",
  "fieldErrors": {
    "startsAt": "The selected time is no longer available."
  }
}
```

Use `409 Conflict` for optimistic-lock or scheduling collisions, `401` for an absent/expired session, `403` for denied permissions, and `404` when the caller must not learn whether a protected resource exists.

## Microservice and Kafka boundary

Keep the browser connected to one API gateway or backend-for-frontend rather than exposing internal services. Kafka remains server-side. The UI should learn about resulting state through REST reads, server-sent events, or WebSockets; it should never publish directly to Kafka.

Useful backend domain boundaries for the current screens are identity/access, patient registry, appointments, clinical records, messaging/notifications, organization operations, finance/claims, platform governance, and audit. Carry a request ID, actor ID, organization ID, and resource version across synchronous requests and emitted events.

## Security and privacy checklist

- Enforce authorization in Spring Security; never trust the role or permissions rendered by React.
- Scope every hospital query by organization and, where required, department or care relationship.
- Treat receptionist access as administrative-only and audit every patient record view.
- Use field-level response DTOs so unauthorized clinical or financial data is never serialized.
- Require step-up authentication for exports, access-policy changes, emergency access, and privileged session actions.
- Store durable, append-only audit events on the server. The audit screens in this template are presentation layers, not evidence stores.
- Never put patient data, tokens, request bodies, or clinical notes in browser analytics or error telemetry.
- Validate uploads by type, size, malware scan, and authorization before durable storage.

## Deployment notes

The production build is a static SPA in `dist/`. Configure Azure hosting to serve `index.html` for unknown client-side routes, inject environment-specific API configuration at build or startup, use HTTPS only, and set an explicit Content Security Policy. CI should run `npm run typecheck` and `npm run build` before publishing.
