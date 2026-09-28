# Backend permission catalogue — V1

Updated: 2026-09-21. Verification status is recorded in [the living plan](INTERNSHIP_PLAN.md).

## Policy and ownership

Roles describe an assignment; permissions describe operations. Each service owns
its `*Permissions` constants and immutable role-to-permission mapping. Its HTTP
security configuration checks those permissions. Communication and Notification
also check stream permissions at handshake and STOMP subscription boundaries.
The names below are backend authorities, not the frontend's presentation-only
permission vocabulary and not fields clients can submit to obtain access.

The shared `session-security` library contains a stateless technical
`JwtPermissionAuthorities` converter, with no permission names, role bundles,
entities, database, network lookup or domain policy. Auth now also depends on
this library for conversion; its session decision remains PostgreSQL-owned.

Only validated JWTs reach conversion, after the existing signature/claim and
current-session checks. Global assignments come only from `roles`. Organisation
assignments come only from `org_roles` paired with a valid UUID `org_id`. Unknown
roles, roles in the wrong claim, malformed collections, and supplied
`permissions`, `scope` or `authorities` claims cannot create permissions.
Assignments are unioned and deduplicated; no `ROLE_*` authority is minted.

Permissions are fixed V1 bundles, **not** a new custom-grant editor or central
permission database. Adding custom/revocable grants later requires an explicit
ownership, versioning and invalidation design. No token/schema/API response was
changed for this catalogue.

## Authorisation layers

1. Validate the access credential and authoritative session/account state.
2. Require the operation permission at the service (Gateway has only a coarse
   platform-route permission, not clinical policy).
3. Resolve current membership and authorise the specific resource/action inside
   its owning service. Existing role/participation checks here remain deliberate:
   a permission is necessary, never sufficient.
4. Apply state/version, consent, grant expiry/revocation, CSRF and immutable-record
   rules. Keep the existing audit/outbox behavior and field-safe DTOs.

An Organisation or Platform Administrator does not receive Doctor capabilities
by implication. A multi-role administrator+doctor receives both bundles but must
still own the encounter or meet the applicable care/grant rule. Messaging never
grants records or file access. Shared-read permissions only allow requesting a
live, purpose-scoped sharing decision; they are not a blanket patient read.

Self-service capabilities are intentionally available to any authenticated
account, including staff who are also patients. Existing account ownership,
patient-registration links and active-membership/recipient checks remain required.
They do not allow administrators to read other users' notifications or patient
records. Notification's patient-portal delivery is still separate unfinished work.

## Service-owned authorities

In these tables, **Account** means the existing authenticated self-service entry
point, not a new global role. **Platform**, **Admin**, **Doctor**, and **Reception**
mean explicit assignments in their correct global/active-organisation scope.

### Auth

Source: `auth-service/src/main/java/com/sahha/auth/security/AuthPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `auth:account:read:platform` | Platform |
| `auth:account:status:platform` | Platform |
| `auth:account:manage:self` | Account |

### Gateway

Source: `api-gateway/src/main/java/com/sahha/gateway/security/GatewayPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `gateway:platform:route` | Platform |

### Organisation

Source: `organisation-service/src/main/java/com/sahha/organisation/security/OrganisationPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `organisation:read:platform` | Platform |
| `organisation:manage:platform` | Platform |
| `organisation:profile:manage` | Admin |
| `organisation:departments:manage` | Admin |
| `organisation:staff:manage` | Admin |
| `organisation:invitations:manage` | Admin |
| `organisation:doctor-profile:manage:self` | Doctor |
| `organisation:colleagues:read` | Doctor |
| `organisation:context:read:self` | Account |
| `organisation:invitations:respond:self` | Account |

### Patient

Source: `patient-service/src/main/java/com/sahha/patient/security/PatientPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `patient:administrative:read` | Admin, Reception |
| `patient:administrative:write` | Admin, Reception |
| `patient:registration:link:self` | Account |

### Scheduling

Source: `scheduling-service/src/main/java/com/sahha/scheduling/security/SchedulingPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `scheduling:availability:manage:self` | Doctor |
| `scheduling:availability:read` | Admin, Reception, Doctor |
| `scheduling:appointment:read` | Admin, Reception, Doctor |
| `scheduling:appointment:book` | Admin, Reception |
| `scheduling:appointment:adjust` | Admin, Reception, Doctor |
| `scheduling:appointment:check-in` | Admin, Reception |
| `scheduling:appointment:respond` | Doctor |
| `scheduling:appointment:treat` | Doctor |
| `scheduling:clinical-context:read` | Doctor |
| `scheduling:appointment:manage:patient-self` | Account |

### Clinical

Source: `clinical-service/src/main/java/com/sahha/clinical/security/ClinicalPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `clinical:record:read:own` | Doctor |
| `clinical:draft:write:own` | Doctor |
| `clinical:record:finalize:own` | Doctor |
| `clinical:record:correct:own` | Doctor |
| `clinical:summary:read:care` | Doctor |
| `clinical:selected-resource:read:shared` | Doctor |
| `clinical:history:read:shared-care` | Doctor; fresh accepted-care decision and Clinical-owned finality/organisation/patient checks required |
| `clinical:attachment-context:read:own` | Doctor |

### Communication

Source: `communication-service/src/main/java/com/sahha/communication/security/CommunicationPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `communication:conversation:read:participant` | Doctor |
| `communication:conversation:write:participant` | Doctor |
| `communication:message:stream:participant` | Doctor |
| `communication:referral:read:participant` | Doctor |
| `communication:referral:manage:participant` | Doctor |
| `communication:sharing:decide` | Doctor |

### Notification

Source: `notification-service/src/main/java/com/sahha/notification/security/NotificationPermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `notification:read:self` | Account |
| `notification:update:self` | Account |
| `notification:stream:self` | Account |

### File

Source: `file-service/src/main/java/com/sahha/file/security/FilePermissions.java`.

| Authority | Assigned by |
| --- | --- |
| `file:read:own` | Doctor |
| `file:upload:own` | Doctor |
| `file:read:shared` | Doctor |
| `file:read:shared-care` | Doctor |
| `file:scan:synthetic` | Doctor |

## Important resource restrictions

- Organisation: current local membership remains authoritative for administration,
  invitations, colleague directories and own doctor profiles. Platform creation and
  administrator assignment keep their existing platform account checks.
- Patient: administrative DTOs remain separate from clinical information; the
  account-link endpoint still verifies identity and requires a matching registration.
- Scheduling: doctor access remains limited to their own appointments/availability;
  administrative actors cannot confirm/reject or start/complete clinical care.
  Booking, check-in, rescheduling and no-show state/time rules remain in Scheduling.
- Clinical: author membership and appointment/care context are revalidated; sharing
  returns explicitly selected resources or finalised same-patient/org history under
  a fresh accepted shared-treatment grant. Finalisation and correction
  permissions do not allow overwriting signed content or another author's drafts.
- Communication: participant ownership, current colleague membership, consent and
  live grants remain required for every message/referral/share decision.
- File: upload/read authority still requires a current Clinical/Communication
  resource decision, clean file state and short-lived bound access grants. Synthetic
  scanning still requires its existing explicit development flag; no production
  malware-scanner claim is made.
  Shared-care list/metadata/grant/bytes require a live Clinical finality/patient
  context backed by Communication's current care decision. This read-only context
  never grants upload authority. Download tokens bind actor/file/organisation and
  immutable OWN/SELECTED/SHARED_CARE scope; no route accepts old LEGACY tokens.
- Notification: only the current recipient and active organisation may access
  their projection/stream. Destination restrictions and per-frame session
  revalidation stay in force.

## Verification and limits

Role-policy tests cover every current role, explicit multi-role unions, no active
organisation, unknown roles, wrong-scope roles and forged permission-shaped claims
in every affected service and Gateway. Shared-converter tests also cover malformed
input, immutability and defensive policy copies. HTTP regressions exercise the
actual security filters and converters, with resource clients/decoders isolated
where their existing test contract requires it; session decoder and transport tests
separately verify cryptographic/session and real WebSocket behavior.

The native reactor uses isolated PostgreSQL databases/schemas and disposable
Redis test state. Full live multi-doctor/Kafka acceptance, central Audit delivery,
permission-denial observability and Azure deployment remain separate tracker gates.
No backend test or intercepted browser check by itself completes those phases.
